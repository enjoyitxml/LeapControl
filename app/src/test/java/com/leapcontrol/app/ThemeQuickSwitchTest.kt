package com.leapcontrol.app

import com.leapcontrol.app.ui.SeasonThemes
import com.leapcontrol.app.ui.ThemeResolver
import com.leapcontrol.app.ui.breathCurve
import com.leapcontrol.app.ui.breathScaleAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 首页「快捷换壁纸」（车模 / 相伴卡右上角的呼吸小白点）的随机规则。
 *
 * 用户定的规则只有一句：**先看当前壁纸在哪个池子里，就只在那个池子里换**。
 * 这条规则一旦漏了，浅色界面就可能随机到夜间主题 —— 黑底黑字，属于一眼可见的严重问题，
 * 所以每条边界都钉在单测里。
 */
class ThemeQuickSwitchTest {

    private val lightTheme = SeasonThemes.MIST_HILLS
    private val nightTheme = SeasonThemes.STARRY
    private val lightVideo = SeasonThemes.VIDEO_SPRING
    private val darkVideo = SeasonThemes.VIDEO_STARS

    // ---------------- 池子判定 ----------------

    @Test
    fun `日间主题落在日间池`() {
        assertSame(SeasonThemes.DAY, ThemeResolver.poolOf(lightTheme))
    }

    @Test
    fun `夜间主题落在夜间池`() {
        assertSame(SeasonThemes.NIGHT_ALL, ThemeResolver.poolOf(nightTheme))
    }

    @Test
    fun `浅色视频落在视频浅色池`() {
        assertSame(SeasonThemes.VIDEO_LIGHT, ThemeResolver.poolOf(lightVideo))
    }

    @Test
    fun `深色视频落在视频深色池且只有一套`() {
        assertEquals(listOf(darkVideo), ThemeResolver.poolOf(darkVideo))
    }

    // ---------------- 只换当前那一侧 ----------------

    @Test
    fun `浅色界面只随机浅色主题`() {
        repeat(30) { seed ->
            val pick = ThemeResolver.randomSwitch(
                mode = AppearanceMode.SYSTEM,
                systemDark = false,
                lightThemeId = lightTheme.id,
                darkThemeId = nightTheme.id,
                random = Random(seed),
            )
            assertTrue("浅色槽必须是日间主题", SeasonThemes.DAY.any { it.id == pick.lightThemeId })
            // 深色槽一个字节都不动：白天换壁纸不该顺手改掉夜里那套
            assertEquals(nightTheme.id, pick.darkThemeId)
        }
    }

    @Test
    fun `深色界面只随机夜间主题`() {
        repeat(30) { seed ->
            val pick = ThemeResolver.randomSwitch(
                mode = AppearanceMode.SYSTEM,
                systemDark = true,
                lightThemeId = lightTheme.id,
                darkThemeId = nightTheme.id,
                random = Random(seed),
            )
            assertTrue("深色槽必须是夜间主题", SeasonThemes.NIGHT_ALL.any { it.id == pick.darkThemeId })
            assertEquals(lightTheme.id, pick.lightThemeId)
        }
    }

    @Test
    fun `视频主题只在视频池里换`() {
        repeat(30) { seed ->
            val pick = ThemeResolver.randomSwitch(
                mode = AppearanceMode.SYSTEM,
                systemDark = false,
                lightThemeId = lightVideo.id,
                darkThemeId = darkVideo.id,
                random = Random(seed),
            )
            assertTrue(
                "视频主题不能换成静态主题",
                SeasonThemes.VIDEO_LIGHT.any { it.id == pick.lightThemeId },
            )
            assertEquals(darkVideo.id, pick.darkThemeId)
        }
    }

    @Test
    fun `深色视频池只有星空 点了原地不动`() {
        val pick = ThemeResolver.randomSwitch(
            mode = AppearanceMode.SYSTEM,
            systemDark = true,
            lightThemeId = lightVideo.id,
            darkThemeId = darkVideo.id,
            random = Random(7),
        )
        assertEquals(darkVideo.id, pick.darkThemeId)
        assertEquals(lightVideo.id, pick.lightThemeId)
    }

    @Test
    fun `锁定模式下也只在同侧池子里换`() {
        repeat(30) { seed ->
            val lightPick = ThemeResolver.randomSwitch(
                mode = AppearanceMode.LIGHT,
                systemDark = true, // 手机是深色，但 App 锁定浅色：仍然只换浅色
                lightThemeId = lightTheme.id,
                darkThemeId = nightTheme.id,
                random = Random(seed),
            )
            assertTrue(SeasonThemes.DAY.any { it.id == lightPick.lightThemeId })

            val darkPick = ThemeResolver.randomSwitch(
                mode = AppearanceMode.DARK,
                systemDark = false,
                lightThemeId = lightTheme.id,
                darkThemeId = nightTheme.id,
                random = Random(seed),
            )
            assertTrue(SeasonThemes.NIGHT_ALL.any { it.id == darkPick.darkThemeId })
        }
    }

    // ---------------- 其它约定 ----------------

    @Test
    fun `换壁纸不改外观模式`() {
        // 哪怕当前浅色槽里是视频主题（pick 会强制跟随系统），快捷换壁纸也不插手模式：
        // 用户点的是「换张壁纸」，模式应该留在原地
        assertEquals(
            AppearanceMode.LIGHT,
            ThemeResolver.randomSwitch(
                AppearanceMode.LIGHT, false, lightVideo.id, darkVideo.id, Random(1),
            ).mode,
        )
        assertEquals(
            AppearanceMode.DARK,
            ThemeResolver.randomSwitch(
                AppearanceMode.DARK, true, lightVideo.id, darkVideo.id, Random(1),
            ).mode,
        )
    }

    @Test
    fun `每次都会换成另一套`() {
        var current = lightTheme.id
        repeat(40) { seed ->
            val pick = ThemeResolver.randomSwitch(
                AppearanceMode.SYSTEM, false, current, nightTheme.id, Random(seed),
            )
            assertNotEquals("随机到的不能是当前这套，否则看起来像没点上", current, pick.lightThemeId)
            current = pick.lightThemeId
        }
    }

    // ---------------- 呼吸曲线 ----------------

    @Test
    fun `呼吸曲线在一个周期内收放一次`() {
        assertEquals(0.62f, breathScaleAt(0f), 0.001f)     // 起点最小
        assertEquals(1.00f, breathScaleAt(0.5f), 0.001f)   // 半周期最大
        assertEquals(0.62f, breathScaleAt(1f), 0.001f)     // 回到起点
        assertEquals(0.81f, breathScaleAt(0.25f), 0.01f)   // 中点：不跳变
        assertEquals(0f, breathCurve(0f), 0.001f)
        assertEquals(1f, breathCurve(0.5f), 0.001f)
    }
}
