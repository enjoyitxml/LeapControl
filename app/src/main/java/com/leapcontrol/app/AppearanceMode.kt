package com.leapcontrol.app

/**
 * 外观模式（个性化页的第一层选择）。
 *
 * 四种模式决定「界面明暗从哪来」以及「能选哪一类主题」：
 *  - [SYSTEM] 跟随系统：明暗跟着手机的深色模式走，浅色 / 深色两侧各记一套主题；
 *    在浅色系统下选深色主题会提示「将退出跟随」，确认后固定为深色。
 *  - [LIGHT] 始终浅色：明暗锁定浅色，只能选浅色主题。
 *  - [DARK] 始终深色：明暗锁定深色，只能选深色主题。
 *  - [FREE] 自由模式：明暗仍跟手机走，但浅色 / 深色用哪套主题完全由用户指定，
 *    选主题不会改变明暗，也不会退出跟随。
 */
enum class AppearanceMode(
    val label: String,
    /** 选项卡上的一句话说明 */
    val desc: String,
) {
    SYSTEM("跟随系统", "随手机深浅模式切换，白天黑夜各一套"),
    LIGHT("始终浅色", "锁定浅色界面，只能选浅色主题"),
    DARK("始终深色", "锁定深色界面，只能选深色主题"),
    FREE("自由模式", "明暗跟手机，浅色/深色各指定一套");

    /**
     * 该模式是否把明暗锁死：返回 true / false 表示锁定结果，null 表示交给系统。
     *
     * 注意 [SYSTEM] 与 [FREE] 都返回 null —— 两者的区别不在明暗来源（都跟手机），
     * 而在「选主题时是否会影响明暗」，见 [ThemeResolver]。
     */
    val lockedDark: Boolean?
        get() = when (this) {
            LIGHT -> false
            DARK -> true
            SYSTEM, FREE -> null
        }

    /** 兼容旧调用点：把「跟随系统」当成按系统明暗解析。 */
    fun resolvesToDark(systemInDarkTheme: Boolean): Boolean =
        lockedDark ?: systemInDarkTheme

    companion object {
        fun fromPreference(value: String?): AppearanceMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
