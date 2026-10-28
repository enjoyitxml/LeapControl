package com.leapcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import com.leapcontrol.app.ChargePlanCyclesHelper
import com.leapcontrol.app.ChargeSchedule
import com.leapcontrol.app.QuickSwitchPrefs
import com.leapcontrol.app.QuickTarget
import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleCapabilities
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
    /** 向上汇报「当前是否在零控首页主界面」—— 视频背景只在首页主界面播放 */
    onMainPageChange: (Boolean) -> Unit,
    /** 向上汇报「车模是否已滚出可视区」—— 滚出后视频背景切换为纯色背景，提高可读性 */
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
        var showBleKey by remember { mutableStateOf(false) }
        // 行驶里程 / 能耗（独立接口，拿不到就静默保持上一次结果）
        val energySummary by runtime.mileageEnergyFlow.collectAsState()
        // 相伴卡背景：个性化里切换后要立刻生效，所以直接读 State
        val ownershipBackdropId = runtime.ownershipBackdrop.value
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
        // 胎压监测独立页
        var showTire by remember { mutableStateOf(false) }
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
        // 开机后车端回报同一个温度 = 草稿已生效，交还给车端，避免下次关空调又被旧草稿盖掉
        LaunchedEffect(acOnNow, remoteAcTemp, pendingAcTempDraft) {
            if (acOnNow && pendingAcTempDraft != null && remoteAcTemp == pendingAcTempDraft) {
                pendingAcTempDraft = null
            }
        }
        val acTargetTemp = AcTempDraft.shown(acOnNow, remoteAcTemp, pendingAcTempDraft)

        LaunchedEffect(Unit) { vehicles = runCatching { runtime.vehicles() }.getOrDefault(emptyList()) }

        // 二级页（充电中心 / 座椅 / 空调 / 体检 / OTA…）不属于「首页」，
        // 打开时向 MainScreen 汇报，让背景视频只在首页主界面播放
        val onMainPage = !showChargingCenter && !showSeatComfort && !showFridge && !showClimate &&
            !showAcScreen && !showScheduledAc && !showVehicleHealth && !showOta &&
            !showControlEditor && !showTire
        LaunchedEffect(onMainPage) { onMainPageChange(onMainPage) }

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

        // 进入首页（或切换车辆）时拉一次里程 / 能耗；15 分钟内不重复请求，
        // 失败也不打扰用户 —— 模块卡保持上一次结果或显示「--」。
        LaunchedEffect(vinKey) {
            runCatching { runtime.refreshMileageEnergy() }
        }

        // 去重：2 秒内的重复请求忽略（避免冷启动时启动刷新与前台事件叠加）
        var lastRequestAt = 0L
        /**
         * 拉取车况。结果由 runtime.statusFlow 直接下发，UI 不做「先清空再填充」。
         *
         * @param silent true = 静默刷新（默认）：不显示转圈、不改右上角文案，车况到位自动替换数值；
         *               false = 用户手动下拉刷新，才显示下拉指示器。
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
                if (!silent) refreshing = false
            }
        }

        // 冷启动：进入首页刷新一次
        LaunchedEffect(Unit) {
            refresh(force = true)
        }
        // 从后台切回前台时刷新
        LaunchedEffect(Unit) {
            ForegroundSignal.events.collect { refresh() }
        }
        // 网络恢复：仅当上一次刷新失败时补刷一次（避免频繁请求）
        LaunchedEffect(Unit) {
            ForegroundSignal.networkEvents.collect {
                if (status == null || refreshError != null) refresh()
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
        fun runAcTemperature(temp: Int) {
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
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        /**
         * 空调设定温度加减：空调**开着**就是调温（立即下发），**关着**只改本地草稿，
         * 等点电源时才把草稿温度带下去。判据见 [AcTempDraft.sendOnArrow]。
         */
        fun stepAcTemp(delta: Int) {
            val next = AcTempDraft.step(acTargetTemp, delta)
            if (next == acTargetTemp) return
            if (AcTempDraft.sendOnArrow(acOnNow)) runAcTemperature(next) else pendingAcTempDraft = next
        }

        /**
         * 空调展开面板里的指令统一入口。
         *
         * 命令名由面板按当前车况拼好（快捷场景直接用 `quickCool` / `defrost` 这类，
         * 风量 / 出风方向 / 循环用 [com.leapcontrol.app.Commands.acSettingName] 合成，
         * 座椅类用 `driverSeatHeating_2` 这种「前缀:档位」），这里只负责下发 + 回拉车况。
         */
        fun runAcPanelCommand(commandName: String) {
            scope.launch {
                controlling = commandName
                actionMessage = null
                try {
                    actionMessage = runtime.control(commandName)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingAcPanelCommand = commandName
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
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
                    limitSoc = plan?.percent ?: current.limitSoc,
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
                actionMessage = null
                try {
                    val msg = when (action) {
                        "unlock" -> runtime.unlockCharger()
                        "start" -> runtime.startCharging()
                        else -> runtime.stopCharging()
                    }
                    actionMessage = msg
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingChargeGun = action
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
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
                onBack = { showAcScreen = false },
                onTempStep = { stepAcTemp(it) },
                onPower = { on ->
                    ensureConfirmed(
                        if (on) "开启空调（${acTargetTemp}℃）" else "关闭空调",
                    ) { runAcCommand(on, acTargetTemp) }
                },
                onCommand = { name -> runAcPanelCommand(name) },
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
        } else if (showTire) {
            TirePressureScreen(
                status = status,
                onBack = { showTire = false },
            )
        } else if (showBleKey && bleHost != null) {
            BluetoothKeyScreen(host = bleHost, onBack = { showBleKey = false })
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
                // 右侧图标按钮：蓝牙钥匙（个性化左边）+ 个性化（皮肤），顶边与左侧车名顶端对齐
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Row(
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
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "km",
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
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
                        icon = if (locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        label = when (locked) {
                            true -> "车辆已闭锁"
                            false -> "车辆未闭锁"
                            null -> "闭锁状态未知"
                        },
                        alert = locked == false,
                    )
                    // 已开启提醒：排在「车辆已闭锁」正下方、纵向排列；未开启不提示
                    val openHints = buildList {
                        if (currentStatus?.windowOpen == true) add(Icons.Filled.Window to "车窗已开启")
                        if (currentStatus?.trunkOpen == true) add(Icons.Filled.Luggage to "后备箱已开启")
                        if (currentStatus?.sunshadeOpen == true) add(Icons.Filled.Curtains to "遮阳帘已开启")
                        if (currentStatus?.acOn == true) add(Icons.Filled.AcUnit to "空调已开启")
                    }
                    if (openHints.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalAlignment = Alignment.End,
                        ) {
                            openHints.forEachIndexed { index, (icon, text) ->
                                if (index > 0) Spacer(Modifier.height(8.dp))
                                StatusItem(icon, text, alert = true)
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
                // 快捷换壁纸的触发方式由设置决定：小白点 / 点车模本体 / 不提供
                val carQuick = quickSwitch.carTarget
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
                        modifier = Modifier.fillMaxSize(),
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

            // ---- 空调概览条：车内温度 / ◀ 设定温度 ▶ / 风扇开关 ----
            // 点加减号只改温度（关机态是草稿），点右侧风扇开关才带着温度下发开启指令。
            // 点卡片空白处进入全屏空调页（风量 / 出风方向 / 座椅舒适…都在那一页）
            Spacer(Modifier.height(14.dp))
            AcQuickCard(
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

            // ---- 胎压监测卡（左半）+ 近 7 天行程卡（右半）；相伴卡整行宽放在下面 ----
            // 胎压卡点进去是胎压详情页（四轮数值 + 温度 + 状态）；
            // 近 7 天行程卡画每日里程柱状图；相伴卡的提车天数 / 累计公里都取自里程能耗接口。
            // 三张卡本身都不发指令。
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TireQuickCard(
                    status = currentStatus,
                    modifier = Modifier.weight(1f),
                    onOpen = { showTire = true },
                )
                Spacer(Modifier.width(12.dp))
                WeeklyTripCard(
                    energy = energySummary,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            OwnershipCard(
                status = currentStatus,
                energy = energySummary,
                backdropId = ownershipBackdropId,
                quickTarget = quickSwitch.cardTarget,
                onRandomBackdrop = onRandomBackdrop,
            )

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
                        onOpenTire = { showTire = true },
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
            }
        }

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
                                    when {
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
                            text = "${vehicle.carType} · VIN ${vehicle.vin.takeLast(6)}",
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
 * 里程行（手绘稿样式）：从左至右依次是 图标 · 里程 · 进度条（撑满剩余）· 百分比。
 * 百分比列固定宽度右对齐，保证上下两行的进度条长度一致。
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
            modifier = Modifier.width(46.dp),
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
        Spacer(Modifier.width(6.dp))
        Text(
            text = percent,
            color = C.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

/**
 * 车辆状态面板里的单行状态：左图标 + 右文字。
 * 文字统一用主色（浅色模式黑、深色模式白）—— 状态提示压在动态背景上时，
 * 灰色或红色小字会糊掉；警示信息改由图标颜色承担（[alert]=true 时图标转红）。
 */
@Composable
private fun StatusItem(icon: ImageVector, label: String, alert: Boolean) {
    val C = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (alert) STATUS_ALERT_RED else C.textPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 15.sp,
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
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

