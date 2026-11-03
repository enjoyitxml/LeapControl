package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开关类车况信号的三态解析（[VehicleSignals.bool]）。
 *
 * 除霜位（1945）在不同车型上有 `1` / `"1"` / `true` 三种形态，而**缺信号不能当 false**：
 * 「没上报」和「确实是关着」是两件事，前者不该让界面报「除霜未开启」。
 */
class VehicleSignalsBoolTest {

    @Test
    fun `数字与布尔与字符串都认`() {
        assertTrue(VehicleSignals.bool(1) == true)
        assertTrue(VehicleSignals.bool(true) == true)
        assertTrue(VehicleSignals.bool("1") == true)
        assertTrue(VehicleSignals.bool(" on ") == true)
        assertFalse(VehicleSignals.bool(0) == true)
        assertFalse(VehicleSignals.bool(false) == true)
        assertFalse(VehicleSignals.bool("0") == true)
        assertFalse(VehicleSignals.bool("off") == true)
    }

    @Test
    fun `缺信号或无法解析一律 null`() {
        assertNull(VehicleSignals.bool(null))
        assertNull(VehicleSignals.bool(""))
        assertNull(VehicleSignals.bool("--"))
        assertNull(VehicleSignals.bool(listOf(1)))
    }

    @Test
    fun `浮点与非零数字按真处理`() {
        assertEquals(true, VehicleSignals.bool(1.0))
        assertEquals(true, VehicleSignals.bool(7))
        assertEquals(false, VehicleSignals.bool(0.0))
    }
}
