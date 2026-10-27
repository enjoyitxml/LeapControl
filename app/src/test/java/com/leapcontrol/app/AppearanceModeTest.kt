package com.leapcontrol.app

import com.leapcontrol.app.ui.SeasonThemes
import com.leapcontrol.app.ui.ThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观模式 × 主题槽位的推演规则。
 *
 * 这块是「四种模式 + 两套槽位 + 系统明暗」的组合，手点很容易漏掉某条分支，
 * 所以把规则钉在单测里：只要有人改了 [ThemeResolver]，这里会立刻报出来。
 */
class AppearanceModeTest {

    private val light = SeasonThemes.MIST_HILLS
    private val dark = SeasonThemes.STARRY
    private val darkVideo = SeasonThemes.VIDEO_STARS

    // ---------------- 明暗 ----------------

    @Test
    fun `始终浅色永远不深`() {
        assertFalse(ThemeResolver.isDark(AppearanceMode.LIGHT, systemDark = true))
        assertFalse(ThemeResolver.isDark(AppearanceMode.LIGHT, systemDark = false))
        // 夜间覆盖对锁定模式无效：用户明确选了「始终浅色」，到点也不该翻黑
        assertFalse(ThemeResolver.isDark(AppearanceMode.LIGHT, systemDark = true, nightOverride = true))
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
        // 夜间覆盖只属于自由模式
        assertFalse(ThemeResolver.isDark(AppearanceMode.SYSTEM, systemDark = false, nightOverride = true))
    }

    @Test
    fun `自由模式看手机并可被夜间时段顶成深色`() {
        assertFalse(ThemeResolver.isDark(AppearanceMode.FREE, systemDark = false))
        assertTrue(ThemeResolver.isDark(AppearanceMode.FREE, systemDark = true))
        assertTrue(ThemeResolver.isDark(AppearanceMode.FREE, systemDark = false, nightOverride = true))
    }

    @Test
    fun `夜间覆盖只对自由模式开放`() {
        assertTrue(ThemeResolver.supportsNightOverride(AppearanceMode.FREE))
        assertFalse(ThemeResolver.supportsNightOverride(AppearanceMode.SYSTEM))
        assertFalse(ThemeResolver.supportsNightOverride(AppearanceMode.LIGHT))
        assertFalse(ThemeResolver.supportsNightOverride(AppearanceMode.DARK))
    }

    // ---------------- 生效主题 ----------------

    @Test
    fun `浅色取浅色槽 深色取深色槽`() {
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
    fun `星空视频属于深色槽`() {
        assertTrue(darkVideo.night)
        assertEquals(darkVideo.id, ThemeResolver.safeDark(darkVideo.id).id)
        // 深色主题放进浅色槽 → 回落到日间默认主题，不能直接拿来当浅色用
        assertEquals(SeasonThemes.DAY.first().id, ThemeResolver.safeLight(darkVideo.id).id)
    }

    // ---------------- 能不能选 ----------------

    @Test
    fun `始终浅色只能选浅色主题`() {
        assertTrue(ThemeResolver.canPick(AppearanceMode.LIGHT, light))
        assertFalse(ThemeResolver.canPick(AppearanceMode.LIGHT, dark))
        assertFalse(ThemeResolver.canPick(AppearanceMode.LIGHT, darkVideo))
    }

    @Test
    fun `始终深色只能选深色主题`() {
        assertTrue(ThemeResolver.canPick(AppearanceMode.DARK, dark))
        assertTrue(ThemeResolver.canPick(AppearanceMode.DARK, darkVideo))
        assertFalse(ThemeResolver.canPick(AppearanceMode.DARK, light))
    }

    @Test
    fun `跟随与自由都能选两类`() {
        listOf(AppearanceMode.SYSTEM, AppearanceMode.FREE).forEach { mode ->
            assertTrue(mode.name, ThemeResolver.canPick(mode, light))
            assertTrue(mode.name, ThemeResolver.canPick(mode, dark))
        }
    }

    // ---------------- 跟随系统下的退出确认 ----------------

    @Test
    fun `浅色系统下选深色主题要确认`() {
        assertTrue(ThemeResolver.needsFollowExitPrompt(AppearanceMode.SYSTEM, false, dark))
    }

    @Test
    fun `深色系统下选浅色主题也要确认`() {
        assertTrue(ThemeResolver.needsFollowExitPrompt(AppearanceMode.SYSTEM, true, light))
    }

    @Test
    fun `同侧主题不打扰`() {
        assertFalse(ThemeResolver.needsFollowExitPrompt(AppearanceMode.SYSTEM, false, light))
        assertFalse(ThemeResolver.needsFollowExitPrompt(AppearanceMode.SYSTEM, true, dark))
    }

    @Test
    fun `自由模式选什么都不弹窗`() {
        assertFalse(ThemeResolver.needsFollowExitPrompt(AppearanceMode.FREE, false, dark))
        assertFalse(ThemeResolver.needsFollowExitPrompt(AppearanceMode.FREE, true, light))
    }

    @Test
    fun `确认退出后落到与主题一致的固定模式`() {
        assertEquals(AppearanceMode.DARK, ThemeResolver.modeAfterFollowExit(dark))
        assertEquals(AppearanceMode.LIGHT, ThemeResolver.modeAfterFollowExit(light))
    }

    @Test
    fun `四种模式都在偏好里可还原`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromPreference(mode.name))
        }
        // 旧版本可能存过别的值，一律回到跟随系统
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreference("LEGACY"))
    }
}
