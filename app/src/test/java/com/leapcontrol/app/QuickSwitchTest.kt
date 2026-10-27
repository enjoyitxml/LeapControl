package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页「快速切换壁纸 / 背景」三态规则。
 *
 * 这里钉的是**模式 → 单个部件怎么呈现**的映射。这一层一旦算错，表现是
 * 「小白点模式下车模能点、相伴卡点不动」或者「关掉了还冒出一颗点」，
 * 都是用户一眼能看出来的问题，所以每个组合都过一遍。
 */
class QuickSwitchTest {

    // ---------------- 模式本身 ----------------

    @Test
    fun `未知或空值退回小白点模式`() {
        assertEquals(QuickSwitchMode.DOT, QuickSwitchMode.fromPreference(null))
        assertEquals(QuickSwitchMode.DOT, QuickSwitchMode.fromPreference(""))
        assertEquals(QuickSwitchMode.DOT, QuickSwitchMode.fromPreference("TAP_MODE"))
    }

    @Test
    fun `三种模式的枚举名可往返`() {
        QuickSwitchMode.entries.forEach {
            assertEquals(it, QuickSwitchMode.fromPreference(it.name))
        }
    }

    @Test
    fun `旧布尔开关关过的人升级后落到关闭模式`() {
        // 上一版「关掉」= 两颗点都隐藏且没有其它入口，升级后不能突然又冒出点来
        assertEquals(QuickSwitchMode.OFF, QuickSwitchMode.fromLegacy(null, legacyDotEnabled = false))
    }

    @Test
    fun `旧布尔开关开着或从未写过时落回小白点`() {
        assertEquals(QuickSwitchMode.DOT, QuickSwitchMode.fromLegacy(null, legacyDotEnabled = true))
        assertEquals(QuickSwitchMode.DOT, QuickSwitchMode.fromLegacy(null, legacyDotEnabled = null))
    }

    @Test
    fun `新键存在时以新键为准`() {
        assertEquals(QuickSwitchMode.TAP, QuickSwitchMode.fromLegacy("TAP", legacyDotEnabled = false))
        assertEquals(QuickSwitchMode.DOT, QuickSwitchMode.fromLegacy("DOT", legacyDotEnabled = false))
        assertEquals(QuickSwitchMode.OFF, QuickSwitchMode.fromLegacy("OFF", legacyDotEnabled = true))
    }

    // ---------------- 模式 → 单个部件的呈现 ----------------

    @Test
    fun `小白点模式下两块默认都显示白点`() {
        val q = QuickSwitchPrefs(mode = QuickSwitchMode.DOT)
        assertEquals(QuickTarget.DOT, q.carTarget)
        assertEquals(QuickTarget.DOT, q.cardTarget)
    }

    @Test
    fun `小白点模式下可以只关掉其中一个`() {
        val carOnly = QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = true, cardDot = false)
        assertEquals(QuickTarget.DOT, carOnly.carTarget)
        assertEquals(QuickTarget.NONE, carOnly.cardTarget)

        val cardOnly = QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = false, cardDot = true)
        assertEquals(QuickTarget.NONE, cardOnly.carTarget)
        assertEquals(QuickTarget.DOT, cardOnly.cardTarget)
    }

    @Test
    fun `两块都关掉时等于没有白点`() {
        val q = QuickSwitchPrefs(QuickSwitchMode.DOT, carDot = false, cardDot = false)
        assertEquals(QuickTarget.NONE, q.carTarget)
        assertEquals(QuickTarget.NONE, q.cardTarget)
    }

    @Test
    fun `点击切换模式下两块都是整块可点，与小白点开关无关`() {
        // 单个部件开关只在小白点模式下有意义；切到点击切换时它不该再拦着谁
        listOf(true, false).forEach { carDot ->
            listOf(true, false).forEach { cardDot ->
                val q = QuickSwitchPrefs(QuickSwitchMode.TAP, carDot, cardDot)
                assertEquals(QuickTarget.TAP, q.carTarget)
                assertEquals(QuickTarget.TAP, q.cardTarget)
            }
        }
    }

    @Test
    fun `关闭模式下两块都不提供快捷入口`() {
        val q = QuickSwitchPrefs(QuickSwitchMode.OFF)
        assertEquals(QuickTarget.NONE, q.carTarget)
        assertEquals(QuickTarget.NONE, q.cardTarget)
    }

    @Test
    fun `默认值就是小白点且两块都开`() {
        val q = QuickSwitchPrefs()
        assertEquals(QuickSwitchMode.DOT, q.mode)
        assertTrue(q.carDot)
        assertTrue(q.cardDot)
    }
}
