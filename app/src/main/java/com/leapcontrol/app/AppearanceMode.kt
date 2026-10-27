package com.leapcontrol.app

/**
 * 外观模式（个性化页的第一层选择）。
 *
 * 三种模式决定「界面明暗从哪来」以及「能选哪一类主题」：
 *  - [SYSTEM] 跟随系统：明暗跟着手机的深色模式走，浅色 / 深色两侧各记一套主题，
 *    搭配使用 —— 白天用日间那套、夜里用夜间那套，随手机自动切。
 *  - [LIGHT] 始终浅色：明暗锁定浅色，只能选浅色主题。
 *  - [DARK] 始终深色：明暗锁定深色，只能选深色主题。
 *
 * 视频主题是唯一例外：它自带「浅色一组（春夏秋冬）+ 深色一套（星空）」的搭配，
 * 选到任意一套外观都会切回 [SYSTEM]，见 `ThemeResolver.pick`。
 */
enum class AppearanceMode(
    val label: String,
    /** 选项卡上的一句话说明 */
    val desc: String,
) {
    SYSTEM("跟随系统", "浅色用日间、深色用夜间，两套搭配"),
    LIGHT("始终浅色", "锁定浅色界面，只能选浅色主题"),
    DARK("始终深色", "锁定深色界面，只能选深色主题");

    /**
     * 该模式是否把明暗锁死：返回 true / false 表示锁定结果，null 表示交给系统。
     *
     * [SYSTEM] 返回 null —— 明暗完全由手机决定，App 只在两个槽位里取对应那一套。
     */
    val lockedDark: Boolean?
        get() = when (this) {
            LIGHT -> false
            DARK -> true
            SYSTEM -> null
        }

    /** 兼容旧调用点：把「跟随系统」当成按系统明暗解析。 */
    fun resolvesToDark(systemInDarkTheme: Boolean): Boolean =
        lockedDark ?: systemInDarkTheme

    companion object {
        fun fromPreference(value: String?): AppearanceMode = when (value) {
            // 旧版的「自由模式」已并入跟随系统：同样是「明暗跟手机 + 浅深各一套」，
            // 两者合并后没有行为差异，老用户升级不会觉得设定被改。
            "FREE" -> SYSTEM
            else -> entries.firstOrNull { it.name == value } ?: SYSTEM
        }
    }
}
