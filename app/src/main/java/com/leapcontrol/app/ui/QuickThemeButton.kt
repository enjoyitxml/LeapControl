package com.leapcontrol.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

// ============================================================================
// 首页「快捷换壁纸」按钮 —— 一颗会呼吸的小白点
// ----------------------------------------------------------------------------
// 放在两处：车模右上角、相伴卡右上角。点一下在「当前壁纸所属的类型」里随机换一套，
// 规则见 ThemeResolver.randomSwitch / poolOf。
//
// 视觉上刻意做得很轻：点只有 16dp，深色下最亮七成不透明度，靠光晕和呼吸幅度
// 让人「注意到但不干扰」。浅色主题下纯白点会淹没在浅底里，所以浅色侧换成
// 「白珠子 + 淡投影」，用投影的边缘把点托出来，而不是靠加粗、加大透明度。
// ============================================================================

/** 一次完整呼吸的时长：3.4s 吸气 + 呼气，慢到不会让人盯着看，又能看出在动。 */
private const val BREATH_CYCLE_NS = 3_400_000_000L

/** 呼吸动画的最小帧间隔（约 30fps）：慢动画用不着 120fps，别跟滚动抢帧预算。 */
private const val BREATH_FRAME_INTERVAL_NS = 33_000_000L

/** 按钮的触控区（含留白），比可见的小圆点大得多，保证点得中。 */
private val TOUCH_SIZE = 44.dp

/**
 * 圆点半径（呼吸最大时 8dp，直径 16dp）。
 *
 * 画布直接铺满整个触控区，因为光晕半径是圆点的 2.3 倍——
 * 画布要是只按圆点开，光晕会被画布边界切出一个硬圆边。
 */
private val DOT_RADIUS = 8.dp

/**
 * 快捷换壁纸按钮。
 *
 * @param onClick 点一下换一套壁纸（随机规则在调用方，见 [ThemeResolver.randomSwitch]）
 * @param modifier 定位用（右上角对齐、边距）
 */
@Composable
fun QuickThemeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalAppColors.current.isDark
    val breath = rememberBreathPhase()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按下时轻轻一收：即使这次随机到的壁纸看不清差别，手指也能得到反馈
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "quickThemePress",
    )

    Box(
        modifier = modifier
            .size(TOUCH_SIZE)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = "随机切换壁纸" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        ) {
            val k = breathCurve(breath.value)
            val center = Offset(size.width / 2f, size.height / 2f)
            // 圆点半径在 62%~100% 之间起伏，光晕跟着一起放大缩小
            val dotRadius = DOT_RADIUS.toPx() * breathScaleAt(breath.value)
            val glowRadius = dotRadius * 2.3f

            if (dark) {
                // 深色底：白点自带一层白光晕，像夜里的一盏小灯
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f * (0.55f + 0.45f * k)),
                            Color.White.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.44f + 0.26f * k),
                    radius = dotRadius,
                    center = center,
                )
            } else {
                // 浅色底：纯白点会直接淹没在浅底里，所以改成「白珠子 + 淡投影」，
                // 投影给边缘一条能看见的界线，靠它把点托出来，而不是靠加粗或不透明
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF15171C).copy(alpha = 0.13f * (0.6f + 0.4f * k)),
                            Color(0xFF15171C).copy(alpha = 0f),
                        ),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
                // 投影：柔边（径向渐变到透明），dot 半径 1.5 倍左右收干净
                val shadowCenter = Offset(center.x, center.y + 0.7.dp.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF15171C).copy(alpha = 0.20f + 0.06f * k),
                            Color(0xFF15171C).copy(alpha = 0f),
                        ),
                        center = shadowCenter,
                        radius = dotRadius * 1.55f,
                    ),
                    radius = dotRadius * 1.55f,
                    center = shadowCenter,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.82f + 0.12f * k),
                    radius = dotRadius,
                    center = center,
                )
            }
        }
    }
}

/**
 * 呼吸相位（0~1 循环）。
 *
 * 与场景动画同一套做法：手写节流而不用 `rememberInfiniteTransition`，
 * 慢速呼吸在 120Hz 屏上没必要每帧重绘；进后台帧时钟会停，不必额外处理。
 *
 * 相位只在绘制阶段被读取（`Canvas` 的 draw lambda），所以每帧只重绘这一颗小点，
 * 不会引起重组，也不会让首页其它内容跟着刷新。
 */
@Composable
private fun rememberBreathPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNs = 0L
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNs == 0L) {
                    startNs = now
                    lastFrameNs = now
                } else if (now - lastFrameNs >= BREATH_FRAME_INTERVAL_NS) {
                    lastFrameNs = now
                    phase.floatValue = ((now - startNs) % BREATH_CYCLE_NS).toFloat() / BREATH_CYCLE_NS
                }
            }
        }
    }
    return phase
}

/**
 * 呼吸曲线：0 → 1 → 0 的余弦，两端缓、中间顺，比线性更像「一口气」。
 *
 * 抽成纯函数是为了能直接单测这条曲线（振幅、周期端点）；Canvas 里也调它，
 * 保证「测试验的」和「画出来的」是同一条曲线。
 */
internal fun breathCurve(phase: Float): Float =
    0.5f - 0.5f * cos(phase * 2f * PI.toFloat())

/** 呼吸相位对应的小点直径倍率：0.62（最小）~ 1.0（最大）。 */
internal fun breathScaleAt(phase: Float): Float = 0.62f + 0.38f * breathCurve(phase)

