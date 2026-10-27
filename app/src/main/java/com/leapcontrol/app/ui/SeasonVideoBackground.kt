package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.leapcontrol.app.R
import kotlin.math.max

/** 视频主题 id → 打包在 res/raw 里的循环背景视频。 */
internal fun seasonVideoResId(seasonId: String): Int = when (seasonId) {
    SeasonThemes.VIDEO_SUMMER.id -> R.raw.bg_season_summer
    SeasonThemes.VIDEO_AUTUMN.id -> R.raw.bg_season_autumn
    SeasonThemes.VIDEO_WINTER.id -> R.raw.bg_season_winter
    SeasonThemes.VIDEO_STARS.id -> R.raw.bg_season_stars
    else -> R.raw.bg_season_spring
}

/**
 * 视频主题的背景层：视频 + 顶部雾化 + 下缘渐隐。
 *
 * 视频仍然是满屏播放（和旧版一致：主景在上部、铺到状态栏之后），
 * 但**下缘要用页面底色渐隐掉**，否则卡片会压在有画面上的一层，
 * 和另外 20 套「只覆盖到空调卡上沿」的主题观感就对不上了。
 *
 * 淡出终点取主题渐变的最底色（浅色主题近白、深色主题近黑），
 * 所以无论深浅模式，视频都是「淡出进背景」而不是被糊上一块别的颜色。
 */
@Composable
internal fun ThemeVideoBackdrop(
    theme: SeasonTheme,
    dark: Boolean,
    paused: Boolean,
    veilBrush: Brush?,
    modifier: Modifier = Modifier,
    sceneHeight: Dp = SCENE_HEIGHT,
) {
    val bottom = theme.gradient(dark).last()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val sceneHeightPx = with(LocalDensity.current) { sceneHeight.toPx() }
        val sceneEnd = (sceneHeightPx / totalH).coerceIn(0.25f, 1f)

        SeasonVideoBackground(
            seasonId = theme.id,
            muted = true,
            paused = paused,
            modifier = Modifier.matchParentSize(),
        )

        // 顶部雾化：只在场景高度内叠，避免把下方卡片压上一层灰
        if (veilBrush != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sceneHeight)
                    .background(veilBrush),
            )
        }

        // 下缘渐隐：从场景高度 62% 处开始变实，到场景底边完全变成页面底色
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        *arrayOf(
                            0f to Color.Transparent,
                            sceneEnd * 0.62f to Color.Transparent,
                            sceneEnd * 0.84f to bottom.copy(alpha = 0.55f),
                            sceneEnd to bottom,
                            1f to bottom,
                        ),
                    ),
                ),
        )
    }
}


/**
 * 四季动态背景层。
 *
 * 用 TextureView + MediaPlayer 循环播放竖屏背景视频：
 *  - **原比例、不拉伸**：TextureView 的布局宽高比直接等于视频原始宽高比，
 *    再用它铺满容器（cover）。比例由布局保证而不是靠播放期矩阵修正，
 *    因此不存在拉伸变形，也不依赖解码器回调的时序；
 *  - **顶部对齐**：多出容器的部分向下裁掉，主景（花枝 / 树冠 / 落叶 / 飘雪）
 *    始终顶在屏幕上沿，穿到状态栏之后；
 *  - 不吃焦点、不拦触摸、不参与无障碍播报，纯背景；
 *  - 播放失败静默降级为静态渐变，界面照常可用。
 */
@Composable
internal fun SeasonVideoBackground(
    seasonId: String,
    muted: Boolean,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resId = seasonVideoResId(seasonId)
    val controller = remember { SeasonVideoController() }

    // 视频原始像素由解码器异步回报，拿到之前先按 9:16 占位，到位后再收敛
    var videoW by remember { mutableIntStateOf(9) }
    var videoH by remember { mutableIntStateOf(16) }

    DisposableEffect(controller) {
        controller.onVideoSize = { w, h ->
            if (w > 0 && h > 0) {
                videoW = w
                videoH = h
            }
        }
        onDispose { controller.release() }
    }
    // 声画开关各自独立：调音量不会重建播放器（重建会从头起播、画面跳一下）
    DisposableEffect(muted) {
        controller.setMuted(muted)
        onDispose { }
    }
    DisposableEffect(paused) {
        controller.setPaused(paused)
        onDispose { }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        if (boxW <= 0f || boxH <= 0f) return@BoxWithConstraints

        // 等比放大到铺满：左右多出的部分两边均分裁掉，顶边严格贴齐容器顶部
        val scale = maxOf(boxW / videoW.toFloat(), boxH / videoH.toFloat())
        val outW = videoW * scale
        val outH = videoH * scale
        val density = LocalDensity.current
        val wDp = with(density) { outW.toDp() }
        val hDp = with(density) { outH.toDp() }
        val xDp = with(density) { ((boxW - outW) / 2f).toDp() }

        AndroidView(
            modifier = Modifier
                .requiredSize(wDp, hDp)
                .offset(x = xDp),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isFocusable = false
                    isClickable = false
                    isLongClickable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            controller.setSurface(Surface(st))
                            controller.setSource(ctx, resId)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            st: SurfaceTexture, w: Int, h: Int
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            controller.clearSurface()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            update = { controller.setSource(context, resId) },
        )
    }
}

/**
 * MediaPlayer 生命周期管家。
 *
 * 单独拎出来是因为 TextureView 的 factory 只跑一次，Compose 重组时捕获不到新的控制器引用；
 * 控制器作为 remember 的常驻对象，由 update 回调驱动状态收敛。
 */
private class SeasonVideoController {

    /** 解码器回报视频原始像素尺寸（用于按原比例确定 TextureView 布局）。 */
    var onVideoSize: ((Int, Int) -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var appContext: Context? = null
    private var resId = 0
    private var prepared = false
    private var wantPlay = true
    private var volume = 0f

    fun setSurface(s: Surface) {
        surface?.release()
        surface = s
        mediaPlayer?.setSurface(s)
        if (mediaPlayer == null) start()
    }

    fun clearSurface() {
        runCatching { mediaPlayer?.setSurface(null) }
        runCatching { surface?.release() }
        surface = null
    }

    fun setSource(context: Context, res: Int) {
        val changed = res != resId
        appContext = context
        resId = res
        if (changed) start()
    }

    fun setMuted(muted: Boolean) {
        volume = if (muted) 0f else 1f
        runCatching { mediaPlayer?.setVolume(volume, volume) }
    }

    fun setPaused(paused: Boolean) {
        wantPlay = !paused
        val mp = mediaPlayer ?: return
        runCatching {
            if (paused) {
                if (mp.isPlaying) mp.pause()
            } else if (prepared && !mp.isPlaying) {
                mp.start()
            }
        }
    }

    fun release() {
        clearSurface()
        stopPlayer()
        onVideoSize = null
    }

    private fun start() {
        stopPlayer()
        val s = surface ?: return
        val ctx = appContext ?: return
        val id = resId
        if (id == 0) return

        val mp = MediaPlayer()
        mediaPlayer = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }
        mp.isLooping = true
        mp.setSurface(s)
        mp.setOnVideoSizeChangedListener { _, w, h -> onVideoSize?.invoke(w, h) }
        mp.setOnPreparedListener { player ->
            prepared = true
            player.setVolume(volume, volume)
            if (wantPlay) runCatching { player.start() }
        }
        // 起播失败不弹错：本来就只是背景层，降级成静态渐变即可
        mp.setOnErrorListener { _, _, _ ->
            prepared = false
            true
        }
        runCatching {
            mp.setDataSource(ctx, Uri.parse("android.resource://${ctx.packageName}/$id"))
            mp.prepareAsync()
        }.onFailure { stopPlayer() }
    }

    private fun stopPlayer() {
        runCatching { mediaPlayer?.setSurface(null) }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        prepared = false
    }
}
