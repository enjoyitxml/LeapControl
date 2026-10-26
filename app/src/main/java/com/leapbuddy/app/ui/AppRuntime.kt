package com.leapbuddy.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.leapbuddy.app.AmapPoi
import com.leapbuddy.app.AmapWebApi
import com.leapbuddy.app.ApiException
import com.leapbuddy.app.Commands
import com.leapbuddy.app.ControlCommand
import com.leapbuddy.app.ControlResultPollingPolicy
import com.leapbuddy.app.FridgeControlCommand
import com.leapbuddy.app.FridgeMode
import com.leapbuddy.app.FridgeStyle
import com.leapbuddy.app.LeapmotorApi
import com.leapbuddy.app.ScheduledAcPlan
import com.leapbuddy.app.ScheduledAcState
import com.leapbuddy.app.ObfuscatedSecrets
import com.leapbuddy.app.Session
import com.leapbuddy.app.SessionStore
import com.leapbuddy.app.ShumeiSecurityManager
import com.leapbuddy.app.TrunkState
import com.leapbuddy.app.TrunkStateMapper
import com.leapbuddy.app.Vehicle
import com.leapbuddy.app.VehicleChargePlan
import com.leapbuddy.app.VehicleLocationDomain
import com.leapbuddy.app.VehicleOtaInfo
import com.leapbuddy.app.VehicleCapabilityDetector
import com.leapbuddy.app.VehicleCapabilities
import com.leapbuddy.app.VehicleStatusMapper
import com.leapbuddy.app.WidgetStatusMapper
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
    /** 行车状态文本：行驶中 / 已驻车（信号缺失为 null）。 */
    val drivingStatus: String? = null,
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
    private val namingPrefs = appContext.getSharedPreferences("leapbuddy_naming", Context.MODE_PRIVATE)

    fun customName(): String =
        namingPrefs.getString("name_${session.selectedVin}", "")?.trim().orEmpty()

    fun setCustomName(name: String) {
        namingPrefs.edit().putString("name_${session.selectedVin}", name.trim()).apply()
    }

    fun displayName(): String = customName()
        .ifEmpty { session.selectedNickname.trim() }
        .ifEmpty { "我的零跑" }

    /**
     * 车模展示方式：
     * - "auto"：优先用官方 carpicture 下发的车图，取不到时用内置剪影
     * - "builtin"：始终用内置矢量剪影（官方图不准时可强制走这条）
     */
    /**
     * 车模展示方式："auto" = 拉取官方车模图（拉不到就不显示）；"off" = 完全关闭车模。
     * 历史值 "builtin"（内置剪影）已废弃，一律按 "off" 处理。
     */
    fun carArtworkMode(): String = when (namingPrefs.getString("car_artwork_mode", "auto") ?: "auto") {
        "builtin" -> "off"
        else -> namingPrefs.getString("car_artwork_mode", "auto") ?: "auto"
    }

    fun setCarArtworkMode(mode: String) {
        namingPrefs.edit().putString("car_artwork_mode", mode).apply()
    }

    /** 车模显示开关：true = 自动拉取官方车模，false = 不显示车模。 */
    fun carArtworkEnabled(): Boolean = carArtworkMode() != "off"

    fun setCarArtworkEnabled(enabled: Boolean) {
        setCarArtworkMode(if (enabled) "auto" else "off")
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
    }

    // ---------------- 车辆配置识别（冰箱 / 天窗 / 哨兵 / 座椅等） ----------------

    private val capabilityPrefs = appContext.getSharedPreferences("leapbuddy_capabilities", Context.MODE_PRIVATE)

    private val _capabilities = MutableStateFlow(VehicleCapabilities.EMPTY)
    /** 当前车辆已识别到的可选配置（粘滞 + 持久化）。 */
    val capabilities: StateFlow<VehicleCapabilities> = _capabilities.asStateFlow()

    /** 已加载能力所对应的 VIN，用于切换车辆时判断是否需要重新加载。 */
    private var capabilityVin: String? = null

    init { syncCapabilities() }

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
        CarStatus(
            soc = VehicleStatusMapper.soc(st),
            range = range,
            electricRange = electric,
            fuelRange = fuel,
            fuelSoc = VehicleStatusMapper.fuelSocPercent(st),
            isRangeExtender = isRev,
            totalMileage = st.opt("totalMileage")?.toString()?.takeIf { it.isNotEmpty() },
            interiorTemp = st.opt("interiorTemp")?.toString()?.takeIf { it.isNotEmpty() },
            locked = WidgetStatusMapper.locked(st),
            acOn = parseOnOff(st.opt("acSwitch")),
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
        ).also { observeCapabilities(st) }
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

    /** 行车状态：优先取文本字段，否则用速度推算（>0 行驶中，==0 已驻车）。 */
    private fun parseDrivingStatus(st: JSONObject): String? {
        val text = listOf("drivingStatus", "vehicleStatus", "runStatus", "carStatus")
            .firstNotNullOfOrNull { st.opt(it)?.toString()?.trim()?.takeIf { t -> t.isNotEmpty() } }
        if (text != null) return text
        val speed = (st.opt("speed") ?: st.opt("velocity") ?: st.opt("vehicleSpeed"))
            ?.toString()?.toDoubleOrNull()
        return when {
            speed == null -> null
            speed > 0.0 -> "行驶中"
            else -> "已驻车"
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

    // ---------------- 外观模式（跟随系统 / 浅色 / 深色） ----------------

    fun appearanceMode(): com.leapbuddy.app.AppearanceMode =
        com.leapbuddy.app.AppearanceMode.fromPreference(namingPrefs.getString("appearance_mode", "SYSTEM"))

    fun setAppearanceMode(mode: com.leapbuddy.app.AppearanceMode) {
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
    suspend fun fetchCarImage(force: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        val vin = session.selectedVin
        if (vin.isBlank()) return@withContext null
        val cacheFile = File(appContext.filesDir, "car_model_$vin.img")
        val urlPrefKey = "car_model_url_$vin"

        if (!force && cacheFile.isFile) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@withContext it }
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
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@withContext it }
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
        // 轮询执行结果（尽力而为，超时则提示用户看车辆反馈）
        for (attempt in 0 until 4) {
            Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(attempt))
            val resp = api.queryControlResult(result.msgID)
            val value = (resp.opt("result") ?: resp.opt("code"))?.toString()
            if (value == "0" || value == "200") return@withContext "${command.label}执行成功"
        }
        "指令已下发，请在车辆上确认执行结果"
    }

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
