package com.leapcontrol.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 场景画面覆盖的高度：从屏幕顶端（含状态栏）到空调控件上沿。
 *
 * 首页到空调卡之前的内容大致是：状态栏 + 车名/续航/充电中心 176dp + 车模 200dp +
 * 快捷控车 ~80dp，再留一点余量。场景在这个高度上画完，往下已经淡到看不见。
 */
private val SCENE_HEIGHT = 508.dp

/** 动态场景的循环周期：慢一点才有氛围，快了像故障。 */
private const val SCENE_CYCLE_MS = 24_000

/** 静态场景的固定相位（0.32 能让云/花瓣/星点落在比较自然的位置）。 */
private const val STATIC_PHASE = 0.08f

/**
 * 主题场景层。
 *
 * 只画在页面上半部分（[SCENE_HEIGHT]），并做两重衰减保证不挡内容：
 *  1. 场景自身用 `BlendMode.DstIn` 蒙一层竖向渐变，从 30% 处开始变淡、到底完全消失；
 *  2. 上层再叠一层很淡的雾（MainScreen 里的椭圆遮罩），把车名小字压稳。
 *
 * 动画只在 draw 阶段读取进度，不触发 recomposition；
 * [animate] 传 false（不在首页主界面 / 进后台）时改用固定相位，完全静止、省电。
 */
@Composable
fun ThemeBackdrop(
    theme: SeasonTheme,
    dark: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    sceneHeight: Dp = SCENE_HEIGHT,
    /**
     * 顶部雾化遮罩（通常是 MainScreen 那个以车模为圆心的椭圆）。
     * 传进来一起参与下面的淡出蒙层，遮罩才会和场景同步消失；
     * 单独在场景外面叠一层的话，会在场景底边留下一道硬边。
     */
    veilBrush: Brush? = null,
) {
    if (theme.scene == SceneKind.NONE) return

    val phase = rememberScenePhase(theme = theme, animate = animate)
    val palette = theme.palette()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(sceneHeight),
    ) {
        val t = phase.value
        // 先把场景画进一个图层，再用 DstIn 渐变「擦掉」下半部分的透明度
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
            paintScene(theme.scene, t, palette)
            if (veilBrush != null) drawRect(brush = veilBrush)
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.White,
                    0.30f to Color.White,
                    0.52f to Color.White.copy(alpha = 0.60f),
                    0.74f to Color.White.copy(alpha = 0.22f),
                    0.90f to Color.White.copy(alpha = 0.06f),
                    1.00f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
    }
}

/**
 * 主题缩略图：渐变底 + 场景（固定相位、不淡出），用在个性化页的选择网格里。
 *
 * 和真实背景共用同一个 [paintScene]，所以缩略图看到的就是首页会看到的东西。
 */
@Composable
fun ThemeSwatch(
    theme: SeasonTheme,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = theme.palette()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(theme.gradient(dark))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            paintScene(theme.scene, STATIC_PHASE, palette)
        }
    }
}

/** 场景动画进度：动态主题走无限循环，其余给一个固定相位。 */
@Composable
private fun rememberScenePhase(theme: SeasonTheme, animate: Boolean): State<Float> {
    return if (theme.animated && animate) {
        val transition = rememberInfiniteTransition(label = "scene")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SCENE_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scenePhase",
        )
    } else {
        remember { mutableStateOf(STATIC_PHASE) }
    }
}
