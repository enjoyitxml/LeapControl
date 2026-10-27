package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.R
import com.leapcontrol.app.ScheduledAcPlan
import com.leapcontrol.app.VehicleCapabilities
import java.util.Locale

/**
 * 首页功能模块区：定义在 [HomeModules]，卡片统一按「空调卡」的尺寸与圆角渲染，
 * 在零控页面最下方横向排布（左右滑动 + 横向滚动指示条），
 * 最底部一个「自定义」按钮可调整顺序、删除与添加模块。
 */
object HomeModules {
    /** 模块分组（用于首页底部按「车辆状态 / 舒适座舱 / 充电中心 / 安全守护」归类）。 */
    const val GROUP_STATUS = "status"
    const val GROUP_COMFORT = "comfort"
    const val GROUP_CHARGING = "charging"
    const val GROUP_SAFETY = "safety"

    /** 分组展示顺序与标题（对应原型四组）。 */
    val GROUP_ORDER = listOf(GROUP_STATUS, GROUP_COMFORT, GROUP_CHARGING, GROUP_SAFETY)
    fun groupTitle(group: String): String = when (group) {
        GROUP_STATUS -> "车辆状态"
        GROUP_COMFORT -> "舒适座舱"
        GROUP_CHARGING -> "充电中心"
        GROUP_SAFETY -> "安全守护"
        else -> ""
    }

    data class Spec(
        val id: String,
        val title: String,
        val iconRes: Int,
        /** 所属分组。 */
        val group: String = GROUP_STATUS,
        /** 本车是否具备该模块：没有对应硬件的模块不给入口。 */
        val available: (VehicleCapabilities) -> Boolean = { true },
    )

    val CLIMATE = Spec("climate", "空调", R.drawable.ic_qc_ac, GROUP_COMFORT)
    val SEAT = Spec("seat", "座椅舒适", R.drawable.ic_mod_seat, GROUP_COMFORT) { it.hasSeatComfort }
    val TIRE = Spec("tire", "胎压", R.drawable.ic_mod_tire, GROUP_STATUS)
    val MILEAGE = Spec("mileage", "里程能耗", R.drawable.ic_mod_mileage, GROUP_STATUS)
    val HEALTH = Spec("health", "车辆健康", R.drawable.ic_health, GROUP_STATUS)
    val FRIDGE = Spec("fridge", "车载冰箱", R.drawable.ic_mod_fridge, GROUP_COMFORT) { it.fridge }
    val SCHEDULED_AC = Spec("scheduledAc", "定时空调", R.drawable.ic_phosphor_fan, GROUP_COMFORT)
    val CHARGING = Spec("charging", "充电", R.drawable.ic_charge_station, GROUP_CHARGING)
    val SENTRY = Spec("sentry", "哨兵模式", R.drawable.ic_qc_sentry, GROUP_SAFETY) { it.sentry }
    val LOCATION = Spec("location", "车辆位置", R.drawable.ic_loc_pin, GROUP_SAFETY)

    val ALL: List<Spec> = listOf(
        CLIMATE, SEAT, TIRE, MILEAGE, HEALTH, FRIDGE, SCHEDULED_AC, CHARGING, SENTRY, LOCATION,
    )

    /**
     * 默认顺序。车辆位置默认不放进来 —— 车端基本不上报经纬度，
     * 放进来也只会显示「--」，需要的话在自定义里手动添加。
     */
    val DEFAULT_ORDER: List<String> = listOf(
        "climate", "mileage", "tire", "seat", "health", "charging", "scheduledAc", "fridge", "sentry",
    )

    fun find(id: String): Spec? = ALL.firstOrNull { it.id == id }

    /** 去掉未知 id 与重复项。 */
    fun sanitize(ids: List<String>): List<String> =
        ids.filter { id -> ALL.any { it.id == id } }.distinct()

    /** 按顺序取出本车可用的模块（不可用的直接跳过，不留空卡片）。 */
    fun available(ids: List<String>, caps: VehicleCapabilities): List<Spec> =
        sanitize(ids).mapNotNull(::find).filter { it.available(caps) }
}

/** 模块卡统一尺寸：与官方空调卡一致（半屏宽 × 150dp，圆角同空调卡）。 */
val MODULE_CARD_HEIGHT = 150.dp
private val MODULE_CARD_RADIUS get() = OFFICIAL_CARD_RADIUS

/** 模块卡片需要的交互回调（由 HomeScreen 传入）。 */
data class ModuleCallbacks(
    val onOpenClimate: () -> Unit = {},
    val onOpenSeat: () -> Unit = {},
    val onOpenFridge: () -> Unit = {},
    val onOpenHealth: () -> Unit = {},
    val onOpenScheduledAc: () -> Unit = {},
    val onOpenCharging: () -> Unit = {},
    val onOpenTire: () -> Unit = {},
    val onOpenFindCar: () -> Unit = {},
    val onToggleSentry: () -> Unit = {},
    val onAcOn: () -> Unit = {},
    val onAcOff: () -> Unit = {},
    val onAcTempChange: (Int) -> Unit = {},
    val acBusy: Boolean = false,
    val onRefreshEnergy: () -> Unit = {},
)

/**
 * 首页最下方的模块区：横向滑动的模块卡 + 滚动指示条 + 自定义按钮。
 */
@Composable
fun HomeModuleSection(
    runtime: AppRuntime,
    status: CarStatus?,
    capabilities: VehicleCapabilities,
    energy: MileageEnergySummary?,
    acPlan: ScheduledAcPlan,
    callbacks: ModuleCallbacks,
) {
    var layout by remember { mutableStateOf(runtime.homeModules()) }
    var showEditor by remember { mutableStateOf(false) }
    val specs = HomeModules.available(layout, capabilities)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 11.dp
        val cardWidth = (maxWidth - gap) / 2
        // 4 组固定 4 个 LazyListState（数量固定，避免分组增减导致 remember 计数变化）
        val listStates = List(HomeModules.GROUP_ORDER.size) { rememberLazyListState() }

        Column(modifier = Modifier.fillMaxWidth()) {
            HomeModules.GROUP_ORDER.forEachIndexed { gi, group ->
                val groupSpecs = specs.filter { it.group == group }
                if (groupSpecs.isEmpty()) return@forEachIndexed

                // 分组标题（不参与横滑，固定在该组卡片上方）
                Text(
                    text = HomeModules.groupTitle(group),
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
                )

                LazyRow(
                    state = listStates[gi],
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(groupSpecs.size, key = { groupSpecs[it].id }) { index ->
                        HomeModuleCard(
                            spec = groupSpecs[index],
                            status = status,
                            energy = energy,
                            acPlan = acPlan,
                            callbacks = callbacks,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }

                if (groupSpecs.size > 2) {
                    Spacer(Modifier.height(10.dp))
                    ScrollHintBar(state = listStates[gi], itemCount = groupSpecs.size, visibleCount = 2)
                }
                Spacer(Modifier.height(4.dp))
            }

            // ---- 页面最底部的自定义按钮 ----
            CustomizeButton(onClick = { showEditor = true })
        }
    }

    if (showEditor) {
        ModuleEditorDialog(
            initial = layout,
            capabilities = capabilities,
            onSave = { ids ->
                runtime.setHomeModules(ids)
                layout = runtime.homeModules()
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

/** 底部「自定义」胶囊按钮：只显示文字，不放图标（避免视觉噪声）。 */
@Composable
private fun CustomizeButton(onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = C.chipBg,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick),
        ) {
            Text(
                text = "自定义",
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

/** 按 id 分发到具体卡片。 */
@Composable
private fun HomeModuleCard(
    spec: HomeModules.Spec,
    status: CarStatus?,
    energy: MileageEnergySummary?,
    acPlan: ScheduledAcPlan,
    callbacks: ModuleCallbacks,
    modifier: Modifier = Modifier,
) {
    when (spec.id) {
        HomeModules.CLIMATE.id -> AcTile(
            modifier = modifier
                .height(MODULE_CARD_HEIGHT)
                .clip(officialCardShape())
                .clickable(onClick = callbacks.onOpenClimate),
            status = status,
            busy = callbacks.acBusy,
            busyOn = callbacks.acBusy,
            busyOff = callbacks.acBusy,
            onAcOn = callbacks.onAcOn,
            onAcOff = callbacks.onAcOff,
            onTempChange = callbacks.onAcTempChange,
            compact = true,
        )

        HomeModules.SEAT.id -> SeatModuleCard(status, callbacks.onOpenSeat, modifier)
        HomeModules.TIRE.id -> TireModuleCard(status, callbacks.onOpenTire, modifier)
        HomeModules.MILEAGE.id -> MileageModuleCard(status, energy, callbacks.onRefreshEnergy, modifier)
        HomeModules.HEALTH.id -> HealthModuleCard(status, callbacks.onOpenHealth, modifier)
        HomeModules.FRIDGE.id -> FridgeModuleCard(status, callbacks.onOpenFridge, modifier)
        HomeModules.SCHEDULED_AC.id -> ScheduledAcModuleCard(acPlan, callbacks.onOpenScheduledAc, modifier)
        HomeModules.CHARGING.id -> ChargingModuleCard(status, callbacks.onOpenCharging, modifier)
        HomeModules.SENTRY.id -> SentryModuleCard(status, callbacks.onToggleSentry, modifier)
        HomeModules.LOCATION.id -> LocationModuleCard(status, callbacks.onOpenFindCar, modifier)
    }
}

// ============================================================================
// 卡片骨架与各个模块卡片
// ============================================================================

/** 模块卡骨架：标题行（标题 + 右上图标）+ 内容区，尺寸与圆角对齐空调卡。 */
@Composable
private fun ModuleFrame(
    modifier: Modifier = Modifier,
    title: String,
    corner: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(MODULE_CARD_RADIUS)
    Column(
        modifier = modifier
            .height(MODULE_CARD_HEIGHT)
            .clip(shape)
            .background(C.cardBg, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) { corner() }
        }
        content()
    }
}

/** 卡片右上角图标（与空调卡的风扇钮同尺寸，默认主色，高亮时用强调色）。 */
@Composable
private fun ModuleCornerIcon(iconRes: Int, highlight: Boolean = false) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = if (highlight) LocalAppColors.current.accent else LocalAppColors.current.textSecondary,
        modifier = Modifier.size(20.dp),
    )
}

/** 卡片底部的一行说明文字。 */
@Composable
private fun ModuleLine(text: String, highlight: Boolean = false) {
    Text(
        text = text,
        color = if (highlight) LocalAppColors.current.accent else LocalAppColors.current.textSecondary,
        fontSize = 11.sp,
        maxLines = 1,
    )
}

/** 卡片中部的大号数值 + 单位。 */
@Composable
private fun ModuleBigValue(value: String, unit: String) {
    val C = LocalAppColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            color = C.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = unit,
            color = C.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

// ---- 座椅舒适 ----
@Composable
private fun SeatModuleCard(status: CarStatus?, onClick: () -> Unit, modifier: Modifier) {
    ModuleFrame(
        modifier = modifier,
        title = "座椅舒适",
        corner = { ModuleCornerIcon(R.drawable.ic_mod_seat) },
        onClick = onClick,
    ) {
        Spacer(Modifier.height(12.dp))
        val heat = status?.seatHeating
        val vent = status?.seatVentilation
        ModuleLine("主驾 加热${levelText(heat?.driver)} · 通风${levelText(vent?.driver)}")
        Spacer(Modifier.height(4.dp))
        ModuleLine("副驾 加热${levelText(heat?.passenger)} · 通风${levelText(vent?.passenger)}")
        status?.steeringWheelHeating?.let { level ->
            Spacer(Modifier.height(4.dp))
            ModuleLine("方向盘加热 ${levelText(level)}")
        }
    }
}

private fun levelText(level: Int?): String = when (level) {
    null -> "--"
    0 -> "关"
    else -> "${level}档"
}

// ---- 胎压 ----
@Composable
private fun TireModuleCard(status: CarStatus?, onClick: () -> Unit, modifier: Modifier) {
    ModuleFrame(
        modifier = modifier,
        title = "胎压",
        corner = { ModuleCornerIcon(R.drawable.ic_mod_tire) },
        onClick = onClick,
    ) {
        val tires = status?.tires.orEmpty()
        if (tires.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "暂无胎压数据",
                color = LocalAppColors.current.textSecondary,
                fontSize = 13.sp,
            )
            return@ModuleFrame
        }
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TireCell("左前", tires.firstOrNull { it.position == "左前" }, Modifier.weight(1f))
                TireCell("右前", tires.firstOrNull { it.position == "右前" }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TireCell("左后", tires.firstOrNull { it.position == "左后" }, Modifier.weight(1f))
                TireCell("右后", tires.firstOrNull { it.position == "右后" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TireCell(label: String, tire: TireInfo?, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Column(modifier = modifier) {
        Text(label, color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = tire?.pressure ?: "--",
            color = when {
                tire == null -> C.textSecondary
                tire.warning -> Color(0xFFE5484D)
                else -> C.textPrimary
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---- 行驶里程 / 能耗 ----
@Composable
private fun MileageModuleCard(
    status: CarStatus?,
    energy: MileageEnergySummary?,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val totalKm = status?.totalMileage?.toDoubleOrNull()
        ?: energy?.totalMileageKm
    ModuleFrame(
        modifier = modifier,
        title = "里程能耗",
        corner = { ModuleCornerIcon(R.drawable.ic_mod_mileage) },
        onClick = onRefresh,
    ) {
        Spacer(Modifier.height(10.dp))
        ModuleBigValue(value = totalKm?.let { formatKm(it) } ?: "--", unit = "km")
        Spacer(Modifier.height(8.dp))
        ModuleLine(
            text = energy?.overallConsumption?.let { "综合能耗 ${formatNumber(it)} kWh/100km" }
                ?: "综合能耗 --",
        )
        Spacer(Modifier.height(4.dp))
        ModuleLine(
            text = energy?.recent7DaysKm?.let { "近 7 日 ${formatKm(it)} km" } ?: "近 7 日 --",
        )
    }
}

private fun formatKm(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)

// ---- 车辆健康 ----
@Composable
private fun HealthModuleCard(status: CarStatus?, onClick: () -> Unit, modifier: Modifier) {
    val report = remember(status) { VehicleHealthDiagnostics.evaluate(status) }
    ModuleFrame(
        modifier = modifier,
        title = "车辆健康",
        corner = { ModuleCornerIcon(R.drawable.ic_health, highlight = report.level == HealthLevel.GOOD) },
        onClick = onClick,
    ) {
        Spacer(Modifier.height(10.dp))
        val C = LocalAppColors.current
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (status == null) "--" else report.score.toString(),
                color = C.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "分",
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        ModuleLine(report.summary)
        Spacer(Modifier.height(4.dp))
        ModuleLine(
            text = if (report.issues.isEmpty()) "未发现异常" else "${report.issues.size} 项待处理",
            highlight = report.issues.isNotEmpty(),
        )
    }
}

// ---- 车载冰箱 ----
@Composable
private fun FridgeModuleCard(status: CarStatus?, onClick: () -> Unit, modifier: Modifier) {
    val fridge = status?.fridge
    ModuleFrame(
        modifier = modifier,
        title = "车载冰箱",
        corner = { ModuleCornerIcon(R.drawable.ic_mod_fridge, highlight = fridge?.enabled == true) },
        onClick = onClick,
    ) {
        Spacer(Modifier.height(12.dp))
        val state = when (fridge?.enabled) {
            true -> "工作中"
            false -> "已关闭"
            null -> "状态未知"
        }
        ModuleBigValue(value = fridge?.temp?.let { "$it" } ?: "--", unit = "°C")
        Spacer(Modifier.height(8.dp))
        ModuleLine(state, highlight = fridge?.enabled == true)
    }
}

// ---- 定时空调 ----
@Composable
private fun ScheduledAcModuleCard(plan: ScheduledAcPlan, onClick: () -> Unit, modifier: Modifier) {
    ModuleFrame(
        modifier = modifier,
        title = "定时空调",
        corner = { ModuleCornerIcon(R.drawable.ic_phosphor_fan, highlight = plan.enabled) },
        onClick = onClick,
    ) {
        Spacer(Modifier.height(12.dp))
        ModuleBigValue(value = if (plan.enabled) plan.startTime else "--", unit = "")
        Spacer(Modifier.height(8.dp))
        ModuleLine(
            text = if (plan.enabled) "每天 ${plan.temperatureC}°C" else "未设置定时",
            highlight = plan.enabled,
        )
    }
}

// ---- 充电 ----
@Composable
private fun ChargingModuleCard(status: CarStatus?, onClick: () -> Unit, modifier: Modifier) {
    val charging = status?.charging == true
    ModuleFrame(
        modifier = modifier,
        title = "充电",
        corner = { ModuleCornerIcon(R.drawable.ic_charge_station, highlight = charging) },
        onClick = onClick,
    ) {
        Spacer(Modifier.height(12.dp))
        ModuleBigValue(
            value = if (charging) status?.chargingPower?.filter { it.isDigit() || it == '.' } ?: "--" else "--",
            unit = if (charging) "kW" else "",
        )
        Spacer(Modifier.height(8.dp))
        ModuleLine(
            text = when {
                charging -> status?.chargeTimeLeft?.let { "剩余 $it" } ?: "充电中"
                status?.chargeCableConnected == true -> "已插枪，未充电"
                else -> "未充电"
            },
            highlight = charging,
        )
    }
}

// ---- 哨兵模式 ----
@Composable
private fun SentryModuleCard(status: CarStatus?, onToggle: () -> Unit, modifier: Modifier) {
    val on = status?.sentryOn == true
    ModuleFrame(
        modifier = modifier,
        title = "哨兵模式",
        corner = { ModuleCornerIcon(R.drawable.ic_qc_sentry, highlight = on) },
        onClick = onToggle,
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = when (status?.sentryOn) {
                true -> "已开启"
                false -> "已关闭"
                null -> "--"
            },
            color = LocalAppColors.current.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        ModuleLine(if (on) "点击关闭哨兵" else "点击开启哨兵", highlight = on)
    }
}

// ---- 车辆位置 ----
@Composable
private fun LocationModuleCard(status: CarStatus?, onClick: () -> Unit, modifier: Modifier) {
    val hasFix = status?.latitude != null && status?.longitude != null
    ModuleFrame(
        modifier = modifier,
        title = "车辆位置",
        corner = { ModuleCornerIcon(R.drawable.ic_loc_pin, highlight = hasFix) },
        onClick = onClick,
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = "--",
            color = LocalAppColors.current.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        ModuleLine(status?.drivingStatus ?: "位置未知")
    }
}

// ============================================================================
// 自定义模块：顺序（上移 / 下移）、删除、添加
// ============================================================================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModuleEditorDialog(
    initial: List<String>,
    capabilities: VehicleCapabilities,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    // 已有的（本车可用的）排在前面，未添加的放进下方候选区
    val items = remember {
        mutableStateListOf<String>().apply {
            addAll(HomeModules.available(initial, capabilities).map { it.id })
        }
    }
    val candidates = HomeModules.available(HomeModules.ALL.map { it.id }, capabilities)
        .filter { it.id !in items }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.cardBg,
        title = { Text("自定义功能模块", color = C.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "拖动顺序会同步到首页横滑区；最少保留 1 个模块。",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { index, id ->
                    val spec = HomeModules.find(id) ?: return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(spec.iconRes),
                            contentDescription = null,
                            tint = C.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            spec.title,
                            color = C.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    items.removeAt(index)
                                    items.add(index - 1, id)
                                }
                            },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                null,
                                tint = if (index > 0) C.textPrimary else C.barTrack,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < items.size - 1) {
                                    items.removeAt(index)
                                    items.add(index + 1, id)
                                }
                            },
                            enabled = index < items.size - 1,
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                null,
                                tint = if (index < items.size - 1) C.textPrimary else C.barTrack,
                            )
                        }
                        IconButton(
                            onClick = { if (items.size > 1) items.removeAt(index) },
                            enabled = items.size > 1,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = if (items.size > 1) Color(0xFFFF6B6B) else C.barTrack,
                            )
                        }
                    }
                }
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("添加模块", color = C.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        candidates.forEach { spec ->
                            AssistChip(
                                onClick = { items.add(spec.id) },
                                label = { Text(spec.title, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(spec.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = C.textPrimary,
                                    leadingIconContentColor = C.textPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 未添加进列表的模块也保留在配置里（顺序在前，显示时按可用性过滤）
                val rest = HomeModules.sanitize(initial).filter { it !in items }
                onSave(items.toList() + rest)
            }) { Text("保存", color = C.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = C.textSecondary) }
        },
    )
}
