package com.leapcontrol.app.widget

import android.content.Context

/**
 * 小组件右上角的「刚刚控车的结果」短时提示。
 *
 * ## 为什么需要这一层
 * 车端 T-Box 唤醒 + 执行要 5~15 秒，报文回得更慢。控车成功后如果立刻回到
 * 「12:30 更新」，用户看到的就是「点了上锁，卡片什么也没变」——
 * 这正是 2026-11-05 那条反馈。所以把结果先记在这里，卡片右上角直接说
 * 「车辆已上锁 / 空调已开启」，等真实车况跟上再自然交回去。
 *
 * ## 为什么用 prefs 而不是内存字段
 * 小组件的渲染跑在桌面进程发起的广播里，进程随时可能被回收；写进 prefs 之后
 * 即使这一帧之后进程没了，下次重画（桌面定时更新、改尺寸）仍能带上这条结果。
 *
 * 只放**一条**：控车是串行的人机动作，新的结果覆盖旧的比排队更符合直觉。
 */
object WidgetFlash {

    private const val PREFS = "widget_flash"
    private const val KEY_TEXT = "text"
    private const val KEY_AT = "at"
    private const val KEY_OK = "ok"
    private const val KEY_HINT = "hint"

    /**
     * 结果在右上角停留多久。
     *
     * 2 秒：够用户看清「车辆已上锁」这一句，随即交回「HH:mm 更新」/
     * 充电中 / 未锁车这些真正该常显的车况。用户明确要求「过一会儿就切回来，
     * 不要一直挂着上一轮的结果」—— 之前 20 秒太长，看起来像卡片卡住了。
     *
     * 到期后必须**主动重画一帧**（见 `VehicleWidgetProvider.scheduleFlashExpiry`）：
     * 这里只是让 [read] 返回 null，桌面不重画的话，那行字会一直停在旧结果上，
     * 直到下次有事件（刷新、控车、改尺寸）才更新。
     */
    const val TTL_MS = 2_000L

    /**
     * @param text 要显示的完成式短句，如「车辆已解锁」
     * @param ok true = 车端回执确认已执行（主色）；false = 只下发、没等到回执（警示色）
     * @param hint 这次动作让**哪个按键**应该亮起来，取值见 [HINT_UNLOCK] 等。
     *   有了它，右上角写「空调已开启」的同一帧里，空调键也已经是高亮的 ——
     *   否则要等 2~3 秒后的车况回读，中间那一下文字和按键互相矛盾。
     */
    data class Flash(val text: String, val ok: Boolean, val hint: String?)

    /** 结果意图：车已解锁 → 解锁键亮。 */
    const val HINT_UNLOCK = "unlock"

    /** 结果意图：车已上锁 → 上锁键**不亮**（见 VehicleWidget.bindLive 的高亮规则）。 */
    const val HINT_LOCK = "lock"
    const val HINT_AC_ON = "acOn"
    const val HINT_AC_OFF = "acOff"
    const val HINT_TRUNK_OPEN = "trunkOpen"
    const val HINT_TRUNK_CLOSE = "trunkClose"

    fun write(context: Context, text: String, ok: Boolean, hint: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEXT, text)
            .putLong(KEY_AT, System.currentTimeMillis())
            .putBoolean(KEY_OK, ok)
            .putString(KEY_HINT, hint)
            .apply()
    }

    /** 读当前有效的那条；过期就当没有（顺便不清理，下次写入会覆盖）。 */
    fun read(context: Context): Flash? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (at == 0L || System.currentTimeMillis() - at > TTL_MS) return null
        return Flash(text, prefs.getBoolean(KEY_OK, true), prefs.getString(KEY_HINT, null))
    }

    /** 用户主动点了刷新，说明想看真实车况 —— 结果提示立刻让位。 */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
