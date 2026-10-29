package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.Medal
import com.leapcontrol.app.journal.MedalGlyph
import com.leapcontrol.app.journal.MedalTier
import kotlin.math.cos
import kotlin.math.sin

/**
 * 零记的共用视觉件。
 *
 * 全部自绘：进度环、勋章、小柱状图、分类圆标。不引第三方图表库 ——
 * 这里需要的东西都很小，引一个库反而要跟着它的主题色、字号走。
 */

/**
 * 零记的卡片底座。
 *
 * **必须是实心色 + 1dp 发丝描边、不带投影**。
 *
 * 以前这里直接用首页那套 `AppCard`（`0xD9FFFFFF` 半透明白 + 4dp 阴影），在首页
 * 压在车模 / 季节渐变上很好看，搬到零记这种整页浅色的列表页就出问题了：
 * 阴影会从半透明的卡片底下透上来，卡面变成四周发灰、中间发白的「脏渐变」，
 * 卡片里再没有别的东西时尤其明显。零记一律用不透明的 [AppColors.panelBg]，
 * 靠一条极淡的描边交代边界 —— 与服务、个性化等二级页保持一致。
 */
@Composable
fun JournalCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.panelBg, shape)
            .border(1.dp, C.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

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
 * 金属档位的颜色：青铜 / 白银 / 黄金 / 铂金。
 *
 * 深色模式整体提亮一档，否则青铜在 `#14161B` 上几乎看不见。
 */
fun medalTierColor(tier: MedalTier, dark: Boolean): Color = when (tier) {
    MedalTier.BRONZE -> if (dark) Color(0xFFD79A66) else Color(0xFFB0703C)
    MedalTier.SILVER -> if (dark) Color(0xFFB6C0CB) else Color(0xFF7C8B98)
    MedalTier.GOLD -> if (dark) Color(0xFFE0B24E) else Color(0xFFB58A24)
    MedalTier.PLATINUM -> if (dark) Color(0xFF9FA9E6) else Color(0xFF6B7ABB)
}

/**
 * 勋章圆牌。解锁 = 档位金属色 + 高亮描边；未解锁 = 灰、低透明度。
 *
 * [size] 是直径；glyph 的线条粗细跟着 size 走，缩到 22dp 也不会糊成一团。
 */
@Composable
fun MedalBadge(
    medal: Medal,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val C = LocalAppColors.current
    val tint = if (unlocked) medalTierColor(medal.tier, C.isDark) else C.textSecondary
    val fill = if (unlocked) tint.copy(alpha = 0.13f) else C.textSecondary.copy(alpha = 0.07f)
    val ring = if (unlocked) tint.copy(alpha = 0.55f) else C.textSecondary.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.56f)) {
            drawGlyph(medal.glyph, tint.copy(alpha = if (unlocked) 1f else 0.62f))
        }
    }
}

/** 在画布里画一个 24×24 归一坐标的 glyph（实际按画布尺寸缩放）。 */
fun DrawScope.drawGlyph(glyph: MedalGlyph, color: Color) {
    val s = size.minDimension / 24f
    fun px(v: Float) = v * s
    val thin = Stroke(width = px(1.7f), cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun seg(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 1.9f) {
        drawLine(color, Offset(px(x1), px(y1)), Offset(px(x2), px(y2)), strokeWidth = px(w), cap = StrokeCap.Round)
    }

    fun box(l: Float, t: Float, r: Float, b: Float, radius: Float = 1.5f, filled: Boolean = false) {
        val tl = Offset(px(l), px(t))
        val sz = Size(px(r - l), px(b - t))
        val corner = CornerRadius(px(radius))
        if (filled) drawRoundRect(color, tl, sz, corner)
        else drawRoundRect(color, tl, sz, corner, style = thin)
    }

    when (glyph) {
        MedalGlyph.SPROUT -> {
            seg(12f, 20.5f, 12f, 10.5f)
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

        MedalGlyph.LEAF -> {
            val leaf = Path().apply {
                moveTo(px(4.6f), px(19.4f))
                cubicTo(px(4.6f), px(9.4f), px(10.6f), px(4.2f), px(19.4f), px(4.2f))
                cubicTo(px(19.4f), px(13.6f), px(13.4f), px(19.4f), px(4.6f), px(19.4f))
                close()
            }
            drawPath(leaf, color.copy(alpha = 0.9f))
            drawPath(
                Path().apply {
                    moveTo(px(6.2f), px(17.8f))
                    quadraticBezierTo(px(11f), px(14f), px(17.2f), px(6.4f))
                },
                Color.White.copy(alpha = 0.75f),
                style = Stroke(width = px(1.4f), cap = StrokeCap.Round),
            )
        }

        MedalGlyph.CALENDAR -> {
            box(3.8f, 6.2f, 20.2f, 20.4f, radius = 3f)
            seg(3.8f, 10.6f, 20.2f, 10.6f, 1.6f)
            seg(8.2f, 3.4f, 8.2f, 7.4f, 2.0f)
            seg(15.8f, 3.4f, 15.8f, 7.4f, 2.0f)
            drawCircle(color, px(1.35f), Offset(px(8.9f), px(14.4f)))
            drawCircle(color, px(1.35f), Offset(px(12f), px(14.4f)))
            drawCircle(color, px(1.35f), Offset(px(15.1f), px(14.4f)))
            drawCircle(color, px(1.35f), Offset(px(8.9f), px(17.4f)))
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
            seg(3.6f, 20.6f, 20.4f, 20.6f)
        }

        MedalGlyph.FLAG -> {
            seg(7f, 4f, 7f, 21f)
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

        MedalGlyph.CAR -> {
            val roof = Path().apply {
                moveTo(px(6.4f), px(12.4f))
                lineTo(px(8.6f), px(7.2f))
                lineTo(px(15.4f), px(7.2f))
                lineTo(px(17.6f), px(12.4f))
            }
            drawPath(roof, color, style = Stroke(width = px(1.8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            box(3.2f, 12.2f, 20.8f, 17.4f, radius = 2f)
            drawCircle(color, px(1.7f), Offset(px(7.6f), px(17.6f)))
            drawCircle(color, px(1.7f), Offset(px(16.4f), px(17.6f)))
        }

        MedalGlyph.COMPASS -> {
            drawCircle(color, radius = px(8.8f), center = Offset(px(12f), px(12f)), style = Stroke(width = px(1.7f)))
            val needle = Path().apply {
                moveTo(px(15.2f), px(8.8f))
                lineTo(px(13.1f), px(13.1f))
                lineTo(px(8.8f), px(15.2f))
                lineTo(px(10.9f), px(10.9f))
                close()
            }
            drawPath(needle, color)
        }

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

        MedalGlyph.GEM -> {
            val gem = Path().apply {
                moveTo(px(4f), px(9.2f))
                lineTo(px(7.6f), px(4.4f))
                lineTo(px(16.4f), px(4.4f))
                lineTo(px(20f), px(9.2f))
                lineTo(px(12f), px(20.2f))
                close()
            }
            drawPath(gem, color.copy(alpha = 0.35f))
            drawPath(gem, color, style = thin)
            seg(4f, 9.2f, 20f, 9.2f, 1.5f)
            seg(7.6f, 4.4f, 9.6f, 9.2f, 1.3f)
            seg(16.4f, 4.4f, 14.4f, 9.2f, 1.3f)
            seg(9.6f, 9.2f, 12f, 20.2f, 1.3f)
            seg(14.4f, 9.2f, 12f, 20.2f, 1.3f)
        }

        MedalGlyph.TROPHY -> {
            val cup = Path().apply {
                moveTo(px(7f), px(4.2f))
                lineTo(px(17f), px(4.2f))
                lineTo(px(16.2f), px(11.2f))
                quadraticBezierTo(px(12f), px(15.4f), px(7.8f), px(11.2f))
                close()
            }
            drawPath(cup, color.copy(alpha = 0.9f))
            drawArc(
                color = color,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(px(2.8f), px(5.2f)),
                size = Size(px(4.6f), px(4.6f)),
                style = Stroke(width = px(1.5f)),
            )
            drawArc(
                color = color,
                startAngle = 270f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(px(16.6f), px(5.2f)),
                size = Size(px(4.6f), px(4.6f)),
                style = Stroke(width = px(1.5f)),
            )
            seg(12f, 15f, 12f, 18.4f, 1.8f)
            seg(8.6f, 20.2f, 15.4f, 20.2f, 2.0f)
            seg(10.2f, 18.4f, 13.8f, 18.4f, 1.6f)
        }

        MedalGlyph.MAP_PIN -> {
            drawCircle(color, radius = px(4.8f), center = Offset(px(12f), px(9.2f)), style = Stroke(width = px(1.8f)))
            val tail = Path().apply {
                moveTo(px(8.5f), px(12.5f))
                lineTo(px(12f), px(20.8f))
                lineTo(px(15.5f), px(12.5f))
            }
            drawPath(tail, color, style = Stroke(width = px(1.8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, px(1.7f), Offset(px(12f), px(9.2f)))
        }

        MedalGlyph.CITY -> {
            box(3.4f, 11f, 8.4f, 20.6f, radius = 1.2f)
            box(8.4f, 6f, 15.6f, 20.6f, radius = 1.2f)
            box(15.6f, 13.4f, 20.6f, 20.6f, radius = 1.2f)
            listOf(10.6f, 14.2f, 17.8f).forEach { y ->
                drawCircle(color.copy(alpha = 0.65f), px(0.8f), Offset(px(12f), px(y)))
            }
            drawCircle(color.copy(alpha = 0.65f), px(0.8f), Offset(px(6f), px(14.4f)))
            drawCircle(color.copy(alpha = 0.65f), px(0.8f), Offset(px(18.2f), px(16.4f)))
        }

        MedalGlyph.GRID -> {
            box(3.6f, 3.6f, 20.4f, 20.4f, radius = 2.4f)
            seg(9.2f, 3.6f, 9.2f, 20.4f, 1.4f)
            seg(14.8f, 3.6f, 14.8f, 20.4f, 1.4f)
            seg(3.6f, 9.2f, 20.4f, 9.2f, 1.4f)
            seg(3.6f, 14.8f, 20.4f, 14.8f, 1.4f)
        }

        MedalGlyph.COIN -> {
            drawCircle(color, radius = px(8.4f), center = Offset(px(12f), px(12f)), style = Stroke(width = px(1.8f)))
            seg(9.6f, 8.2f, 12f, 11.6f, 1.7f)
            seg(14.4f, 8.2f, 12f, 11.6f, 1.7f)
            seg(12f, 11.6f, 12f, 16.4f, 1.7f)
            seg(9.4f, 12.8f, 14.6f, 12.8f, 1.5f)
            seg(9.4f, 14.8f, 14.6f, 14.8f, 1.5f)
        }

        MedalGlyph.RECEIPT -> {
            val receipt = Path().apply {
                moveTo(px(5.6f), px(3.6f))
                lineTo(px(18.4f), px(3.6f))
                lineTo(px(18.4f), px(20.4f))
                lineTo(px(16.2f), px(18.6f))
                lineTo(px(14f), px(20.4f))
                lineTo(px(12f), px(18.6f))
                lineTo(px(10f), px(20.4f))
                lineTo(px(7.8f), px(18.6f))
                lineTo(px(5.6f), px(20.4f))
                close()
            }
            drawPath(receipt, color, style = thin)
            seg(8.6f, 8f, 15.4f, 8f, 1.5f)
            seg(8.6f, 11.4f, 15.4f, 11.4f, 1.5f)
            seg(8.6f, 14.8f, 13.2f, 14.8f, 1.5f)
        }

        MedalGlyph.BOLT -> {
            val bolt = Path().apply {
                moveTo(px(13.6f), px(2.6f))
                lineTo(px(5.8f), px(13.6f))
                lineTo(px(11.2f), px(13.6f))
                lineTo(px(10.2f), px(21.4f))
                lineTo(px(18.2f), px(10.2f))
                lineTo(px(12.8f), px(10.2f))
                close()
            }
            drawPath(bolt, color)
        }

        MedalGlyph.FUEL -> {
            // 加油机：机身 + 显示屏 + 右侧油枪软管
            box(3.6f, 5.4f, 12.2f, 20.6f, radius = 1.8f)
            box(5.8f, 7.8f, 10f, 10.6f, radius = 0.8f)
            // 机身里的油滴，比一块空白更像「油」
            val drip = Path().apply {
                moveTo(px(7.9f), px(13.6f))
                cubicTo(px(9.6f), px(15.8f), px(10.1f), px(16.9f), px(10.1f), px(17.6f))
                cubicTo(px(10.1f), px(19.1f), px(9.1f), px(20f), px(7.9f), px(20f))
                cubicTo(px(6.7f), px(20f), px(5.7f), px(19.1f), px(5.7f), px(17.6f))
                cubicTo(px(5.7f), px(16.9f), px(6.2f), px(15.8f), px(7.9f), px(13.6f))
                close()
            }
            drawPath(drip, color.copy(alpha = 0.85f))
            seg(12.2f, 9.4f, 16f, 9.4f, 1.6f)
            seg(16f, 9.4f, 16f, 16.8f, 1.6f)
            seg(16f, 16.8f, 19f, 16.8f, 1.6f)
        }

        MedalGlyph.WRENCH -> {
            // 开口扳手：斜柄 + 一个留了缺口的圆环当「开口头」。
            // 圆环 + 直柄会被看成钥匙，缺口是它和钥匙的唯一区别。
            seg(5.2f, 18.8f, 12.4f, 11.6f, 2.7f)
            drawArc(
                color = color,
                startAngle = 42f,
                sweepAngle = 276f,
                useCenter = false,
                topLeft = Offset(px(11.3f), px(2.5f)),
                size = Size(px(9.8f), px(9.8f)),
                style = Stroke(width = px(2.7f), cap = StrokeCap.Round),
            )
        }

        MedalGlyph.DROPLET -> {
            val drop = Path().apply {
                moveTo(px(12f), px(3.4f))
                cubicTo(px(17.4f), px(9.2f), px(19.4f), px(12.4f), px(19.4f), px(15.2f))
                cubicTo(px(19.4f), px(19.3f), px(16.1f), px(21.4f), px(12f), px(21.4f))
                cubicTo(px(7.9f), px(21.4f), px(4.6f), px(19.3f), px(4.6f), px(15.2f))
                cubicTo(px(4.6f), px(12.4f), px(6.6f), px(9.2f), px(12f), px(3.4f))
                close()
            }
            drawPath(drop, color.copy(alpha = 0.9f))
        }

        MedalGlyph.SHIELD -> {
            val shield = Path().apply {
                moveTo(px(12f), px(3f))
                lineTo(px(20f), px(6.4f))
                lineTo(px(20f), px(12.4f))
                quadraticBezierTo(px(20f), px(18.6f), px(12f), px(21.4f))
                quadraticBezierTo(px(4f), px(18.6f), px(4f), px(12.4f))
                lineTo(px(4f), px(6.4f))
                close()
            }
            drawPath(shield, color, style = thin)
            seg(8.8f, 12f, 11.2f, 14.4f, 1.8f)
            seg(11.2f, 14.4f, 15.6f, 9.4f, 1.8f)
        }

        MedalGlyph.TOLL -> {
            seg(4.6f, 8.4f, 4.6f, 21f, 2.2f)
            box(5.6f, 9.6f, 20f, 12.8f, radius = 1.4f)
            seg(7.8f, 12.8f, 7.8f, 15.6f, 1.5f)
            seg(12.8f, 12.8f, 12.8f, 15.6f, 1.5f)
            seg(17.8f, 12.8f, 17.8f, 15.6f, 1.5f)
        }

        MedalGlyph.PARKING -> {
            box(3.6f, 3.6f, 20.4f, 20.4f, radius = 4f)
            seg(9.4f, 17.2f, 9.4f, 7f, 2.0f)
            val bowl = Path().apply {
                moveTo(px(9.4f), px(7f))
                lineTo(px(13.2f), px(7f))
                quadraticBezierTo(px(16.4f), px(7f), px(16.4f), px(10f))
                quadraticBezierTo(px(16.4f), px(13f), px(13.2f), px(13f))
                lineTo(px(9.4f), px(13f))
            }
            drawPath(bowl, color, style = Stroke(width = px(2.0f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        MedalGlyph.TARGET -> {
            drawCircle(color, radius = px(8.6f), center = Offset(px(12f), px(12f)), style = Stroke(width = px(1.6f)))
            drawCircle(color, radius = px(5.2f), center = Offset(px(12f), px(12f)), style = Stroke(width = px(1.6f)))
            drawCircle(color, radius = px(1.9f), center = Offset(px(12f), px(12f)))
        }

        MedalGlyph.GAUGE -> {
            drawArc(
                color = color,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(px(3.4f), px(5.2f)),
                size = Size(px(17.2f), px(17.2f)),
                style = Stroke(width = px(1.9f), cap = StrokeCap.Round),
            )
            seg(12f, 13.2f, 16.4f, 8.6f, 2.0f)
            drawCircle(color, px(1.7f), Offset(px(12f), px(13.2f)))
        }

        MedalGlyph.HOURGLASS -> {
            seg(5.6f, 3.6f, 18.4f, 3.6f, 2.0f)
            seg(5.6f, 20.4f, 18.4f, 20.4f, 2.0f)
            val glass = Path().apply {
                moveTo(px(6.9f), px(4.8f))
                lineTo(px(17.1f), px(4.8f))
                lineTo(px(12.6f), px(12f))
                lineTo(px(17.1f), px(19.2f))
                lineTo(px(6.9f), px(19.2f))
                lineTo(px(11.4f), px(12f))
                close()
            }
            drawPath(glass, color, style = thin)
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

/**
 * 折线趋势图（成本页用它替代柱状图）。
 *
 * 为什么换掉柱状图：柱子的高度只能靠肉眼比，横轴一密（近 8 周 / 6 个月）
 * 就变成一排细条，既看不出走势也读不出具体数；折线天然表达「涨还是跌」。
 *
 * [values] 是 0~1 的归一化高度（调用方按本期最大值算好），[labels] 是横轴文字。
 * 刻度文字走 Canvas 内的 `drawText`，而不是外面套一排 `Row` —— 只有这样才能
 * 让标签中心与折线拐点严格对齐（Row 均分是「格子中心」，与 `i/(n-1)` 的采样点错位）。
 */
@Composable
fun JournalLineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    color: Color = LocalAppColors.current.accent,
) {
    val C = LocalAppColors.current
    if (values.isEmpty()) return

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = C.textSecondary, fontSize = 9.sp)
    val gridColor = C.border

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val topPad = 8.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val chartH = size.height - topPad - bottomPad
        if (chartH <= 0f) return@Canvas
        val chartW = size.width
        val n = values.size
        val bottom = topPad + chartH
        val stepX = if (n > 1) chartW / (n - 1) else chartW

        // 三条横向参考线：给「高低」一个可对照的标尺，又不会抢折线的注意力
        repeat(3) { i ->
            val y = topPad + chartH * i / 2f
            drawLine(
                color = gridColor.copy(alpha = 0.45f),
                start = Offset(0f, y),
                end = Offset(chartW, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                ),
            )
        }

        val points = values.mapIndexed { i, raw ->
            Offset(stepX * i, bottom - chartH * raw.coerceIn(0f, 1f))
        }

        // 面积：折线下方的淡渐变，让「这一期整体多高」一眼可见
        if (n > 1) {
            val area = Path().apply {
                moveTo(points[0].x, bottom)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, bottom)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0f)),
                    startY = topPad,
                    endY = bottom,
                ),
            )
        }

        // 折线本体：圆头圆角，2dp —— 细了在渐变背景上看不清，粗了显得笨
        if (n > 1) {
            val line = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = line,
                color = color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // 采样点：普通点小一圈，最后一个（当期）加号放大 + 白芯，作为「你现在在这儿」
        points.forEachIndexed { i, p ->
            val isLast = i == n - 1
            drawCircle(color, radius = if (isLast) 4.2.dp.toPx() else 2.2.dp.toPx(), center = p)
            if (isLast) drawCircle(Color.White, radius = 1.7.dp.toPx(), center = p)
        }

        // 横轴标签：点多了隔一个画（保留最后一个），否则 8 个 9sp 字会糊成一片
        labels.forEachIndexed { i, label ->
            if (label.isEmpty()) return@forEachIndexed
            if (n > 6 && i % 2 == 1 && i != n - 1) return@forEachIndexed
            val layout = measurer.measure(AnnotatedString(label), style = labelStyle)
            val x = (points[i].x - layout.size.width / 2f)
                .coerceIn(0f, (chartW - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, bottom + 5.dp.toPx()))
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

/** 可点的小药丸（分类筛选、年限建议、勋章分组）。 */
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
            // 未选中用**实心**底色：半透明白压在季节背景上会泛出一层暖灰，
            // 一屏里混着卡片和药丸两种白，看起来就是「脏」
            .background(if (selected) C.accent.copy(alpha = 0.18f) else C.panelBg)
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

/**
 * 花费分类的小图标：圆底 + 自绘线稿。
 *
 * 图标直接用勋章那一套 glyph（充电=闪电、加油=加油机、维修=扳手……），
 * 同一条线稿在两个页面里出现，风格不会打架。
 */
@Composable
fun CategoryIcon(
    category: com.leapcontrol.app.journal.CostCategory,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val color = categoryColor(category.hue)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.58f)) {
            drawGlyph(category.glyph, color)
        }
    }
}

/**
 * 分段选择器（今日 / 本周 / 本月 / 今年 / 全部）。
 *
 * 五个药丸横着排会挤到换行，这里改成等分格：选中格填主题色，其余透明。
 */
@Composable
fun JournalSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val outer = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outer)
            .background(C.chipBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) readableOn(C.accent) else C.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 分类占比环形图。
 *
 * 比堆叠条更容易看出「哪一类占了一半」—— 中间留白正好放当期总额。
 * 只有一个分类时不画缝隙，避免看起来像没画满。
 */
@Composable
fun JournalDonut(
    slices: List<Pair<com.leapcontrol.app.journal.CostCategory, Double>>,
    total: Double,
    centerTitle: String,
    centerSub: String,
    modifier: Modifier = Modifier,
    stroke: Dp = 15.dp,
) {
    val C = LocalAppColors.current
    // 颜色必须在组合期算好：categoryColor 是 @Composable，不能在绘制 lambda 里调
    val sliceColors = slices.map { categoryColor(it.first.hue) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val strokePx = stroke.toPx()
            val diameter = size.minDimension - strokePx
            if (diameter <= 0f) return@Canvas
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = C.barTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (total <= 0.0) return@Canvas
            val gap = if (slices.size > 1) 1.6f else 0f
            var start = -90f
            slices.forEachIndexed { index, (_, amount) ->
                val sweep = (amount / total * 360.0).toFloat()
                if (sweep > 0.6f) {
                    drawArc(
                        color = sliceColors[index],
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx),
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, color = C.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(centerSub, color = C.textSecondary, fontSize = 9.sp)
        }
    }
}

/**
 * 按底色亮度选字色：主题 accent 有深有浅，固定白字在亮绿上会糊，
 * 固定黑字在深蓝上又看不清。
 */
fun readableOn(background: Color): Color {
    val luma = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luma > 0.62f) Color(0xFF12161C) else Color.White
}

