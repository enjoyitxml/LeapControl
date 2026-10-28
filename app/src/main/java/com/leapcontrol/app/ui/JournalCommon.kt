package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.Medal
import com.leapcontrol.app.journal.MedalGlyph
import kotlin.math.cos
import kotlin.math.sin

/**
 * 零记的共用视觉件。
 *
 * 全部自绘：进度环、勋章、小柱状图、分类圆标。不引第三方图表库 ——
 * 这里需要的东西都很小，引一个库反而要跟着它的主题色、字号走。
 */

/** 环形进度。[fraction] 0~1，超出的部分夹住；中间留给人放数字。 */
@Composable
fun JournalRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 11.dp,
    color: Color = LocalAppColors.current.accent,
    track: Color = LocalAppColors.current.barTrack,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = size.minDimension
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(w - strokePx, w - strokePx)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** 线形进度条（分类占比、目标进度的小尺寸版本）。 */
@Composable
fun JournalProgressLine(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = LocalAppColors.current.accent,
    track: Color = LocalAppColors.current.barTrack,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * 勋章圆牌。解锁 = 主题色 + 高亮描边；未解锁 = 灰、低透明度。
 *
 * [size] 是直径；glyph 的线条粗细跟着 size 走，缩到 26dp 也不会糊成一团。
 */
@Composable
fun MedalBadge(
    medal: Medal,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val C = LocalAppColors.current
    val tint = if (unlocked) C.accent else C.textSecondary
    val fill = if (unlocked) C.accent.copy(alpha = 0.14f) else C.textSecondary.copy(alpha = 0.10f)
    val ring = if (unlocked) C.accent.copy(alpha = 0.55f) else C.textSecondary.copy(alpha = 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.54f)) {
            drawGlyph(medal.glyph, tint.copy(alpha = if (unlocked) 1f else 0.75f))
        }
    }
}

/** 在画布里画一个 24×24 归一坐标的 glyph（实际按画布尺寸缩放）。 */
fun DrawScope.drawGlyph(glyph: MedalGlyph, color: Color) {
    val s = size.minDimension / 24f
    fun px(v: Float) = v * s
    val stroke = Stroke(width = px(1.9f), cap = StrokeCap.Round)
    when (glyph) {
        MedalGlyph.SPROUT -> {
            drawLine(color, Offset(px(12f), px(20f)), Offset(px(12f), px(11f)), strokeWidth = px(1.9f), cap = StrokeCap.Round)
            val left = Path().apply {
                moveTo(px(12f), px(14.5f))
                quadraticBezierTo(px(5.4f), px(14.6f), px(5.2f), px(8.6f))
                quadraticBezierTo(px(11.6f), px(8.8f), px(12f), px(14.5f))
                close()
            }
            val right = Path().apply {
                moveTo(px(12f), px(12f))
                quadraticBezierTo(px(18.4f), px(12.1f), px(18.8f), px(6.2f))
                quadraticBezierTo(px(12.4f), px(6.3f), px(12f), px(12f))
                close()
            }
            drawPath(left, color)
            drawPath(right, color)
        }

        MedalGlyph.FLAG -> {
            drawLine(color, Offset(px(7f), px(4f)), Offset(px(7f), px(21f)), strokeWidth = px(1.9f), cap = StrokeCap.Round)
            val flag = Path().apply {
                moveTo(px(8f), px(5f))
                lineTo(px(19f), px(8f))
                lineTo(px(8f), px(12f))
                close()
            }
            drawPath(flag, color)
        }

        MedalGlyph.ROAD -> {
            val road = Path().apply {
                moveTo(px(8f), px(21f))
                lineTo(px(10.6f), px(3.5f))
                moveTo(px(16f), px(21f))
                lineTo(px(13.4f), px(3.5f))
            }
            drawPath(road, color, style = Stroke(width = px(1.7f), cap = StrokeCap.Round))
            listOf(6.5f, 12f, 17.5f).forEach { y ->
                drawLine(
                    color.copy(alpha = 0.8f),
                    Offset(px(12f), px(y)),
                    Offset(px(12f), px(y + 2f)),
                    strokeWidth = px(1.6f),
                    cap = StrokeCap.Round,
                )
            }
        }

        MedalGlyph.SUN -> {
            drawCircle(color, radius = px(5.2f), center = Offset(px(12f), px(12f)), style = Stroke(width = px(1.9f)))
            for (i in 0 until 8) {
                val ang = (Math.PI / 4 * i).toFloat()
                drawLine(
                    color,
                    Offset(px(12f) + cos(ang) * px(8.2f), px(12f) + sin(ang) * px(8.2f)),
                    Offset(px(12f) + cos(ang) * px(10.6f), px(12f) + sin(ang) * px(10.6f)),
                    strokeWidth = px(1.8f),
                    cap = StrokeCap.Round,
                )
            }
        }

        MedalGlyph.MOON -> {
            val moon = Path().apply {
                moveTo(px(15.4f), px(3.6f))
                quadraticBezierTo(px(7.4f), px(6.4f), px(8.8f), px(14.2f))
                quadraticBezierTo(px(9.8f), px(19.4f), px(15.4f), px(20.4f))
                quadraticBezierTo(px(7.6f), px(21.4f), px(5.2f), px(14.0f))
                quadraticBezierTo(px(3.4f), px(7.4f), px(15.4f), px(3.6f))
                close()
            }
            drawPath(moon, color)
        }

        MedalGlyph.STAR -> drawPath(starPath(::px), color)

        MedalGlyph.MOUNTAIN -> {
            val back = Path().apply {
                moveTo(px(3f), px(19f))
                lineTo(px(8.5f), px(9f))
                lineTo(px(13f), px(15.5f))
                lineTo(px(15.2f), px(12f))
                lineTo(px(21f), px(19f))
                close()
            }
            drawPath(back, color.copy(alpha = 0.55f))
            val front = Path().apply {
                moveTo(px(2.5f), px(20.5f))
                lineTo(px(9.6f), px(11.2f))
                lineTo(px(16.8f), px(20.5f))
                close()
            }
            drawPath(front, color)
        }

        MedalGlyph.HEART -> {
            val heart = Path().apply {
                moveTo(px(12f), px(20.6f))
                cubicTo(px(3.4f), px(14.4f), px(3.6f), px(7.6f), px(8.2f), px(6.2f))
                cubicTo(px(10.4f), px(5.5f), px(11.7f), px(7.0f), px(12f), px(8.4f))
                cubicTo(px(12.3f), px(7.0f), px(13.6f), px(5.5f), px(15.8f), px(6.2f))
                cubicTo(px(20.4f), px(7.6f), px(20.6f), px(14.4f), px(12f), px(20.6f))
                close()
            }
            drawPath(heart, color)
        }

        MedalGlyph.CROWN -> {
            val crown = Path().apply {
                moveTo(px(3.6f), px(18.6f))
                lineTo(px(4.6f), px(7.4f))
                lineTo(px(9.0f), px(11.6f))
                lineTo(px(12f), px(5.6f))
                lineTo(px(15f), px(11.6f))
                lineTo(px(19.4f), px(7.4f))
                lineTo(px(20.4f), px(18.6f))
                close()
            }
            drawPath(crown, color)
            drawLine(
                color,
                Offset(px(3.6f), px(20.6f)),
                Offset(px(20.4f), px(20.6f)),
                strokeWidth = px(1.9f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun starPath(px: (Float) -> Float): Path {
    val cx = px(12f)
    val cy = px(12.4f)
    val outer = px(9.2f)
    val inner = px(4.0f)
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = (-Math.PI / 2 + Math.PI / 5 * i).toFloat()
            val x = cx + cos(ang) * r
            val y = cy + sin(ang) * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * 迷你柱状图（月度支出 / 每日里程）。
 *
 * [values] 与 [labels] 一一对应；值全为 0 时只画灰底，不画「假的柱子」。
 * 最后一根用主题色，其余用半透明的同色 —— 让人一眼看出「当前这个月 / 今天」。
 */
@Composable
fun JournalBars(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 64.dp,
    color: Color = LocalAppColors.current.accent,
    track: Color = LocalAppColors.current.barTrack,
    valueTexts: List<String>? = null,
) {
    val C = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEachIndexed { index, raw ->
            val isLast = index == values.lastIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (valueTexts != null && valueTexts.getOrNull(index)?.isNotEmpty() == true) {
                    Text(
                        valueTexts[index],
                        color = C.textSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier.height(barHeight).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // 底槽：没有数据的月份也看得出「这里有一格」
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(track.copy(alpha = 0.35f)),
                    )
                    val f = raw.coerceIn(0f, 1f)
                    if (f > 0.02f) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isLast) color else color.copy(alpha = 0.45f)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    labels.getOrNull(index).orEmpty(),
                    color = if (isLast) C.textPrimary else C.textSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 统计小格：大数字 + 小标签。 */
@Composable
fun JournalStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalAppColors.current.textPrimary,
    align: Alignment.Horizontal = Alignment.Start,
) {
    val C = LocalAppColors.current
    Column(modifier = modifier, horizontalAlignment = align) {
        Text(
            value,
            color = valueColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = C.textSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

/** 章节标题 + 右侧说明。 */
@Composable
fun JournalSection(
    title: String,
    desc: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val C = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (desc != null) {
                Spacer(Modifier.width(8.dp))
                Text(desc, color = C.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 二级页通用顶栏：返回 + 标题 + 可选右侧动作。 */
@Composable
fun JournalTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "返回",
            size = 38.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(title, color = C.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 圆形色块 + 单字（花费分类）。 */
@Composable
fun CategoryDot(
    short: String,
    color: Color,
    size: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            short,
            color = color,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 可点的小药丸（分类筛选、年限建议）。 */
@Composable
fun JournalPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val shape = CircleShape
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) C.accent.copy(alpha = 0.18f) else C.chipBg)
            .border(1.dp, if (selected) C.accent.copy(alpha = 0.7f) else C.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) C.accent else C.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 「还没数据」的占位块，统一文案风格，避免每页各写一套。 */
@Composable
fun JournalEmpty(text: String, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = C.textSecondary, fontSize = 12.sp)
    }
}
