package com.leapcontrol.app

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * 刷新率：只「声明偏好」，不指定显示模式。
 *
 * ## 和曾经的 `preferredDisplayModeId` 有什么区别
 *
 * 早先的 `apply()` 用 `WindowManager.LayoutParams.preferredDisplayModeId` **指定了具体的
 * 显示模式 id**，并且在 onResume / onWindowFocusChanged 里反复调用。那等于每次都要系统
 * 做一次「显示模式切换 → VSYNC 时钟重同步」，正好发生在滑动过程中时就直接打断滚动节奏，
 * 表现为「渲染很轻却一直掉帧」。这段历史教训不要再重演。
 *
 * 这里用的是 [`preferredRefreshRate`]：只告诉窗口管理器「我希望按这个帧率出帧」，
 * 由它自己挑最接近的模式，**不指定 id、不强制切模式**。而且只在 onResume 设一次，
 * 不在焦点变化里反复设 —— 反复协商本身就是抖动来源。
 */
object DisplayRefresh {

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

    /** 这台设备的硬件峰值刷新率（拿不到时按 60 处理）。 */
    fun peakOf(display: Display?): Float = runCatching {
        display?.supportedModes?.maxOfOrNull { it.refreshRate }
    }.getOrNull() ?: 60f

    /**
     * 当前窗口所在的 Display。
     *
     * **不能直接读 `activity.display`**：那是 `Context.getDisplay()`，Android **API 30**
     * 才加进公开接口，API 26~29 上调用会 `NoSuchMethodError` 直接闪退
     * （1.0.17 在 Android 10 上启动即崩，就是 `onResume` 里这次调用）。
     * 低版本退回 `WindowManager.defaultDisplay`：虽然已标废弃，但 API 1 起就有、行为一致。
     */
    private fun displayOf(activity: Activity): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager?.defaultDisplay
        }

    /**
     * 声明「本窗口希望按最高刷新率出帧」。
     *
     * 为什么需要它：系统的动态刷新率会盯着应用出帧的节奏判断该给多少 Hz。
     * 界面上只要还有一个按 30fps 推进的动画，就会被判成「只需 30Hz」而降频；
     * 用户一滑动又提到 120 —— 这就是「画面在 30 和 120 之间来回横跳」的来源。
     * 把动画改成每帧推进是治本，但静态主题下界面本来就没有持续出帧的理由，
     * 需要明确声明一次意图，系统才不会又滑回低频。
     *
     * 值没变就不重复写：`window.attributes` 每次赋值都会走一遍窗口布局，
     * 无意义的重复设置反而可能引入抖动。
     */
    fun applyMaxFrameRate(activity: Activity) {
        val peak = peakOf(displayOf(activity))
        // 60Hz 的屏没什么可声明的，跳过省一次窗口属性更新
        if (peak <= 60f) return
        val window = activity.window ?: return
        runCatching {
            val lp = window.attributes
            if (lp.preferredRefreshRate != peak) {
                lp.preferredRefreshRate = peak
                window.attributes = lp
            }
        }
    }
}
