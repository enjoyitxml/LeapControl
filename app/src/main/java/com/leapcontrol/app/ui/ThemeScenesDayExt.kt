package com.leapcontrol.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 日间扩展场景（新增 4 套静态 + 6 套动态）。
 *
 * 与 [ThemeScenes] 里原有 20 套共用同一组绘制工具（[softGlow] / [ridge] / [mistBand] /
 * [cloud] / [petal]），所以观感一致：**尺寸全部按画布比例算，颜色全部来自 [ScenePalette]**，
 * 换主题时只要换配色，不用改绘制代码。
 *
 * 两条踩过坑的经验（改这些场景前先读）：
 *  1. **画面只有上 55% 是真能看见的**：`ThemeBackdrop` 从 30% 处开始淡出，74% 只剩 0.22。
 *     主体（山、麦田、房子）压到 0.3~0.6 才有存在感，铺到 0.9 等于白画。
 *  2. **浅色底上不要画白色**：白雾、白光斑、白绒球在浅底上直接消失。要"亮"就提高
 *     已有色的不透明度或改用 `palette.a / b / ink`，白色只留给小面积高光点。
 *
 * 动态场景只用 `t`（0~1 循环）当唯一变量，不保存状态 —— 这样 30fps 节流、切后台暂停、
 * 滑动时定格全都不用额外处理。
 */

private const val TWO_PI = 2f * PI.toFloat()

// ============================================================================
// 日间 · 静态
// ============================================================================

/**
 * 麦浪金黄：远山 + 三层麦秆，越近越高越浓。
 *
 * 麦秆用细线，穗头用一段加粗线 —— 画成椭圆会变成「一地珠子」。
 */
internal fun DrawScope.drawWheatField(p: ScenePalette) {
    val w = size.width
    val h = size.height
    softGlow(w * 0.78f, h * 0.14f, w * 0.48f, p.c, 0.55f)
    ridge(h * 0.34f, h * 0.040f, 1.8f, p.a, 0.26f, waves = 1.1f)
    mistBand(h * 0.40f, h * 0.14f, Color.White, 0.30f)
    // 麦田的「底」：先铺一层暖色坡，麦秆才不会浮在空气里
    ridge(h * 0.46f, h * 0.028f, 0.7f, p.b, 0.24f, waves = 1.2f)

    for (layer in 0..2) {
        val baseY = h * (0.46f + layer * 0.085f)
        val tint = when (layer) {
            0 -> p.b
            1 -> p.a
            else -> p.ink
        }
        val alpha = 0.34f + layer * 0.18f
        val count = 24 + layer * 8
        for (i in 0 until count) {
            val x = w * ((i + 0.5f) / count) + (rnd(211 + layer, i) - 0.5f) * w * 0.045f
            val len = h * (0.070f + rnd(231 + layer, i) * 0.085f) * (1f + layer * 0.14f)
            val lean = (rnd(251 + layer, i) - 0.5f) * w * 0.035f
            drawLine(
                tint.copy(alpha = alpha),
                start = Offset(x, baseY),
                end = Offset(x + lean, baseY - len),
                strokeWidth = w * 0.0032f,
            )
            // 穗头：秆顶一小段明显加粗
            drawLine(
                tint.copy(alpha = (alpha * 1.3f).coerceAtMost(1f)),
                start = Offset(x + lean * 0.82f, baseY - len * 0.84f),
                end = Offset(x + lean, baseY - len),
                strokeWidth = w * 0.0105f,
            )
        }
    }

    // 穗尖反光：一点点暖白点，让麦田不平板
    for (i in 0 until 30) {
        drawCircle(
            color = Color.White,
            radius = w * (0.0022f + rnd(291, i) * 0.0034f),
            center = Offset(rnd(271, i) * w, h * (0.40f + rnd(281, i) * 0.30f)),
            alpha = 0.35f + rnd(301, i) * 0.40f,
        )
    }
}

/** 湖光倒影：对岸两层山 + 平静湖面 + 倒影与横纹碎光。 */
internal fun DrawScope.drawLakeMirror(p: ScenePalette) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.38f

    softGlow(w * 0.24f, h * 0.12f, w * 0.46f, p.c, 0.52f)
    ridge(h * 0.32f, h * 0.045f, 2.4f, p.a, 0.38f, waves = 1.2f)
    ridge(horizon, h * 0.032f, 4.8f, p.ink, 0.30f, waves = 1.5f)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.62f), p.a.copy(alpha = 0.40f), Color.Transparent),
            startY = horizon,
            endY = h * 0.86f,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h * 0.86f - horizon),
    )
    // 倒影：同一条脊线压在水面下方，透明度更低就够「像倒影」
    ridge(horizon + h * 0.025f, h * 0.045f, 2.4f, p.ink, 0.16f, waves = 1.2f)

    for (i in 0 until 20) {
        val f = i / 19f
        val y = horizon + (h * 0.42f) * f
        val halfW = w * (0.08f + 0.30f * f)
        drawLine(
            Color.White.copy(alpha = 0.34f * (1f - f * 0.55f)),
            start = Offset(w * 0.5f - halfW * rnd(311, i), y),
            end = Offset(w * 0.5f + halfW * rnd(317, i), y),
            strokeWidth = h * 0.0035f,
        )
    }
    ridge(h * 0.74f, h * 0.018f, 0.9f, p.c, 0.26f, waves = 1.4f)
}

/** 竹影清风：一排竹竿（带竹节）+ 稀疏竹叶 + 底部薄雾。 */
internal fun DrawScope.drawBambooGrove(p: ScenePalette) {
    val w = size.width
    val h = size.height

    softGlow(w * 0.62f, h * 0.08f, w * 0.58f, p.c, 0.44f)

    // 竿的位置刻意不均匀：等距排开像篱笆，不像竹林
    val positions = floatArrayOf(0.05f, 0.13f, 0.27f, 0.38f, 0.52f, 0.65f, 0.79f, 0.93f)

    for (i in positions.indices) {
        val x = w * positions[i] + (rnd(331, i) - 0.5f) * w * 0.02f
        val sway = (rnd(337, i) - 0.5f) * w * 0.022f
        val topY = h * (0.01f + rnd(341, i) * 0.06f)
        val bottomY = h * 0.84f
        // 前景几根加粗、加深，才有纵深
        val near = i == 1 || i == 4 || i == 6
        val thick = w * (if (near) 0.0125f else 0.0070f) * (0.85f + rnd(347, i) * 0.30f)
        val color = if (near) p.ink else p.a
        val alpha = if (near) 0.52f else 0.30f + rnd(349, i) * 0.16f

        val stalk = Path().apply {
            moveTo(x, bottomY)
            quadraticBezierTo(x + sway * 0.35f, h * 0.45f, x + sway, topY)
        }
        drawPath(stalk, color.copy(alpha = alpha), style = Stroke(width = thick))

        // 竹节：越往上越密，看着才像竹子
        var ny = bottomY
        var k = 0
        while (ny > topY && k < 12) {
            val span = bottomY - topY
            val step = h * (0.060f + rnd(353 + k, i) * 0.026f)
            ny -= step
            val ratio = ((bottomY - ny) / span).coerceIn(0f, 1f)
            val gx = x + sway * ratio
            drawLine(
                color.copy(alpha = (alpha * 1.35f).coerceAtMost(1f)),
                start = Offset(gx - thick * 1.05f, ny),
                end = Offset(gx + thick * 1.05f, ny),
                strokeWidth = thick * 0.50f,
            )
            k++
        }

        // 竹叶：**叶根必须落在竿上**，成簇向一侧下垂。
        // 叶子一旦离开竿到处飘，整幅画就变成「芦苇堆」而不是竹林。
        for (j in 0 until 4) {
            val ly = topY + h * (0.02f + rnd(359 + j, i) * 0.11f)
            val span = bottomY - topY
            val lx = x + sway * ((bottomY - ly) / span).coerceIn(0f, 1f)
            val len = w * (0.055f + rnd(373 + j, i) * 0.045f)
            val side = if (j % 2 == 0) 1f else -1f
            petal(
                lx + side * len * 0.46f, ly + len * 0.14f, len, len * 0.15f,
                side * (34f + rnd(367 + j, i) * 26f),
                if (j % 2 == 0) p.ink else p.a, alpha * 0.85f,
            )
        }
    }

    mistBand(h * 0.62f, h * 0.20f, Color.White, 0.42f)
}

/** 沙丘暖阳：一轮白日 + 四层沙丘，脊线用亮色勾出来。 */
internal fun DrawScope.drawDesertDune(p: ScenePalette) {
    val w = size.width
    val h = size.height

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.55f), Color.Transparent),
            startY = 0f,
            endY = h * 0.40f,
        ),
        size = Size(w, h * 0.40f),
    )
    softGlow(w * 0.78f, h * 0.12f, w * 0.52f, p.c, 0.65f)
    drawCircle(
        color = Color.White.copy(alpha = 0.62f),
        radius = w * 0.045f,
        center = Offset(w * 0.78f, h * 0.12f),
    )

    ridge(h * 0.42f, h * 0.040f, 0.9f, p.a, 0.26f, waves = 0.9f, rim = p.c)
    ridge(h * 0.52f, h * 0.038f, 3.1f, p.b, 0.32f, waves = 1.1f, rim = p.c)
    ridge(h * 0.62f, h * 0.045f, 5.4f, p.a, 0.38f, waves = 1.3f, rim = p.c)
    ridge(h * 0.73f, h * 0.034f, 2.2f, p.ink, 0.32f, waves = 1.6f, rim = p.c)

    // 风纹：沙面上几道短线（够远看不出是直线）
    for (i in 0 until 26) {
        val f = rnd(381, i)
        val x = w * (0.04f + rnd(383, i) * 0.90f)
        val y = h * (0.44f + rnd(389, i) * 0.26f)
        val len = w * (0.03f + f * 0.06f)
        drawLine(
            p.ink.copy(alpha = 0.12f + rnd(397, i) * 0.14f),
            start = Offset(x, y),
            end = Offset(x + len, y - len * 0.10f),
            strokeWidth = h * 0.0026f,
        )
    }
}

// ============================================================================
// 日间 · 动态
// ============================================================================

/** 涟漪湖面：水面上一圈圈向外扩的涟漪 + 缓慢左右滑的波光。 */
internal fun DrawScope.drawRipplingLake(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.34f

    softGlow(w * 0.30f, h * 0.11f, w * 0.50f, p.c, 0.50f)
    ridge(h * 0.29f, h * 0.045f, 1.4f, p.a, 0.34f, waves = 1.1f)
    ridge(horizon, h * 0.028f, 4.2f, p.ink, 0.28f, waves = 1.5f)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.b.copy(alpha = 0.60f), p.a.copy(alpha = 0.44f), Color.Transparent),
            startY = horizon,
            endY = h * 0.82f,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h * 0.82f - horizon),
    )

    // 涟漪：每个都是从某点向外扩的椭圆环，扩到边界就淡掉
    for (i in 0 until 14) {
        val phase = ((t * 0.8f) + rnd(397, i)) % 1f
        val cx = w * (0.06f + rnd(383, i) * 0.88f)
        val cy = horizon + (h * 0.46f) * (0.06f + rnd(389, i) * 0.88f)
        val rx = w * (0.022f + rnd(401, i) * 0.048f) * (0.35f + phase * 1.8f)
        val ry = rx * 0.30f
        drawOval(
            color = Color.White.copy(alpha = (1f - phase) * 0.55f),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = h * 0.0034f),
        )
    }

    for (i in 0 until 14) {
        val f = i / 17f
        val y = horizon + (h * 0.46f) * f
        val slide = sin(TWO_PI * (t * 0.7f + rnd(409, i))) * w * 0.030f
        val halfW = w * (0.05f + 0.22f * f)
        drawLine(
            Color.White.copy(alpha = 0.40f * (1f - f * 0.5f)),
            start = Offset(w * 0.5f - halfW + slide, y),
            end = Offset(w * 0.5f + halfW * 0.75f + slide, y),
            strokeWidth = h * 0.0034f,
        )
    }
}

/** 飞鸟掠空：云缓慢漂 + 一群雁横掠（翅膀按正弦扇动）。 */
internal fun DrawScope.drawBirdsFly(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height

    softGlow(w * 0.20f, h * 0.10f, w * 0.50f, p.c, 0.48f)
    cloud(w * 0.74f, h * 0.20f, 1.10f, p.a, 0.85f)
    cloud(w * 0.28f, h * 0.36f, 0.80f, p.a, 0.62f)

    for (i in 0 until 7) {
        val speed = 0.24f + rnd(419, i) * 0.13f
        val fx = ((rnd(421, i) + t * speed) % 1.26f) - 0.13f
        val x = w * fx
        // 排队：让几只挤在同一条斜线上，像雁阵而不是随机撒点
        val y = h * (0.13f + rnd(431, i) * 0.22f) + i * h * 0.014f +
            sin(TWO_PI * (t * 1.4f + rnd(433, i))) * h * 0.012f
        val s = w * (0.026f + rnd(437, i) * 0.018f)
        val flap = sin(TWO_PI * (t * 2.2f + rnd(439, i)))
        val wing = Path().apply {
            moveTo(x - s, y + s * 0.45f * flap)
            quadraticBezierTo(x - s * 0.45f, y - s * 0.32f, x, y)
            quadraticBezierTo(x + s * 0.45f, y - s * 0.32f, x + s, y + s * 0.45f * flap)
        }
        drawPath(wing, p.ink.copy(alpha = 0.78f), style = Stroke(width = w * 0.0060f))
    }

    ridge(h * 0.62f, h * 0.020f, 1.2f, p.a, 0.26f, waves = 1.3f)
}

/** 蒲公英舞：一团团白色种子边升边摆，越往上越淡。 */
internal fun DrawScope.drawDandelion(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height

    softGlow(w * 0.78f, h * 0.13f, w * 0.50f, p.c, 0.52f)
    ridge(h * 0.66f, h * 0.026f, 2.1f, p.a, 0.24f, waves = 1.3f)
    ridge(h * 0.73f, h * 0.020f, 4.4f, p.ink, 0.18f, waves = 1.5f)

    // 母株：一根弯曲的茎 + 顶端绒球，让满天飞絮有「出处」
    val stemTopX = w * 0.20f
    val stemTopY = h * 0.31f
    val stem = Path().apply {
        moveTo(w * 0.13f, h * 0.58f)
        quadraticBezierTo(w * 0.13f, h * 0.40f, stemTopX, stemTopY)
    }
    drawPath(stem, p.ink.copy(alpha = 0.42f), style = Stroke(width = w * 0.0042f))
    // 伞状绒球：9 根丝向上张开，中心一个白球
    for (k in 0 until 9) {
        val ang = -PI.toFloat() * 0.95f + k * 0.24f
        drawLine(
            p.ink.copy(alpha = 0.42f),
            start = Offset(stemTopX, stemTopY),
            end = Offset(stemTopX + cos(ang) * w * 0.050f, stemTopY + sin(ang) * w * 0.042f),
            strokeWidth = w * 0.0020f,
        )
        drawCircle(
            Color.White.copy(alpha = 0.75f),
            w * 0.0035f,
            Offset(stemTopX + cos(ang) * w * 0.050f, stemTopY + sin(ang) * w * 0.042f),
        )
    }
    softGlow(stemTopX, stemTopY, w * 0.055f, Color.White, 0.90f)
    drawCircle(Color.White.copy(alpha = 0.95f), w * 0.0090f, Offset(stemTopX, stemTopY))
    drawCircle(p.ink.copy(alpha = 0.32f), w * 0.0040f, Offset(stemTopX, stemTopY))

    for (i in 0 until 14) {
        // 种子从底部升起：先出现在 0.72 附近，升到 0.1 左右淡出
        val rise = ((rnd(449, i) + t * (0.12f + rnd(457, i) * 0.14f)) % 1f)
        val y = h * (0.74f - rise * 0.62f)
        val x = w * (0.04f + rnd(461, i) * 0.92f) +
            sin(TWO_PI * (t * 0.9f + rnd(463, i))) * w * 0.036f
        val r = w * (0.0090f + rnd(467, i) * 0.0055f)
        val fade = (1f - rise * 0.55f).coerceIn(0.25f, 1f)

        // 伞丝：向上张开成小伞（不是全方位星芒，那样像雪花）。
        // 用 ink 才看得见 —— 纯白细线在浅绿底上直接消失
        val spin = t * 1.6f + i * 0.7f
        for (k in 0 until 5) {
            val spread = -PI.toFloat() / 2f + (k - 2) * 0.42f
            val ang = spread + sin(spin) * 0.10f
            drawLine(
                p.ink.copy(alpha = 0.56f * fade),
                start = Offset(x, y),
                end = Offset(x + cos(ang) * r * 3.2f, y + sin(ang) * r * 3.2f),
                strokeWidth = w * 0.0022f,
            )
        }
        // 绒球：白色小球 + 一点深色芯，白球才有边界
        softGlow(x, y, r * 2.6f, Color.White, 0.70f * fade)
        drawCircle(Color.White.copy(alpha = 0.95f * fade), r, Offset(x, y))
        drawCircle(p.ink.copy(alpha = 0.28f * fade), r * 0.42f, Offset(x, y))
    }
}

/** 晨雾流动：三层山 + 十几团雾横向缓慢漂移。 */
internal fun DrawScope.drawMistFlow(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height

    softGlow(w * 0.30f, h * 0.09f, w * 0.55f, p.c, 0.40f)
    ridge(h * 0.36f, h * 0.045f, 0.8f, p.a, 0.34f, waves = 1.1f)
    ridge(h * 0.50f, h * 0.040f, 3.1f, p.b, 0.40f, waves = 1.3f)
    ridge(h * 0.64f, h * 0.032f, 1.5f, p.ink, 0.30f, waves = 1.5f)

    // 雾：浅灰蓝的柔光团（不能用纯白，浅底上会消失）
    for (i in 0 until 13) {
        val speed = 0.08f + rnd(479, i) * 0.12f
        val fx = ((rnd(487, i) + t * speed) % 1.42f) - 0.21f
        val y = h * (0.28f + rnd(491, i) * 0.34f)
        val r = w * (0.13f + rnd(499, i) * 0.15f)
        softGlow(w * fx, y, r, p.b, 0.34f)
        softGlow(w * fx + r * 0.35f, y - r * 0.10f, r * 0.62f, Color.White, 0.30f)
    }
}

/** 树影斑驳：斜射阳光 + 满地叶隙光斑（呼吸）+ 几片晃动的叶影。 */
internal fun DrawScope.drawTreeShadow(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height

    drawRect(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.Transparent),
            start = Offset(w * 0.10f, -h * 0.05f),
            end = Offset(w * 0.85f, h * 0.75f),
        ),
    )
    softGlow(w * 0.14f, h * 0.05f, w * 0.44f, p.c, 0.55f)

    // 枝干：两根斜线，别画多，多了像电线
    drawLine(
        p.ink.copy(alpha = 0.32f),
        start = Offset(-w * 0.02f, h * 0.03f),
        end = Offset(w * 0.34f, h * 0.24f),
        strokeWidth = h * 0.007f,
    )
    drawLine(
        p.ink.copy(alpha = 0.26f),
        start = Offset(w * 0.10f, h * 0.01f),
        end = Offset(w * 0.30f, h * 0.16f),
        strokeWidth = h * 0.005f,
    )

    // 叶隙光斑：白心 + 暖色外圈，明暗呼吸
    for (i in 0 until 16) {
        val pulse = 0.5f + 0.5f * sin(TWO_PI * (t * (0.4f + rnd(503, i) * 0.5f) + rnd(509, i)))
        val x = w * (0.03f + rnd(521, i) * 0.94f) +
            sin(TWO_PI * (t * 0.35f + rnd(523, i))) * w * 0.022f
        val y = h * (0.05f + rnd(527, i) * 0.55f)
        val r = w * (0.030f + rnd(541, i) * 0.048f)
        softGlow(x, y, r, p.c, 0.18f + 0.26f * pulse)
        drawCircle(
            Color.White.copy(alpha = 0.22f + 0.30f * pulse),
            r * 0.55f,
            Offset(x, y),
        )
    }

    // 叶影：比底色深的暗块，晃动
    for (i in 0 until 16) {
        val x = w * (0.02f + rnd(547, i) * 0.96f) +
            sin(TWO_PI * (t * 0.55f + rnd(549, i))) * w * 0.026f
        val y = h * (0.08f + rnd(551, i) * 0.58f)
        petal(
            x, y,
            w * (0.055f + rnd(553, i) * 0.045f),
            w * (0.030f + rnd(557, i) * 0.026f),
            25f + rnd(559, i) * 70f + t * 8f,
            p.ink, 0.16f,
        )
    }
}

/** 云海日出：太阳在云上呼吸 + 云海横向漂。 */
internal fun DrawScope.drawSunriseGlow(t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height
    val sunY = h * 0.34f
    val breath = 0.94f + 0.06f * sin(TWO_PI * t)

    softGlow(w * 0.42f, sunY, w * 0.58f * breath, p.c, 0.62f)
    softGlow(w * 0.42f, sunY, w * 0.28f * breath, p.c, 0.55f)
    drawCircle(
        color = Color.White.copy(alpha = 0.62f),
        radius = w * 0.048f,
        center = Offset(w * 0.42f, sunY),
    )

    for (i in 0 until 9) {
        val speed = 0.07f + rnd(563, i) * 0.10f
        val fx = ((rnd(569, i) + t * speed) % 1.42f) - 0.21f
        val y = h * (0.38f + rnd(571, i) * 0.20f)
        cloud(w * fx, y, 0.85f + rnd(573, i) * 0.55f, p.a, 0.82f)
    }

    ridge(h * 0.64f, h * 0.038f, 1.1f, p.a, 0.34f, waves = 1.2f)
    ridge(h * 0.72f, h * 0.030f, 3.4f, p.ink, 0.24f, waves = 1.4f)
}
