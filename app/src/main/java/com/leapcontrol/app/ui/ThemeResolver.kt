package com.leapcontrol.app.ui

import com.leapcontrol.app.AppearanceMode

/**
 * 主题解析：把「外观模式 + 手机明暗 + 两个槽位里存的主题」推导成
 * 「现在该用深色还是浅色」以及「现在生效的是哪一套主题」。
 *
 * 抽成纯函数（不碰 Compose、不碰 SharedPreferences），一是让三种模式的行为
 * 有唯一出口，二是能直接跑 JVM 单测 —— 这块分支组合多，靠手点验证容易漏。
 *
 * 两个槽位：
 *  - 浅色槽（lightThemeId）：界面为浅色时用哪套，只能是浅色主题；
 *  - 深色槽（darkThemeId）：界面为深色时用哪套，只能是深色主题。
 */
object ThemeResolver {

    /** 界面是否用深色：锁定模式直接给结果，跟随系统就看手机。 */
    fun isDark(mode: AppearanceMode, systemDark: Boolean): Boolean =
        mode.lockedDark ?: systemDark

    /** 当前生效主题：按明暗取对应槽位，槽里的主题类型不对时回落到该类型的默认主题。 */
    fun activeTheme(
        mode: AppearanceMode,
        dark: Boolean,
        lightThemeId: String?,
        darkThemeId: String?,
    ): SeasonTheme {
        // 锁定模式以模式为准（用户明确选了「始终浅色 / 始终深色」），
        // 跟随系统按当前明暗取槽
        val wantDark = mode.lockedDark ?: dark
        return if (wantDark) safeDark(darkThemeId) else safeLight(lightThemeId)
    }

    /** 浅色槽里的主题必须是浅色主题，否则回落到第一套日间主题。 */
    fun safeLight(id: String?): SeasonTheme =
        SeasonThemes.byId(id).takeIf { !it.night } ?: SeasonThemes.DAY.first()

    /** 深色槽里的主题必须是深色主题，否则回落到默认夜间主题。 */
    fun safeDark(id: String?): SeasonTheme =
        SeasonThemes.byId(id).takeIf { it.night } ?: SeasonThemes.NIGHT

    /**
     * 该模式下这套主题能不能选：始终浅色只能浅色主题，始终深色只能深色主题。
     *
     * 视频主题例外 —— 它自带深浅搭配，选到就会切回跟随系统，
     * 所以在锁定模式下也不置灰（点了只是换模式，不会出现黑底黑字）。
     */
    fun canPick(mode: AppearanceMode, theme: SeasonTheme): Boolean = when (mode) {
        AppearanceMode.LIGHT -> !theme.night || theme.video
        AppearanceMode.DARK -> theme.night || theme.video
        AppearanceMode.SYSTEM -> true
    }

    /** 点一套主题之后需要的额外提示。纯逻辑只描述意图，文案留在 UI 层。 */
    enum class PickHint {
        /** 一切按预期生效，不用多话 */
        NONE,

        /** 点的是另一侧主题：界面明暗没变，等切到那侧才生效 */
        OTHER_SIDE,

        /** 视频主题自带深浅搭配，外观已切回「跟随系统」 */
        VIDEO_FOLLOWED,

        /** 当前模式不允许这类主题，什么都没改 */
        BLOCKED,
    }

    /** 一次选主题的完整结果：模式 + 两个槽位 + 要不要提示。 */
    data class Pick(
        val mode: AppearanceMode,
        val lightThemeId: String,
        val darkThemeId: String,
        val hint: PickHint = PickHint.NONE,
    )

    /**
     * 用户在外观模式 [mode] 下点了 [theme]，算出「模式与两个槽位各应变成什么」。
     *
     * 规则：
     *  - **视频主题**：自带一组深浅搭配（浅色春夏秋冬 / 深色星空），点是哪套就落哪一侧，
     *    另一侧若还不是视频主题则补上同组默认，外观切回跟随系统 —— 一路看下来
     *    视频主题永远是「白天一个样、夜里另一个样」，不需要用户自己配两次；
     *  - **始终浅色 / 始终深色**：只接受同侧主题，反向主题原样返回不改动；
     *  - **跟随系统**：按主题明暗落槽，两个槽互不影响；点到另一侧时给一句提示，
     *    因为界面此刻不会有任何变化。
     */
    fun pick(
        mode: AppearanceMode,
        systemDark: Boolean,
        theme: SeasonTheme,
        lightThemeId: String?,
        darkThemeId: String?,
    ): Pick {
        val light = safeLight(lightThemeId)
        val dark = safeDark(darkThemeId)

        // ---- 视频主题：成套使用，永远跟随系统 ----
        if (theme.video) {
            val hint = if (mode == AppearanceMode.SYSTEM) PickHint.NONE else PickHint.VIDEO_FOLLOWED
            return if (theme.night) {
                Pick(
                    mode = AppearanceMode.SYSTEM,
                    // 浅色侧保留用户已选的视频主题（比如夏日），否则补默认的春日
                    lightThemeId = if (light.video) light.id else SeasonThemes.VIDEO_LIGHT.first().id,
                    darkThemeId = theme.id,
                    hint = hint,
                )
            } else {
                Pick(
                    mode = AppearanceMode.SYSTEM,
                    lightThemeId = theme.id,
                    darkThemeId = if (dark.video) dark.id else SeasonThemes.VIDEO_DARK.id,
                    hint = hint,
                )
            }
        }

        if (!canPick(mode, theme)) {
            // 反向主题：什么都不改，只回一句原因
            return Pick(mode, light.id, dark.id, PickHint.BLOCKED)
        }

        return if (theme.night) {
            Pick(
                mode = mode,
                lightThemeId = light.id,
                darkThemeId = theme.id,
                // 跟随时选了夜间主题：界面还是亮的，说一句免得以为没点到
                hint = if (mode == AppearanceMode.SYSTEM && !systemDark) {
                    PickHint.OTHER_SIDE
                } else {
                    PickHint.NONE
                },
            )
        } else {
            Pick(
                mode = mode,
                lightThemeId = theme.id,
                darkThemeId = dark.id,
                hint = if (mode == AppearanceMode.SYSTEM && systemDark) {
                    PickHint.OTHER_SIDE
                } else {
                    PickHint.NONE
                },
            )
        }
    }
}
