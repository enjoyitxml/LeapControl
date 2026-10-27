package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.leapcontrol.app.AmapPoi
import com.leapcontrol.app.AmapWebApi
import com.leapcontrol.app.ApiException
import com.leapcontrol.app.Commands
import com.leapcontrol.app.ControlCommand
import com.leapcontrol.app.ControlResultPollingPolicy
import com.leapcontrol.app.FridgeControlCommand
import com.leapcontrol.app.FridgeMode
import com.leapcontrol.app.FridgeStyle
import com.leapcontrol.app.LeapmotorApi
import com.leapcontrol.app.MileageEnergyParser
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.ScheduledAcPlan
import com.leapcontrol.app.ScheduledAcState
import com.leapcontrol.app.ObfuscatedSecrets
import com.leapcontrol.app.Session
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ShumeiSecurityManager
import com.leapcontrol.app.TrunkState
import com.leapcontrol.app.TrunkStateMapper
import com.leapcontrol.app.Vehicle
import com.leapcontrol.app.VehicleChargePlan
import com.leapcontrol.app.VehicleLocationDomain
import com.leapcontrol.app.VehicleOtaInfo
import com.leapcontrol.app.VehicleCapabilityDetector
import com.leapcontrol.app.VehicleCapabilities
import com.leapcontrol.app.VehicleHomeStatus
import com.leapcontrol.app.VehicleStatusMapper
import com.leapcontrol.app.WidgetStatusMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** 未设置 4 位控车密码时抛出，由 UI 层弹出输入框。 */
class NeedPinException : Exception("需要先设置 4 位控车密码")

/**
 * 控车结果轮询的最长等待时间。
 * 车辆休眠时 T-Box 需要先被唤醒再执行，实测常在 5~15 秒区间内回执；
 * 官方抓包文档同样给出最长 24 秒的窗口。
 */
private const val CONTROL_RESULT_MAX_WAIT_MS = 20_000L

/** 里程 / 能耗缓存有效期：15 分钟。老服务限频，不必每次进首页都打一次。 */
private const val MILEAGE_ENERGY_TTL_MS = 15 * 60 * 1000L

/** 单个车轮的胎压状态。 */
data class TireInfo(
    val position: String,
    val pressure: String?,
    val temperature: String?,
    val warning: Boolean,
)

/** 四个座位的档位（主驾/副驾/左后/右后），信号缺失为 null。 */
data class SeatLevels(
    val driver: Int? = null,
    val passenger: Int? = null,
    val leftRear: Int? = null,
    val rightRear: Int? = null,
)

/**
 * 四门与前舱盖是否未关好（true = 未关好，null = 无该路信号）。
 * 注意官方命名：rbcmDriverDoorStatus 是右前门，不是后门。
 */
data class DoorStates(
    val driver: Boolean? = null,
    val passenger: Boolean? = null,
    val leftRear: Boolean? = null,
    val rightRear: Boolean? = null,
    val hood: Boolean? = null,
) {
    /** 是否至少收到过一路信号；全部为 null 时表示这台车不上报门状态。 */
    val hasAnySignal: Boolean
        get() = driver != null || passenger != null || leftRear != null || rightRear != null || hood != null

    /** 未关好的部位名称列表。 */
    val openLabels: List<String>
        get() = buildList {
            if (driver == true) add("左前门")
            if (passenger == true) add("右前门")
            if (leftRear == true) add("左后门")
            if (rightRear == true) add("右后门")
            if (hood == true) add("前舱盖")
        }
}

/** 车载冰箱状态快照。 */
data class FridgeSnapshot(
    val enabled: Boolean? = null,
    val mode: FridgeMode? = null,
    val style: FridgeStyle? = null,
    val temp: Int? = null,
)

/** 首页展示的车辆状态快照。 */
data class CarStatus(
    val soc: Int,
    val range: String?,
    val electricRange: String?,
    val fuelRange: String?,
    val fuelSoc: Int?,
    val isRangeExtender: Boolean,
    /**
     * 续航工况标签：服务端 rangeMode = 0 → CLTC（标准工况），1 → WLTC（动态工况）。
     * 取不到就不显示徽标，不猜。
     */
    val rangeStandard: String? = null,
    val totalMileage: String?,
    val interiorTemp: String?,
    val locked: Boolean?,
    val acOn: Boolean?,
    val tires: List<TireInfo> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val acTargetTemp: String? = null,
    val acWindLevel: String? = null,
    val acCircle: String? = null,
    /** 后备箱是否开启（信号缺失时为 null，不允许猜测成已关）。 */
    val trunkOpen: Boolean? = null,
    /** 是否有车窗未关（信号缺失时为 null）。 */
    val windowOpen: Boolean? = null,
    /** 天幕遮阳帘是否开启（信号缺失时为 null）。 */
    val sunshadeOpen: Boolean? = null,
    /** 行车状态文本：D挡 · 60km/h / R挡 / N挡 / 已驻车 / 行驶中（信号缺失为 null）。 */
    val drivingStatus: String? = null,
    /** 车速原始值（1319，可能是数字或带单位的文本）。 */
    val speed: String? = null,
    /** 当前挡位：P / R / N / D（1010）。 */
    val gearStatus: String? = null,
    /** 是否行驶中（速度 > 0，或处于 D / R 挡）。 */
    val isDriving: Boolean? = null,
    /** 整车是否已下电 / 离车驻车（闭锁、电源 OFF、EPB 拉起任一成立）。 */
    val isShutDown: Boolean = false,
    /** 充电中心相关（信号缺失均为 null，UI 显示「--」） */
    val chargingPower: String? = null, // 充电功率，如 "7.2 kW"
    val chargeTimeLeft: String? = null, // 剩余充电时间，如 "1 小时 23 分"
    val chargeVoltage: String? = null, // 电压，如 "368 V"
    val chargeCurrent: String? = null, // 电流，如 "18.5 A"
    val batteryTemp: String? = null, // 电池温度，如 "28 °C"
    /** 天窗是否开启（1718 sunroofOpening），与遮阳帘（1724）分开。 */
    val sunroofOpen: Boolean? = null,
    /** 前备箱是否开启（仅部分车型支持，信号缺失为 null）。 */
    val frunkOpen: Boolean? = null,
    /** 哨兵模式是否开启。 */
    val sentryOn: Boolean? = null,
    /** 四门与前舱盖是否未关好（信号缺失为 null）。 */
    val doors: DoorStates? = null,
    /** 是否正在充电。 */
    val charging: Boolean? = null,
    /** 充电枪是否连接。 */
    val chargeCableConnected: Boolean? = null,
    /** 座椅加热档位 0-3。 */
    val seatHeating: SeatLevels? = null,
    /** 座椅通风档位 0-3。 */
    val seatVentilation: SeatLevels? = null,
    /** 方向盘加热：0 关 / 1 弱 / 2 强。 */
    val steeringWheelHeating: Int? = null,
    /** 后视镜加热是否开启。 */
    val rearviewMirrorHeating: Boolean? = null,
    /** 车载冰箱状态。 */
    val fridge: FridgeSnapshot? = null,
)

/**
 * 应用运行时：持有会话与 API 层，全部业务方法为挂起函数（内部切 IO 线程）。
 * UI 层（LoginScreen / HomeScreen）只调用这里，方便后期整体重构界面。
 */
class AppRuntime(context: Context) {
    private val appContext = context.applicationContext
    val sessionStore = SessionStore(appContext)
    val session: Session = sessionStore.load()

    // 车辆自定义命名（按 VIN 独立存储，登出不清除）
    private val namingPrefs = appContext.getSharedPreferences("leapcontrol_naming", Context.MODE_PRIVATE)

    fun customName(): String =
        namingPrefs.getString("name_${session.selectedVin}", "")?.trim().orEmpty()

    fun setCustomName(name: String) {
        namingPrefs.edit().putString("name_${session.selectedVin}", name.trim()).apply()
    }

    fun displayName(): String = customName()
        .ifEmpty { session.selectedNickname.trim() }
        .ifEmpty { "我的零跑" }

    // 控车二次确认（指纹 / 密码防误触）：默认开启
    private val securityPrefs = appContext.getSharedPreferences("leapcontrol_security", Context.MODE_PRIVATE)

    /** 主界面向车端下发控车指令前，是否需要指纹 / 密码二次确认（默认开启）。 */
    fun biometricConfirmEnabled(): Boolean =
        securityPrefs.getBoolean("biometric_confirm", true)

    fun setBiometricConfirmEnabled(enabled: Boolean) {
        securityPrefs.edit().putBoolean("biometric_confirm", enabled).apply()
    }

    /**
     * 车模展示方式：
     * - "auto"：优先用官方 carpicture 下发的车图，取不到时用内置剪影
     * - "builtin"：始终用内置矢量剪影（官方图不准时可强制走这条）
     */
    /**
     * 车模展示方式。车模显示开关已移除 —— 车模默认常开，恒为 "auto"
     * （拉取官方 carpicture 车图，拉不到则首页不占位）。
     * 历史遗留值 "builtin" / "off" 一律按 "auto" 处理，老版本关掉过的用户升级后也会恢复显示。
     */
    fun carArtworkMode(): String = "auto"

    fun setCarArtworkMode(mode: String) {
        namingPrefs.edit().putString("car_artwork_mode", mode).apply()
    }

    // ---------------- 目的地搜索（高德 Web 服务） ----------------

    /** 高德 Web 服务 Key：优先用户自定义，其次内置（内置为空时地址解析不可用）。 */
    fun amapKey(): String =
        sessionStore.loadCustomAmapWebKey()?.takeIf { it.isNotBlank() }
            ?: runCatching { ObfuscatedSecrets.getAmapWebKey(appContext) }.getOrDefault("")

    fun setAmapKey(key: String) {
        sessionStore.saveCustomAmapWebKey(key.trim().ifEmpty { null })
    }

    fun hasAmapKey(): Boolean = amapKey().isNotBlank()

    /** 搜索目的地 POI。无 Key 时返回空列表。 */
    suspend fun searchDestination(keyword: String): List<AmapPoi> = withContext(Dispatchers.IO) {
        val key = amapKey()
        if (key.isBlank() || keyword.isBlank()) return@withContext emptyList()
        runCatching { AmapWebApi.searchPoi(key, keyword.trim()) }.getOrDefault(emptyList())
    }

    /** 首页控车按钮顺序（按 VIN 独立存储）。 */
    fun controlLayout(): List<String> {
        val raw = namingPrefs.getString("control_layout_${session.selectedVin}", "") ?: ""
        return if (raw.isBlank()) ControlActions.defaultLayout
        else ControlActions.sanitize(raw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun setControlLayout(ids: List<String>) {
        val clean = ControlActions.sanitize(ids).take(ControlActions.MaxSelectable)
        namingPrefs.edit().putString("control_layout_${session.selectedVin}", clean.joinToString(",")).apply()
    }

    // ---------------- 首页功能模块（顺序 / 增删） ----------------

    /** 首页横向模块区的模块顺序（按 VIN 独立存储）。 */
    fun homeModules(): List<String> {
        val raw = namingPrefs.getString("home_modules_${session.selectedVin}", "") ?: ""
        return if (raw.isBlank()) HomeModules.DEFAULT_ORDER
        else HomeModules.sanitize(raw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun setHomeModules(ids: List<String>) {
        namingPrefs.edit()
            .putString("home_modules_${session.selectedVin}", HomeModules.sanitize(ids).joinToString(","))
            .apply()
    }

    val isLoggedIn: Boolean get() = session.oldAuth != null

    // ---------------- 网络 ----------------

    private val connectivityManager: ConnectivityManager?
        get() = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /** 当前是否有可用的互联网连接（锁屏/切后台后网络可能短暂不可用）。 */
    fun isNetworkAvailable(): Boolean {
        val cm = connectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 监听网络恢复，返回注销函数。 */
    fun registerNetworkRestoreListener(): () -> Unit {
        val cm = connectivityManager ?: return { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ForegroundSignal.emitNetworkRestored()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    ForegroundSignal.emitNetworkRestored()
                }
            }
        }
        return try {
            cm.registerDefaultNetworkCallback(callback)
            val cleanup: () -> Unit = { runCatching { cm.unregisterNetworkCallback(callback) } }
            cleanup
        } catch (e: Exception) {
            { }
        }
    }

    /** 瞬时网络故障（DNS 解析失败/超时/连接中断）判定。 */
    private fun isTransientNetworkError(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            if (cur is java.net.UnknownHostException ||
                cur is java.net.SocketTimeoutException ||
                cur is java.net.ConnectException ||
                cur is java.net.SocketException ||
                cur is javax.net.ssl.SSLException
            ) return true
            val msg = cur.message.orEmpty()
            if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("No address associated with hostname", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true)
            ) return true
            cur = cur.cause
        }
        return false
    }

    /** 瞬时故障自动重试：最多 3 次，间隔 400ms / 900ms。 */
    private fun <T> withNetworkRetry(block: () -> T): T {
        var lastError: Exception? = null
        for (attempt in 0 until 3) {
            try {
                if (attempt > 0 && !isNetworkAvailable()) {
                    Thread.sleep(600L * attempt)
                    continue
                }
                return block()
            } catch (e: Exception) {
                lastError = e
                if (!isTransientNetworkError(e) || attempt == 2) throw e
                Thread.sleep(if (attempt == 0) 400L else 900L)
            }
        }
        throw lastError ?: ApiException("网络请求失败")
    }

    // ---------------- 登录 ----------------

    suspend fun sendSms(phone: String): Unit = withContext(Dispatchers.IO) {
        ShumeiSecurityManager.initialize(appContext)
        LeapmotorApi(session).sendSms(phone.trim())
    }

    /** 返回账号下全部车辆。 */
    suspend fun login(phone: String, smsCode: String): List<Vehicle> = withContext(Dispatchers.IO) {
        ShumeiSecurityManager.initialize(appContext)
        val api = LeapmotorApi(session)
        api.loginWithSms(phone.trim(), smsCode.trim())
        val vehicles = api.listVehicles()
        if (vehicles.isEmpty()) throw ApiException("账号下没有找到车辆")
        sessionStore.saveVehicles(vehicles)
        val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.first()
        selectVehicle(selected)
        vehicles
    }

    suspend fun selectVehicle(vehicle: Vehicle) {
        session.selectedVin = vehicle.vin
        session.selectedCarType = vehicle.carType
        session.selectedNickname = vehicle.nickname.ifEmpty { vehicle.officialNickname }
        withContext(Dispatchers.IO) { sessionStore.save(session) }
        // 换车后能力要重新按 VIN 加载，不能直接沿用上一辆的结果
        syncCapabilities()
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        sessionStore.clear()
        // 登出后不该再给别人看到上一辆车的车况/车模，清掉内存与磁盘快照
        _status.value = null
        _lastUpdated.value = 0L
        carBitmapCache = null
        carBitmapCacheVin = null
        runCatching { statusPrefs.edit().clear().apply() }
    }

    // ---------------- 车辆配置识别（冰箱 / 天窗 / 哨兵 / 座椅等） ----------------

    private val capabilityPrefs = appContext.getSharedPreferences("leapcontrol_capabilities", Context.MODE_PRIVATE)

    private val _capabilities = MutableStateFlow(VehicleCapabilities.EMPTY)
    /** 当前车辆已识别到的可选配置（粘滞 + 持久化）。 */
    val capabilities: StateFlow<VehicleCapabilities> = _capabilities.asStateFlow()

    // ---------------- 车况快照（跨页面 / 跨 Tab 保持） ----------------
    //
    // 首页状态原本是 HomeScreen 里的 remember：切到「我的」再切回来会整棵树重组，
    // remember 丢失 → 车况变 null → 所有卡片短暂显示「--」再填充，视觉上就是闪一下。
    // 这里提到 AppRuntime（App 级单例 + 磁盘缓存），首帧就有内容，永远不会走「空数据」那一帧。

    private val statusPrefs =
        appContext.getSharedPreferences("leapcontrol_status_cache", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow<CarStatus?>(null)
    /** 最近一次成功拉取的车况（进程内保持；冷启动时由磁盘缓存预填）。 */
    val statusFlow: StateFlow<CarStatus?> = _status.asStateFlow()

    private val _lastUpdated = MutableStateFlow(0L)
    /** 最近一次车况更新时刻（毫秒）。 */
    val lastUpdatedFlow: StateFlow<Long> = _lastUpdated.asStateFlow()

    private fun statusCacheKey(vin: String) = "status_$vin"
    private fun statusTimeKey(vin: String) = "status_time_$vin"

    /** 把原始报文写进磁盘：下次冷启动先显示上一次车况，再静默联网刷新。 */
    private fun cacheStatus(vin: String, st: JSONObject) {
        if (vin.isBlank()) return
        runCatching {
            val raw = st.toString()
            // 异常报文可能极大，超过 256KB 就不缓存，避免 SharedPreferences 被撑爆
            if (raw.length <= 262_144) {
                statusPrefs.edit()
                    .putString(statusCacheKey(vin), raw)
                    .putLong(statusTimeKey(vin), System.currentTimeMillis())
                    .apply()
            }
        }
    }

    /**
     * 用磁盘缓存预填 [statusFlow]（按 VIN 独立）。
     * 解析时不重复做配置识别：当时那次识别结果已经持久化在 capabilityPrefs 里了。
     */
    fun loadCachedStatus(vin: String = session.selectedVin) {
        val raw = runCatching { statusPrefs.getString(statusCacheKey(vin), null) }.getOrNull()
        if (raw.isNullOrBlank()) {
            _status.value = null
            _lastUpdated.value = 0L
            return
        }
        val st = runCatching { JSONObject(raw) }.getOrNull()
        _status.value = st?.let { runCatching { buildStatus(it, observeCaps = false) }.getOrNull() }
        _lastUpdated.value = runCatching { statusPrefs.getLong(statusTimeKey(vin), 0L) }.getOrDefault(0L)
    }

    /** 已加载能力所对应的 VIN，用于切换车辆时判断是否需要重新加载。 */
    private var capabilityVin: String? = null

    // ---- 里程 / 能耗 ----
    // ⚠️ 这两个必须声明在 init 块**之前**：Kotlin 的属性与 init 块按书写顺序执行，
    //    放到后面时 init{} 里的 loadCachedMileageEnergy() 会拿到一个还没赋值的
    //    _mileageEnergy → 冷启动必崩（MutableStateFlow.setValue on a null object）。
    private val energyPrefs =
        appContext.getSharedPreferences("leapcontrol_mileage_energy", Context.MODE_PRIVATE)

    private val _mileageEnergy = MutableStateFlow<MileageEnergySummary?>(null)
    /** 最近一次成功拉取的里程 / 能耗汇总（进程内保持，冷启动由磁盘缓存预填）。 */
    val mileageEnergyFlow: StateFlow<MileageEnergySummary?> = _mileageEnergy.asStateFlow()

    init {
        syncCapabilities()
        // 冷启动先用上一次的车况把界面填上，联网刷新再覆盖（不留空白帧）
        loadCachedStatus()
        // 里程 / 能耗同样先上缓存，联网拉取在首页进入时触发
        loadCachedMileageEnergy()
    }

    /** 让 [_capabilities] 跟随当前选中车辆；VIN 没变则不重复读取。 */
    private fun syncCapabilities() {
        val vin = session.selectedVin
        if (vin == capabilityVin) return
        capabilityVin = vin
        _capabilities.value = loadCapabilities(vin)
    }

    private fun loadCapabilities(vin: String): VehicleCapabilities {
        if (vin.isBlank()) return VehicleCapabilities.EMPTY
        val raw = capabilityPrefs.getString("caps_$vin", null) ?: return VehicleCapabilities.EMPTY
        return runCatching { VehicleCapabilities.fromJson(JSONObject(raw)) }
            .getOrDefault(VehicleCapabilities.EMPTY)
    }

    /**
     * 每次拉到车况后，从**原始 signalMap** 识别配置并与历史结果粘滞合并。
     * 必须用原始 JSONObject：车不支持某硬件时网关会整组不下发 key，
     * 解析成 CarStatus 后「无信号」和「值为 0」都变成 null，无从区分。
     */
    private fun observeCapabilities(st: JSONObject) {
        val vin = session.selectedVin
        if (vin.isBlank()) return
        val previous = if (capabilityVin == vin) _capabilities.value else loadCapabilities(vin)
        val observed = VehicleCapabilityDetector.detect(st, session.selectedCarType)
        val merged = VehicleCapabilityDetector.merge(previous, observed)
        if (merged != previous) {
            capabilityPrefs.edit().putString("caps_$vin", merged.toJson().toString()).apply()
            capabilityVin = vin
            _capabilities.value = merged
        }
    }

    // ---------------- 车况 ----------------

    suspend fun refreshStatus(): CarStatus = withContext(Dispatchers.IO) {
        val api = LeapmotorApi(session)
        val st: JSONObject = withNetworkRetry { api.getVehicleState() }
        val status = buildStatus(st)
        cacheStatus(session.selectedVin, st)
        // 结果通过 statusFlow 下发：首页直接读到新值，不需要 UI 自己再 setState
        _status.value = status
        _lastUpdated.value = System.currentTimeMillis()
        status
    }

    // ---------------- 行驶里程 / 能耗 ----------------
    //
    // 老服务的 drivingrecord/mileage/energy/detail 接口：累计口径 + 近 7 日口径。
    // 这个接口与车况轮询完全独立，**失败时静默保留上一次结果**，
    // 绝不因为拿不到能耗就把车况卡片一起拖垮。

    private fun energyCacheKey(vin: String) = "energy_$vin"

    /** 用磁盘缓存预填 [mileageEnergyFlow]，避免冷启动时模块先空一帧。 */
    fun loadCachedMileageEnergy(vin: String = session.selectedVin) {
        val raw = runCatching { energyPrefs.getString(energyCacheKey(vin), null) }.getOrNull()
        if (raw.isNullOrBlank()) {
            _mileageEnergy.value = null
            return
        }
        _mileageEnergy.value = runCatching { MileageEnergySummary.fromJson(JSONObject(raw)) }.getOrNull()
    }

    /**
     * 拉取里程 / 能耗（15 分钟内不重复请求）。
     * 两个口径都拿不到时返回上一次的缓存（可能为 null），由 UI 显示「--」。
     */
    suspend fun refreshMileageEnergy(force: Boolean = false): MileageEnergySummary? =
        withContext(Dispatchers.IO) {
            val vin = session.selectedVin
            if (vin.isBlank()) return@withContext null
            val cached = _mileageEnergy.value
            val now = System.currentTimeMillis()
            if (!force && cached != null && now - cached.capturedAt < MILEAGE_ENERGY_TTL_MS) {
                return@withContext cached
            }
            if (!isNetworkAvailable()) return@withContext cached
            val api = LeapmotorApi(session)
            var summary = cached
            runCatching { withNetworkRetry { api.getMileageEnergy() } }
                .onSuccess { resp ->
                    MileageEnergyParser.parseLifetime(resp)?.let { parsed ->
                        summary = summary?.merge(parsed) ?: parsed
                    }
                }
            runCatching { withNetworkRetry { api.getRecentMileageEnergy() } }
                .onSuccess { resp ->
                    MileageEnergyParser.parseRecent(resp)?.let { parsed ->
                        summary = summary?.merge(parsed) ?: parsed
                    }
                }
            val result = summary
            if (result != null && result.hasAny) {
                _mileageEnergy.value = result
                runCatching {
                    energyPrefs.edit().putString(energyCacheKey(vin), result.toJson().toString()).apply()
                }
            }
            result
        }

    /**
     * 原始报文 → [CarStatus]。
     *
     * @param observeCaps 是否顺带识别车辆配置。联网刷新时要；从磁盘缓存恢复时不要
     *                    （那次的识别结果当时已经合并进 capabilities 并持久化了）。
     */
    private fun buildStatus(st: JSONObject, observeCaps: Boolean = true): CarStatus {
        val electric = VehicleStatusMapper.electricRange(st)
        val fuel = VehicleStatusMapper.fuelRange(st)
        // 增程车：存在燃油信号即判定，主续航取综合续航（纯电+燃油）
        val isRev = fuel != null || VehicleStatusMapper.fuelSocPercent(st) != null
        val range = if (isRev) {
            VehicleStatusMapper.combinedRange(st)
                ?: VehicleStatusMapper.remainingRange(st, session.selectedCarType)
        } else {
            VehicleStatusMapper.remainingRange(st, session.selectedCarType)
        }
        return CarStatus(
            soc = VehicleStatusMapper.soc(st),
            range = range,
            electricRange = electric,
            fuelRange = fuel,
            fuelSoc = VehicleStatusMapper.fuelSocPercent(st),
            isRangeExtender = isRev,
            rangeStandard = when (st.opt("rangeMode")?.toString()?.trim()) {
                "0" -> "CLTC"
                "1" -> "WLTC"
                else -> null
            },
            totalMileage = st.opt("totalMileage")?.toString()?.takeIf { it.isNotEmpty() },
            interiorTemp = st.opt("interiorTemp")?.toString()?.takeIf { it.isNotEmpty() },
            locked = WidgetStatusMapper.locked(st),
            acOn = parseAcOn(st),
            tires = parseTires(st),
            latitude = parseLocation(st).first,
            longitude = parseLocation(st).second,
            acTargetTemp = parseAcSetting(st)["temperature"],
            acWindLevel = parseAcSetting(st)["windlevel"],
            acCircle = parseAcSetting(st)["circle"]?.let {
                when (it) { "in", "1" -> "内循环"; "out", "0" -> "外循环"; else -> null }
            },
            trunkOpen = parseTrunkOpen(st),
            windowOpen = parseWindowOpen(st, session.selectedCarType),
            sunshadeOpen = parseSunshadeOpen(st),
            drivingStatus = parseDrivingStatus(st),
            speed = st.opt("speed")?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            gearStatus = formatGear(st.opt("gearStatus")),
            isDriving = runCatching { WidgetStatusMapper.isDriving(st) }.getOrNull(),
            isShutDown = VehicleHomeStatus.isVehicleShutDown(
                bcmKeyPositionOn3 = st.opt("bcmKeyPositionOn3"),
                vehicleState = st.opt("vehicleState"),
                parkingBrakeState = st.opt("parkingBrakeState"),
                locked = WidgetStatusMapper.locked(st),
            ),
            chargingPower = formatValue(pick(st, "chargingPower", "chargePower", "chargePowerKw"), "kW"),
            chargeTimeLeft = parseChargeTimeLeft(st),
            chargeVoltage = formatValue(pick(st, "chargeVoltage", "voltage", "bmsVoltage"), "V"),
            chargeCurrent = formatValue(pick(st, "chargeCurrent", "current", "bmsCurrent"), "A"),
            batteryTemp = formatValue(pick(st, "batteryTemp", "batteryTemperature", "packTemp", "batTemp"), "°C"),
            sunroofOpen = parseSunroofOpen(st),
            frunkOpen = parseFrunkOpen(st),
            sentryOn = parseSentryOn(st),
            doors = parseDoors(st),
            charging = parseCharging(st),
            chargeCableConnected = parseChargeCableConnected(st),
            // 座椅信号号（前一个）必须与其命名信号严格对应，串位会把副驾读到主驾通风上去。
            // 权威映射：加热 主驾 2100 / 副驾 2118 / 左后 1879 / 右后 1880
            //          通风 主驾 2101 / 副驾 2119 / 左后 3727 / 右后 3728
            seatHeating = SeatLevels(
                driver = seatLevel(st, "driverSeatHeating", "2100"),
                passenger = seatLevel(st, "passengerSeatHeating", "2118"),
                leftRear = seatLevel(st, "leftRearSeatHeating", "1879"),
                rightRear = seatLevel(st, "rightRearSeatHeating", "1880"),
            ),
            seatVentilation = SeatLevels(
                driver = seatLevel(st, "driverSeatVentilation", "2101"),
                passenger = seatLevel(st, "passengerSeatVentilation", "2119"),
                leftRear = seatLevel(st, "leftRearSeatVentilation", "3727"),
                rightRear = seatLevel(st, "rightRearSeatVentilation", "3728"),
            ),
            steeringWheelHeating = seatLevel(st, "steeringWheelHeating", "1816")?.coerceIn(0, 2),
            // 不兜底 rearWindowHeating（后风挡加热）：那几乎全系都有，会让「有没有后视镜加热」永远为真
            rearviewMirrorHeating = parseOnOff(pick(st, "rearviewMirrorHeating", "49", "50")),
            fridge = parseFridge(st),
        ).also { if (observeCaps) observeCapabilities(st) }
    }

    // ---------------- 车况解析辅助 ----------------

    private fun parseTires(st: JSONObject): List<TireInfo> {
        val layout = listOf(
            Triple("左前", "leftFrontTirePressure", "leftFrontTirePressureState"),
            Triple("右前", "rightFrontTirePressure", "rightFrontTirePressureState"),
            Triple("左后", "leftRearTirePressure", "leftRearTirePressureState"),
            Triple("右后", "rightRearTirePressure", "rightRearTirePressureState"),
        )
        return layout.mapNotNull { (label, pressureKey, stateKey) ->
            val pressure = formatValue(st.opt(pressureKey), "kPa")
            val prefix = pressureKey.removeSuffix("Pressure")
            val temperature = listOf(
                "${prefix}Temperature",
                "${prefix}Temp",
                "${prefix}TireTemperature",
                "${prefix}TireTemp",
            ).firstNotNullOfOrNull { formatValue(st.opt(it), "°C") }
            if (pressure == null && temperature == null && st.opt(stateKey) == null) return@mapNotNull null
            TireInfo(label, pressure, temperature, optBool(st, stateKey) == true)
        }
    }

    /**
     * 后备箱开启状态：1281 / bbcmBackDoorStatus。
     * 信号缺失或取值无法判定时返回 null，避免把一次点击解析成相反指令。
     */
    private fun parseTrunkOpen(st: JSONObject): Boolean? {
        val raw = st.opt("bbcmBackDoorStatus") ?: st.opt("1281")
        return when (TrunkStateMapper.fromSignal(raw)) {
            TrunkState.OPEN -> true
            TrunkState.CLOSED -> false
            TrunkState.UNKNOWN -> null
        }
    }

    /** 是否有车窗未关：1693-1696 开关位或四窗开度百分比。 */
    private fun parseWindowOpen(st: JSONObject, carType: String): Boolean? {
        if (!WidgetStatusMapper.hasWindowTelemetry(st, carType)) return null
        return WidgetStatusMapper.openWindowLabels(st, carType).isNotEmpty()
    }

    /** 天幕遮阳帘开启状态：1724 roofOpening（0-100）优先，其次 1718 sunroofOpening（0-10）。 */
    private fun parseSunshadeOpen(st: JSONObject): Boolean? {
        val roof = percentValue(st, "roofOpening", "1724")
        val sunroof = percentValue(st, "sunroofOpening", "1718")
        return when {
            roof != null -> roof > 0.0
            sunroof != null -> sunroof > 0.0
            else -> null
        }
    }

    /** 天窗开启状态：1718 sunroofOpening（0-10，部分车型为 0-100）。 */
    private fun parseSunroofOpen(st: JSONObject): Boolean? =
        percentValue(st, "sunroofOpening", "1718")?.let { it > 0.0 }

    /** 前备箱开启状态：bbcmFrontDoorStatus / frunkStatus。 */
    private fun parseFrunkOpen(st: JSONObject): Boolean? {
        // 注意：1280 是 rbcmRightRearDoorStatus（右后门），不能拿来当前备箱信号
        val raw = pick(st, "bbcmFrontDoorStatus", "frunkStatus", "frontDoorStatus", "frontTrunkStatus")
        return when (raw?.toString()?.trim()?.lowercase()) {
            "1", "true", "open", "opened" -> true
            "0", "false", "closed", "close" -> false
            else -> null
        }
    }

    /**
     * 四门与前舱盖状态：true 表示未关好。
     * 信号命名沿用官方上报：rbcmDriverDoorStatus 即右前门（注意不是后门）。
     * 全部信号都缺失时返回 null，让体检界面显示「未知」而不是假装全部关好。
     */
    private fun parseDoors(st: JSONObject): DoorStates? {
        val states = DoorStates(
            driver = parseOnOff(pick(st, "lbcmDriverDoorStatus", "1277")),
            passenger = parseOnOff(pick(st, "rbcmDriverDoorStatus", "1278")),
            leftRear = parseOnOff(pick(st, "lbcmLeftRearDoorStatus", "1279")),
            rightRear = parseOnOff(pick(st, "rbcmRightRearDoorStatus", "1280")),
            hood = parseOnOff(pick(st, "hoodStatus", "fbcmHoodStatus", "1282", "1276")),
        )
        return states.takeIf { it.hasAnySignal }
    }

    /** 哨兵模式开启状态。 */
    private fun parseSentryOn(st: JSONObject): Boolean? =
        parseOnOff(pick(st, "sentryMode", "sentryStatus", "sentrySwitch", "sentry"))

    /** 是否正在充电。 */
    private fun parseCharging(st: JSONObject): Boolean? {
        val raw = pick(st, "chargingStatus", "chargeStatus", "chargingState", "chargeState")
        return when (raw?.toString()?.trim()?.lowercase()) {
            "1", "2", "true", "charging", "charge" -> true
            "0", "false", "idle", "not_charging", "stop" -> false
            else -> null
        }
    }

    /** 充电枪是否连接（枪插着但没在充也算已连接）。 */
    private fun parseChargeCableConnected(st: JSONObject): Boolean? {
        val raw = pick(st, "chargeGunStatus", "gunStatus", "chargeCableStatus", "chargeConnectorStatus")
        return when (raw?.toString()?.trim()?.lowercase()) {
            "1", "2", "true", "connected" -> true
            "0", "false", "disconnected" -> false
            else -> null
        }
    }

    /** 座椅/方向盘等档位：依次尝试字段名与信号号，取 0-3。 */
    private fun seatLevel(st: JSONObject, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { st.opt(it) }
            ?.toString()?.trim()
            ?.toDoubleOrNull()?.toInt()
            ?.coerceIn(0, 3)

    /** 车载冰箱：开关、模式、风格、目标温度。 */
    private fun parseFridge(st: JSONObject): FridgeSnapshot? {
        val enabledRaw = pick(st, "fridgeStatus", "fridgeEnable", "fridgeSwitch", "fridgeOn")
        val tempRaw = pick(st, "fridgeTemp", "fridgeSetTemp", "fridgeTargetTemp")
        val modeRaw = pick(st, "fridgeMode")
        val styleRaw = pick(st, "fridgeStyle")
        val enabled = when (enabledRaw?.toString()?.trim()?.lowercase()) {
            "1", "true", "on" -> true
            "0", "false", "off" -> false
            else -> null
        }
        if (enabled == null && tempRaw == null && modeRaw == null && styleRaw == null) return null
        return FridgeSnapshot(
            enabled = enabled,
            mode = FridgeMode.fromSignal(modeRaw?.toString()?.trim()?.toIntOrNull()),
            style = FridgeStyle.fromSignal(styleRaw?.toString()?.trim()?.toIntOrNull()),
            temp = tempRaw?.toString()?.trim()?.toDoubleOrNull()?.toInt(),
        )
    }

    /**
     * 行车状态：挡位（1010）+ 车速（1319）为准，再由整车状态 / EPB / 闭锁判断是否已下电驻车。
     *
     * 之前只看 speed 推算，车端不发速度信号时恒为「已驻车」，界面永远停在驻车中；
     * 现在 D / R 挡直接显示「D挡 · 60km/h」，P 挡与已下电显示「已驻车」。
     */
    private fun parseDrivingStatus(st: JSONObject): String? {
        val shutDown = VehicleHomeStatus.isVehicleShutDown(
            bcmKeyPositionOn3 = st.opt("bcmKeyPositionOn3"),
            vehicleState = st.opt("vehicleState"),
            parkingBrakeState = st.opt("parkingBrakeState"),
            locked = WidgetStatusMapper.locked(st),
        )
        VehicleHomeStatus.resolveDetailedDrivingState(
            gearStatus = formatGear(st.opt("gearStatus")),
            speed = st.opt("speed")?.toString(),
            isDriving = runCatching { WidgetStatusMapper.isDriving(st) }.getOrNull(),
            isShutDown = shutDown,
        )?.let { return it.label }
        // 既没有挡位也没有车速：退回整车运行状态（1944：1=Ready 行车就绪）
        return when (st.opt("vehicleState")?.toString()?.trim()?.toIntOrNull()) {
            1 -> "已启动"
            0 -> "已驻车"
            else -> null
        }
    }

    /** 挡位归一化：P / R / N / D（服务端可能下发字母、中文或数字编码）。 */
    private fun formatGear(v: Any?): String? {
        if (v == null || v == JSONObject.NULL) return null
        val raw = v.toString().trim()
        if (raw.isEmpty()) return null
        return when (raw.uppercase()) {
            "P", "PARK", "驻车", "停车", "0" -> "P"
            "R", "REVERSE", "倒车", "1" -> "R"
            "N", "NEUTRAL", "空挡", "2" -> "N"
            "D", "DRIVE", "前进", "3" -> "D"
            else -> raw
        }
    }

    /** 依次尝试多个候选 key，返回第一个非空值（用于不同车型字段命名不一致）。 */
    private fun pick(st: JSONObject, vararg keys: String): Any? =
        keys.firstNotNullOfOrNull { st.opt(it) }

    /** 剩余充电时间：数字按分钟格式化，文本原样返回。 */
    private fun parseChargeTimeLeft(st: JSONObject): String? {
        val raw = listOf("chargeTimeLeft", "chargeTimeRemaining", "remainingChargeTime", "chargeRemainTime")
            .firstNotNullOfOrNull { st.opt(it) } ?: return null
        val mins = raw.toString().toDoubleOrNull()
        return if (mins != null) {
            val m = mins.toLong()
            if (m >= 60) {
                val h = m / 60
                val mm = m % 60
                if (mm == 0L) "$h 小时" else "$h 小时 $mm 分"
            } else "$m 分钟"
        } else raw.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun percentValue(st: JSONObject, namedKey: String, numericKey: String): Double? =
        (st.opt(namedKey) ?: st.opt(numericKey))
            ?.toString()
            ?.trim()
            ?.removeSuffix("%")
            ?.toDoubleOrNull()

    /** 车辆经纬度（优先命名信号，其次已验证的数字信号）。 */
    private fun parseLocation(st: JSONObject): Pair<Double?, Double?> {
        val values = HashMap<String, Any?>()
        val keys = st.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            values[k] = st.opt(k)
        }
        val pair = VehicleLocationDomain.selectFromDecodedOrRaw(values).pair
        return (pair.latitude?.toString()?.toDoubleOrNull()) to
            (pair.longitude?.toString()?.toDoubleOrNull())
    }

    /**
     * 空调开关状态：优先权威信号 1938(acSwitch)，车端不上报时退回 2183(acSetting) 的 operate 字段。
     *
     * 部分车型/网关根本不下发 acSwitch，只在 acSetting 里带 operate（off/stop=关，manual/auto=开）。
     * 只看单一信号会让 acOn 恒为 null → 开关方向永远判不出来（这就是「只能开不能关」的根源之一）。
     * 两个信号都取不到时才返回 null（未知），不允许猜。
     */
    private fun parseAcOn(st: JSONObject): Boolean? {
        parseOnOff(pick(st, "acSwitch", "1938"))?.let { return it }
        val operate = parseAcSetting(st)["operate"]?.trim()?.lowercase() ?: return null
        return when (operate) {
            "off", "stop", "false", "0" -> false
            else -> true
        }
    }

    /** 空调状态字段（车端回传的 acSetting 为 JSON 串或对象）。 */
    private fun parseAcSetting(st: JSONObject): Map<String, String> {
        val raw = st.opt("acSetting") ?: st.opt("2183") ?: return emptyMap()
        val obj = when (raw) {
            is JSONObject -> raw
            else -> runCatching { JSONObject(raw.toString()) }.getOrNull()
        } ?: return emptyMap()
        val out = HashMap<String, String>()
        obj.keys().forEach { key ->
            val v = obj.opt(key)
            if (v != null && v != JSONObject.NULL) out[key] = v.toString()
        }
        return out
    }

    private fun formatValue(value: Any?, unit: String): String? {
        if (value == null || value == JSONObject.NULL) return null
        val text = when (value) {
            is Number -> {
                val d = value.toDouble()
                if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
            }
            else -> value.toString().trim()
        }
        return if (text.isEmpty()) null else "$text $unit"
    }

    private fun optBool(o: JSONObject, key: String): Boolean? = when (val v = o.opt(key)) {
        null, JSONObject.NULL -> null
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", ignoreCase = true)
        else -> null
    }

    // ---------------- 车模图片（官方主题接口） ----------------

    /** 识别到的车型名（由官方车辆列表接口下发，如 C16 / C11 / C01 / T03）。 */
    fun vehicleModel(): String = session.selectedCarType.trim()

    /** 账号下全部车辆（用于首页多车切换下拉）。 */
    fun vehicles(): List<Vehicle> = sessionStore.loadVehicles()

    // ---------------- 四季主题 ----------------

    fun seasonThemeId(): String =
        namingPrefs.getString("season_theme", SeasonThemes.SPRING.id) ?: SeasonThemes.SPRING.id

    fun setSeasonThemeId(id: String) {
        namingPrefs.edit().putString("season_theme", id).apply()
    }

    /** 动态背景开关：true = 播放对应季节的背景视频，false = 只用静态渐变。 */
    fun seasonVideoEnabled(): Boolean = namingPrefs.getBoolean("season_video", true)

    fun setSeasonVideoEnabled(enabled: Boolean) {
        namingPrefs.edit().putBoolean("season_video", enabled).apply()
    }

    /**
     * 背景视频的声音开关，默认关。
     *
     * 控车类 App 打开即出声太打扰，默认静音；想听配乐的用户自己在设置里打开。
     */
    fun seasonVideoSoundEnabled(): Boolean = namingPrefs.getBoolean("season_video_sound", false)

    fun setSeasonVideoSoundEnabled(enabled: Boolean) {
        namingPrefs.edit().putBoolean("season_video_sound", enabled).apply()
    }

    /**
     * 夜间自动切换到星空主题，默认开。
     *
     * 每天 22:00 – 次日 06:00 自动生效星空主题，白天自动还原用户自己选的季节
     * —— 用户的季节偏好本身不会被覆盖，只是夜间被临时顶替。
     */
    fun nightThemeAuto(): Boolean = namingPrefs.getBoolean("night_theme_auto", true)

    fun setNightThemeAuto(enabled: Boolean) {
        namingPrefs.edit().putBoolean("night_theme_auto", enabled).apply()
    }

    // ---------------- 外观模式（跟随系统 / 浅色 / 深色） ----------------

    fun appearanceMode(): com.leapcontrol.app.AppearanceMode =
        com.leapcontrol.app.AppearanceMode.fromPreference(namingPrefs.getString("appearance_mode", "SYSTEM"))

    fun setAppearanceMode(mode: com.leapcontrol.app.AppearanceMode) {
        namingPrefs.edit().putString("appearance_mode", mode.name).apply()
    }

    /**
     * 拉取官方车模图。
     *
     * 数据源为「车辆外观图片与 3D 车模元数据」接口（carpicture/3d/key）下发的 shareBindUrl，
     * 即该 VIN 对应车型的官方 2D 定妆照（透明底 PNG，约 290KB）。
     *
     * 注意：不能改用 theme/listThemeList、theme/latest 接口——那两个返回的是
     * 车机主题壁纸（雪山、晴空等风景图），不是车模，会导致展示错误图片。
     *
     * 结果缓存到本地文件，URL 变化时自动重下。
     */
    /**
     * 车模图内存缓存：切页面再回来时直接复用上一次解码好的 Bitmap。
     *
     * 之前车模只存在 HomeScreen 的 remember 里，切 Tab 重组后先 null（车模区塌掉）、
     * 等异步解码完才回来——这就是掉那儿时不时被肉眼捕捉到的那一下闪动。
     */
    private var carBitmapCache: Bitmap? = null
    private var carBitmapCacheVin: String? = null

    /** 当前车辆已缓存的车模图（内存热缓存，同步返回，首帧即可显示）。 */
    fun cachedCarBitmap(): Bitmap? =
        carBitmapCache.takeIf { carBitmapCacheVin == session.selectedVin }

    suspend fun fetchCarImage(force: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        val vin = session.selectedVin
        if (vin.isBlank()) return@withContext null
        val cacheFile = File(appContext.filesDir, "car_model_$vin.img")
        val urlPrefKey = "car_model_url_$vin"

        if (!force) {
            cachedCarBitmap()?.let { return@withContext it }
            if (cacheFile.isFile) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)?.let {
                    carBitmapCache = it
                    carBitmapCacheVin = vin
                    return@withContext it
                }
            }
        }

        val api = LeapmotorApi(session)
        val meta = try {
            withNetworkRetry { api.getVehiclePictureMeta(vin) }
        } catch (e: Exception) {
            null
        }
        val rawUrl = meta?.shareBindUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        // 阿里云 CDN 只签 http，强制升级为 https 再下载
        val url = if (rawUrl.startsWith("http://", ignoreCase = true)) {
            "https://" + rawUrl.substring(7)
        } else {
            rawUrl
        }

        val cachedUrl = namingPrefs.getString(urlPrefKey, null)
        if (!force && cachedUrl == url && cacheFile.isFile) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let {
                carBitmapCache = it
                carBitmapCacheVin = vin
                return@withContext it
            }
        }

        val bitmap = try {
            api.downloadParkingPhotoBitmap(url) ?: api.downloadParkingPhotoBitmap(rawUrl)
        } catch (e: Exception) {
            null
        } ?: return@withContext null

        runCatching {
            java.io.FileOutputStream(cacheFile).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            namingPrefs.edit().putString(urlPrefKey, url).apply()
        }
        carBitmapCache = bitmap
        carBitmapCacheVin = vin
        bitmap
    }

    /** 车模图来源标记，用于 UI 区分「官方图 / 内置剪影」。 */
    fun hasOfficialCarImage(): Boolean =
        File(appContext.filesDir, "car_model_${session.selectedVin}.img").isFile

    /** 清除车模缓存（换车时使用）。 */
    fun clearCarImageCache() {
        runCatching {
            File(appContext.filesDir, "car_model_${session.selectedVin}.img").delete()
        }
    }

    // ---------------- 控车 ----------------

    /**
     * 执行控车指令（commandName 如 "lock"/"unlock"/"acOn"/"acOff"/"horn"）。
     * [presetAcTemperature] 非空时改为按指定温度下发空调指令（Commands.buildAc）。
     */
    suspend fun control(commandName: String, presetAcTemperature: Int? = null): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        val api = LeapmotorApi(session)
        val command = if (presetAcTemperature != null) {
            Commands.buildAc(presetAcTemperature)
        } else {
            Commands.build(commandName)
        }
        val result = withNetworkRetry { api.sendControl(command, pin) }
        // 轮询执行结果：车端 T-Box 唤醒 + 执行通常需要数秒，官方抓包协议允许最长 24 秒。
        // 之前只等约 2 秒就在车辆真正执行前放弃，返回的「已下发」无法反映真实结果，
        // 也让用户无法判断到底是「车没收到」还是「收到了没执行」。
        val deadline = System.currentTimeMillis() + CONTROL_RESULT_MAX_WAIT_MS
        var attempt = 0
        var lastCode: String? = null
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(resultPollDelayMs(attempt))
            attempt++
            val resp = runCatching { api.queryControlResult(result.msgID) }.getOrNull() ?: continue
            val code = firstResultCode(resp)
            if (code != null) lastCode = code
            if (code == "0" || code == "200") return@withContext "${command.label}执行成功"
        }
        // 超时或结果码不可解析：回读一次车况做二次判定，避免谎报成功
        val suffix = lastCode?.let { "（车端结果码 $it）" }.orEmpty()
        if (commandName == "acOn" || commandName == "acOff" || presetAcTemperature != null) {
            val stillOn = runCatching { parseAcOn(api.getVehicleState()) }.getOrNull()
            val turnedOn = presetAcTemperature != null || commandName == "acOn"
            return@withContext when {
                turnedOn && stillOn == true -> "空调已开启$suffix"
                !turnedOn && stillOn == false -> "空调已关闭$suffix"
                !turnedOn -> "关闭指令已下发$suffix，但车端仍上报空调运行中，请在车上确认"
                else -> "开启指令已下发$suffix，暂未读到空调运行信号，请下拉刷新查看"
            }
        }
        "指令已下发$suffix，请在车辆上确认执行结果"
    }

    /** 结果轮询等待得到的网关结果码（优先 result，其次 code）。 */
    private fun firstResultCode(resp: JSONObject): String? {
        val value = (resp.opt("result") ?: resp.opt("code"))
            ?.takeIf { it != JSONObject.NULL }?.toString()?.trim()
        return value?.takeIf { it.isNotEmpty() }
    }

    /** 轮询节奏：首次 1s，随后每 1s 一次（官方约定首包 1s、间隔 500ms，最长 24s）。 */
    private fun resultPollDelayMs(attempt: Int): Long = if (attempt == 0) 1_000L else 1_000L

    /** 校验并保存 4 位控车密码。 */
    suspend fun verifyAndSavePin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!pin.matches(Regex("\\d{4}"))) return@withContext false
        val ok = LeapmotorApi(session).verifyOperatePassword(pin)
        if (ok) sessionStore.saveOpPassword(pin)
        ok
    }

    /**
     * 下发充电中心设置（参考原项目 leapauto 的实现）：
     * - 健康充电 + 充电上限：走 healthyCharging/control（不需要控车密码）
     * - 预约充电：走 cmdid=190 充电计划（需要旧会话凭证，有控车密码则带上）
     * - 预约电池预热：走 cmdid=161 PTC 预热计划（同上）
     * - 绑桩：官方需扫码/网页流程，App 内暂不开放，仅占位
     * 返回下发结果摘要；单项失败不影响其它项，异常向上抛由调用方转错误提示。
     */
    suspend fun applyChargingSettings(
        healthyCharge: Boolean,
        chargeLimit: Int,
        reserveCharge: Boolean,
        batteryPreheat: Boolean,
        scheduledStart: String = "23:00",
        scheduledEnd: String = "07:00",
        scheduledTargetSoc: Int = 80,
        preheatStart: String = "23:00",
    ): String = withContext(Dispatchers.IO) {
        val api = LeapmotorApi(session)
        val pin = sessionStore.loadOpPassword().orEmpty()
        val results = mutableListOf<String>()
        // 1. 健康充电（含充电上限）
        runCatching {
            api.setHealthyCharging(enabled = healthyCharge, targetSoc = chargeLimit.coerceIn(50, 100))
            results += "健康充电已更新"
        }.onFailure { results += "健康充电失败" }
        // 2. 预约充电
        runCatching {
            api.setScheduledCharging(
                enabled = reserveCharge,
                startTime = scheduledStart,
                endTime = scheduledEnd,
                targetSoc = scheduledTargetSoc.coerceIn(50, 100),
                opPassword = pin,
            )
            results += if (reserveCharge) "预约充电已开启" else "预约充电已关闭"
        }.onFailure { results += "预约充电失败" }
        // 3. 预约电池预热
        runCatching {
            api.setScheduledBatteryPreheat(
                enabled = batteryPreheat,
                startTime = preheatStart,
                opPassword = pin,
            )
            results += if (batteryPreheat) "预约电池预热已开启" else "预约电池预热已关闭"
        }.onFailure { results += "预约电池预热失败" }
        results.joinToString("；")
    }

    // ---------------- 舒适配置 / 定时空调 / OTA ----------------

    /** 车载冰箱控制（cmdid 500）。 */
    suspend fun controlFridge(command: FridgeControlCommand): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        val api = LeapmotorApi(session)
        val cmd = Commands.buildFridgeControl(command)
        val result = withNetworkRetry { api.sendControl(cmd, pin) }
        for (attempt in 0 until 4) {
            Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(attempt))
            val resp = api.queryControlResult(result.msgID)
            val value = (resp.opt("result") ?: resp.opt("code"))?.toString()
            if (value == "0" || value == "200") return@withContext "${cmd.label}执行成功"
        }
        "指令已下发，请在车辆上确认执行结果"
    }

    /** 读取车端已保存的充电计划（commonConfig config.3）。 */
    suspend fun loadChargePlan(): VehicleChargePlan? = withContext(Dispatchers.IO) {
        runCatching { LeapmotorApi(session).getVehicleChargePlan() }.getOrNull()
    }

    /** 解锁充电枪物理锁止销（cmdid 192）。 */
    suspend fun unlockCharger(): String = controlChargeGun("unlockCharger")

    /** 开始充电（cmdid 193）。 */
    suspend fun startCharging(): String = controlChargeGun("startCharging")

    /** 停止充电（cmdid 193）。 */
    suspend fun stopCharging(): String = controlChargeGun("stopCharging")

    private suspend fun controlChargeGun(commandName: String): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        val api = LeapmotorApi(session)
        val cmd = Commands.build(commandName)
        val result = withNetworkRetry { api.sendControl(cmd, pin) }
        for (attempt in 0 until 4) {
            Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(attempt))
            val resp = api.queryControlResult(result.msgID)
            val value = (resp.opt("result") ?: resp.opt("code"))?.toString()
            if (value == "0" || value == "200") return@withContext "${cmd.label}成功"
        }
        "指令已下发，请在车辆上确认执行结果"
    }

    /** 读取车端已保存的定时空调计划。 */
    suspend fun loadScheduledAc(): ScheduledAcState? = withContext(Dispatchers.IO) {
        runCatching { LeapmotorApi(session).getScheduledAirConditioning() }.getOrNull()
    }

    /** 下发定时空调计划（plan.enabled = false 表示取消）。 */
    suspend fun applyScheduledAc(plan: ScheduledAcPlan): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        LeapmotorApi(session).setScheduledAirConditioning(plan, pin)
        if (plan.enabled) "定时空调已设置（每天 ${plan.startTime}）" else "定时空调已取消"
    }

    /** 车辆 OTA 升级信息。 */
    suspend fun loadOtaInfo(): VehicleOtaInfo? = withContext(Dispatchers.IO) {
        val vin = session.selectedVin?.takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching { LeapmotorApi(session).getVehicleOtaInfo(vin) }.getOrNull()
    }

    /** 下载 OTA 固件包（cmdid 390）。 */
    suspend fun downloadOta(taskId: String): String = runFotaCommand("fotaDownload:$taskId")

    /** 安装已下载的固件包（cmdid 391，安装期间车辆不可使用）。 */
    suspend fun installOta(taskId: String): String = runFotaCommand("fotaInstall:$taskId")

    /** 预约安装固件包（cmdid 392），scheduleTime 形如 "23:00"。 */
    suspend fun scheduleOta(taskId: String, scheduleTime: String): String =
        runFotaCommand("fotaSchedule:$taskId:$scheduleTime")

    private suspend fun runFotaCommand(commandName: String): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        val api = LeapmotorApi(session)
        val cmd = Commands.build(commandName)
        val result = withNetworkRetry { api.sendControl(cmd, pin) }
        for (attempt in 0 until 4) {
            Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(attempt))
            val resp = api.queryControlResult(result.msgID)
            val value = (resp.opt("result") ?: resp.opt("code"))?.toString()
            if (value == "0" || value == "200") return@withContext "${cmd.label}成功"
        }
        "指令已下发，请在车辆上确认执行结果"
    }

    val hasPin: Boolean get() = sessionStore.loadOpPassword() != null

    // ---------------- 工具 ----------------

    private fun parseOnOff(value: Any?): Boolean? = when (value) {
        null, JSONObject.NULL -> null
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.lowercase()) {
            "1", "true", "on" -> true
            "0", "false", "off" -> false
            else -> null
        }
        else -> null
    }
}
