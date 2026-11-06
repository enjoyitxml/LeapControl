package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 1° 的弧度值。用常量而不是 `PI / 180`：后者是 Double，会把整条计算链拖进 Double。 */
private const val DEG_TO_RAD = 0.017453292f

/** 2π。同上，避免顺手写 `PI.toFloat() * 2` 时漏掉后缀。 */
private const val TAU = 6.2831855f

/**
 * 车模行驶动效（个性化 → 车模外观 → 行驶动效 / 首页共用同一份绘制）。
 *
 * ## 只有两个元素
 *
 * 1. **车身抖动** —— 车模整体在 `graphicsLayer` 里做十几分之一像素级的位移 +
 *    极小的俯仰，外加每 0.7 秒一次短促下沉（过路面接缝那种「顿一下」）。
 * 2. **风** —— 全屏速度线盖在车身上，从**左往右**掠过（车模朝左，车往左开，
 *    空气相对车往右走），另在车头前方留 5 条短须。车速越快，线越亮、越长、越快。
 *
 * ## 性能（首页是常驻页面，这一条比好看重要）
 *
 * - [CarMotionSettings.animatesAt] 为假（静止 / 没速度信号）时**一次 `withFrameNanos`
 *   都不申请**，也没有任何绘制。
 * - 滚动时由 [paused] 停帧：沿用首页「滑动只更新平移矩阵」的老规矩。
 * - 抖动在 `graphicsLayer` 的 draw lambda 里算，风在 Canvas 的 draw lambda 里算，
 *   两者都只读时钟 State → 只失效绘制，不触发重组。
 * - 粒子池只有 40 条线，不分配位图、不做离屏缓冲。
 */
@Composable
internal fun CarMotionStage(
    /** 当前车速（km/h）；null / 低于阈值时整层静止。 */
    speedKmh: Float?,
    settings: CarMotionSettings,
    /** 首页滚动时停帧；设置页预览传恒 false。 */
    paused: State<Boolean>,
    /** 深色主题：风线要浅色，浅底上要换成偏深的蓝灰，否则看不见。 */
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // 帧时钟：draw 阶段读它 → 只失效绘制，不重组
    val clock = remember { mutableLongStateOf(0L) }
    val engine = remember { WindEngine() }
    val animating = settings.animatesAt(speedKmh)
    val speed = speedKmh ?: 0f

    LaunchedEffect(animating, paused) {
        if (!animating) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            if (paused.value) {
                // 滚动 / 不可见：不申请新帧，省电；恢复时重新取时间基准，避免相位跳一大段
                lastNs = 0L
                delay(120)
                continue
            }
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    engine.step(dt, speed, settings.windAngle)
                }
                lastNs = now
                clock.longValue = now
            }
        }
    }

    Box(modifier) {
        // 车模本体：抖动只加在这一层，风盖在它上面（见下面的 Canvas）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 滚动中要显式归零：layer 的属性不会自动复位，直接 return 会把
                    // 上一帧的偏移留在车上，滑回来时车模歪着停在卡片里。
                    if (!animating || paused.value) {
                        translationX = 0f
                        translationY = 0f
                        rotationZ = 0f
                        return@graphicsLayer
                    }
                    val t = clock.longValue / 1_000_000_000f
                    if (t <= 0f) return@graphicsLayer
                    val off = carShake(t, speed, settings.shakeDp)
                    translationX = off.x.toDp().toPx()
                    translationY = off.y.toDp().toPx()
                    rotationZ = off.rotation
                },
        ) {
            content()
        }
        Canvas(Modifier.fillMaxSize()) {
            // 暂停时也要重绘一次（读 paused → 失效）：Canvas 每帧从透明重画，
            // 这一帧什么都不画就等于把已经画上的风线擦掉，不会留一层"静止的风"。
            if (!animating || paused.value) return@Canvas
            // 读一下帧时钟：让 Canvas 订阅帧推进（只读到 draw 阶段，不会重组）
            engine.draw(this, clock.longValue / 1_000_000_000f, speed, settings.windAngle, dark)
        }
    }
}

/** 一帧的抖动偏移：位移单位 dp，俯仰单位度。 */
internal data class ShakeOffset(val x: Float, val y: Float, val rotation: Float)

/**
 * 车身抖动（纯函数，方便单测）。
 *
 * 三条正弦叠出「不规则的细碎颠簸」，再叠一次每 0.7 秒的衰减冲击。频率与幅度
 * 都随车速走：20 km/h 只是轻轻晃，120 km/h 才明显颠。
 *
 * @param t 秒（动画起点之后的时间）
 * @param speedKmh 车速
 * @param shakeDp 用户调好的幅度（dp）
 */
internal fun carShake(t: Float, speedKmh: Float, shakeDp: Float): ShakeOffset {
    if (!t.isFinite() || shakeDp <= 0f || speedKmh < CarMotionSettings.MIN_SPEED_KMH) {
        return ShakeOffset(0f, 0f, 0f)
    }
    val k = motionFactor(speedKmh)
    val freq = 2.4f + k * 3.2f // 2.4 ~ 5.6 Hz
    val amp = shakeDp * (0.55f + k * 0.75f)
    val ph = t * freq * TAU

    var y = sin(ph) * amp + sin(ph * 2.7f + 1.1f) * amp * 0.32f
    val x = sin(ph * 1.3f + 0.7f) * amp * 0.22f
    val rot = sin(ph * 0.9f + 2f) * 0.10f

    // 每 0.7 秒一次单次冲击：短促下沉再回弹（阻尼正弦）。
    // 只有「在跑」的时候出现，匀速直线时它负责给出一点点意外感。
    val period = 0.7f
    val p = (t % period) / period
    y += exp(-p * 9f) * sin(p * 34f) * amp * 1.5f

    return ShakeOffset(x, y, rot)
}

/**
 * 风线粒子池。
 *
 * 40 条线，全部从左边外飘进来、往右走（车模朝左）。速度 = `(140 + 车速×13) dp/s`，
 * 120 km/h 时约 1700 dp/s，一屏 375dp 大约 0.2 秒穿过 —— 和真实高速上「唰」一下
 * 掠过车身的节奏对得上。
 */
private class WindEngine {
    private class Streak {
        var x = 0f
        var y = 0f
        var len = 0f
        var v = 1f
        var a = 0.2f
        var w = 1.5f
    }

    private val streaks = ArrayList<Streak>(COUNT)
    private var w = 0f
    private var h = 0f
    private var density = 1f
    private val rnd = Random(0x5EED)

    fun step(dt: Float, speedKmh: Float, windAngle: Float) {
        if (streaks.isEmpty() || dt <= 0f) return
        val ang = windAngle * DEG_TO_RAD
        val dx = cos(ang)
        val dy = sin(ang)
        val speedPx = (140f + speedKmh * 13f) * density
        streaks.forEach { s ->
            s.x += dx * speedPx * s.v * dt
            s.y += dy * speedPx * s.v * dt
            // 回收：跑出右边界或上下边界后从左侧重新进场
            if (s.x > w + s.len || s.y < -s.len || s.y > h + s.len) respawn(s, initial = false)
        }
    }

    fun draw(
        scope: DrawScope,
        /** 动画时钟（秒）：迎风锥的相位靠它推进，暂停时会一起停住。 */
        timeSec: Float,
        speedKmh: Float,
        windAngle: Float,
        dark: Boolean,
    ) {
        if (scope.size.width <= 0f || scope.size.height <= 0f) return
        ensure(scope.size.width, scope.size.height, scope.density)
        if (streaks.isEmpty()) return

        val k = motionFactor(speedKmh)
        val ang = windAngle * DEG_TO_RAD
        val dx = cos(ang)
        val dy = sin(ang)
        val base = if (dark) Color(0xFFD6E8FF) else Color(0xFF55657A)

        // 风线：两端渐隐的细线，盖在车漆上也只有「空气掠过」的量感，不会把车涂灰
        streaks.forEach { s ->
            val alpha = (s.a * (0.62f + k * 0.7f)).coerceIn(0f, 0.78f)
            if (alpha <= 0.01f) return@forEach
            val len = s.len * (0.5f + k * 0.9f)
            val start = Offset(s.x, s.y)
            val end = Offset(s.x + dx * len, s.y + dy * len)
            scope.drawLine(
                brush = Brush.linearGradient(
                    0f to base.copy(alpha = 0f),
                    0.45f to base.copy(alpha = alpha),
                    1f to base.copy(alpha = 0f),
                    start = start,
                    end = end,
                ),
                start = start,
                end = end,
                strokeWidth = s.w * density,
                cap = StrokeCap.Round,
            )
        }

        // 车头迎风锥：车模朝左，车头前方（左侧边缘）5 条短须，向右一冲就散
        val coneX = 3f * density
        val top = h * 0.36f
        val stepY = 12f * density
        val travel = 34f * density
        val phaseSpeed = 0.55f + k * 1.4f
        for (i in 0 until 7) {
            val phase = ((timeSec * phaseSpeed + i * 0.17f) % 1f + 1f) % 1f
            val x = coneX + phase * travel
            val y = top + i * stepY
            val alpha = sin(phase * TAU * 0.5f) * (0.16f + k * 0.30f)
            if (alpha <= 0.01f) continue
            scope.drawLine(
                color = base.copy(alpha = alpha),
                start = Offset(x, y),
                end = Offset(x + travel * 0.5f, y + dy * travel * 0.5f),
                strokeWidth = 1.2f * density,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun ensure(width: Float, height: Float, density: Float) {
        if (width == w && height == h && density == this.density) return
        w = width
        h = height
        this.density = density
        while (streaks.size < COUNT) streaks.add(Streak())
        streaks.forEach { respawn(it, initial = true) }
    }

    private fun respawn(s: Streak, initial: Boolean) {
        val scale = density
        s.len = rnd.nextFloat() * 46f * scale + 18f * scale
        s.v = 0.9f + rnd.nextFloat() * 0.7f
        s.a = 0.20f + rnd.nextFloat() * 0.32f
        s.w = 1.1f + rnd.nextFloat() * 0.9f
        s.y = rnd.nextFloat() * if (h > 0f) h else 1f
        s.x = if (initial) {
            rnd.nextFloat() * if (w > 0f) w else 1f
        } else {
            -s.len - rnd.nextFloat() * 80f * scale
        }
    }

    private companion object {
        const val COUNT = 46
    }
}
