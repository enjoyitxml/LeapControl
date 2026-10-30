package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.leapcontrol.app.EnergyComposition
import com.leapcontrol.app.EnergyFormat
import com.leapcontrol.app.EnergySlice
import com.leapcontrol.app.WeeklyEnergyRank
import com.leapcontrol.app.ui.theme.statusGood
import com.leapcontrol.app.ui.theme.statusGoodDark

/**
 * 「周能耗分布」卡：上周总能耗环形图 + 行车 / 空调 / 其他三项占比。
 *
 * 数据来自 `getLastweekEC` 的 `driverEC` / `acEC` / `otherEC`（单位 kWh），
 * 总量是三项之和（官方 App 也没有独立的总量字段）。占比由 [EnergyFormat.percentInts]
 * 用最大余数法算，保证三项加起来正好 100%。
 *
 * 三色固定（绿 / 蓝 / 橙），不跟季节主题走：这三块要「一眼分得开」，
 * 跟着主题变（比如冬天全变雾蓝）就分不出来了。
 */
@Composable
fun WeeklyEnergyCompositionCard(
    composition: EnergyComposition?,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val slices = composition?.slices.orEmpty()
    val total = composition?.totalKwh ?: 0.0
    val percents = EnergyFormat.percentInts(slices.map { it.kwh })

    Column(
        modifier = modifier
            .height(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "周能耗分布",
            color = C.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(8.dp))

        if (total <= 0.0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无上周能耗数据", color = C.textSecondary, fontSize = 12.sp)
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 环形图：三项按占比铺满一圈，圆心写总量
            // 直径固定 96dp 而不是按剩余高度撑满 —— 撑满会占到卡片宽度的 40%，
            // 右侧三行文字就被挤成一行两三个字。
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                val track = C.border.copy(alpha = 0.55f)
                val colors = slices.map { sliceColor(it) }
                Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    val stroke = size.minDimension * 0.115f
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    var start = -90f
                    slices.forEachIndexed { index, slice ->
                        val sweep = (slice.kwh.coerceAtLeast(0.0) / total * 360.0).toFloat()
                        // 项与项之间留 2° 缝，色块边界才清楚（单块占比极小时不画缝，否则会消失）
                        val gap = if (sweep > 6f) 2f else 0f
                        if (sweep > 0f) {
                            drawArc(
                                color = colors[index],
                                startAngle = start + gap / 2f,
                                sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = EnergyFormat.kwh(total),
                        color = C.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "总能耗 kWh",
                        color = C.textSecondary,
                        fontSize = 8.5.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                slices.forEachIndexed { index, slice ->
                    CompositionRow(
                        slice = slice,
                        color = sliceColor(slice),
                        percent = percents.getOrElse(index) { 0 },
                        fraction = (slice.kwh.coerceAtLeast(0.0) / total).toFloat(),
                    )
                }
            }
        }
    }
}

/** 单项：色点 + 名称 + 数值 + 占比徽标，下面一条按占比填充的细条。 */
@Composable
private fun CompositionRow(
    slice: EnergySlice,
    color: Color,
    percent: Int,
    fraction: Float,
) {
    val C = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = slice.label,
                color = C.textPrimary,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${EnergyFormat.kwh(slice.kwh)} kWh",
                color = C.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$percent%",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(C.border.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** 三项固定配色；服务端若返回未知分类，用正文色兜底（不猜颜色）。 */
private fun sliceColor(slice: EnergySlice): Color = when (slice.label) {
    "行车" -> Color(0xFF22C55E)
    "空调" -> Color(0xFF3B82F6)
    "其他" -> Color(0xFFF59E0B)
    else -> Color(0xFF8A8F98)
}

/**
 * 「近 6 周百公里能耗」卡：综合电耗大字 + 6 根周柱 + 「超越全国 xx% 的车型车主」。
 *
 * 柱高按 6 周峰值归一化并只占柱区 76%，上方留给数值标注 —— 和「近 7 天行程」卡同一套做法。
 * 最后一根（最近一周）用主题色，其余用主题色的低透明度渐变，换季节主题时整组跟着变。
 */
@Composable
fun WeeklyEnergyRankCard(
    rank: WeeklyEnergyRank?,
    carModel: String,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val good = if (C.isDark) statusGoodDark else statusGood
    val weeks = rank?.weeks.orEmpty()
    val maxEc = weeks.maxOfOrNull { it.hundredKmEc } ?: 0.0
    val model = EnergyFormat.carModelName(carModel)

    Column(
        modifier = modifier
            .height(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "近 6 周百公里能耗",
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (rank?.rankPercent != null) {
                Text(
                    text = buildAnnotatedString {
                        append("超越全国")
                        withStyle(SpanStyle(color = good, fontWeight = FontWeight.Bold)) {
                            append(rank.rankPercent)
                        }
                        append("的${model}车主")
                    },
                    color = C.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(3.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = EnergyFormat.kwh(rank?.averageEc),
                color = if (rank?.averageEc == null) C.textSecondary else good,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "kWh/100km",
                color = C.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        if (weeks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无能效数据", color = C.textSecondary, fontSize = 12.sp)
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weeks.forEachIndexed { index, point ->
                val isLatest = index == weeks.lastIndex
                // 76%：柱区上方要给数值标注留位置，否则最高那根会把数字顶出卡片
                val fraction = if (maxEc <= 0.0) 0.06f else {
                    maxOf(0.06f, (point.hundredKmEc / maxEc).toFloat() * 0.76f)
                }
                val color = if (isLatest) C.accent else C.accent.copy(alpha = 0.55f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = EnergyFormat.kwh(point.hundredKmEc),
                        color = if (isLatest) C.accent else C.textSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(C.border.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.verticalGradient(
                                        if (isLatest) {
                                            listOf(C.accent, C.accent.copy(alpha = 0.60f))
                                        } else {
                                            listOf(color, color.copy(alpha = 0.30f))
                                        },
                                    ),
                                ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 标签取这一周的周日（= 这根柱覆盖到哪一天），不是周一；
                        // 否则 10/30 打开首页最后一根写着 10/19，看着像半个月没更新。
                        text = EnergyFormat.weekTick(point.weekStart, point.weekEnd),
                        color = if (isLatest) C.accent else C.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = if (isLatest) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
