package com.leapcontrol.app.ui

import com.leapcontrol.app.AppearanceMode

/**
 * 主题解析：把「外观模式 + 手机明暗 + 两个槽位里存的主题」推导成
 * 「现在该用深色还是浅色」以及「现在生效的是哪一套主题」。
 *
 * 抽成纯函数（不碰 Compose、不碰 SharedPreferences），一是让四种模式的行为
 * 有唯一出口，二是能直接跑 JVM 单测 —— 这块分支组合多，靠手点验证容易漏。
 *
 * 两个槽位：
 *  - 浅色槽（lightThemeId）：界面为浅色时用哪套，只能是浅色主题；
 *  - 深色槽（darkThemeId）：界面为深色时用哪套，只能是深色主题。
 */
object ThemeResolver {

    /**
     * 界面是否用深色。
     *
     * @param nightOverride 夜间时段（22:00–06:00）强制深色，仅自由模式会传 true。
     */
    fun isDark(
        mode: AppearanceMode,
        systemDark: Boolean,
        nightOverride: Boolean = false,
    ): Boolean = mode.lockedDark
        // 夜间覆盖只属于自由模式；其余模式即使传了 true 也不该被顶翻
        ?: (systemDark || (nightOverride && supportsNightOverride(mode)))

    /** 当前生效主题：按明暗取对应槽位，槽里的主题类型不对时回落到该类型的默认主题。 */
    fun activeTheme(
        mode: AppearanceMode,
        dark: Boolean,
        lightThemeId: String?,
        darkThemeId: String?,
    ): SeasonTheme {
        // 锁定模式以模式为准（用户明确选了「始终浅色/始终深色」），
        // 其余模式按当前明暗取槽
        val wantDark = mode.lockedDark ?: dark
        return if (wantDark) safeDark(darkThemeId) else safeLight(lightThemeId)
    }

    /** 浅色槽里的主题必须是浅色主题，否则回落到第一套日间主题。 */
    fun safeLight(id: String?): SeasonTheme =
        SeasonThemes.byId(id).takeIf { !it.night } ?: SeasonThemes.DAY.first()

    /** 深色槽里的主题必须是深色主题，否则回落到默认夜间主题。 */
    fun safeDark(id: String?): SeasonTheme =
        SeasonThemes.byId(id).takeIf { it.night } ?: SeasonThemes.NIGHT

    /** 该模式下这套主题能不能选：始终浅色只能浅色主题，始终深色只能深色主题。 */
    fun canPick(mode: AppearanceMode, theme: SeasonTheme): Boolean = when (mode) {
        AppearanceMode.LIGHT -> !theme.night
        AppearanceMode.DARK -> theme.night
        AppearanceMode.SYSTEM, AppearanceMode.FREE -> true
    }

    /**
     * 跟随系统时，选这套主题要不要先提示「会关闭跟随」。
     *
     * 判据：主题的明暗和手机当前明暗相反 —— 应用它就必然要改明暗，
     * 而「跟随系统」不允许改明暗，只能退出跟随。同侧主题不打扰。
     */
    fun needsFollowExitPrompt(
        mode: AppearanceMode,
        systemDark: Boolean,
        theme: SeasonTheme,
    ): Boolean = mode == AppearanceMode.SYSTEM && theme.night != systemDark

    /** 确认退出跟随之后，模式应该落到哪个固定明暗。 */
    fun modeAfterFollowExit(theme: SeasonTheme): AppearanceMode =
        if (theme.night) AppearanceMode.DARK else AppearanceMode.LIGHT

    /** 该模式下是否显示「夜间时段强制深色」开关（只有自由模式需要，其余已锁定或纯粹跟随）。 */
    fun supportsNightOverride(mode: AppearanceMode): Boolean = mode == AppearanceMode.FREE
}
