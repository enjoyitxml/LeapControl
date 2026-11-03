package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 空调实时风量 / 循环方式的取值规则。
 *
 * 背景：2026-11-03 用户实测车上 7 挡风、App 显示 2 挡 —— 因为之前只读 acSetting
 * 里的设定值。这里锁住「实时信号优先、设定值兜底、都没有就不显示」这三条。
 */
class AcLiveStateTest {

    @Test
    fun `实时风量优先于设定值`() {
        assertEquals("7", AcLiveState.windLevel(7, "2"))
        assertEquals("7", AcLiveState.windLevel("7", 2))
        assertEquals("4", AcLiveState.windLevel(4.0, null))
    }

    @Test
    fun `实时风量缺失时退回设定值`() {
        assertEquals("5", AcLiveState.windLevel(null, "5"))
        assertEquals("5", AcLiveState.windLevel(JSONObject.NULL, 5))
    }

    @Test
    fun `实时风量为零表示没在送风而不是零挡`() {
        // 空调关着时 1941 会报 0；显示成「0 挡」不对，退回设定值
        assertEquals("3", AcLiveState.windLevel(0, "3"))
        // 设定值也解析不出 → 只能说不知道
        assertNull(AcLiveState.windLevel(0, null))
    }

    @Test
    fun `超出 1 到 7 的实时值不可信`() {
        assertEquals("2", AcLiveState.windLevel(9, 2))
        assertEquals("2", AcLiveState.windLevel(-1, 2))
        // 设定值本身也越界 → 不采用
        assertNull(AcLiveState.windLevel(9, 0))
    }

    @Test
    fun `两边都没有就返回 null 不猜默认值`() {
        assertNull(AcLiveState.windLevel(null, null))
        assertNull(AcLiveState.windLevel("", ""))
    }

    @Test
    fun `循环方式读实时信号`() {
        assertEquals("内循环", AcLiveState.circle(1, "外循环"))
        assertEquals("外循环", AcLiveState.circle(0, "内循环"))
        assertEquals("外循环", AcLiveState.circle("0", null))
    }

    @Test
    fun `循环信号缺失或异常时退回设定值`() {
        assertEquals("内循环", AcLiveState.circle(null, "内循环"))
        assertEquals("外循环", AcLiveState.circle(5, "外循环"))
        assertNull(AcLiveState.circle(null, null))
    }
}
