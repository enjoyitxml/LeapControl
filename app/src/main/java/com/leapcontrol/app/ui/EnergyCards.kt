package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.leapcontrol.app.EnergyComposition
import com.leapcontrol.app.EnergyFormat
import com.leapcontrol.app.EnergySlice
import com.leapcontrol.app.WeeklyEnergyRank
import com.leapcontrol.app.WeeklyEnergyPoint
import com.leapcontrol.app.ui.theme.statusGood
import com.leapcontrol.app.ui.theme.statusGoodDark
import kotlin.math.abs

/**
 * 「周能耗分布」卡：上周总能耗 + **一条三色比例带** + 三项明细。
 *
 * 数据来自 `getLastweekEC` 的 `driverEC` / `acEC` / `otherEC`（单位 kWh），
 * 总量是三项之和（官方 App 也没有独立的总量字段）。占比由 [EnergyFormat.percentInts]
 * 用最大余数法算，保证三项加起来正好 100%。
 *
 * ## 为什么从「环形 + 三条进度条」改成一条比例带
 * 旧版把同一件事编码了三次：环形画一遍、每项一条进度条再画一遍、右边还挂个百分比徽标。
 * 真实数据是 92% / 1% / 7% 这种极端分布，「空调 1%」那条进度条几乎是空的，
 * 看上去像坏了；环形也只剩一个绿圈，信息量很低。
 * 现在：
 *  - 一条横向比例带，三段首尾相接、各占其宽 —— 细项自然就细，不需要额外解释；
 *  - 带下三列并排：名字 / 占比（大字，用项色）/ 度数 —— 每个数字只出现一次。
 * 整卡从 176dp 压到约 150dp，而「总量」和「几乎全在行车」反而更醒目。
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
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "周能耗分布",
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "上周 ${EnergyFormat.lastWeekRange()}",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (total <= 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无上周能耗数据", color = C.textSecondary, fontSize = 12.sp)
            }
            return@Column
        }

        // ---- 总量：一个数字先说清「上周用了几度电」 ----
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = EnergyFormat.kwh(total),
                color = C.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "kWh",
                color = C.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "总能耗",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        CompositionBar(
            slices = slices,
            total = total,
            track = C.border.copy(alpha = 0.45f),
        )

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            slices.forEachIndexed { index, slice ->
                CompositionLegend(
                    slice = slice,
                    color = sliceColor(slice),
                    percent = percents.getOrElse(index) { 0 },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 比例带高度 / 段间缝 / 单段最小宽度。 */
private val BAR_HEIGHT = 10.dp
private val SEGMENT_GAP = 3.dp
private val MIN_SEGMENT = 4.dp

/**
 * 三色比例带。
 *
 * 用 Canvas 而不是 `Row` + `weight`：1% 的项交给 weight 会被压成 0.3dp 而彻底消失。
 * 这里给每段一个最小宽度（[MIN_SEGMENT]），最后一段吃掉剩余宽度，
 * 保证总长严丝合缝、段与段之间恒有 [SEGMENT_GAP] 的缝。
 *
 * 每段各自圆角（半径不超过自身半宽），细项就渲染成一个小圆点 —— 这是它应得的样子，
 * 而不是一条看起来坏掉的空槽。
 */
@Composable
private fun CompositionBar(
    slices: List<EnergySlice>,
    total: Double,
    track: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT),
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = track,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(radius),
        )
        if (slices.isEmpty() || total <= 0.0) return@Canvas

        val gap = SEGMENT_GAP.toPx()
        val minWidth = MIN_SEGMENT.toPx()
        val available = (size.width - gap * (slices.size - 1)).coerceAtLeast(0f)
        var x = 0f
        slices.forEachIndexed { index, slice ->
            val raw = (slice.kwh.coerceAtLeast(0.0) / total * available).toFloat()
            // 最后一段补到右边界：前面若因最小宽度多占了几个像素，这里吸收掉
            val width = if (index == slices.lastIndex) {
                (size.width - x).coerceAtLeast(0f)
            } else {
                raw.coerceAtLeast(minWidth)
            }
            if (width > 0f) {
                drawRoundRect(
                    color = sliceColor(slice),
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(minOf(radius, width / 2f)),
                )
            }
            x += width + gap
        }
    }
}

/** 明细一列：色点 + 名字，下面「占比大字（项色）+ 度数小字」。 */
@Composable
private fun CompositionLegend(
    slice: EnergySlice,
    color: Color,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = slice.label,
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$percent%",
                color = color,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${EnergyFormat.kwh(slice.kwh)} kWh",
                color = C.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
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
 * 「近 6 周百公里能耗」卡：综合电耗大字 + 6 周折线 + 「超越全国 xx% 的车型车主」。
 *
 * 图形部分是折线（见 [EnergyLineChart]）：柱状在这个尺寸下读不出高低差，
 * 用户反馈「数据不明显、糊在一起」，改折线后趋势与数值都能读。
 * 绘制统一走主题强调色，最近一周加深并放大末点，换季节主题时整组跟着变。
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

        EnergyLineChart(
            weeks = weeks,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/**
 * 近 6 周能耗折线图。
 *
 * **为什么换掉柱状**：6 根柱子在 176dp 的卡里每根只有 14dp 宽，
 * 满高的灰色轨道 + `alpha 1.0→0.3` 的竖向渐变让柱底和轨道糊成一片，
 * 而 11.2 与 15.7 按 `value/max × 0.76` 算出来只差 14dp —— 六根看起来一样高
 * （用户反馈「数据非常不明显、糊在一起」）。折线把「涨还是跌」直接画出来，
 * 再把 6 个数值贴在点上，趋势与绝对值都不丢。
 *
 * 绘制风格与零记的 `JournalLineChart` 一致（2dp 圆头线、面积淡渐变、末点放大 + 白芯），
 * 没有直接复用是因为这张图**每个点都必须带数值**、绘制区高度要按标注反推，
 * 与那张「只有横轴标签」的图需求不同，强行合并会把通用组件变成万能组件。
 *
 * 纵轴按 6 周区间自适应（外扩 18%），不是从 0 起 —— 这是折线图让趋势显形的常规做法，
 * 点的绝对值始终标在图上，不会读错量级。
 */
@Composable
private fun EnergyLineChart(
    weeks: List<WeeklyEnergyPoint>,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val values = weeks.map { it.hundredKmEc }

    Canvas(modifier = modifier) {
        val n = weeks.size
        if (n == 0) return@Canvas

        val lo = values.min()
        val hi = values.max()
        // 全周数值相同（span = 0）时压成一条水平线，不能除以 0
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val low = lo - span * 0.18
        val high = hi + span * 0.18

        val valueH = 12.dp.toPx()   // 点上数值占的高度
        val tickH = 11.dp.toPx()    // 横轴周标签占的高度
        val gap = 4.dp.toPx()
        val plotTop = valueH + gap
        val labelTop = size.height - tickH
        val plotBottom = labelTop - gap
        val plotH = plotBottom - plotTop
        if (plotH <= 0f) return@Canvas

        // 首尾各留 10dp，否则第一个点的数值会被卡片左边缘切掉
        val inset = 10.dp.toPx()
        val stepX = if (n > 1) (size.width - inset * 2) / (n - 1) else 0f
        val points = values.mapIndexed { i, v ->
            val f = ((v - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
            Offset(inset + stepX * i, plotBottom - plotH * f)
        }

        // 面积：折线下方一层很淡的渐变，给「这一周整体多高」一个体量感
        if (n > 1) {
            val area = Path().apply {
                moveTo(points[0].x, plotBottom)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, plotBottom)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(C.accent.copy(alpha = 0.20f), C.accent.copy(alpha = 0f)),
                    startY = plotTop,
                    endY = plotBottom,
                ),
            )
            val line = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = line,
                color = C.accent,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // 采样点：普通点小一圈；最后一根（当周）放大 + 白芯，作为「你现在在这儿」
        points.forEachIndexed { i, p ->
            val isLast = i == n - 1
            drawCircle(C.accent, radius = if (isLast) 4.dp.toPx() else 2.2.dp.toPx(), center = p)
            if (isLast) drawCircle(Color.White, radius = 1.6.dp.toPx(), center = p)
        }

        // 数值标注：默认贴点上方；相邻两点高度太接近（13.7 / 13.6 这种）就翻到下方，
        // 否则两团数字会叠在一起 —— 这是折线图在「值都挤在均值附近」时最容易踩的坑
        var lastY: Float? = null
        points.forEachIndexed { i, p ->
            val isLast = i == n - 1
            val layout = measurer.measure(
                AnnotatedString(EnergyFormat.kwh(values[i])),
                style = TextStyle(
                    color = if (isLast) C.accent else C.textSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                ),
            )
            val crowded = lastY != null && abs(p.y - lastY!!) < 11.dp.toPx()
            val x = (p.x - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            val y = if (crowded) p.y + gap else p.y - gap - layout.size.height
            drawText(layout, topLeft = Offset(x, y.coerceIn(0f, labelTop - layout.size.height)))
            lastY = p.y
        }

        // 横轴：这一周覆盖到的日期（周日）；**最近一周写「上周」**，不写具体日期；
        // 最后一根用主题色
        weeks.forEachIndexed { i, week ->
            val isLast = i == n - 1
            val layout = measurer.measure(
                AnnotatedString(
                    EnergyFormat.weekAxisLabel(week.weekStart, week.weekEnd, isLast),
                ),
                style = TextStyle(
                    color = if (isLast) C.accent else C.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
            val x = (points[i].x - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, labelTop))
        }
    }
}
