package com.leapbuddy.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.view.WindowCompat
import com.leapbuddy.app.AmapPoi
import com.leapbuddy.app.MapIntents
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    seasonId: String,
    onSeasonChange: (String) -> Unit,
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
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<CarStatus?>(null) }
        var refreshError by remember { mutableStateOf<String?>(null) }
        var actionMessage by remember { mutableStateOf<String?>(null) }
        var controlling by remember { mutableStateOf<String?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        var lastUpdated by remember { mutableStateOf(0L) }
        var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
        var carName by remember { mutableStateOf(runtime.displayName()) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var showPinDialog by remember { mutableStateOf(false) }
        var pendingCommand by remember { mutableStateOf<String?>(null) }
        var pendingAcTemp by remember { mutableStateOf<Int?>(null) }
        var controlLayout by remember { mutableStateOf(runtime.controlLayout()) }
        var showControlEditor by remember { mutableStateOf(false) }
        var carBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var vinKey by remember { mutableStateOf(runtime.session.selectedVin) }
        var vehicles by remember { mutableStateOf<List<com.leapbuddy.app.Vehicle>>(emptyList()) }
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
        var acPlan by remember { mutableStateOf(com.leapbuddy.app.ScheduledAcPlan()) }
        var acPlanLoading by remember { mutableStateOf(false) }
        var acPlanLoadFailed by remember { mutableStateOf(false) }
        var acPlanSaving by remember { mutableStateOf(false) }
        var acPlanResult by remember { mutableStateOf<String?>(null) }
        var otaInfo by remember { mutableStateOf<com.leapbuddy.app.VehicleOtaInfo?>(null) }
        var otaLoading by remember { mutableStateOf(false) }
        var otaBusy by remember { mutableStateOf<String?>(null) }
        var otaResult by remember { mutableStateOf<String?>(null) }
        var pendingHealthFix by remember { mutableStateOf<String?>(null) }
        var pendingOtaAction by remember { mutableStateOf<Pair<String, String>?>(null) }
        // 冰箱指令在 PIN 验证完成后重放
        var pendingFridgeCommand by remember {
            mutableStateOf<com.leapbuddy.app.FridgeControlCommand?>(null)
        }
        val season = SeasonThemes.byId(seasonId)

        LaunchedEffect(Unit) { vehicles = runCatching { runtime.vehicles() }.getOrDefault(emptyList()) }

        // 进入首页时拉取一次官方车模图（本地有缓存则直接用）；
        // 若用户在设置里关闭了车模显示，则完全不请求官方图。
        // 依赖 vinKey：切换车辆后自动重新拉取
        val artworkMode = runtime.carArtworkMode()
        LaunchedEffect(artworkMode, vinKey) {
            carBitmap = if (artworkMode == "off") {
                null
            } else {
                runCatching { runtime.fetchCarImage() }.getOrNull()
            }
        }

        // 去重：2 秒内的重复请求忽略（避免冷启动时启动刷新与前台事件叠加）
        var lastRequestAt = 0L
        fun refresh(force: Boolean = false) {
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
                refreshing = true
                try {
                    status = runtime.refreshStatus()
                    refreshError = null
                    lastUpdated = System.currentTimeMillis()
                } catch (e: Exception) {
                    refreshError = friendlyError(e)
                }
                refreshing = false
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
        fun saveScheduledAc(plan: com.leapbuddy.app.ScheduledAcPlan) {
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
        val healthReport = remember(status) { VehicleHealthDiagnostics.evaluate(status) }
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
        fun runFridgeCommand(command: com.leapbuddy.app.FridgeControlCommand) {
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
                onCommand = { runControl(it) },
                onTempChange = { runAcTemperature(it) },
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
                onBack = { showScheduledAc = false },
            )
        } else if (showVehicleHealth) {
            VehicleHealthScreen(
                status = status,
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
        } else androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refresh(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            // ---- 顶部（参考设计图）：左 车名 · 右 更新时间（带转圈） ----
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)) {
                // 右上：刷新状态 + 更新时间
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 1.8.dp,
                            color = LocalAppColors.current.textSecondary,
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = when {
                            refreshing -> "更新中…"
                            lastUpdated == 0L -> "尚未更新"
                            else -> "车况更新于 ${formatUpdateTime(lastUpdated)}"
                        },
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(enabled = !refreshing) { refresh() },
                    )
                }
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
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
                            modifier = Modifier.widthIn(max = 230.dp),
                        )
                        // 账号下有多辆车时才显示下拉箭头
                        if (vehicles.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "切换车辆",
                                tint = LocalAppColors.current.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
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
                                    status = null
                                    refreshError = null
                                    lastUpdated = 0L
                                    refresh(force = true)
                                }
                            }
                        },
                    )
                }
            }

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
                        fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "km",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 14.sp,
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
                    // 车辆状态：闭锁始终显示；车窗/后备箱/遮阳帘仅开启时红色高亮；同一行、可横向滚动
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val locked = currentStatus?.locked
                        StatusItem(
                            icon = if (locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            label = when (locked) {
                                true -> "车辆已闭锁"
                                false -> "车辆未闭锁"
                                null -> "闭锁状态未知"
                            },
                            alert = locked == false,
                        )
                        if (currentStatus?.windowOpen == true) {
                            Spacer(Modifier.width(16.dp))
                            StatusItem(Icons.Filled.Window, "车窗未关", alert = true)
                        }
                        if (currentStatus?.trunkOpen == true) {
                            Spacer(Modifier.width(16.dp))
                            StatusItem(Icons.Filled.Luggage, "后备箱未关", alert = true)
                        }
                        if (currentStatus?.sunshadeOpen == true) {
                            Spacer(Modifier.width(16.dp))
                            StatusItem(Icons.Filled.Curtains, "遮阳帘开启", alert = true)
                        }
                    }
                }
            }

            // ---- 充电中心入口（胶囊按钮，续航下方，样式参考官网 App）----
            ChargingCenterEntry(onClick = { showChargingCenter = true })

            // ---- 车模：只显示官方接口图；拉不到（或已关闭）就不占位置 ----
            if (carBitmap != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
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

            // ---- 行车状态（车模下方居中，灰色无图标，靠车模更近）----
            // （无占位间距，负位移再向上贴近车模）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                DrivingStatusItem(currentStatus?.drivingStatus)
            }
            // ---- 快捷控车：一行显示 4 个，左右滑动查看其余，下方带横向滚动提示条 ----
            Spacer(Modifier.height(10.dp))
            val cells = controlLayout + listOf(EDIT_CELL)
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
                                        icon = action.icon,
                                        busy = controlling == cellId,
                                        active = active,
                                        onClick = { runControl(cellId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ScrollHintBar(state = listState, itemCount = cells.size, visibleCount = 4)

            // ---- 快捷控车下方：空调 + 位置 两个半宽模块 ----
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AcTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showClimate = true },
                    status = currentStatus,
                    busy = controlling == "acTemp",
                    onToggleAc = { runControl(if (currentStatus?.acOn == true) "acOff" else "acOn") },
                    onTempChange = { runAcTemperature(it) },
                )
                LocationTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    status = currentStatus,
                    runtime = runtime,
                )
            }

            // ---- 座椅舒适 + 车载冰箱 ----
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeatComfortTile(
                    modifier = Modifier.weight(1f),
                    status = currentStatus,
                    onClick = { showSeatComfort = true },
                )
                FridgeTile(
                    modifier = Modifier.weight(1f),
                    status = currentStatus,
                    onClick = { showFridge = true },
                )
            }

            // ---- 定时空调 + 车辆体检 + 系统升级 ----
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScheduledAcTile(modifier = Modifier.weight(1f), onClick = { showScheduledAc = true })
                VehicleHealthTile(
                    modifier = Modifier.weight(1f),
                    subtitle = healthSummary,
                    onClick = { showVehicleHealth = true },
                )
                OtaTile(
                    modifier = Modifier.weight(1f),
                    subtitle = otaSubtitle,
                    onClick = { showOta = true },
                )
            }

            Spacer(Modifier.height(20.dp))
            // ---- 底部反馈信息 ----
            (refreshError ?: actionMessage)?.let { msg ->
                Text(
                    text = msg,
                    color = if (refreshError != null) Color(0xFFFF6B6B) else LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    maxLines = 3,
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
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(12) },
                        placeholder = { Text("如：小7", color = LocalAppColors.current.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LocalAppColors.current.textPrimary,
                            unfocusedTextColor = LocalAppColors.current.textPrimary,
                            cursorColor = LocalAppColors.current.accent,
                            focusedBorderColor = LocalAppColors.current.accent,
                            unfocusedBorderColor = LocalAppColors.current.barTrack,
                        ),
                    )
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
                                    when {
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
    vehicles: List<com.leapbuddy.app.Vehicle>,
    selectedVin: String,
    onDismiss: () -> Unit,
    onSelect: (com.leapbuddy.app.Vehicle) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SeasonThemes.ALL.forEach { theme ->
                    val selected = theme.id == currentId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(theme.id) }
                            .padding(4.dp)
                            .weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
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
                            fontSize = 13.sp,
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

/**
 * 空调模块：右上风扇按钮开关空调，中间「− 温度 +」调温（参考原项目 Commands.buildAc），
 * 底部显示车内实时温度。
 */
@Composable
private fun AcTile(
    modifier: Modifier = Modifier,
    status: CarStatus?,
    busy: Boolean,
    onToggleAc: () -> Unit,
    onTempChange: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    val target = status?.acTargetTemp?.toIntOrNull() ?: 24
    val acOn = status?.acOn == true
    ModuleTile(
        modifier = modifier,
        title = "空调",
        cornerIcon = {
            Surface(
                shape = CircleShape,
                color = C.iconBg,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable(enabled = !busy, onClick = onToggleAc),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Toys,
                        contentDescription = if (acOn) "关闭空调" else "开启空调",
                        tint = if (acOn) C.accent else C.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "−",
                color = C.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !busy) { onTempChange((target - 1).coerceIn(16, 32)) }
                    .padding(horizontal = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${target}°",
                color = C.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "+",
                color = C.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !busy) { onTempChange((target + 1).coerceIn(16, 32)) }
                    .padding(horizontal = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "车内温度 ${status?.interiorTemp ?: "--"}°C",
            color = C.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 位置模块：右上绿色定位图标，中间点击打开地图，底部显示行车状态。 */
@Composable
private fun LocationTile(
    modifier: Modifier = Modifier,
    status: CarStatus?,
    runtime: AppRuntime,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val lat = status?.latitude
    val lng = status?.longitude
    var address by remember(lat, lng) { mutableStateOf<String?>(null) }

    LaunchedEffect(lat, lng) {
        if (lat == null || lng == null) return@LaunchedEffect
        address = runCatching { runtime.resolveAddress(lat, lng) }.getOrNull()
    }

    ModuleTile(
        modifier = modifier,
        title = "位置",
        cornerIcon = {
            Surface(shape = CircleShape, color = C.iconBg, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF2FCC71),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                address != null -> address!!
                lat == null || lng == null -> "暂无位置数据"
                else -> "点击打开地图"
            },
            color = C.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(lat != null && lng != null) {
                    MapIntents.showPoint(context, lat!!, lng!!, address ?: "我的车")
                }
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = status?.drivingStatus ?: "--",
            color = C.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 位置模块：当前地址（逆地理）+ 点击打开地图 + 搜索目的地发送车机 */
@Composable
private fun LocationModule(
    status: CarStatus,
    runtime: AppRuntime,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val lat = status.latitude
    val lng = status.longitude
    var address by remember(lat, lng) { mutableStateOf<String?>(null) }
    var resolving by remember(lat, lng) { mutableStateOf(lat != null && lng != null) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(lat, lng) {
        if (lat == null || lng == null) return@LaunchedEffect
        resolving = true
        address = runCatching { runtime.resolveAddress(lat, lng) }.getOrNull()
        resolving = false
    }

    ModuleCard("车辆位置") {
        if (lat == null || lng == null) {
            Text("暂无位置数据", color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        MapIntents.showPoint(context, lat, lng, address ?: "我的车")
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            address != null -> address!!
                            resolving -> "正在解析位置…"
                            !runtime.hasAmapKey() -> "未配置高德 Key，仅显示坐标"
                            else -> "位置解析失败"
                        },
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "纬度 %.5f · 经度 %.5f".format(lat, lng),
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    null,
                    tint = LocalAppColors.current.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { MapIntents.showPoint(context, lat, lng, address ?: "我的车") },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = LocalAppColors.current.accent,
                    ),
                ) {
                    Text("打开地图", fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.accent,
                        contentColor = Color(0xFFFFFFFF),
                    ),
                ) {
                    Text("发送目的地", fontSize = 12.sp)
                }
            }
        }
    }

    if (showPicker) {
        DestinationPickerDialog(
            runtime = runtime,
            onDismiss = { showPicker = false },
            onResult = { msg -> onMessage(msg) },
        )
    }
}

/** 目的地搜索弹窗：搜到 POI 后可发送到车机或直接导航。 */
@Composable
private fun DestinationPickerDialog(
    runtime: AppRuntime,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AmapPoi>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AmapPoi?>(null) }

    fun search(text: String) {
        if (text.isBlank()) {
            results = emptyList()
            searched = false
            return
        }
        scope.launch {
            searching = true
            searched = true
            results = runCatching { runtime.searchDestination(text) }.getOrDefault(emptyList())
            searching = false
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.cardBg,
        title = {
            Text("搜索目的地", color = LocalAppColors.current.textPrimary, fontSize = 16.sp)
        },
        text = {
            Column {
                if (!runtime.hasAmapKey()) {
                    Text(
                        "未配置高德 Web Key，无法搜索。请在「我的 → 高德 Web Key」中填写。",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it; selected = null },
                    placeholder = { Text("输入地点名称", color = LocalAppColors.current.textSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search(keyword) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        cursorColor = LocalAppColors.current.accent,
                        focusedBorderColor = LocalAppColors.current.accent,
                        unfocusedBorderColor = LocalAppColors.current.barTrack,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.height(210.dp)) {
                    when {
                        searching -> Text("搜索中…", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                        results.isEmpty() && searched -> Text(
                            "没有找到相关地点",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        else -> androidx.compose.foundation.lazy.LazyColumn {
                            items(results) { poi ->
                                val picked = selected?.id == poi.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (picked) LocalAppColors.current.chipBgActive else Color.Transparent,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable { selected = poi }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(poi.name, color = LocalAppColors.current.textPrimary, fontSize = 13.sp, maxLines = 1)
                                        if (poi.address.isNotBlank()) {
                                            Text(
                                                poi.address,
                                                color = LocalAppColors.current.textSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (picked) {
                                        Icon(
                                            Icons.Filled.Check,
                                            null,
                                            tint = LocalAppColors.current.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    val poi = selected ?: return@TextButton
                    val usedAmap = MapIntents.sendToCar(context, poi.latitude, poi.longitude, poi.name)
                    onResult(
                        if (usedAmap) "已在高德中规划「${poi.name}」，手机与车机高德同账号即可同步"
                        else "未安装高德地图，已用网页版打开「${poi.name}」"
                    )
                    onDismiss()
                },
            ) {
                Text(
                    "发送到车机",
                    color = if (selected != null) LocalAppColors.current.accent else LocalAppColors.current.textSecondary,
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = selected != null,
                    onClick = {
                        val poi = selected ?: return@TextButton
                        MapIntents.navigate(context, poi.latitude, poi.longitude, poi.name)
                        onDismiss()
                    },
                ) {
                    Text(
                        "导航",
                        color = if (selected != null) LocalAppColors.current.textPrimary else LocalAppColors.current.textSecondary,
                    )
                }
                TextButton(onClick = onDismiss) { Text("取消", color = LocalAppColors.current.textSecondary) }
            }
        },
    )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    onClick: () -> Unit,
    active: Boolean? = null,
) {
    val on = active == true
    val iconTint = if (on) LocalAppColors.current.accent else LocalAppColors.current.textPrimary
    val labelColor = if (on) LocalAppColors.current.accent else LocalAppColors.current.textSecondary
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
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(
                    color = when {
                        on -> LocalAppColors.current.iconBgActive
                        pressed -> LocalAppColors.current.chipBgActive
                        else -> LocalAppColors.current.iconBg
                    },
                    shape = CircleShape,
                )
                .then(
                    if (on) {
                        Modifier.border(1.5.dp, LocalAppColors.current.accent.copy(alpha = 0.55f), CircleShape)
                    } else {
                        Modifier.border(1.dp, LocalAppColors.current.border, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = LocalAppColors.current.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
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

/** 末尾的「编辑」按钮 */
@Composable
private fun EditButton(onClick: () -> Unit) {
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
                .background(LocalAppColors.current.iconBg, CircleShape)
                .border(1.dp, LocalAppColors.current.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑控车按钮",
                tint = LocalAppColors.current.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("编辑", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
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
) {
    val items = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(initial) } }
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
                        Icon(action.icon, null, tint = LocalAppColors.current.textPrimary, modifier = Modifier.size(20.dp))
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
                val available = ControlActions.all.filter { it.id !in items }
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
                                    Icon(action.icon, null, modifier = Modifier.size(16.dp))
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
 * 横向滚动提示条：只在内容超出一屏时显示，
 * 滑块宽度 = 可见数 / 总数，位置随 LazyListState 实时跟随。
 */
@Composable
private fun ScrollHintBar(
    state: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    visibleCount: Int,
) {
    if (itemCount <= visibleCount) return
    val C = LocalAppColors.current
    val progress by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
            if (total <= visible) {
                0f
            } else {
                val span = info.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
                val raw = state.firstVisibleItemIndex +
                    state.firstVisibleItemScrollOffset.toFloat() / span
                (raw / (total - visible)).coerceIn(0f, 1f)
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(56.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(C.barTrack),
        ) {
            val thumbWidth = maxWidth * (visibleCount.toFloat() / itemCount).coerceIn(0.25f, 1f)
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumbWidth) * progress)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(C.textSecondary.copy(alpha = 0.55f)),
            )
        }
    }
}

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
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(C.barTrack),
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
            color = C.textSecondary,
            fontSize = 12.sp,
        textAlign = TextAlign.End,
        modifier = Modifier.width(36.dp),
        )
    }
}

/** 车辆状态面板里的单行状态：左图标 + 右文字。
 *  [alert]=true 表示需要警示（红色高亮），否则用次级灰。 */
private val STATUS_ALERT_RED = Color(0xFFE5484D)

@Composable
private fun StatusItem(icon: ImageVector, label: String, alert: Boolean) {
    val C = LocalAppColors.current
    val tint = if (alert) STATUS_ALERT_RED else C.textSecondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
        )
    }
}

/**
 * 行车状态（在闭锁上方一行）：行驶中（蓝）/ 已驻车（主色）/ 未知（次要色）。
 * status 为 null 时显示「行车状态未知」。
 */
/** 行车状态（车模下方居中显示）：行驶中 / 已驻车 / 行车状态未知。灰色文字，无图标。 */
@Composable
private fun DrivingStatusItem(status: String?) {
    val C = LocalAppColors.current
    Text(
        text = status ?: "行车状态未知",
        color = C.textSecondary,
        fontSize = 13.sp,
        lineHeight = 14.sp,
    )
}

/** 充电中心入口：胶囊按钮（文字 + 右箭头），样式参考官网 App。 */
@Composable
private fun ChargingCenterEntry(onClick: () -> Unit) {
    val C = LocalAppColors.current
    Spacer(Modifier.height(14.dp))
    // 小胶囊入口：整体比上一版缩小约 1/3，内容（文字 + 箭头）严格居中
    Surface(
        shape = RoundedCornerShape(50),
        color = C.chipBg,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "充电中心",
                color = C.textPrimary,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(1.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = C.textSecondary,
                modifier = Modifier.size(12.dp),
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

/** 把时间戳格式化成「yyyy-MM-dd HH:mm:ss」，用于车况更新时间展示。 */
private fun formatUpdateTime(ts: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
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

