package com.leapcontrol.app

import android.content.Context
import android.os.Build
import android.provider.Settings
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
 * 取哪一档不是无脑取硬件最高：先读系统设置里的 `peak_refresh_rate`（用户在
 * 「设置 → 显示 → 屏幕刷新率」里选的档位，比如 120 或 144），按它来；读不到
 * （隐藏设置项、厂商改过 key）才退回「硬件支持的最高档」。这样用户把手机设成
 * 60Hz 省电时 App 不会硬顶到 144，设成 144 时也不会被锁在 120。
 */
object DisplayRefresh {

    private const val TAG = "LeapControl"

    /** 系统设置里用户选的峰值刷新率；读不到返回 0。 */
    private fun userPeakRate(context: Context): Float = runCatching {
        Settings.System.getFloat(context.contentResolver, "peak_refresh_rate", 0f)
    }.getOrDefault(0f)

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
        val userPeak = userPeakRate(context).takeIf { it > 0f } ?: hardwarePeak
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

        // Android 15 起系统按窗口声明的 frame rate 选档，不声明会被当成 60Hz 处理
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching { window.decorView.setRequestedFrameRate(best.refreshRate) }
        }
        Log.i(TAG, "刷新率已申请 ${best.refreshRate}Hz（硬件峰值 ${hardwarePeak}Hz）")
        best.refreshRate
    }.getOrDefault(0f)
}
