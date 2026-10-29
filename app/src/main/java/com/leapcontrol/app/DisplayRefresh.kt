package com.leapcontrol.app

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window

/**
 * 把窗口刷新率顶到设备当前允许的最高一档。
 *
 * 为什么需要这个：Android 里「屏幕支持 120Hz」和「这个 App 跑在 120Hz」是两件事。
 * 应用不主动声明时，系统经常按 60Hz 给它出帧（省电策略、部分厂商的策略都是这样），
 * Compose 的滚动就会一直停在 60 帧的手感上。声明方式有两处，都要设置：
 *
 *  1. `WindowManager.LayoutParams.preferredRefreshRate` / `preferredDisplayModeId`
 *     —— 让系统在「切显示模式」时优先选这一档（API 23+，全版本可用）；
 *  2. `View.setRequestedFrameRate()`（Android 15 / API 35 起）
 *     —— 新版系统按窗口的 frame rate 决定用哪一档，不调用就可能被按 60Hz 处理。
 *
 * 取哪一档：直接用「同分辨率下的硬件最高档」，不看系统里的 `peak_refresh_rate`。
 * 那个系统项在多数国产 ROM 上是「智能切换」的隐藏值，读到的经常是 60 或者读不到，
 * 结果就是 120Hz 的屏被锁在 60 帧。想省电请让用户在系统里自己限帧 ——
 * 系统限了之后 `preferredDisplayModeId` 会被系统收敛，我们不会硬顶。
 */
object DisplayRefresh {

    private const val TAG = "LeapControl"

    /**
     * 当前生效的刷新率与「这台设备能到多少」。
     * 返回 (当前, 硬件峰值)，用于设置页与帧率浮层的展示。
     */
    fun describe(display: Display): Pair<Float, Float> {
        val current = runCatching { display.refreshRate }.getOrDefault(60f)
        val peak = runCatching {
            display.supportedModes?.maxOfOrNull { it.refreshRate } ?: current
        }.getOrDefault(current)
        return current to peak
    }

    /**
     * 申请最高刷新率，返回实际申请到的档位（0 表示没动，交给系统决定）。
     *
     * 只在「同分辨率」的模式里挑，避免为了高频把渲染分辨率降下去（清晰度优先）。
     * 失败一律静默：老机型、外接屏、投屏场景下拿不到 modes 是正常的，
     * 拿不到就维持系统默认，不影响功能。
     */
    fun apply(window: Window): Float = runCatching {
        val context = window.context
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        } ?: return 0f

        val modes = display.supportedModes?.takeIf { it.isNotEmpty() } ?: return 0f
        val currentMode = runCatching { display.mode }.getOrNull()
        // 同一个分辨率下才有可比性：分辨率不同时改模式会让布局/位图全部重建
        val sameSize = modes.filter {
            currentMode == null ||
                (it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight)
        }.ifEmpty { modes.toList() }

        val hardwarePeak = sameSize.maxOf { it.refreshRate }
        // 直接顶到同分辨率下的最高档。
        //
        // 早先这里会读系统的 `peak_refresh_rate`（用户在「显示 → 屏幕刷新率」里选的档位），
        // 但那玩意儿在多数国产 ROM 上是「智能切换」的隐藏值，读到的经常是 60 或干脆读不到，
        // 结果就是 120Hz 的屏被锁在 60。用户的要求很明确：跑到手机支持的最高帧率。
        // 所以这里不再看系统设置，取硬件峰值；想省电就让用户在系统里自己限帧，
        // 系统限了之后 preferredDisplayModeId 会被系统收敛，不会硬顶。
        val userPeak = hardwarePeak
        // 用户设的档位可能不是设备支持的精确值（比如 90 在只有 60/120 的屏上），
        // 取「不超过它」的最高一档；一档都不满足（设得比 60 还低）就不动
        val best = sameSize
            .filter { it.refreshRate <= userPeak + 0.5f }
            .maxByOrNull { it.refreshRate }
            ?: return 0f

        val attrs = window.attributes
        // preferredRefreshRate 是「声明这窗口想跑多少」，系统会按用户设置的峰值上限收敛；
        // preferredDisplayModeId 是「直接切到这个显示模式」，更硬，但也可能引起一次模式切换闪烁。
        // 所以只在当前确实还没跑到目标档时才指定 modeId，已经够高就只做声明。
        attrs.preferredRefreshRate = best.refreshRate
        val current = runCatching { display.refreshRate }.getOrDefault(60f)
        if (best.refreshRate > current + 0.5f) {
            attrs.preferredDisplayModeId = best.modeId
        }
        window.attributes = attrs

        // Android 15 起系统按窗口声明的 frame rate 选档，不声明会被当成 60Hz 处理。
        //
        // 这一句必须在 decorView 已经 attach 到窗口之后调，否则请求会被静默丢弃：
        // onCreate 里第一次 apply 时窗口常常还没 attach（setContent 之后才 attach），
        // 所以这里不仅当场调一次，还要 post 到消息队列里等 attach 完再补一次。
        // 失败也静默 —— 老系统 / 部分厂商禁用了这个接口，维持系统默认即可。
        if (Build.VERSION.SDK_INT >= 35) {
            val rate = best.refreshRate
            runCatching { window.decorView.setRequestedFrameRate(rate) }
            window.decorView.post {
                runCatching { window.decorView.setRequestedFrameRate(rate) }
                Log.i(TAG, "刷新率已按窗口声明 $rate Hz（attach 后补发）")
            }
        }
        Log.i(TAG, "刷新率已申请 ${best.refreshRate}Hz（硬件峰值 ${hardwarePeak}Hz）")
        best.refreshRate
    }.getOrDefault(0f)
}
