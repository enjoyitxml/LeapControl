package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CostCategory

/**
 * 保险 / 保养的实心角标。
 *
 * 两张卡原来用的是「淡色圆底 + 细线图形」，在首页车况、能耗图旁边显得很轻，
 * 一眼扫过去不像「需要处理的事」。改成实心徽章：保养是六边形扳手、保险是盾牌「保」，
 * 形状本身就带含义，颜色压到最深，哪怕缩到 34dp 也立得住。
 *
 * 全部用路径画，不引图片资源：
 * - 原素材是白底不透明 PNG，直接贴会在卡片上留一个白方块；
 * - 深色模式下纯黑徽章会消失在卡片里，所以填充取 `textPrimary`、图形取 `panelBg` ——
 *   浅色下是「黑底白图」，深色下自动翻成「白底黑图」，明暗对比始终成立。
 */
@Composable
fun ReminderBadge(
    category: CostCategory,
    size: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val fill = C.textPrimary
    val mark = C.panelBg
    when (category) {
        CostCategory.SERVICE -> HexWrenchBadge(fill, mark, size, modifier)
        CostCategory.INSURANCE -> ShieldBadge(fill, mark, size, modifier)
        // 其余分类保持原样：这次只换保险与保养两张卡的观感
        else -> CategoryIcon(category = category, modifier = modifier, size = size)
    }
}

/** 六边形 + 开口扳手（保养）。 */
@Composable
private fun HexWrenchBadge(fill: Color, mark: Color, size: Dp, modifier: Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val hex = Path().apply {
            moveTo(6.5f * u, 2.7f * u)
            lineTo(17.5f * u, 2.7f * u)
            lineTo(21.3f * u, 12f * u)
            lineTo(17.5f * u, 21.3f * u)
            lineTo(6.5f * u, 21.3f * u)
            lineTo(2.7f * u, 12f * u)
            close()
        }
        drawPath(hex, fill)
        // 同色描边只为把六个尖角磨圆 —— 1.4 单位的圆角在 34dp 上是「工业感的倒角」，
        // 尖角在这个尺寸会像被裁坏的图形
        drawPath(hex, fill, style = Stroke(width = 1.4f * u, join = StrokeJoin.Round))
        // 必须裁进六边形：扳手的开口是「用底色切出来的负形」，不裁的话这道切口
        // 会顺着同一个 45° 从六边形左上角戳出去，在卡片上留下一根黑杠
        clipPath(hex) { drawWrench(mark, fill, u) }
    }
}

/**
 * 扳手：头部一个圆 + 一道开口，手柄是一根圆头粗线。
 *
 * 开口用「和底色同色的一块矩形」切出来 —— 徽章本身是实心的，所以「负形」直接画成填充色最省事，
 * 不用做布尔运算（Compose 的 Path 没有现成的差集）。
 */
private fun DrawScope.drawWrench(mark: Color, fill: Color, u: Float) {
    val cx = 8.9f * u
    val cy = 8.9f * u
    val head = 4.35f * u
    val handle = 3.5f * u

    drawCircle(mark, head, Offset(cx, cy))
    drawLine(
        color = mark,
        start = Offset(10.7f * u, 10.7f * u),
        end = Offset(16.4f * u, 16.4f * u),
        strokeWidth = handle,
        cap = StrokeCap.Round,
    )

    // 开口朝左上：把画布绕头部中心逆时针转 45°，切一个外宽内窄的楔形 ——
    // 等宽的竖槽在 34dp 下会读成「一道划痕」，楔形才像开口扳手的两颗牙
    rotate(degrees = -45f, pivot = Offset(cx, cy)) {
        val jaw = Path().apply {
            moveTo(cx - 0.85f * u, cy + 0.4f * u)
            lineTo(cx + 0.85f * u, cy + 0.4f * u)
            lineTo(cx + 2.5f * u, cy - 11f * u)
            lineTo(cx - 2.5f * u, cy - 11f * u)
            close()
        }
        drawPath(jaw, fill)
    }
}

/** 盾牌 + 「保」（保险）。 */
@Composable
private fun ShieldBadge(fill: Color, mark: Color, size: Dp, modifier: Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val u = this.size.minDimension / 24f
            val shield = Path().apply {
                moveTo(12f * u, 2.3f * u)
                cubicTo(15.2f * u, 3.9f * u, 18.3f * u, 4.7f * u, 20.5f * u, 5f * u)
                cubicTo(20.9f * u, 11.2f * u, 19.4f * u, 16.5f * u, 16.2f * u, 19.6f * u)
                cubicTo(14.9f * u, 20.9f * u, 13.3f * u, 21.6f * u, 12f * u, 22.6f * u)
                cubicTo(10.7f * u, 21.6f * u, 9.1f * u, 20.9f * u, 7.8f * u, 19.6f * u)
                cubicTo(4.6f * u, 16.5f * u, 3.1f * u, 11.2f * u, 3.5f * u, 5f * u)
                cubicTo(5.7f * u, 4.7f * u, 8.8f * u, 3.9f * u, 12f * u, 2.3f * u)
                close()
            }
            drawPath(shield, fill)
        }
        // 「保」用文字而不是路径：这个字有 9 笔，手绘路径在 40dp 以下必然糊
        Text(
            "保",
            color = mark,
            fontSize = (size.value * 0.40f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
