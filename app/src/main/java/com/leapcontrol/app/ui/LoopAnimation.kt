package com.leapcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * 手写节流的循环动画推进器（首页充电脉冲、空调风扇旋转、主题场景漂移共用一套）。
 *
 * ## 为什么不用 `rememberInfiniteTransition`
 *
 * 1. 无限动画固定按屏幕刷新率推进（60/90/120fps）。这些动画本身就是慢速漂移，
 *    按 120fps 推进纯属浪费 —— 每帧都要在动画阶段跑一次、并让读数方重绘。
 * 2. `rememberInfiniteTransition` 的值一旦被组合期解包，就会每帧重组一次。
 *    首页空调卡片外层是 `BoxWithConstraints`（SubcomposeLayout），一重组等于每帧
 *    subcompose + measure，滑动时是实打实抢帧。
 * 3. 需要有「暂停」与「停用」语义：滑动时、关空调时能停在当前相位，而不是被重置回起点。
 *
 * 所以这里返回的是 **`State<Float>` 本身**，请只在绘制阶段（`Canvas` 的 draw lambda、
 * `graphicsLayer` 的 block）读它的 `.value`，不要在组合期 `by` 解包 —— 那样又把重组拉回来了。
 */

/** 风扇转一圈的时间：1.5s，四叶转到肉眼看不出跳帧。 */
internal const val FAN_CYCLE_MS = 1500

/** 循环动画的默认推进间隔：33ms（约 30fps）。 */
internal const val FRAME_INTERVAL_NS = 33_000_000L

/**
 * 相位推进：按经过的纳秒把 0~1 的循环进度往前推，结果始终落在 `[0, 1)`。
 *
 * 单独抽成纯函数是为了能单测：循环动画最容易在「刚好走完一圈」和「跨过 1.0」这两个
 * 边界上出岔子（浮点误差累积成负相位、或越界到 1.x 让画面跳一下）。
 *
 * @param current 当前相位
 * @param deltaNanos 距上次推进的纳秒数；非正数（时钟异常）时原样返回，不推进
 * @param cycleMs 一圈的时长（毫秒）；非正数视为配置错误，原样返回（绝不能算出 NaN）
 */
internal fun advanceLoopPhase(current: Float, deltaNanos: Long, cycleMs: Int): Float {
    if (cycleMs <= 0) return current
    if (deltaNanos <= 0L) return current
    if (current.isNaN()) return 0f
    val step = deltaNanos / 1_000_000f / cycleMs
    if (step.isNaN() || step.isInfinite()) return current
    // 用「减去下取整」而不是 `%`：`%` 对负数会返回负结果，相位一负画面就整块不画
    val next = current + step
    return next - kotlin.math.floor(next)
}

/**
 * 循环进度（0~1），只在 [enabled] 时推进，[paused] 为 true 时停在当前相位。
 *
 * @param cycleMs 一圈时长
 * @param enabled false 时整个循环停摆（LambdaEffect 直接返回，不再申请帧）
 * @param intervalNs 推进间隔，默认 33ms；风车这类要更顺的可传 16ms
 * @param paused 暂停信号（读的是 State，所以暂停/恢复不会让调用方重组）
 */
@Composable
internal fun rememberLoopProgress(
    cycleMs: Int,
    enabled: Boolean,
    intervalNs: Long = FRAME_INTERVAL_NS,
    paused: State<Boolean>? = null,
): State<Float> {
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(cycleMs, enabled, intervalNs, paused) {
        if (!enabled || cycleMs <= 0 || intervalNs <= 0L) return@LaunchedEffect
        var lastNs = 0L
        // 攒够一个 intervalNs 才真正推进一次，中间跳过的帧不丢时间
        var pendingNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs == 0L) {
                    // 第一帧只记时间基准，否则会拿「开机以来的纳秒数」当增量，相位直接飞出去
                    lastNs = now
                } else {
                    val delta = now - lastNs
                    lastNs = now
                    if (paused?.value != true && delta > 0L) {
                        pendingNs += delta
                        if (pendingNs >= intervalNs) {
                            progress.floatValue = advanceLoopPhase(progress.floatValue, pendingNs, cycleMs)
                            pendingNs = 0L
                        }
                    }
                }
            }
        }
    }
    return progress
}
