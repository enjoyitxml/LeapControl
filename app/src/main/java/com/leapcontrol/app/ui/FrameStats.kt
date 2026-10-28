package com.leapcontrol.app.ui

import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun rememberFrameStats(enabled: Boolean): FrameStats {
    val view = LocalView.current
    val counter = remember { FrameCounter() }
    var stats by remember { mutableStateOf(FrameStats()) }

    DisposableEffect(enabled, view) {
        if (!enabled) return@DisposableEffect onDispose { }
        val observer = view.rootView.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            counter.frames++
            counter.lastFrameAt = android.os.SystemClock.uptimeMillis()
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
        var lastAt = android.os.SystemClock.uptimeMillis()
        while (true) {
            delay(500)
            val now = android.os.SystemClock.uptimeMillis()
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
 * 帧率浮层：`62 fps · 120/144Hz`。
 *
 * 颜色按「实际帧率 / 屏幕刷新率」的比例走：≥90% 绿、≥60% 橙、其余红。
 * 始终用深色半透明底 + 白字 —— 它要压在视频/渐变/卡片上，任何跟随主题的配色都会在某种主题下糊掉。
 */
@Composable
fun FrameStatsBadge(stats: FrameStats, modifier: Modifier = Modifier) {
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

    Row(
        modifier = modifier
            .background(Color(0xB3000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
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
}
