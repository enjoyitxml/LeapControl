package com.leapcontrol.app

import com.leapcontrol.app.ui.SeasonThemes
import com.leapcontrol.app.ui.ThemeResolver
import com.leapcontrol.app.ui.breathCurve
import com.leapcontrol.app.ui.breathScaleAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 首页车模右上角「快捷换壁纸」小白点的随机规则（相伴卡那个点换的是卡片背景，见 [OwnershipBackdropRandomTest]）。
 *
 * 用户定的规则只有一句：**先看当前壁纸在哪个池子里，就只在那个池子里换**。
 * 这条规则一旦漏了，浅色界面就可能随机到夜间主题 —— 黑底黑字，属于一眼可见的严重问题，
 * 所以每条边界都钉在单测里。
 */
class ThemeQuickSwitchTest {

    private val lightTheme = SeasonThemes.MIST_HILLS
    private val nightTheme = SeasonThemes.STARRY

    // ---------------- 池子判定 ----------------

    @Test
    fun `日间主题落在日间池`() {
        assertEquals(SeasonThemes.DAY.filter { !it.custom }, ThemeResolver.poolOf(lightTheme))
    }

    @Test
    fun `夜间主题落在夜间池`() {
        assertEquals(SeasonThemes.NIGHT_ALL.filter { !it.custom }, ThemeResolver.poolOf(nightTheme))
    }

    @Test
    fun `两个池子各二十套内置主题`() {
        assertEquals(20, ThemeResolver.poolOf(lightTheme).size)
        assertEquals(20, ThemeResolver.poolOf(nightTheme).size)
    }

    @Test
    fun `随机换壁纸永远不选自定义壁纸`() {
        // 用户上传的图一旦被随机换掉就找不回来了（只能重新进个性化点它），
        // 所以自定义主题必须被排除在随机池之外 —— 当前就是自定义主题时也一样
        listOf(SeasonThemes.CUSTOM_DAY, SeasonThemes.MIST_HILLS).forEach { current ->
            repeat(20) { seed ->
                val pick = ThemeResolver.randomSwitch(
                    mode = AppearanceMode.SYSTEM,
                    systemDark = false,
                    lightThemeId = current.id,
                    darkThemeId = nightTheme.id,
                    random = Random(seed),
                )
                assertFalse(
                    "随机换壁纸不能落到自定义壁纸上",
                    SeasonThemes.byId(pick.lightThemeId).custom,
                )
            }
        }
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
            assertFalse("浅色槽不能出现夜间主题", SeasonThemes.NIGHT_ALL.any { it.id == pick.lightThemeId })
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
        // 用户点的是「换张壁纸」，模式应该留在原地，不能被顺手改成「跟随系统」
        assertEquals(
            AppearanceMode.LIGHT,
            ThemeResolver.randomSwitch(
                AppearanceMode.LIGHT, false, lightTheme.id, nightTheme.id, Random(1),
            ).mode,
        )
        assertEquals(
            AppearanceMode.DARK,
            ThemeResolver.randomSwitch(
                AppearanceMode.DARK, true, lightTheme.id, nightTheme.id, Random(1),
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

    // ---------------- 自定义图生效时收起入口 ----------------
    //
    // 自定义壁纸 / 自定义背景是用户从相册里翻出来、又亲手拖过缩放框的图，
    // 换掉之后**找不回来**（只能重新去挑一次）。所以只要这一块正在显示自己的图，
    // 首页那两颗小白点（或整块可点）一律不给 —— 三种模式都得压住，漏一种就等于有个后门。

    @Test
    fun `自定义壁纸生效时车模侧三种模式都不给入口`() {
        listOf(
            QuickSwitchPrefs(mode = QuickSwitchMode.DOT, carDot = true),
            QuickSwitchPrefs(mode = QuickSwitchMode.TAP),
            QuickSwitchPrefs(mode = QuickSwitchMode.OFF),
        ).forEach { prefs ->
            assertEquals(
                "锁定时 ${prefs.mode} 也必须是不提供",
                QuickTarget.NONE,
                prefs.effectiveTarget(QuickPart.CAR, customLocked = true),
            )
        }
    }

    @Test
    fun `自定义背景生效时相伴卡侧三种模式都不给入口`() {
        listOf(
            QuickSwitchPrefs(mode = QuickSwitchMode.DOT, cardDot = true),
            QuickSwitchPrefs(mode = QuickSwitchMode.TAP),
            QuickSwitchPrefs(mode = QuickSwitchMode.OFF),
        ).forEach { prefs ->
            assertEquals(
                QuickTarget.NONE,
                prefs.effectiveTarget(QuickPart.CARD, customLocked = true),
            )
        }
    }

    @Test
    fun `没有自定义图时完全按用户设置走`() {
        // 小白点模式：开着就是小白点，单块关掉就是没有
        assertEquals(
            QuickTarget.DOT,
            QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = true)
                .effectiveTarget(QuickPart.CAR, customLocked = false),
        )
        assertEquals(
            QuickTarget.NONE,
            QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = false)
                .effectiveTarget(QuickPart.CAR, customLocked = false),
        )
        // 点击切换模式：整块可点
        assertEquals(
            QuickTarget.TAP,
            QuickSwitchPrefs(QuickSwitchMode.TAP)
                .effectiveTarget(QuickPart.CARD, customLocked = false),
        )
        // 关闭模式：本来就没有
        assertEquals(
            QuickTarget.NONE,
            QuickSwitchPrefs(QuickSwitchMode.OFF)
                .effectiveTarget(QuickPart.CARD, customLocked = false),
        )
    }

    @Test
    fun `只锁一块不影响另一块`() {
        val prefs = QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = true, cardDot = true)
        assertEquals(QuickTarget.NONE, prefs.effectiveTarget(QuickPart.CAR, customLocked = true))
        assertEquals(QuickTarget.DOT, prefs.effectiveTarget(QuickPart.CARD, customLocked = false))
    }

    @Test
    fun `锁定只影响呈现 不动用户存的偏好`() {
        // 用户没关过这个功能：切回内置壁纸后入口应该自己回来，
        // 不需要再去设置里重新打开一次
        val prefs = QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = true)
        prefs.effectiveTarget(QuickPart.CAR, customLocked = true)
        assertEquals(QuickTarget.DOT, prefs.carTarget)
        assertEquals(QuickTarget.DOT, prefs.effectiveTarget(QuickPart.CAR, customLocked = false))
    }

    @Test
    fun `设置页提示写明是哪几块被收起`() {
        val onlyCar = QuickSwitchLock.settings(listOf(QuickSwitchLock.PART_CAR))
        assertTrue(onlyCar.contains("车模"))
        assertFalse(onlyCar.contains("相伴卡"))

        val both = QuickSwitchLock.settings(
            listOf(QuickSwitchLock.PART_CAR, QuickSwitchLock.PART_CARD),
        )
        assertTrue(both.contains("车模"))
        assertTrue(both.contains("相伴卡"))
        // 得告诉用户换图的正确去处，否则「入口没了」等于「功能没了」
        assertTrue(both.contains("个性化"))
    }

    @Test
    fun `挑图建议提到上半屏和下半屏`() {
        // 用户明确要求提示「主体放上半屏、下半屏留空」——文案改字别把这层意思改没了
        assertTrue(QuickSwitchLock.PICK_ADVICE.contains("上半屏"))
        assertTrue(QuickSwitchLock.PICK_ADVICE.contains("留空"))
    }
}
