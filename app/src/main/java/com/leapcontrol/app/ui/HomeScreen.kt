package com.leapcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Curtains
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleCapabilities
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    seasonId: String,
    onSeasonChange: (String) -> Unit,
    /** 向上汇报「当前是否在零控首页主界面」—— 视频背景只在首页主界面播放 */
    onMainPageChange: (Boolean) -> Unit,
    /** 向上汇报「车模是否已滚出可视区」—— 滚出后视频背景切换为纯色背景，提高可读性 */
    onCarModelGoneChange: (Boolean) -> Unit,
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
        val scope = rememberCoroutineScope()
        // 车况提升到 AppRuntime 持有（跨页面/跨 Tab 保留 + 磁盘缓存），
        // 这里只订阅，不再用 remember 存 —— 切到「我的」再回来不会先变成 null 而闪一下。
        val status by runtime.statusFlow.collectAsState()
        val lastUpdated by runtime.lastUpdatedFlow.collectAsState()
        // 行驶里程 / 能耗（独立接口，拿不到就静默保持上一次结果）
        val energySummary by runtime.mileageEnergyFlow.collectAsState()
        var refreshError by remember { mutableStateOf<String?>(null) }
        var actionMessage by remember { mutableStateOf<String?>(null) }
        var controlling by remember { mutableStateOf<String?>(null) }
        // 只在「用户手动下拉刷新」时转圈；来自生命周期的自动刷新全程静默，避免闪动
        var refreshing by remember { mutableStateOf(false) }
        var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
        var carName by remember { mutableStateOf(runtime.displayName()) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var showPinDialog by remember { mutableStateOf(false) }
        var pendingCommand by remember { mutableStateOf<String?>(null) }
        var pendingAcTemp by remember { mutableStateOf<Int?>(null) }
        // 空调开/关指令在 PIN 验证完成后重放（记录要重放的指令名）
        var pendingAcCommand by remember { mutableStateOf<String?>(null) }
        var controlLayout by remember { mutableStateOf(runtime.controlLayout()) }
        var showControlEditor by remember { mutableStateOf(false) }
        // 车辆配置（冰箱/天窗/哨兵/座椅…）：已持久化的识别结果，不依赖当次车况
        val capabilities by runtime.capabilities.collectAsState()
        // 首帧直接复用 runtime 已解码好的车模，避免重进页面时车模先消失再回来
        var carBitmap by remember { mutableStateOf(runtime.cachedCarBitmap()) }
        var vinKey by remember { mutableStateOf(runtime.session.selectedVin) }
        var vehicles by remember { mutableStateOf<List<com.leapcontrol.app.Vehicle>>(emptyList()) }
        var showVehicleMenu by remember { mutableStateOf(false) }
        // 充电中心二级页状态
        var showChargingCenter by remember { mutableStateOf(false) }
        var healthyCharge by remember { mutableStateOf(false) }
        var chargeLimit by remember { mutableStateOf(100) }
        var reserveCharge by remember { mutableStateOf(false) }
        var batteryPreheat by remember { mutableStateOf(false) }
        var chargeSaving by remember { mutableStateOf(false) }
        var chargeResult by remember { mutableStateOf<String?>(null) }
        // 预约充电计划（进入充电中心时从车端回填，避免写死 23:00 / 07:00）
        var planStart by remember { mutableStateOf("23:00") }
        var planEnd by remember { mutableStateOf("07:00") }
        var planTargetSoc by remember { mutableStateOf(80) }
        var preheatStart by remember { mutableStateOf("07:00") }
        var planLoaded by remember { mutableStateOf(false) }
        // 充电枪操作指令在 PIN 验证完成后重放
        var pendingChargeGun by remember { mutableStateOf<String?>(null) }
        // 二级页面：座椅舒适 / 车载冰箱 / 完整空调
        var showSeatComfort by remember { mutableStateOf(false) }
        var showFridge by remember { mutableStateOf(false) }
        var showClimate by remember { mutableStateOf(false) }
        // 定时空调 / 车辆体检 / 系统升级
        var showScheduledAc by remember { mutableStateOf(false) }
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
        val season = SeasonThemes.byId(seasonId)

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

        LaunchedEffect(Unit) { vehicles = runCatching { runtime.vehicles() }.getOrDefault(emptyList()) }

        // 二级页（充电中心 / 座椅 / 空调 / 体检 / OTA…）不属于「首页」，
        // 打开时向 MainScreen 汇报，让背景视频只在首页主界面播放
        val onMainPage = !showChargingCenter && !showSeatComfort && !showFridge && !showClimate &&
            !showScheduledAc && !showVehicleHealth && !showOta && !showControlEditor &&
            !showTire
        LaunchedEffect(onMainPage) { onMainPageChange(onMainPage) }
        // 车模滚出可视区 -> 通知 MainScreen 把视频背景切纯色
        val carModelGone = homeScrollState.value >= carModelBottomY - 2f
        LaunchedEffect(carModelGone) { onCarModelGoneChange(carModelGone) }

        // 进入首页时拉取一次官方车模图（本地有缓存则直接用）。
        // 车模默认常开（设置里的显示开关已移除），不再有「关闭车模」这条分支；
        // 依赖 vinKey：切换车辆后自动重新拉取
        LaunchedEffect(vinKey) {
            carBitmap = runCatching { runtime.fetchCarImage() }.getOrNull()
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
        // 每分钟刷新"更新于 x 前"文案（仅刷新文案，不请求数据）
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                nowTick = System.currentTimeMillis()
            }
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
         * 空调「开启 / 关闭」下发：[turnOn] 为 true 固定发 acOn，false 固定发 acOff。
         *
         * 与之前「按推断状态取反」的做法不同：这里**不做状态推断**，点哪个按钮就发哪条指令，
         * 因此不会出现「因为状态读不到而一直发 acOn」的问题。
         * 指令返回后强制回拉车况（refresh），把真实的 acSwitch / acSetting 状态刷回界面。
         */
        fun runAcCommand(turnOn: Boolean) {
            scope.launch {
                val command = if (turnOn) "acOn" else "acOff"
                controlling = command
                actionMessage = null
                try {
                    actionMessage = runtime.control(command)
                    // 关键：回拉真实车况，刷新首页空调状态展示
                    refresh(force = true)
                    // 车端上报 acSwitch / acSetting 通常滞后几秒，再拉一次让状态收敛，
                    // 避免刚下发就回读到旧值、状态展示与实际不符
                    delay(AC_SETTLE_DELAY_MS)
                    refresh(force = true)
                } catch (e: NeedPinException) {
                    pendingAcCommand = command
                    showPinDialog = true
                } catch (e: Exception) {
                    actionMessage = friendlyError(e)
                }
                controlling = null
            }
        }

        /** 充电中心：保存并下发设置（健康充电/预约充电/预约电池预热，参考原项目实现）。 */
        fun saveCharging() {
            scope.launch {
                chargeSaving = true
                chargeResult = runCatching {
                    runtime.applyChargingSettings(
                        healthyCharge = healthyCharge,
                        chargeLimit = chargeLimit,
                        reserveCharge = reserveCharge,
                        batteryPreheat = batteryPreheat,
                        scheduledStart = planStart,
                        scheduledEnd = planEnd,
                        scheduledTargetSoc = planTargetSoc,
                        preheatStart = preheatStart,
                    )
                }.fold(
                    onSuccess = { "充电设置已下发：$it" },
                    onFailure = { friendlyError(it) },
                )
                chargeSaving = false
            }
        }

        // 进入充电中心时回填车端已保存的计划（只拉一次，用户手动改过之后不再覆盖）
        LaunchedEffect(showChargingCenter) {
            if (showChargingCenter && !planLoaded) {
                val plan = runCatching { runtime.loadChargePlan() }.getOrNull()
                if (plan != null) {
                    reserveCharge = plan.isEnable
                    plan.beginTime?.let { planStart = it }
                    plan.endTime?.let { planEnd = it }
                    plan.percent?.let { planTargetSoc = it.coerceIn(50, 100) }
                }
                planLoaded = true
            }
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
                chargeResult = null
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
                // 定时空调是同级二级页：必须先收起空调页，否则会被上面的分支挡住不显示
                onOpenScheduledAc = {
                    showClimate = false
                    showScheduledAc = true
                },
            )
        } else if (showChargingCenter) {
            ChargingCenterScreen(
                status = status,
                healthyCharge = healthyCharge,
                onHealthyChargeChange = { healthyCharge = it },
                chargeLimit = chargeLimit,
                onChargeLimitChange = { chargeLimit = it },
                reserveCharge = reserveCharge,
                onReserveChargeChange = { reserveCharge = it },
                planStart = planStart,
                onPlanStartChange = { planStart = it },
                planEnd = planEnd,
                onPlanEndChange = { planEnd = it },
                planTargetSoc = planTargetSoc,
                onPlanTargetSocChange = { planTargetSoc = it },
                batteryPreheat = batteryPreheat,
                onBatteryPreheatChange = { batteryPreheat = it },
                preheatStart = preheatStart,
                onPreheatStartChange = { preheatStart = it },
                onChargeGun = { runChargeGun(it) },
                gunBusy = controlling == "chargeGun",
                onSave = { saveCharging() },
                saving = chargeSaving,
                saveResult = chargeResult,
                onBack = { showChargingCenter = false },
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
                // 定时空调现在只从「车内温控」页进入，返回时回到该页而不是首页
                onBack = {
                    showScheduledAc = false
                    showClimate = true
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
                // 右侧图标按钮：个性化（皮肤），顶边与左侧车名顶端对齐
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        // 车名 + 下拉箭头：多车时点这一块切换车辆
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = vehicles.size > 1) { showVehicleMenu = true },
                        ) {
                            Text(
                                text = carName,
                                color = LocalAppColors.current.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                            // 账号下有多辆车时才显示下拉箭头
                            if (vehicles.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "切换车辆",
                                    tint = LocalAppColors.current.textPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        // 编辑车名：只存本地，清空则回到零跑官方 App 的昵称
                        HeaderIconButton(
                            iconRes = R.drawable.ic_edit,
                            description = "修改车名",
                            size = 18.dp,
                            onClick = { showRenameDialog = true },
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
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
                }
            }

            // ---- 快捷控车：一行显示 4 个，左右滑动查看其余，下方带横向滚动提示条 ----
            Spacer(Modifier.height(6.dp))
            // 车不带对应硬件的按钮直接不出现（如无天窗的车不给天窗入口）
            val cells = ControlActions.available(capabilities, controlLayout)
                .map { it.id } + listOf(EDIT_CELL)
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

            // ---- 空调概览条：设定温度 / 车内温度 / 风扇开关（风扇可一键开关空调）----
            Spacer(Modifier.height(14.dp))
            AcQuickCard(
                status = currentStatus,
                busy = controlling == "acOn" || controlling == "acOff",
                onToggle = { turnOn ->
                    ensureConfirmed(if (turnOn) "开启空调" else "关闭空调") { runAcCommand(turnOn) }
                },
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
                containerColor = LocalAppColors.current.cardBg,
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
                containerColor = LocalAppColors.current.cardBg,
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
                                    when {
                                        acCmd != null -> {
                                            val turnOn = acCmd == "acOn"
                                            pendingAcCommand = null
                                            runAcCommand(turnOn)
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
        containerColor = LocalAppColors.current.cardBg,
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
        containerColor = LocalAppColors.current.cardBg,
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

/** 四季主题选择弹窗。 */
@Composable
private fun ThemePickerDialog(
    currentId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.cardBg,
        title = { Text("主题", color = LocalAppColors.current.textPrimary, fontSize = 17.sp) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeasonThemes.ALL.forEach { theme ->
                    val selected = theme.id == currentId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(theme.id) }
                            .padding(2.dp)
                            .weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.verticalGradient(theme.gradient(LocalAppColors.current.isDark)))
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, LocalAppColors.current.accent, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier.border(0.5.dp, LocalAppColors.current.barTrack, RoundedCornerShape(10.dp))
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, null, tint = LocalAppColors.current.accent, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = theme.label,
                            color = if (selected) LocalAppColors.current.accent else LocalAppColors.current.textPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = LocalAppColors.current.accent) }
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
                                text = tire?.pressure ?: "--",
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
 * 控车 / 刷新结果通知：屏幕正中、系统通知样式，6 秒后自动消失，点击可关闭。
 *
 * 之前这条反馈是滚动列表最后一行——用户根本滚不到那里，
 * 于是「指令发出但 nothing changed」就被感知成“点了没反应”；改成居中浮层后始终可见。
 */
@Composable
private fun ActionBanner(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    if (message.isNullOrBlank()) return
    val C = LocalAppColors.current
    LaunchedEffect(message) {
        delay(6_000)
        onDismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isError) Color(0xFFFDECEA) else C.cardBg)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.Close else Icons.Filled.Check,
                contentDescription = null,
                tint = if (isError) Color(0xFFD32F2F) else Color(0xFF2FCC71),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                color = if (isError) Color(0xFFD32F2F) else C.textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
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
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = C.cardBg,
            shadowElevation = 18.dp,
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
        containerColor = LocalAppColors.current.cardBg,
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
    val progress by remember(state, itemCount, visibleCount) {
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
                    .offset(x = (trackWidth - thumbWidth) * progress)
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

/** 开关行：左标题 + 右开关（充电中心二级页用）。 */
@Composable
private fun ChargeSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = C.accent,
                uncheckedTrackColor = C.barTrack,
            ),
        )
    }
}

/** 充电枪连接状态文案（信号缺失时给出中性的未知提示，不猜测）。 */
private fun chargeConnectionText(status: CarStatus?): String = when {
    status?.charging == true -> "正在充电中"
    status?.chargeCableConnected == true -> "充电枪已连接，未开始充电"
    status?.chargeCableConnected == false -> "充电枪未连接"
    else -> "充电枪连接状态未知"
}

/** 单项读数格：上标题下数值，数值缺失显示「--」。 */
@Composable
private fun ChargeReadingCell(title: String, value: String?, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, color = C.textSecondary, fontSize = 11.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value ?: "--",
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/** 充电枪操作按钮：竖排图标 + 文案。 */
@Composable
private fun GunActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.iconBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) C.accent else C.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (enabled) C.textPrimary else C.textSecondary,
            fontSize = 11.5.sp,
        )
    }
}

/** 时间选择行：左右两个下拉（小时 / 分钟），分钟按 5 分钟步进到整刻。 */
@Composable
private fun TimePickerRow(
    label: String,
    time: String,
    onTimeChange: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TimeDropdown(
            value = hour,
            range = 0..23,
            step = 1,
            onValueChange = { onTimeChange(String.format(Locale.CHINA, "%02d:%02d", it, minute)) },
        )
        Text(":", color = C.textPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
        TimeDropdown(
            value = minute,
            range = 0..59,
            step = 5,
            onValueChange = { onTimeChange(String.format(Locale.CHINA, "%02d:%02d", hour, it)) },
        )
    }
}

/** 单个时间的下拉选择器。 */
@Composable
private fun TimeDropdown(
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val options = remember(range, step) { range.step(step).toList() }
    val display = String.format(Locale.CHINA, "%02d", options.indexOf(value).let { if (it >= 0) value else options.first() })
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(C.iconBg)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = display, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = C.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(String.format(Locale.CHINA, "%02d", option)) },
                    onClick = { onValueChange(option); expanded = false },
                )
            }
        }
    }
}

/**
 * 充电中心二级页（参考官网 App）：
 * 大字 SOC + 电池示意 + 充电实时读数 + 充电枪操作 + 健康充电 / 充电上限 /
 * 预约充电（可配时间）/ 预约电池预热 / 绑桩。
 */
@Composable
private fun ChargingCenterScreen(
    status: CarStatus?,
    healthyCharge: Boolean,
    onHealthyChargeChange: (Boolean) -> Unit,
    chargeLimit: Int,
    onChargeLimitChange: (Int) -> Unit,
    reserveCharge: Boolean,
    onReserveChargeChange: (Boolean) -> Unit,
    planStart: String,
    onPlanStartChange: (String) -> Unit,
    planEnd: String,
    onPlanEndChange: (String) -> Unit,
    planTargetSoc: Int,
    onPlanTargetSocChange: (Int) -> Unit,
    batteryPreheat: Boolean,
    onBatteryPreheatChange: (Boolean) -> Unit,
    preheatStart: String,
    onPreheatStartChange: (String) -> Unit,
    onChargeGun: (String) -> Unit,
    gunBusy: Boolean = false,
    onSave: () -> Unit,
    saving: Boolean = false,
    saveResult: String? = null,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val soc = status?.soc
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        // 顶栏：左返回 + 居中标题
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
            Text(
                text = "充电中心",
                color = C.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            Surface(
                shape = CircleShape,
                color = C.iconBg,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "返回",
                        tint = C.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // 大字 SOC
        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = soc?.toString() ?: "--",
                color = C.textPrimary,
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = "%",
                color = C.textSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }
        // 电池示意条（绿色渐变填充）
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.72f)
                .height(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(C.barTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(((soc ?: 0) / 100f).coerceIn(0f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2FCC71), Color(0xFF7BE09B)),
                        ),
                    ),
            )
        }

        // 充电实时读数：功率 / 剩余时间 / 电压 / 电流 / 电池温度
        Spacer(Modifier.height(18.dp))
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChargeReadingCell("功率", status?.chargingPower, Modifier.weight(1f))
                ChargeReadingCell("剩余时间", status?.chargeTimeLeft, Modifier.weight(1f))
                ChargeReadingCell("电压", status?.chargeVoltage, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChargeReadingCell("电流", status?.chargeCurrent, Modifier.weight(1f))
                ChargeReadingCell("电池温度", status?.batteryTemp, Modifier.weight(1f))
                Box(Modifier.weight(1f))
            }
        }

        // 充电枪操作：开始 / 停止 / 解锁
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(
                text = chargeConnectionText(status),
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                GunActionButton(
                    label = "开始充电",
                    icon = Icons.Filled.PlayArrow,
                    enabled = !gunBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onChargeGun("start") },
                )
                Spacer(Modifier.width(8.dp))
                GunActionButton(
                    label = "停止充电",
                    icon = Icons.Filled.Stop,
                    enabled = !gunBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onChargeGun("stop") },
                )
                Spacer(Modifier.width(8.dp))
                GunActionButton(
                    label = "解锁充电枪",
                    icon = Icons.Filled.LockOpen,
                    enabled = !gunBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onChargeGun("unlock") },
                )
            }
        }

        // 健康充电 + 充电上限
        Spacer(Modifier.height(14.dp))
        AppCard {
            ChargeSwitchRow("健康充电", healthyCharge, onHealthyChargeChange)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "打开后，将根据车辆电池状态，自动为您调整充电上限，以保持电池健康",
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "充电上限$chargeLimit%",
                color = C.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("80%", color = C.textSecondary, fontSize = 12.sp)
                Slider(
                    value = chargeLimit.toFloat(),
                    onValueChange = { onChargeLimitChange(it.roundToInt()) },
                    valueRange = 80f..100f,
                    steps = 4,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                Text("100%", color = C.textSecondary, fontSize = 12.sp)
            }
            Text(
                text = "最佳阈值 90%",
                color = C.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            )
        }

        // 预约充电
        Spacer(Modifier.height(14.dp))
        AppCard {
            ChargeSwitchRow("预约充电", reserveCharge, onReserveChargeChange)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "插枪后会根据设定时间充电，仅支持慢充",
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            if (reserveCharge) {
                Spacer(Modifier.height(14.dp))
                TimePickerRow("开始时间", planStart, onPlanStartChange)
                Spacer(Modifier.height(10.dp))
                TimePickerRow("结束时间", planEnd, onPlanEndChange)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "充电至$planTargetSoc%",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("50%", color = C.textSecondary, fontSize = 12.sp)
                    Slider(
                        value = planTargetSoc.toFloat(),
                        onValueChange = { onPlanTargetSocChange(it.roundToInt()) },
                        valueRange = 50f..100f,
                        steps = 9,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    Text("100%", color = C.textSecondary, fontSize = 12.sp)
                }
            }
        }

        // 预约电池预热
        Spacer(Modifier.height(14.dp))
        AppCard {
            ChargeSwitchRow("预约电池预热", batteryPreheat, onBatteryPreheatChange)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "寒冷天气时，电池系统会自动调节电池温度，以提升电池续航和性能。达到理想温度时自动停止预热。",
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            if (batteryPreheat) {
                Spacer(Modifier.height(14.dp))
                TimePickerRow("预热开始", preheatStart, onPreheatStartChange)
            }
        }

        // 绑桩
        Spacer(Modifier.height(14.dp))
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { /* 绑桩流程暂未开放 */ },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "绑桩",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = C.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "绑桩后，插枪即可进行充电，安全省心。",
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "暂无绑桩",
                color = C.textSecondary,
                fontSize = 12.sp,
            )
        }

        // 保存并下发设置
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text("下发中…", color = Color.White, fontSize = 15.sp)
            } else {
                Text("保存并下发设置", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (saveResult != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = saveResult!!,
                color = C.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
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
            tint = LocalAppColors.current.textPrimary,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * 车况更新时间：只保留「月-日 时:分」（MM-dd HH:mm）。
 * 放在车名下方，年份与秒钟对当下刷新判断没有意义，去掉后一行能完整显示。
 */
private fun formatUpdateTime(ts: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    return sdf.format(Date(ts))
}

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

