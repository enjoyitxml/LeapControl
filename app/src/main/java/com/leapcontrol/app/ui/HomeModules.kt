package com.leapcontrol.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.MileageEnergyParser
import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleCapabilities

/**
 * 首页功能模块区：照搬开源参考项目 leapauto 的下半部分布局（保留零控自身的视频背景与
 * AppColors 深浅主题）。结构为三行：
 *   ① 胎压俯视卡 + 车况状态 2×2 卡（总里程 / 电流 / 门锁 / 车窗）—— 并排一行；
 *   ② 空调条（+ 车载冰箱条，本车有冰箱时 50:50 并排）—— 一行；
 *   ③ 近 7 天行驶里程图表卡 —— 整行。
 * 原先「统一大卡片纵列 + 自定义顺序」的方案作废（与官方首页 1:1 卡片式不一致，且原项目
 * 下半部分本就是这套结构化布局）。
 *
 * 注：座椅舒适 / 车辆健康 / 哨兵 / 定时空调 / 充电 这些模块在原项目首页下半部分没有对应入口
 * （分别位于空调详情、健康诊断弹窗、控制中心磁贴等），故此处按原项目结构不重复放置。
 */
object HomeModules {
    // 仅保留给 AppRuntime.homeModules() / setHomeModules() 兼容（顺序偏好现由下方固定布局取代）。
    private val KNOWN = setOf(
        "climate", "mileage", "tire", "seat", "health",
        "charging", "scheduledAc", "fridge", "sentry",
    )
    val DEFAULT_ORDER = listOf(
        "climate", "mileage", "tire", "seat", "health",
        "charging", "scheduledAc", "fridge", "sentry",
    )

    fun sanitize(ids: List<String>): List<String> =
        ids.filter { it in KNOWN }.distinct()
}

/** 模块区需要的交互回调（由 HomeScreen 传入）。 */
data class ModuleCallbacks(
    val onOpenClimate: () -> Unit = {},
    val onOpenFridge: () -> Unit = {},
    val onOpenTire: () -> Unit = {},
    val onAcOn: () -> Unit = {},
    val onAcOff: () -> Unit = {},
    val onToggleFridge: (Boolean) -> Unit = {},
    val acBusy: Boolean = false,
    val onRefreshEnergy: () -> Unit = {},
)

/**
 * 首页最下方的功能模块区（快捷控车圆钮下方）。照搬原项目三行布局。
 */
@Composable
fun HomeModuleSection(
    status: CarStatus?,
    energy: MileageEnergySummary?,
    capabilities: VehicleCapabilities,
    callbacks: ModuleCallbacks,
) {
    val hasFridge = status?.fridge != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ① 胎压俯视卡 + 车况状态 2×2 卡
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TireStatusCard(
                status = status,
                onOpen = callbacks.onOpenTire,
                modifier = Modifier.weight(1f).height(176.dp),
            )
            VehicleStatusGridCard(
                status = status,
                modifier = Modifier.weight(1f).height(176.dp),
            )
        }

        // ② 空调条（+ 冰箱条）
        if (hasFridge) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClimateBar(
                    status = status,
                    callbacks = callbacks,
                    modifier = Modifier.weight(1f),
                )
                FridgeBar(
                    status = status,
                    callbacks = callbacks,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ClimateBar(
                status = status,
                callbacks = callbacks,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ③ 近 7 天行驶里程图表卡
        RecentMileageCard(
            energy = energy,
            onRefresh = callbacks.onRefreshEnergy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ============================================================================
// 通用卡片骨架
// ============================================================================

/** 模块区统一卡片底：C.cardBg + 16dp 圆角（与首页其他卡片一致）。 */
@Composable
private fun LowerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .background(C.cardBg, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content,
    )
}

/** 卡片右上角的异常小圆点 + 文案。 */
@Composable
private fun WarningDot(text: String) {
    val C = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.5.dp)
                .background(Color(0xFFE5484D), CircleShape),
        )
        Text(
            text = text,
            color = Color(0xFFE5484D),
            fontSize = 10.sp,
        )
    }
}

// ============================================================================
// ① 胎压俯视卡
// ============================================================================

@Composable
private fun TireStatusCard(
    status: CarStatus?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val tires = status?.tires.orEmpty()
    val hasWarning = tires.any { it.warning }
    Box(modifier = modifier) {
        LowerCard(modifier = Modifier.fillMaxSize()) {
            if (status == null || tires.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无胎压数据", color = C.textSecondary, fontSize = 13.sp)
                }
                return@LowerCard
            }
            if (hasWarning) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    WarningDot("异常")
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    TireMiniValue(tires.firstOrNull { it.position == "左前" }, alignEnd = true)
                    TireMiniValue(tires.firstOrNull { it.position == "左后" }, alignEnd = true)
                }
                Spacer(Modifier.width(6.dp))
                TireTopView(
                    tires = tires,
                    modifier = Modifier
                        .size(width = 92.dp, height = 150.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpen),
                )
                Spacer(Modifier.width(6.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    TireMiniValue(tires.firstOrNull { it.position == "右前" }, alignEnd = false)
                    TireMiniValue(tires.firstOrNull { it.position == "右后" }, alignEnd = false)
                }
            }
        }
    }
}

@Composable
private fun TireMiniValue(
    tire: TireInfo?,
    alignEnd: Boolean,
) {
    val C = LocalAppColors.current
    val warning = tire?.warning == true
    val num = tire?.pressure
        ?.replace(Regex("[^\\d.]"), "")
        ?.takeIf { it.isNotBlank() } ?: "--"
    val valueColor = if (warning) Color(0xFFE5484D) else C.textPrimary
    val unitColor = if (warning) Color(0xFFE5484D) else C.textSecondary
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = num,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            lineHeight = 16.sp,
            maxLines = 1,
        )
        Text(
            text = "kPa",
            color = unitColor,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            maxLines = 1,
        )
    }
}

// ============================================================================
// ① 车况状态 2×2 卡（总里程 / 电流 / 门锁 / 车窗）
// ============================================================================

@Composable
private fun VehicleStatusGridCard(
    status: CarStatus?,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    LowerCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusCell(
                    label = "总里程",
                    value = status?.totalMileage?.formatMileage() ?: "--",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PowerInfoCell(
                    status = status,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lockWarning = status?.locked == false
                val lockValue = when (status?.locked) {
                    true -> "车锁已锁"
                    false -> "车锁未锁"
                    null -> "--"
                }
                StatusCell(
                    label = "门锁",
                    value = lockValue,
                    warning = lockWarning,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                val windowWarning = status?.windowOpen == true
                val windowValue = when (status?.windowOpen) {
                    true -> "车窗未关"
                    false -> "车窗已关"
                    null -> "--"
                }
                StatusCell(
                    label = "车窗",
                    value = windowValue,
                    warning = windowWarning,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

private fun String.formatMileage(): String {
    val raw = this.trim()
    return when {
        raw.isEmpty() || raw == "--" -> "--"
        raw.endsWith("km", ignoreCase = true) -> raw.replace(" ", "")
        else -> "${raw}km"
    }
}

/** 2×2 单元格：标签 + 数值。 */
@Composable
private fun StatusCell(
    label: String,
    value: String,
    warning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val valueColor = when {
        warning -> Color(0xFFE5484D)
        value == "--" -> C.textSecondary
        else -> C.textPrimary
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = label, color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * 功率信息单元格：点击在 充电功率 / 剩余时间 / 电压 / 电流 / 电池温度 之间循环，
 * 底部小圆点指示当前页（照搬原项目 power pager 思路，默认停在「电流」）。
 */
@Composable
private fun PowerInfoCell(
    status: CarStatus?,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val pages = listOf(
        "充电功率" to (status?.chargingPower ?: "--"),
        "剩余时间" to (status?.chargeTimeLeft ?: "--"),
        "电压" to (status?.chargeVoltage ?: "--"),
        "电流" to (status?.chargeCurrent ?: "--"),
        "电池温度" to (status?.batteryTemp ?: "--"),
    )
    var page by remember { mutableStateOf(3) } // 默认「电流」
    Column(
        modifier = modifier
            .clickable { page = (page + 1) % pages.size },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = pages[page].first, color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = pages[page].second,
            color = if (pages[page].second == "--") C.textSecondary else C.textPrimary,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(pages.size) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == page) 6.dp else 2.5.dp, 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i == page) C.accent else C.barTrack),
                )
            }
        }
    }
}

// ============================================================================
// ② 空调条 / 冰箱条（照搬原项目 ClimateOverviewCard 紧凑双子舱样式）
// ============================================================================

@Composable
private fun ClimateBar(
    status: CarStatus?,
    callbacks: ModuleCallbacks,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val target = status?.acTargetTemp?.toIntOrNull() ?: 24
    val acOn = status?.acOn == true
    val interior = status?.interiorTemp?.replace("°C", "", ignoreCase = true)
        ?.replace("°", "", ignoreCase = true)?.trim()
    val sub = if (acOn) {
        if (interior.isNullOrBlank()) "运行中" else "车内 $interior°C"
    } else "已关闭"
    LowerCard(
        modifier = modifier.height(58.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = callbacks.onOpenClimate),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (acOn) C.accent else C.textSecondary
                Icon(
                    painter = painterResource(R.drawable.ic_ac_fan),
                    contentDescription = "空调状态",
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        if (acOn) "空调 · ${target}°C" else "空调",
                        color = if (acOn) C.accent else C.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = sub,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            PillSwitch(
                checked = acOn,
                enabled = !callbacks.acBusy,
                onCheckedChange = { if (it) callbacks.onAcOn() else callbacks.onAcOff() },
            )
        }
    }
}

@Composable
private fun FridgeBar(
    status: CarStatus?,
    callbacks: ModuleCallbacks,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val fridge = status?.fridge
    val enabled = fridge?.enabled == true
    val temp = fridge?.temp
    val sub = if (enabled) "${temp ?: "--"}°C" else "已关闭"
    LowerCard(
        modifier = modifier.height(58.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = callbacks.onOpenFridge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (enabled) C.accent else C.textSecondary
                Icon(
                    painter = painterResource(R.drawable.ic_mod_fridge),
                    contentDescription = "车载冰箱",
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        "车载冰箱",
                        color = if (enabled) C.accent else C.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = sub,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            PillSwitch(
                checked = enabled,
                onCheckedChange = { callbacks.onToggleFridge(it) },
            )
        }
    }
}

/** iOS 风格胶囊开关：开启时滑块居右、轨道着主题色。 */
@Composable
private fun PillSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "pillThumb",
    )
    Box(
        modifier = Modifier
            .size(44.dp, 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) C.accent else C.barTrack)
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(20.dp)
                .background(Color.White, CircleShape),
        )
    }
}

// ============================================================================
// ③ 近 7 天行驶里程图表卡
// ============================================================================

@Composable
private fun RecentMileageCard(
    energy: MileageEnergySummary?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    LowerCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("近7天行驶里程", color = C.textPrimary, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Icon(
                painter = painterResource(R.drawable.ic_mod_mileage),
                contentDescription = null,
                tint = C.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onRefresh),
            )
        }
        Spacer(Modifier.height(4.dp))
        val total = energy?.recent7DaysKm
        Text(
            text = total?.let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format("%.1f", it) } ?: "--",
            color = C.accent,
            fontSize = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text("km", color = C.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        SevenDayChart(
            points = energy?.recentDays.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SevenDayChart(
    points: List<com.leapcontrol.app.DailyMileage>,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无近 7 天里程数据", color = C.textSecondary, fontSize = 13.sp)
        }
        return
    }
    // 最大值与日期标签只跟这份数据有关，别在每次重组时重新扫一遍、重新解析日期
    val max = remember(points) {
        (points.maxOfOrNull { it.km } ?: 0.0).coerceAtLeast(1.0)
    }
    val labels = remember(points) {
        points.map { MileageEnergyParser.dayLabel(it.day) }
    }
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .padding(top = 10.dp, bottom = 2.dp),
        ) {
            val w = size.width
            val h = size.height
            val n = points.size
            val stepX = w / (n - 1).coerceAtLeast(1)
            val chartH = h - 18.dp.toPx()
            val baseY = h - 14.dp.toPx()
            val pts = points.mapIndexed { i, p ->
                val x = if (n == 1) w / 2 else i * stepX
                val y = baseY - (p.km / max).toFloat().coerceIn(0f, 1f) * chartH
                Offset(x, y)
            }
            // 面积填充
            val areaPath = Path().apply {
                moveTo(pts.first().x, baseY)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, baseY)
                close()
            }
            drawPath(
                areaPath,
                Brush.verticalGradient(
                    listOf(
                        C.accent.copy(alpha = 0.35f),
                        C.accent.copy(alpha = 0.02f),
                    ),
                ),
            )
            // 折线
            val linePath = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.forEach { lineTo(it.x, it.y) }
            }
            drawPath(linePath, C.accent, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            // 数据点（末点放大强调）
            pts.forEachIndexed { i, p ->
                val last = i == pts.lastIndex
                drawCircle(C.accent, radius = if (last) 3.5.dp.toPx() else 2.5.dp.toPx())
            }
        }
        // 日期标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = C.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}
