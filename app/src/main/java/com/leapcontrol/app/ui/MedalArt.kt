package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leapcontrol.app.journal.Medal
import com.leapcontrol.app.journal.MedalGlyph
import com.leapcontrol.app.journal.MedalTier
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 制图规则（改图形前先读这一节，这是 108 枚看起来像「一套」的唯一原因）
// ---------------------------------------------------------------------------
//
// 1. 全部画在 24×24 网格里，图形外接框约 16~18 单位；同一枚勋章里只有一个视觉重心。
// 2. 只有三档线宽：主轮廓 1.65、内部细节 1.05、点缀圆点 1.75。不再出现第 4 种粗细。
// 3. 端点一律圆头、拐角一律圆角；不画尖角，避免 46dp 下出毛刺。
// 4. 只有 [MedalGlyph.TARGET] 的红心是实心块，其余全部线稿 —— 实心块在 46dp 会糊成墨点，
//    线稿才是「刻」在金属上的东西，也是这套章显精致的原因。
// 5. 圆点用「零长度线段 + 圆帽」画（`o` 前缀），比 drawCircle 更省，缩小时也会自然消失。

/** 24 网格的边长。 */
private const val GRID = 24f

/** 主轮廓线宽（24 网格下）。 */
private const val W_MAIN = 1.65f

/** 内部细节线宽。 */
private const val W_FINE = 1.05f

/** 点缀圆点的直径。 */
private const val W_DOT = 1.75f

/**
 * 图形表。
 *
 * 字符串里用 `|` 分层，前缀决定线宽：`~` 细线、`o` 圆点、`*` 实心，无前缀即主轮廓。
 * 这些路径与设计稿逐字一致，改一个数字都要同时看 46dp 和 76dp 两种尺寸。
 */
internal object MedalGlyphSource {

    val ALL: Map<MedalGlyph, String> = mapOf(
        MedalGlyph.SPROUT to "M12 21.2V11.9|M12 14.9C7.4 15.1 4.5 12 4.3 7.7 9.2 7.5 12.2 10 12 14.9Z|M12 11.9C12.2 7.6 15 5.3 19.6 5.1 19.7 9.5 17 12.2 12 11.9Z",
        MedalGlyph.LEAF to "M5.2 18.9C5.2 10.4 10.6 5 19 5 19 13.4 13.6 18.9 5.2 18.9Z|~M6.8 17.5C10.2 13.9 14 10.1 17.4 6.7",
        MedalGlyph.CALENDAR to "M4.6 7.6A3 3 0 0 1 7.6 4.6H16.4A3 3 0 0 1 19.4 7.6V17.4A3 3 0 0 1 16.4 20.4H7.6A3 3 0 0 1 4.6 17.4Z|~M4.6 10.4H19.4|M8.6 3.4V6.4M15.4 3.4V6.4|oM8.9 13.5h0.01|oM12 13.5h0.01|oM15.1 13.5h0.01|oM8.9 16.9h0.01|oM12 16.9h0.01",
        MedalGlyph.SUN to "M17.1 12A5.1 5.1 0 1 1 6.9 12A5.1 5.1 0 1 1 17.1 12Z|M12 2.6V5M12 19V21.4M2.6 12H5M19 12H21.4|M5.4 5.4L7.1 7.1M16.9 16.9L18.6 18.6M18.6 5.4L16.9 7.1M7.1 16.9L5.4 18.6",
        MedalGlyph.MOON to "M20.2 13.2A8.6 8.6 0 1 1 10.8 3.8 6.9 6.9 0 0 0 20.2 13.2Z",
        MedalGlyph.STAR to "M12.00 3.20L14.20 9.17L20.56 9.42L15.57 13.36L17.29 19.48L12.00 15.95L6.71 19.48L8.43 13.36L3.44 9.42L9.80 9.17Z",
        MedalGlyph.HEART to "M12 20.4C4.6 15.2 4.3 8.7 8.5 6.8 10.4 6 11.7 7.4 12 8.9 12.3 7.4 13.6 6 15.5 6.8 19.7 8.7 19.4 15.2 12 20.4Z",
        MedalGlyph.CROWN to "M4.4 18.2 5.4 7.8 9.6 11.8 12 6.2 14.4 11.8 18.6 7.8 19.6 18.2Z|~M4.9 20.6H19.1",
        MedalGlyph.FLAG to "M6.6 3.8V20.6|M6.6 5.2C10.3 2.7 13.2 7.9 17.8 5.2V12.9C13.2 15.6 10.3 10.4 6.6 12.9Z",
        MedalGlyph.ROAD to "M7.4 21 10.2 3.4H13.8L16.6 21Z|~M12 6.6V9.2M12 11.6V14.6M12 17V20.6",
        MedalGlyph.CAR to "M3.8 12.4A1.8 1.8 0 0 1 5.6 10.6H18.4A1.8 1.8 0 0 1 20.2 12.4V15.4A1.8 1.8 0 0 1 18.4 17.2H5.6A1.8 1.8 0 0 1 3.8 15.4Z|M6.6 10.6 8.8 6.4H15.2L17.4 10.6|M9.3 17.2A1.6 1.6 0 1 1 6.1 17.2A1.6 1.6 0 1 1 9.3 17.2Z|M17.9 17.2A1.6 1.6 0 1 1 14.7 17.2A1.6 1.6 0 1 1 17.9 17.2Z",
        MedalGlyph.COMPASS to "M20.6 12A8.6 8.6 0 1 1 3.4 12A8.6 8.6 0 1 1 20.6 12Z|M15.6 8.4 12.9 12.9 8.4 15.6 11.1 11.1Z|oM12 12h0.01",
        MedalGlyph.MOUNTAIN to "M3.2 20.4 9.4 9.4 12.9 15.3 15.1 11.8 20.8 20.4Z",
        MedalGlyph.GEM to "M4.2 9.4 7.8 4.4H16.2L19.8 9.4 12 20.2Z|~M4.2 9.4H19.8M7.8 4.4 9.8 9.4 12 20.2 14.2 9.4 16.2 4.4",
        MedalGlyph.TROPHY to "M6.8 3.6H17.2V8.7A5.2 5.2 0 0 1 6.8 8.7Z|M7.2 5.6H4.8V6.6A2.4 2.4 0 0 0 7.2 9|M16.8 5.6H19.2V6.6A2.4 2.4 0 0 1 16.8 9|M12 13.4V16.2|M9.6 20.4V18.8A1.2 1.2 0 0 1 10.8 17.6H13.2A1.2 1.2 0 0 1 14.4 18.8V20.4|~M7.8 20.4H16.2",
        MedalGlyph.MAP_PIN to "M12 21C16.2 15.9 18.4 12.8 18.4 9.9A6.4 6.4 0 1 0 5.6 9.9C5.6 12.8 7.8 15.9 12 21Z|M14.4 9.7A2.4 2.4 0 1 1 9.6 9.7A2.4 2.4 0 1 1 14.4 9.7Z",
        MedalGlyph.CITY to "M3.8 12.6H8.4V20.4H3.8Z|M8.4 6.6H15.6V20.4H8.4Z|M15.6 14.4H20.2V20.4H15.6Z|oM10.7 9.6h0.01|oM13.3 9.6h0.01|oM10.7 12.6h0.01|oM13.3 12.6h0.01|oM10.7 15.6h0.01|oM13.3 15.6h0.01|oM6.1 15.2h0.01|oM6.1 17.8h0.01|oM17.9 17.4h0.01",
        MedalGlyph.GRID to "M7 3.8H17A3.2 3.2 0 0 1 20.2 7V17A3.2 3.2 0 0 1 17 20.2H7A3.2 3.2 0 0 1 3.8 17V7A3.2 3.2 0 0 1 7 3.8Z|~M9.2 3.8V20.2M14.8 3.8V20.2M3.8 9.2H20.2M3.8 14.8H20.2",
        MedalGlyph.COIN to "M20.4 12A8.4 8.4 0 1 1 3.6 12A8.4 8.4 0 1 1 20.4 12Z|M9.2 7.8 12 11.4 14.8 7.8M12 11.4V16.6|~M9.4 12.6H14.6M9.4 14.6H14.6",
        MedalGlyph.RECEIPT to "M5.8 3.8H18.2V20.4L16.1 18.7 14 20.4 12 18.7 10 20.4 7.9 18.7 5.8 20.4Z|~M8.8 8.2H15.2M8.8 11.4H15.2M8.8 14.6H13.2",
        MedalGlyph.BOLT to "M13.4 2.8 6.2 13.4H11.4L10.6 21.2 17.8 10.6H12.6Z",
        MedalGlyph.FUEL to "M4.6 9.4A2.2 2.2 0 0 1 6.8 7.2H11.4A2.2 2.2 0 0 1 13.6 9.4V20.4H4.6Z|~M6.9 9.9H11.3V13.3H6.9Z|M13.6 9.2H15.4A2.2 2.2 0 0 1 17.6 11.4V16.6|~M17.6 16.6H20.4V19.2H17.6|~M3.4 20.6H14.8",
        MedalGlyph.WRENCH to "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
        MedalGlyph.DROPLET to "M12 3.4C15.2 7.2 17.6 10.2 17.6 13A5.6 5.6 0 0 1 6.4 13C6.4 10.2 8.8 7.2 12 3.4Z|~M9.4 13.6A2.8 2.8 0 0 0 12 16.4",
        MedalGlyph.SHIELD to "M12 3.2 19.6 5.9V11.9C19.6 16.1 16.6 19.2 12 20.8 7.4 19.2 4.4 16.1 4.4 11.9V5.9Z|~M9.1 11.9 11.3 14.1 15.1 9.9",
        MedalGlyph.TOLL to "M3.2 6.8H20.8|M4.8 6.8V20.4|M19.2 6.8V20.4|~M3 20.4H21M9.6 20.4 11.2 12M14.4 20.4 12.8 12",
        MedalGlyph.PARKING to "M8 4H16A4 4 0 0 1 20 8V16A4 4 0 0 1 16 20H8A4 4 0 0 1 4 16V8A4 4 0 0 1 8 4Z|M9.8 17V7H13A3.3 3.3 0 0 1 13 13.6H9.8",
        MedalGlyph.TARGET to "M20.6 12A8.6 8.6 0 1 1 3.4 12A8.6 8.6 0 1 1 20.6 12Z|M17 12A5 5 0 1 1 7 12A5 5 0 1 1 17 12Z|*M12 9.6A2.4 2.4 0 1 1 12 14.4A2.4 2.4 0 1 1 12 9.6Z",
        MedalGlyph.GAUGE to "M3.8 18.4A8.6 8.6 0 0 1 20.2 18.4|M12 18.4 16.6 12.2|oM12 18.4h0.01|~M12 9.8V11.6|~M4.8 20.8H19.2",
        MedalGlyph.HOURGLASS to "M6.8 3.6V6.2C6.8 8.9 12 10.7 12 12S6.8 15.1 6.8 17.8V20.4|M17.2 3.6V6.2C17.2 8.9 12 10.7 12 12S17.2 15.1 17.2 17.8V20.4|~M5.8 3.6H18.2M5.8 20.4H18.2",
    )
}

/** 解析后的一层：线宽为 0 时按填充画。 */
private class GlyphLayer(val path: Path, val width: Float)

private val MEDAL_LAYERS: Map<MedalGlyph, List<GlyphLayer>> by lazy {
    MedalGlyphSource.ALL.mapValues { (_, src) -> parseGlyph(src) }
}

private fun parseGlyph(src: String): List<GlyphLayer> = src.split('|').map { raw ->
    var text = raw
    var width = W_MAIN
    while (text.isNotEmpty()) {
        when (text[0]) {
            '~' -> { width = W_FINE; text = text.substring(1) }
            'o' -> { width = W_DOT; text = text.substring(1) }
            '*' -> { width = 0f; text = text.substring(1) }
            else -> break
        }
    }
    GlyphLayer(PathParser().parsePathString(text).toPath(), width)
}

/** 在画布里画一个 24×24 归一坐标的 glyph（按画布尺寸自动缩放）。 */
fun DrawScope.drawGlyph(glyph: MedalGlyph, color: Color) {
    val s = size.minDimension / GRID
    withTransform({ scale(s, s, pivot = Offset.Zero) }) { drawGlyphRaw(glyph, color) }
}

/** 24 网格原尺寸绘制，调用方负责缩放。 */
private fun DrawScope.drawGlyphRaw(glyph: MedalGlyph, color: Color) {
    MEDAL_LAYERS[glyph]?.forEach { layer ->
        if (layer.width <= 0f) {
            drawPath(layer.path, color)
        } else {
            drawPath(
                layer.path,
                color,
                style = Stroke(width = layer.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 档位色板
// ---------------------------------------------------------------------------

/**
 * 一枚章要用到的全部颜色。
 *
 * 拆成七个数而不是一个「主色」：桂冠这套工艺靠的是「盘面亮、叶子深、图形更深」的
 * 三层明度差，一个颜色调不出这种层次。
 */
data class MedalPalette(
    /** 盘心（最亮）。 */
    val plate: Color,
    /** 盘面 55% 处。 */
    val plateEdge: Color,
    /** 盘缘（最暗）。 */
    val plateDeep: Color,
    /** 外圈描线。 */
    val ring: Color,
    /** 铸齿虚线圈。 */
    val teeth: Color,
    /** 桂叶与枝干。 */
    val leaf: Color,
    /** 内芯图形。 */
    val ink: Color,
)

/**
 * 桂冠章的色板。
 *
 * 浅色是「抛光金属」：盘心近白、边缘落到档位色，靠明度做出弧度；
 * 深色不能直接把颜色压黑 —— 那样四档会糊成同一种灰，所以只压到 80~90%，
 * 保留档位色相，图形则翻成亮色（刻线里的「填漆」）。
 */
fun medalPalette(tier: MedalTier, dark: Boolean): MedalPalette {
    val hi: Color
    val mid: Color
    val deep: Color
    val ink: Color
    when (tier) {
        MedalTier.BRONZE -> {
            hi = Color(0xFFF6D6AC); mid = Color(0xFFC98A50); deep = Color(0xFFA2642E); ink = Color(0xFF6B3D15)
        }
        MedalTier.SILVER -> {
            hi = Color(0xFFFBFDFF); mid = Color(0xFFC7CDD3); deep = Color(0xFF9AA2AA); ink = Color(0xFF3F464D)
        }
        MedalTier.GOLD -> {
            hi = Color(0xFFFFF1C2); mid = Color(0xFFE5C068); deep = Color(0xFFC1933A); ink = Color(0xFF6B4E12)
        }
        MedalTier.PLATINUM -> {
            hi = Color(0xFFEFF3FF); mid = Color(0xFFC3CDEE); deep = Color(0xFF8F9CD3); ink = Color(0xFF414B7D)
        }
    }
    return if (!dark) {
        MedalPalette(
            plate = Color(0xFFFFFDF8),
            plateEdge = hi,
            plateDeep = mid,
            ring = mid,
            teeth = deep.copy(alpha = 0.34f),
            leaf = deep,
            ink = ink,
        )
    } else {
        MedalPalette(
            plate = lerp(hi, Color.Black, 0.86f),
            plateEdge = lerp(mid, Color.Black, 0.80f),
            plateDeep = lerp(deep, Color.Black, 0.90f),
            ring = mid.copy(alpha = 0.45f),
            teeth = hi.copy(alpha = 0.20f),
            leaf = mid.copy(alpha = 0.90f),
            ink = hi,
        )
    }
}

/** 未解锁：整套降成纸灰，不抢已解锁章的视线。 */
fun lockedMedalPalette(dark: Boolean): MedalPalette = if (!dark) {
    MedalPalette(
        plate = Color(0xFFFDFCF9),
        plateEdge = Color(0xFFF1EEE8),
        plateDeep = Color(0xFFE7E3DA),
        ring = Color(0xFFD3CEC4),
        teeth = Color(0xFFC9C3B8).copy(alpha = 0.6f),
        leaf = Color(0xFFC8C2B7),
        ink = Color(0xFFADA79C),
    )
} else {
    MedalPalette(
        plate = Color(0xFF232220),
        plateEdge = Color(0xFF1B1A17),
        plateDeep = Color(0xFF141311),
        ring = Color(0xFF3A3833),
        teeth = Color(0xFF4A4740),
        leaf = Color(0xFF474440),
        ink = Color(0xFF6E6A62),
    )
}

// ---------------------------------------------------------------------------
// 桂冠工艺
// ---------------------------------------------------------------------------

/** 桂叶：以叶尖为原点朝上（-y），由 `drawWreath` 逐片旋转移位。 */
private val WREATH_LEAF: Path by lazy {
    Path().apply {
        moveTo(0f, -4.6f)
        cubicTo(1.75f, -2.9f, 1.75f, -0.7f, 0f, 1.1f)
        cubicTo(-1.75f, -0.7f, -1.75f, -2.9f, 0f, -4.6f)
        close()
    }
}

private const val DEG = (Math.PI / 180.0).toFloat()

/**
 * 盘面：径向渐变 + 外圈 + 铸齿边 + 桂叶环。
 *
 * [detailed] 为 false 时只留盘面和外圈 —— 22~24dp 的概览位放不下桂叶，
 * 硬画只会得到一圈脏点，那时「看得懂图形」比「工艺完整」重要。
 */
fun DrawScope.drawMedalPlate(pal: MedalPalette, detailed: Boolean = true) {
    val k = size.minDimension / 48f
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(pal.plate, pal.plateEdge, pal.plateDeep),
                center = Offset(17.3f, 13.4f),
                radius = 44f,
            ),
            radius = 22f,
            center = Offset(24f, 24f),
        )
        drawCircle(pal.ring, radius = 22f, center = Offset(24f, 24f), style = Stroke(1f))
        if (!detailed) return@withTransform
        // 铸币齿边：1×2.3 的虚线，远看是一圈细密的亮点
        drawCircle(
            color = pal.teeth,
            radius = 20.3f,
            center = Offset(24f, 24f),
            style = Stroke(
                width = 1.4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 2.3f)),
            ),
        )
        drawWreath(pal.leaf)
    }
}

/** 两侧各 4 片桂叶，从下往上逐渐放大、彼此叠压成一道环；叶尖沿切线向上扫。 */
private fun DrawScope.drawWreath(leaf: Color) {
    for (side in intArrayOf(-1, 1)) {
        for (i in 0 until 4) {
            val th = (36f + i * 17.5f) * DEG
            val x = 24f + side * sin(th) * 14.4f
            val y = 24f - cos(th) * 14.4f
            val tilt = side * (36f + i * 17.5f - 30f)
            val sc = 0.98f + i * 0.07f
            withTransform({
                translate(x, y)
                rotate(tilt, Offset.Zero)
                scale(sc, sc, Offset.Zero)
            }) {
                drawPath(WREATH_LEAF, leaf)
            }
        }
        val r = 12.2f
        drawArc(
            color = leaf.copy(alpha = 0.72f),
            startAngle = if (side < 0) -124f else -56f,
            sweepAngle = if (side < 0) -60f else 60f,
            useCenter = false,
            topLeft = Offset(24f - r, 24f - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 0.85f, cap = StrokeCap.Round),
        )
    }
}

/**
 * 在盘面上画内芯图形。
 *
 * [factor] 是相对 24 网格的放大倍数：桂冠版 0.88（要给桂叶留出呼吸），
 * 概览小图 1.12（桂叶没了，图形要自己撑住盘面）。
 */
fun DrawScope.drawMedalGlyph(glyph: MedalGlyph, color: Color, factor: Float) {
    val k = size.minDimension / 48f
    val s = k * factor
    val t = k * (24f - 12f * factor)
    withTransform({
        translate(t, t)
        scale(s, s, pivot = Offset.Zero)
    }) { drawGlyphRaw(glyph, color) }
}

/**
 * 一枚勋章。
 *
 * [size] 是直径：≥34dp 画完整工艺（桂齿 + 桂叶），更小的概览位只画盘面 + 内芯图形。
 */
@Composable
fun MedalBadge(
    medal: Medal,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val dark = LocalAppColors.current.isDark
    val pal = if (unlocked) medalPalette(medal.tier, dark) else lockedMedalPalette(dark)
    val detailed = size >= 34.dp
    Canvas(modifier.size(size)) {
        drawMedalPlate(pal, detailed)
        drawMedalGlyph(medal.glyph, pal.ink, if (detailed) 0.88f else 1.12f)
    }
}
