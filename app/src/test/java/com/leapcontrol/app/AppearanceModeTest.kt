package com.leapcontrol.app

import com.leapcontrol.app.ui.SeasonThemes
import com.leapcontrol.app.ui.ThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观模式 × 主题槽位的推演规则。
 *
 * 这块是「三种模式 + 两个槽位 + 系统明暗」的组合，手点很容易漏掉某条分支，
 * 所以把规则钉在单测里：只要有人改了 [ThemeResolver]，这里会立刻报出来。
 *
 * 定稿语义：
 *  - 三种模式：跟随系统（浅深各一套，搭配用）/ 始终浅色 / 始终深色；
 *  - 锁定模式下只能选同侧主题，反向主题点了不改动、只给提示。
 */
class AppearanceModeTest {

    private val light = SeasonThemes.MIST_HILLS
    private val dark = SeasonThemes.STARRY

    // ---------------- 模式与明暗 ----------------

    @Test
    fun `只有三种模式`() {
        assertEquals(
            listOf(AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK),
            AppearanceMode.entries.toList(),
        )
    }

    @Test
    fun `始终浅色永远不深`() {
        assertFalse(ThemeResolver.isDark(AppearanceMode.LIGHT, systemDark = true))
        assertFalse(ThemeResolver.isDark(AppearanceMode.LIGHT, systemDark = false))
    }

    @Test
    fun `始终深色永远深`() {
        assertTrue(ThemeResolver.isDark(AppearanceMode.DARK, systemDark = false))
        assertTrue(ThemeResolver.isDark(AppearanceMode.DARK, systemDark = true))
    }

    @Test
    fun `跟随系统只看手机`() {
        assertTrue(ThemeResolver.isDark(AppearanceMode.SYSTEM, systemDark = true))
        assertFalse(ThemeResolver.isDark(AppearanceMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `旧版自由模式回落到跟随系统`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference("FREE"))
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromPreference(mode.name))
        }
        // 未知值 / 空值一律回到跟随系统
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference("LEGACY"))
    }

    // ---------------- 生效主题 ----------------

    @Test
    fun `跟随系统浅色取浅色槽 深色取深色槽`() {
        assertEquals(light.id, ThemeResolver.activeTheme(AppearanceMode.SYSTEM, false, light.id, dark.id).id)
        assertEquals(dark.id, ThemeResolver.activeTheme(AppearanceMode.SYSTEM, true, light.id, dark.id).id)
    }

    @Test
    fun `始终浅色即使传入深色也不会取到夜间主题`() {
        // dark 参数误传 true 也不该翻车 —— 锁定模式以模式为准
        val theme = ThemeResolver.activeTheme(AppearanceMode.LIGHT, true, light.id, dark.id)
        assertFalse(theme.night)
        assertEquals(light.id, theme.id)
    }

    @Test
    fun `始终深色不会取到浅色主题`() {
        val theme = ThemeResolver.activeTheme(AppearanceMode.DARK, false, light.id, dark.id)
        assertTrue(theme.night)
        assertEquals(dark.id, theme.id)
    }

    @Test
    fun `槽里存错类型时回落到该类型的默认主题`() {
        // 浅色槽里是夜间主题 → 回落日间默认，绝不出现「浅色界面配深空底」
        assertEquals(SeasonThemes.DAY.first().id, ThemeResolver.safeLight(dark.id).id)
        // 深色槽里是日间主题 → 回落默认夜间主题
        assertEquals(SeasonThemes.NIGHT.id, ThemeResolver.safeDark(light.id).id)
        // 空值同样回落
        assertEquals(SeasonThemes.DAY.first().id, ThemeResolver.safeLight(null).id)
        assertEquals(SeasonThemes.NIGHT.id, ThemeResolver.safeDark(null).id)
    }

    @Test
    fun `夜间主题属于深色槽`() {
        assertTrue(dark.night)
        assertEquals(dark.id, ThemeResolver.safeDark(dark.id).id)
        // 深色主题放进浅色槽 → 回落到日间默认主题，不能直接拿来当浅色用
        assertEquals(SeasonThemes.DAY.first().id, ThemeResolver.safeLight(dark.id).id)
    }

    // ---------------- 能不能选 ----------------

    @Test
    fun `始终浅色只能选浅色主题`() {
        assertTrue(ThemeResolver.canPick(AppearanceMode.LIGHT, light))
        assertFalse(ThemeResolver.canPick(AppearanceMode.LIGHT, dark))
    }

    @Test
    fun `始终深色只能选深色主题`() {
        assertTrue(ThemeResolver.canPick(AppearanceMode.DARK, dark))
        assertFalse(ThemeResolver.canPick(AppearanceMode.DARK, light))
    }

    @Test
    fun `锁定模式下反向主题不可选`() {
        // 始终浅色时夜间主题置灰、始终深色时日间主题置灰（与 canPick 的规则一致）
        assertFalse(ThemeResolver.canPick(AppearanceMode.LIGHT, dark))
        assertFalse(ThemeResolver.canPick(AppearanceMode.DARK, light))
        SeasonThemes.NIGHT_ALL.forEach { assertFalse(it.label, ThemeResolver.canPick(AppearanceMode.LIGHT, it)) }
        SeasonThemes.DAY.forEach { assertFalse(it.label, ThemeResolver.canPick(AppearanceMode.DARK, it)) }
    }

    @Test
    fun `跟随系统两类都能选`() {
        assertTrue(ThemeResolver.canPick(AppearanceMode.SYSTEM, light))
        assertTrue(ThemeResolver.canPick(AppearanceMode.SYSTEM, dark))
    }

    // ---------------- 选主题：跟随系统各落各槽 ----------------

    @Test
    fun `跟随时选日间主题只改浅色槽`() {
        val pick = ThemeResolver.pick(
            mode = AppearanceMode.SYSTEM,
            systemDark = false,
            theme = SeasonThemes.SAKURA,
            lightThemeId = light.id,
            darkThemeId = dark.id,
        )
        assertEquals(AppearanceMode.SYSTEM, pick.mode)
        assertEquals(SeasonThemes.SAKURA.id, pick.lightThemeId)
        assertEquals(dark.id, pick.darkThemeId) // 深色槽不动
        assertEquals(ThemeResolver.PickHint.NONE, pick.hint) // 当前就是浅色，立刻生效
    }

    @Test
    fun `跟随时选夜间主题只改深色槽`() {
        val pick = ThemeResolver.pick(
            mode = AppearanceMode.SYSTEM,
            systemDark = true,
            theme = SeasonThemes.AURORA,
            lightThemeId = light.id,
            darkThemeId = dark.id,
        )
        assertEquals(AppearanceMode.SYSTEM, pick.mode)
        assertEquals(light.id, pick.lightThemeId) // 浅色槽不动
        assertEquals(SeasonThemes.AURORA.id, pick.darkThemeId)
        assertEquals(ThemeResolver.PickHint.NONE, pick.hint)
    }

    @Test
    fun `跟随时选另一侧主题要提示`() {
        // 手机是浅色，却点了夜间主题：界面此刻不会变，得说一句
        val night = ThemeResolver.pick(
            mode = AppearanceMode.SYSTEM,
            systemDark = false,
            theme = dark,
            lightThemeId = light.id,
            darkThemeId = SeasonThemes.STARRY.id,
        )
        assertEquals(ThemeResolver.PickHint.OTHER_SIDE, night.hint)

        // 反过来：手机是深色，点日间主题
        val day = ThemeResolver.pick(
            mode = AppearanceMode.SYSTEM,
            systemDark = true,
            theme = light,
            lightThemeId = light.id,
            darkThemeId = dark.id,
        )
        assertEquals(ThemeResolver.PickHint.OTHER_SIDE, day.hint)
    }

    @Test
    fun `跟随模式下选主题绝不改明暗`() {
        // 这是这次改版的核心：跟随系统下选明暗相反的主题，不再弹「退出跟随」
        listOf(true, false).forEach { systemDark ->
            listOf(light, dark).forEach { theme ->
                val pick = ThemeResolver.pick(
                    AppearanceMode.SYSTEM, systemDark, theme, light.id, dark.id,
                )
                assertEquals(AppearanceMode.SYSTEM, pick.mode)
            }
        }
    }

    // ---------------- 锁定模式 ----------------

    @Test
    fun `始终浅色点夜间主题什么都不改`() {
        val pick = ThemeResolver.pick(
            mode = AppearanceMode.LIGHT,
            systemDark = true,
            theme = dark,
            lightThemeId = light.id,
            darkThemeId = dark.id,
        )
        assertEquals(AppearanceMode.LIGHT, pick.mode)
        assertEquals(light.id, pick.lightThemeId)
        assertEquals(dark.id, pick.darkThemeId)
        assertEquals(ThemeResolver.PickHint.BLOCKED, pick.hint)
    }

    @Test
    fun `始终深色点日间主题什么都不改`() {
        val pick = ThemeResolver.pick(
            mode = AppearanceMode.DARK,
            systemDark = false,
            theme = light,
            lightThemeId = light.id,
            darkThemeId = dark.id,
        )
        assertEquals(AppearanceMode.DARK, pick.mode)
        assertEquals(ThemeResolver.PickHint.BLOCKED, pick.hint)
        assertEquals(light.id, pick.lightThemeId)
    }

    @Test
    fun `始终浅色点浅色主题正常落槽`() {
        val pick = ThemeResolver.pick(
            mode = AppearanceMode.LIGHT,
            systemDark = true,
            theme = SeasonThemes.CLEAR_SKY,
            lightThemeId = light.id,
            darkThemeId = dark.id,
        )
        assertEquals(AppearanceMode.LIGHT, pick.mode)
        assertEquals(SeasonThemes.CLEAR_SKY.id, pick.lightThemeId)
        assertEquals(ThemeResolver.PickHint.NONE, pick.hint)
    }

    // ---------------- 槽位合法性 ----------------

    @Test
    fun `任何主题点完都落在合法槽位`() {
        SeasonThemes.ALL.forEach { theme ->
            val pick = ThemeResolver.pick(
                AppearanceMode.SYSTEM, false, theme, light.id, dark.id,
            )
            // 浅色槽不能是深色主题、深色槽不能是浅色主题
            assertFalse(theme.label, SeasonThemes.byId(pick.lightThemeId).night)
            assertTrue(theme.label, SeasonThemes.byId(pick.darkThemeId).night)
            // 同一套主题同时占两个槽会让「深浅切换」看起来毫无变化
            assertNotEquals(theme.label, pick.lightThemeId, pick.darkThemeId)
        }
    }
}
