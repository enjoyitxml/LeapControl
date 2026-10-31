package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Curtains
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import com.leapcontrol.app.ChargePlanCyclesHelper
import com.leapcontrol.app.ChargeSchedule
import com.leapcontrol.app.EnergyFormat
import com.leapcontrol.app.HomeCardId
import com.leapcontrol.app.HomeCardLayout
import com.leapcontrol.app.OwnershipStats
import com.leapcontrol.app.QuickPart
import com.leapcontrol.app.QuickSwitchPrefs
import com.leapcontrol.app.QuickTarget
import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleCapabilities
import com.leapcontrol.app.journal.JournalDates
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ============================================================
// 首页 —— 参考零跑官方 App 的浅色沉浸式设计
//   左上：车名（多车时带下拉切换） 右上：车辆健康 + 主题 + 设置
//   下方：大字续航 + 电量进度条 → 车模 → 快捷控车圆钮 → 功能模块
//   背景：春夏秋冬四套简约渐变（SeasonThemes）
// ============================================================

/** 最后一格「编辑」按钮的占位 id。 */
private const val EDIT_CELL = "__edit__"

/**
 * 首页底部「功能模块区」总开关。
 *
 * 当前为 false：模块卡片（胎压/车况/空调/冰箱/里程图…）整体从首页下线，
 * 但 `ui/HomeModules.kt` 里的实现代码与这里的接线都完整保留、照常参与编译，
 * 后续按需要逐项恢复时不必重写。整块恢复只需把这里改回 true。
 */
private const val SHOW_HOME_MODULES = false
/** 控车指令下发后，车端上报车况的滞后补偿时间（再拉一次车况让状态收敛）。 */
private const val AC_SETTLE_DELAY_MS = 3_000L

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
    /** 顶部「皮肤」按钮：打开个性化页（外观模式 / 四季主题 / 动态背景） */
    onOpenSkin: () -> Unit,
    /** 车模这一侧的快捷切换：点白点 / 点车模本体 / 不提供（换当前主题壁纸） */
    onRandomTheme: () -> Unit = {},
    /** 相伴卡这一侧的快捷切换：点白点 / 点卡片本体 / 不提供（换卡片背景，与主题无关） */
    onRandomBackdrop: () -> Unit = {},
    /** 快捷切换的当前配置（设置页里选的小白点 / 点击切换 / 关闭） */
    quickSwitch: QuickSwitchPrefs = QuickSwitchPrefs(),
    /**
     * 当前生效主题是不是用户自己上传的壁纸（且那张图确实还在）。
     *
     * true 时车模这一侧的快捷换壁纸入口一律收起：那是用户从相册里挑、又亲手裁过的图，
     * 点一下就被随机换成内置主题、还找不回来。判定在 MainScreen（season 在那边），
     * 这里只负责按结果渲染。
     */
    themeCustomLocked: Boolean = false,
    /** 向上汇报「当前是否在零控首页主界面」—— 场景动画只在首页主界面跑 */
    onMainPageChange: (Boolean) -> Unit,
    /** 向上汇报「车模是否已滚出可视区」—— 滚出后场景层淡出，改为纯色背景，提高可读性 */
    onCarModelGoneChange: (Boolean) -> Unit,
    /** 向上汇报「首页是否正在滑动」—— 滑动期间冻结动态背景，把整帧预算都留给滚动 */
    onScrollingChange: (Boolean) -> Unit = {},
) {
    // 配色由 MainScreen 通过 LocalAppColors 下发（跟随系统 / 浅色 / 深色）
    val C = LocalAppColors.current
    MaterialTheme(
        colorScheme = if (C.isDark) {
            androidx.compose.material3.darkColorScheme(primary = C.accent, background = C.bgTop)
        } else {
            androidx.compose.material3.lightColorScheme(primary = C.accent, background = C.bgTop)
        },
    ) {
        // 跨 Tab 切换保留滚动位置（随 Activity 重建也能恢复）
        val homeScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        // 车模底边在滚动内容中的纵向坐标（px），用于判断车模是否已滚出可视区
        var carModelBottomY by remember { mutableStateOf(0f) }
        // 车模「整块可点换壁纸」时用的交互源（放在这里而不是 clickable 的 if 分支里）
        val carTapInteraction = remember { MutableInteractionSource() }
        val scope = rememberCoroutineScope()
        // 车况提升到 AppRuntime 持有（跨页面/跨 Tab 保留 + 磁盘缓存），
        // 这里只订阅，不再用 remember 存 —— 切到「我的」再回来不会先变成 null 而闪一下。
        val status by runtime.statusFlow.collectAsState()
        val lastUpdated by runtime.lastUpdatedFlow.collectAsState()
        // 蓝牙钥匙：宿主由 LeapControlApp 下发（进程级单例，设置页也共用同一份状态）
        val bleHost = LocalBleKeyHost.current
        val bleIconVisible by runtime.bleIconVisible
        val tipIconVisible by runtime.tipIconVisible
        var showBleKey by remember { mutableStateOf(false) }
        var showPlaybook by remember { mutableStateOf(false) }
        // ---- 首次打开首页：右上角「玩法说明」的手势引导 ----
        // 落盘只记「看过没看过」（点开说明 / 点不再提示都算看过），一个安装只出现一次；
        // 本次会话里 20 秒没人理它就自动收起，别一直挡着首页。
        val coachContext = LocalContext.current
        var coachVisible by remember { mutableStateOf(!PlaybookHint.isDone(coachContext)) }
        // 玩法说明图标在 root 里的位置：顶栏图标会随「个性化」开关增减，写死偏移一定对不准
        var playbookAnchor by remember { mutableStateOf(Rect.Zero) }
        LaunchedEffect(coachVisible) {
            if (!coachVisible) return@LaunchedEffect
            delay(20_000)
            coachVisible = false
        }
        // 保养 / 保险卡（可加到首页）：数据与设置弹层都复用零记那一套
        val journalData by runtime.journal.data
        // 零记总开关：关掉后首页不再出现保险 / 保养这两张零记卡（顺序仍保留在原地）
        val journalOn by runtime.journalEnabled
        var showInsuranceSheet by remember { mutableStateOf(false) }
        var showMaintenanceSheet by remember { mutableStateOf(false) }
        // 目的地搜索弹层（首页「发送到车机」卡）
        var showDestination by remember { mutableStateOf(false) }
        // 行驶里程 / 能耗（独立接口，拿不到就静默保持上一次结果）
        val energySummary by runtime.mileageEnergyFlow.collectAsState()
        // 周能耗（近 6 周百公里能耗 + 上周能耗构成），两张能耗卡共用
        val weeklyEnergy by runtime.weeklyEnergyFlow.collectAsState()
        // 首页卡片顺序 / 显隐（可自定义）
        val homeCards by runtime.homeCards
        // 卡片编辑态：编辑时卡片区可长按拖动排序、删除、添加
        var cardEditing by remember { mutableStateOf(false) }
        // 相伴卡背景：个性化里切换后要立刻生效，所以直接读 State
        val ownershipBackdropId = runtime.ownershipBackdrop.value
        // 相伴卡的「自定义背景」图：只有当前选的是它才需要解码，
        // 其余 6 套内置图是 drawable，不该为了一张用不到的用户图付出解码成本
        val customBackdropState = runtime.customBackdrop.value
        var ownershipCustomImage by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(ownershipBackdropId, customBackdropState.ownership) {
            ownershipCustomImage = if (ownershipBackdropId == OwnershipBackdrops.CUSTOM_ID) {
                runtime.customBackdrop(CustomBackdropKind.OWNERSHIP)?.asImageBitmap()
            } else {
                null
            }
        }
        var refreshError by remember { mutableStateOf<String?>(null) }
        var actionMessage by remember { mutableStateOf<String?>(null) }
        var controlling by remember { mutableStateOf<String?>(null) }
        // 只在「用户手动下拉刷新」时转圈；来自生命周期的自动刷新全程静默，避免闪动
        var refreshing by remember { mutableStateOf(false) }
        var carName by remember { mutableStateOf(runtime.displayName()) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var showPinDialog by remember { mutableStateOf(false) }
        var pendingCommand by remember { mutableStateOf<String?>(null) }
        var pendingAcTemp by remember { mutableStateOf<Int?>(null) }
        // 空调开/关指令在 PIN 验证完成后重放（记录要重放的指令名）
        var pendingAcCommand by remember { mutableStateOf<String?>(null) }
        // 开启空调时随指令下发的设定温度（PIN 弹窗关掉后要能原样补发，
        // 不能退回「用默认温度开」）
        var pendingAcOnTemp by remember { mutableStateOf<Int?>(null) }
        // 全屏空调页（点首页空调条进入）
        var showAcScreen by remember { mutableStateOf(false) }
        // 空调页里的指令在 PIN 验证完成后重放（记录命令名，含 acSetting: 合成命令）
        var pendingAcPanelCommand by remember { mutableStateOf<String?>(null) }
        // 设定温度的本地草稿：概览条与展开面板共用同一份，两处加减号才不会各调各的
        var pendingAcTempDraft by remember { mutableStateOf<Int?>(null) }
        var controlLayout by remember { mutableStateOf(runtime.controlLayout()) }
        var showControlEditor by remember { mutableStateOf(false) }
        // 车辆配置（冰箱/天窗/哨兵/座椅…）：已持久化的识别结果，不依赖当次车况
        val capabilities by runtime.capabilities.collectAsState()
        // 首帧直接复用 runtime 已解码好的车模，避免重进页面时车模先消失再回来
        // 「显示哪张」由 runtime 决定：改过色就用自定义那张，否则用官方图
        var carBitmap by remember { mutableStateOf(runtime.cachedDisplayCarBitmap()) }
        var vinKey by remember { mutableStateOf(runtime.session.selectedVin) }
        var vehicles by remember { mutableStateOf<List<com.leapcontrol.app.Vehicle>>(emptyList()) }
        var showVehicleMenu by remember { mutableStateOf(false) }
        // ---- 充电中心（二级页 + 两个三级页）----
        // 页面上所有改动先落到 chargeDraft，点「保存并下发设置」才发指令：
        // 健康充电 / 190 预约充电 / 161 电池预热三条连发很容易撞网关限流。
        var showChargingCenter by remember { mutableStateOf(false) }
        var chargeDraft by remember { mutableStateOf(com.leapcontrol.app.ChargeCenterDraft()) }
        var chargeLoading by remember { mutableStateOf(false) }
        var chargeLoadFailed by remember { mutableStateOf(false) }
        var chargeSaving by remember { mutableStateOf(false) }
        var showChargeSchedule by remember { mutableStateOf(false) }
        var showPreheatSchedule by remember { mutableStateOf(false) }
        // 充电枪操作指令在 PIN 验证完成后重放
        var pendingChargeGun by remember { mutableStateOf<String?>(null) }
        // 正在下发的充电枪动作（unlock / start / stop）：充电中心用它把按钮文案改成「…中」
        var chargeGunAction by remember { mutableStateOf<String?>(null) }
        // 二级页面：座椅舒适 / 车载冰箱 / 完整空调
        var showSeatComfort by remember { mutableStateOf(false) }
        var showFridge by remember { mutableStateOf(false) }
        var showClimate by remember { mutableStateOf(false) }
        // 定时空调 / 车辆体检 / 系统升级
        var showScheduledAc by remember { mutableStateOf(false) }
        // 定时空调是从哪个页面进来的：返回时要回到那一页（"ac" 全屏空调 / "climate" 温控页 / null 其它入口）
        var scheduledAcReturnTo by remember { mutableStateOf<String?>(null) }
        var showVehicleHealth by remember { mutableStateOf(false) }
        var showOta by remember { mutableStateOf(false) }
        var acPlan by remember { mutableStateOf(com.leapcontrol.app.ScheduledAcPlan()) }
        var acPlanLoading by remember { mutableStateOf(false) }
        var acPlanLoadFailed by remember { mutableStateOf(false) }
        var acPlanSaving by remember { mutableStateOf(false) }
        var acPlanResult by remember { mutableStateOf<String?>(null) }
        var otaInfo by remember { mutableStateOf<com.leapcontrol.app.VehicleOtaInfo?>(null) }
        var otaLoading by remember { mutableStateOf(false) }
        var otaBusy by remember { mutableStateOf<String?>(null) }
        var otaResult by remember { mutableStateOf<String?>(null) }
        var pendingHealthFix by remember { mutableStateOf<String?>(null) }
        // 一键关闭耗电功能：正在下发 / 进度（已完成, 总数）/ PIN 通过后重放的指令列表
        var powerDrainBusy by remember { mutableStateOf(false) }
        var powerDrainProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var pendingPowerDrains by remember { mutableStateOf<List<String>?>(null) }
        var pendingOtaAction by remember { mutableStateOf<Pair<String, String>?>(null) }
        // 冰箱指令在 PIN 验证完成后重放
        var pendingFridgeCommand by remember {
            mutableStateOf<com.leapcontrol.app.FridgeControlCommand?>(null)
        }
        // ---- 控车二次确认（应用内弹窗确认，仅主界面触发；控制中心磁贴绕过）----
        // 指纹 / 设备密码验证已移除：发起控车时只弹应用内确认框，不再要求验证。
        val confirmGate = remember { runtime.biometricConfirmEnabled() }
        var pendingConfirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

        /**
         * 下发控车指令前的二次确认闸门：
         * - 开关关闭：直接执行，不再弹窗。
         * - 开关开启：弹出应用内确认弹窗（取消 / 确认两个大按钮），点确认后才执行；
         *   点取消或点遮罩放弃本次操作。
         * 注：本函数只包裹「用户主动点击」的入口；PIN 弹窗重放指令时直接调用底层执行函数，
         * 不再重复触发本闸门。
         */
        fun ensureConfirmed(label: String, action: () -> Unit) {
            if (!confirmGate) { action(); return }
            pendingConfirm = label to action
        }

        // ---- 空调设定温度：车端真值 + 本地草稿（规则见 AcTempDraft）----
        // 这里提升到页面层，是因为概览条与展开面板各有一颗加减号，必须共用同一份温度。
        val acOnNow = status?.acOn == true
        val remoteAcTemp = AcTempDraft.parse(status?.acTargetTemp)
        // 草稿什么时候交还给车端（配合 AcTempDraft.shown 的「草稿优先」）：
        // ① 车端回报了同一个温度 → 指令已生效，草稿没用了；
        // ② 车端回报了一个「新出现的、和草稿不同」的温度 → 有人直接在车机上改了，以车端为准。
        // 只判 ① 的话，用户点完温度又去车机上改，界面会一直卡在草稿上不动。
        var lastRemoteAcTemp by remember { mutableStateOf(remoteAcTemp) }
        LaunchedEffect(remoteAcTemp, pendingAcTempDraft) {
            val draft = pendingAcTempDraft
            if (draft != null) {
                if (remoteAcTemp == draft) {
                    pendingAcTempDraft = null
                } else if (remoteAcTemp != null && remoteAcTemp != lastRemoteAcTemp) {
                    pendingAcTempDraft = null
                }
            }
            lastRemoteAcTemp = remoteAcTemp
        }
        val acTargetTemp = AcTempDraft.shown(acOnNow, remoteAcTemp, pendingAcTempDraft)

        LaunchedEffect(journalOn) {
            vehicles = runCatching { runtime.vehicles() }.getOrDefault(emptyList())
            // 首页可能挂着保养 / 保险卡：零记的数据是按需加载的，这里主动读一次，
            // 否则没进过零记就等于「没有计划」，卡片会一直显示「去设置」。
            // 一个 VIN 只读一次盘（JournalRuntime 内部按 VIN 缓存），代价可以忽略。
            // 零记关掉时不读：那两张卡根本不会渲染，白解析一遍 JSON 没有意义。
            if (journalOn) runCatching { runtime.journal.ensureLoaded() }
        }

        // 二级页（充电中心 / 座椅 / 空调 / 体检 / OTA…）不属于「首页」，
        // 打开时向 MainScreen 汇报，让场景动画只在首页主界面跑
        val onMainPage = !showChargingCenter && !showSeatComfort && !showFridge && !showClimate &&
            !showAcScreen && !showScheduledAc && !showVehicleHealth && !showOta &&
            !showControlEditor && !showBleKey && !showPlaybook
        LaunchedEffect(onMainPage) { onMainPageChange(onMainPage) }

        // ---- 系统返回（返回键 / 左右滑返回手势）：逐级退回，不再直接退出 App ----
        // 顺序与下面渲染用的 else-if 链一致：三级页（预约充电 / 预约预热）→ 定时空调
        // （回到进来时的那一页）→ 其余二级页 → 首页主界面。
        BackHandler(enabled = !onMainPage) {
            when {
                showChargeSchedule -> showChargeSchedule = false
                showPreheatSchedule -> showPreheatSchedule = false
                showScheduledAc -> {
                    showScheduledAc = false
                    when (scheduledAcReturnTo) {
                        "ac" -> showAcScreen = true
                        "climate" -> showClimate = true
                    }
                    scheduledAcReturnTo = null
                }
                showOta -> showOta = false
                showVehicleHealth -> showVehicleHealth = false
                showAcScreen -> showAcScreen = false
                showClimate -> showClimate = false
                showFridge -> showFridge = false
                showSeatComfort -> showSeatComfort = false
                showChargingCenter -> {
                    // 与 ChargingScreen 的 onBack 保持一致：三级页开关一并复位
                    showChargeSchedule = false
                    showPreheatSchedule = false
                    showChargingCenter = false
                }
                showControlEditor -> showControlEditor = false
                // 蓝牙钥匙页自己没注册返回处理，不在这里兜底就会一滑直接退出 App
                showBleKey -> showBleKey = false
                showPlaybook -> showPlaybook = false
            }
        }

        // ---- 车模滚出可视区 / 正在滑动：两个「滚动派生信号」----
        // 这里绝对不能在组合期直接读 homeScrollState.value：它在滑动时每帧都会变，
        // 组合期读它等于让整个首页（近 3000 行的 composable）每帧重组一次，
        // 真机上就是肉眼可见的掉帧。改用 snapshotFlow —— 只在布尔值真正翻转时回调一次。
        LaunchedEffect(carModelBottomY) {
            // 车模还没测量出位置时（初值 0）别急着报「已滑出」，否则首帧场景会被压暗
            if (carModelBottomY <= 0f) return@LaunchedEffect
            snapshotFlow { homeScrollState.value >= carModelBottomY - 2f }
                .distinctUntilChanged()
                .collect { onCarModelGoneChange(it) }
        }
        LaunchedEffect(homeScrollState) {
            snapshotFlow { homeScrollState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { onScrollingChange(it) }
        }

        // 进入首页时拉取一次官方车模图（本地有缓存则直接用）。
        // 车模默认常开（设置里的显示开关已移除），不再有「关闭车模」这条分支；
        // 依赖 vinKey：切换车辆后自动重新拉取
        // 依赖 carModelRevision：在「车模外观」里换色 / 导入 / 恢复官方后立刻换图
        val carModelRevision by runtime.carModelRevision
        LaunchedEffect(vinKey, carModelRevision) {
            carBitmap = runCatching { runtime.displayCarBitmap() }.getOrNull()
        }

        /**
         * 拉取里程 / 能耗 / 周能耗 —— 也就是首页「近 7 天行程」「周能耗分布」「近 6 周能耗」
         * 三张卡的数据。
         *
         * 这三张卡的接口和车况接口是分开的，早先**只在进入首页时拉一次**（15 分钟缓存），
         * 于是下拉刷新、回到前台、网络恢复都只刷了车况，三张卡的数字纹丝不动 ——
         * 用户反馈的「近 7 天 / 周能耗不更新」就是这个原因。
         * 现在凡是「用户主动刷新」或「重新回到首页」都带上它们：
         *  - 非强制（force=false）走 15 分钟缓存，回到前台、网络恢复这类自动触发不会刷接口；
         *  - 强制（force=true）用于下拉刷新、跨天、切换车辆，用户要的就是当场拿新数。
         */
        fun refreshEnergy(force: Boolean = false) {
            scope.launch { runCatching { runtime.refreshMileageEnergy(force) } }
            scope.launch { runCatching { runtime.refreshWeeklyEnergy(force) } }
        }

        // 进入首页（或切换车辆）时拉一次里程 / 能耗；15 分钟内不重复请求，
        // 失败也不打扰用户 —— 模块卡保持上一次结果或显示「--」。
        LaunchedEffect(vinKey) {
            refreshEnergy()
        }

        // 去重：2 秒内的重复请求忽略（避免冷启动时启动刷新与前台事件叠加）
        var lastRequestAt = 0L
        /**
         * 拉取车况。结果由 runtime.statusFlow 直接下发，UI 不做「先清空再填充」。
         *
         * @param silent true = 静默刷新（默认）：不显示转圈、不改右上角文案，车况到位自动替换数值；
         *               false = 用户手动下拉刷新，才显示下拉指示器，并且**连三张能耗卡一起刷**
         *               （用户的心智是「下拉刷新 = 页面上所有数据都刷新」）。
         */
        fun refresh(force: Boolean = false, silent: Boolean = true) {
            val now = System.currentTimeMillis()
            if (!force && now - lastRequestAt < 2_000) return
            lastRequestAt = now
            // 锁屏切回时网络可能尚未就绪：直接跳过，等网络恢复回调再刷，避免报 DNS 错误
            if (!runtime.isNetworkAvailable()) {
                refreshError = "网络暂不可用，恢复后自动刷新"
                refreshing = false
                return
            }
            scope.launch {
                if (!silent) refreshing = true
                try {
                    runtime.refreshStatus()
                    refreshError = null
                } catch (e: Exception) {
                    refreshError = friendlyError(e)
                }
                if (!silent) {
                    // 下拉刷新：能耗 / 里程也强制拉一遍，转圈保持到它们回来
                    runCatching { runtime.refreshMileageEnergy(force = true) }
                    runCatching { runtime.refreshWeeklyEnergy(force = true) }
                }
                if (!silent) refreshing = false
            }
        }

        // 冷启动：进入首页刷新一次
        LaunchedEffect(Unit) {
            refresh(force = true)
        }
        // 从后台切回前台时刷新：车况必刷，能耗走缓存（超过 15 分钟才会真的请求）
        LaunchedEffect(Unit) {
            ForegroundSignal.events.collect {
                refresh()
                refreshEnergy()
            }
        }
        // 网络恢复：仅当上一次刷新失败时补刷一次（避免频繁请求）
        LaunchedEffect(Unit) {
            ForegroundSignal.networkEvents.collect {
                if (status == null || refreshError != null) {
                    refresh()
                    refreshEnergy()
                }
            }
        }
        // 注册网络恢复监听
        DisposableEffect(Unit) {
            val unregister = runtime.registerNetworkRestoreListener()
            onDispose { unregister() }
        }
        // 行驶中提高刷新频率：车在跑时挡位 / 车速变化快，靠「切前台才刷」跟不上，
        // 停在首页主界面时每 12 秒静默回拉一次（不转圈、不改文案）
        val drivingNow = status?.isDriving == true
        LaunchedEffect(drivingNow, onMainPage) {
            while (drivingNow && onMainPage) {
                delay(12_000)
                refresh()
            }
        }
        // 跨天：近 7 日窗口整体前移一天，周能耗也会换周。
        // 只在主界面、一分钟看一次本地日期，不请求接口；真跨天了才强刷一次。
        // （车机 / 常亮设备上 App 可能连着跑好几天，光靠「进页面才拉」会一直停在旧的一天。）
        LaunchedEffect(onMainPage) {
            if (!onMainPage) return@LaunchedEffect
            var day = JournalDates.dayKey(JournalDates.todayStart())
            while (true) {
                delay(60_000)
                val now = JournalDates.dayKey(JournalDates.todayStart())
                if (now != day) {
                    day = now
                    refresh(force = true)
                    refreshEnergy(force = true)
                }
            }
        }

        /** cellId 为按钮 id；复合按钮（后备箱/车窗/遮阳帘）在此解析成开或关指令。 */
        fun runControl(cellId: String) {
            scope.launch {
                val command = ControlActions.resolveCommand(cellId, status)
                controlling = cellId
                actionMessage = null
                try {
                    actionMessage = runtime.control(command)
                    // 复合按钮执行后立刻回拉车况，让高亮状态跟着变
                    if (ControlActions.find(cellId)?.toggle == true) refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingCommand = cellId
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        /** 空调模块：按指定温度下发空调指令（参考原项目 Commands.buildAc）。 */
        fun runAcTemperature(temp: Int, onFailed: (() -> Unit)? = null) {
            scope.launch {
                controlling = "acTemp"
                actionMessage = null
                try {
                    actionMessage = runtime.control("acOn", presetAcTemperature = temp)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingAcTemp = temp
                    showPinDialog = true
                } catch (e: Exception) {
                    // 没发出去就把界面上刚跳的温度退回去，别让页面显示一个车上根本没有的温度
                    onFailed?.invoke()
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        /**
         * 空调设定温度加减：**点一下就确认、确认就下发**，不再攒成草稿等点电源。
         *
         * 以前空调关着时只改本地草稿，页面上温度变了、车上没变，要等返回首页点电源才带上，
         * 看上去就像「指令要等返回才发出去」。这里统一成一条路径：
         * 空调开着 = 改设定温度；关着 = 开空调并把温度设成这个值，两者都要先问一句。
         */
        fun stepAcTemp(delta: Int) {
            val next = AcTempDraft.step(acTargetTemp, delta)
            if (next == acTargetTemp) return
            // 温度 ± 是连续调整，按下即下发（不弹确认）；失败时把显示值退回原值
            val previous = acTargetTemp
            pendingAcTempDraft = next
            runAcTemperature(next) { pendingAcTempDraft = previous }
        }

        /**
         * 空调展开面板里的指令统一入口。
         *
         * 命令名由面板按当前车况拼好（快捷场景直接用 `quickCool` / `defrost` 这类，
         * 风量 / 出风方向 / 循环用 [com.leapcontrol.app.Commands.acSettingName] 合成，
         * 座椅类用 `driverSeatHeating_2` 这种「前缀:档位」），这里只负责下发 + 回拉车况。
         */
        fun runAcPanelCommand(commandName: String, onFailed: (() -> Unit)? = null) {
            scope.launch {
                controlling = commandName
                actionMessage = null
                try {
                    actionMessage = runtime.control(commandName)
                } catch (e: NeedPinException) {
                    // 密码框取消才会真的失败，这里先不回滚：用户补完密码会重发同一条
                    pendingAcPanelCommand = commandName
                    showPinDialog = true
                    controlling = null
                    return@launch
                } catch (e: Exception) {
                    // 下发失败：把界面上已经乐观改掉的那一项退回原值。
                    // 不回滚的话，界面显示 3 档、车上还是 0 档，两边对不上。
                    onFailed?.invoke()
                    actionMessage = friendlyError(e)
                }
                // 先放开按钮（不阻塞用户继续操作），回拉放在后面：
                // 车端上报 acSetting / 座椅档位通常滞后 2~3 秒，只拉一次会读到旧值，
                // 界面看着像「指令没生效」。
                refresh(force = true)
                controlling = null
                delay(AC_SETTLE_DELAY_MS)
                refresh(force = true)
            }
        }

        /**
         * 空调「开启 / 关闭」下发：[turnOn] 为 true 固定发 acOn，false 固定发 acOff。
         *
         * 与之前「按推断状态取反」的做法不同：这里**不做状态推断**，点哪个按钮就发哪条指令，
         * 因此不会出现「因为状态读不到而一直发 acOn」的问题。
         *
         * [temperature] 是界面上当前的设定温度。车端没有单独的调温指令，温度只是「开空调」
         * 指令里的一个字段（[Commands.buildAc]），所以只有开启时才有意义——关闭指令一律不带。
         * 指令返回后强制回拉车况（refresh），把真实的 acSwitch / acSetting 状态刷回界面。
         */
        fun runAcCommand(turnOn: Boolean, temperature: Int? = null) {
            scope.launch {
                val command = if (turnOn) "acOn" else "acOff"
                val preset = if (turnOn) temperature else null
                controlling = command
                actionMessage = null
                try {
                    actionMessage = runtime.control(command, presetAcTemperature = preset)
                    // 关键：回拉真实车况，刷新首页空调状态展示
                    refresh(force = true)
                    // 车端上报 acSwitch / acSetting 通常滞后几秒，再拉一次让状态收敛，
                    // 避免刚下发就回读到旧值、状态展示与实际不符
                    delay(AC_SETTLE_DELAY_MS)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingAcCommand = command
                    pendingAcOnTemp = preset
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        /**
         * 回读车端充电设置，用来填页面草稿（不用本地缓存冒充真值）：
         * - 健康充电开关 → 车况信号 48
         * - 充电上限 / 预约充电时段与重复日 → 云端 commonConfig 的充电计划
         * - 电池预热 → `cmdid=161` getappointment
         *
         * 两个云端来源都失败才置 [chargeLoadFailed]，页面据此显示「重试」；
         * 只失败一项时保留草稿里的旧值，不把用户的设置清掉。
         */
        fun loadChargeCenter() {
            if (chargeLoading) return
            scope.launch {
                chargeLoading = true
                chargeLoadFailed = false
                // 先静默拉一次车况：健康充电开关（信号 48）与电量 / 电池温度都来自车况，
                // 只读组合期捕获的 status 会拿到进入页面之前那一份旧值
                runCatching { runtime.refreshStatus() }
                val live = runtime.statusFlow.value
                val plan = runCatching { runtime.loadChargePlan() }.getOrNull()
                val preheat = runCatching { runtime.loadBatteryPreheatSchedule() }.getOrNull()
                if (plan == null && preheat == null) chargeLoadFailed = true
                val current = chargeDraft
                chargeDraft = current.copy(
                    healthy = live?.healthyChargeEnabled ?: current.healthy,
                    // 车端回读的 percent 也过一次 sanitizeSoc：历史遗留值（比如 50）会越界，
                    // 不清掉就会出现「标题写 50%、圆钮却停在 80%」的矛盾
                    limitSoc = plan?.percent?.let { ChargeSchedule.sanitizeSoc(it) }
                        ?: current.limitSoc,
                    scheduledEnabled = plan?.isEnable ?: current.scheduledEnabled,
                    scheduledStart = ChargeSchedule.timeOfDay(plan?.beginTime) ?: current.scheduledStart,
                    scheduledEnd = ChargeSchedule.timeOfDay(plan?.endTime) ?: current.scheduledEnd,
                    scheduledDays = plan?.cycles
                        ?.let { ChargePlanCyclesHelper.parseToDaySet(it) }
                        ?: current.scheduledDays,
                    continueUntilLimit = plan?.recharge ?: current.continueUntilLimit,
                    preheatEnabled = preheat?.hasSchedule ?: current.preheatEnabled,
                    preheatStart = preheat?.startTime ?: current.preheatStart,
                    preheatDays = preheat?.days
                        ?.let { ChargePlanCyclesHelper.parseToDaySet(it) }
                        ?: current.preheatDays,
                )
                chargeLoading = false
            }
        }

        /** 充电中心：一次性下发健康充电 / 预约充电 / 电池预热，再回读车端真实值。 */
        fun saveCharging() {
            if (chargeSaving) return
            scope.launch {
                chargeSaving = true
                actionMessage = null
                val draft = chargeDraft
                actionMessage = runCatching {
                    runtime.applyChargingSettings(
                        healthyCharge = draft.healthy,
                        chargeLimit = draft.limitSoc,
                        reserveCharge = draft.scheduledEnabled,
                        batteryPreheat = draft.preheatEnabled,
                        scheduledStart = draft.scheduledStart,
                        scheduledEnd = draft.scheduledEnd,
                        continueUntilLimit = draft.continueUntilLimit,
                        scheduledDays = draft.scheduledDays,
                        preheatStart = draft.preheatStart,
                        preheatDays = draft.preheatDays,
                    )
                }.fold(
                    onSuccess = { "充电设置：$it" },
                    onFailure = { friendlyError(it) },
                )
                chargeSaving = false
                // 下发后回读：车端可能纠正（比如设的值被取整），让界面显示车端真值
                loadChargeCenter()
            }
        }

        // 进入充电中心时回读车端设置；离开再进来会再读一次，方便在车上改过之后同步
        LaunchedEffect(showChargingCenter) {
            if (showChargingCenter) loadChargeCenter()
        }

        /** 定时空调：进入页面时拉回车端计划回填；失败时保留默认草稿让用户手动下发。 */
        LaunchedEffect(showScheduledAc) {
            if (!showScheduledAc) return@LaunchedEffect
            acPlanLoading = true
            acPlanLoadFailed = false
            acPlanResult = null
            val state = runCatching { runtime.loadScheduledAc() }.getOrNull()
            if (state != null) acPlan = state.plan.copy(enabled = state.hasSchedule)
            else acPlanLoadFailed = true
            acPlanLoading = false
        }

        /** 系统升级：进入页面时查询车辆固件版本。 */
        LaunchedEffect(showOta) {
            if (!showOta) return@LaunchedEffect
            otaLoading = true
            otaResult = null
            otaInfo = runCatching { runtime.loadOtaInfo() }.getOrNull()
            otaLoading = false
        }

        /** 定时空调保存 / 取消下发。 */
        fun saveScheduledAc(plan: com.leapcontrol.app.ScheduledAcPlan) {
            scope.launch {
                acPlanSaving = true
                acPlanResult = runCatching { runtime.applyScheduledAc(plan) }
                    .fold(onSuccess = { it }, onFailure = { friendlyError(it) })
                if (plan.enabled) acPlan = plan
                acPlanSaving = false
                refresh(force = true)
            }
        }

        /** 体检项一键修复：复用已有控车通道（可能需要控车密码）。 */
        fun runHealthFix(commandName: String) {
            scope.launch {
                controlling = commandName
                actionMessage = null
                try {
                    actionMessage = runtime.control(commandName)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingHealthFix = commandName
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        /**
         * 一键关闭所有正在耗电的舒适功能。
         *
         * 指令一次性连续下发（[AppRuntime.shutDownPowerDrains] 内部按 800ms 间隔排好），
         * 不逐条等执行回执 —— 否则四项要等一分多钟；发完统一回拉一次车况看效果，
         * 卡片上的耗电项会跟着消失。
         */
        fun runShutDownDrains(commands: List<String>) {
            if (commands.isEmpty() || powerDrainBusy) return
            scope.launch {
                powerDrainBusy = true
                powerDrainProgress = null
                actionMessage = null
                try {
                    actionMessage = runtime.shutDownPowerDrains(commands) { done, total ->
                        powerDrainProgress = done to total
                    }
                    // 车端执行有延迟：立刻回拉大概率还是「运行中」，等 4 秒再拉一次，
                    // 卡片上的耗电项才会真的消失（否则用户会以为没关掉）。
                    refresh(force = true)
                    delay(4_000)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingPowerDrains = commands
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                powerDrainBusy = false
                powerDrainProgress = null
            }
        }

        /** OTA 动作：download / install / schedule。 */
        fun runOtaAction(action: String, arg: String) {
            scope.launch {
                otaBusy = action
                otaResult = null
                try {
                    val taskId = otaInfo?.taskId.orEmpty()
                    otaResult = when (action) {
                        "download" -> runtime.downloadOta(taskId)
                        "install" -> runtime.installOta(taskId)
                        else -> runtime.scheduleOta(taskId, arg)
                    }
                    delay(2_000)
                    otaInfo = runCatching { runtime.loadOtaInfo() }.getOrNull()
                } catch (e: NeedPinException) {
                    pendingOtaAction = action to arg
                    showPinDialog = true
                } catch (e: Exception) {
                    otaResult = friendlyError(e)
                }
                otaBusy = null
            }
        }

        // 首页入口处的副标题：体检摘要与升级状态（避免为此多请求接口）
        val healthReport = remember(status, capabilities) { VehicleHealthDiagnostics.evaluate(status, capabilities) }
        val healthSummary = healthReport.summary +
            (healthReport.issues.takeIf { it.isNotEmpty() }?.let { "（${it.size} 项待处理）" } ?: "")
        val otaSubtitle = otaInfo?.let {
            if (it.hasNewVersion && it.newVersion.isNotBlank()) "有新版本 ${it.newVersion}" else "当前已是最新版本"
        } ?: "点击查看固件版本"

        /** 充电枪操作：解锁充电枪 / 开始充电 / 停止充电（需控车密码）。 */
        fun runChargeGun(action: String) {
            scope.launch {
                controlling = "chargeGun"
                // 记下具体是哪一条，充电中心页据此把对应按钮变成「解锁中 / 启动中 / 停止中」：
                // 这两条指令的回执窗口有 10 秒，按钮纯禁用会像点空了
                chargeGunAction = action
                actionMessage = null
                try {
                    val msg = when (action) {
                        "unlock" -> runtime.unlockCharger()
                        "start" -> runtime.startCharging()
                        else -> runtime.stopCharging()
                    }
                    actionMessage = msg
                    refresh(force = true)
                    // 车端的 chargeState 要滞后几秒才翻转，这里补一次刷新，
                    // 让「充电中心」的读数与按钮文案跟着变；放在独立协程里，
                    // 不能延长按钮的禁用时间
                    scope.launch {
                        delay(AC_SETTLE_DELAY_MS)
                        refresh(force = true)
                    }
                } catch (e: NeedPinException) {
                    pendingChargeGun = action
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
                chargeGunAction = null
            }
        }

        /** 车载冰箱：构造指令下发（cmdid 500），未设控车密码时弹 PIN 输入框。 */
        fun runFridgeCommand(command: com.leapcontrol.app.FridgeControlCommand) {
            scope.launch {
                controlling = "fridge"
                actionMessage = null
                try {
                    actionMessage = runtime.controlFridge(command)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingFridgeCommand = command
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        // 二级页：座椅舒适 / 车载冰箱 / 完整空调 / 充电中心（覆盖首页内容）
        //
        // 整条分支链外面套一层 Box：指令结果条与二次确认弹窗挂在链**外面**，
        // 这样在空调 / 充电中心这类二级页里下发指令时也能看到「成功 / 失败」和确认框
        // （以前这两个组件只在首页分支里渲染，二级页点了像没反应）。
        Box(modifier = Modifier.fillMaxSize()) {
        if (showSeatComfort) {
            SeatComfortScreen(
                onBack = { showSeatComfort = false },
                status = status,
                busy = controlling != null,
                onCommand = { runControl(it) },
                caps = capabilities,
            )
        } else if (showFridge) {
            FridgeScreen(
                onBack = { showFridge = false },
                status = status,
                busy = controlling != null,
                onControlFridge = { runFridgeCommand(it) },
            )
        } else if (showClimate) {
            ClimateScreen(
                onBack = { showClimate = false },
                status = status,
                busy = controlling != null,
                onCommand = { cmd ->
                    // 空调开/关改成显式指令路由，不再依赖状态推断
                    when (cmd) {
                        "acOn" -> runAcCommand(true)
                        "acOff" -> runAcCommand(false)
                        else -> runControl(cmd)
                    }
                },
                onTempChange = { runAcTemperature(it) },
                busyOn = controlling == "acOn",
                busyOff = controlling == "acOff",
                // 「开启空调」按页面上的设定温度下发（关机态调温只改草稿，不再顺手开空调）
                onAcPower = { turnOn, temp ->
                    ensureConfirmed(
                        if (turnOn) "开启空调（${temp}℃）" else "关闭空调",
                    ) { runAcCommand(turnOn, temp) }
                },
                // 定时空调是同级二级页：必须先收起空调页，否则会被上面的分支挡住不显示
                onOpenScheduledAc = {
                    scheduledAcReturnTo = "climate"
                    showClimate = false
                    showScheduledAc = true
                },
            )
        } else if (showAcScreen) {
            // 首页空调条点进来的全屏空调页：温度草稿与首页共用（acTargetTemp），
            // 所以两处加减号不会各调各的
            AcScreen(
                status = status,
                caps = capabilities,
                busy = controlling != null,
                targetTemp = acTargetTemp,
                // 正在下发的指令名：车控指令最长要等 24 秒才回执，这段时间页面必须有东西在动，
                // 否则用户以为点了没反应（以前二级页连结果条都看不到）
                busyLabel = controlling?.let { acCommandLabel(it) },
                onRefresh = { refresh(force = true) },
                onBack = { showAcScreen = false },
                onTempStep = { stepAcTemp(it) },
                onPower = { on -> runAcCommand(on, acTargetTemp) },
                // 空调页内一律「按下即下发」，不再弹二次确认（用户明确要求回到直接生效的版本）。
                // rollback 保留：下发失败时把界面已改的档位退回原值，避免界面与车端不一致。
                onCommand = { name, rollback -> runAcPanelCommand(name, rollback) },
                onOpenScheduledAc = {
                    scheduledAcReturnTo = "ac"
                    showAcScreen = false
                    showScheduledAc = true
                },
            )
        } else if (showChargeSchedule) {
            // 预约充电三级页：只改草稿，确定后回到充电中心，仍要点「保存并下发设置」
            ScheduledChargeScreen(
                start = chargeDraft.scheduledStart,
                end = chargeDraft.scheduledEnd,
                days = chargeDraft.scheduledDays,
                continueUntilLimit = chargeDraft.continueUntilLimit,
                onConfirm = { newStart, newEnd, newDays, newContinue ->
                    chargeDraft = chargeDraft.copy(
                        scheduledStart = newStart,
                        scheduledEnd = newEnd,
                        scheduledDays = newDays,
                        continueUntilLimit = newContinue,
                    )
                    showChargeSchedule = false
                },
                onClose = { showChargeSchedule = false },
            )
        } else if (showPreheatSchedule) {
            ScheduledPreheatScreen(
                start = chargeDraft.preheatStart,
                days = chargeDraft.preheatDays,
                onConfirm = { newStart, newDays ->
                    chargeDraft = chargeDraft.copy(preheatStart = newStart, preheatDays = newDays)
                    showPreheatSchedule = false
                },
                onClose = { showPreheatSchedule = false },
            )
        } else if (showChargingCenter) {
            ChargingScreen(
                status = status,
                draft = chargeDraft,
                onDraftChange = { chargeDraft = it },
                loading = chargeLoading,
                loadFailed = chargeLoadFailed,
                saving = chargeSaving,
                gunBusy = controlling == "chargeGun",
                pendingGun = chargeGunAction,
                onGunAction = { runChargeGun(it) },
                onRetryLoad = { loadChargeCenter() },
                onOpenScheduled = { showChargeSchedule = true },
                onOpenPreheat = { showPreheatSchedule = true },
                onSave = { saveCharging() },
                // 退出时把三级页开关一并复位，否则下次进来会直接落在三级页
                onBack = {
                    showChargeSchedule = false
                    showPreheatSchedule = false
                    showChargingCenter = false
                },
            )
        } else if (showScheduledAc) {
            ScheduledAcScreen(
                plan = acPlan,
                onPlanChange = { acPlan = it },
                loading = acPlanLoading,
                loadingFailed = acPlanLoadFailed,
                onReload = {
                    acPlanLoading = true
                    acPlanLoadFailed = false
                    scope.launch {
                        val state = runCatching { runtime.loadScheduledAc() }.getOrNull()
                        if (state != null) acPlan = state.plan.copy(enabled = state.hasSchedule)
                        else acPlanLoadFailed = true
                        acPlanLoading = false
                    }
                },
                saving = acPlanSaving,
                result = acPlanResult,
                onSave = { saveScheduledAc(acPlan) },
                onCancel = { saveScheduledAc(acPlan.copy(enabled = false)) },
                // 定时空调有两个入口：全屏空调页与车内温控页，返回时必须回到来时的那一页
                onBack = {
                    showScheduledAc = false
                    when (scheduledAcReturnTo) {
                        "ac" -> showAcScreen = true
                        "climate" -> showClimate = true
                    }
                    scheduledAcReturnTo = null
                },
            )
        } else if (showVehicleHealth) {
            VehicleHealthScreen(
                status = status,
                capabilities = capabilities,
                busy = controlling,
                onFix = { runHealthFix(it) },
                onRefresh = { refresh(force = true) },
                onBack = { showVehicleHealth = false },
            )
        } else if (showOta) {
            OtaScreen(
                info = otaInfo,
                loading = otaLoading,
                busy = otaBusy,
                result = otaResult,
                onAction = { action, arg -> runOtaAction(action, arg) },
                onReload = {
                    otaLoading = true
                    scope.launch {
                        otaInfo = runCatching { runtime.loadOtaInfo() }.getOrNull()
                        otaLoading = false
                    }
                },
                onBack = { showOta = false },
            )
        } else if (showBleKey && bleHost != null) {
            BluetoothKeyScreen(host = bleHost, onBack = { showBleKey = false })
        } else if (showPlaybook) {
            PlaybookScreen(onBack = { showPlaybook = false }, journalEnabled = journalOn)
        } else {
            // 外层 Box：承载悬浮反馈条（指令结果必须让用户看到，放在滚动列表底部等于看不见）
            Box(modifier = Modifier.fillMaxSize()) {
            // 固定顶栏：车名 / 更新时间 / 车辆健康 / 皮肤，不随内容滚动
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 22.dp, end = 22.dp)) {
                // 车名与右侧图标**放在同一行、一起垂直居中**。
                // 原来是「图标 align(TopEnd) + 车名 align(CenterStart)」两套对齐：
                // 系统字号调大后车名那一行变高，图标就浮在车名上方看着错位（实机反馈的就是这个）。
                // 放进同一个 Row 之后，字号多大都对着车名居中。
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 车名：点一下直接改名（原来那个铅笔按钮已去掉，入口合并到车名本身，
                        // 省一个图标位、也少一次「先找按钮」的判断）
                        Text(
                            text = carName,
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            // 大字号下宁可省略也不折行：折行会把整块顶栏撑高、图标跟着跑
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 180.dp)
                                .clickable { showRenameDialog = true },
                        )
                        // 账号下有多辆车时才有下拉箭头：**箭头单独可点**用于切换车辆，
                        // 和「点车名改名」分成两个热区，互不抢
                        if (vehicles.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable { showVehicleMenu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "切换车辆",
                                    tint = LocalAppColors.current.textPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        // 车名与图标之间用弹簧隔开：车名再长也只会吃掉中间的空隙
                        Spacer(Modifier.weight(1f))
                        // 右侧图标按钮：玩法说明 → 蓝牙钥匙 → 个性化（皮肤）。
                        // 靠右排列，所以中间任何一个关掉，后面的会自然往右补位。
                        // 玩法说明：换车模 / 换背景 / 快捷控车这些「怎么用」都收在这一页，
                        // 默认显示，个性化里可以关掉
                        if (tipIconVisible) {
                            HeaderIconButton(
                                iconRes = R.drawable.ic_playbook,
                                description = "玩法说明",
                                size = 23.dp,
                                // 记录下来给新手引导的光环/手势定位用
                                modifier = Modifier.onGloballyPositioned {
                                    playbookAnchor = it.boundsInRoot()
                                },
                                onClick = {
                                    // 点开说明就算「看过了」，别再提示
                                    PlaybookHint.markDone(coachContext)
                                    coachVisible = false
                                    showPlaybook = true
                                },
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        // 蓝牙钥匙：不可用（没配对 / 没开 / 没权限）时是灰的，点进去就能开启
                        if (bleHost != null && bleIconVisible) {
                            HeaderIconButton(
                                iconRes = R.drawable.ic_phosphor_key,
                                description = "蓝牙钥匙",
                                tint = when (bleHost.status) {
                                    BleKeyStatus.ACTIVE -> LocalAppColors.current.accent
                                    BleKeyStatus.STANDBY -> LocalAppColors.current.textPrimary
                                    else -> LocalAppColors.current.textSecondary.copy(alpha = 0.45f)
                                },
                                onClick = { showBleKey = true },
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        HeaderIconButton(
                            iconRes = R.drawable.ic_skin,
                            description = "皮肤",
                            onClick = onOpenSkin,
                        )
                    }
                    // 车况更新时间：紧贴车名下方，点一下可手动刷新
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !refreshing) { refresh() },
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.6.dp,
                                color = LocalAppColors.current.textPrimary,
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = when {
                                refreshing -> "更新中…"
                                lastUpdated == 0L -> "尚未更新"
                                else -> "更新于 ${formatUpdateTime(lastUpdated)}"
                            },
                            color = LocalAppColors.current.textPrimary.copy(alpha = 0.7f),
                            fontSize = 11.5.sp,
                        )
                    }
                    VehicleDropdown(
                        expanded = showVehicleMenu,
                        vehicles = vehicles,
                        selectedVin = vinKey,
                        onDismiss = { showVehicleMenu = false },
                        onSelect = { vehicle ->
                            showVehicleMenu = false
                            if (vehicle.vin != vinKey) {
                                scope.launch {
                                    runtime.selectVehicle(vehicle)
                                    vinKey = vehicle.vin
                                    carName = runtime.displayName()
                                    controlLayout = runtime.controlLayout()
                                    runtime.loadCachedStatus()
                                    refreshError = null
                                    refresh(force = true)
                                }
                            }
                        },
                    )
                }
            }

            // ---- 可滚动内容区（续航 / 充电中心 / 车模 / 快捷控车 / 模块）----
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refresh(force = true, silent = false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 滚动内容的「最小高度」固定为可视区高度：这样即便内容不足一屏，
            // 底部占位块也能被下面的 weight 弹簧压到最底部（贴近底部导航栏）。
            val pageHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(homeScrollState)
                    .heightIn(min = pageHeight)
                    .padding(horizontal = 22.dp),
            ) {

            // ---- 续航区：左半（大字续航 + 电/油里程行），右半（车辆状态面板）----
            Spacer(Modifier.height(16.dp))
            val currentStatus = status
            val soc = currentStatus?.soc
            // 充电中判定与充电中心页保持一致：以 chargeState 为准，缺失时退回布尔信号
            val chargingNow = currentStatus?.chargeState == 1 ||
                (currentStatus?.chargeState == null && currentStatus?.charging == true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // 左：续航大字 + 里程行（靠左，宽度按内容自适应，不强行占 50%）
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    // 进度条固定为左栏约 1/4 宽（约为上一版一半）：左栏≈0.66×页面，故≈页面 1/6
                    val barWidth = maxWidth * 0.25f
                    Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                        text = currentStatus?.range ?: "--",
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Normal,
                        // 大字号下「445」绝不能被折成两行（折了整块续航区就塌了）
                        maxLines = 1,
                        softWrap = false,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "km",
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            // 抬高量跟着字号走：6dp 是 40sp 时量出来的基线差，
                            // 字号被放大后大字的降部空间也变大，固定 6dp 会让 km 看着偏高
                            modifier = Modifier.padding(
                                bottom = 6.dp * LocalDensity.current.fontScale.coerceAtMost(1.3f),
                            ),
                        )
                        // 充电中：总续航右侧的充电头脉冲入口（只在充电时出现），点进充电中心
                        if (chargingNow) {
                            Spacer(Modifier.width(6.dp))
                            ChargingPulseEntry(onClick = { showChargingCenter = true })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 里程行（手绘稿样式）：图标 · 里程 · 进度条 · 百分比
                    if (currentStatus?.isRangeExtender == true) {
                        MileageRow(
                            icon = Icons.Filled.EvStation,
                            label = "${currentStatus.electricRange ?: "--"}km",
                            fraction = ((soc ?: 0) / 100f).coerceIn(0f, 1f),
                            percent = "${soc ?: "--"}%",
                            barColor = Color(0xFF2FCC71),
                            barWidth = barWidth,
                        )
                        Spacer(Modifier.height(5.dp))
                        MileageRow(
                            icon = Icons.Filled.LocalGasStation,
                            label = "${currentStatus.fuelRange ?: "--"}km",
                            fraction = ((currentStatus.fuelSoc ?: 0) / 100f).coerceIn(0f, 1f),
                            percent = "${currentStatus.fuelSoc ?: "--"}%",
                            barColor = Color(0xFF2F86F6),
                            barWidth = barWidth,
                        )
                    } else {
                        MileageRow(
                            icon = Icons.Filled.EvStation,
                            label = "${currentStatus?.range ?: "--"}km",
                            fraction = ((soc ?: 0) / 100f).coerceIn(0f, 1f),
                            percent = "${soc ?: "--"}%",
                            barColor = Color(0xFF2FCC71),
                            barWidth = barWidth,
                        )
                    }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // 右：车辆状态（靠右；行车状态在闭锁上方，4 项状态同一行可横滑）
                Column(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    val locked = currentStatus?.locked
                    // 闭锁状态始终显示
                    StatusItem(
                        iconRes = if (locked == true) R.drawable.ic_qc_lock else R.drawable.ic_qc_unlock,
                        label = when (locked) {
                            true -> "车辆已闭锁"
                            false -> "车辆未闭锁"
                            null -> "闭锁状态未知"
                        },
                        alert = locked == false,
                    )
                    // 已开启提醒：排在「车辆已闭锁」正下方、纵向排列；未开启不提示
                    val openHints = buildList {
                        if (currentStatus?.windowOpen == true) add(R.drawable.ic_qc_window to "车窗已开启")
                        if (currentStatus?.trunkOpen == true) add(R.drawable.ic_qc_trunk to "后备箱已开启")
                        if (currentStatus?.sunshadeOpen == true) add(R.drawable.ic_qc_sunshade to "遮阳帘已开启")
                        if (currentStatus?.acOn == true) add(R.drawable.ic_qc_ac to "空调已开启")
                    }
                    if (openHints.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalAlignment = Alignment.End,
                        ) {
                            openHints.forEachIndexed { index, (iconRes, text) ->
                                if (index > 0) Spacer(Modifier.height(8.dp))
                                StatusItem(iconRes, text, alert = true)
                            }
                        }
                    }
                }
            }

            // ---- 充电中心入口（胶囊按钮，续航下方，样式参考官网 App）----
            ChargingCenterEntry(onClick = { showChargingCenter = true })

            // ---- 车模：显示官方接口图（默认常开，不再提供关闭开关）----
            if (carBitmap != null) {
                Spacer(Modifier.height(4.dp))
                // 快捷换壁纸的触发方式由设置决定：小白点 / 点车模本体 / 不提供。
                // 用户自己的壁纸正在生效时再压一层（themeCustomLocked）：一律不提供入口，
                // 免得一次误触把挑好裁好的图换成内置主题。
                val carQuick = quickSwitch.effectiveTarget(QuickPart.CAR, themeCustomLocked)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        // 点车模本体换壁纸时不要涟漪：车模是透明底 PNG，
                        // 一圈方形涟漪会盖出"这块是个按钮"的方框，很难看
                        .then(
                            if (carQuick == QuickTarget.TAP) {
                                Modifier.clickable(
                                    interactionSource = carTapInteraction,
                                    indication = null,
                                    onClick = onRandomTheme,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .onGloballyPositioned { coords ->
                            // 车模底部在「滚动内容」（父 Column）坐标系里的 y：
                            // 这是固定布局位置，不随滚动偏移变化，正好和 homeScrollState.value
                            // 处于同一坐标空间，可直接比较判断是否滑出视口。
                            coords.parentLayoutCoordinates?.let { parent ->
                                carModelBottomY = parent.localBoundingBoxOf(coords).bottom
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = carBitmap!!.asImageBitmap(),
                        contentDescription = "车模",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        // 千像素级的透明位图，滚动时父级每帧重绘都会重做一次缩放采样。
                        // 单独提一层 graphicsLayer 之后，滚动只更新这块的平移矩阵，
                        // 位图本身不再重绘 —— 这是首页上下滑时最实的一笔开销。
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(),
                    )
                    // 快捷换壁纸：小白点放在车模右上角，正好落在车顶右侧的空画面上。
                    // 车模上的点换的是「主题壁纸」，相伴卡上的点换的是「相伴卡背景」，两者互不影响。
                    if (carQuick == QuickTarget.DOT) {
                        QuickThemeButton(
                            onClick = onRandomTheme,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 2.dp),
                        )
                    }
                }
            }

            // ---- 快捷控车：一行显示 4 个，左右滑动查看其余，下方带横向滚动提示条 ----
            Spacer(Modifier.height(6.dp))
            // 车不带对应硬件的按钮直接不出现（如无天窗的车不给天窗入口）。
            // 可用按钮只跟「车辆能力 + 用户自定义顺序」有关，钉住即可，
            // 否则每次重组都要把整张指令表按硬件条件过滤一遍。
            val cells = remember(capabilities, controlLayout) {
                ControlActions.available(capabilities, controlLayout).map { it.id } + EDIT_CELL
            }
            val listState = rememberLazyListState()
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gap = 12.dp
                val itemWidth = (maxWidth - gap * 3) / 4
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(cells) { cellId ->
                        // contentAlignment=Center 保证按钮在自己格子里居中（否则会贴左）
                        Box(modifier = Modifier.width(itemWidth), contentAlignment = Alignment.Center) {
                            if (cellId == EDIT_CELL) {
                                EditButton { showControlEditor = true }
                            } else {
                                val action = ControlActions.find(cellId)
                                if (action != null) {
                                    val active = if (action.toggle) ControlActions.isActive(cellId, status) else null
                                    QuickButton(
                                        label = action.label,
                                        iconRes = action.iconRes,
                                        busy = controlling == cellId,
                                        active = active,
                                        onClick = { ensureConfirmed(action.label) { runControl(cellId) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ScrollHintBar(state = listState, itemCount = cells.size, visibleCount = 4)

            // ---- 卡片区：顺序 / 显隐可自定义（长按拖动排序、删除、添加）----
            // 每张卡的内容都在这里按 id 分发，[HomeCardArea] 只负责排版与编辑交互：
            // 空调概览条 / 胎压·行程 / 周能耗分布 / 近 6 周能耗 / 相伴卡。
            // 卡片本身都不发「设置类」指令（空调条上的开关除外），顺序变化不影响到数据来源。
            Spacer(Modifier.height(14.dp))
            // 零记关着时先把零记卡滤掉；编辑提交上来的列表里没有它们，
            // 落盘前再按原位置插回去，重新打开零记时卡片还在老地方。
            val shownCards = HomeCardLayout.visible(homeCards, journalOn)
            HomeCardArea(
                cards = shownCards,
                editing = cardEditing,
                onEditingChange = { cardEditing = it },
                onCardsChange = { next ->
                    runtime.setHomeCards(
                        if (journalOn) next else HomeCardLayout.restore(next, homeCards),
                    )
                },
                scrollState = homeScrollState,
                journalEnabled = journalOn,
            ) { cardId ->
                when (cardId) {
                    // 空调概览条：车内温度 / ◀ 设定温度 ▶ / 风扇开关；
                    // 点加减号只改温度（关机态是草稿），点电源开关才带着温度下发开启指令；
                    // 点卡片空白处进入全屏空调页（风量 / 出风方向 / 座椅舒适…都在那一页）
                    HomeCardId.AC -> AcQuickCard(
                        status = currentStatus,
                        busy = controlling != null,
                        target = acTargetTemp,
                        onStep = { stepAcTemp(it) },
                        onToggle = { turnOn, temp ->
                            ensureConfirmed(
                                if (turnOn) "开启空调（${temp}℃）" else "关闭空调",
                            ) { runAcCommand(turnOn, temp) }
                        },
                        onOpen = { showAcScreen = true },
                    )

                    // 发送到车机：搜目的地 → 调起高德路径规划，手机与车机登同一账号即同步。
                    // 卡片只负责开弹层，搜索与下发都在弹层里（见 DestinationSearch.kt）
                    HomeCardId.DESTINATION -> DestinationSearchCard(
                        hasKey = runtime.hasAmapKey(),
                        onClick = { showDestination = true },
                    )

                    // 胎压卡（左半，四轮数值 + 车模）+ 近 7 天行程卡（右半，每日里程柱状图）。
                    // 两张半屏卡合成一个可拖动条目：拆开时拖动过程中会出现「各占一行」的中间态。
                    HomeCardId.TIRE_TRIP -> Row(modifier = Modifier.fillMaxWidth()) {
                        TireQuickCard(
                            status = currentStatus,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        WeeklyTripCard(
                            energy = energySummary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 周能耗分布：上周总能耗环形图 + 行车 / 空调 / 其他占比
                    HomeCardId.COMPOSITION -> WeeklyEnergyCompositionCard(
                        composition = weeklyEnergy?.composition,
                    )

                    // 近 6 周百公里能耗 + 同车型全国排行
                    HomeCardId.RANK -> WeeklyEnergyRankCard(
                        rank = weeklyEnergy?.rank,
                        carModel = runtime.session.selectedCarType,
                    )

                    // 车辆健康：电池温度 / 电压 / 电流 + 后台耗电功能监测（空调、座椅加热通风、
                    // 方向盘加热…），有耗电项时可一次把关闭指令连续下发。
                    // 判定逻辑在 VehiclePowerWatch（纯函数，可单测），这里只做排版与接线。
                    HomeCardId.HEALTH -> {
                        val drains = remember(currentStatus) {
                            VehiclePowerWatch.drains(currentStatus)
                        }
                        VehicleHealthCard(
                            status = currentStatus,
                            drains = drains,
                            busy = powerDrainBusy,
                            progress = powerDrainProgress,
                            onShutDownAll = {
                                ensureConfirmed("关闭全部耗电功能") {
                                    runShutDownDrains(drains.map { it.offCommand })
                                }
                            },
                        )
                    }

                    // 相伴卡：提车天数 / 累计公里，背景图可在个性化里换。
                    // 选的是用户自己的背景时同样把快捷切换压掉 —— 那张图按 3:1 裁过，
                    // 被随机换成内置的 6 张之一后找不回来。
                    HomeCardId.OWNERSHIP -> OwnershipCard(
                        status = currentStatus,
                        energy = energySummary,
                        backdropId = ownershipBackdropId,
                        quickTarget = quickSwitch.effectiveTarget(
                            QuickPart.CARD,
                            // 用「文件存不存在」判断而不是等解码完成：
                            // ownershipCustomImage 是异步解出来的，慢一帧就有一帧的可点窗口
                            ownershipBackdropId == OwnershipBackdrops.CUSTOM_ID &&
                                customBackdropState.ownership > 0L,
                        ),
                        onRandomBackdrop = onRandomBackdrop,
                        customBackdrop = ownershipCustomImage,
                    )

                    // 保险续费 / 保养提醒：与零记那两张卡同源（数据、弹层都复用）。
                    // 首页这里是整行卡，点一下直接开设置弹层，不用绕到零记。
                    HomeCardId.INSURANCE -> InsuranceCard(
                        plan = journalData.insurance,
                        onClick = { showInsuranceSheet = true },
                    )

                    HomeCardId.MAINTENANCE -> MaintenanceCard(
                        plan = journalData.maintenance,
                        // 表显里程优先：里程接口的累计口径滞后，拿它算剩余里程会「不更新」
                        currentKm = OwnershipStats.currentKm(
                            currentStatus?.totalMileage,
                            energySummary?.totalMileageKm,
                        ),
                        onClick = { showMaintenanceSheet = true },
                    )
                }
            }

            // ---- 功能模块区（页面最下方）：横向滑动的模块卡 + 指示条 + 自定义按钮 ----
            // 空调 / 座椅 / 胎压 / 里程能耗 / 健康 / 充电 / 定时空调 / 冰箱 / 哨兵 …
            // 全部按「空调卡」的尺寸与圆角渲染，顺序、增删由底部「自定义模块」调整。
            //
            // 当前由 SHOW_HOME_MODULES 关闭（模块整体下线、代码保留）。接线一并留在 if 内，
            // 只为保持编译期可校验；改回 true 即可整块恢复。
            if (SHOW_HOME_MODULES) {
                Spacer(Modifier.height(14.dp))
                HomeModuleSection(
                    status = currentStatus,
                    energy = energySummary,
                    capabilities = capabilities,
                    callbacks = ModuleCallbacks(
                        onOpenClimate = { showClimate = true },
                        onOpenFridge = { showFridge = true },
                        onAcOn = { ensureConfirmed("开启空调") { runAcCommand(true) } },
                        onAcOff = { ensureConfirmed("关闭空调") { runAcCommand(false) } },
                        onToggleFridge = { enable ->
                            ensureConfirmed(if (enable) "开启车载冰箱" else "关闭车载冰箱") {
                                val f = currentStatus?.fridge
                                runFridgeCommand(
                                    com.leapcontrol.app.FridgeControlCommand(
                                        enable = enable,
                                        mode = f?.mode ?: com.leapcontrol.app.FridgeMode.COLD,
                                        temp = f?.temp ?: 4,
                                        style = f?.style ?: com.leapcontrol.app.FridgeStyle.NORMAL,
                                    ),
                                )
                            }
                        },
                        acBusy = controlling == "acOn" || controlling == "acOff" || controlling == "acTemp",
                        onRefreshEnergy = {
                            scope.launch { runCatching { runtime.refreshMileageEnergy(force = true) } }
                        },
                    ),
                )
            }

            // ---- 底部占位：更多功能开发中（手绘铁锹 + 路障 + 一行小字）----
            // 模块区整体下线期间用它收尾，避免页面滑到底空荡荡；模块逐项恢复后可直接删掉这块。
            // 26dp 是「最少留白」，weight 弹簧再把整块压到可视区最底部，紧贴底部导航栏。
            Spacer(Modifier.height(26.dp))
            Spacer(Modifier.weight(1f))
            UnderConstructionHint()

            Spacer(Modifier.height(14.dp))
            }
            }
            }
            }

        // ---- 指令结果条 / 二次确认弹窗：挂在页面分支之外，二级页同样可见 ----
        ActionBanner(
            message = refreshError ?: actionMessage,
            isError = refreshError != null,
            onDismiss = { actionMessage = null; refreshError = null },
        )
        // 控车二次确认弹窗（应用内确认，无指纹 / 密码验证）
        pendingConfirm?.let { (label, action) ->
            ConfirmDialog(
                title = "确认操作",
                message = "确定要向车辆发送「$label」指令吗？",
                onConfirm = { pendingConfirm = null; action() },
                onDismiss = { pendingConfirm = null },
            )
        }
        // ---- 新手引导：首次打开时指向右上角「玩法说明」 ----
        // 图标被用户在「个性化 → 首页图标」里关掉时不提示：没有可点的目标，指过去只会更迷惑。
        // 一层透明浮层（不吃触摸），只有说明卡上的两个按钮可点。
        if (coachVisible && tipIconVisible && playbookAnchor != Rect.Zero) {
            PlaybookCoachMark(
                anchor = playbookAnchor,
                onOpen = {
                    PlaybookHint.markDone(coachContext)
                    coachVisible = false
                    showPlaybook = true
                },
                onNeverShowAgain = {
                    PlaybookHint.markDone(coachContext)
                    coachVisible = false
                },
            )
        }
        }   // 首页内容 Box(fillMaxSize)
        }   // 二级页分支链（else）
        }   // 分支链外层 Box（结果条 / 确认弹窗都在这层之上）

        // ---- 自定义车名弹窗 ----
        if (showRenameDialog) {
            var nameInput by remember { mutableStateOf(runtime.customName().ifEmpty { runtime.displayName() }) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                containerColor = LocalAppColors.current.dialogBg,
                title = { Text("自定义车名", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text(
                            "车名只保存在本机；留空则恢复为零跑官方 App 的昵称。",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it.take(12) },
                            placeholder = { Text("如：小零", color = LocalAppColors.current.textSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LocalAppColors.current.textPrimary,
                                unfocusedTextColor = LocalAppColors.current.textPrimary,
                                cursorColor = LocalAppColors.current.accent,
                                focusedBorderColor = LocalAppColors.current.accent,
                                unfocusedBorderColor = LocalAppColors.current.barTrack,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        runtime.setCustomName(nameInput)
                        carName = runtime.displayName()
                        showRenameDialog = false
                    }) { Text("保存", color = LocalAppColors.current.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("取消", color = LocalAppColors.current.textSecondary)
                    }
                },
            )
        }

        // ---- 控车按钮自定义 ----
        if (showControlEditor) {
            ControlEditorDialog(
                initial = controlLayout,
                onSave = { list ->
                    runtime.setControlLayout(list)
                    controlLayout = runtime.controlLayout()
                    showControlEditor = false
                },
                onDismiss = { showControlEditor = false },
                capabilities = capabilities,
            )
        }

        // （车辆健康弹窗 / 四季主题弹窗：入口按钮已移除，代码保留，后续可从设置页或我的页重新挂回）

        // ---- 目的地搜索弹层：从首页「发送到车机」卡进入 ----
        if (showDestination) {
            DestinationSearchSheet(
                runtime = runtime,
                onDismiss = { showDestination = false },
                onMessage = { actionMessage = it },
            )
        }

        // ---- 保养 / 保险设置弹层：从首页那两张卡直接进入，改完立刻回到首页 ----
        if (showMaintenanceSheet) {
            MaintenanceSheet(
                plan = journalData.maintenance,
                currentKm = OwnershipStats.currentKm(
                    status?.totalMileage,
                    energySummary?.totalMileageKm,
                ),
                onDismiss = { showMaintenanceSheet = false },
                onSave = { plan ->
                    runtime.journal.setMaintenance(plan)
                    showMaintenanceSheet = false
                    actionMessage = if (plan == null) "已关闭保养提醒" else "保养提醒已保存"
                },
            )
        }

        if (showInsuranceSheet) {
            InsuranceSheet(
                plan = journalData.insurance,
                onDismiss = { showInsuranceSheet = false },
                onSave = { plan ->
                    runtime.journal.setInsurance(plan)
                    showInsuranceSheet = false
                    actionMessage = if (plan == null) "已关闭保险提醒" else "保险提醒已保存"
                },
            )
        }

        // ---- 控车密码弹窗 ----
        if (showPinDialog) {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                containerColor = LocalAppColors.current.dialogBg,
                title = { Text("输入控车密码", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text(
                            "输入零跑官方 App 中设置的 4 位操控密码（本地加密存储，仅用于指令签名）",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LocalAppColors.current.textPrimary,
                                unfocusedTextColor = LocalAppColors.current.textPrimary,
                                cursorColor = LocalAppColors.current.accent,
                                focusedBorderColor = LocalAppColors.current.accent,
                                unfocusedBorderColor = LocalAppColors.current.barTrack,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = pin.length == 4,
                        onClick = {
                            scope.launch {
                                val ok = try {
                                    runtime.verifyAndSavePin(pin)
                                } catch (e: Exception) {
                                    false
                                }
                                if (ok) {
                                    showPinDialog = false
                                    // 空调调温与常规控车都会触发密码输入，这里按来源重放
                                    val temp = pendingAcTemp
                                    val fridge = pendingFridgeCommand
                                    val gun = pendingChargeGun
                                    val fix = pendingHealthFix
                                    val otaAction = pendingOtaAction
                                    val acCmd = pendingAcCommand
                                    // 开启指令要连同用户设定的温度一起补发
                                    val acOnTemp = pendingAcOnTemp
                                    // 空调展开面板里的指令（风量 / 出风方向 / 座椅…）
                                    val acPanelCmd = pendingAcPanelCommand
                                    val drains = pendingPowerDrains
                                    when {
                                        drains != null -> {
                                            pendingPowerDrains = null
                                            runShutDownDrains(drains)
                                        }
                                        acPanelCmd != null -> {
                                            pendingAcPanelCommand = null
                                            runAcPanelCommand(acPanelCmd)
                                        }
                                        acCmd != null -> {
                                            val turnOn = acCmd == "acOn"
                                            pendingAcCommand = null
                                            pendingAcOnTemp = null
                                            runAcCommand(turnOn, acOnTemp)
                                        }
                                        temp != null -> {
                                            pendingAcTemp = null
                                            runAcTemperature(temp)
                                        }
                                        fridge != null -> {
                                            pendingFridgeCommand = null
                                            runFridgeCommand(fridge)
                                        }
                                        gun != null -> {
                                            pendingChargeGun = null
                                            runChargeGun(gun)
                                        }
                                        fix != null -> {
                                            pendingHealthFix = null
                                            runHealthFix(fix)
                                        }
                                        otaAction != null -> {
                                            val (action, arg) = otaAction
                                            pendingOtaAction = null
                                            runOtaAction(action, arg)
                                        }
                                        else -> pendingCommand?.let { runControl(it) }
                                    }
                                } else {
                                    actionMessage = "控车密码校验失败"
                                }
                            }
                        },
                    ) { Text("确认", color = if (pin.length == 4) LocalAppColors.current.accent else LocalAppColors.current.textSecondary) }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("取消", color = LocalAppColors.current.textSecondary)
                    }
                },
            )
        }

    }
}

/** 多车切换下拉：账号下有多辆车时挂在车名下方。 */
@Composable
private fun VehicleDropdown(
    expanded: Boolean,
    vehicles: List<com.leapcontrol.app.Vehicle>,
    selectedVin: String,
    onDismiss: () -> Unit,
    onSelect: (com.leapcontrol.app.Vehicle) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.dialogBg,
    ) {
        vehicles.forEach { vehicle ->
            val selected = vehicle.vin == selectedVin
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = (vehicle.nickname.ifEmpty { vehicle.officialNickname })
                                .ifEmpty { vehicle.carType },
                            color = if (selected) LocalAppColors.current.accent else LocalAppColors.current.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            // carType 可能是内部项目代号（Lafa5 下发的是 B05），统一走别名表
                            text = "${EnergyFormat.carModelName(vehicle.carType)} · VIN ${vehicle.vin.takeLast(6)}",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                },
                trailingIcon = {
                    if (selected) {
                        Icon(Icons.Filled.Check, null, tint = LocalAppColors.current.accent, modifier = Modifier.size(18.dp))
                    }
                },
                onClick = { onSelect(vehicle) },
            )
        }
    }
}

/** 车辆健康弹窗：胎压俯视图 + 四轮数值。 */
@Composable
private fun HealthDialog(status: CarStatus?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.dialogBg,
        title = { Text("车辆健康", color = LocalAppColors.current.textPrimary, fontSize = 17.sp) },
        text = {
            if (status == null || status.tires.isEmpty()) {
                Text("暂无胎压数据，请先刷新车况", color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
            } else {
                TirePressureContent(status)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = LocalAppColors.current.accent) }
        },
    )
}

/** 功能模块通用卡片容器 */
@Composable
private fun ModuleCard(
    title: String,
    content: @Composable () -> Unit,
) {
    AppCard(title = title) { content() }
}

/** 胎压模块：俯视车模（轮胎按状态着色）+ 四轮数值 */
@Composable
private fun TirePressureModule(status: CarStatus) {
    ModuleCard("胎压状态") {
        if (status.tires.isEmpty()) {
            Text("暂无胎压数据", color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
        } else {
            TirePressureContent(status)
        }
    }
}

/** 胎压内容：俯视车模 + 2×2 数值网格（模块卡片与健康弹窗共用）。 */
@Composable
private fun TirePressureContent(status: CarStatus) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TireTopView(
            tires = status.tires,
            modifier = Modifier
                .width(84.dp)
                .height(175.dp),
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            listOf(listOf("左前", "右前"), listOf("左后", "右后")).forEachIndexed { index, labels ->
                if (index > 0) Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    labels.forEach { label ->
                        val tire = status.tires.firstOrNull { it.position == label }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tire?.pressure?.let { "$it kPa" } ?: "--",
                                color = when {
                                    tire == null -> LocalAppColors.current.textSecondary
                                    tire.warning -> Color(0xFFE5484D)
                                    else -> LocalAppColors.current.accent
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = tire?.temperature ?: "--",
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 半宽模块卡片骨架：标题行（标题 + 右上圆形图标）+ 内容区。 */
@Composable
private fun ModuleTile(
    modifier: Modifier = Modifier,
    title: String,
    cornerIcon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .shadow(4.dp, shape)
            .background(C.cardBg, shape)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(36.dp)) { cornerIcon() }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** 空调卡 / 位置卡等并排小卡的官方圆角：实测 6dp，卡片宽 158dp、高 142dp */
internal fun officialCardShape() = RoundedCornerShape(OFFICIAL_CARD_RADIUS)

/**
 * 空调卡（官方 1:1）：
 * 标题行「空调」+ 右上 28dp 浅灰圆底里的风扇图标（圆底 #F3F4F6、图标 #9A9C9B，兼作开关）
 * → 中部「− 24° +」（温度 26sp，− / + 贴左右两端）→ 底部灰字「车内温度 26.5℃」。
 */
@Composable
internal fun AcTile(
    modifier: Modifier = Modifier,
    status: CarStatus?,
    busy: Boolean,
    busyOn: Boolean,
    busyOff: Boolean,
    onAcOn: () -> Unit,
    onAcOff: () -> Unit,
    onTempChange: (Int) -> Unit,
    /** 半屏并排时为 true：按钮标签与温度字号收窄一档，避免文字被挤成两行。 */
    compact: Boolean = false,
) {
    val C = LocalAppColors.current
    val target = status?.acTargetTemp?.toIntOrNull() ?: 24
    val acOn = status?.acOn
    val shape = officialCardShape()
    Column(
        modifier = modifier
            .background(C.cardBg, shape)
            .padding(horizontal = 14.dp, vertical = 15.dp),
    ) {
        // 标题行：左标题 + 右上风扇圆钮（兼作空调开关，官方同款）
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("空调", color = C.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (acOn == true) C.accent.copy(alpha = 0.16f) else FAN_CIRCLE_BG)
                    .clickable(enabled = !busy) { if (acOn == true) onAcOff() else onAcOn() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ac_fan),
                    contentDescription = "空调开关",
                    tint = if (busy) FAN_ICON_TINT.copy(alpha = 0.45f)
                    else if (acOn == true) C.accent else FAN_ICON_TINT,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        // 官方版式：标题与大温度之间是留白（约占卡高 37%），− / + 贴左右两端
        // 放进首页模块区（固定卡高 150dp）时留白收一档，避免内容顶出卡片
        Spacer(Modifier.height(if (compact) 38.dp else 46.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TempStepButton(symbol = "−", enabled = !busy && target > 16) {
                onTempChange((target - 1).coerceIn(16, 32))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${target}°",
                color = C.textPrimary,
                fontSize = if (compact) 26.sp else 30.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.weight(1f))
            TempStepButton(symbol = "+", enabled = !busy && target < 32) {
                onTempChange((target + 1).coerceIn(16, 32))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "车内温度 ${status?.interiorTemp ?: "--"}℃",
            color = C.textSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 空调温度步进：官方是细横杠「−」与十字「+」，居左右两端。 */
@Composable
private fun TempStepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = if (enabled) C.textPrimary else C.textSecondary.copy(alpha = 0.4f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
    }
}


/**
 * 控车 / 刷新结果提示：屏幕正中一张干净的小卡片，3 秒后自动淡出，点击立即关闭。
 *
 * 之前这条反馈是滚动列表最后一行——用户根本滚不到那里，
 * 于是「指令发出但 nothing changed」就被感知成“点了没反应”；改成居中浮层后始终可见。
 *
 * ⚠️ 修饰符顺序曾经是 `clip → background → shadow`：阴影被前面的 clip 裁在圆角**里面**，
 * 卡面上于是浮出一圈灰边、整块发灰发脏（截图里那个灰蒙蒙的药丸）。
 * 正确顺序是 `shadow(clip = false) → clip → background`，投影落在卡外，边缘才干净；
 * 卡面用不透明的 [AppColors.dialogBg]，不再用浅色打底 + 深色阴影的混搭。
 */
@Composable
private fun ActionBanner(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    if (message.isNullOrBlank()) return
    val C = LocalAppColors.current
    // 自己管进出场：先淡出、再摘掉，否则 onDismiss 一调用整棵子树瞬间消失，看着像闪一下
    var shown by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) {
        shown = true
        delay(if (isError) 5_000L else 3_200L)
        shown = false
        delay(200)
        onDismiss()
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(180),
        label = "bannerAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.94f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "bannerScale",
    )
    val tint = when {
        isError && C.isDark -> Color(0xFFFF7A6E)
        isError -> Color(0xFFD93A2B)
        C.isDark -> Color(0xFF3DD68C)
        else -> Color(0xFF1FA45B)
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.22f),
                )
                .clip(shape)
                .background(C.dialogBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 小圆底 + 单色对勾，比原来「裸图标 + 大白底」焦点更集中
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (C.isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isError) Icons.Filled.Close else Icons.Filled.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                text = message,
                color = if (isError) tint else C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                maxLines = 3,
            )
        }
    }
}

/**
 * 控车二次确认弹窗：应用内确认，不再要求指纹 / 设备密码。
 * 取消 / 确认两个按钮做得较大（高 50dp、并排等大），便于单手操作。
 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    // 半透明遮罩 + 居中卡片；点遮罩 = 取消
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIALOG_SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = C.dialogBg,
            shadowElevation = 20.dp,
            // 卡片自身消费点击，避免穿透到遮罩误关
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clickable(onClick = { }),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = title,
                    color = C.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row {
                    // 取消
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.chipBg)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消", color = C.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(12.dp))
                    // 确认
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.accent)
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("确认", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** 空调开关操作按钮：[highlight] 用于标出当前已知所处的状态。 */
@Composable
private fun RowScope.AcActionChip(
    text: String,
    enabled: Boolean,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) C.chipBgActive else C.chipBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (highlight) C.accent else C.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 空调模块：开关状态、设定温度、风量、循环 */
@Composable
private fun ClimateModule(status: CarStatus) {
    ModuleCard("空调信息") {
        ModuleRow("状态", when (status.acOn) {
            true -> "运行中"
            false -> "已关闭"
            null -> "--"
        }, valueColor = if (status.acOn == true) LocalAppColors.current.accent else LocalAppColors.current.textPrimary)
        ModuleRow("车内温度", status.interiorTemp?.let { "$it °C" } ?: "--")
        ModuleRow("设定温度", status.acTargetTemp?.let { "$it °C" } ?: "--")
        ModuleRow("风量", status.acWindLevel?.let { "${it} 档" } ?: "--")
        status.acCircle?.let { ModuleRow("循环", it) }
    }
}

/** 空调卡右上风扇圆底的官方浅灰 #F3F4F6（图标 #9A9C9B） */
private val FAN_CIRCLE_BG = Color(0xFFF3F4F6)
private val FAN_ICON_TINT = Color(0xFF9A9C9B)
/** 官方空调 / 位置卡圆角：实测约 6dp（卡片宽 158dp 时半径 / 宽 ≈ 0.037）。
 *  首页功能模块区（HomeModules.kt）复用同一圆角，保证所有模块卡与空调卡一致。 */
internal val OFFICIAL_CARD_RADIUS = 6.dp

/** 总里程模块 */
@Composable
private fun MileageModule(status: CarStatus) {
    ModuleCard("总里程") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = status.totalMileage ?: "--",
                color = LocalAppColors.current.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = " km",
                color = LocalAppColors.current.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun ModuleRow(
    label: String,
    value: String,
    valueColor: Color = LocalAppColors.current.textPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 把底层网络异常转换为用户可读文案。 */
private fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("No address associated with hostname", ignoreCase = true) ->
            "网络连接不稳定，正在重试…"
        msg.contains("timeout", ignoreCase = true) -> "请求超时，稍后自动重试"
        msg.contains("timed out", ignoreCase = true) -> "请求超时，稍后自动重试"
        msg.contains("Failed to connect", ignoreCase = true) -> "无法连接服务器，请检查网络"
        msg.contains("unexpected end of stream", ignoreCase = true) -> "连接被中断，正在重试…"
        msg.isBlank() -> "请求失败，请稍后重试"
        else -> msg.lines().first().take(80)
    }
}

@Composable
/**
 * @param active 复合按钮（后备箱/车窗/遮阳帘）的开启状态：
 *               true 高亮（绿底绿图标），false 常规，null 车况未知（常规）。
 */
private fun QuickButton(
    label: String,
    iconRes: Int,
    busy: Boolean,
    onClick: () -> Unit,
    active: Boolean? = null,
) {
    val on = active == true
    val c = LocalAppColors.current
    val iconTint = if (on) c.accent else c.textPrimary
    val labelColor = if (on) c.accent else c.textSecondary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = !busy,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 官方样式：纯白实心正圆 + 轻微下投影，常规态不描边；
        // 开启态保留零控的绿色反馈（淡绿底 + 绿色描边 + 绿色图标）。
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(
                    elevation = if (pressed) 1.dp else 3.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                )
                .background(
                    color = when {
                        on -> c.iconBgActive
                        pressed -> c.quickButtonPressed
                        else -> c.quickButtonBg
                    },
                    shape = CircleShape,
                )
                .then(
                    if (on) {
                        Modifier.border(1.5.dp, c.accent.copy(alpha = 0.55f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = c.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 末尾的「编辑」按钮：沿用官方「自定义」槽位的三点图标。 */
@Composable
private fun EditButton(onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                )
                .background(c.quickButtonBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_qc_custom),
                contentDescription = "编辑控车按钮",
                tint = c.textPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("编辑", color = c.textSecondary, fontSize = 12.sp)
    }
}

/**
 * 控车按钮自定义：调整顺序（上移/下移）、移除、添加。
 * 约束：最少保留 3 个，最多 7 个（第 8 格固定为编辑按钮）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ControlEditorDialog(
    initial: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    capabilities: VehicleCapabilities = VehicleCapabilities.EMPTY,
) {
    // 编辑列表里也要剔除本车不支持的项，否则加进去也显示不出来
    val items = remember {
        androidx.compose.runtime.mutableStateListOf<String>()
            .apply { addAll(initial.filter { id -> ControlActions.find(id)?.available(capabilities) == true }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.dialogBg,
        title = { Text("自定义控车按钮", color = LocalAppColors.current.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "最多 ${ControlActions.MaxSelectable} 个（双行共 8 格，最后一格为编辑），最少 ${ControlActions.MinSelectable} 个",
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { index, id ->
                    val action = ControlActions.find(id) ?: return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(action.iconRes),
                            null,
                            tint = LocalAppColors.current.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(action.label, color = LocalAppColors.current.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { if (index > 0) { items.removeAt(index); items.add(index - 1, id) } },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, null, tint = if (index > 0) LocalAppColors.current.textPrimary else LocalAppColors.current.barTrack)
                        }
                        IconButton(
                            onClick = { if (index < items.size - 1) { items.removeAt(index); items.add(index + 1, id) } },
                            enabled = index < items.size - 1,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (index < items.size - 1) LocalAppColors.current.textPrimary else LocalAppColors.current.barTrack)
                        }
                        IconButton(
                            onClick = { if (items.size > ControlActions.MinSelectable) items.removeAt(index) },
                            enabled = items.size > ControlActions.MinSelectable,
                        ) {
                            Icon(Icons.Filled.Close, null, tint = if (items.size > ControlActions.MinSelectable) Color(0xFFFF6B6B) else LocalAppColors.current.barTrack)
                        }
                    }
                }
                val canAdd = items.size < ControlActions.MaxSelectable
                val available = ControlActions.all.filter { it.id !in items && it.available(capabilities) }
                if (available.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("添加按钮", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        available.forEach { action ->
                            androidx.compose.material3.AssistChip(
                                onClick = { if (canAdd) items.add(action.id) },
                                enabled = canAdd,
                                label = { Text(action.label, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(painterResource(action.iconRes), null, modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(items.toList()) }) { Text("保存", color = LocalAppColors.current.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = LocalAppColors.current.textSecondary) }
        },
    )
}

/**
 * 横向滚动提示条：只在内容超出一屏时显示。
 *
 * 无级（连续）调节：轨道上只有一个滑块，滑块宽度 = 可见比例，
 * 水平位置按 LazyListState 的实时滚动进度线性跟随 —— 手指滑多少它走多少，
 * 而不是以前那样「按页切段、到页才跳一下」的两段式。
 */
@Composable
internal fun ScrollHintBar(
    state: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    visibleCount: Int,
) {
    if (itemCount <= visibleCount) return
    val C = LocalAppColors.current
    // 轨道比原先的分段条略长一点，无级滑动的位移才看得出来（原分段条 26dp）
    val trackWidth = 36.dp
    // 滑块宽度与「可见比例」一致；过窄时兜底 9dp，保证始终看得见
    val thumbWidth = (trackWidth * (visibleCount.toFloat() / itemCount)).coerceAtLeast(9.dp)
    // 连续进度 = (首个可见项下标 + 该项已滑出的比例) / 最大可滚动格数，取值 0..1
    // 这里刻意不 by 解包：进度只在 graphicsLayer 里读，横滑时只更新图层属性，
    // 不重组、也不重新布局（用 Modifier.offset 的话每帧都要走一遍 measure/place）。
    val progress = remember(state, itemCount, visibleCount) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total == 0 || visible.isEmpty()) return@derivedStateOf 0f
            // 相邻两项 offset 之差 = 单格步距（含卡片间距），
            // 用它把像素偏移换算成「格」，间距才不会让进度忽快忽慢
            val stride = (if (visible.size >= 2) {
                (visible[1].offset - visible[0].offset).toFloat()
            } else {
                visible[0].size.toFloat()
            }).takeIf { it > 0f } ?: 1f
            val scrolled = state.firstVisibleItemIndex +
                state.firstVisibleItemScrollOffset.toFloat() / stride
            val maxScroll = (total - visibleCount).coerceAtLeast(1)
            (scrolled / maxScroll).coerceIn(0f, 1f)
        }
    }
    val maxOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (trackWidth - thumbWidth).toPx()
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(C.textPrimary.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = maxOffsetPx * progress.value }
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(C.textPrimary),
            )
        }
    }
}

private val STATUS_ALERT_RED = Color(0xFFE5484D)

/**
 * 里程行（手绘稿样式）：从左至右依次是 图标 · 里程 · 进度条（定宽）· 百分比。
 * 进度条是定宽的，所以百分比的起点两行天然一致，不需要再靠定宽槽位去对齐。
 *
 * **字号要跟着系统缩放一起放大**：里程文字槽原来写死 46dp，系统字号 1.3 倍时
 * 「445km」需要约 50dp，塞不下就折成「445k / m」—— 整个续航区被撑成两行，
 * 底下的「充电中心」也跟着挤在一起。所以槽位宽度按 fontScale 放大，并且禁止折行。
 */
@Composable
private fun MileageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    fraction: Float,
    percent: String,
    barColor: Color,
    barWidth: Dp,
) {
    val C = LocalAppColors.current
    // 48dp 是系统字号 1.0 时「445km」实测宽度（约 45dp）再留一点余量；跟着 fontScale 缩放，
    // 1.3 倍时槽位 62dp、文字 58dp，仍装得下，不会折行也不会被裁掉
    val labelWidth = 48.dp * LocalDensity.current.fontScale
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = barColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(labelWidth),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                // 滑槽用「强」档：透明度更低（约 55%），压在动态背景上也看得清边界
                .background(C.barTrackStrong),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(9.dp))
        // 百分比紧贴进度条右侧：原来给它 38dp 定宽 + 右对齐，短数字（如 30%）左边的空位
        // 会全部留在条与数字之间，看起来像进度条断了一截。改为左对齐、宽度自适应后，
        // 上下两行的百分比起点都跟着同宽的进度条走，天然对齐
        Text(
            text = percent,
            color = C.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * 车辆状态面板里的单行状态：左图标 + 右文字。
 * 文字统一用主色（浅色模式黑、深色模式白）—— 状态提示压在动态背景上时，
 * 灰色或红色小字会糊掉；警示信息改由图标颜色承担（[alert]=true 时图标转红）。
 *
 * 图标用「快捷控车」那一套 `ic_qc_*` 矢量图（与首页圆钮同源），
 * 不再混用 Material 图标 —— 两套线宽/圆角不同，并排时会明显不是一家人。
 */
@Composable
private fun StatusItem(iconRes: Int, label: String, alert: Boolean) {
    val C = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (alert) STATUS_ALERT_RED else C.textPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 15.sp,
            // 状态文案不折行：折了会把右侧状态栏撑高，左边的续航区跟着错位
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 总续航右侧的「充电中」入口：充电头图标 + 两圈向外扩散的脉冲环。
 *
 * - 只在 `chargeState == 1`（充电中）时由调用方渲染，未插枪 / 已充满都不出现；
 * - 颜色沿用里程条的绿（`0xFF2FCC71`），脉冲环与光晕都是同色低透明度叠加，
 *   不引入新色相，深色浅色主题下都不用另调；
 * - 点击直接进充电中心（就是首页那颗胶囊的同一目标页）。
 *
 * 不做涟漪：这里是个 40dp 的透明热点，方形涟漪会在续航区盖出一块背景。
 */
@Composable
private fun ChargingPulseEntry(onClick: () -> Unit) {
    val green = Color(0xFF2FCC71)
    val glow = Color(0xFF46E58C)
    val transition = rememberInfiniteTransition(label = "chargePulse")
    // 两圈环错相半个周期：读起来是"电流一下一下往外推"，而不是单点呼吸
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "chargePulsePhase",
    )
    // 图标本体的轻微心跳，与环分开走，避免整块一起涨缩
    val beat by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(760, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "chargePulseBeat",
    )
    Box(
        modifier = Modifier
            // 抬 3dp 让图标中心落在 40sp 数字的视觉中线上
            .padding(bottom = 3.dp)
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val maxR = size.minDimension / 2f - 1.dp.toPx()
            val baseR = size.minDimension * 0.30f
            // 中心常亮光晕：先告诉眼睛这一格在通电
            val haloR = baseR * 1.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0f)),
                    center = c,
                    radius = haloR,
                ),
                radius = haloR,
                center = c,
            )
            // 向外扩散的脉冲环
            repeat(2) { i ->
                val p = (phase + i * 0.5f) % 1f
                drawCircle(
                    color = green.copy(alpha = (1f - p) * 0.55f),
                    radius = baseR + (maxR - baseR) * p,
                    center = c,
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_charge_plug),
            contentDescription = "充电中，进入充电中心",
            tint = green,
            modifier = Modifier
                .size(19.dp)
                .graphicsLayer {
                    scaleX = beat
                    scaleY = beat
                    alpha = (0.80f + (beat - 0.94f) * 3.6f).coerceIn(0f, 1f)
                },
        )
    }
}

/** 充电中心入口：胶囊按钮（文字 + 右箭头），样式参考官网 App。 */
@Composable
private fun ChargingCenterEntry(onClick: () -> Unit) {
    val C = LocalAppColors.current
    Spacer(Modifier.height(8.dp))
    // 胶囊入口：比上一版收一档（左右 14/8 → 10/6，上下 7 → 4，字号 13 → 12sp，箭头 16 → 14dp）；
    // 左右内边距仍不对称，文字整体略微靠右，不贴着左侧圆弧。
    Surface(
        shape = RoundedCornerShape(50),
        color = C.chipBg,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "充电中心",
                color = C.textPrimary,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(1.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = C.textPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 顶部图标按钮：只显示图标（车辆健康 / 皮肤 / 改车名）。
 * 触控区固定 34dp、图标 22dp，纵向高度与左侧车名那一行对齐。
 */
@Composable
private fun HeaderIconButton(
    iconRes: Int,
    description: String,
    size: androidx.compose.ui.unit.Dp = 22.dp,
    tint: Color = LocalAppColors.current.textPrimary,
    /** 外部要在按钮上挂测量 / 引导等修饰符时用（默认什么都不加）。 */
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * 车况更新时间：只保留「月-日 时:分」（MM-dd HH:mm）。
 * 放在车名下方，年份与秒钟对当下刷新判断没有意义，去掉后一行能完整显示。
 */
private fun formatUpdateTime(ts: Long): String = UPDATE_TIME_FORMAT.format(Date(ts))

/**
 * 复用一个格式化器。
 *
 * 这行文案在首页顶部，重组频率不低；每次 new 一个 `SimpleDateFormat` 要重新解析
 * 模式串、建 Calendar，属于纯粹的白费。`SimpleDateFormat` 不是线程安全的，
 * 但这里只在主线程的组合阶段调用，复用是安全的。
 */
private val UPDATE_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

/** 按电量百分比填充的电池图标；percent 为空时只画空心轮廓。 */
@Composable
private fun BatteryIcon(
    percent: Int?,
    size: androidx.compose.ui.unit.Dp,
    tint: Color,
) {
    val fillColor = Color(0xFF2FCC71)
    Canvas(modifier = Modifier.size(width = size, height = size * 0.52f)) {
        val h = this.size.height
        val w = this.size.width
        val bodyWidth = w * 0.88f
        val strokeWidth = w * 0.045f
        // 电池外壳
        drawRoundRect(
            color = tint,
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(bodyWidth, h),
            cornerRadius = CornerRadius(h * 0.22f, h * 0.22f),
            style = Stroke(width = strokeWidth),
        )
        // 正极凸起
        drawRect(
            color = tint,
            topLeft = Offset(bodyWidth + w * 0.02f, h * 0.3f),
            size = androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.4f),
        )
        // 电量填充
        if (percent != null) {
            val inner = h - strokeWidth * 3f
            val innerWidth = (bodyWidth - strokeWidth * 3f) * (percent.coerceIn(0, 100) / 100f)
            if (innerWidth > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(strokeWidth * 1.5f, h / 2f - inner / 2f),
                    size = androidx.compose.ui.geometry.Size(innerWidth, inner),
                    cornerRadius = CornerRadius(inner * 0.22f, inner * 0.22f),
                )
            }
        }
    }
}

