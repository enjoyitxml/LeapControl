package com.leapcontrol.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.leapcontrol.app.LeapmotorApi
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.bluetooth.BleAccessPolicy
import com.leapcontrol.app.bluetooth.BleCertificateSync
import com.leapcontrol.app.bluetooth.BleConnectionPhase
import com.leapcontrol.app.bluetooth.BleConnectionState
import com.leapcontrol.app.bluetooth.BleKeyCertificate
import com.leapcontrol.app.bluetooth.BleKeyRuntime
import com.leapcontrol.app.bluetooth.BleManagedKey
import com.leapcontrol.app.bluetooth.BleNearbyDevice
import com.leapcontrol.app.bluetooth.BlePassiveConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// ============================================================================
// 蓝牙钥匙 —— 界面层宿主
// ----------------------------------------------------------------------------
// 首页图标、设置开关、个性化开关、钥匙详情页都要读同一份状态，
// 而 [BleKeyRuntime] 是进程级单例（后台服务也用它），所以这里做一层「宿主」：
//   · 把 runtime 的三个 StateFlow 收成 Compose 可读的 state；
//   · 持有当前车辆的证书（同步一次就够，按键与 VIN 缓存）；
//   · 把扫描 / 连接 / 写入钥匙设置这些动作收敛成几个方法，UI 只调这里。
//
// 约定：本类只做「薄桥接」，所有安全判断仍走 BleAccessPolicy / BleKeyRuntime 里的 check，
// 不在这里复制第二套规则 —— 否则两个地方一旦分叉，就会出现「UI 说可以、底层抛异常」。
// ============================================================================

/** 蓝牙钥匙当前是否「可用」（决定首页图标是彩色还是灰色）。 */
enum class BleKeyStatus {
    /** 已绑定、已开启，且车端链路在握手或已就绪 */
    ACTIVE,

    /** 已绑定但当前没连上（人在远处 / 后台待命） */
    STANDBY,

    /** 还没绑定车辆，或钥匙材料缺失 */
    UNBOUND,

    /** 设备不支持 / 没给权限 / 蓝牙关着 */
    UNAVAILABLE,
}

@Stable
class BleKeyHost internal constructor(
    val context: Context,
    val runtime: AppRuntime,
    val ble: BleKeyRuntime,
    private val scope: CoroutineScope,
) {
    /** 连接状态（runtime 回调推进，UI 直接读） */
    var connection by mutableStateOf(BleConnectionState())

    /** 已绑定的钥匙记录；null = 这台车还没配对过 */
    var key by mutableStateOf<BleManagedKey?>(null)

    /** 后台保活服务是否在跑 */
    var backgroundRunning by mutableStateOf(false)

    /** 当前车辆的钥匙材料（证书），按 VIN 缓存在 SessionStore */
    var certificate by mutableStateOf<BleKeyCertificate?>(null)

    /** 正在向云端同步钥匙材料 */
    var syncing by mutableStateOf(false)

    /** 一次性提示文案（详情页顶部那条） */
    var notice by mutableStateOf<String?>(null)

    private val sessionStore = SessionStore(context)

    /**
     * 设备「到底能不能用蓝牙钥匙」的三个外部条件。
     *
     * 这三个都要走 PackageManager / Binder，不能在首页顶栏的 getter 里现算 ——
     * 顶栏每次重组都会读一遍，抖动时会变成每帧两三次 binder 调用。
     * 所以只在 attach / 回前台 / 主动刷新时更新一次，其余时候读缓存。
     */
    private var adapterUsable by mutableStateOf(false)
    private var permissionsGranted by mutableStateOf(false)

    fun refreshEnvironment() {
        permissionsGranted = runCatching { ble.controller.hasPermissions() }.getOrDefault(false)
        adapterUsable = runCatching { ble.controller.adapter?.isEnabled == true }.getOrDefault(false)
    }

    val bound: Boolean get() = key != null
    val enabled: Boolean get() = key?.desired?.enabled == true
    val pending: Boolean get() = key?.pending == true
    val phase: BleConnectionPhase get() = connection.phase
    val hasPermissions: Boolean get() = permissionsGranted
    val certificateReady: Boolean
        get() = BleAccessPolicy.isCertificateReady(certificate, runtime.session.selectedVin)

    /** 首页图标该显示成什么样子 */
    val status: BleKeyStatus
        get() = when {
            !supported -> BleKeyStatus.UNAVAILABLE
            !bound || !certificateReady -> BleKeyStatus.UNBOUND
            !enabled -> BleKeyStatus.STANDBY
            phase == BleConnectionPhase.READY || backgroundRunning -> BleKeyStatus.ACTIVE
            else -> BleKeyStatus.STANDBY
        }

    private val supported: Boolean
        get() = hasLe && permissionsGranted && adapterUsable

    // ---- 生命周期 ----

    private val hasLe: Boolean = context.packageManager
        .hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)

    internal fun attach() {
        refreshEnvironment()
        ble.attachSession(runtime.session)
        certificate = loadCertificate()
        key = ble.managedKey.value
        connection = ble.connection.value
        backgroundRunning = ble.backgroundRunning.value
    }

    private fun loadCertificate(): BleKeyCertificate? {
        val account = runtime.session.oldAuth?.accountId.orEmpty()
        val vin = runtime.session.selectedVin
        if (account.isBlank() || vin.isBlank()) return null
        return sessionStore.loadBluetoothKeyCertificate(account, vin)
    }

    // ---- 钥匙材料同步 ----

    /**
     * 从云端拉一次钥匙材料。
     *
     * 与参考实现一致：只有拿到「刷新后的会话 + 新证书」且两者仍然指向同一身份时才会落盘，
     * 否则本次作废（避免用旧设备 id 的证书去连车，连上也会被车端拒）。
     */
    fun syncCertificate(silent: Boolean = false, scanAfterSync: Boolean = false) {
        if (syncing) return
        val current = runtime.session
        val account = current.oldAuth?.accountId.orEmpty()
        if (account.isBlank() || current.selectedVin.isBlank()) {
            if (!silent) notice = "请先登录并选择车辆"
            return
        }
        if (backgroundRunning) {
            if (!silent) notice = "请先关闭后台钥匙，再重新同步"
            return
        }
        val requestSession = sessionStore.load()
        val sync = BleCertificateSync(requestSession)
        syncing = true
        if (!silent) notice = "正在同步钥匙…"
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    LeapmotorApi(requestSession).fetchBluetoothKeyCertificate()
                }
                if (!sync.canCommit(current, requestSession, fetched) ||
                    !sessionStore.completeBluetoothCertificateSync(sync, requestSession, fetched)
                ) {
                    if (!silent) notice = "登录状态已变化，请重新同步钥匙"
                    return@launch
                }
                // 云端可能顺带刷新了设备 id / token，写回内存会话后再告诉 runtime
                current.deviceId = requestSession.deviceId
                current.oldAuth = requestSession.oldAuth
                current.newAuth = requestSession.newAuth
                current.route = requestSession.route
                ble.attachSession(current)
                certificate = fetched
                key = ble.managedKey.value
                if (!silent) notice = null
                if (scanAfterSync) startScan()
            } catch (_: Exception) {
                if (!silent) notice = "钥匙同步失败，请检查网络后重试"
            } finally {
                syncing = false
            }
        }
    }

    // ---- 配对（扫描 → 连接 → 写设置）----

    fun startScan() {
        if (!hasPermissions) {
            refreshEnvironment()
            if (!hasPermissions) {
                notice = "请先授予附近设备权限"
                return
            }
        }
        if (!certificateReady) {
            // 首次使用还没有钥匙材料：先静默同步，成功后自动接着扫描，省掉一次多余的点击
            syncCertificate(scanAfterSync = true)
            return
        }
        if (backgroundRunning || enabled) {
            notice = "蓝牙钥匙已开启，先关闭后台钥匙再重新配对"
            return
        }
        if (!BleAccessPolicy.canScan(phase, syncing)) return
        ble.controller.scan()
    }

    fun cancelScan() {
        if (phase == BleConnectionPhase.SCANNING) ble.controller.disconnect()
    }

    /**
     * 连接选中的车辆。连上并认证通过后 [BleKeyRuntime] 会自动把设备登记为「已绑定」，
     * 这里再等一个 READY 把默认的被动配置下发一次（开启无感 + 后台保活）。
     */
    fun connect(device: BleNearbyDevice) {
        val cert = certificate
        if (cert == null || !certificateReady) {
            notice = "钥匙材料未就绪，请先同步钥匙"
            syncCertificate()
            return
        }
        if (!BleAccessPolicy.canConnect(phase, syncing, true)) return
        notice = null
        runCatching { ble.connectManually(device, cert, runtime.session) }
            .onFailure { notice = "无法连接：先关闭后台钥匙或重新扫描" }
        scope.launch {
            val settled = ble.connection.first {
                it.phase == BleConnectionPhase.READY || it.phase == BleConnectionPhase.FAILED
            }
            if (settled.phase == BleConnectionPhase.READY) {
                key = ble.managedKey.value
                // 首次配对：把默认配置（开启 + 车外按键，视协议版本）推给车端并转后台保活
                key?.let { applyConfiguration(it.desired) }
            }
        }
    }

    // ---- 钥匙设置 ----

    /**
     * 下发一版新的钥匙设置。
     *
     * 车端不支持单字段下发，必须整组下发（温度那套一样），所以这里接一个变换函数，
     * 由调用方只改自己关心的字段，其余沿用当前值。
     */
    fun applyConfiguration(next: BlePassiveConfiguration) {
        val boundKey = key ?: run {
            notice = "请先配对车辆"
            return
        }
        if (runtime.sessionStore.loadOpPassword().isNullOrBlank()) {
            notice = "请先在「设置 → 控车密码」里设置 4 位密码"
            return
        }
        if (!hasPermissions) {
            notice = "请先授予附近设备权限"
            return
        }
        if (!BleAccessPolicy.canApplyConfiguration(connection, boundKey, certificate, next)) {
            notice = "当前状态无法写入钥匙设置（请确认已认证且设备支持）"
            return
        }
        runCatching {
            ble.applyConfiguration(next)
            notice = when {
                !next.enabled -> "已关闭，等待车辆确认"
                boundKey.pending -> "设置待车辆确认"
                else -> "设置已下发"
            }
        }.onFailure { notice = it.message ?: "设置下发失败" }

        // 车端确认之后必须把后台服务拉起来，否则一进后台连接就被断开 ——
        // 表现就是「配对时好好的，锁屏走到车边没反应」。
        scope.launch {
            val confirmed = withTimeoutOrNull(15_000) {
                ble.managedKey.first { it != null && !it.pending }
            }
            if (confirmed == null) return@launch
            if (confirmed.desired.enabled) {
                runCatching { ble.startBackground() }
                runCatching { ble.restoreBackground() }
            } else if (ble.backgroundRunning.value) {
                // 关掉总开关后服务必须一起停，不然通知栏会挂着一条「蓝牙钥匙」常驻
                runCatching { ble.stopBackground() }
            }
        }
    }

    fun toggleEnabled(on: Boolean) {
        val boundKey = key ?: return
        applyConfiguration(boundKey.desired.copy(enabled = on))
    }

    fun stopBackground() {
        runCatching { ble.stopBackground() }
        notice = "后台钥匙已关闭"
    }

    /**
     * 「重新连接」：
     *  · 已经开启无感钥匙时，重连由后台服务负责（[BleKeyRuntime.startBackground] 会踢一次 reconnect）；
     *  · 没开启时退回手动扫描配对。
     */
    fun reconnect() {
        if (enabled) {
            runCatching { ble.startBackground() }
            runCatching { ble.setForeground(true) }
            notice = "正在重新连接…"
        } else {
            startScan()
        }
    }

    fun disconnect() {
        ble.controller.disconnect()
    }

    fun refreshFlows() {
        refreshEnvironment()
        connection = ble.connection.value
        key = ble.managedKey.value
        backgroundRunning = ble.backgroundRunning.value
    }
}

/** 供 Compose 的 @Stable 注解用（避免额外 import 一串 runtime 注解）。 */

val LocalBleKeyHost = staticCompositionLocalOf<BleKeyHost?> { null }

/**
 * 建立（并保持）蓝牙钥匙宿主。
 *
 * 只在登录后调用一次：换 runtime（重新登录）会重建宿主，退出登录时结束会话、
 * 把已绑定的钥匙挂起，下次登录需要重新确认才会继续免密解锁。
 */
@Composable
fun rememberBleKeyHost(runtime: AppRuntime): BleKeyHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = remember(runtime) {
        BleKeyHost(context.applicationContext, runtime, BleKeyRuntime.get(context.applicationContext), scope)
    }
    LaunchedEffect(host, runtime.session.selectedVin, runtime.session.generation, runtime.session.deviceId) {
        host.attach()
    }
    LaunchedEffect(host) {
        // 前台：允许主动探测/连接；回到前台时顺手恢复后台钥匙
        launch {
            ForegroundSignal.events.collect {
                host.ble.setForeground(true)
                runCatching { host.ble.restoreBackground() }
                host.refreshFlows()
            }
        }
        launch {
            ForegroundSignal.pauseEvents.collect {
                host.ble.setForeground(false)
                host.refreshFlows()
            }
        }
        launch {
            host.ble.connection.collect {
                host.connection = it
            }
        }
        launch {
            host.ble.managedKey.collect {
                host.key = it
            }
        }
        launch {
            host.ble.backgroundRunning.collect {
                host.backgroundRunning = it
            }
        }
    }
    return host
}
