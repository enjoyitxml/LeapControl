package com.leapcontrol.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * 夜间扩展场景（新增 4 套静态 + 6 套动态）。
 *
 * 夜间场景的三条额外约定：
 *  · **画面只有上 55% 是真能看见的**（`ThemeBackdrop` 从 30% 起淡出），主体压在 0.3~0.6；
 *  · 亮部（月亮 / 灯 / 雪 / 星）要敢于用白色，否则整块糊成一片；
 *  · **不要用 `palette.ink` 画主体**：它接近纯黑，压在深色渐变上等于隐形。剪影要用
 *    `palette.a / b`（比底色亮一档），或者给 ink 剪影配一圈高光描边（`rim`）。
 */

private const val TWO_PI = 2f * PI.toFloat()

// ============================================================================
// 夜间 · 静态
// ============================================================================

/** 松林夜雾：星空 + 月 + 三排塔形松树，越近越深。 */
internal fun DrawScope.drawPineNight(p: ScenePalette) {
    val w = size.width
    val h = size.height

    starField(seed = 811, count = 58, alphaScale = 0.68f, t = 0f, twinkle = false)
    softGlow(w * 0.74f, h * 0.14f, w * 0.34f, p.c, 0.32f)
    drawCircle(Color.White.copy(alpha = 0.80f), w * 0.026f, Offset(w * 0.74f, h * 0.14f))

    // 地面雾：先压一层，松树才不是「浮在星空里」
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, p.b.copy(alpha = 0.55f)),
            startY = h * 0.44f,
            endY = h * 0.72f,
        ),
        topLeft = Offset(0f, h * 0.44f),
        size = Size(w, h * 0.28f),
    )

    for (row in 0..2) {
        val baseY = h * (0.52f + row * 0.075f)
        val color = when (row) {
            0 -> p.b
            1 -> p.a
            else -> p.a
        }
        val alpha = 0.55f + row * 0.18f
        val count = 7 - row
        for (i in 0 until count) {
            val cx = w * ((i + 0.5f) / count) + (rnd(861 + row, i) - 0.5f) * w * 0.09f
            val treeH = h * (0.11f + row * 0.05f + rnd(871 + row, i) * 0.07f)
            val halfW = treeH * (0.26f + rnd(879 + row, i) * 0.10f)
            pine(cx, baseY, treeH, halfW, color, alpha.coerceAtMost(1f))
        }
    }

    mistBand(h * 0.62f, h * 0.16f, Color.White, 0.06f)
}

/** 一棵塔形松：三段收窄 + 一截树干。夜间剪影只要轮廓对，细节越少越干净。 */
private fun DrawScope.pine(cx: Float, baseY: Float, treeH: Float, halfW: Float, color: Color, alpha: Float) {
    // 树干
    drawLine(
        color,
        start = Offset(cx, baseY),
        end = Offset(cx, baseY - treeH * 0.16f),
        strokeWidth = halfW * 0.22f,
        alpha = alpha,
    )
    val body = Path().apply {
        moveTo(cx, baseY - treeH)
        lineTo(cx + halfW, baseY - treeH * 0.20f)
        lineTo(cx + halfW * 0.52f, baseY - treeH * 0.20f)
        lineTo(cx + halfW * 0.86f, baseY)
        lineTo(cx - halfW * 0.86f, baseY)
        lineTo(cx - halfW * 0.52f, baseY - treeH * 0.20f)
        lineTo(cx - halfW, baseY - treeH * 0.20f)
        close()
    }
    drawPath(body, color, alpha = alpha)
}

/** 雪夜村落：星空、雪坡、几间亮着窗灯的房子。 */
internal fun DrawScope.drawSnowVillage(p: ScenePalette) {
    val w = size.width
    val h = size.height

    starField(seed = 821, count = 52, alphaScale = 0.60f, t = 0f, twinkle = false)
    softGlow(w * 0.24f, h * 0.14f, w * 0.34f, p.c, 0.30f)
    drawCircle(Color.White.copy(alpha = 0.82f), w * 0.025f, Offset(w * 0.24f, h * 0.14f))

    // 远山与雪坡
    ridge(h * 0.40f, h * 0.040f, 1.6f, p.a, 0.42f, waves = 1.2f)
    ridge(h * 0.49f, h * 0.034f, 4.1f, p.b, 0.55f, waves = 1.4f)
    ridge(h * 0.54f, h * 0.028f, 4.1f, Color.White, 0.16f, waves = 1.4f)

    // 房子：墙比背景亮一档，屋顶压深，窗灯是画面里最亮的点
    for (i in 0 until 4) {
        val cx = w * (0.16f + i * 0.22f) + (rnd(881, i) - 0.5f) * w * 0.03f
        val groundY = h * (0.60f + rnd(883, i) * 0.03f)
        val houseW = w * (0.105f + rnd(887, i) * 0.050f)
        val houseH = h * (0.062f + rnd(891, i) * 0.032f)
        val wallTop = groundY - houseH

        drawRect(
            p.a.copy(alpha = 0.95f),
            topLeft = Offset(cx - houseW / 2f, wallTop),
            size = Size(houseW, houseH),
        )
        // 屋顶（三角形，稍微出檐）
        val roof = Path().apply {
            moveTo(cx - houseW * 0.62f, wallTop + h * 0.003f)
            lineTo(cx, wallTop - houseH * 0.60f)
            lineTo(cx + houseW * 0.62f, wallTop + h * 0.003f)
            close()
        }
        drawPath(roof, p.ink)
        // 屋顶积雪：只盖靠上的一小块，别把屋顶整片涂白
        val snowCap = Path().apply {
            moveTo(cx - houseW * 0.40f, wallTop - houseH * 0.18f)
            lineTo(cx, wallTop - houseH * 0.57f)
            lineTo(cx + houseW * 0.40f, wallTop - houseH * 0.18f)
            close()
        }
        drawPath(snowCap, Color.White, alpha = 0.40f)

        // 窗灯：两扇，暖色 + 一圈明显的光晕
        for (k in 0 until 2) {
            val wx = cx + (if (k == 0) -1f else 1f) * houseW * 0.24f
            val wy = wallTop + houseH * 0.46f
            softGlow(wx, wy, w * 0.055f, p.c, 0.70f)
            drawRect(
                p.c,
                topLeft = Offset(wx - w * 0.010f, wy - h * 0.009f),
                size = Size(w * 0.020f, h * 0.018f),
            )
            drawRect(
                Color.White.copy(alpha = 0.75f),
                topLeft = Offset(wx - w * 0.005f, wy - h * 0.005f),
                size = Size(w * 0.010f, h * 0.010f),
            )
        }
    }

    // 近处雪坡把房子「站住」
    ridge(h * 0.66f, h * 0.022f, 2.3f, Color.White, 0.16f, waves = 1.6f)
    for (i in 0 until 28) {
        drawCircle(
            color = Color.White,
            radius = w * (0.0016f + rnd(897, i) * 0.0026f),
            center = Offset(rnd(899, i) * w, h * (0.50f + rnd(901, i) * 0.18f)),
            alpha = 0.26f + rnd(903, i) * 0.38f,
        )
    }
}

/** 孤塔灯塔：海面 + 岬角 + 灯塔与两道光束。 */
internal fun DrawScope.drawLighthouse(p: ScenePalette) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.50f

    starField(seed = 831, count = 44, alphaScale = 0.52f, t = 0f, twinkle = false)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.60f), p.ink.copy(alpha = 0.85f)),
            startY = horizon,
            endY = h,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )
    drawLine(
        Color.White.copy(alpha = 0.18f),
        start = Offset(0f, horizon),
        end = Offset(w, horizon),
        strokeWidth = h * 0.0025f,
    )

    val towerCx = w * 0.70f
    val towerBase = h * 0.62f
    val towerTop = h * 0.30f
    val baseHalf = w * 0.038f
    val topHalf = w * 0.026f
    val lampY = towerTop - h * 0.012f

    // 光束：一道朝向海面的主光（右侧），左侧只留一点余晖 ——
    // 两边等长会画成一个「十字风车」，不像灯塔
    val beams = listOf(-1f to 0.26f, 1f to 0.62f)
    beams.forEach { (dir, strength) ->
        val beam = Path().apply {
            moveTo(towerCx, lampY)
            lineTo(towerCx + dir * w * 0.46f, lampY - h * 0.11f)
            lineTo(towerCx + dir * w * 0.46f, lampY + h * 0.075f)
            close()
        }
        drawPath(
            beam,
            brush = Brush.linearGradient(
                listOf(p.c.copy(alpha = strength), p.c.copy(alpha = strength * 0.12f), Color.Transparent),
                start = Offset(towerCx, lampY),
                end = Offset(towerCx + dir * w * 0.46f, lampY),
            ),
        )
    }

    // 岬角岩体：灯塔站在上面（放高一点，否则全被下缘淡出吃掉）
    ridge(h * 0.635f, h * 0.024f, 2.9f, p.a, 0.95f, waves = 1.1f)

    // 塔身：上窄下宽
    val tower = Path().apply {
        moveTo(towerCx - baseHalf, towerBase)
        lineTo(towerCx - topHalf, towerTop)
        lineTo(towerCx + topHalf, towerTop)
        lineTo(towerCx + baseHalf, towerBase)
        close()
    }
    drawPath(tower, p.a)
    // 塔身右侧受光，一条细高光就够（整面亮会像纸片）
    drawLine(
        Color.White.copy(alpha = 0.20f),
        start = Offset(towerCx + topHalf * 0.72f, towerTop + h * 0.01f),
        end = Offset(towerCx + baseHalf * 0.72f, towerBase - h * 0.01f),
        strokeWidth = w * 0.0026f,
    )
    // 灯室平台
    drawRect(
        p.a,
        topLeft = Offset(towerCx - topHalf * 1.35f, towerTop - h * 0.004f),
        size = Size(topHalf * 2.7f, h * 0.010f),
    )
    softGlow(towerCx, lampY, w * 0.11f, p.c, 0.85f)
    drawCircle(Color.White.copy(alpha = 0.95f), w * 0.012f, Offset(towerCx, lampY))

    // 海面倒影：灯下一列碎光
    for (i in 0 until 12) {
        val f = i / 11f
        val y = horizon + (h * 0.30f) * f
        val halfW = w * (0.012f + 0.045f * f)
        drawLine(
            p.c.copy(alpha = 0.34f * (1f - f * 0.7f)),
            start = Offset(towerCx - halfW, y),
            end = Offset(towerCx + halfW, y),
            strokeWidth = h * 0.0045f,
        )
    }
}

/** 湖映星空：倒映在湖面里的星空。上半真星空，下半是它的「柔化版」。 */
internal fun DrawScope.drawLakeStars(p: ScenePalette) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.42f

    starField(seed = 841, count = 86, alphaScale = 0.88f, t = 0f, twinkle = false)
    // 淡淡一条银河，把上半天撑起来
    for (i in 0 until 7) {
        val f = i / 6f
        softGlow(w * (0.10f + 0.80f * f), h * (0.44f - 0.32f * f), w * (0.13f + 0.07f * sin(f * PI.toFloat())), p.c, 0.14f)
    }

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.70f), p.ink.copy(alpha = 0.90f)),
            startY = horizon,
            endY = h,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

    // 倒影星点：位置比真星空「糊」，半径更大、亮度更低
    for (i in 0 until 48) {
        val x = rnd(911, i) * w
        val y = horizon + rnd(919, i) * (h * 0.34f)
        drawCircle(
            color = Color.White,
            radius = w * (0.0016f + rnd(923, i) * 0.0034f),
            center = Offset(x, y),
            alpha = 0.20f + rnd(929, i) * 0.38f,
        )
    }
    // 湖面横纹
    for (i in 0 until 14) {
        val f = i / 13f
        val y = horizon + (h * 0.36f) * f
        val halfW = w * (0.10f + 0.34f * f)
        drawLine(
            p.c.copy(alpha = 0.12f * (1f - f * 0.6f)),
            start = Offset(w * 0.5f - halfW, y),
            end = Offset(w * 0.5f + halfW, y),
            strokeWidth = h * 0.0030f,
        )
    }
    drawLine(
        Color.White.copy(alpha = 0.16f),
        start = Offset(0f, horizon),
        end = Offset(w, horizon),
        strokeWidth = h * 0.0025f,
    )
}

// ============================================================================
// 夜间 · 动态
// ============================================================================

/** 星轨流转：以天顶为圆心的同心短弧，整体缓慢转。 */
internal fun DrawScope.drawStarTrails(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val cx = w * 0.58f
    val cy = h * 0.06f

    softGlow(cx, cy, w * 0.24f, p.c, 0.24f)
    drawCircle(Color.White.copy(alpha = 0.88f), w * 0.010f, Offset(cx, cy))
    starField(seed = 851, count = 26, alphaScale = 0.45f, t = t, twinkle = true)

    for (i in 0 until 34) {
        val r = w * (0.14f + rnd(861, i) * 0.85f)
        val startAngle = (rnd(867, i) * 360f + t * 26f) % 360f
        val sweep = 16f + rnd(869, i) * 42f
        drawArc(
            color = Color.White.copy(alpha = 0.14f + rnd(871, i) * 0.42f),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = h * (0.0018f + rnd(877, i) * 0.0018f)),
        )
    }

    ridge(h * 0.64f, h * 0.024f, 1.7f, p.ink, 0.92f, waves = 1.3f, rim = p.c.copy(alpha = 0.22f))
}

/** 月影涟漪：月亮 + 水面被风拂动的倒影 + 外扩的涟漪环。 */
internal fun DrawScope.drawMoonRipple(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.42f
    val moonX = w * 0.62f
    val moonY = h * 0.18f

    softGlow(moonX, moonY, w * 0.34f, p.c, 0.42f)
    drawCircle(Color.White.copy(alpha = 0.90f), w * 0.040f, Offset(moonX, moonY))
    starField(seed = 881, count = 34, alphaScale = 0.45f, t = 0f, twinkle = false)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.65f), p.ink.copy(alpha = 0.88f)),
            startY = horizon,
            endY = h,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

    // 倒影：一列宽度呼吸的横线，越往下越宽越淡
    for (i in 0 until 16) {
        val f = i / 15f
        val y = horizon + (h * 0.36f) * f
        val breathe = 0.65f + 0.35f * sin(TWO_PI * (t * 1.1f + i * 0.18f))
        val halfW = w * (0.016f + 0.060f * f) * breathe
        drawLine(
            p.c.copy(alpha = 0.58f * (1f - f * 0.68f)),
            start = Offset(moonX - halfW, y),
            end = Offset(moonX + halfW, y),
            strokeWidth = h * 0.0050f,
        )
    }

    // 涟漪环：三个相位错开的椭圆
    for (i in 0 until 3) {
        val phase = ((t * 1.2f) + i / 3f) % 1f
        val cx = w * (0.30f + rnd(883, i) * 0.40f)
        val cy = horizon + (h * 0.30f) * (0.28f + rnd(887, i) * 0.60f)
        val rx = w * (0.03f + rnd(889, i) * 0.05f) * (0.4f + phase * 1.7f)
        val ry = rx * 0.26f
        drawOval(
            color = p.c.copy(alpha = (1f - phase) * 0.42f),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = h * 0.0030f),
        )
    }

    drawLine(
        Color.White.copy(alpha = 0.14f),
        start = Offset(0f, horizon),
        end = Offset(w, horizon),
        strokeWidth = h * 0.0025f,
    )
}

/** 城市车流：楼群剪影 + 地平线上双向流动的车灯（白去红来）。 */
internal fun DrawScope.drawNightTraffic(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val ground = h * 0.54f

    starField(seed = 891, count = 28, alphaScale = 0.42f, t = 0f, twinkle = false)

    // 楼上方的暖色光雾
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, p.c.copy(alpha = 0.30f)),
            startY = h * 0.16f,
            endY = ground,
        ),
        topLeft = Offset(0f, h * 0.16f),
        size = Size(w, ground - h * 0.16f),
    )

    // 楼群：用 palette.a（比底亮一档）而不是 ink，否则整片楼是黑的
    var x = -w * 0.04f
    var i = 0
    while (x < w && i < 16) {
        val bw = w * (0.07f + rnd(893, i) * 0.06f)
        val bh = h * (0.09f + rnd(897, i) * 0.24f)
        drawRect(
            p.a.copy(alpha = 0.72f),
            topLeft = Offset(x, ground - bh),
            size = Size(bw, bh + h * 0.26f),
        )
        val cols = (bw / (w * 0.024f)).toInt().coerceAtLeast(1)
        val rows = (bh / (h * 0.030f)).toInt().coerceAtLeast(1)
        for (cx in 0 until cols) for (cy in 0 until rows) {
            if (rnd(901 + cx * 7 + cy * 13, i) > 0.50f) {
                drawRect(
                    p.c.copy(alpha = 0.45f + rnd(907 + cx, cy) * 0.50f),
                    topLeft = Offset(
                        x + bw * 0.16f + cx * (bw * 0.74f / cols),
                        ground - bh + h * 0.010f + cy * (h * 0.028f),
                    ),
                    size = Size(bw * 0.10f, h * 0.010f),
                )
            }
        }
        x += bw + w * 0.010f
        i++
    }

    // 路面：用 palette.b 而不是 ink —— 近黑会在画面下半切出一条黑带
    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.90f), p.b.copy(alpha = 0.30f), Color.Transparent),
            startY = ground,
            endY = h * 0.72f,
        ),
        topLeft = Offset(0f, ground),
        size = Size(w, h * 0.18f),
    )

    // 车流：往右是白灯，往左是尾灯红（红是车灯本来的颜色，不跟着主题变）
    val redLight = Color(0xFFFF6B5E)
    for (i in 0 until 16) {
        val lane = i % 2 == 0
        val speed = (if (lane) 0.22f else -0.18f) + rnd(911, i) * (if (lane) 0.16f else -0.14f)
        val raw = (rnd(919, i) + t * speed) % 1.30f
        val fx = (if (raw < 0f) raw + 1.30f else raw) - 0.15f
        val px = w * fx
        val py = ground + h * (0.012f + rnd(923, i) * 0.10f)
        val tail = w * 0.16f * (if (lane) 1f else -1f)
        val headColor = if (lane) Color.White else redLight
        drawLine(
            brush = Brush.linearGradient(
                listOf(headColor.copy(alpha = 0.72f), Color.Transparent),
                start = Offset(px, py),
                end = Offset(px - tail, py),
            ),
            start = Offset(px, py),
            end = Offset(px - tail, py),
            strokeWidth = h * 0.0055f,
        )
        softGlow(px, py, w * 0.030f, headColor, 0.80f)
        drawCircle(Color.White.copy(alpha = if (lane) 0.95f else 0.55f), w * 0.0090f, Offset(px, py))
        drawCircle(headColor, w * 0.014f, Offset(px, py), alpha = 0.45f)
    }
}

/** 夜雪纷飞：雪点下落 + 左右摆动，底下一层雪脊。 */
internal fun DrawScope.drawSnowFall(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height

    starField(seed = 931, count = 20, alphaScale = 0.30f, t = 0f, twinkle = false)
    softGlow(w * 0.70f, h * 0.16f, w * 0.40f, p.c, 0.20f)

    for (i in 0 until 64) {
        val speed = 0.32f + rnd(937, i) * 0.42f
        val y = ((rnd(941, i) + t * speed) % 0.78f) * h
        val sway = sin(TWO_PI * (t * 0.8f + rnd(943, i))) * w * 0.028f
        val x = rnd(947, i) * w + sway
        val r = w * (0.0018f + rnd(953, i) * 0.0034f)
        drawCircle(
            color = Color.White,
            radius = r,
            center = Offset(x, y),
            alpha = 0.34f + rnd(957, i) * 0.58f,
        )
    }

    ridge(h * 0.70f, h * 0.028f, 1.3f, p.a, 0.50f, waves = 1.4f)
    ridge(h * 0.725f, h * 0.024f, 1.3f, Color.White, 0.18f, waves = 1.4f)
}

/** 云遮明月：大月亮后面暗云缓缓掠过，偶尔把月亮挡掉一半。 */
internal fun DrawScope.drawCloudMoon(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val moonX = w * 0.58f
    val moonY = h * 0.22f

    softGlow(moonX, moonY, w * 0.42f, p.c, 0.45f)
    drawCircle(Color.White.copy(alpha = 0.90f), w * 0.044f, Offset(moonX, moonY))

    // 暗云：先铺一层深的（p.b），再叠一层浅的（p.a）做出体积。
    // 夜间云不能画白色 —— 白色 softGlow 会把月亮糊成一团灰。
    for (i in 0 until 7) {
        val speed = 0.05f + rnd(961, i) * 0.09f
        val fx = ((rnd(967, i) + t * speed) % 1.40f) - 0.20f
        val cx = w * fx
        val cy = h * (0.10f + rnd(971, i) * 0.26f)
        val r = w * (0.13f + rnd(977, i) * 0.12f)
        softGlow(cx, cy + r * 0.12f, r * 1.05f, p.b, 0.80f)
        softGlow(cx - r * 0.35f, cy + r * 0.05f, r * 0.80f, p.b, 0.70f)
        softGlow(cx + r * 0.30f, cy, r * 0.72f, p.a, 0.62f)
        softGlow(cx - r * 0.55f, cy + r * 0.10f, r * 0.55f, p.a, 0.55f)
        // 云顶一点冷光，云才有边界（否则就是一团糊）
        softGlow(cx + r * 0.10f, cy - r * 0.30f, r * 0.42f, p.c, 0.14f)
    }

    ridge(h * 0.64f, h * 0.026f, 1.1f, p.ink, 0.90f, waves = 1.3f, rim = p.c.copy(alpha = 0.18f))
}

/** 潮汐夜浪：一排排浪线向岸边推进，浪尖带碎光。 */
internal fun DrawScope.drawNightWaves(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.42f

    starField(seed = 981, count = 34, alphaScale = 0.45f, t = 0f, twinkle = false)
    softGlow(w * 0.34f, h * 0.17f, w * 0.30f, p.c, 0.28f)
    drawCircle(Color.White.copy(alpha = 0.72f), w * 0.024f, Offset(w * 0.34f, h * 0.17f))

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.62f), p.ink.copy(alpha = 0.92f)),
            startY = horizon,
            endY = h,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

    val steps = 26
    for (i in 0 until 9) {
        val f = i / 8f
        val baseY = horizon + (h * 0.40f) * (0.06f + f * 0.94f)
        val amp = h * (0.004f + f * 0.014f)
        val wave = Path()
        for (k in 0..steps) {
            val g = k / steps.toFloat()
            val y = baseY + sin(TWO_PI * (g * (1.1f + f) + t * (0.55f + f * 0.30f) + i * 0.12f)) * amp
            if (k == 0) wave.moveTo(w * g, y) else wave.lineTo(w * g, y)
        }
        drawPath(
            wave,
            p.c.copy(alpha = 0.24f + f * 0.30f),
            style = Stroke(width = h * (0.0022f + f * 0.0026f)),
        )
    }

    // 浪尖碎光：集中在近处
    for (i in 0 until 24) {
        val f = 0.30f + rnd(983, i) * 0.66f
        val y = horizon + (h * 0.40f) * f
        val x = w * (0.04f + rnd(987, i) * 0.92f)
        drawCircle(
            color = Color.White,
            radius = w * (0.0014f + rnd(991, i) * 0.0028f),
            center = Offset(x, y),
            alpha = 0.24f + rnd(997, i) * 0.42f,
        )
    }
}
