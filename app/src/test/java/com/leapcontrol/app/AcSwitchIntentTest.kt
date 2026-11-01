package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空调开关「本地意图」的回归测试。
 *
 * 真机症状：首页空调开关点下去半天没反应，用户反复点，有时还越点越错。
 * 根因是显示状态完全跟车况走 + 指令在途时按钮被禁用；这里把「点下去先跟手」
 * 与「什么时候交还给车端」两条规则钉住。
 */
class AcSwitchIntentTest {

    // ---------------- 显示状态 ----------------

    @Test
    fun `没有意图时完全跟车况`() {
        assertTrue(AcSwitchIntent.shown(remote = true, intent = null))
        assertFalse(AcSwitchIntent.shown(remote = false, intent = null))
    }

    @Test
    fun `车端读不到状态时按关闭显示，与既有行为一致`() {
        assertFalse(AcSwitchIntent.shown(remote = null, intent = null))
    }

    @Test
    fun `点下开启立刻显示为开，不等车端上报`() {
        // 车端还报着「关」，但用户刚点了开 —— 这就是原来「点了没反应」的那一瞬间
        assertTrue(AcSwitchIntent.shown(remote = false, intent = true))
    }

    @Test
    fun `点下关闭立刻显示为关`() {
        assertFalse(AcSwitchIntent.shown(remote = true, intent = false))
    }

    // ---------------- 意图何时交还 ----------------

    @Test
    fun `车端回报同一状态后交还给车况`() {
        assertNull(AcSwitchIntent.keep(remote = true, intent = true, msSinceIntent = 3_000))
        assertNull(AcSwitchIntent.keep(remote = false, intent = false, msSinceIntent = 3_000))
    }

    @Test
    fun `车端还没上报时保留意图，别让开关弹回去`() {
        assertEquals(true, AcSwitchIntent.keep(remote = null, intent = true, msSinceIntent = 2_000))
    }

    @Test
    fun `车端仍报旧值时先保留意图，等它收敛`() {
        // 点了关，车端还在报「开」：2 秒内不要弹回，否则用户以为点了没用
        assertEquals(false, AcSwitchIntent.keep(remote = true, intent = false, msSinceIntent = 2_000))
    }

    @Test
    fun `超过放弃时限就回到车端真实状态`() {
        assertNull(
            AcSwitchIntent.keep(
                remote = true,
                intent = false,
                msSinceIntent = AcSwitchIntent.GIVE_UP_MS,
            ),
        )
        assertNull(
            AcSwitchIntent.keep(
                remote = null,
                intent = true,
                msSinceIntent = AcSwitchIntent.GIVE_UP_MS + 1,
            ),
        )
    }

    @Test
    fun `没有意图时 keep 恒为 null`() {
        assertNull(AcSwitchIntent.keep(remote = true, intent = null, msSinceIntent = 0))
    }

    // ---------------- 收敛循环发什么 ----------------

    @Test
    fun `没有意图就不发指令`() {
        assertNull(AcSwitchIntent.nextTarget(intent = null, lastSent = null))
    }

    @Test
    fun `第一次点击要发`() {
        assertEquals(true, AcSwitchIntent.nextTarget(intent = true, lastSent = null))
    }

    @Test
    fun `等待期间重复点同一个方向不再重发`() {
        // 用户着急连点三下关闭，只能打出一条指令
        assertNull(AcSwitchIntent.nextTarget(intent = false, lastSent = false))
    }

    @Test
    fun `等待期间点了反方向，要补发一条`() {
        assertEquals(true, AcSwitchIntent.nextTarget(intent = true, lastSent = false))
    }
}
