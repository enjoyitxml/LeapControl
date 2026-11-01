package com.leapcontrol.app

import android.view.Display

/**
 * 显示器刷新率描述（仅供帧率浮层 / 设置页展示）。
 *
 * ⚠️ 这里**不再**包含任何「主动申请 / 切换显示模式」的逻辑。
 *
 * 历史教训：早先的 `apply()` 会用 `WindowManager.LayoutParams.preferredDisplayModeId`
 * 「硬切显示模式」，并且在 onResume / onWindowFocusChanged 里反复调用。这个操作会触发
 * 系统做「显示模式切换 → VSYNC 时钟重同步」，正好发生在滑动过程中时就直接打断滚动节奏，
 * 表现为「渲染很轻却一直掉帧」。用户在系统里设「固定 120Hz」后系统会自行给足高刷，
 * App 侧什么都不该做 —— 反复请求反而和系统打架，越修越卡。
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
}
