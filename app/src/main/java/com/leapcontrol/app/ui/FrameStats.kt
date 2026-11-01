package com.leapcontrol.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.DisplayRefresh
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 实时帧率采样结果。
 *
 * [fps] 为 -1 表示这一秒几乎没有出帧（界面静止），不是 0 帧。
 * [refreshRate] 是屏幕此刻实际用的刷新率，[peakRate] 是硬件峰值。
 */
data class FrameStats(
    val fps: Int = 0,
    val refreshRate: Int = 60,
    val peakRate: Int = 60,
)

/**
 * 每帧的 CPU / GPU 耗时（毫秒，滑动期间的平均值）。
 *
 * 只用来自查卡顿到底卡在哪一侧，不参与任何业务逻辑：
 *  · [cpuMs] = SYNC + DRAW，即「主线程算这一步要多久」—— 重组、测量、录制绘制指令都算在里面；
 *  · [gpuMs] = GPU_DURATION，Android 12 以下拿不到，这时为 -1。
 *
 * 判读方式：cpu 大 → Compose 侧在反复重组 / 重新录制绘制；gpu 大 → 填充率
 * （半透明层叠太多、阴影、大图缩放），此时继续压 CPU 是白费劲。
 */
data class FrameCost(
    val cpuMs: Float = -1f,
    val gpuMs: Float = -1f,
)

/** 帧计数：刻意不用 Compose State，绘制期自增不能触发重组。 */
private class FrameCounter {
    var frames = 0
    var lastFrameAt = 0L
}

/**
 * 采样真实帧率。
 *
 * 为什么用 `ViewTreeObserver.OnPreDrawListener` 而不是 Choreographer：
 * Choreographer 的回调是按垂直同步走的，就算一帧都没画出来它照样每秒回调 120 次，
 * 量出来永远是「满帧」。OnPreDraw 只在真的要出一帧时回调，掉帧时它的频率会跟着掉，
 * 才是用户眼睛看到的东西。
 *
 * 采样窗口 500ms，采样本身的开销只有一次自增，不影响被测对象。
 */
@Composable
private fun rememberFrameStats(enabled: Boolean): FrameStats {
    val view = LocalView.current
    val counter = remember { FrameCounter() }
    var stats by remember { mutableStateOf(FrameStats()) }

    DisposableEffect(enabled, view) {
        if (!enabled) return@DisposableEffect onDispose { }
        val observer = view.rootView.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            counter.frames++
            counter.lastFrameAt = SystemClock.uptimeMillis()
            true
        }
        observer.addOnPreDrawListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }

    LaunchedEffect(enabled, view) {
        if (!enabled) return@LaunchedEffect
        var lastFrames = 0
        var lastAt = SystemClock.uptimeMillis()
        while (true) {
            delay(500)
            val now = SystemClock.uptimeMillis()
            val elapsed = (now - lastAt).coerceAtLeast(1)
            val delta = counter.frames - lastFrames
            lastFrames = counter.frames
            lastAt = now
            val measured = (delta * 1000f / elapsed).roundToInt()
            // 1.2 秒没出过帧＝界面静止，别把上一秒的数字一直挂着骗人
            val fps = if (now - counter.lastFrameAt > 1200) -1 else measured
            val display = view.display
            val rates = display?.let { DisplayRefresh.describe(it) }
            stats = FrameStats(
                fps = fps,
                refreshRate = (rates?.first ?: 60f).roundToInt(),
                peakRate = (rates?.second ?: 60f).roundToInt(),
            )
        }
    }
    return stats
}

/**
 * 逐帧耗时的累加器。
 *
 * 用 [Window.OnFrameMetricsAvailableListener] 而不是自己拿 Choreographer 掐表：
 * FrameMetrics 是系统本来就采好的，包含 GPU 阶段（Android 12+），
 * 拿它才能分清「主线程算太久」和「GPU 画不完」——自己掐表只能量到前者。
 *
 * 回调走主线程（handler 传 null 即当前 Looper），每帧一次、只做三次 long 累加，
 * 开销在噪声级别；而且只在调试浮层打开时才注册。
 */
private class FrameCostCounter {
    private var cpuNanos = 0L
    private var gpuNanos = 0L
    private var frames = 0
    private var gpuFrames = 0

    fun add(metrics: FrameMetrics) {
        // SYNC = 构建/同步这一帧的视图树，DRAW = 录制绘制指令，两者相加即主线程成本
        val sync = metrics.getMetric(FrameMetrics.SYNC_DURATION)
        val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION)
        if (sync >= 0 && draw >= 0) cpuNanos += sync + draw
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val gpu = metrics.getMetric(FrameMetrics.GPU_DURATION)
            if (gpu >= 0) {
                gpuNanos += gpu
                gpuFrames++
            }
        }
        frames++
    }

    fun take(): FrameCost {
        val n = frames
        if (n == 0) return FrameCost()
        val cpu = (cpuNanos / n.toFloat()) / 1_000_000f
        val gpu = if (gpuFrames == 0) -1f else (gpuNanos / gpuFrames.toFloat()) / 1_000_000f
        cpuNanos = 0
        gpuNanos = 0
        frames = 0
        gpuFrames = 0
        return FrameCost(cpuMs = cpu, gpuMs = gpu)
    }
}

/** 向上找到宿主 Activity（Compose 的 LocalContext 可能是 ContextWrapper）。 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun rememberFrameCost(enabled: Boolean): FrameCost {
    val context = LocalContext.current
    val counter = remember { FrameCostCounter() }
    var cost by remember { mutableStateOf(FrameCost()) }

    DisposableEffect(enabled, context) {
        if (!enabled) return@DisposableEffect onDispose { }
        val window: Window = context.findActivity()?.window
            ?: return@DisposableEffect onDispose { }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            counter.add(metrics)
        }
        // handler 必须显式给主线程 Looper，**不能传 null**。
        // Android 16 起 null 不再表示「用主线程」：Window.addOnFrameMetricsAvailableListener 会直接把它
        // 传进 HardwareRendererObserver，内部取 handler.getLooper() 当场 NPE
        // （"handler and its looper cannot be null"），App 启动就崩。
        val handler = Handler(Looper.getMainLooper())
        // 这是调试探针，注册失败最多是这个数字不显示，绝不能把 App 带走
        val registered = try {
            window.addOnFrameMetricsAvailableListener(listener, handler)
            true
        } catch (t: Throwable) {
            false
        }
        onDispose {
            if (registered) {
                try {
                    window.removeOnFrameMetricsAvailableListener(listener)
                } catch (t: Throwable) {
                    // 窗口已经销毁时移除会抛，忽略
                }
            }
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(500)
            cost = counter.take()
        }
    }
    return cost
}

/**
 * 帧率浮层入口。
 *
 * **这里必须是独立的一层 composable**：帧率与耗时每 500ms 变一次，
 * 如果在 MainScreen 的组合期读它们，每 500ms 就会把整页（含首页两百多个节点）重组一遍 ——
 * 用户正好开着这个浮层看「滑得顺不顺」，等于是探针自己在制造卡顿。
 * 现在这些 State 只在 [FrameStatsBadgeHost] 内部读，重组范围缩到浮层自己。
 *
 * 关掉时不注册任何监听，也没有任何额外开销。
 */
@Composable
fun FrameStatsOverlay(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        FrameStatsBadgeHost(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 6.dp, end = 14.dp),
        )
    }
}

@Composable
private fun FrameStatsBadgeHost(modifier: Modifier) {
    val stats = rememberFrameStats(true)
    val cost = rememberFrameCost(true)
    val recompose = rememberRecomposeRate(true)
    FrameStatsBadge(stats = stats, cost = cost, recompose = recompose, modifier = modifier)
}

/**
 * 每秒重组次数（首页）。用来区分「主线程在重组」和「主线程在等 GPU」。
 *
 * 静止时应该在 0 附近；上下滑动时也应该是 0 —— 一旦滑动期间它跳出个位数以上，
 * 就说明有 State 在滚动路径上被每帧读到了，那时该查的是组合，不是绘制。
 */
@Composable
private fun rememberRecomposeRate(enabled: Boolean): Int {
    var rate by remember { mutableStateOf(0) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var last = RecomposeMeter.total
        while (true) {
            delay(1000)
            val now = RecomposeMeter.total
            rate = (now - last).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            last = now
        }
    }
    return rate
}

/**
 * 帧率浮层：`62 fps · 120/144Hz` + `CPU 7.2 · GPU 11.4`。
 *
 * 颜色按「实际帧率 / 屏幕刷新率」的比例走：≥90% 绿、≥60% 橙、其余红。
 * 始终用深色半透明底 + 白字 —— 它要压在视频/渐变/卡片上，任何跟随主题的配色都会在某种主题下糊掉。
 */
@Composable
fun FrameStatsBadge(
    stats: FrameStats,
    modifier: Modifier = Modifier,
    cost: FrameCost = FrameCost(),
    recompose: Int = 0,
) {
    val ratio = if (stats.refreshRate > 0) stats.fps.toFloat() / stats.refreshRate else 0f
    val dot = when {
        stats.fps < 0 -> Color(0xFF8E9196)
        ratio >= 0.9f -> Color(0xFF3DD68C)
        ratio >= 0.6f -> Color(0xFFFFB454)
        else -> Color(0xFFFF6B6F)
    }
    val rateText = if (stats.peakRate > stats.refreshRate) {
        "${stats.refreshRate}/${stats.peakRate}Hz"
    } else {
        "${stats.refreshRate}Hz"
    }
    val fpsText = if (stats.fps < 0) "静止" else "${stats.fps} fps"
    // 一帧的预算是屏幕刷新周期：60Hz = 16.7ms，120Hz = 8.3ms。超过就是掉帧
    val budgetMs = if (stats.refreshRate > 0) 1000f / stats.refreshRate else 16.7f
    val dot2 = when {
        cost.cpuMs < 0f -> Color(0xFF8E9196)
        cost.cpuMs > budgetMs -> Color(0xFFFF6B6F)
        cost.cpuMs > budgetMs * 0.7f -> Color(0xFFFFB454)
        else -> Color(0xFF3DD68C)
    }
    val cpuText = if (cost.cpuMs < 0f) "CPU --" else "CPU %.1f".format(cost.cpuMs)
    val gpuText = if (cost.gpuMs < 0f) "GPU --" else "GPU %.1f".format(cost.gpuMs)

    Column(
        modifier = modifier
            .background(Color(0xB3000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Canvas(Modifier.size(5.dp)) {
                drawCircle(dot)
            }
            Text(
                text = "$fpsText · $rateText",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Canvas(Modifier.size(5.dp)) {
                drawCircle(dot2)
            }
            Text(
                text = "$cpuText · $gpuText ms",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "重组 $recompose/s",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
