package com.leapcontrol.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主题背景场景。
 *
 * 20 套主题全部用 Canvas 实时绘制，没有一张图片、也没有视频文件：
 *  - 静态场景在布局阶段画一次，之后零开销；
 *  - 动态场景只把「时间进度 t」当参数，重绘走 draw 阶段，不触发 recomposition。
 *
 * 两条硬性约束：
 *  1. **所有尺寸都按画布尺寸换算**（`size.width` / `size.height` 的比例），
 *     不能写死 px —— 同一个场景要同时用在 393dp 宽的缩略图和 1080px 宽的首页上。
 *  2. 场景只画在页面上半部分（见 [ThemeBackdrop]），并且刻意压低不透明度，
 *     保证车名 / 续航 / 车模 / 快捷控车压在画面上仍然清楚。
 */
enum class SceneKind(val animated: Boolean) {
    NONE(false),

    // ---- 日间静态 ----
    MIST_HILLS(false),
    CLEAR_SKY(false),
    BLOSSOM(false),
    AUTUMN_WOODS(false),
    SNOW_FIELD(false),
    SEASIDE(false),

    // ---- 日间动态 ----
    CLOUD_DRIFT(true),
    PETAL_FALL(true),
    SUN_DUST(true),
    LIGHT_RAIN(true),

    // ---- 夜间静态 ----
    STARRY(false),
    CITY_NIGHT(false),
    MOON_VALLEY(false),
    NIGHT_SEA(false),
    MILKY_WAY(false),
    NIGHT_HILLS(false),

    // ---- 夜间动态 ----
    AURORA(true),
    METEOR(true),
    FIREFLY(true),
    NIGHT_RAIN(true),
}

/**
 * 一套场景用到的颜色。
 *
 * @param a 主色：山体 / 云影 / 水面
 * @param b 次色：远端层、渐变下半
 * @param c 高光色：阳光、月光、星点、极光
 * @param ink 剪影色：近景山脊、城市轮廓
 */
data class ScenePalette(
    val a: Color,
    val b: Color,
    val c: Color,
    val ink: Color,
)

// ============================================================================
// 公共绘制工具（全部按画布尺寸换算，不写死 px）
// ============================================================================

/** 确定性伪随机：同一个 i 永远得到同一个 0~1，动画不会抖动、每次进入画面一致。 */
private fun rnd(seed: Int, i: Int): Float {
    var x = i * 374761393 + seed * 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return (x and 0x7FFFFFFF).toFloat() / 2147483647f
}

/** 柔和光斑（太阳 / 月亮 / 萤火 / 云团）。中心最亮，外圈化开。 */
private fun DrawScope.softGlow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
    if (radius <= 1f || alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.55f),
                color.copy(alpha = alpha * 0.18f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
}

/**
 * 一条波浪状的山脊 / 海平线，从 baseline 往下填满。
 *
 * @param rim 传了颜色就给山脊画一条描边（月光、极光下的山脊轮廓光）
 */
private fun DrawScope.ridge(
    baseline: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    alpha: Float,
    waves: Float = 1.6f,
    rim: Color? = null,
) {
    val w = size.width
    val h = size.height
    val line = Path()
    val steps = 20
    var prevX = 0f
    var prevY = baseline + sin(phase) * amplitude
    line.moveTo(prevX, prevY)
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        val x = w * f
        val y = baseline + sin(phase + f * waves * 2f * PI.toFloat()) * amplitude
        val midX = (prevX + x) / 2f
        val midY = (prevY + y) / 2f
        line.quadraticBezierTo(prevX, prevY, midX, midY)
        prevX = x
        prevY = y
    }
    line.lineTo(w, prevY)

    val fill = Path().apply {
        addPath(line)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(fill, color, alpha = alpha)

    if (rim != null) {
        drawPath(line, rim, alpha = 0.55f, style = Stroke(width = h * 0.004f))
    }
}

/** 横向雾带：中间浓、两端化开，用来分隔山层。 */
private fun DrawScope.mistBand(cy: Float, height: Float, color: Color, alpha: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
            startY = cy - height / 2f,
            endY = cy + height / 2f,
        ),
        topLeft = Offset(0f, cy - height / 2f),
        size = Size(size.width, height),
    )
}

/**
 * 一朵云：阴影 + 三团白色叠在一起。
 *
 * 白底上画纯白云是看不见的，所以先用 [shade] 在下方铺一层淡影，
 * 再把主体白色压上去，云才有体积。
 */
private fun DrawScope.cloud(
    cx: Float,
    cy: Float,
    scale: Float,
    shade: Color,
    alpha: Float,
) {
    val r = size.width * 0.105f * scale
    softGlow(cx + r * 0.10f, cy + r * 0.34f, r * 1.08f, shade, alpha * 0.85f)
    softGlow(cx - r * 0.70f, cy + r * 0.26f, r * 0.80f, shade, alpha * 0.65f)
    softGlow(cx + r * 0.75f, cy + r * 0.30f, r * 0.82f, shade, alpha * 0.65f)

    softGlow(cx, cy, r, Color.White, alpha)
    softGlow(cx - r * 0.62f, cy + r * 0.08f, r * 0.74f, Color.White, alpha * 0.96f)
    softGlow(cx + r * 0.68f, cy + r * 0.12f, r * 0.80f, Color.White, alpha * 0.92f)
}

/** 一片花瓣 / 落叶：小椭圆，按角度转一下。 */
private fun DrawScope.petal(cx: Float, cy: Float, w: Float, h: Float, deg: Float, color: Color, alpha: Float) {
    rotate(degrees = deg, pivot = Offset(cx, cy)) {
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.5f)),
                start = Offset(cx - w / 2f, cy - h / 2f),
                end = Offset(cx + w / 2f, cy + h / 2f),
            ),
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h),
        )
    }
}

/** 星空点阵：位置与亮度都由 seed 决定。 */
private fun DrawScope.starField(seed: Int, count: Int, alphaScale: Float, t: Float, twinkle: Boolean) {
    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val x = rnd(seed, i) * w
        val y = rnd(seed + 7, i) * h * 0.92f
        val r = w * (0.0014f + rnd(seed + 13, i) * 0.0032f)
        val base = 0.32f + rnd(seed + 21, i) * 0.62f
        val a = if (twinkle) {
            val s = sin(2f * PI.toFloat() * (t + rnd(seed + 31, i)))
            base * (0.55f + 0.45f * s * s)
        } else {
            base
        }
        drawCircle(Color.White, radius = r, center = Offset(x, y), alpha = (a * alphaScale).coerceIn(0f, 1f))
    }
}

// ============================================================================
// 入口
// ============================================================================

/**
 * 把某个场景画到当前画布上。
 *
 * @param t 0~1 循环进度，静态场景忽略它。
 */
internal fun DrawScope.paintScene(kind: SceneKind, t: Float, p: ScenePalette) {
    val w = size.width
    val h = size.height

    when (kind) {
        SceneKind.NONE -> Unit

        // ---------------- 日间静态 ----------------
        SceneKind.MIST_HILLS -> {
            softGlow(w * 0.80f, h * 0.16f, w * 0.46f, p.c, 0.55f)
            ridge(h * 0.48f, h * 0.05f, 0.6f, p.a, 0.30f, waves = 1.1f)
            mistBand(h * 0.56f, h * 0.18f, Color.White, 0.42f)
            ridge(h * 0.62f, h * 0.05f, 2.1f, p.b, 0.34f, waves = 1.4f)
            mistBand(h * 0.72f, h * 0.16f, Color.White, 0.38f)
            ridge(h * 0.78f, h * 0.045f, 4.2f, p.ink, 0.26f, waves = 1.2f)
            ridge(h * 0.90f, h * 0.035f, 1.2f, p.ink, 0.20f, waves = 1.8f)
        }

        SceneKind.CLEAR_SKY -> {
            softGlow(w * 0.18f, h * 0.12f, w * 0.55f, p.c, 0.50f)
            cloud(w * 0.72f, h * 0.26f, 1.20f, p.a, 0.95f)
            cloud(w * 0.26f, h * 0.44f, 0.95f, p.a, 0.85f)
            cloud(w * 0.92f, h * 0.58f, 0.78f, p.a, 0.68f)
            ridge(h * 0.88f, h * 0.025f, 0.2f, p.a, 0.18f, waves = 1.2f)
        }

        SceneKind.BLOSSOM -> {
            softGlow(w * 0.22f, h * 0.14f, w * 0.62f, p.c, 0.48f)
            // 一根斜伸进来的枝条（细一点才不像铁丝）
            val branch = Path().apply {
                moveTo(-w * 0.04f, h * 0.12f)
                cubicTo(w * 0.24f, h * 0.02f, w * 0.52f, h * 0.16f, w * 0.80f, h * 0.06f)
            }
            drawPath(branch, p.ink.copy(alpha = 0.30f), style = Stroke(width = h * 0.008f))
            // 花簇：两三层柔光叠出「一蓬花」，比实心圆更耐看（实心圆看着像珠子）
            for (i in 0 until 13) {
                val f = i / 12f
                val x = w * (-0.02f + 0.82f * f)
                val y = h * (0.11f - 0.05f * sin(f * PI.toFloat()) + (rnd(3, i) - 0.5f) * 0.11f)
                val r = h * (0.020f + rnd(5, i) * 0.014f)
                softGlow(x, y, r * 2.6f, p.c, 0.20f)
                softGlow(x, y, r * 1.15f, p.c, 0.72f)
                softGlow(x - r * 0.35f, y - r * 0.30f, r * 0.55f, Color.White, 0.55f)
                if (i % 2 == 0) {
                    softGlow(x + r * 1.45f, y + r * 0.70f, r * 0.85f, p.c, 0.55f)
                    softGlow(x - r * 1.30f, y + r * 0.85f, r * 0.75f, p.c, 0.45f)
                }
            }
            ridge(h * 0.82f, h * 0.03f, 1.1f, p.a, 0.26f, waves = 1.3f)
            mistBand(h * 0.78f, h * 0.20f, Color.White, 0.40f)
        }

        SceneKind.AUTUMN_WOODS -> {
            softGlow(w * 0.72f, h * 0.14f, w * 0.48f, p.c, 0.52f)
            ridge(h * 0.50f, h * 0.06f, 0.9f, p.a, 0.26f, waves = 1.1f)
            ridge(h * 0.64f, h * 0.055f, 3.4f, p.b, 0.32f, waves = 1.4f)
            mistBand(h * 0.72f, h * 0.16f, Color.White, 0.26f)
            ridge(h * 0.80f, h * 0.04f, 1.7f, p.ink, 0.28f, waves = 1.3f)
            // 飘着的几片叶
            for (i in 0 until 6) {
                val x = w * (0.12f + 0.15f * i) + (rnd(9, i) - 0.5f) * w * 0.06f
                val y = h * (0.28f + rnd(11, i) * 0.28f)
                petal(x, y, w * 0.048f, h * 0.022f, 20f + rnd(13, i) * 60f, p.c, 0.75f)
            }
        }

        SceneKind.SNOW_FIELD -> {
            // 顶部一层冷蓝天空：没有它，白色雪坡压在浅灰底上会「消失」
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(p.b.copy(alpha = 0.55f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.58f,
                ),
            )
            softGlow(w * 0.74f, h * 0.18f, w * 0.42f, p.c, 0.55f)
            // 远山（冷灰蓝，把天空和雪面分开）
            ridge(h * 0.54f, h * 0.05f, 2.6f, p.a, 0.40f, waves = 1.2f)
            // 雪坡：先铺一层冷色阴影，再压白色雪面，才有体积
            ridge(h * 0.70f, h * 0.05f, 0.8f, p.a, 0.32f, waves = 1.1f)
            ridge(h * 0.675f, h * 0.055f, 0.8f, Color.White, 0.90f, waves = 1.1f)
            ridge(h * 0.865f, h * 0.045f, 3.1f, p.a, 0.28f, waves = 1.4f)
            ridge(h * 0.835f, h * 0.045f, 3.1f, Color.White, 0.82f, waves = 1.4f)
            ridge(h * 0.955f, h * 0.026f, 1.4f, p.a, 0.26f, waves = 1.8f)
            // 雪面反光
            for (i in 0 until 30) {
                val x = rnd(17, i) * w
                val y = h * (0.68f + rnd(19, i) * 0.28f)
                drawCircle(
                    Color.White,
                    radius = w * (0.002f + rnd(23, i) * 0.003f),
                    center = Offset(x, y),
                    alpha = 0.6f + rnd(29, i) * 0.4f,
                )
            }
        }

        SceneKind.SEASIDE -> {
            val horizon = h * 0.46f
            softGlow(w * 0.5f, horizon - h * 0.10f, w * 0.55f, p.c, 0.60f)
            // 太阳本体
            drawCircle(Color.White.copy(alpha = 0.55f), radius = w * 0.055f, center = Offset(w * 0.5f, horizon - h * 0.10f))
            // 海面
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(p.b.copy(alpha = 0.52f), p.a.copy(alpha = 0.62f)),
                    startY = horizon,
                    endY = h,
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h - horizon),
            )
            // 海平线
            drawLine(
                Color.White.copy(alpha = 0.45f),
                start = Offset(0f, horizon),
                end = Offset(w, horizon),
                strokeWidth = h * 0.004f,
            )
            // 海面碎光
            for (i in 0 until 20) {
                val f = i / 19f
                val y = horizon + (h - horizon) * f * 0.92f
                val halfW = w * (0.05f + 0.26f * f)
                drawLine(
                    Color.White.copy(alpha = 0.42f * (1f - f * 0.8f)),
                    start = Offset(w * 0.5f - halfW * rnd(31, i), y),
                    end = Offset(w * 0.5f + halfW * rnd(37, i), y),
                    strokeWidth = h * 0.005f,
                )
            }
            ridge(h * 0.93f, h * 0.025f, 0.7f, p.c, 0.35f, waves = 1.2f)
        }

        // ---------------- 日间动态 ----------------
        SceneKind.CLOUD_DRIFT -> {
            softGlow(w * 0.24f, h * 0.14f, w * 0.52f, p.c, 0.52f)
            for (i in 0 until 5) {
                val speed = 0.35f + rnd(41, i) * 0.5f
                val baseX = rnd(43, i) * 1.3f
                val x = (((baseX + t * speed) % 1.3f) - 0.15f) * w
                val y = h * (0.14f + rnd(47, i) * 0.52f)
                cloud(x, y, 0.85f + rnd(53, i) * 0.55f, p.a, 0.88f)
            }
        }

        SceneKind.PETAL_FALL -> {
            softGlow(w * 0.30f, h * 0.12f, w * 0.58f, p.c, 0.42f)
            for (i in 0 until 16) {
                val baseX = rnd(59, i)
                val speed = 0.55f + rnd(61, i) * 0.55f
                val y = ((rnd(67, i) + t * speed) % 1.12f) * h
                val sway = sin(2f * PI.toFloat() * (t * 1.6f + rnd(71, i))) * w * 0.035f
                val x = baseX * w + sway
                petal(
                    x, y,
                    w * (0.036f + rnd(73, i) * 0.020f),
                    h * (0.016f + rnd(79, i) * 0.010f),
                    t * 360f * (0.5f + rnd(83, i)) + i * 24f,
                    p.c, 0.80f,
                )
            }
            ridge(h * 0.90f, h * 0.025f, 1.4f, p.a, 0.22f, waves = 1.3f)
        }

        SceneKind.SUN_DUST -> {
            // 斜射光柱
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.Transparent),
                    start = Offset(w * 0.06f, -h * 0.05f),
                    end = Offset(w * 0.80f, h * 0.85f),
                ),
            )
            softGlow(w * 0.14f, h * 0.08f, w * 0.46f, p.c, 0.55f)
            for (i in 0 until 24) {
                val drift = (rnd(89, i) - t * (0.20f + rnd(97, i) * 0.25f)) % 1.05f
                val y = (if (drift < 0f) drift + 1.05f else drift) * h
                val x = rnd(101, i) * w + sin(2f * PI.toFloat() * (t + rnd(103, i))) * w * 0.02f
                val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t * 1.4f + rnd(107, i))))
                drawCircle(
                    Color.White,
                    radius = w * (0.0026f + rnd(109, i) * 0.0056f),
                    center = Offset(x, y),
                    alpha = 0.95f * pulse,
                )
            }
        }

        SceneKind.LIGHT_RAIN -> {
            softGlow(w * 0.62f, h * 0.16f, w * 0.52f, p.c, 0.34f)
            ridge(h * 0.70f, h * 0.05f, 2.2f, p.a, 0.26f, waves = 1.2f)
            mistBand(h * 0.80f, h * 0.22f, Color.White, 0.34f)
            for (i in 0 until 34) {
                val speed = 1.6f + rnd(113, i) * 1.2f
                val y = ((rnd(127, i) + t * speed) % 1.15f) * h
                val x = rnd(131, i) * w * 1.1f - (y * 0.10f)
                val len = h * (0.045f + rnd(137, i) * 0.045f)
                drawLine(
                    p.ink.copy(alpha = 0.30f + rnd(139, i) * 0.30f),
                    start = Offset(x, y),
                    end = Offset(x - len * 0.22f, y + len),
                    strokeWidth = w * 0.0030f,
                )
            }
        }

        // ---------------- 夜间静态 ----------------
        SceneKind.STARRY -> {
            starField(seed = 11, count = 90, alphaScale = 0.95f, t = 0f, twinkle = false)
            softGlow(w * 0.28f, h * 0.22f, w * 0.58f, p.c, 0.16f)
            softGlow(w * 0.80f, h * 0.16f, w * 0.18f, p.c, 0.38f)
            drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.030f, center = Offset(w * 0.80f, h * 0.16f))
        }

        SceneKind.CITY_NIGHT -> {
            val ground = h * 0.80f
            // 城市上空的灯光雾
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, p.c.copy(alpha = 0.32f)),
                    startY = h * 0.40f,
                    endY = ground,
                ),
                topLeft = Offset(0f, h * 0.40f),
                size = Size(w, ground - h * 0.40f),
            )
            starField(seed = 23, count = 34, alphaScale = 0.6f, t = 0f, twinkle = false)
            // 两排楼，远排更淡
            for (row in 0..1) {
                var x = -w * 0.05f
                var i = 0
                while (x < w) {
                    val bw = w * (0.07f + rnd(149 + row, i) * 0.06f)
                    val bh = h * ((if (row == 0) 0.16f else 0.06f) + rnd(151 + row, i) * 0.26f)
                    val alpha = if (row == 0) 0.30f else 0.55f
                    drawRect(
                        p.ink.copy(alpha = alpha),
                        topLeft = Offset(x, ground - bh),
                        size = Size(bw, bh + h * 0.20f),
                    )
                    if (row == 1) {
                        val cols = (bw / (w * 0.022f)).toInt().coerceAtLeast(1)
                        val rows = (bh / (h * 0.030f)).toInt().coerceAtLeast(1)
                        for (cx in 0 until cols) for (cy in 0 until rows) {
                            if (rnd(157 + cx * 7 + cy * 13, i) > 0.5f) {
                                drawRect(
                                    p.c.copy(alpha = 0.45f + rnd(163, cx + cy * 5) * 0.45f),
                                    topLeft = Offset(
                                        x + bw * 0.14f + cx * (bw * 0.78f / cols),
                                        ground - bh + h * 0.012f + cy * (h * 0.028f),
                                    ),
                                    size = Size(bw * 0.10f, h * 0.011f),
                                )
                            }
                        }
                    }
                    x += bw + w * 0.012f
                    i++
                }
            }
        }

        SceneKind.MOON_VALLEY -> {
            softGlow(w * 0.70f, h * 0.24f, w * 0.36f, p.c, 0.42f)
            drawCircle(Color.White.copy(alpha = 0.90f), radius = w * 0.055f, center = Offset(w * 0.70f, h * 0.24f))
            starField(seed = 29, count = 40, alphaScale = 0.55f, t = 0f, twinkle = false)
            ridge(h * 0.58f, h * 0.05f, 1.4f, p.a, 0.34f, waves = 1.1f, rim = p.c.copy(alpha = 0.35f))
            mistBand(h * 0.66f, h * 0.16f, Color.White, 0.10f)
            ridge(h * 0.74f, h * 0.045f, 3.8f, p.b, 0.55f, waves = 1.3f, rim = p.c.copy(alpha = 0.30f))
            ridge(h * 0.88f, h * 0.035f, 0.6f, p.ink, 0.85f, waves = 1.2f, rim = p.c.copy(alpha = 0.22f))
        }

        SceneKind.NIGHT_SEA -> {
            val horizon = h * 0.50f
            softGlow(w * 0.42f, h * 0.24f, w * 0.32f, p.c, 0.38f)
            drawCircle(Color.White.copy(alpha = 0.80f), radius = w * 0.040f, center = Offset(w * 0.42f, h * 0.24f))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(p.b.copy(alpha = 0.55f), p.ink.copy(alpha = 0.75f)),
                    startY = horizon,
                    endY = h,
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h - horizon),
            )
            // 月光倒影
            for (i in 0 until 16) {
                val f = i / 15f
                val y = horizon + (h - horizon) * f
                val halfW = w * (0.02f + 0.08f * f)
                drawLine(
                    p.c.copy(alpha = 0.55f * (1f - f * 0.7f)),
                    start = Offset(w * 0.42f - halfW, y),
                    end = Offset(w * 0.42f + halfW, y),
                    strokeWidth = h * 0.005f,
                )
            }
            starField(seed = 33, count = 30, alphaScale = 0.5f, t = 0f, twinkle = false)
        }

        SceneKind.MILKY_WAY -> {
            // 银河：沿对角线排一串柔光斑，再撒密集星点（用矩形会因为旋转露出硬边）
            val steps = 9
            for (i in 0 until steps) {
                val f = i / (steps - 1f)
                val x = w * (0.04f + 0.92f * f)
                val y = h * (0.70f - 0.52f * f)
                softGlow(x, y, w * (0.16f + 0.10f * sin(f * PI.toFloat())), p.c, 0.16f)
            }
            starField(seed = 37, count = 70, alphaScale = 0.85f, t = 0f, twinkle = false)
            for (i in 0 until 50) {
                val f = rnd(167, i)
                val x = w * (0.06f + 0.88f * f)
                val y = h * (0.72f - 0.54f * f) + (rnd(173, i) - 0.5f) * h * 0.12f
                drawCircle(
                    Color.White,
                    radius = w * (0.0014f + rnd(179, i) * 0.0030f),
                    center = Offset(x, y),
                    alpha = 0.40f + rnd(181, i) * 0.50f,
                )
            }
        }

        SceneKind.NIGHT_HILLS -> {
            starField(seed = 41, count = 55, alphaScale = 0.65f, t = 0f, twinkle = false)
            softGlow(w * 0.24f, h * 0.18f, w * 0.42f, p.c, 0.22f)
            ridge(h * 0.50f, h * 0.05f, 0.4f, p.a, 0.40f, waves = 1.1f, rim = p.c.copy(alpha = 0.22f))
            ridge(h * 0.62f, h * 0.05f, 2.7f, p.b, 0.55f, waves = 1.3f, rim = p.c.copy(alpha = 0.18f))
            ridge(h * 0.76f, h * 0.04f, 4.6f, p.ink, 0.70f, waves = 1.2f)
            ridge(h * 0.90f, h * 0.03f, 1.9f, p.ink, 0.90f, waves = 1.6f)
        }

        // ---------------- 夜间动态 ----------------
        SceneKind.AURORA -> {
            starField(seed = 43, count = 40, alphaScale = 0.45f, t = t, twinkle = true)
            val bands = listOf(
                Triple(p.c, 0.20f, 0.50f),
                Triple(p.a, 0.34f, 0.42f),
                Triple(p.b, 0.48f, 0.34f),
            )
            bands.forEachIndexed { index, (color, baseY, alpha) ->
                val cy = h * baseY
                val amp = h * (0.05f + index * 0.015f)
                val path = Path()
                val steps = 26
                for (i in 0..steps) {
                    val f = i / steps.toFloat()
                    val x = w * f
                    val y = cy + sin(2f * PI.toFloat() * (f * 1.4f + t * (0.5f + index * 0.2f)) + index) * amp
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (i in steps downTo 0) {
                    val f = i / steps.toFloat()
                    val x = w * f
                    val y = cy + sin(2f * PI.toFloat() * (f * 1.4f + t * (0.5f + index * 0.2f)) + index) * amp -
                        h * 0.26f
                    path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                        startY = cy - h * 0.26f,
                        endY = cy + h * 0.10f,
                    ),
                )
            }
            ridge(h * 0.86f, h * 0.03f, 1.3f, p.ink, 0.85f, waves = 1.3f, rim = p.c.copy(alpha = 0.18f))
        }

        SceneKind.METEOR -> {
            starField(seed = 47, count = 70, alphaScale = 0.75f, t = t, twinkle = true)
            for (i in 0 until 3) {
                // 每颗流星有自己的周期与相位，错开出现
                val phase = (t + rnd(53, i)) % 1f
                if (phase > 0.34f) continue
                val f = phase / 0.34f
                val x0 = w * (0.10f + rnd(59, i) * 0.55f)
                val y0 = h * (0.04f + rnd(61, i) * 0.18f)
                val len = w * (0.24f + rnd(67, i) * 0.18f)
                val dx = len * (0.7f + rnd(71, i) * 0.3f)
                val dy = len * (0.5f + rnd(73, i) * 0.25f)
                val headX = x0 + dx * f
                val headY = y0 + dy * f
                val fade = 1f - f
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.75f * fade), Color.Transparent),
                        start = Offset(headX, headY),
                        end = Offset(headX - dx, headY - dy),
                    ),
                    start = Offset(headX, headY),
                    end = Offset(headX - dx, headY - dy),
                    strokeWidth = h * 0.006f,
                )
                drawCircle(Color.White.copy(alpha = 0.85f * fade), radius = h * 0.006f, center = Offset(headX, headY))
            }
        }

        SceneKind.FIREFLY -> {
            starField(seed = 79, count = 24, alphaScale = 0.35f, t = t, twinkle = true)
            for (i in 0 until 16) {
                val bx = rnd(83, i)
                val by = rnd(89, i)
                val x = w * (bx + 0.10f * sin(2f * PI.toFloat() * (t * (0.3f + rnd(97, i) * 0.4f) + rnd(101, i))))
                val y = h * (by * 0.78f + 0.05f + 0.06f * cos(2f * PI.toFloat() * (t * (0.25f + rnd(103, i) * 0.35f) + rnd(107, i))))
                val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t * (1.2f + rnd(109, i)) + rnd(113, i))))
                softGlow(x, y, w * 0.026f, p.c, 0.55f * pulse)
                drawCircle(p.c.copy(alpha = 0.95f * pulse), radius = w * 0.0055f, center = Offset(x, y))
            }
            ridge(h * 0.80f, h * 0.05f, 0.9f, p.a, 0.55f, waves = 1.3f)
            ridge(h * 0.92f, h * 0.03f, 3.3f, p.ink, 0.85f, waves = 1.2f)
        }

        SceneKind.NIGHT_RAIN -> {
            softGlow(w * 0.72f, h * 0.28f, w * 0.46f, p.c, 0.20f)
            starField(seed = 127, count = 20, alphaScale = 0.30f, t = 0f, twinkle = false)
            ridge(h * 0.86f, h * 0.04f, 1.6f, p.ink, 0.70f, waves = 1.4f)
            for (i in 0 until 38) {
                val speed = 1.8f + rnd(131, i) * 1.3f
                val y = ((rnd(137, i) + t * speed) % 1.15f) * h
                val x = rnd(139, i) * w * 1.12f - y * 0.12f
                val len = h * (0.045f + rnd(149, i) * 0.06f)
                drawLine(
                    p.c.copy(alpha = 0.30f + rnd(151, i) * 0.30f),
                    start = Offset(x, y),
                    end = Offset(x - len * 0.25f, y + len),
                    strokeWidth = w * 0.0022f,
                )
            }
            // 地面湿光
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, p.c.copy(alpha = 0.18f)),
                    startY = h * 0.88f,
                    endY = h,
                ),
                topLeft = Offset(0f, h * 0.88f),
                size = Size(w, h * 0.12f),
            )
        }
    }
}
