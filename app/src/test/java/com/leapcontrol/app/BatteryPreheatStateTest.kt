package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 电池预热的车况口径（用户 2026-11-05：首页「电池预热」按钮在功能开启时要高亮）。
 *
 * 车端信号 1186 `batteryThermalRequest` 只有 4 / 0 两个值，
 * 其余一律当「不知道」—— 宁可不高亮，也不能把「读不到」当成「预热关着」。
 */
class BatteryPreheatStateTest {

    @Test
    fun `4 表示正在加热`() {
        assertEquals(true, BatteryPreheatState.fromRaw("4"))
        assertEquals(true, BatteryPreheatState.fromRaw(4))
        assertEquals(true, BatteryPreheatState.fromRaw(" 4 "))
    }

    @Test
    fun `0 表示空闲`() {
        assertEquals(false, BatteryPreheatState.fromRaw("0"))
        assertEquals(false, BatteryPreheatState.fromRaw(0))
    }

    @Test
    fun `缺失或未知值不给结论`() {
        assertNull(BatteryPreheatState.fromRaw(null))
        assertNull(BatteryPreheatState.fromRaw(""))
        assertNull(BatteryPreheatState.fromRaw("1"))
        assertNull(BatteryPreheatState.fromRaw("ptcon"))
    }
}
