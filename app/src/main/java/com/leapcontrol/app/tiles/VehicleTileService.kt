package com.leapcontrol.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 系统控制中心磁贴基类。
 *
 * 点击后直接在本进程内下发控车指令（不需要常驻前台服务）：
 * 指令通常在 1~3 秒内完成，即使进程随后被回收，指令也已到达车端。
 *
 * 开关型磁贴（空调 / 后备箱 / 车窗 / 遮阳帘）的**方向只看本地记住的开合态**，
 * 不再用车况回读做判据 —— 原因见 [QuickTiles.setOn] 上的注释：
 * 部分车型读不到 acOn / trunkOpen 这类信号，会把每次点击都判成「当前是关的」，
 * 于是磁贴只能开、关不掉。
 */
abstract class VehicleTileService : TileService() {

    protected abstract val spec: TileSpec

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences("quick_tiles_state", Context.MODE_PRIVATE) }
    private var busy = false

    override fun onTileAdded() {
        super.onTileAdded()
        prefs.edit().putBoolean("added_${spec.id}", true).apply()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        prefs.edit().putBoolean("added_${spec.id}", false).apply()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!busy) render()
        // 顺手用车况校准一次开合态：用户在车里手动开了后备箱，磁贴不该还显示「已关闭」
        if (spec.kind == TileKind.TOGGLE) scope.launch { syncFromVehicle() }
    }

    override fun onClick() {
        super.onClick()
        if (busy) return

        val ctx = applicationContext
        val store = SessionStore(ctx)
        val session = store.load()

        if (session.oldAuth == null && session.newAuth == null) {
            render("请先登录", active = false)
            toast("请先在零控 App 登录")
            openApp()
            return
        }
        if (session.selectedVin.isBlank()) {
            render("未选择车辆", active = false)
            toast("未选择车辆，请先打开 App")
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            render("未设置密码", active = false)
            toast("请先在「我的」里设置控车密码")
            openApp()
            return
        }

        // 本次要下发的方向：一次性动作固定「执行」，开关磁贴取反本地记住的开合态。
        val turningOn = spec.kind == TileKind.MOMENTARY || !QuickTiles.isOn(ctx, spec)

        busy = true
        render(spec.doing, active = turningOn)
        scope.launch {
            val result = runCatching { execute(ctx, turningOn) }
            busy = false
            result.fold(
                onSuccess = { message ->
                    // 指令下发成功才落状态：失败时磁贴不能显示成「已开启」
                    if (spec.kind == TileKind.TOGGLE) QuickTiles.setOn(ctx, spec, turningOn)
                    toast(message)
                    render(message.take(18), active = spec.kind == TileKind.TOGGLE && turningOn)
                },
                onFailure = { e ->
                    toast("执行失败：${e.message?.take(30) ?: "网络异常"}")
                    render("执行失败", active = false)
                },
            )
            delay(2500)
            render()
        }
    }

    private suspend fun execute(ctx: Context, turningOn: Boolean): String = withContext(Dispatchers.IO) {
        val runtime = AppRuntime(ctx)
        if (spec.kind == TileKind.MOMENTARY) {
            return@withContext runtime.control(spec.openCommand)
        }
        runtime.control(if (turningOn) spec.openCommand else spec.closeCommand)
    }

    /**
     * 用车况校准开合态（只在后台悄悄改状态，不弹任何提示、不打断用户）。
     *
     * 三条安全线：
     * 1. 一分钟内只校一次 —— 每次拉开控制中心所有磁贴都会 onStartListening，不能每次都打车况接口；
     * 2. 该车型读不到这个信号（null）就原地不动，绝不拿「读不到」当「关闭」；
     * 3. 校准期间用户点过磁贴，就以用户刚下发的指令为准，不让更早的车况把状态改回去。
     */
    private suspend fun syncFromVehicle() {
        val ctx = applicationContext
        val startedAt = System.currentTimeMillis()
        if (startedAt - QuickTiles.syncedAt(ctx, spec) < SYNC_INTERVAL_MS) return

        val status = TileStatusSync.status(ctx) ?: return
        QuickTiles.markSynced(ctx, spec)
        if (busy || QuickTiles.lastActionAt(ctx, spec) > startedAt) return

        val open = isOpen(status) ?: return
        if (QuickTiles.isOn(ctx, spec) == open) return
        QuickTiles.setOn(ctx, spec, open)
        if (!busy) render()
    }

    private fun isOpen(status: CarStatus?): Boolean? = when (spec.id) {
        "ac" -> status?.acOn
        "trunk" -> status?.trunkOpen
        "window" -> status?.windowOpen
        "sunshade" -> status?.sunshadeOpen
        else -> null
    }

    private fun render(subtitle: String? = null, active: Boolean = false) {
        val tile = qsTile ?: return
        tile.label = spec.title
        tile.icon = Icon.createWithResource(this, spec.iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: defaultSubtitle()
        }
        tile.state = when {
            active -> Tile.STATE_ACTIVE
            spec.kind == TileKind.TOGGLE && QuickTiles.isOn(this, spec) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun defaultSubtitle(): String = when {
        spec.kind != TileKind.TOGGLE -> "点击执行"
        QuickTiles.isOn(this, spec) -> "已开启 · 点击关闭"
        else -> "已关闭 · 点击开启"
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        /** 车况校准的最小间隔：一分钟内同一块磁贴只校一次。 */
        const val SYNC_INTERVAL_MS = 60_000L
    }
}

/**
 * 磁贴共用的车况快照。
 *
 * 控制中心一拉开，七八块磁贴会同时 onStartListening，各自 `refreshStatus()` 就是七八个请求；
 * 这里用一把锁 + 15 秒缓存把它们合并成一次。
 */
private object TileStatusSync {
    private const val TTL_MS = 15_000L
    private val mutex = Mutex()
    private var at = 0L
    private var cached: CarStatus? = null

    suspend fun status(context: Context): CarStatus? = mutex.withLock {
        val now = System.currentTimeMillis()
        val hit = cached
        if (hit != null && now - at < TTL_MS) return@withLock hit
        val fresh = runCatching { AppRuntime(context).refreshStatus() }.getOrNull()
            ?: return@withLock null
        cached = fresh
        at = now
        fresh
    }
}
