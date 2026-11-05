package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 首页快捷控车按钮的高亮与点击方向（用户 2026-11-05 定稿）：
 *
 * - 解锁 / 后备箱 / 车窗 / 电池预热 / 遮阳帘 / 哨兵：功能「开着」就高亮；
 * - 上锁 / 鸣笛：单次动作，永远不高亮；
 * - 高亮时点击 = 关闭，熄灭时点击 = 开启。
 */
class ControlActionsTest {

    @Test
    fun `解锁在车辆已解锁时高亮`() {
        assertEquals(true, ControlActions.isActive("unlock", status(locked = false)))
        assertEquals(false, ControlActions.isActive("unlock", status(locked = true)))
        // 读不到闭锁信号：不高亮，也不猜成「已解锁」
        assertNull(ControlActions.isActive("unlock", status(locked = null)))
    }

    @Test
    fun `上锁和鸣笛永远不高亮`() {
        for (locked in listOf(true, false, null)) {
            assertNull(ControlActions.isActive("lock", status(locked = locked)))
            assertNull(ControlActions.isActive("horn", status(locked = locked)))
        }
    }

    @Test
    fun `开关类按钮跟着车况亮灭`() {
        assertEquals(true, ControlActions.isActive("trunk", status(trunkOpen = true)))
        assertEquals(false, ControlActions.isActive("trunk", status(trunkOpen = false)))
        assertEquals(true, ControlActions.isActive("window", status(windowOpen = true)))
        assertEquals(true, ControlActions.isActive("sunshade", status(sunshadeOpen = true)))
        assertEquals(true, ControlActions.isActive("sentry", status(sentryOn = true)))
        assertEquals(false, ControlActions.isActive("sentry", status(sentryOn = false)))
    }

    @Test
    fun `电池预热跟着加热信号亮灭`() {
        assertEquals(true, ControlActions.isActive("batteryPreheat", status(preheat = true)))
        assertEquals(false, ControlActions.isActive("batteryPreheat", status(preheat = false)))
        assertNull(ControlActions.isActive("batteryPreheat", status(preheat = null)))
    }

    @Test
    fun `预热高亮时点击下发关闭指令`() {
        assertEquals("batteryPreheatOff", ControlActions.resolveCommand("batteryPreheat", status(preheat = true)))
        assertEquals("batteryPreheat", ControlActions.resolveCommand("batteryPreheat", status(preheat = false)))
        // 读不到状态时保守地当「开」，与其余复合按钮一致
        assertEquals("batteryPreheat", ControlActions.resolveCommand("batteryPreheat", status(preheat = null)))
    }

    private fun status(
        locked: Boolean? = null,
        trunkOpen: Boolean? = null,
        windowOpen: Boolean? = null,
        sunshadeOpen: Boolean? = null,
        sentryOn: Boolean? = null,
        preheat: Boolean? = null,
    ) = CarStatus(
        soc = 80,
        range = null,
        electricRange = null,
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = null,
        interiorTemp = null,
        locked = locked,
        acOn = null,
        trunkOpen = trunkOpen,
        windowOpen = windowOpen,
        sunshadeOpen = sunshadeOpen,
        sentryOn = sentryOn,
        batteryPreheatOn = preheat,
    )
}
