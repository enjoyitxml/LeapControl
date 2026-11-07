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
import com.leapcontrol.app.AcLiveState
import com.leapcontrol.app.AmapPoi
import com.leapcontrol.app.ApkUpdater
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.ChargingControlRules
import com.leapcontrol.app.CarModel3DManager
import com.leapcontrol.app.ChassisPhotoQuery
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
import com.leapcontrol.app.ControlCommand
import com.leapcontrol.app.ControlPollBackoff
import com.leapcontrol.app.ControlResultPollingPolicy
import com.leapcontrol.app.FridgeControlCommand
import com.leapcontrol.app.FridgeMode
import com.leapcontrol.app.FridgeStyle
import com.leapcontrol.app.LeapmotorApi
import com.leapcontrol.app.MileageEnergyParser
import com.leapcontrol.app.OwnershipStats
import com.leapcontrol.app.ParkingPhotoStore
import com.leapcontrol.app.ParkingPhotoSource
import com.leapcontrol.app.ParkingPhotoSourceRules
import com.leapcontrol.app.OfficialParkingPhotoStore
import com.leapcontrol.app.OfficialPhotoStatus
import com.leapcontrol.app.OfficialThumbMemory
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.trip.TripCloseResult
import com.leapcontrol.app.trip.TripMergeResult
import com.leapcontrol.app.trip.TripOverview
import com.leapcontrol.app.trip.TripRules
import com.leapcontrol.app.trip.TripSample
import com.leapcontrol.app.trip.TripStartResult
import com.leapcontrol.app.trip.TripTracker
import com.leapcontrol.app.ScheduledAcPlan
import com.leapcontrol.app.ScheduledAcState
import com.leapcontrol.app.ScheduledPreheatState
import com.leapcontrol.app.SentryModeControlPolicy
import com.leapcontrol.app.SteeringWheelHeat
import com.leapcontrol.app.ObfuscatedSecrets
import com.leapcontrol.app.QuickSwitchMode
import com.leapcontrol.app.QuickSwitchPrefs
import com.leapcontrol.app.Session
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ShumeiSecurityManager
import com.leapcontrol.app.TrunkState
import com.leapcontrol.app.TrunkStateMapper
import com.leapcontrol.app.UpdateCheckMode
import com.leapcontrol.app.UpdateCheckPolicy
import com.leapcontrol.app.UpdateCheckResult
import com.leapcontrol.app.journal.BaiduAsr
import com.leapcontrol.app.journal.ChargeCycle
import com.leapcontrol.app.journal.ChargeSample
import com.leapcontrol.app.journal.ChargeWatch
import com.leapcontrol.app.Vehicle
import com.leapcontrol.app.VehicleChargePlan
import com.leapcontrol.app.WeeklyEnergyData
import com.leapcontrol.app.WeeklyEnergyParser
import com.leapcontrol.app.VehicleLocationDiagnostics
import com.leapcontrol.app.VehicleLocationDomain
import com.leapcontrol.app.VehicleLocationStore
import com.leapcontrol.app.VehicleDatumDetector
import com.leapcontrol.app.CoordinateDatum
import com.leapcontrol.app.LocationAnchor
import com.leapcontrol.app.LocationAnchorRule
import com.leapcontrol.app.LocationCalibration
import com.leapcontrol.app.LocationCalibrationStore
import com.leapcontrol.app.LocationCalibrator
import com.leapcontrol.app.VehicleLocationAnchorStore
import com.leapcontrol.app.VehicleLocationRules
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 *
 * 配合 [ControlPollBackoff] 的递进节奏，这 20 秒里只会发出 5 次探询（原来 20 次）。
 */
private const val CONTROL_RESULT_MAX_WAIT_MS = 20_000L

/**
 * 空调（170）的回执窗口，比通用指令短得多。
 *
 * 空调是用户在首页最常点、也最在意「点了有没有反应」的一条：原来跟锁车一样等满 20 秒，
 * 期间首页开关一直处于在途状态、车况也不回拉，用户看到的就是「点了十几秒没动静」，
 * 于是反复点，每次又都多等一轮。
 *
 * 实测空调回执 1~5 秒就回；8 秒窗口内探询点是 1 / 3 / 6 秒（3 次），窗口到了还没回执
 * 就走 [AppRuntime.control] 里那段「回读车况再判定」的兜底，不再死等。
 */
private const val AC_RESULT_MAX_WAIT_MS = 8_000L

/**
 * 网关受理之后，**用车况信号自证「车端真的执行了」**的窗口。
 *
 * 结果码 0 / 200 只代表网关收下了这条指令：车端 T-Box 唤醒 + 执行还要 5~15 秒，
 * 而且执行完还可能被车端自己否掉（车窗防夹弹回、后备箱被挡住）。
 * 早先一拿到 0 就回「XX执行成功」，首页右上角跟着写「车辆已解锁」，
 * 车其实没动 —— 用户 2026-11-05 反馈的「假状态」就是这一条。
 *
 * 现在受理后再读几次车况：信号变成意图值才算成功；读得到但没变只说「已发出」。
 * 4 秒起步（车端最快也要这么久才上报），窗口内读 3 次，够覆盖 5~15 秒的常规节奏。
 */
private const val VERIFY_CONTROL_WINDOW_MS = 12_000L

/** 求证时两次读车况的间隔。 */
private const val VERIFY_CONTROL_GAP_MS = 4_000L

/** 空调执行快（1~5 秒），求证窗口短一些，别让首页那颗开关按死在在途状态。 */
private const val AC_VERIFY_WINDOW_MS = 6_000L

/**
 * 「前挡除霜」前置开机的等待时间。
 *
 * 除霜必须在空调运行中才生效，而车端对同一 cmdid 连发会回「系统繁忙」，
 * 所以先发开机、等车端把这条处理掉（实测 1~2 秒）再发除霜参数。
 * 短了会被限流丢掉除霜那条，长了用户要干等，1.5 秒是两者之间的取值。
 */
private const val DEFROST_PRECONDITION_MS = 1_500L

/** 除霜下发后回读除霜位的轮询间隔：车端从受理到把 1945 报上来要几秒，且常滞后于风量。 */
private const val DEFROST_CONFIRM_GAP_MS = 2_200L

/** 每组除霜参数回读几次（4 × 2.2 秒 ≈ 9 秒）。太短会把「上报慢」误判成「没执行」。 */
private const val DEFROST_CONFIRM_ROUNDS = 4

/** 换下一组除霜参数前的间隔：同 cmdid 连发太快会被网关当「系统繁忙」丢掉后一条。 */
private const val DEFROST_VARIANT_GAP_MS = 1_200L

/**
 * 方向盘加热下发后回读 1816 / 1624 的轮询间隔与次数（4 × 2.2 秒 ≈ 9 秒）。
 *
 * 车端从受理到把方向盘加热的状态报上来要几秒，比座椅更慢 —— 3 秒回读等于没读。
 */
private const val STEERING_CONFIRM_GAP_MS = 2_200L
private const val STEERING_CONFIRM_ROUNDS = 4

/** 「关」第一次发 0 没关掉、补发 1 之前的间隔：同 cmdid 连发太快会被网关当「系统繁忙」。 */
private const val STEERING_FALLBACK_GAP_MS = 1_200L

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
    /**
     * 坐标取不到时的一句话原因（[VehicleLocationDiagnostics]）。
     * 只在 debug 构建的找车页显示，用于用户拍照回报；正常有坐标时为 null。
     */
    val locationHint: String? = null,
    /**
     * **这条坐标是从什么时候起就是这个值**（毫秒）。
     *
     * 报文里的经纬度没有自己的时间戳，[lastUpdatedFlow] 又只是「我们拉取的时刻」，
     * 拿它当「位置更新于 x 前」会永远说着「刚刚」。这个字段由
     * [LocationAnchorRule] 按「坐标有没有变过」推出来：车在跑时它就是刚刚，
     * 车停了半天不动时它会一直往回长 —— 后者才是该提醒用户的情况。
     */
    val locationSeenAt: Long = 0L,
    /** 这次坐标取自哪一对信号（仅 debug 的找车页显示，用来排查取错键的情况）。 */
    val locationSource: String? = null,
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
    /**
     * 前挡除霜是否开启（信号 1945 windshieldDefrost）。
     *
     * 与 [acPosition] 是两个不同的东西：**除霜位**（wshld）负责化霜，
     * **出风方向**（position）只决定风从哪儿吹。车端可以「前挡出风但不除霜」，
     * 所以判断「除霜到底开没开」只能用这个字段，不能拿 position 顶替。
     * null = 车端没上报，UI 不高亮。
     */
    val windshieldDefrost: Boolean? = null,
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
    /**
     * 剩余充电分钟数（信号 1200 原值）。
     *
     * 与 [chargeTimeLeft] 是同一个信号的两种形态：那个给界面看，这个给
     * [com.leapcontrol.app.journal.ChargeWatch] 决定采样间隔 —— 快充满时加密采样，
     * 才抓得住「充到多少」的最后一格。
     */
    val chargeRemainMinutes: Int? = null,
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
    /**
     * 电池预热（PTC 电芯加热）是否正在工作。
     *
     * 信号 1186 `batteryThermalRequest`：4 = 正在加热，0 = 空闲。
     * 车端不给这两个值以外的状态，所以解析见 [com.leapcontrol.app.BatteryPreheatState]，
     * 认不出来就是 null（首页那颗按钮不高亮，也不瞎猜成「关」）。
     */
    val batteryPreheatOn: Boolean? = null,
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
    /**
     * 电量原始百分比（优先 preciseSoc 100003，其次 soc 1204），给行程能耗用。
     *
     * [soc] 是取整后的展示值，且只看 1204；部分车型只下发 100003，那样 [soc] 会变 0。
     * 行程靠首末电量差算耗电，0 会被当成「没读到」，所以这里单独留一份真实读数。
     */
    val socSignal: Double? = null,
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
     * 首页顶部「蓝牙钥匙」图标是否显示（个性化 → 首页图标）。
     *
     * 默认显示：这是车主的常用入口，藏起来要翻两层设置才找得到。
     * 但蓝牙钥匙靠手机与车端直接通信，不是每辆车都带这套硬件，适配程度也参差 ——
     * 所以功能本身的风险说明放在两处常驻：个性化开关旁的小字、蓝牙钥匙页的「兼容性说明」。
     * 放在 namingPrefs 里，跟着本机走、登出不丢。
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
     * 零记「灵感球」停在哪个位置（个性化 → 灵感球）。
     *
     * 默认右下角：那块地方是拇指最容易够到的，也不压住卡片正文。
     * 想要对称、或者习惯左手的人可以在个性化里改成居中 / 左下。
     * 读出来是 Int（[OrbPosition]），存进 prefs，切完即时生效，不用重启。
     */
    private val _orbPosition = mutableStateOf(
        namingPrefs.getInt("journal_orb_pos", OrbPosition.RIGHT)
            .takeIf { OrbPosition.isValid(it) } ?: OrbPosition.RIGHT,
    )
    val orbPosition: State<Int> get() = _orbPosition

    fun setOrbPosition(position: Int) {
        if (!OrbPosition.isValid(position) || _orbPosition.value == position) return
        _orbPosition.value = position
        namingPrefs.edit().putInt("journal_orb_pos", position).apply()
    }

    /**
     * 首页主区用哪套布局（个性化 → 首页布局，见 [HeroLayout]）。
     *
     * 挂在 AppRuntime 而不是页面本地 remembered 状态：切 tab 会把首页整棵子树重建，
     * 本地状态会跟着丢，而这块恰好是「用户挑过的样子」，丢了就等于每次回来都跳回经典。
     */
    private val _heroLayout = MutableStateFlow(HeroLayoutPrefs.load(appContext))
    val heroLayout: StateFlow<HeroLayout> = _heroLayout.asStateFlow()

    fun setHeroLayout(layout: HeroLayout) {
        if (_heroLayout.value == layout) return
        HeroLayoutPrefs.save(appContext, layout)
        _heroLayout.value = layout
    }

    /**
     * 首页车模的行驶动效（个性化 → 车模外观 → 车辆行驶动效）。
     *
     * **默认关闭**：这是装饰，不能让所有人升级后就被动画打扰；老机器上每帧几十个粒子
     * 也不该是默认开销。参数按 VIN 存（轮位标定是贴着这张车模图做的，换车必须重标）。
     *
     * 挂 StateFlow 而不是页面本地状态：首页和设置页要同时看到同一份，
     * 设置页改完回首页必须立刻生效（切 tab 会重建首页子树）。
     */
    private val _carMotion = MutableStateFlow(
        CarMotionPrefs.load(appContext, session.selectedVin),
    )
    val carMotion: StateFlow<CarMotionSettings> = _carMotion.asStateFlow()

    /** 当前 StateFlow 是按哪个 VIN 读出来的；换车后要重新读，否则会沿用上一辆车的轮位标定。 */
    private var carMotionVin: String = session.selectedVin

    private fun syncCarMotionVin() {
        val vin = session.selectedVin
        if (vin == carMotionVin) return
        carMotionVin = vin
        _carMotion.value = CarMotionPrefs.load(appContext, vin)
    }

    fun carMotionSettings(): CarMotionSettings {
        syncCarMotionVin()
        return _carMotion.value
    }

    fun setCarMotion(settings: CarMotionSettings) {
        val clean = settings.normalized()
        if (_carMotion.value == clean) return
        CarMotionPrefs.save(appContext, session.selectedVin, clean)
        _carMotion.value = clean
    }

    /**
     * 是否开启「滑动性能日志」采集（设置 → 显示）。
     *
     * 默认关闭。开着的时候，每次首页上下滑动都会在 filesDir/perf/ 下留一份
     * 逐帧性能日志（帧间隔 / 滚动速度 / 掉帧 / 主线程耗时 / GPU 耗时），
     * 供排查滑动掉帧用。平时关着零开销。
     */
    private val _perfLogEnabled = mutableStateOf(namingPrefs.getBoolean("perf_log_enabled", false))
    val perfLogEnabled: State<Boolean> get() = _perfLogEnabled

    init {
        // 进程重启后把 prefs 里的开关同步到单例（PerfLogger 是进程级单例，不持久化）
        PerfLogger.enabled = _perfLogEnabled.value
    }

    fun setPerfLogEnabled(enabled: Boolean) {
        _perfLogEnabled.value = enabled
        namingPrefs.edit().putBoolean("perf_log_enabled", enabled).apply()
        PerfLogger.enabled = enabled
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
        // 重新登录成功：把「登录已失效」的标记撤掉，桌面卡片的「点此登录」才能回到车况
        ControlFailure.clearSessionExpired(appContext)
        val selected = vehicles.firstOrNull { it.vin == session.selectedVin } ?: vehicles.first()
        selectVehicle(selected)
        vehicles
    }

    suspend fun selectVehicle(vehicle: Vehicle) {
        // 换车的判定要在改 selectedVin 之前：换了一辆就必须丢掉上一辆的停车位置，
        // 否则新车还没报坐标的那几帧，首页会把**另一辆车**停的地方画出来并导航过去
        val switched = vehicle.vin != session.selectedVin
        session.selectedVin = vehicle.vin
        session.selectedCarType = vehicle.carType
        session.selectedNickname = vehicle.nickname.ifEmpty { vehicle.officialNickname }
        // 动力类型的「手动指定」是按 VIN 存的，换车必须跟着换，否则会沿用上一辆车的口径
        _powerTypeOverride.value = loadManualPowerType(vehicle.vin)
        withContext(Dispatchers.IO) { sessionStore.save(session) }
        if (switched) {
            withContext(Dispatchers.IO) {
                // 位置缓存 + 坐标锚点一起清。车位照片**不清**：那是用户自己拍的实物记录，
                // 换车时删掉属于丢用户数据；照片卡本身写着「拍摄于 x 前」，看得出生效时间
                VehicleLocationStore.clear(appContext)
                // 内存里那份也要清：留着的话，换车后第一次进首页会先亮上一辆车的地图（见 VehiclePlaceMemory）
                VehiclePlaceMemory.clear()
                ParkingThumbMemory.clear()
                // 车端照片是「上一辆车停在哪」，绝不能留给下一辆车看
                clearOfficialPhoto()
                resetLocationAnchor()
                // 校准是按 VIN 存的：换成新车要把这一辆自己的读出来。
                // 沿用上一辆的偏移会让好车看着像坏了 —— 两辆车的偏差根本没有关系
                _locationCalibration.value = LocationCalibrationStore.load(appContext, vehicle.vin)
                // 行驶动效同理：轮位标定是贴着上一辆车的车模图拖出来的，换车必须重读
                carMotionVin = vehicle.vin
                _carMotion.value = CarMotionPrefs.load(appContext, vehicle.vin)
                // 换车时把充电采样闹钟撤掉：它是给上一辆车的 VIN 排的，
                // 留着会拿新车的账号去读旧车的充电状态
                runCatching { ChargeWatch.cancel(appContext) }
                // 小组件里的车模也要换掉：留着的话桌面上还是上一辆车的图
                com.leapcontrol.app.widget.VehicleWidget.clearCarCache(appContext)
            }
        }
        // 换车后能力要重新按 VIN 加载，不能直接沿用上一辆的结果
        syncCapabilities()
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        sessionStore.clear()
        ControlFailure.clearSessionExpired(appContext)
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
        // 行驶动效回到默认（关闭）：换账号/登出后不能让首页继续画上一个人的车模特效
        _carMotion.value = CarMotionSettings.DEFAULT
        carMotionVin = ""
        runCatching { statusPrefs.edit().clear().apply() }
        // 停车位置缓存必须一起清：那个目录里存着车停在哪、旁边是什么地址，
        // 换账号后还留着，等于把上一位车主的行踪给了下一个人
        VehicleLocationStore.clear(appContext)
        // 历史行程比单个停车点更敏感（是一串行踪），登出必须整份清掉
        TripTracker.clear(appContext)
        // 内存里那份同理，而且要一起清：否则退出后重新登录，首页会先亮别人的位置（见 VehiclePlaceMemory）
        VehiclePlaceMemory.clear()
        resetLocationAnchor()
        // 校准也一起清：它记着「这辆车的位置要怎么挪」，换账号后不该留给下一个人
        LocationCalibrationStore.clear(appContext)
        _locationCalibration.value = LocationCalibration.NONE
        // 停车位照片同理，而且它更直接：照片里常能看到车牌、车位号、甚至车库门牌
        ParkingPhotoStore.clear(appContext)
        ParkingThumbMemory.clear()
        // 车端照片同样是这辆车的位置实景，连 VIN 记录一起清
        clearOfficialPhoto()
        // 充电采样闹钟也要撤：登出后它再响也只是个没登录的空转
        runCatching { ChargeWatch.cancel(appContext) }
        // 桌面小组件也要立刻变成「点此登录」，别把上一辆车的车况留在锁屏外
        com.leapcontrol.app.widget.VehicleWidget.clearCarCache(appContext)
        runCatching { com.leapcontrol.app.widget.VehicleWidget.render(appContext) }
    }

    /**
     * 登录已失效，用户选择「重新登录」：**只退会话，不动用户数据**。
     *
     * 和 [logout] 的区别很关键：位置缓存、停车位照片、位置校准都是用户自己攒下来的东西，
     * 只是 token 过期一次就全删了，重新登录同一账号等于白干一遍。
     * 这里只把会话和内存快照收掉，界面自然回到登录页（见 `LeapControlApp`）。
     */
    suspend fun endExpiredSession(): Unit = withContext(Dispatchers.IO) {
        sessionStore.clear()
        ControlFailure.clearSessionExpired(appContext)
        _status.value = null
        _lastUpdated.value = 0L
        // 车况快照不适用于「没登录」的状态；位置与照片**保留**
        runCatching { statusPrefs.edit().clear().apply() }
        runCatching { com.leapcontrol.app.widget.VehicleWidget.render(appContext) }
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

    // ---- 自动检测更新的档位（关闭 / 每次启动 / 7 天 / 14 天 / 30 天）----
    // 落盘的是档位的字符串 key 而不是枚举序号：以后加档位、调顺序都不会把老用户的选择读错。
    // 老用户没有这个键 → 走默认（7 天），与改版前「每次启动都查」相比更省流量，
    // 但绝不会因为读不到设置而彻底不查。

    /** 当前自动检测档位。首次读会把 0.2.54 的整数键迁移过来。 */
    fun updateCheckMode(): UpdateCheckMode {
        updatePrefs.getString(UpdateCheckPolicy.PREF_KEY, null)?.let {
            return UpdateCheckPolicy.fromKey(it)
        }
        // 0.2.54 存的是 "auto_check_days" 整数。那版发布过，不能直接丢掉它的选择。
        if (updatePrefs.contains(UpdateCheckPolicy.LEGACY_PREF_KEY)) {
            val migrated = UpdateCheckPolicy.fromDays(
                updatePrefs.getInt(UpdateCheckPolicy.LEGACY_PREF_KEY, UpdateCheckPolicy.DEFAULT.intervalDays),
            )
            updatePrefs.edit()
                .putString(UpdateCheckPolicy.PREF_KEY, migrated.key)
                .remove(UpdateCheckPolicy.LEGACY_PREF_KEY)
                .apply()
            return migrated
        }
        return UpdateCheckPolicy.DEFAULT
    }

    fun setUpdateCheckMode(mode: UpdateCheckMode) {
        updatePrefs.edit().putString(UpdateCheckPolicy.PREF_KEY, mode.key).apply()
    }

    /** 上次**成功**自动/手动检测的时间戳；0 = 从未成功检测过。 */
    fun lastUpdateCheckAt(): Long = updatePrefs.getLong("last_check_at", 0L)

    /**
     * 这次启动要不要自动联网检测。
     *
     * 手动点「检查更新」成功也会记时间，所以刚手动查过不会紧接着又自动查一次。
     */
    fun shouldAutoCheckUpdate(now: Long = System.currentTimeMillis()): Boolean =
        UpdateCheckPolicy.isDue(updateCheckMode(), lastUpdateCheckAt(), now)

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
        // 只有真的拿到 Release 才记时间：网络失败不记账，否则「连不上」会被当成
        // 「刚查过」，接下来一整周都不再重试。
        updatePrefs.edit().putLong("last_check_at", System.currentTimeMillis()).apply()
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

    /**
     * 最近一次读到的表显总里程（km，仅进程内）。
     *
     * 用途只有一个：「自上次充电行驶里程」卡在这种帧里缺 `totalMileage` 时不要整块消失。
     * 车况报文偶尔是**部分字段**的（车型没上报 / 网关只回了几路信号），那时卡片会从
     * 「128.4 km」直接变成「刷新车况后显示」—— 和「数字被清零」在用户眼里是一回事。
     * 拿上一帧的读数顶上，最多旧几十秒，比空白强。
     *
     * 刻意不落盘：跨天 / 冷启动时一个旧里程粘在卡片上，比空白更容易误导。
     */
    @Volatile
    var lastKnownOdometerKm: Double? = null
        private set

    /**
     * 当前位置校准（见 [LocationCalibration]），按 VIN 存，跟着车走。
     *
     * 放在这一层而不是 UI 状态里：首页卡、找车页地图、导航目的地、地址逆地理、
     * 桌面小组件读的都是 [buildStatus] 产出的那两个数字。只有在这里生效，
     * 「地图上的钉子和导航过去的点是不是同一个」才靠得住。
     */
    private val _locationCalibration = MutableStateFlow(
        LocationCalibrationStore.load(appContext, session.selectedVin),
    )
    val locationCalibration: StateFlow<LocationCalibration> = _locationCalibration.asStateFlow()

    /**
     * 「这一帧的坐标是怎么来的」—— 给校准面板显示一句话，让用户知道自动判定做了什么。
     *
     * 只在校验结果变化时写：车况每秒都在刷，同样的句子反复塞进 StateFlow 会让
     * 整个面板重组一遍。
     */
    private val _locationDatumNote = MutableStateFlow<String?>(null)
    val locationDatumNote: StateFlow<String?> = _locationDatumNote.asStateFlow()

    private fun publishDatumNote(note: String?) {
        if (_locationDatumNote.value != note) _locationDatumNote.value = note
    }

    /**
     * 改校准值。**不联网重拉车况** —— 校准只是坐标的显示换算，车况报文里
     * 那些数字一个都没变，为此叫醒一次车机不值得。
     */
    fun setLocationCalibration(next: LocationCalibration) {
        val clean = LocationCalibrator.sanitize(next)
        if (clean == _locationCalibration.value) return
        _locationCalibration.value = clean
        LocationCalibrationStore.save(appContext, session.selectedVin, clean)
        // 缓存里的地址和地图是按旧坐标取的：留着会让用户以为「调了没生效」，
        // 而且地址与钉子会各说各话（一个按新点请求、一个还是旧图）
        VehicleLocationStore.clear(appContext)
        VehiclePlaceMemory.clear()
        remapCachedStatus()
    }

    /**
     * 用磁盘里那份**原始报文**按新校准重新映射一遍车况。
     *
     * 只重算展示，不发请求：坐标换了，钉子立刻落到新位置，地址则由下一次
     * 进页面时按新坐标重新取（缓存已经清了）。
     */
    private fun remapCachedStatus() {
        val raw = runCatching {
            statusPrefs.getString(statusCacheKey(session.selectedVin), null)
        }.getOrNull()
        if (raw.isNullOrBlank()) return
        val st = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val status = runCatching { buildStatus(st, observeCaps = false) }.getOrNull()
        _status.value = status
        OwnershipStats.kmFromText(status?.totalMileage)?.let { lastKnownOdometerKm = it }
    }

    // ---------------- 驻车拍照：手机拍照 / 车端照片 ----------------
    //
    // 官方 App 那套「驻车后自动上传环视俯视图」不是全系都有（见 ParkingPhotoSource 的说明），
    // 所以这里是**两选一**：默认手机拍照（老行为），用户自己切到车端照片。

    private val parkingPhotoPrefs =
        appContext.getSharedPreferences("leapcontrol_parking_photo", Context.MODE_PRIVATE)

    /** 车端照片成功落盘的次数，UI 靠它决定什么时候重新读盘。 */
    private var officialPhotoRevision = 0

    /** 已经为哪个 VIN 自动取过一次（每次启动、每辆车只自动试一次，别反复打接口）。 */
    private var officialAutoFetchVin: String? = null

    private val _parkingPhotoSource = MutableStateFlow(
        ParkingPhotoSource.fromId(parkingPhotoPrefs.getString(ParkingPhotoSourceRules.PREF_KEY, null)),
    )
    /** 当前选用的驻车拍照方案。 */
    val parkingPhotoSource: StateFlow<ParkingPhotoSource> = _parkingPhotoSource.asStateFlow()

    private val _officialPhotoStatus = MutableStateFlow(seedOfficialPhotoStatus())
    /** 车端照片的获取进度（取到了 / 还没上传 / 不支持 / 网络失败）。 */
    val officialPhotoStatus: StateFlow<OfficialPhotoStatus> = _officialPhotoStatus.asStateFlow()

    /**
     * 冷启动/换车后先用磁盘上那张把状态填上，免得首帧一律显示「还没取到」。
     * 只有 VIN 对得上才算数（见 [OfficialParkingPhotoStore.belongsTo]）。
     */
    private fun seedOfficialPhotoStatus(): OfficialPhotoStatus {
        val vin = session.selectedVin
        if (vin.isBlank()) return OfficialPhotoStatus.Idle
        if (!OfficialParkingPhotoStore.hasPhoto(appContext, vin)) return OfficialPhotoStatus.Idle
        officialPhotoRevision += 1
        return OfficialPhotoStatus.Ready(
            uploadTimeMs = OfficialParkingPhotoStore.uploadTime(appContext, vin),
            revision = officialPhotoRevision,
        )
    }

    fun setParkingPhotoSource(source: ParkingPhotoSource) {
        if (_parkingPhotoSource.value == source) return
        _parkingPhotoSource.value = source
        parkingPhotoPrefs.edit().putString(ParkingPhotoSourceRules.PREF_KEY, source.name).apply()
        // 首页右上角那一格存的是「当前来源」的缩略图，切来源后必须重新解码
        ParkingThumbMemory.clear()
    }

    /**
     * 取一次车端驻车照片。
     *
     * [force] = false 时是「进页面顺手试一下」：**每辆车每次启动只试一次**，本地已经有
     * 这张车的照片时直接跳过 —— 车端照片是停车那一刻上传的，短时间内重复问同一件事
     * 既没有新结果，也会让用户在流量/电耗上多付一次。用户点「重新获取」时才走 [force]。
     */
    suspend fun refreshOfficialPhoto(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val vin = session.selectedVin
        if (vin.isBlank()) {
            _officialPhotoStatus.value = OfficialPhotoStatus.Failed("未登录")
            return@withContext
        }
        if (!force) {
            if (officialAutoFetchVin == vin) return@withContext
            officialAutoFetchVin = vin
            if (OfficialParkingPhotoStore.hasPhoto(appContext, vin)) return@withContext
        }
        _officialPhotoStatus.value = OfficialPhotoStatus.Loading
        val api = LeapmotorApi(session)
        val query = runCatching { withNetworkRetry { api.queryParkingPhoto() } }
            .getOrElse { ChassisPhotoQuery.Failed(UserMessage.of(it, "照片没取到，请稍后重试")) }
        _officialPhotoStatus.value = when (query) {
            is ChassisPhotoQuery.Found -> {
                val bitmap = runCatching {
                    api.downloadParkingPhotoBitmap(query.photo.secureUrl)
                }.getOrNull()
                if (bitmap != null &&
                    OfficialParkingPhotoStore.save(appContext, vin, bitmap, query.photo.uploadTimeMs)
                ) {
                    officialPhotoRevision += 1
                    OfficialPhotoStatus.Ready(query.photo.uploadTimeMs, officialPhotoRevision)
                } else {
                    OfficialPhotoStatus.Failed("照片下载失败")
                }
            }
            ChassisPhotoQuery.Empty -> OfficialPhotoStatus.Empty
            is ChassisPhotoQuery.Unsupported -> OfficialPhotoStatus.Unsupported(query.message)
            is ChassisPhotoQuery.Failed -> OfficialPhotoStatus.Failed(query.message)
        }
    }

    /**
     * 清掉车端照片（换车 / 退出登录）。
     *
     * 手机拍的那张**不动**：它是用户自己的实物记录，换车删掉属于丢数据；
     * 车端这张是「这辆车停在哪」的信息，留给下一辆车就是彻底的错误。
     */
    private fun clearOfficialPhoto() {
        runCatching { OfficialParkingPhotoStore.clear(appContext) }
        OfficialThumbMemory.clear()
        officialAutoFetchVin = null
        _officialPhotoStatus.value = OfficialPhotoStatus.Idle
    }

    /** 用户在界面上「清除车端照片」：只清本地缓存，方案选择保留。 */
    fun deleteOfficialPhoto() {
        runCatching { OfficialParkingPhotoStore.delete(appContext) }
        OfficialThumbMemory.clear()
        _officialPhotoStatus.value = OfficialPhotoStatus.Idle
    }


    /**
     * 舒适件（座椅 / 方向盘 / 后视镜）的乐观覆盖记录，见 [ComfortOptimistic]。
     *
     * 这些信号车端回报滞后，下发后到真值翻篇之间任何一次刷新都会把界面弹回旧档位 ——
     * 用户在空调页和舒适卡上点「关闭」看到「又变回弱档」，根因就在这里。所有读写都过
     * [comfortLock]：控车在 IO 线程写、车况刷新也在 IO 线程写，不能让它俩互相覆盖。
     */
    private val comfortLock = Any()
    private var comfortPending: List<ComfortOptimistic.Pending> = emptyList()

    /**
     * 指令下发成功后登记一条待确认记录，并**立刻**把目标档位推进状态流。
     *
     * 后者是必要的：舒适卡（`ComfortScreens`）上的档位按钮直接读 `status`，不像空调页
     * 那样另有一份本地档位，光登记 pending 不改状态的话，用户点完「关闭」要盯着「弱档」
     * 最多等 20 秒回执，观感就是「点了没反应」。
     */
    private fun rememberComfortTarget(commandName: String) {
        val target = ComfortOptimistic.targetOf(commandName, System.currentTimeMillis()) ?: return
        synchronized(comfortLock) {
            // 同一字段只留最后一条：连点两下（关 → 强）时以最后一击为准
            comfortPending = comfortPending.filterNot { it.field == target.field } + target
            val current = _status.value ?: return
            // 只是为了算出「覆盖后长什么样」，**不采纳** merge 交回的 pending：
            // merge 会把「当前值已等于目标」的记录剔掉，而 current 本身就含乐观值，
            // 照单全收会让记录在真正刷新之前就被清空，一刷新就弹回旧档位。
            _status.value = ComfortOptimistic.merge(current, comfortPending, target.at).status
        }
    }

    /** 解析出的车况先过一遍乐观覆盖，再把用掉的记录剔掉。 */
    private fun mergeComfortOptimistic(status: CarStatus): CarStatus = synchronized(comfortLock) {
        val merged = ComfortOptimistic.merge(status, comfortPending, System.currentTimeMillis())
        comfortPending = merged.pending
        merged.status
    }

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
        // 磁盘缓存也写进进程内快照：磁贴展开时能立刻套用上一次已知状态。
        // 时间戳用缓存时间而不是 now —— 几小时前的报文不能当「刚拉到的」用。
        StatusSnapshot.put(_status.value, _lastUpdated.value)
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

    // ---- 行程轨迹 ----
    // 数据挂在 TripTracker 的进程级 StateFlow 上（跨页返回不丢），冷启动由磁盘预填。
    // 这里只转发，不复制一份状态，免得首页和二级页读到两份不同的数据。
    val tripFlow: StateFlow<TripOverview?> get() = TripTracker.flow

    /**
     * 自动开始记录行程：**默认关**，开关在行程轨迹页。
     *
     * 关着的时候只有用户亲手点「开始记录行程」才开一段 —— 这是 2026-11-06 的定稿，
     * 因为自动判据曾被车端的挡位噪声（UNKNOWN / NONE 里都含 N）骗到，一打开 App 就
     * 显示「正在记录」。开着的时候走「连续两帧行驶」的硬判据（见 TripRules.ingest）。
     * 存在 namingPrefs：跟着本机走、登出不丢。
     */
    private val _autoStartTrip = mutableStateOf(namingPrefs.getBoolean("trip_auto_start", false))
    val autoStartTripState: State<Boolean> get() = _autoStartTrip

    fun autoStartTrip(): Boolean = _autoStartTrip.value

    fun setAutoStartTrip(on: Boolean) {
        _autoStartTrip.value = on
        namingPrefs.edit().putBoolean("trip_auto_start", on).apply()
    }

    /**
     * 手动开始一段行程。
     *
     * 起点用**最近一次车况快照**，不额外发请求：用户点「开始」时屏幕上的数字就是他看到的
     * 起点。还没拉到过车况（冷启动直接进行程页）就返回 NO_STATUS，让 UI 提示先刷新。
     */
    fun startTrip(): TripStartResult {
        val status = _status.value ?: return TripStartResult.NO_STATUS
        val at = _lastUpdated.value.takeIf { it > 0L } ?: System.currentTimeMillis()
        return TripTracker.start(appContext, session.selectedVin, tripSampleOf(status, at))
    }

    /** 把一帧车况压成行程采样点。刷新与「手动开始」共用，保证起点口径完全一致。 */
    private fun tripSampleOf(status: CarStatus, at: Long): TripSample {
        val speed = TripRules.parseSpeedKmh(status.speed)
        return TripSample(
            at = at,
            latitude = status.latitude,
            longitude = status.longitude,
            speedKmh = speed,
            odometerKm = OwnershipStats.kmFromText(status.totalMileage),
            driving = TripRules.isDriving(speed, status.gearStatus),
            // 电量：0 当作「没读到」而不是「真的 0%」—— 车况信号缺失时 socFraction
            // 会返回 0，那会让一趟行程的耗电变成 -x% 的假数据。真跑到 0% 也开不回来。
            // 优先用 socSignal（含 preciseSoc），它比展示用的取整 soc 更接近真实读数。
            socPercent = (status.socSignal ?: status.soc.takeIf { it in 1..100 }?.toDouble())
                ?.takeIf { it in 1.0..100.0 },
            // 表显续航只对纯电有意义：增程车的主续航是「电 + 油」综合值，
            // 拿它算掉电续航会把油跑掉的部分算成电，直接不记。
            rangeKm = if (status.isRangeExtender) null
            else OwnershipStats.kmFromText(status.range),
            // 锁车状态：锁车 + 没在行驶 = 人走了，这一趟当场收尾（见 TripRules.ingest）。
            // null 表示车端没上报，此时退回「停车 5 分钟宽限期」的老办法。
            locked = status.locked,
        )
    }

    /**
     * 手动结束当前进行中的行程。首页卡 / 行程二级页 / 零记入口共用这一个出口，
     * 收尾结果同步返回给 UI 显示（点太少、太短、算不出里程都要有明确反馈）。
     * **不发任何车况请求**，只用已经采到的点。
     */
    fun finishCurrentTrip(): TripCloseResult = TripTracker.finishCurrent(
        appContext,
        session.selectedVin,
        amapKey(),
        System.currentTimeMillis(),
    )

    /** 删除一段历史行程（只删已收尾的段，进行中的删不到）。 */
    fun deleteTrip(id: Long): Boolean =
        TripTracker.delete(appContext, session.selectedVin, id)

    /**
     * 用户确认「这趟不要了」：清掉进行中的一段，不写历史。
     *
     * 不到 30 秒、车机没读到位移的那种段本来就建不了档，但用户点了结束就得真的结束 ——
     * 否则会一直卡在「结束了却还在记录」。
     */
    fun discardCurrentTrip(): Boolean =
        TripTracker.discardCurrent(appContext, session.selectedVin)

    /** 多段合并成一段。返回结果给 UI 反馈，成功/失败都要说话。 */
    fun mergeTrips(ids: Collection<Long>): TripMergeResult =
        TripTracker.merge(appContext, session.selectedVin, amapKey(), ids)

    /**
     * 电池容量（kWh）：把「电量掉了几个百分点」换算成度数要它。
     *
     * 按 VIN 存。同一款车有好几个电池版本（C11 就有 78.5 / 90 kWh），按车型猜一个数字
     * 等于把估算写成实测，所以留空时不换算、只显示百分比，让用户自己在设置里填一次。
     */
    fun batteryKwh(): Double? =
        namingPrefs.getString("battery_kwh_${session.selectedVin}", "")?.trim()
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in TripRules.MIN_BATTERY_KWH..TripRules.MAX_BATTERY_KWH }

    fun setBatteryKwh(kwh: Double?) {
        val clean = kwh?.takeIf {
            it.isFinite() && it in TripRules.MIN_BATTERY_KWH..TripRules.MAX_BATTERY_KWH
        }
        namingPrefs.edit().apply {
            val key = "battery_kwh_${session.selectedVin}"
            if (clean == null) remove(key) else putString(key, clean.toString())
        }.apply()
        // 立刻作用到进程内状态：用户填完容量后，正在记录的那段马上就能算度数，
        // 不用等下一次车况刷新。
        TripTracker.setBatteryKwh(appContext, clean)
    }

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
        // 行程轨迹：先用本机上次的结果填一帧，避免冷启动时卡片先空一下
        TripTracker.load(appContext, session.selectedVin, batteryKwh())
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
        // 刚下发的舒适件命令在保护窗口内盖住滞后的旧值（见 ComfortOptimistic）
        val status = mergeComfortOptimistic(buildStatus(st))
        cacheStatus(session.selectedVin, st)
        // 结果通过 statusFlow 下发：首页直接读到新值，不需要 UI 自己再 setState
        _status.value = status
        _lastUpdated.value = System.currentTimeMillis()
        // 记下这一帧的表显里程，供缺字段时给卡片兜底（见 lastKnownOdometerKm）
        OwnershipStats.kmFromText(status.totalMileage)?.let { lastKnownOdometerKm = it }
        // 顺便喂一次「自上次充电后行驶里程」的观测（见 ChargeCycle）：
        // 车端没有充电历史接口，只能在拿到车况时「看见」充电 —— 充电时车停着，
        // 此刻的表显里程就是准确起点。
        // **不充电时也要喂**：那一下会给上一次充电盖「已结束」的戳，是下次能正确重置的
        // 前提。不充电且已有戳时判定返回 null，不写盘。记账失败也绝不能影响车况刷新。
        // 光有 chargeState 不够：它会在拔枪之后残留，因此一起把旁证（交直流输入 /
        // 枪信号 / 电池电流）和「车在不在动」带上，判定见 ChargeSample。
        runCatching {
            journal.observeChargeCycle(chargeSampleOf(status, st))
            // 充电中续上采样闹钟（不在充电就撤掉）。这一发同时是「后台采样」的接线点：
            // 所有拿车况的路径都走这里，闹钟响时也走这里，所以只需接一次。
            ChargeWatch.reschedule(appContext, status.chargeState, status.chargeRemainMinutes)
        }
        // 同步给控制中心磁贴：用户在车里手动关掉的空调 / 后备箱，
        // 靠的就是这条新鲜报文把磁贴的高亮改回来（磁贴展开时直接读这里，不再发请求）
        StatusSnapshot.put(status, _lastUpdated.value)
        // 桌面小组件跟磁贴同一份数据：有卡片挂在桌面上就顺手重画一帧。
        // 没有卡片时 render 第一行就返回，等于零开销；失败也绝不能影响车况刷新。
        // partial：这里只换了数字和状态，背景 / 壁纸 / 字号都没变 —— 走全量会让
        // 沉浸样式在桌面进程里把用户那张壁纸重新解码一遍，卡片会闪一下。
        runCatching {
            com.leapcontrol.app.widget.VehicleWidget.render(appContext, this@AppRuntime, partial = true)
        }
        // 行程轨迹：搭这次车况刷新的顺风车记一个点，**不新增任何一次车况请求**。
        // 采集失败绝不影响车况刷新；起终点中文地址由 TripTracker 在行程收尾时异步补。
        runCatching {
            TripTracker.observe(
                appContext,
                session.selectedVin,
                amapKey(),
                batteryKwh(),
                tripSampleOf(status, _lastUpdated.value),
                // 自动开始：用户自己在行程页开的开关。默认关 = 只能手动点开始。
                autoStart = autoStartTrip(),
            )
        }
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
            // 注意方向：**新拉到的值必须覆盖缓存**（refreshedBy），缓存只补新响应里缺的字段。
            // 早先写成 `summary?.merge(parsed)`，而 merge 是「本对象优先、other 补空」，
            // 于是总里程 / 综合能耗 / 提车天数从第一次拉取之后就永远不再更新（用户反馈
            // 「剩余保养里程不动」的根因就在这里）。磁盘缓存会在启动时预填，所以这条路径
            // 几乎每次都会走到。
            var summary = cached
            runCatching { withNetworkRetry { api.getMileageEnergy() } }
                .onSuccess { resp ->
                    MileageEnergyParser.parseLifetime(resp)?.let { parsed ->
                        summary = summary?.refreshedBy(parsed) ?: parsed
                    }
                }
            runCatching { withNetworkRetry { api.getRecentMileageEnergy() } }
                .onSuccess { resp ->
                    MileageEnergyParser.parseRecent(resp)?.let { parsed ->
                        summary = summary?.refreshedBy(parsed) ?: parsed
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
        // 位置只解析一次：坐标、来源、稳定起点三者都从这一对值推出来
        val location = resolveLocation(st)
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
            latitude = location.latitude,
            longitude = location.longitude,
            locationHint = location.hint,
            locationSeenAt = location.seenAt,
            locationSource = location.source,
            acTargetTemp = acSetting["temperature"],
            // 风量/循环以**实时信号**为准（1941 / 1943），acSetting 里的只是设定值：
            // 车机上把风量拉到 7 挡后 acSetting 可能还是 2（用户 2026-11-03 实测），
            // 官方 App 与上游都读 acAirVolume，这里同样处理。见 AcLiveState。
            acWindLevel = AcLiveState.windLevel(pick(st, "acAirVolume", "1941"), acSetting["windlevel"]),
            acCircle = AcLiveState.circle(
                pick(st, "recirculationMode", "1943"),
                acSetting["circle"]?.let {
                    when (it) { "in", "1" -> "内循环"; "out", "0" -> "外循环"; else -> null }
                },
            ),
            acPosition = acSetting["position"]?.trim()?.takeIf {
                it == "all" || it == "wshld"
            },
            acMode = acSetting["mode"]?.trim()?.takeIf {
                it in com.leapcontrol.app.Commands.AC_MODES
            },
            // 除霜位单独取信号 1945：acSetting 里没有这个字段，只有 wshld（请求侧的名字）
            windshieldDefrost = VehicleSignals.bool(pick(st, "windshieldDefrost", "1945")),
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
                // 挂 D/R 挡时的「已闭锁」是行车自动落锁，不能当成离车（用户 2026-11-04 反馈）
                gearStatus = formatGear(st.opt("gearStatus")),
            ),
            chargeState = ChargeStatus.state(st),
            healthyChargeEnabled = parseOnOff(pick(st, "healthyChargeEnabled", "48")),
            // 车端没有独立的充电功率信号，官方也是「电压 × 电流」推算（未充电时电流≈0 → 0 kW，
            // 界面在这种状态下显示「未充电」，不显示 0.0 kW）
            chargingPower = formatValue(pick(st, "chargingPower", "chargePower", "chargePowerKw"), "kW")
                ?: ChargeStatus.electricalPower(batteryVoltageRaw, batteryCurrentRaw),
            chargeTimeLeft = parseChargeTimeLeft(st),
            chargeRemainMinutes = pick(st, "chargeRemainTime", "1200")
                ?.toString()?.trim()?.toIntOrNull()?.takeIf { it >= 0 },
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
            // 电池预热：信号 1186，4 = 正在加热 / 0 = 空闲（上游同款口径）
            batteryPreheatOn = com.leapcontrol.app.BatteryPreheatState.fromRaw(
                pick(st, "batteryThermalRequest", "1186"),
            ),
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
            // 方向盘加热：1816（0 关）为主，1624（剩余加热分钟）兜底 ——
            // 只报分钟数的车型如果只看 1816，按钮会永远停「关」。
            steeringWheelHeating = SteeringWheelHeat.levelOf(
                level = seatLevel(st, "steeringWheelHeating", "1816"),
                minutes = seatLevel(st, "steeringWheelHeaterMinutes", "1624"),
            ),
            // 不兜底 rearWindowHeating（后风挡加热）：那几乎全系都有，会让「有没有后视镜加热」永远为真
            rearviewMirrorHeating = parseOnOff(pick(st, "rearviewMirrorHeating", "49", "50")),
            fridge = parseFridge(st),
            // 行程能耗要用真实读数：只看 soc(1204) 会在「车型只下发 preciseSoc」时变 0
            socSignal = VehicleStatusMapper.electricSocPercent(st)?.toDouble(),
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

    /**
     * 充电枪是否连接（枪插着但没在充也算已连接）。
     *
     * 优先用专用的枪状态信号；车型不上报这几路时，退到
     * [ChargeStatus.gunConnected] 的「交直流输入 + chargeState」口径 ——
     * 以前这里直接给 null，于是充电中心只能自己拿 `chargeState != 0` 兜底，
     * 把「预约了充电但还没插枪（state=4）」也算成插枪，冒出一排点了没用的按钮。
     */
    private fun parseChargeCableConnected(st: JSONObject): Boolean? {
        val raw = pick(st, "chargeGunStatus", "gunStatus", "chargeCableStatus", "chargeConnectorStatus")
        val named = when (raw?.toString()?.trim()?.lowercase()) {
            "1", "2", "true", "connected" -> true
            "0", "false", "disconnected" -> false
            else -> null
        }
        if (named != null) return named
        val state = ChargeStatus.state(st)
        // 三路信号一个都没有：结论是「不知道」，别硬猜成未插枪
        if (state == null && st.opt("acInputSlowCharge") == null && st.opt("dcInputFastCharge") == null) {
            return null
        }
        return ChargeStatus.gunConnected(
            acInputSlowCharge = st.opt("acInputSlowCharge"),
            dcInputFastCharge = st.opt("dcInputFastCharge"),
            state = state,
        )
    }

    /**
     * 把一帧车况压成「充电起点」的一次观测（见 [com.leapcontrol.app.journal.ChargeCycle]）。
     *
     * 除了 `chargeState`，这里还带三样东西，为的是让「改起点」这件不可逆的事有据可依：
     *  - **车在不在动**：充电时车不可能在跑，动着的帧一律不作数；
     *  - **交 / 直流输入**：硬件上真有电进来，才是「正在充电」；
     *  - **电池电流**：1178 是 BMS 常驻信号，充电时几十安、停着时接近 0。
     *
     * 以前只喂 `chargeState`，车端那一格残留（拔枪后仍报 1）就会把起点一路改到当下，
     * 卡片上的数字看起来就是「总是自动清零」（用户 2026-11-06 反馈）。
     */
    private fun chargeSampleOf(status: CarStatus, st: JSONObject): ChargeSample = ChargeSample(
        chargeState = status.chargeState,
        soc = status.soc,
        totalKm = OwnershipStats.kmFromText(status.totalMileage),
        moving = status.isDriving == true,
        evidence = ChargeCycle.evidence(
            inputOn = chargeInputOn(st),
            gunSignal = chargeGunSignal(st),
            currentAmp = ampOf(pick(st, "batteryCurrent", "1178", "chargeCurrent", "bmsCurrent")),
        ),
    )

    /** 交流 / 直流输入是否有电：任一为真 = true，两路都明确为 0 = false，其余不知道。 */
    private fun chargeInputOn(st: JSONObject): Boolean? {
        val ac = VehicleSignals.bool(st.opt("acInputSlowCharge"))
        val dc = VehicleSignals.bool(st.opt("dcInputFastCharge"))
        return when {
            ac == true || dc == true -> true
            ac == false && dc == false -> false
            else -> null
        }
    }

    /**
     * 专用的枪状态信号（不退化到 `chargeState`）。
     *
     * 刻意不复用 [parseChargeCableConnected]：那个方法在没有专用信号时会拿 `chargeState`
     * 反推，用它当旁证等于「拿结论证明结论」，残影照样混过去。
     */
    private fun chargeGunSignal(st: JSONObject): Boolean? {
        val raw = pick(st, "chargeGunStatus", "gunStatus", "chargeCableStatus", "chargeConnectorStatus")
        return when (raw?.toString()?.trim()?.lowercase()) {
            "1", "2", "true", "connected" -> true
            "0", "false", "disconnected" -> false
            else -> null
        }
    }

    /** 电流读数（A）：车端可能带单位或符号，解析不出就是 null。 */
    private fun ampOf(raw: Any?): Double? = raw?.toString()?.trim()
        ?.removeSuffix("A")
        ?.trim()
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }

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
        val gearStatus = formatGear(st.opt("gearStatus"))
        val shutDown = VehicleHomeStatus.isVehicleShutDown(
            bcmKeyPositionOn3 = st.opt("bcmKeyPositionOn3"),
            vehicleState = st.opt("vehicleState"),
            parkingBrakeState = st.opt("parkingBrakeState"),
            locked = VehicleSignals.locked(st),
            gearStatus = gearStatus,
        )
        VehicleHomeStatus.resolveDetailedDrivingState(
            gearStatus = gearStatus,
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

    /** 一帧车况里与「车停在哪」有关的全部结论。 */
    private data class ResolvedLocation(
        val latitude: Double?,
        val longitude: Double?,
        /** 取不到时的一句话原因（debug 用）。 */
        val hint: String?,
        /** 这条坐标从什么时候起没变过；坐标无效时为 0。 */
        val seenAt: Long,
        /** 实际用了哪一对信号键（debug 用）。 */
        val source: String?,
    )

    /**
     * 车辆经纬度（优先命名信号，其次 3724/3725，最后 2/3；见 [VehicleLocationDomain]）。
     *
     * 这里比 [VehicleLocationDomain.selectFromDecodedOrRaw] 多走一步：**跳过解不出坐标的那一对**。
     * 报文里 `3724`/`3725` 与 `2`/`3` 同时存在、而前者是 0 或空串时（车机刚唤醒常见），
     * 旧逻辑只看「键在不在」，于是把一对 0 认成坐标，真坐标反而被挡住。
     */
    private fun resolveLocation(st: JSONObject): ResolvedLocation {
        val values = HashMap<String, Any?>()
        val keys = st.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            values[k] = st.opt(k)
        }
        val selected = VehicleLocationDomain.selectUsable(values)
        // 自动判定（见 [VehicleDatumDetector]）：车机同时报了两对坐标、且能用
        // WGS-84 → GCJ-02 的公式互证时，直接挑出本来就是 GCJ-02 的那一对。
        // 这是「B10 / C01 定位不准」最可能的真凶 —— 这两款车的命名键 / 3724+3725
        // 报的可能是 GPS 原始值，而 C11 报的本来就是火星坐标，所以只有部分用户需要换算。
        val detected = VehicleDatumDetector.detect(values)
        publishDatumNote(
            when {
                detected != null -> "已自动纠正：车机同时报了两套坐标，用的是高德那套"
                VehicleDatumDetector.usablePairCount(values) >= 2 ->
                    "车机报了两套坐标且对不上，坐标系可以在下面手动选一项"
                else -> null
            },
        )
        val rawLatitude = (detected?.latitude ?: selected.pair.latitude?.toString()?.toDoubleOrNull())
        val rawLongitude = (detected?.longitude ?: selected.pair.longitude?.toString()?.toDoubleOrNull())
        val valid = VehicleLocationRules.isValid(rawLatitude, rawLongitude)
        // 校准（坐标系换算 + 手调偏移，见 [LocationCalibration]）只作用在**输出**上：
        // 车况里 rawLatitude 那一对仍然是车端原话，后面判「位置有没有变过」也用它 ——
        // 否则用户调一次校准，就会被当成「车挪了」，时间的锚点直接归零
        val shown = if (valid) {
            LocationCalibrator.apply(
                rawLatitude!!,
                rawLongitude!!,
                _locationCalibration.value,
                // 检测出的那一对已经是 GCJ-02，交给 apply 的语义是「原样用，别再换算」
                autoDatum = if (detected != null) CoordinateDatum.RAW else null,
            )
        } else {
            null
        }
        return ResolvedLocation(
            latitude = shown?.first ?: rawLatitude,
            longitude = shown?.second ?: rawLongitude,
            hint = VehicleLocationDiagnostics.describe(values),
            seenAt = if (valid) {
                trackLocationAnchor(rawLatitude!!, rawLongitude!!, System.currentTimeMillis())
            } else {
                0L
            },
            source = if (valid) {
                detected?.let { "自动判定 · ${it.note}" } ?: VehicleLocationDiagnostics.describeSource(values)
            } else {
                null
            },
        )
    }

    // ---- 位置的「稳定起点」----
    // 坐标本身没有时间戳，只有「什么时候变过」是可观测的（见 [LocationAnchorRule]）。
    // 内存里持有当前锚点，只在起点变化时落盘：车在跑时每次刷新都写一次 prefs 太吵，
    // 而且写进去的值每次都一样。
    private var locationAnchor: LocationAnchor? = null
    private var locationAnchorLoaded = false

    /** 推进锚点并返回「这条坐标从什么时候起没变过」。 */
    private fun trackLocationAnchor(latitude: Double, longitude: Double, now: Long): Long {
        if (!locationAnchorLoaded) {
            locationAnchor = VehicleLocationAnchorStore.load(appContext)
            locationAnchorLoaded = true
        }
        val previous = locationAnchor
        val next = LocationAnchorRule.next(previous, latitude, longitude, now) ?: return 0L
        if (previous == null || next.since != previous.since) {
            VehicleLocationAnchorStore.save(appContext, next)
        }
        locationAnchor = next
        return next.since
    }

    /** 换车 / 退出登录：锚点跟着一起清，否则新车的第一个位置会继承上一辆车的时间。 */
    private fun resetLocationAnchor() {
        locationAnchor = null
        locationAnchorLoaded = true
        VehicleLocationAnchorStore.save(appContext, null)
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

    // ---------------- 自定义背景（用户上传的壁纸 / 相伴卡图） ----------------
    //
    // 三张图（日间壁纸 / 夜间壁纸 / 相伴卡背景）走 [CustomBackdropStore]，这里管三件事：
    //  1. 内存热缓存：MainScreen 用 `when(tab)` 切页，切回来整棵子树重建，
    //     只靠页面里的 remember 会重新解码一张 10MB 的壁纸（肉眼可见地卡一下）；
    //  2. 版本号：换图后要让 UI 重新解码，而「文件时间戳」是最省事的变化信号；
    //  3. 主色：壁纸生效时主题色由它代替内置主题色，跟着图走。

    /**
     * 三张图的版本号 + 两个壁纸主色，打包成一个 State，换图时一次刷新。
     *
     * [day] / [night] / [ownership] 是文件最后修改时间（0 = 没有图）；
     * [revision] 是内存里的自增号 —— 文件时间戳只精确到秒（个别文件系统 2 秒），
     * 连续两次换图可能撞在同一秒，界面就不会重新解码，所以额外带一个只增不减的号。
     */
    data class CustomBackdropState(
        val day: Long = 0L,
        val night: Long = 0L,
        val ownership: Long = 0L,
        /** 日间壁纸提取出的主题色（0 = 没取到 / 没设图） */
        val lightAccent: Int = 0,
        /** 夜间壁纸提取出的主题色 */
        val darkAccent: Int = 0,
        /** 每次保存 / 删除自增，保证 UI 一定能感知到变化 */
        val revision: Long = 0L,
    ) {
        fun versionOf(kind: CustomBackdropKind): Long = when (kind) {
            CustomBackdropKind.WALLPAPER_DAY -> day
            CustomBackdropKind.WALLPAPER_NIGHT -> night
            CustomBackdropKind.OWNERSHIP -> ownership
        }

        fun accent(dark: Boolean): Int = if (dark) darkAccent else lightAccent
    }

    private val _customBackdrop = mutableStateOf(readCustomBackdropState())
    val customBackdrop: State<CustomBackdropState> get() = _customBackdrop

    /**
     * 解码后的图缓存，LRU 留最近两张。
     *
     * 上限压到 2 而不是 3：一张壁纸 1080 宽解码进内存约 10MB，三张同时留着
     * 在低端机上有被系统回收的风险；而实际同时用得上的最多两张
     * （当前这一侧壁纸 + 相伴卡）。
     *
     * 淘汰时**不 recycle** —— 界面上可能刚好还挂着一个 ImageBitmap 在画它，
     * recycle 之后立刻绘制会抛 "trying to use a recycled bitmap"。Bitmap 交给 GC 就好。
     */
    private val customBackdropCache =
        object : LinkedHashMap<CustomBackdropKind, Bitmap>(4, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<CustomBackdropKind, Bitmap>,
            ): Boolean = size > 2
        }

    private fun readCustomBackdropState(): CustomBackdropState = CustomBackdropState(
        day = CustomBackdropStore.version(appContext, CustomBackdropKind.WALLPAPER_DAY),
        night = CustomBackdropStore.version(appContext, CustomBackdropKind.WALLPAPER_NIGHT),
        ownership = CustomBackdropStore.version(appContext, CustomBackdropKind.OWNERSHIP),
        lightAccent = namingPrefs.getInt("custom_accent_light", 0),
        darkAccent = namingPrefs.getInt("custom_accent_dark", 0),
    )

    fun hasCustomBackdrop(kind: CustomBackdropKind): Boolean =
        CustomBackdropStore.exists(appContext, kind)

    /** 同步拿缓存（切页回来第一帧就能用）；没加载过返回 null，由调用方去 [customBackdrop] 取。 */
    fun cachedCustomBackdrop(kind: CustomBackdropKind): Bitmap? =
        customBackdropCache[kind]?.takeIf { !it.isRecycled }

    /** 读图：命中缓存直接返回，否则从磁盘解码（IO 线程）。 */
    suspend fun customBackdrop(kind: CustomBackdropKind): Bitmap? = withContext(Dispatchers.IO) {
        cachedCustomBackdrop(kind) ?: CustomBackdropStore.load(appContext, kind)?.also {
            customBackdropCache[kind] = it
        }
    }

    /**
     * 保存用户选好并裁切过的背景，成功返回 true。
     *
     * 壁纸顺手提取主色：日间图写浅色槽、夜间图写深色槽。
     * 提取只在保存这一下跑一次（缩到 64×64 再算），不是每次进首页都算。
     */
    suspend fun saveCustomBackdrop(kind: CustomBackdropKind, bitmap: Bitmap): Boolean {
        if (!CustomBackdropStore.save(appContext, kind, bitmap)) return false
        customBackdropCache.remove(kind)

        if (kind.isWallpaper) {
            val small = withContext(Dispatchers.Default) {
                // 缩到 64×64 再取像素：1080 宽的图 getPixels 要 19MB 的临时数组，
                // 而主色只看「大面积色块」，64×64 完全够
                runCatching { Bitmap.createScaledBitmap(bitmap, 64, 64, true) }.getOrNull()
            }
            if (small != null) {
                val pixels = IntArray(small.width * small.height)
                small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                val dark = kind == CustomBackdropKind.WALLPAPER_NIGHT
                val accent = WallpaperPalette.accentFrom(
                    pixels = pixels,
                    width = small.width,
                    height = small.height,
                    dark = dark,
                )
                if (small !== bitmap) small.recycle()
                if (accent != null) {
                    namingPrefs.edit()
                        .putInt(if (dark) "custom_accent_dark" else "custom_accent_light", accent)
                        .apply()
                }
            }
        }

        _customBackdrop.value = readCustomBackdropState()
            .copy(revision = _customBackdrop.value.revision + 1)
        return true
    }

    /** 删掉某张自定义背景，界面回落到内置主题 / 默认相伴卡图。 */
    fun clearCustomBackdrop(kind: CustomBackdropKind) {
        CustomBackdropStore.clear(appContext, kind)
        // 只从缓存里摘掉，**不要 recycle**：界面上可能还挂着一个 ImageBitmap 在画这一帧，
        // recycle 之后立刻绘制会直接抛 "Canvas: trying to use a recycled bitmap"
        customBackdropCache.remove(kind)
        val accentKey = when (kind) {
            CustomBackdropKind.WALLPAPER_DAY -> "custom_accent_light"
            CustomBackdropKind.WALLPAPER_NIGHT -> "custom_accent_dark"
            CustomBackdropKind.OWNERSHIP -> null
        }
        if (accentKey != null) namingPrefs.edit().remove(accentKey).apply()
        _customBackdrop.value = readCustomBackdropState()
            .copy(revision = _customBackdrop.value.revision + 1)
    }

    /** 自定义壁纸的主色；没设图或没提取到就返回 null，界面继续用内置主题色。 */
    fun customWallpaperAccent(dark: Boolean): Int? =
        _customBackdrop.value.accent(dark).takeIf { it != 0 }

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

    /**
     * 车模换了（刷新官方图 / 换色 / 相册导入 / 恢复官方）：通知桌面小组件换掉那张缩略图。
     *
     * 小组件有自己的磁盘缓存（`widget/WidgetCarImage`），不主动通知的话它会一直贴着旧图 ——
     * 它没法实时读到这里的内存状态。失败绝不能影响 App 内的显示，所以整体兜住。
     */
    fun refreshWidgetCarImage() {
        runCatching { com.leapcontrol.app.widget.VehicleWidget.refreshCarImage(appContext, this) }
    }

    // ---------------- 官方 3D 车模 ----------------
    //
    // 2D 定妆照是服务端按车辆档案**预先渲染**的一张图：接口只收 VIN，车型配色、
    // 轮毂、车顶全在上传那一次就定死了，所以「颜色和配置跟实车不一样」根本改不了。
    // 3D 是拿官方下发的 modelParam 在设备上实时渲染，才对得上实车。
    //
    // 代价是第一次要下约 12MB 的两个包（H5 运行时 + 车型资产），之后永久缓存。
    // 所以这里只负责「拿包、判就绪」，渲染在 ui/CarModel3DView.kt。

    /**
     * 3D 车模当前走到哪一步。
     *
     * 首页**只在 [READY] 时**把 2D 图换成 3D；其余状态一律还是那张 2D 图 ——
     * 「下载中 / 失败」对用户就等于「没变」，不会出现空镜头或半成品。
     */
    enum class Car3DState {
        /** 还没开始（未登录、开关关着、或已经清过缓存）。 */
        IDLE,
        /** 正在下载 / 解压模型包。 */
        DOWNLOADING,
        /** 包就绪，可以渲染。 */
        READY,
        /** 有 h5Key 但下载失败，下次进首页会重试。 */
        FAILED,
        /** 官方没给这台车下发 3D 包，只能看 2D。 */
        UNAVAILABLE,
    }

    private val _car3DState = MutableStateFlow(Car3DState.IDLE)
    val car3DState: StateFlow<Car3DState> = _car3DState.asStateFlow()

    private val _car3DMeta = MutableStateFlow(
        CarModel3DManager.loadMeta(appContext, session.selectedVin),
    )
    val car3DMeta: StateFlow<CarModel3DManager.Meta?> = _car3DMeta.asStateFlow()

    private val _car3DEnabled = MutableStateFlow(
        CarModel3DManager.isEnabled(appContext, session.selectedVin),
    )
    val car3DEnabled: StateFlow<Boolean> = _car3DEnabled.asStateFlow()

    /** 正在同步哪台车：中途切车时旧任务的结果要丢掉，不能写到新车上。 */
    @Volatile
    private var car3DSyncVin: String = ""

    fun setCar3DEnabled(enabled: Boolean) {
        CarModel3DManager.setEnabled(appContext, session.selectedVin, enabled)
        _car3DEnabled.value = enabled
        if (!enabled) _car3DState.value = Car3DState.IDLE
    }

    /**
     * 准备 3D 车模（首页进来就调一次）。
     *
     * 顺序：本地元数据 → 命中就绪直接 READY → 否则联网要 h5Key/srcKey 再下载。
     * 全程不阻塞首帧：首页先显示 2D 图，3D 就绪后靠 StateFlow 自己换上去。
     */
    suspend fun prepareCarModel3D(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val vin = session.selectedVin
        if (vin.isBlank()) {
            _car3DState.value = Car3DState.IDLE
            return@withContext false
        }
        // 开关按车存，换车后要重新读一次
        _car3DEnabled.value = CarModel3DManager.isEnabled(appContext, vin)
        if (!_car3DEnabled.value) {
            _car3DState.value = Car3DState.IDLE
            return@withContext false
        }

        var meta = if (force) null else CarModel3DManager.loadMeta(appContext, vin)
        _car3DMeta.value = meta
        if (meta != null && CarModel3DManager.isReady(appContext, meta.h5Key, meta.srcKey)) {
            _car3DState.value = Car3DState.READY
            return@withContext true
        }

        if (meta == null) {
            // 24 小时内已经问过、结论是「这台车没有 3D 包」就不再浪费一次完整探测
            if (!force && CarModel3DManager.shouldSkipUnavailable(appContext, vin)) {
                _car3DState.value = Car3DState.UNAVAILABLE
                return@withContext false
            }
            val fetched = runCatching {
                withNetworkRetry { LeapmotorApi(session).getVehiclePictureMeta(vin) }
            }.getOrNull()
            val h5Key = fetched?.h5Key
            if (h5Key.isNullOrBlank()) {
                // 网络失败不记结论（下次还得试）；服务端明确回了数据但没 h5Key 才记
                if (fetched != null) CarModel3DManager.markUnavailable(appContext, vin)
                _car3DState.value = Car3DState.UNAVAILABLE
                return@withContext false
            }
            CarModel3DManager.clearUnavailable(appContext, vin)
            meta = CarModel3DManager.Meta(
                h5Key = h5Key,
                srcKey = fetched.srcKey,
                modelParam = fetched.modelParam,
                pictureKey = fetched.pictureKey,
            )
            CarModel3DManager.saveMeta(appContext, vin, meta)
            _car3DMeta.value = meta
        }

        car3DSyncVin = vin
        _car3DState.value = Car3DState.DOWNLOADING
        val ok = runCatching {
            CarModel3DManager.syncPackage(appContext, meta.h5Key, meta.srcKey) { key, dir ->
                LeapmotorApi(session).downloadCarModelPackage(key, dir)
            }
        }.getOrDefault(false)

        if (car3DSyncVin != vin) return@withContext false
        _car3DState.value = if (ok) Car3DState.READY else Car3DState.FAILED
        ok
    }

    /** 清掉 3D 资源（换车型 / 用户主动清理），下次进首页按需重下。 */
    fun clearCarModel3D() {
        CarModel3DManager.clearAll(appContext)
        CarModel3DManager.clearMeta(appContext, session.selectedVin)
        CarModel3DManager.clearUnavailable(appContext, session.selectedVin)
        _car3DMeta.value = null
        _car3DState.value = Car3DState.IDLE
    }

    /** 3D 包占用的磁盘（设置页显示）。 */
    fun carModel3DSize(): Long = CarModel3DManager.sizeOnDisk(appContext)

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
        // 冷启动兜底：内存两层都是空的，但启动预热（见 CarImagePreload）多半已经
        // 把磁盘那张解码好了。没有这一步，首页第一帧的车模位就是空的 —— 也就是"闪一下"。
        ?: CarImagePreload.peek(session.selectedVin)

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
            refreshWidgetCarImage()
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
        refreshWidgetCarImage()
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
            refreshWidgetCarImage()
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
        refreshWidgetCarImage()
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
     * 哨兵模式的账号权限门禁（2026-11-06 用户反馈「控车失败(40): 无此权限」）。
     *
     * 云端对**授权 / 子账号**下发 cmdid=400 的「开启哨兵」会直接回 result=40，
     * 指令参数、操作密码、登录态都是好的，重试也不会变 —— 这是账号权限，不是故障。
     * 上游项目在点击前用 [SentryModeControlPolicy] 拦了一道；本项目同步了车辆模型与
     * 共享账号识别，却漏了这道拦截，才把「控车失败(40): 无此权限」这句黑话丢给用户。
     *
     * 放在 [control] / [controlForWidget] 的入口：首页、控制中心磁贴、桌面小组件、
     * 车辆体检的一键处理全都走这两条路，一处覆盖全部入口。
     * **关闭哨兵不拦**（子账号也能把安防关掉），与上游策略一致。
     *
     * 账号标记是从车辆列表的一堆候选字段推断的，未必每台车都认得出来；认不出来时
     * 云端仍会回 40，由 [ControlFailure] / [UserMessage] 的权限文案兜底说人话。
     */
    private fun guardSentryPermission(commandName: String) {
        if (commandName != "sentryOn") return
        val shared = runCatching {
            sessionStore.loadVehicles()
                .firstOrNull { it.vin == session.selectedVin }
                ?.isSharedAccount == true
        }.getOrDefault(false)
        if (!SentryModeControlPolicy.canOperateSentry(shared, targetOn = true)) {
            throw ApiException(ControlFailure.HINT_SENTRY_PERMISSION.message)
        }
    }

    /**
     * 执行控车指令（commandName 如 "lock"/"unlock"/"acOn"/"acOff"/"horn"）。
     * [presetAcTemperature] 非空时改为按指定温度下发空调指令（Commands.buildAc）。
     * [resultWaitMs] 等车端执行结果的窗口；传 0 表示**发出去就返回**，不等回执。
     *   给有广播时间预算的调用方（桌面小组件，整条链路只有 10 秒）用 ——
     *   等回执最长 20 秒，等下去只会在车端还没回话时被系统把进程掐掉。
     *
     * **成功必须以车况为准**：网关结果码 0 / 200 只代表指令进了队列，车端执行完还可能
     * 自己否掉（车窗防夹弹回、后备箱被挡住），所以受理后还要读车况求证
     * （见 [verifyOutcome]）。求不到证就只说「已发出」，不能把「发出去了」说成「已经成了」。
     */
    suspend fun control(
        commandName: String,
        presetAcTemperature: Int? = null,
        resultWaitMs: Long? = null,
    ): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        guardSentryPermission(commandName)
        val api = LeapmotorApi(session)
        // 前挡除霜单独一条路：它的验收不看网关结果码，而是看车况信号 1945，
        // 而且可能要换几组参数才有用（见 [runDefrost]）。
        if (commandName == "defrost") {
            return@withContext runDefrost(api, pin, waitForResult = resultWaitMs != 0L)
        }
        // 方向盘加热也单独一条路：它是**两态开关**（2 开 / 1 关 / 0 关），
        // 「关」有两个候选值要试，而且必须读 1816 求证 ——
        // 以前只按网关回执就说「执行成功」，用户摸到的是冷方向盘（2026-11-07 反馈）。
        SteeringWheelHeat.targetOf(commandName)?.let { on ->
            return@withContext runSteeringWheelHeat(api, pin, on, waitForResult = resultWaitMs != 0L)
        }
        val command = if (presetAcTemperature != null) {
            Commands.buildAc(presetAcTemperature)
        } else {
            Commands.build(commandName)
        }
        val result = withNetworkRetry { api.sendControl(command, pin) }
        // 指令已到网关：立刻记下目标档位，后续车况刷新在保护窗口内不会把它弹回旧值
        rememberComfortTarget(commandName)
        // 不等回执的调用方：指令已经到网关，剩下的交给调用方回读车况判断
        if (resultWaitMs == 0L) return@withContext "${command.label}指令已下发"
        // 轮询执行结果：车端 T-Box 唤醒 + 执行通常需要数秒，官方抓包协议允许最长 24 秒。
        // 之前只等约 2 秒就在车辆真正执行前放弃，返回的「已下发」无法反映真实结果，
        // 也让用户无法判断到底是「车没收到」还是「收到了没执行」。
        //
        // 空调单独用短窗口：它就在首页第一屏，等 20 秒等于把开关按死在「在途」，
        // 超时后走下面的回读车况兜底，结论一样拿得到。
        val windowMs = resultWaitMs
            ?: if (command.cmdid == "170") AC_RESULT_MAX_WAIT_MS else CONTROL_RESULT_MAX_WAIT_MS
        val deadline = System.currentTimeMillis() + windowMs
        var attempt = 0
        var accepted = false
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(resultPollDelayMs(attempt))
            attempt++
            val resp = runCatching { api.queryControlResult(result.msgID) }.getOrNull() ?: continue
            val code = firstResultCode(resp)
            if (code == "0" || code == "200") {
                accepted = true
                break
            }
        }
        // 预设温度走的是空调那条指令，求证也按「空调开没开」看
        val verifyKey = if (presetAcTemperature != null) "acOn" else commandName
        // 网关受理 ≠ 车端执行：受理后读车况求证，读到信号变成意图值才算真的成功
        val verified = if (accepted) verifyOutcome(api, verifyKey) else null
        return@withContext when {
            verified == true -> "${command.label}执行成功"
            // 读得到信号但没变成意图值：既不能说成功，也不该说失败 —— 车端可能只是慢
            verified == false -> "${command.label}指令已发出，但车辆状态还没变化，请下拉刷新或到车上确认"
            // 读不到这个信号（鸣笛 / 预热 / 充电枪…）：只能按网关回执说话
            accepted -> "${command.label}执行成功"
            else -> timeoutOutcome(api, commandName, presetAcTemperature)
        }
    }

    /**
     * 超时没等到网关回执时的兜底说法。
     *
     * 空调单独回读一次车况自证（它就在首页第一屏，读一眼最直接）；
     * 其余指令读不到「执行过没有」，只能说发出去了。
     */
    private fun timeoutOutcome(
        api: LeapmotorApi,
        commandName: String,
        presetAcTemperature: Int?,
    ): String {
        if (commandName == "acOn" || commandName == "acOff" || presetAcTemperature != null) {
            val stillOn = runCatching { parseAcOn(api.getVehicleState()) }.getOrNull()
            val turnedOn = presetAcTemperature != null || commandName == "acOn"
            return when {
                turnedOn && stillOn == true -> "空调已开启"
                !turnedOn && stillOn == false -> "空调已关闭"
                !turnedOn -> "关闭指令已经发出，但车辆显示空调还在运行，请到车上确认"
                else -> "开启指令已经发出，但暂时读不到空调状态，下拉刷新看看"
            }
        }
        return "指令已经发出，车辆还没回话，请稍后在车上确认"
    }

    /**
     * 用车况信号求证「车端真的执行了」。
     *
     * 与界面共用同一份映射（[ControlFeedback.reached]）：指令 → 该看哪个信号 →
     * 期望它是开还是关，一处定义，免得磁贴、首页、这里各猜一套。
     *
     * @return true = 信号已变成意图值（真成功）；false = 读得到，但窗口内没变；
     *   null = 这台车读不到这个信号，或这条指令本来就没有可回读的状态
     *   （鸣笛 / 电池预热 / 充电枪）—— 这种情况只能按网关回执说话。
     */
    private fun verifyOutcome(api: LeapmotorApi, commandName: String): Boolean? {
        val topic = ControlFeedback.topicOf(commandName) ?: return null
        if (topic == ControlFeedback.TOPIC_HORN) return null
        val windowMs = when (topic) {
            ControlFeedback.TOPIC_AC -> AC_VERIFY_WINDOW_MS
            else -> VERIFY_CONTROL_WINDOW_MS
        }
        val deadline = System.currentTimeMillis() + windowMs
        var sawSignal = false
        while (true) {
            val remain = deadline - System.currentTimeMillis()
            if (remain <= 0) break
            Thread.sleep(minOf(VERIFY_CONTROL_GAP_MS, remain))
            val st = runCatching { api.getVehicleState() }.getOrNull() ?: continue
            // 不要 mergeComfortOptimistic：那是「刚点完先按意图显示」的乐观值，
            // 拿它求证等于自己证明自己
            val status = runCatching { buildStatus(st, observeCaps = false) }.getOrNull() ?: continue
            val verdict = ControlFeedback.reached(commandName, status) ?: continue
            sawSignal = true
            if (verdict) return true
        }
        return if (sawSignal) false else null
    }

    /**
     * 桌面小组件专用的控车：**发完指令后只等一小会儿回执**，然后带结果返回。
     *
     * 小组件点击走的是 BroadcastReceiver（[android.appwidget.AppWidgetProvider] 的 onReceive），
     * goAsync() 之后整条链路只有约 10 秒预算，不能沿用 [control] 那套最长 20 秒的轮询 ——
     * 一旦超时系统会判 ANR / 掐进程，用户看到的就是「点了没反应还卡一下」。
     *
     * 所以这里把窗口压到 [budgetMs]（默认 3.5 秒）：
     * - 拿到车端结果码 0 / 200 → 返回「XX已执行」，这是**真实执行结果**，不是「已下发」；
     * - 窗口内没等到 → 返回「XX已下发，车辆还没回话」，由调用方回读车况兜底。
     *
     * 与 `control(command, resultWaitMs = 0)` 的区别就在这：那个发完立刻返回，
     * 一个字的结果都不等，小组件只能靠回读车况猜 —— 而车端执行要 5~15 秒，
     * 3 秒回读通常还是上一条报文，于是「点了之后状态不刷新」。
     *
     * ⚠️ 内部 `Thread.sleep` 轮询没有可中断挂起点，外面的 withTimeout 掐不住它，
     * 真正的预算控制就靠这里的 [budgetMs]，调用方不要再给它套大超时。
     */
    suspend fun controlForWidget(commandName: String, budgetMs: Long = 3_500L): String =
        withContext(Dispatchers.IO) {
            val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
            guardSentryPermission(commandName)
            val api = LeapmotorApi(session)
            val command = Commands.build(commandName)
            val result = withNetworkRetry { api.sendControl(command, pin) }
            // 指令已到网关：记下目标档位，后续车况刷新在保护窗口内不会把它弹回旧值
            rememberComfortTarget(commandName)
            val deadline = System.currentTimeMillis() + budgetMs
            var attempt = 0
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(resultPollDelayMs(attempt))
                attempt++
                val resp = runCatching { api.queryControlResult(result.msgID) }.getOrNull() ?: continue
                val code = firstResultCode(resp)
                if (code == "0" || code == "200") return@withContext "${command.label}已执行"
            }
            // 结果码只对排查有用，用户看不懂；上屏只说「发出去了，还没回话」
            "${command.label}已下发，车辆还没回话"
        }

    /**
     * 前挡除霜：逐个组合试，每次都用车况信号 1945（windshieldDefrost）自证。
     *
     * 为什么不能一条参数打天下：用户 2026-11-03 实测两轮 —— 空调开着、关着各试一次，
     * 车端都把风量改成 5 挡、切成外循环（说明整组 170 被**执行**了），
     * 但前挡始终没出风，1945 一直是 0。也就是「指令被接收、只有除霜位被忽略」。
     *
     * 而协议里那条 defrost 给的是 cold + 24℃ / 5 挡 / position=wshld，
     * 与它自己「强效加热除霜」的描述矛盾，且文档是从源码反推的、没有抓包实证。
     * 所以这里按 [Commands.DEFROST_VARIANTS] 逐个下发：
     * ① 加热 + 最大风 + 只吹前挡；② 同样的加热配置但出风给全车。
     * 任何一组让 1945 翻成 1 就停；全都翻不上来才如实说「车端没进去」。
     *
     * 注意：`wshld` 不是除霜开关（acOn / quickCool 都带 wshld=1，车端并不除霜），
     * 真正切前挡的是 `position`，所以两组差异落在出风方向。
     */
    private suspend fun runDefrost(
        api: LeapmotorApi,
        pin: String,
        waitForResult: Boolean = true,
    ): String {
        // 空调关着时先把空调开起来：车端对「关机状态下的 manual」只当开机执行，
        // 除霜位会被直接丢掉（用户 11-03 第一次反馈的正是这种情形）。
        if (statusFlow.value?.acOn != true) {
            // 开机失败不阻断：除霜那条本身也带 manual，车端仍可能自己开机
            runCatching { withNetworkRetry { api.sendControl(Commands.build("acOn"), pin) } }
            // 同 cmdid 连发会撞网关「系统繁忙」，留出车端处理上一条的时间
            delay(DEFROST_PRECONDITION_MS)
        }

        val variants = Commands.DEFROST_VARIANTS
        var sawSignal = false
        variants.forEachIndexed { index, state ->
            val command = ControlCommand("170", state, "开启前挡除霜")
            // 发不出去就直接抛，交给调用方按网络异常提示，不要假装又试了一组
            withNetworkRetry { api.sendControl(command, pin) }
            if (!waitForResult) {
                // 广播预算很紧的调用方（桌面小组件）只负责把指令送出去
                return "前挡除霜指令已下发"
            }
            when (pollDefrostSignal(api)) {
                true -> return "前挡除霜已开启"
                // 读到过 false 说明这台车会报这个信号，只是没进除霜
                false -> sawSignal = true
                // null = 这个信号压根没上报，换参数也不会变，试下一组没有意义
                null -> return "除霜指令已下发，本车未上报除霜状态"
            }
            // 同 cmdid 连发要留间隔，否则下一组会被网关当「系统繁忙」丢掉
            if (index != variants.lastIndex) delay(DEFROST_VARIANT_GAP_MS)
        }
        return if (sawSignal) {
            "除霜指令已下发，但车端两种出风组合都没有进入除霜状态，请在车上确认前挡是否出风"
        } else {
            "除霜指令已下发，本车未上报除霜状态"
        }
    }

    /**
     * 轮询除霜信号。@return true=已进入除霜；false=读过、但不是除霜态；
     *   null=一次都没读到（车型不上报 1945）。
     */
    private suspend fun pollDefrostSignal(api: LeapmotorApi): Boolean? {
        var last: Boolean? = null
        repeat(DEFROST_CONFIRM_ROUNDS) {
            delay(DEFROST_CONFIRM_GAP_MS)
            val value = runCatching {
                VehicleSignals.bool(pick(api.getVehicleState(), "windshieldDefrost", "1945"))
            }.getOrNull()
            if (value == true) return true
            if (value != null) last = value
        }
        return last
    }

    /**
     * 方向盘加热：发指令 → 读 1816 / 1624 求证 → 「关」没关掉再补一条 `level=1`。
     *
     * ## 为什么不能只看网关回执
     * 云端 `code=0` 只代表**网关收下**这条指令。方向盘加热是车端舒适件，车型之间对
     * `{"level":"0"}`、`{"level":"1"}` 的取舍不一样：文档写 `0`=关，C10 实测 `1`=关。
     * 只按网关说话，用户会收到一个「已开启」而方向盘一点不热（这正是这次反馈的情形）。
     *
     * ## 为什么「开」不重试
     * 开只有 `2` 这一个有效值，再发一遍还是同一个值，只会撞网关限流。读不到信号就如实
     * 说「已下发」，不编成功。
     */
    private suspend fun runSteeringWheelHeat(
        api: LeapmotorApi,
        pin: String,
        on: Boolean,
        waitForResult: Boolean = true,
    ): String {
        withNetworkRetry { api.sendControl(SteeringWheelHeat.build(on), pin) }
        // 不等回执的调用方（桌面小组件，广播预算只有 10 秒）：送出去就返回
        if (!waitForResult) return if (on) "方向盘加热指令已下发" else "关闭方向盘加热指令已下发"

        val first = pollSteeringHeat(api, wantOn = on)
        if (first == true) return if (on) "方向盘加热已开启" else "方向盘加热已关闭"

        if (on) {
            // first == false：读得到、但还没进入加热（车端可能只是慢）；null：本车不报这个信号
            return if (first == false) {
                "方向盘加热指令已发出，但车端还没进入加热状态，请稍后刷新看看"
            } else {
                "方向盘加热指令已下发，本车未上报方向盘加热状态，请在车上确认"
            }
        }

        // 关：只有真的读到「还在加热」才补发 1。
        // 信号不上报的车型补发等于瞎猜 —— 万一那辆车把 1 当弱档，反而会把它打开。
        if (first == null) return "关闭方向盘加热指令已下发，本车未上报方向盘加热状态"
        delay(STEERING_FALLBACK_GAP_MS)
        withNetworkRetry { api.sendControl(SteeringWheelHeat.buildOffAlt(), pin) }
        return when (pollSteeringHeat(api, wantOn = false)) {
            true -> "方向盘加热已关闭"
            false -> "关闭指令已发出，但车端还显示在加热，请在车上确认"
            null -> "关闭指令已发出，本车未上报方向盘加热状态"
        }
    }

    /**
     * 轮询方向盘加热状态。
     *
     * @return true = 已经是目标状态；false = 读到了、但与目标不一致；null = 一次都没读到
     *   （1816 与 1624 都不上报的车型）
     */
    private suspend fun pollSteeringHeat(api: LeapmotorApi, wantOn: Boolean): Boolean? {
        var last: Boolean? = null
        repeat(STEERING_CONFIRM_ROUNDS) {
            delay(STEERING_CONFIRM_GAP_MS)
            val on = readSteeringOn(api) ?: return@repeat
            if (on == wantOn) return true
            last = on
        }
        return last
    }

    /**
     * 读一次方向盘加热是否开着。
     *
     * @return null = 1816 与 1624 都没上报，这台车读不出这个状态（不能当成「关」）
     */
    private suspend fun readSteeringOn(api: LeapmotorApi): Boolean? {
        val st = runCatching { api.getVehicleState() }.getOrNull() ?: return null
        val level = SteeringWheelHeat.levelOf(
            level = seatLevel(st, "steeringWheelHeating", "1816"),
            minutes = seatLevel(st, "steeringWheelHeaterMinutes", "1624"),
        ) ?: return null
        return SteeringWheelHeat.isOn(level)
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
        // 方向盘加热是这套「关」里唯一有两个候选值的一项（0 / 1，见 [SteeringWheelHeat]）。
        // 批量路径不逐条等回执，但值选错就等于没关 —— 统一读一次，还开着就补发 1。
        if (unique.any { SteeringWheelHeat.targetOf(it) == false }) {
            delay(STEERING_FALLBACK_GAP_MS)
            val stillOn = runCatching { readSteeringOn(api) }.getOrNull()
            if (stillOn == true) {
                runCatching { withNetworkRetry { api.sendControl(SteeringWheelHeat.buildOffAlt(), pin) } }
            }
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

    /**
     * 轮询节奏：1s → 2s → 3s → 5s 递进（见 [ControlPollBackoff]）。
     *
     * 以前这里两个分支都写 1000L，注释里说的「首次 1s、随后递进」其实没实现，
     * 结果是一条指令问满 20 次。车端回执基本都在前 6 秒，后面十几次纯属空问。
     * 首个探询时刻不变，慢场景（等 T-Box 唤醒）也本来就问不出更早的结果。
     */
    private fun resultPollDelayMs(attempt: Int): Long = ControlPollBackoff.delayMs(attempt)

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

    /** 开始充电（cmdid 193，`{"value":"start"}`）。 */
    suspend fun startCharging(): String = controlChargeGun("startCharging")

    /**
     * 停止充电（cmdid 193，`{"value":"finish"}`）。
     *
     * 入参用 `finish` 而不是 `stop` —— 车端 OBC 只认 `start` / `finish`，
     * 传 `stop` 云端会受理但车不动，见 [ChargingControlRules] 的说明。
     */
    suspend fun stopCharging(): String = controlChargeGun("stopCharging")

    private suspend fun controlChargeGun(commandName: String): String = withContext(Dispatchers.IO) {
        val pin = sessionStore.loadOpPassword() ?: throw NeedPinException()
        val api = LeapmotorApi(session)
        val cmd = Commands.build(commandName)
        val result = withNetworkRetry { api.sendControl(cmd, pin) }

        // 解锁枪锁止销没有可验证的车况信号，只能等回执。
        // 这两条是机械动作，车端确认比锁车慢得多（实测 3~8 秒），
        // 用普通指令那 4 次（约 2 秒）必然问不到，永远落到「指令已下发…」。
        if (commandName == "unlockCharger") {
            if (!result.hasPollingId()) {
                return@withContext ChargingControlRules.resultMessage(commandName, confirmed = false, state = null)
            }
            for (attempt in 0 until ControlResultPollingPolicy.chargeGunMaxAttempts) {
                Thread.sleep(ControlResultPollingPolicy.delayBeforeAttempt(attempt))
                // 单次查询失败不该让整条指令失败，继续问下一轮
                val resp = runCatching { api.queryControlResult(result.msgID) }.getOrNull() ?: continue
                val confirmed = ChargingControlRules.queryConfirmed(
                    result = resp.opt("result"),
                    code = resp.opt("code"),
                    data = resp.opt("data"),
                )
                if (confirmed) return@withContext ChargingControlRules.resultMessage(commandName, true, null)
            }
            return@withContext ChargingControlRules.resultMessage(commandName, confirmed = false, state = null)
        }

        // 起停充电：**回执和车况一起看**。
        //
        // 只问回执是不够的 —— 车端 OBC 断电、再把 chargeState 报上去要十几秒，
        // 这段时间 `data` 一直是 0。老实现问满 10 秒就放弃、只回读一次车况，
        // 于是明明车已经停了，界面还是「停止充电未被执行，车辆仍在充电」，
        // 用户看到的就是「点了没反应」（2026-11-02 反馈）。
        // 现在每轮问一次回执、隔一轮回读一次车况（顺带把 statusFlow 刷成车端真值，
        // 页面上的大字 SOC / 按钮文案跟着变），车况一翻篇就立刻按成功返回。
        var queryConfirmed = false
        var lastState: Int? = null
        var telemetryError: Exception? = null
        for (round in 0 until ChargingControlRules.CONFIRM_ROUNDS) {
            Thread.sleep(ChargingControlRules.CONFIRM_INTERVAL_MS)
            if (!queryConfirmed && result.hasPollingId()) {
                val resp = runCatching { api.queryControlResult(result.msgID) }.getOrNull()
                if (resp != null) {
                    queryConfirmed = ChargingControlRules.queryConfirmed(
                        result = resp.opt("result"),
                        code = resp.opt("code"),
                        data = resp.opt("data"),
                    )
                }
            }
            // 车况别每轮都拉：16 秒里问 4 次足够，别为一次停止充电连打 8 个请求
            if (round % 2 == 1 || round == ChargingControlRules.CONFIRM_ROUNDS - 1) {
                val state = try {
                    readChargeState(api)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    telemetryError = e
                    null
                }
                if (state != null) {
                    lastState = state
                    if (ChargingControlRules.telemetryMatches(commandName, state)) {
                        return@withContext ChargingControlRules.resultMessage(commandName, true, state)
                    }
                }
            }
        }
        // 一次车况都没读到时，别把「读不到」伪装成「没生效」
        if (lastState == null && telemetryError != null) {
            return@withContext "指令已发送，车况刷新失败，执行结果待确认"
        }
        // 回执到了但车况一直没翻篇（车辆仍在充电）也要说实话，不能拿「待确认」糊过去
        ChargingControlRules.resultMessage(commandName, confirmed = false, state = lastState)
    }

    /**
     * 起停充电期间回读一次车况：把最新值推进 [statusFlow]，让页面上的
     * 电量 / 按钮文案跟着车端走；返回 `chargeState` 供验收判断。
     *
     * 刻意不复用 [refreshStatus]：那条路还要写零记、重画桌面小组件，
     * 一次停止充电里连跑四五遍纯属浪费，外层拿到结果后本来就会正常刷新一次。
     */
    private fun readChargeState(api: LeapmotorApi): Int? {
        val st = withNetworkRetry { api.getVehicleState() }
        val status = mergeComfortOptimistic(buildStatus(st))
        cacheStatus(session.selectedVin, st)
        _status.value = status
        _lastUpdated.value = System.currentTimeMillis()
        StatusSnapshot.put(status, _lastUpdated.value)
        // 起停充电这几秒正好是充电状态翻篇的时刻（1 → 6/0），顺手喂一次观测：
        // 给这次充电盖上「已结束」的戳，下次插枪就能干净地重置起点（见 ChargeCycle）。
        runCatching {
            journal.observeChargeCycle(chargeSampleOf(status, st))
            ChargeWatch.reschedule(appContext, status.chargeState, status.chargeRemainMinutes)
        }
        return status.chargeState
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
