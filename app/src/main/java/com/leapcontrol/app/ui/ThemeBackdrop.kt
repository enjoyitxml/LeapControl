package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * 场景画面覆盖的高度：从屏幕顶端（含状态栏）到空调控件上沿。
 *
 * 首页到空调卡之前的内容大致是：状态栏 + 车名/续航/充电中心 176dp + 车模 200dp +
 * 快捷控车 ~80dp，再留一点余量。场景在这个高度上画完，往下已经淡到看不见。
 */
internal val SCENE_HEIGHT = 508.dp

/** 动态场景的循环周期：慢一点才有氛围，快了像故障。 */
private const val SCENE_CYCLE_MS = 24_000f

/**
 * 动态场景的帧间隔（约 30fps）。
 *
 * 这类动画只是缓慢漂移的云 / 星 / 雨丝，24 秒才走完一轮，30fps 与 60fps 肉眼几乎没差别，
 * 但绘制次数少一半 —— 首页滑动时场景与滚动共用同一帧预算，这一半很关键。
 */
private const val SCENE_FRAME_INTERVAL_NS = 33_000_000L

/** 静态场景的固定相位（0.08 能让云/花瓣/星点落在比较自然的位置）。 */
private const val STATIC_PHASE = 0.08f

/**
 * 主题场景层。
 *
 * 只画在页面上半部分（[SCENE_HEIGHT]），并做两重衰减保证不挡内容：
 *  1. 场景自身用 `BlendMode.DstIn` 蒙一层竖向渐变，从 30% 处开始变淡、到底完全消失；
 *  2. 上层再叠一层很淡的雾（MainScreen 里的椭圆遮罩），把车名小字压稳。
 *
 * 性能上有三条约定，改动时别破坏：
 *  · 动画进度只在 **draw 阶段**读取，不触发 recomposition；
 *  · `Paint` / 淡出 `Brush` / 图层边界放在 [drawWithCache] 里，只在尺寸变化时重建，
 *    而不是每帧新建一遍；
 *  · [animate] 传 false（不在首页主界面 / 进后台 / 正在滑动）时**停在当前相位**，
 *    不做动画但也不跳回起点 —— 从静止恢复成动态时画面是接上的，不会闪一下。
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
    // 配色只跟主题有关，别在每帧绘制时重新构造
    val palette = remember(theme) { theme.palette() }
    val scene = theme.scene

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(sceneHeight)
            .drawWithCache {
                // 下面这几样每帧都要用，但只在尺寸变化时才需要重建
                val layerPaint = Paint()
                val layerBounds = Rect(0f, 0f, size.width, size.height)
                val fadeBrush = Brush.verticalGradient(
                    0.00f to Color.White,
                    0.30f to Color.White,
                    0.52f to Color.White.copy(alpha = 0.60f),
                    0.74f to Color.White.copy(alpha = 0.22f),
                    0.90f to Color.White.copy(alpha = 0.06f),
                    1.00f to Color.Transparent,
                )
                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(layerBounds, layerPaint)
                        paintScene(scene, phase.value, palette)
                        if (veilBrush != null) drawRect(brush = veilBrush)
                        drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                        canvas.restore()
                    }
                }
            },
    )
}

/**
 * 自定义壁纸层：把用户上传的图铺满整页。
 *
 * 只有当前生效主题是「我的壁纸」时才画（[enabled]），其余 40 套仍然走
 * [ThemeBackdrop] 那套实时绘制的场景 —— 两条路互不干扰。
 *
 * 上面必须叠一层薄纱（[wallpaperVeil]）：壁纸内容完全不可控，浅色壁纸配深色字还行，
 * 一张夜景配深色字就全糊了。这层纱同时承担「把图压淡一点，别抢卡片内容」的作用。
 *
 * 解码走 AppRuntime 的缓存：切 Tab 回来是同一张 Bitmap，不会每次都重新解码 10MB。
 */
@Composable
fun CustomWallpaperLayer(
    runtime: AppRuntime,
    dark: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    val kind = if (dark) CustomBackdropKind.WALLPAPER_NIGHT else CustomBackdropKind.WALLPAPER_DAY
    // 读版本号：换图 / 删图后版本变化，才会重新解码
    val state = runtime.customBackdrop.value
    val version = state.versionOf(kind)
    // 首帧先吃缓存，避免「已经设过壁纸」的机器切页回来闪一帧渐变
    var bitmap by remember(version, kind, state.revision) {
        mutableStateOf(runtime.cachedCustomBackdrop(kind)?.asImageBitmap())
    }
    LaunchedEffect(version, kind, state.revision) {
        bitmap = if (version == 0L) null else runtime.customBackdrop(kind)?.asImageBitmap()
    }

    val image = bitmap ?: return
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // 顶部轻一点，让壁纸「天空」那块透出来；往下越来越重，
                        // 因为下半屏是卡片区，文字密度最高
                        0.00f to (if (dark) Color.Black else Color.White).copy(alpha = if (dark) 0.42f else 0.30f),
                        0.45f to (if (dark) Color.Black else Color.White).copy(alpha = if (dark) 0.54f else 0.44f),
                        1.00f to (if (dark) Color.Black else Color.White).copy(alpha = if (dark) 0.66f else 0.60f),
                    ),
                ),
        )
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
    val palette = remember(theme) { theme.palette() }
    val gradient = remember(theme.id, dark) { Brush.verticalGradient(theme.gradient(dark)) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(gradient),
    ) {
        if (theme.scene != SceneKind.NONE) {
            Canvas(Modifier.fillMaxSize()) {
                paintScene(theme.scene, STATIC_PHASE, palette)
            }
        }
    }
}

/**
 * 相位推进：按经过的纳秒把 0~1 的循环进度往前推，结果始终落在 `[0, 1)`。
 *
 * 单独抽成纯函数是为了能单测：循环动画最容易在「刚好走完一圈」和「跨过 1.0」这两个
 * 边界上出岔子（浮点误差累积成负相位、或越界到 1.x 让场景跳一下）。
 *
 * @param current 当前相位
 * @param deltaNanos 距上次绘制的纳秒数；非正数（时钟异常）时原样返回，不推进
 */
internal fun advanceScenePhase(current: Float, deltaNanos: Long): Float {
    if (deltaNanos <= 0L) return current
    val next = current + deltaNanos / (SCENE_CYCLE_MS * 1_000_000f)
    // 取小数部分落到 [0,1)：手动 floor，避免 % 对负数出意外
    return next - floor(next)
}

/**
 * 场景动画进度。
 *
 * 手写节流而不用 `rememberInfiniteTransition`，原因有三个：
 *  1. 无限动画固定按屏幕刷新率推进（60/90/120fps），对这种慢速漂移完全是浪费；
 *  2. [animate] 变 false 时能停在当前相位，而不是被重置回起点；
 *  3. 进后台时 Compose 的帧时钟本身会停，不需要额外处理。
 *
 * @return 0~1 的循环进度；非动态主题返回固定相位（静态场景不读它）。
 */
@Composable
private fun rememberScenePhase(theme: SeasonTheme, animate: Boolean): State<Float> {
    if (!theme.animated) return remember { mutableStateOf(STATIC_PHASE) }

    // 相位跨主题切换保留，换主题时画面不会又从 0 开始
    val phase = remember { mutableFloatStateOf(STATIC_PHASE) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = now
                } else if (now - lastFrameNs >= SCENE_FRAME_INTERVAL_NS) {
                    phase.floatValue = advanceScenePhase(phase.floatValue, now - lastFrameNs)
                    lastFrameNs = now
                }
            }
        }
    }
    return phase
}
