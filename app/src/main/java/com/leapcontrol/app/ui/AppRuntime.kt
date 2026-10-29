package com.leapcontrol.app.ui

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.leapcontrol.app.AmapPoi
import com.leapcontrol.app.ApkUpdater
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.GitHubUpdate
import com.leapcontrol.app.HomeCardId
import com.leapcontrol.app.HomeCardLayout
import com.leapcontrol.app.LauncherIcon
import com.leapcontrol.app.AmapWebApi
import com.leapcontrol.app.ApiException
import com.leapcontrol.app.ChargePlanCyclesHelper
import com.leapcontrol.app.ChargeSchedule
import com.leapcontrol.app.ChargeStatus
import com.leapcontrol.app.Commands
import com.leapcontrol.app.ControlResultPollingPolicy
import com.leapcontrol.app.FridgeControlCommand
import com.leapcontrol.app.FridgeMode
import com.leapcontrol.app.FridgeStyle
import com.leapcontrol.app.LeapmotorApi
import com.leapcontrol.app.MileageEnergyParser
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.ScheduledAcPlan
import com.leapcontrol.app.ScheduledAcState
import com.leapcontrol.app.ScheduledPreheatState
import com.leapcontrol.app.ObfuscatedSecrets
import com.leapcontrol.app.QuickSwitchMode
import com.leapcontrol.app.QuickSwitchPrefs
import com.leapcontrol.app.Session
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ShumeiSecurityManager
import com.leapcontrol.app.TrunkState
import com.leapcontrol.app.TrunkStateMapper
import com.leapcontrol.app.UpdateCheckResult
import com.leapcontrol.app.journal.BaiduAsr
import com.leapcontrol.app.Vehicle
import com.leapcontrol.app.VehicleChargePlan
import com.leapcontrol.app.WeeklyEnergyData
import com.leapcontrol.app.WeeklyEnergyParser
import com.leapcontrol.app.VehicleLocationDomain
import com.leapcontrol.app.VehicleOtaInfo
import com.leapcontrol.app.VehicleCapabilityDetector
import com.leapcontrol.app.VehicleCapabilities
import com.leapcontrol.app.VehicleHomeStatus
import com.leapcontrol.app.VehicleStatusMapper
import com.leapcontrol.app.VehiclePowerTypeResolver
import com.leapcontrol.app.toStatusPowerType
import com.leapcontrol.app.VehicleSignals
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

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

/**
 * 「一键关闭耗电功能」时两条指令之间的间隔。
 * 网关对连续车控有限流，实测 1 秒内连发第二条容易回「系统繁忙」；
 * 800ms 是能明显缩短整体耗时的同时又不丢指令的折中值。
 */
private const val POWER_DRAIN_GAP_MS = 800L

/**
 * 单个车轮的胎压状态。
 *
 * [pressure] 是 **kPa 整数文本**（如 "240"），不带单位；展示层统一拼「kPa」。
 */
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
    /**
     * 出风方向（acSetting.position）：`all` 全车出风 / `wshld` 前挡出风。
     * 车端没有独立信号，只能从 acSetting 里读；缺失为 null（UI 不高亮任何一段）。
     */
    val acPosition: String? = null,
    /** 冷热模式（acSetting.mode）：cold / hot / nohotcold。调风量、风向时要原样带下去。 */
    val acMode: String? = null,
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
    /**
     * 充电状态（1149 chargeState）：0 未插枪 / 1 充电中 / 2 充电完成 / 3 故障 /
     * 4 预约等待 / 6 暂停。文案映射见 [com.leapcontrol.app.ChargeStatus.label]。
     */
    val chargeState: Int? = null,
    /** 健康充电（电池养护）是否开启，信号 48 healthyChargeEnabled。 */
    val healthyChargeEnabled: Boolean? = null,
    val chargingPower: String? = null, // 充电功率，如 "7.2 kW"
    val chargeTimeLeft: String? = null, // 剩余充电时间，如 "1 小时 23 分"
    val chargeVoltage: String? = null, // 电压，如 "368 V"
    val chargeCurrent: String? = null, // 电流，如 "18.5 A"
    val batteryTemp: String? = null, // 电池温度，如 "28 °C"
    // 上面三项（1177 / 1178 / 1182）是 BMS 常驻信号，不只充电时才有值；
    // 「车辆健康」卡也直接读这三个字段，别当成充电专属字段删掉。
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

    /**
     * 「零记」运行时（用车目标 / 用车成本 / 城市打卡）。
     *
     * 数据按 VIN 隔离、全部存本地，和控车链路完全解耦：
     * 零记这边出任何问题都不该影响车况与控车，所以它是独立持有的一个对象。
     */
    val journal = com.leapcontrol.app.journal.JournalRuntime(appContext) { session.selectedVin }

    // 车辆自定义命名（按 VIN 独立存储，登出不清除）
    private val namingPrefs = appContext.getSharedPreferences("leapcontrol_naming", Context.MODE_PRIVATE)

    /**
     * 相伴卡背景（个性化里可选 6 套内置图）。
     *
     * 用 State 而不是每次读盘：在个性化里选完，返回首页要立刻换图。
     * 初值在构造时就同步读一次，冷启动不会先闪一帧默认图。
     */
    private val _ownershipBackdrop = mutableStateOf(
        OwnershipBackdrops.byId(
            namingPrefs.getString("ownership_backdrop", OwnershipBackdrops.DEFAULT_ID),
        ).id,
    )
    val ownershipBackdrop: State<String> get() = _ownershipBackdrop

    /**
     * 首页「快速切换壁纸 / 背景」的三项偏好（模式 + 两块各自的小白点开关）。
     *
     * 用 State 而不是每次读盘：设置里一改，退回首页立刻生效，不用等重建。
     * 默认是「小白点 + 两块都显示」，与上一版行为一致；上一版的布尔开关
     * （关掉 = 两颗点都隐藏且没有其它入口）会迁移成 [QuickSwitchMode.OFF]。
     */
    private val _quickSwitch = mutableStateOf(
        QuickSwitchPrefs(
            mode = QuickSwitchMode.fromLegacy(
                rawMode = namingPrefs.getString("quick_switch_mode", null),
                legacyDotEnabled = if (namingPrefs.contains("quick_dot_enabled")) {
                    namingPrefs.getBoolean("quick_dot_enabled", true)
                } else {
                    null
                },
            ),
            carDot = namingPrefs.getBoolean("quick_dot_car", true),
            cardDot = namingPrefs.getBoolean("quick_dot_card", true),
        ),
    )
    val quickSwitch: State<QuickSwitchPrefs> get() = _quickSwitch

    /**
     * 「零记」功能总开关（设置 → 零记）。
     *
     * 关掉 ≠ 删数据：底部 Dock 的「零记」入口、首页那两张零记卡（保险 / 保养）、
     * 玩法说明里的零记一节、以及只服务于灵感球的语音 Key 入口全部收起，
     * 磁盘上的账单 / 打卡 / 目标一律保留 —— 想回来时打开开关就还在。
     *
     * 默认开启（老用户升级后行为不变）。存在 namingPrefs：跟着本机走、登出不丢。
     */
    private val _journalEnabled = mutableStateOf(namingPrefs.getBoolean("journal_enabled", true))
    val journalEnabled: State<Boolean> get() = _journalEnabled

    fun setJournalEnabled(enabled: Boolean) {
        _journalEnabled.value = enabled
        namingPrefs.edit().putBoolean("journal_enabled", enabled).apply()
    }

    /**
     * 动力类型的**手动指定**（设置 → 动力类型），按 VIN 保存，null = 自动推断。
     *
     * 为什么需要它：车端报文靠不住。纯电 C01 会下发 3235 fuelSoc = 0、续航只给 2188，
     * 只按信号推断就可能把纯电车认成增程车，界面显示「电 + 油」两行还不显示续航。
     * 自动推断已经按「有油才算增程」修过一轮，这里再给用户一个能自己拍板的兜底。
     */
    private val _powerTypeOverride = mutableStateOf(loadManualPowerType(session.selectedVin))
    val powerTypeOverride: State<SessionStore.VehiclePowerType?> get() = _powerTypeOverride

    /** 读用户手动确认过的动力类型；没确认过（含仅由车型接口推断出来的）一律返回 null。 */
    private fun loadManualPowerType(vin: String): SessionStore.VehiclePowerType? {
        if (vin.isBlank() || !sessionStore.isVehiclePowerTypeConfirmed(vin)) return null
        return sessionStore.loadVehicleConfig(vin).powerType
    }

    fun setPowerTypeOverride(type: SessionStore.VehiclePowerType?) {
        _powerTypeOverride.value = type
        val vin = session.selectedVin
        if (vin.isNotBlank()) {
            if (type == null) sessionStore.clearVehiclePowerType(vin)
            else sessionStore.saveVehiclePowerType(vin, type)
        }
        // 立刻用磁盘里那份原始报文重建一次：不必等下一轮联网刷新，改完回首页就能看到。
        // 没缓存（刚换车 / 刚装）时**不要**调 loadCachedStatus —— 它会把 _status 清成 null，
        // 首页整块续航会变成「--」，比不改还糟；这种情况交给下一轮刷新。
        val cached = runCatching { statusPrefs.getString(statusCacheKey(vin), null) }.getOrNull()
        if (!cached.isNullOrBlank()) loadCachedStatus()
    }

    /**
     * 首页顶部「蓝牙钥匙」图标是否显示（个性化 → 首页）。
     *
     * 默认显示：这是车主的常用入口，藏起来反而要翻两层设置才能打开。
     * 和壁纸的 `quick_dot_*` 一样放在 namingPrefs 里，跟着本机走、登出不丢。
     */
    private val _bleIconVisible = mutableStateOf(namingPrefs.getBoolean("ble_key_icon_visible", true))
    val bleIconVisible: State<Boolean> get() = _bleIconVisible

    fun setBleIconVisible(visible: Boolean) {
        _bleIconVisible.value = visible
        namingPrefs.edit().putBoolean("ble_key_icon_visible", visible).apply()
    }

    /**
     * 首页顶部「玩法说明」图标是否显示（个性化 → 首页图标）。
     *
     * 默认显示：新用户第一次进来最缺的就是「这功能在哪儿」，
     * 老用户嫌挤可以在个性化里关掉，关掉只是少一个入口，页面本身还在。
     */
    private val _tipIconVisible = mutableStateOf(namingPrefs.getBoolean("tip_icon_visible", true))
    val tipIconVisible: State<Boolean> get() = _tipIconVisible

    fun setTipIconVisible(visible: Boolean) {
        _tipIconVisible.value = visible
        namingPrefs.edit().putBoolean("tip_icon_visible", visible).apply()
    }

    /**
     * 当前桌面图标样式（[LauncherIcon.STYLE_STEERING] / [LauncherIcon.STYLE_OFFICIAL]）。
     *
     * 真值存在 PackageManager 的组件状态里，这里只是给界面看的快照；
     * 切换成功后要重启 App 才完全生效（见 [setLauncherIcon]）。
     */
    private val _launcherIcon = mutableStateOf(LauncherIcon.current(appContext))
    val launcherIcon: State<String> get() = _launcherIcon

    /** 切换桌面图标。返回是否写成功；成功后需要用户重启 App。 */
    fun setLauncherIcon(style: String): Boolean {
        val ok = LauncherIcon.apply(appContext, style)
        if (ok) {
            sessionStore.saveLauncherIconStyle(style)
            _launcherIcon.value = style
        }
        return ok
    }

    /**
     * 「设置 → 高级设置」里的总开关：开启后「个性化」才会出现「应用图标」入口。
     *
     * 默认关闭 —— 官方 logo 那套图标只给自己人用，平时不摆在明面上。
     */
    private val _iconSwitcherVisible = mutableStateOf(sessionStore.loadIconSwitcherVisible())
    val iconSwitcherVisible: State<Boolean> get() = _iconSwitcherVisible

    fun setIconSwitcherVisible(visible: Boolean) {
        _iconSwitcherVisible.value = visible
        sessionStore.saveIconSwitcherVisible(visible)
    }

    /**
     * 是否在界面上显示实时帧率浮层（设置 → 显示）。
     *
     * 默认关闭。开着的时候右上角会有一枚小药丸显示 `帧率 · 屏幕刷新率`，
     * 用来判断「卡」到底是掉帧还是屏幕本来就只有 60Hz。
     */
    private val _frameStatsVisible = mutableStateOf(namingPrefs.getBoolean("show_frame_stats", false))
    val frameStatsVisible: State<Boolean> get() = _frameStatsVisible

    fun setFrameStatsVisible(visible: Boolean) {
        _frameStatsVisible.value = visible
        namingPrefs.edit().putBoolean("show_frame_stats", visible).apply()
    }

    /**
     * 首页卡片顺序 / 显隐（编辑态长按拖动、删除、添加后持久化在这里）。
     *
     * 放在 namingPrefs：跟着本机走、登出不丢。存储内容就是 [HomeCardId] 名字的逗号串，
     * 空串表示用户把卡片全删了（不能回退成默认，否则删不干净）。
     */
    private val _homeCards = mutableStateOf(
        run {
            // 版本号用于「这个版本新增了卡片」的补入：老用户存过的列表里没有新卡，
            // 只有靠版本差才能知道该不该补（用户主动删掉的卡，版本已是最新，不会自己回来）。
            val savedVersion = namingPrefs.getInt("home_cards_v", 0)
            val parsed = HomeCardLayout.parse(
                namingPrefs.getString("home_cards", null),
                savedVersion,
            )
            if (savedVersion < HomeCardLayout.VERSION) {
                namingPrefs.edit()
                    .putString("home_cards", HomeCardLayout.serialize(parsed))
                    .putInt("home_cards_v", HomeCardLayout.VERSION)
                    .apply()
            }
            parsed
        },
    )
    val homeCards: State<List<HomeCardId>> get() = _homeCards

    fun setHomeCards(cards: List<HomeCardId>) {
        val clean = HomeCardLayout.parse(HomeCardLayout.serialize(cards))
        _homeCards.value = clean
        namingPrefs.edit()
            .putString("home_cards", HomeCardLayout.serialize(clean))
            .putInt("home_cards_v", HomeCardLayout.VERSION)
            .apply()
    }

    fun customName(): String =
        namingPrefs.getString("name_${session.selectedVin}", "")?.trim().orEmpty()

    fun setCustomName(name: String) {
        namingPrefs.edit().putString("name_${session.selectedVin}", name.trim()).apply()
    }

    fun displayName(): String = customName()
        .ifEmpty { session.selectedNickname.trim() }
        .ifEmpty { "我的零跑" }

    // 控车二次确认（应用内弹窗确认，防误触）：默认开启
    private val securityPrefs = appContext.getSharedPreferences("leapcontrol_security", Context.MODE_PRIVATE)

    /** 主界面向车端下发控车指令前，是否需要二次确认（应用内弹窗，默认开启）。 */
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

    // ---------------- 语音识别（百度短语音，REST） ----------------

    /**
     * 百度语音的 API Key + Secret Key。
     *
     * 任一项为空就返回 null —— 此时语音走设备自带的 `SpeechRecognizer`，
     * 只是国产 ROM 上基本不可用，所以正常使用需要把这两项填上。
     */
    /**
     * 百度语音凭据：用户自己填的优先，没填就用内置的。
     *
     * 内置之后语音记账开箱可用，不需要先到设置里配 Key；返回类型仍是可空的，
     * 只是当前实现不会返回 null（保留类型是为了后续可能改成「不内置」的构建）。
     */
    fun baiduAsrKeys(): Pair<String, String> {
        val api = sessionStore.loadBaiduAsrApiKey().orEmpty()
            .ifBlank { BaiduAsr.BUILTIN_API_KEY }
        val secret = sessionStore.loadBaiduAsrSecretKey().orEmpty()
            .ifBlank { BaiduAsr.BUILTIN_SECRET_KEY }
        return api to secret
    }

    /** 用户是否自己填过 Key（设置页据此显示「正在用内置凭据」还是「已自定义」）。 */
    fun hasCustomBaiduAsr(): Boolean {
        val api = sessionStore.loadBaiduAsrApiKey().orEmpty()
        val secret = sessionStore.loadBaiduAsrSecretKey().orEmpty()
        return api.isNotBlank() && secret.isNotBlank()
    }

    fun setBaiduAsrKeys(apiKey: String, secretKey: String) {
        sessionStore.saveBaiduAsrKeys(apiKey, secretKey)
    }

    /** 内置凭据始终可用，这个判断恒为真；保留方法是为了调用方语义清晰。 */
    fun hasBaiduAsr(): Boolean = true

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
        // 动力类型的「手动指定」是按 VIN 存的，换车必须跟着换，否则会沿用上一辆车的口径
        _powerTypeOverride.value = loadManualPowerType(vehicle.vin)
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
        // 自定义车模同理：图片文件本身留在磁盘（重新登录同一账号还能接着用），
        // 但内存里那份必须丢掉，否则退出后换账号会先闪一下上一辆车的车模
        customCarCache = null
        customCarCacheVin = null
        _carModelRevision.value += 1
        runCatching { statusPrefs.edit().clear().apply() }
    }

    // ---------------- 车辆配置识别（冰箱 / 天窗 / 哨兵 / 座椅等） ----------------

    private val capabilityPrefs = appContext.getSharedPreferences("leapcontrol_capabilities", Context.MODE_PRIVATE)

    private val _capabilities = MutableStateFlow(VehicleCapabilities.EMPTY)
    /** 当前车辆已识别到的可选配置（粘滞 + 持久化）。 */
    val capabilities: StateFlow<VehicleCapabilities> = _capabilities.asStateFlow()

    // ---------------- 版本更新检测（GitHub Releases） ----------------

    private val updatePrefs = appContext.getSharedPreferences("leapcontrol_update", Context.MODE_PRIVATE)

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    /** 检测到的可用更新（无更新 / 检测失败时为空）。设置页红点据此显示。 */
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    /** 当前已安装的版本号（取自构建配置，避免与 build.gradle.kts 脱节）。 */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /** 用户选择「不再提醒」的版本（空表示从未忽略）。 */
    fun dismissedVersion(): String? = updatePrefs.getString("dismissed_version", null)
    fun setDismissedVersion(version: String) {
        updatePrefs.edit().putString("dismissed_version", version).apply()
    }

    /**
     * 拉取 GitHub 最新 Release 并与当前版本比较。
     *
     * 返回三态而不是单纯的「有无更新」：检测失败时必须让调用方能区别对待，
     * 否则界面会把「网络不通」也显示成「已是最新」，用户无从判断功能是否正常。
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val latest = runCatching { GitHubUpdate.latestRelease() }.getOrNull() ?: run {
            _updateInfo.value = null
            return@withContext UpdateCheckResult.Failed
        }
        val newer = runCatching { GitHubUpdate.isNewer(latest.version, currentVersion()) }.getOrDefault(false)
        if (newer) {
            _updateInfo.value = latest
            UpdateCheckResult.Newer(latest)
        } else {
            _updateInfo.value = null
            UpdateCheckResult.UpToDate
        }
    }

    /** 跳转到外部链接（GitHub 发布页 / APK 下载）。 */
    /** 用系统浏览器打开外链。返回 false 表示没有 App 能接管（比如设备上没装浏览器）。 */
    fun openExternal(url: String): Boolean = runCatching {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrDefault(false)

    /**
     * 下载更新包到本地。
     *
     * 仓库已私有，网页直链用浏览器打不开（要 GitHub 登录态），所以走 GitHub API
     * 的附件接口 + 构建时注入的只读令牌。结果用 [Result] 返回，由调用处给用户看错误文案。
     */
    suspend fun downloadUpdate(
        info: AppUpdateInfo,
        onProgress: (ApkUpdater.Progress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching { ApkUpdater.download(appContext, info, onProgress) }
    }

    /**
     * 唤起系统安装器。
     *
     * 返回 false 有两种可能：文件不在了，或者系统还没允许本应用「安装未知应用」
     * （此时 [ApkUpdater.install] 已经跳到授权页，用户回来再点一次即可）。
     */
    fun installUpdate(file: File): Boolean = ApkUpdater.install(appContext, file)

    /** 当前是否已获得「安装未知应用」授权。 */
    fun canInstallUpdate(): Boolean = ApkUpdater.canInstall(appContext)

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

    // ---- 周能耗（首页两张能耗卡）----
    // 两个接口：近 6 周百公里能耗与排行、上周能耗构成。
    // 和里程能耗一样：失败静默保留上一次结果，不拖累别的卡片。
    private val _weeklyEnergy = MutableStateFlow<WeeklyEnergyData?>(null)
    val weeklyEnergyFlow: StateFlow<WeeklyEnergyData?> = _weeklyEnergy.asStateFlow()

    init {
        syncCapabilities()
        // 冷启动先用上一次的车况把界面填上，联网刷新再覆盖（不留空白帧）
        loadCachedStatus()
        // 里程 / 能耗同样先上缓存，联网拉取在首页进入时触发
        loadCachedMileageEnergy()
        loadCachedWeeklyEnergy()
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

    // ---------------- 周能耗（近 6 周百公里能耗 / 上周能耗构成）----------------

    private fun weeklyEnergyCacheKey(vin: String) = "weekly_$vin"

    /** 用磁盘缓存预填 [weeklyEnergyFlow]，冷启动时两张能耗卡不空一帧。 */
    fun loadCachedWeeklyEnergy(vin: String = session.selectedVin) {
        val raw = runCatching { energyPrefs.getString(weeklyEnergyCacheKey(vin), null) }.getOrNull()
        if (raw.isNullOrBlank()) {
            _weeklyEnergy.value = null
            return
        }
        _weeklyEnergy.value = runCatching { WeeklyEnergyData.fromJson(JSONObject(raw)) }.getOrNull()
    }

    /**
     * 拉取周能耗（15 分钟内不重复请求）。
     *
     * 两个接口各自 runCatching：排行接口挂了不影响能耗构成，反之亦然；
     * 两边都没拿到新值就保留上一次结果（可能是磁盘缓存），UI 显示「--」。
     */
    suspend fun refreshWeeklyEnergy(force: Boolean = false): WeeklyEnergyData? =
        withContext(Dispatchers.IO) {
            val vin = session.selectedVin
            if (vin.isBlank()) return@withContext null
            val cached = _weeklyEnergy.value
            val now = System.currentTimeMillis()
            if (!force && cached != null && now - cached.capturedAt < MILEAGE_ENERGY_TTL_MS) {
                return@withContext cached
            }
            if (!isNetworkAvailable()) return@withContext cached
            val api = LeapmotorApi(session)
            var summary: WeeklyEnergyData? = null
            runCatching { withNetworkRetry { api.getLastNWeeks100kmEcAndRank() } }
                .onSuccess { resp ->
                    runCatching { WeeklyEnergyParser.parseRank(resp) }
                        .onSuccess { summary = WeeklyEnergyData(rank = it) }
                }
            runCatching { withNetworkRetry { api.getLastWeekEc() } }
                .onSuccess { resp ->
                    runCatching { WeeklyEnergyParser.parseComposition(resp) }
                        .onSuccess {
                            summary = summary?.let { s -> s.copy(composition = it) }
                                ?: WeeklyEnergyData(composition = it)
                        }
                }
            val fresh = summary
            val result = when {
                fresh == null -> cached
                else -> fresh.copy(capturedAt = now).let { newly ->
                    // 用上一次的结果补齐另一张卡（这次只成功了一个接口时）
                    cached?.merge(newly) ?: newly
                }
            }
            if (result != null && result.hasAny) {
                _weeklyEnergy.value = result
                runCatching {
                    energyPrefs.edit()
                        .putString(weeklyEnergyCacheKey(vin), result.toJson().toString())
                        .apply()
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
        // 增程车：**有油**才算（燃油续航信号，或 fuelSoc > 0），再加上车型名与用户手动指定。
        // 早先的判据是「fuelSoc 字段存在即增程」—— 纯电车也会下发 3235 = 0，于是纯电 C01
        // 被认成增程：主续航去取综合续航（纯电没有这一路）→ 「--」，界面还多出一行「油 0%」。
        val configured = _powerTypeOverride.value
        val isRev = VehiclePowerTypeResolver.isRangeExtender(
            status = st,
            configuredPowerType = configured,
            carType = session.selectedCarType,
        )
        val range = if (isRev) {
            VehicleStatusMapper.combinedRange(st)
                ?: VehicleStatusMapper.remainingRange(st, session.selectedCarType, configured.toStatusPowerType())
        } else {
            VehicleStatusMapper.remainingRange(st, session.selectedCarType, configured.toStatusPowerType())
        }
        // acSetting 是一个 JSON 串，解析一次给下面几个字段共用（以前每个字段各解析一次）
        val acSetting = parseAcSetting(st)
        // 充电读数的真实信号：1177 batteryVoltage / 1178 batteryCurrent / 1182 minBatteryTemp。
        // 旧实现按 "chargeVoltage" / "batteryTemp" 这类「看起来像」的键名找，车端一个都不发 →
        // 充电中心三个读数永远是「--」。现在以命名信号为准，并保留别名字段兼容不同车型。
        val batteryVoltageRaw = pick(st, "batteryVoltage", "1177", "chargeVoltage", "bmsVoltage")
        val batteryCurrentRaw = pick(st, "batteryCurrent", "1178", "chargeCurrent", "bmsCurrent")
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
            locked = VehicleSignals.locked(st),
            acOn = parseAcOn(st),
            tires = parseTires(st),
            latitude = parseLocation(st).first,
            longitude = parseLocation(st).second,
            acTargetTemp = acSetting["temperature"],
            acWindLevel = acSetting["windlevel"],
            acCircle = acSetting["circle"]?.let {
                when (it) { "in", "1" -> "内循环"; "out", "0" -> "外循环"; else -> null }
            },
            acPosition = acSetting["position"]?.trim()?.takeIf {
                it == "all" || it == "wshld"
            },
            acMode = acSetting["mode"]?.trim()?.takeIf {
                it in com.leapcontrol.app.Commands.AC_MODES
            },
            trunkOpen = parseTrunkOpen(st),
            windowOpen = parseWindowOpen(st, session.selectedCarType),
            sunshadeOpen = parseSunshadeOpen(st),
            drivingStatus = parseDrivingStatus(st),
            speed = st.opt("speed")?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            gearStatus = formatGear(st.opt("gearStatus")),
            isDriving = runCatching { VehicleSignals.isDriving(st) }.getOrNull(),
            isShutDown = VehicleHomeStatus.isVehicleShutDown(
                bcmKeyPositionOn3 = st.opt("bcmKeyPositionOn3"),
                vehicleState = st.opt("vehicleState"),
                parkingBrakeState = st.opt("parkingBrakeState"),
                locked = VehicleSignals.locked(st),
            ),
            chargeState = ChargeStatus.state(st),
            healthyChargeEnabled = parseOnOff(pick(st, "healthyChargeEnabled", "48")),
            // 车端没有独立的充电功率信号，官方也是「电压 × 电流」推算（未充电时电流≈0 → 0 kW，
            // 界面在这种状态下显示「未充电」，不显示 0.0 kW）
            chargingPower = formatValue(pick(st, "chargingPower", "chargePower", "chargePowerKw"), "kW")
                ?: ChargeStatus.electricalPower(batteryVoltageRaw, batteryCurrentRaw),
            chargeTimeLeft = parseChargeTimeLeft(st),
            chargeVoltage = formatValue(batteryVoltageRaw, "V"),
            // 充电时 BMS 上报的电流是负数（-8.299），方向对用户没意义，界面统一显示绝对值
            chargeCurrent = formatValue(absoluteCurrent(batteryCurrentRaw), "A"),
            batteryTemp = formatValue(
                pick(st, "minBatteryTemp", "1182", "batteryTemp", "batteryTemperature", "packTemp"),
                "°C",
            ),
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
            val pressure = formatPressureKpa(st.opt(pressureKey))
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
        if (!VehicleSignals.hasWindowTelemetry(st, carType)) return null
        return VehicleSignals.openWindowLabels(st, carType).isNotEmpty()
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
            locked = VehicleSignals.locked(st),
        )
        VehicleHomeStatus.resolveDetailedDrivingState(
            gearStatus = formatGear(st.opt("gearStatus")),
            speed = st.opt("speed")?.toString(),
            isDriving = runCatching { VehicleSignals.isDriving(st) }.getOrNull(),
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

    /**
     * 胎压统一折算成 **kPa 整数**、不带单位（单位由展示层拼「kPa」）。
     * 2.4 bar → "240"。归一规则与阈值见 [com.leapcontrol.app.TirePressure]。
     *
     * 全部界面（首页胎压卡 / 胎压详情页 / 体检）都吃这个字符串，所以这里必须是 kPa：
     * 体检的阈值 [com.leapcontrol.app.ui.VehicleHealthDiagnostics] 本来就是按 kPa 写的，
     * 之前这里输出 bar（"2.3"），体检会把它当成 2.3 kPa 而一直判「胎压力偏低」。
     */
    private fun formatPressureKpa(value: Any?): String? =
        com.leapcontrol.app.TirePressure.formatKpa(value)

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

    /** 电流的负号只表示方向（充电时为负），界面统一取绝对值。 */
    private fun absoluteCurrent(value: Any?): Any? {
        if (value == null || value == JSONObject.NULL) return null
        val text = value.toString().trim().removeSuffix("A").trim().removePrefix("-").trim()
        return text.toDoubleOrNull() ?: value
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

    // ---------------- 背景主题 ----------------
    //
    // 主题按「明暗」分成两个槽位：浅色槽存浅色主题，深色槽存深色主题。
    // 外观模式（跟随系统 / 始终浅色 / 始终深色）只决定「现在取哪个槽」，
    // 具体取哪套由 ThemeResolver 统一推导，这里只负责读写偏好。

    /**
     * 浅色槽里的主题。
     *
     * 旧版本只存过一个 `season_theme`，它可能是浅色主题也可能是夜间主题
     * （那时「选夜间主题」是直接写进这一格的）；升级后若是夜间主题，
     * 说明用户本来就在用深色，把它让给深色槽，浅色槽回到默认的日间主题。
     */
    fun lightThemeId(): String {
        val raw = namingPrefs.getString("light_theme", null)
            ?: namingPrefs.getString("season_theme", null)
        return ThemeResolver.safeLight(raw).id
    }

    fun setLightThemeId(id: String) {
        val safe = ThemeResolver.safeLight(id)
        namingPrefs.edit().putString("light_theme", safe.id).apply()
    }

    /** 深色槽里的主题，默认「浩瀚星空」。 */
    fun darkThemeId(): String {
        val raw = namingPrefs.getString("dark_theme", null)
            ?: namingPrefs.getString("night_theme", null)
            ?: namingPrefs.getString("season_theme", null)
        return ThemeResolver.safeDark(raw).id
    }

    fun setDarkThemeId(id: String) {
        val safe = ThemeResolver.safeDark(id)
        namingPrefs.edit().putString("dark_theme", safe.id).apply()
    }

    // ---------------- 「我的」页头像 ----------------

    /**
     * 头像选择，存成 `preset:<下标>` 或 `custom` 的可读字符串。
     *
     * 这里只存「选择」本身；自定义照片的文件由 [AvatarStore] 管，
     * 所以换头像不改图片文件，删图片也不动偏好——两边各管一件事。
     */
    fun avatarChoiceRaw(): String =
        namingPrefs.getString("avatar_choice", AvatarChoice.DEFAULT_RAW)
            ?: AvatarChoice.DEFAULT_RAW

    fun setAvatarChoiceRaw(raw: String) {
        namingPrefs.edit().putString("avatar_choice", raw).apply()
    }

    /**
     * 自定义头像的内存热缓存。
     *
     * 和车模图一个道理：MainScreen 用 `when(tab)` 切页，切回「我的」时整棵子树重建、
     * remember 全丢。只靠页面里的 remember，自定义头像会先闪一下内置头像再跳回来。
     * 挂到 AppRuntime 上，切页时能同步拿到，第一帧就是对的。
     */
    private var customAvatarCache: Bitmap? = null

    /** 已解码的自定义头像（同步、可能为 null）。 */
    fun cachedCustomAvatar(): Bitmap? = customAvatarCache?.takeIf { !it.isRecycled }

    /** 读自定义头像，优先用缓存。 */
    suspend fun customAvatar(): Bitmap? = withContext(Dispatchers.IO) {
        cachedCustomAvatar() ?: AvatarStore.load(appContext)?.also { customAvatarCache = it }
    }

    /** 换过照片后强制重读（缓存里还是旧图）。 */
    suspend fun reloadCustomAvatar(): Bitmap? = withContext(Dispatchers.IO) {
        val loaded = AvatarStore.load(appContext)
        customAvatarCache = loaded
        loaded
    }

    /** 删掉自定义头像，连同缓存一起清。 */
    fun clearCustomAvatar() {
        customAvatarCache = null
        AvatarStore.clear(appContext)
    }

    // ---------------- 相伴卡背景 ----------------

    fun ownershipBackdropId(): String = _ownershipBackdrop.value

    fun setOwnershipBackdropId(id: String) {
        val safe = OwnershipBackdrops.byId(id).id
        _ownershipBackdrop.value = safe
        namingPrefs.edit().putString("ownership_backdrop", safe).apply()
    }

    /**
     * 相伴卡小白点：随机换一张相伴卡背景。
     *
     * 和主题那套随机（ThemeResolver.randomSwitch）是两条独立的线：
     * 点这张卡上的点只换这张卡的图，不改外观模式、不改主题。
     */
    fun randomizeOwnershipBackdrop() {
        setOwnershipBackdropId(OwnershipBackdrops.randomOther(_ownershipBackdrop.value))
    }

    // ---------------- 首页快捷切换（小白点 / 点击切换 / 关闭） ----------------

    fun setQuickSwitchMode(mode: QuickSwitchMode) {
        updateQuickSwitch { it.copy(mode = mode) }
        namingPrefs.edit().putString("quick_switch_mode", mode.name).apply()
    }

    fun setCarQuickDotEnabled(enabled: Boolean) {
        updateQuickSwitch { it.copy(carDot = enabled) }
        namingPrefs.edit().putBoolean("quick_dot_car", enabled).apply()
    }

    fun setCardQuickDotEnabled(enabled: Boolean) {
        updateQuickSwitch { it.copy(cardDot = enabled) }
        namingPrefs.edit().putBoolean("quick_dot_card", enabled).apply()
    }

    /** 只改内存里的快照，落盘由调用方按字段写（避免每次改一项就整份覆盖）。 */
    private fun updateQuickSwitch(block: (QuickSwitchPrefs) -> QuickSwitchPrefs) {
        _quickSwitch.value = block(_quickSwitch.value)
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

    // ---------------- 自定义车模（内置换色 / 相册导入） ----------------
    //
    // 官方接口只收 VIN，图是按车辆档案渲染好的定妆照，客户端没有任何「选颜色」的入参，
    // 所以换色只能在自己这边做：拿官方 PNG 重着色，或者直接导入用户自己在外面改好的图。
    // 两类结果都存成「自定义车模」，按 VIN 分文件，官方图永远保留 —— 删掉自定义就回到官方。

    /**
     * 自定义车模的内存热缓存，和官方图分开存两份。
     *
     * 分开缓存的原因：设置页「恢复官方车模」要能立刻回落，不必重新下载；
     * 而且用户切到别处再回首页，第一帧就该是改过色的那张（和车模掉帧那个老问题同一个道理）。
     */
    private var customCarCache: Bitmap? = null
    private var customCarCacheVin: String? = null

    /** 自定义车模改动计数：首页 collect 它决定什么时候重新取图。 */
    private val _carModelRevision = mutableStateOf(0)
    val carModelRevision: State<Int> get() = _carModelRevision

    fun cachedCustomCarBitmap(): Bitmap? =
        customCarCache?.takeIf { !it.isRecycled && customCarCacheVin == session.selectedVin }

    fun hasCustomCarModel(): Boolean = CarModelStore.exists(appContext, session.selectedVin)

    suspend fun customCarModel(): Bitmap? = withContext(Dispatchers.IO) {
        cachedCustomCarBitmap()?.let { return@withContext it }
        val vin = session.selectedVin
        if (vin.isBlank()) return@withContext null
        val loaded = CarModelStore.load(appContext, vin) ?: return@withContext null
        val fixed = runCatching { repairCustomCarModel(loaded, vin) }.getOrNull() ?: loaded
        customCarCache = fixed
        customCarCacheVin = vin
        fixed
    }

    /** 「这个 VIN 的历史遗留已经处理过」的标记。 */
    private fun carModelMigratedKey(vin: String) = "car_model_migrated_$vin"

    /**
     * 修复历史遗留的车模图，**每个 VIN 只做一次**，之后永不改写用户的车模文件。
     *
     * 以前这里每次启动都会拿官方图重算一遍比例并覆盖落盘。官方图的缓存、尺寸、取景
     * 任何一点变化都会让结果跟着变，表现出来就是用户说的「每次更新之后车模就被切了，
     * 得重新导入」——文件被反复重采样、反复重摆，最后贴着画布边像被切过。
     *
     * 现在只做两件事，做完打标记：
     *  1. 成品已经顶到画布边（被裁过）：有原始导入图就用它重摆一遍；老数据没存过原始图，
     *     就先就地备份一份，以后还有机会救；
     *  2. 老版本导入的图（车比官方大一圈、贴边）按官方比例摆一次。
     */
    private suspend fun repairCustomCarModel(custom: Bitmap, vin: String): Bitmap {
        // ---- 1) 被裁过：用原始导入图重摆 ----
        if (CarModelStore.isClipped(custom)) {
            val raw = CarModelStore.loadRaw(appContext, vin)
            if (raw != null) {
                val official = runCatching { fetchCarImage() }.getOrNull()
                val repaired = official?.let { CarModelStore.alignToTemplate(raw, it) }
                // 重摆后仍然顶边 → 原图本身就贴着画布边，接受它，别再反复重采样掉画质
                if (repaired != null &&
                    !CarModelStore.isClipped(repaired) &&
                    CarModelStore.save(appContext, vin, repaired)
                ) {
                    return repaired
                }
            } else {
                CarModelStore.saveRaw(appContext, vin, custom)
            }
        }

        // ---- 2) 一次性迁移 ----
        if (namingPrefs.getBoolean(carModelMigratedKey(vin), false)) return custom
        val official = runCatching { fetchCarImage() }.getOrNull() ?: return custom
        val fixed = CarModelStore.normalizeToTemplate(custom, official) ?: return custom
        if (fixed === custom) {
            namingPrefs.edit().putBoolean(carModelMigratedKey(vin), true).apply()
            return custom
        }
        // 迁移完反而顶到画布边了（极端比例 / 模板本身就贴边）：不落盘，
        // 宁可维持现状，也不能把用户唯一的一份图改坏。
        if (CarModelStore.isClipped(fixed)) {
            namingPrefs.edit().putBoolean(carModelMigratedKey(vin), true).apply()
            return custom
        }
        return if (CarModelStore.save(appContext, vin, fixed)) {
            namingPrefs.edit().putBoolean(carModelMigratedKey(vin), true).apply()
            fixed
        } else {
            custom
        }
    }

    /** 当前该显示哪张车模：改过色就用自定义的，否则用官方图。 */
    suspend fun displayCarBitmap(force: Boolean = false): Bitmap? =
        customCarModel() ?: fetchCarImage(force)

    /** 首页首帧用的同步版本（避免车模区先塌一下再回来）。 */
    fun cachedDisplayCarBitmap(): Bitmap? = cachedCustomCarBitmap() ?: cachedCarBitmap()

    /** 把一张已经改好色的图存成当前车辆的自定义车模。 */
    suspend fun adoptCarModel(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val vin = session.selectedVin
        if (vin.isBlank()) return@withContext false
        val ok = CarModelStore.save(appContext, vin, bitmap)
        if (ok) {
            // 原始图跟着一起换掉：不然这张新图和上一张导入图的「源」对不上，
            // 万一将来需要重摆，会拿旧源把车模重新摆回旧样子。
            CarModelStore.saveRaw(appContext, vin, bitmap)
            customCarCache = bitmap
            customCarCacheVin = vin
            _carModelRevision.value += 1
        }
        ok
    }

    /**
     * 从相册导入一张图作为自定义车模。
     *
     * 会先用官方图当模板做三件事：自动抠背景、裁掉空边、按官方车模的外框重新摆位
     * （详见 [CarModelStore.import]）。这样不管用户传进来什么比例的图，首页的大小和留白
     * 都跟官方那张一致。返回处理结果（含「是否抠了背景 / 是否对齐过」），失败返回 null。
     */
    suspend fun importCarModel(uri: Uri): CarModelStore.Imported? {
        val vin = session.selectedVin
        if (vin.isBlank()) return null
        // 官方图既当尺寸模板，也是「官方车模长什么样」的唯一依据；
        // 拿不到（离线且没缓存）时退化成只抠图 + 裁边，至少不会崩。
        val template = runCatching { fetchCarImage() }.getOrNull()
        val result = CarModelStore.import(appContext, vin, uri, template) ?: return null
        customCarCache = result.bitmap
        customCarCacheVin = vin
        _carModelRevision.value += 1
        return result
    }

    /**
     * 用官方车模图生成一张换色版，并存为自定义车模。
     *
     * 全尺寸生成在 IO 线程做（1600px 的车模约 80 万像素，整块换色真机上 200ms 上下），
     * 生成完立刻落盘，之后首页直接用文件缓存，不再重复算。
     */
    suspend fun tintCarModel(targetArgb: Int, strength: Float): Boolean = withContext(Dispatchers.IO) {
        val official = fetchCarImage() ?: return@withContext false
        val tinted = CarModelStore.tint(official, targetArgb, strength)
        val vin = session.selectedVin
        if (vin.isBlank()) return@withContext false
        val ok = CarModelStore.save(appContext, vin, tinted)
        if (ok) {
            CarModelStore.saveRaw(appContext, vin, tinted)
            customCarCache = tinted
            customCarCacheVin = vin
            _carModelRevision.value += 1
        }
        ok
    }

    /** 恢复官方车模：只删自定义图与缓存，官方图仍在。 */
    fun clearCustomCarModel() {
        val vin = session.selectedVin
        if (vin.isNotBlank()) CarModelStore.clear(appContext, vin)
        customCarCache = null
        customCarCacheVin = null
        _carModelRevision.value += 1
    }

    /** 官方车模图的只读入口（外观页的预览、换色、导出都以它为准）。 */
    suspend fun officialCarModel(): Bitmap? = fetchCarImage()

    /**
     * 把当前车模（有自定义就导自定义）存进系统相册，方便用户拿去外部软件改色。
     *
     * Android 10+ 走 MediaStore，写 `Pictures/零控` 不需要任何权限。
     * 9 及以下没有 RELATIVE_PATH，写相册要存储权限，这里不申请，直接返回 false 由 UI 提示。
     */
    suspend fun exportCarModelToGallery(): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val bitmap = displayCarBitmap() ?: return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "LeapControl_车模_${System.currentTimeMillis()}.png",
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/零控")
            }
            val uri = appContext.contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
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

    /**
     * 一键关闭多项后台耗电功能（空调 / 座椅加热通风 / 方向盘加热 / 后视镜加热 / 冰箱）。
     *
     * 与 [control] 的关键区别：**不逐条等车端执行结果**。control 每条都要轮询 1~24 秒，
     * 五项排下来用户得干等半分钟以上；「全部关掉」本质上是一组操作，
     * 逐条发完让 UI 统一回拉一次车况来判断效果更实际。
     *
     * 两条指令之间留 [POWER_DRAIN_GAP_MS]：连发会撞网关的「系统繁忙」，
     * 后面的指令被丢掉时用户还以为关了（充电中心那次踩过同样的坑）。
     *
     * @param onProgress 已下发条数 / 总条数，用于按钮上显示「关闭中 2/5」。
     */
    suspend fun shutDownPowerDrains(
        commands: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val unique = commands.distinct()
        if (unique.isEmpty()) return@withContext "没有正在耗电的功能"
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        val api = LeapmotorApi(session)
        val failed = mutableListOf<String>()
        unique.forEachIndexed { index, name ->
            runCatching {
                val command = Commands.build(name)
                withNetworkRetry { api.sendControl(command, pin) }
            }.onFailure {
                // 记指令名而不是异常信息：异常文案对用户没意义，指令名能直接定位是哪一项
                failed += name
            }
            onProgress(index + 1, unique.size)
            if (index != unique.lastIndex) Thread.sleep(POWER_DRAIN_GAP_MS)
        }
        val ok = unique.size - failed.size
        when {
            failed.isEmpty() -> "已关闭 $ok 项"
            ok == 0 -> "关闭失败（${failed.joinToString("、")}）"
            else -> "已关闭 $ok 项，${failed.size} 项未成功"
        }
    }

    /** 结果轮询等待得到的网关结果码（优先 result，其次 code）。 */
    private fun firstResultCode(resp: JSONObject): String? {        val value = (resp.opt("result") ?: resp.opt("code"))
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
     * 下发充电中心设置，三条指令各自独立、逐项汇报结果：
     * 1. 健康充电 + 充电上限 → `healthyCharging/control`（state + soc，不需要控车密码）
     * 2. 预约充电 → `cmdid=190` 充电计划（需要旧会话凭证，有控车密码则带上）
     * 3. 预约电池预热 → `cmdid=161` PTC 加热计划（同上）
     *
     * 三个容易被漏掉的细节：
     * - **充电上限只有一个值**。190 的 `chargesoc` 与健康充电的 `targetSoc` 必须是同一个数字，
     *   以前分成两个参数（滑杆 100、预约 80）会让最后下发的那条把前一条覆盖掉。
     * - **`cycles` 必须带上用户选的重复日**，不传就是全周默认值，用户改了星期等于没生效。
     * - **161 要等 1.5 秒**：连着三条车控指令打同一个网关会撞「系统繁忙」，
     *   官方实现里也是隔着 1.5s 单独发预热（[PreheatGateDelayMs]）。
     */
    suspend fun applyChargingSettings(
        healthyCharge: Boolean,
        chargeLimit: Int,
        reserveCharge: Boolean,
        batteryPreheat: Boolean,
        scheduledStart: String = "23:00",
        scheduledEnd: String = "07:00",
        continueUntilLimit: Boolean = true,
        scheduledDays: Set<Int> = (1..7).toSet(),
        preheatStart: String = "07:30",
        preheatDays: Set<Int> = (1..7).toSet(),
    ): String = withContext(Dispatchers.IO) {
        val api = LeapmotorApi(session)
        val pin = sessionStore.loadOpPassword().orEmpty()
        val soc = ChargeSchedule.sanitizeSoc(chargeLimit)
        val results = mutableListOf<String>()

        // 1. 健康充电（含充电上限）
        runCatching { api.setHealthyCharging(enabled = healthyCharge, targetSoc = soc) }
            .onSuccess { results += successLabel("健康充电", it) }
            .onFailure { results += "健康充电下发失败" }

        // 2. 预约充电（190）：cycles 用 7 位 0/1 掩码，recharge 是「未达上限继续充电」
        runCatching {
            api.setScheduledCharging(
                enabled = reserveCharge,
                startTime = scheduledStart,
                endTime = scheduledEnd,
                targetSoc = soc,
                opPassword = pin,
                continueUntilLimit = continueUntilLimit,
                circulation = 1,
                cycles = ChargePlanCyclesHelper.toVehicleMask(scheduledDays),
            )
        }
            .onSuccess { results += successLabel("预约充电", it) }
            .onFailure { results += "预约充电下发失败" }

        // 3. 预约电池预热（161）：隔开一点再发，避免与上面两条抢同一把车控锁
        Thread.sleep(PreheatGateDelayMs)
        val preheatMask = ChargePlanCyclesHelper.toVehicleMask(preheatDays)
        runCatching {
            api.setScheduledBatteryPreheat(
                enabled = batteryPreheat,
                startTime = preheatStart,
                opPassword = pin,
                days = preheatMask,
            )
        }
            .onSuccess { results += successLabel("电池预热", it) }
            .onFailure { results += "电池预热下发失败" }

        results.joinToString("；")
    }

    /** 云端接口的 code / result 落在 0 或 200 才算车端受理。 */
    private fun successLabel(name: String, resp: JSONObject): String {
        val code = resp.optInt("code", resp.optInt("result", -1))
        return if (code == 0 || code == 200) "${name}已更新" else "${name}已提交（结果码 $code）"
    }

    /** 读取车端已保存的电池预热计划（cmdid=161 getappointment）。 */
    suspend fun loadBatteryPreheatSchedule(): ScheduledPreheatState? = withContext(Dispatchers.IO) {
        runCatching { LeapmotorApi(session).getScheduledBatteryPreheat() }.getOrNull()
    }

    private companion object {
        /** 161 与 190 之间的最小间隔（毫秒），见 [applyChargingSettings] 的说明。 */
        const val PreheatGateDelayMs = 1_500L
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
