package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 空调页专用图标集（全部手绘矢量，不依赖 Material Icons）。
 *
 * 为什么不用 Material 的 Filled 图标：空调页里 Material 只有 `AcUnit` / `WbSunny` /
 * `Air` / `Waves` 这几个能凑，`WbSunny`（太阳）被同时拿来当「一键制热」「座椅加热」
 * 「方向盘加热」「后视镜加热」，四个不同功能长一个样；`Waves`（水波）表示「快速除味」
 * 也读不出来。而且 Filled 系列是实心块面，压在同色淡底上糊成一片。
 *
 * 这里统一成 **24 视口 / 圆头描边 / 拐角圆滑** 的线性风格：所有图标线宽一致、留白一致，
 * 摆在同一排里视觉重量相同。坐标全部以 24×24 为基准，绘制时按 `u = 实际边长 / 24`
 * 等比放大，因此在 16dp 到 30dp 之间都不会变形。
 */
enum class AcGlyph {
    /** 雪花 —— 制冷 / 极速降温 / 座椅通风。 */
    Snowflake,

    /** 太阳 —— 制热 / 一键制热。 */
    Sun,

    /** 四根递增竖条 —— 风量档位（比风扇叶片在 16dp 下更可读）。 */
    Airflow,

    /** 前挡风玻璃 + 上升气流 —— 前挡除霜 / 前挡出风。 */
    Defrost,

    /** 双向环形箭头 —— 内外循环。 */
    Circulation,

    /** 星芒 —— 快速除味 / 空气净化。 */
    Fresh,

    /** 时钟 —— 定时空调。 */
    Timer,

    /** 座椅 + 热浪 —— 座椅加热 / 方向盘加热。 */
    SeatHeat,

    /** 座椅 + 风线 —— 座椅通风。 */
    SeatVent,

    /** 方向盘 —— 方向盘加热。 */
    Steering,

    /** 后视镜 —— 后视镜加热。 */
    Mirror,

    /** 电源符号。 */
    Power,

    /** 三道风线 —— 出风方向（全车出风）。 */
    Wind,

    /** 向左折线 —— 返回。 */
    ChevronLeft,

    /** 向右折线 —— 「进入二级页」的提示。 */
    ChevronRight,
}

/**
 * 画一个空调图标。
 *
 * @param size 图标边长，默认 22dp（空调页主按钮用 24dp，行内小图标用 18dp）。
 * @param stroke 线宽，默认 1.6dp；不要低于 1.2dp，小图标会糊。
 */
@Composable
fun AcIcon(
    glyph: AcGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    stroke: Dp = 1.6.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        // 24 视口 → 实际 px 的换算系数
        val u = this.size.minDimension / VIEW_UNIT
        val w = stroke.toPx()
        when (glyph) {
            AcGlyph.Snowflake -> drawSnowflake(tint, u, w)
            AcGlyph.Sun -> drawSun(tint, u, w)
            AcGlyph.Airflow -> {
                // 四根递增的圆头竖条，底端对齐 —— 读作「档位」，一眼看出风量大小
                for ((i, height) in listOf(5.0f, 8.4f, 11.8f, 15.2f).withIndex()) {
                    val x = 6.4f + i * 3.7f
                    line(tint, u, w * 1.15f, x, 18.4f, x, 18.4f - height)
                }
            }
            AcGlyph.Defrost -> drawDefrost(tint, u, w)
            AcGlyph.Circulation -> drawCirculation(tint, u, w)
            AcGlyph.Fresh -> drawFresh(tint, u, w)
            AcGlyph.Timer -> drawTimer(tint, u, w)
            AcGlyph.SeatHeat -> {
                drawSeat(tint, u, w)
                // 两条 S 形热浪（靠背内）：三条会挤成一团，两条最清爽
                for (x in listOf(10.6f, 13.4f)) {
                    val p = Path().apply {
                        moveTo(x * u, 10.6f * u)
                        cubicTo((x - 1.4f) * u, 9.6f * u, (x + 1.4f) * u, 8.0f * u, x * u, 7.0f * u)
                    }
                    drawPath(p, tint, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            AcGlyph.SeatVent -> {
                drawSeat(tint, u, w)
                // 三条水平风线：与座椅加热的「竖向波浪」形成明确区分
                for (y in listOf(7.4f, 9.2f, 11.0f)) {
                    line(tint, u, w, 10.0f, y, 14.0f, y)
                }
            }
            AcGlyph.Steering -> drawSteering(tint, u, w)
            AcGlyph.Mirror -> drawMirror(tint, u, w)
            AcGlyph.Power -> drawPower(tint, u, w)
            AcGlyph.Wind -> {
                line(tint, u, w, 4.4f, 8.2f, 19.0f, 8.2f)
                line(tint, u, w, 6.6f, 12.4f, 17.4f, 12.4f)
                line(tint, u, w, 9.2f, 16.6f, 15.6f, 16.6f)
            }
            AcGlyph.ChevronLeft -> {
                val p = Path().apply {
                    moveTo(15.0f * u, 5.8f * u)
                    lineTo(9.0f * u, 12f * u)
                    lineTo(15.0f * u, 18.2f * u)
                }
                drawPath(p, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            AcGlyph.ChevronRight -> {
                val p = Path().apply {
                    moveTo(9.0f * u, 5.8f * u)
                    lineTo(15.0f * u, 12f * u)
                    lineTo(9.0f * u, 18.2f * u)
                }
                drawPath(p, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** 图标设计基准视口（与 SVG 的 viewBox 同义）。 */
private const val VIEW_UNIT = 24f

private const val PI_F = 3.1415927f

private fun rad(deg: Float): Float = deg * PI_F / 180f

/** 画一条圆头直线（坐标用 24 视口，内部乘 u）。 */
private fun DrawScope.line(
    c: Color,
    u: Float,
    w: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
) = drawLine(
    color = c,
    start = Offset(x1 * u, y1 * u),
    end = Offset(x2 * u, y2 * u),
    strokeWidth = w,
    cap = StrokeCap.Round,
)

/** 雪花：三条穿过中心的辐条（＝6 辐），每辐两端各一个 V 形分叉。 */
private fun DrawScope.drawSnowflake(c: Color, u: Float, w: Float) {
    val pivot = Offset(12f * u, 12f * u)
    for (i in 0 until 3) {
        rotate(i * 60f, pivot) {
            line(c, u, w, 12f, 3.4f, 12f, 20.6f)
            // 上端分叉
            line(c, u, w, 12f, 6.6f, 9.7f, 4.8f)
            line(c, u, w, 12f, 6.6f, 14.3f, 4.8f)
            // 下端分叉
            line(c, u, w, 12f, 17.4f, 9.7f, 19.2f)
            line(c, u, w, 12f, 17.4f, 14.3f, 19.2f)
        }
    }
}

/** 太阳：一个空心圆 + 八道短线（线在圆外，留出缝隙才不糊）。 */
private fun DrawScope.drawSun(c: Color, u: Float, w: Float) {
    val pivot = Offset(12f * u, 12f * u)
    drawCircle(c, radius = 4.1f * u, center = pivot, style = Stroke(w))
    for (i in 0 until 8) {
        rotate(i * 45f, pivot) {
            line(c, u, w, 12f, 6.9f, 12f, 3.6f)
        }
    }
}

/** 前挡除霜：扁宽的挡风玻璃轮廓 + 玻璃下方三道上升气流。 */
private fun DrawScope.drawDefrost(c: Color, u: Float, w: Float) {
    // 上边略窄、下边略宽、整体压扁 —— 拉太高就会读成「水桶」而不是玻璃
    val glass = Path().apply {
        moveTo(6.4f * u, 6.8f * u)
        lineTo(17.6f * u, 6.8f * u)
        lineTo(18.9f * u, 12.0f * u)
        lineTo(5.1f * u, 12.0f * u)
        close()
    }
    drawPath(glass, c, style = Stroke(w, join = StrokeJoin.Round))
    for (x in listOf(8.6f, 12f, 15.4f)) {
        val air = Path().apply {
            moveTo(x * u, 19.6f * u)
            cubicTo((x - 1.5f) * u, 18.2f * u, (x + 1.5f) * u, 17.4f * u, x * u, 16.0f * u)
        }
        drawPath(air, c, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** 内外循环：两段对称弧，末端各带一个 V 形箭头（表示气流成环）。 */
private fun DrawScope.drawCirculation(c: Color, u: Float, w: Float) {
    // 上弧：从左经上方到右上，箭头落在右端
    arcWithArrow(c, u, w, radius = 7.4f, startDeg = 190f, sweepDeg = 115f)
    // 下弧：从右经下方到左下，箭头落在左端
    arcWithArrow(c, u, w, radius = 7.4f, startDeg = 10f, sweepDeg = 115f)
}

/**
 * 画一段圆弧并在**终点**补一个箭头。
 *
 * 箭头用两条短线拼成 V（而不是实心三角）：与整体线性风格一致，
 * 也不会在小尺寸下糊成黑点。
 */
private fun DrawScope.arcWithArrow(
    c: Color,
    u: Float,
    w: Float,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
) {
    drawArc(
        color = c,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset((12f - radius) * u, (12f - radius) * u),
        size = Size(2f * radius * u, 2f * radius * u),
        style = Stroke(w, cap = StrokeCap.Round),
    )
    val endDeg = startDeg + sweepDeg
    val ex = 12f + radius * cos(rad(endDeg))
    val ey = 12f + radius * sin(rad(endDeg))
    // 顺时针弧的切线方向 = 角度 + 90°；箭头两翼相对切线各偏 30°
    val tangent = endDeg + 90f
    for (wing in listOf(tangent + 150f, tangent - 150f)) {
        drawLine(
            color = c,
            start = Offset(ex * u, ey * u),
            end = Offset((ex + 2.5f * cos(rad(wing))) * u, (ey + 2.5f * sin(rad(wing))) * u),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }
}

/** 快速除味：一大一小两颗四角星芒（「净化 / 清新」的通用语义）。 */
private fun DrawScope.drawFresh(c: Color, u: Float, w: Float) {
    val star = Path().apply {
        moveTo(11.0f * u, 3.4f * u)
        quadraticBezierTo(12.2f * u, 10.8f * u, 19.6f * u, 12f * u)
        quadraticBezierTo(12.2f * u, 13.2f * u, 11.0f * u, 20.6f * u)
        quadraticBezierTo(9.8f * u, 13.2f * u, 2.4f * u, 12f * u)
        quadraticBezierTo(9.8f * u, 10.8f * u, 11.0f * u, 3.4f * u)
    }
    drawPath(star, c, style = Stroke(w, join = StrokeJoin.Round))

    // 右上角的小星（实心）：呼应主星，又不跟主星抢视觉重量
    val spark = Path().apply {
        moveTo(19.0f * u, 3.4f * u)
        quadraticBezierTo(19.6f * u, 5.4f * u, 21.4f * u, 6.0f * u)
        quadraticBezierTo(19.6f * u, 6.6f * u, 19.0f * u, 8.6f * u)
        quadraticBezierTo(18.4f * u, 6.6f * u, 16.6f * u, 6.0f * u)
        quadraticBezierTo(18.4f * u, 5.4f * u, 19.0f * u, 3.4f * u)
    }
    drawPath(spark, c)
}

/** 定时空调：表盘 + 四道刻度 + 时针分针。 */
private fun DrawScope.drawTimer(c: Color, u: Float, w: Float) {
    val pivot = Offset(12f * u, 12f * u)
    drawCircle(c, radius = 8.0f * u, center = pivot, style = Stroke(w))
    for (i in 0 until 4) {
        rotate(i * 90f, pivot) {
            line(c, u, w, 12f, 5.0f, 12f, 6.4f)
        }
    }
    // 分针（向上）与时针（向右下）
    line(c, u, w, 12f, 12f, 12f, 7.6f)
    line(c, u, w, 12f, 12f, 15.2f, 13.4f)
}

/**
 * 座椅正视轮廓：靠背（上方竖着的圆角矩形）+ 坐垫（下方更宽更扁的圆角矩形）。
 *
 * 试过「侧视 L 形」，在 18dp 下会被读成一个 `h` 加一道波浪，所以在小尺寸下
 * 改用正视图 —— 两个叠起来的圆角块是「椅子 / 沙发」最省笔墨的写法。
 */
private fun DrawScope.drawSeat(c: Color, u: Float, w: Float) {
    drawRoundRect(
        color = c,
        topLeft = Offset(8.0f * u, 3.4f * u),
        size = Size(8.0f * u, 9.6f * u),
        cornerRadius = CornerRadius(2.4f * u, 2.4f * u),
        style = Stroke(w),
    )
    drawRoundRect(
        color = c,
        topLeft = Offset(6.2f * u, 12.6f * u),
        size = Size(11.6f * u, 4.8f * u),
        cornerRadius = CornerRadius(2.2f * u, 2.2f * u),
        style = Stroke(w),
    )
}

/** 方向盘：外圈 + 轮毂 + 三根辐条（右 / 左 / 下）。 */
private fun DrawScope.drawSteering(c: Color, u: Float, w: Float) {
    val pivot = Offset(12f * u, 12f * u)
    drawCircle(c, radius = 7.6f * u, center = pivot, style = Stroke(w))
    drawCircle(c, radius = 2.4f * u, center = pivot, style = Stroke(w))
    line(c, u, w, 14.3f, 12f, 19.4f, 12f)
    line(c, u, w, 9.7f, 12f, 4.6f, 12f)
    line(c, u, w, 12f, 14.4f, 12f, 19.4f)
}

/** 后视镜：横向圆角镜面 + 一道反光斜线 + 支架。 */
private fun DrawScope.drawMirror(c: Color, u: Float, w: Float) {
    drawRoundRect(
        color = c,
        topLeft = Offset(4.0f * u, 7.0f * u),
        size = Size(16.0f * u, 8.0f * u),
        cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
        style = Stroke(w),
    )
    // 镜面反光：一小段斜线，避免整块看起来像「空盒子」
    line(c, u, w, 7.2f, 12.4f, 9.6f, 10.0f)
    line(c, u, w, 11.0f, 16.0f, 11.0f, 19.4f)
}

/** 电源：顶部开口的圆环 + 竖线。 */
private fun DrawScope.drawPower(c: Color, u: Float, w: Float) {
    val r = 7.6f
    drawArc(
        color = c,
        startAngle = -58f,
        sweepAngle = 296f,
        useCenter = false,
        topLeft = Offset((12f - r) * u, (12f - r) * u),
        size = Size(2f * r * u, 2f * r * u),
        style = Stroke(w, cap = StrokeCap.Round),
    )
    line(c, u, w, 12f, 3.2f, 12f, 10.8f)
}
