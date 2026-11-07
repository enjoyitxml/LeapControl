package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方向盘加热（cmdid=320）：两态开关的语义、指令体、状态读取。
 *
 * 这一组测试守的是 2026-11-07 那次「按了没反应」：
 * 车端 `{"level":"1"}` 是**关**，而我们以前把它当「弱档」当第一个点击发出去，
 * 所以第一次点击等于什么都没做。
 */
class SteeringWheelHeatTest {

    private fun state(cmd: ControlCommand): JSONObject = JSONObject(cmd.stateJson)

    // ---------------- 指令体 ----------------

    @Test
    fun `开是 level 2，不是 1`() {
        val on = SteeringWheelHeat.build(on = true)
        assertEquals("320", on.cmdid)
        assertEquals("2", state(on).getString("level"))
        assertEquals("开启方向盘加热", on.label)
    }

    @Test
    fun `关先发 0，补发用 1`() {
        assertEquals("0", state(SteeringWheelHeat.build(on = false)).getString("level"))
        assertEquals("1", state(SteeringWheelHeat.buildOffAlt()).getString("level"))
    }

    // ---------------- 命令名 ----------------

    @Test
    fun `开关命令名可解析`() {
        assertEquals(true, SteeringWheelHeat.targetOf(SteeringWheelHeat.ON))
        assertEquals(false, SteeringWheelHeat.targetOf(SteeringWheelHeat.OFF))
    }

    /**
     * 老命令名必须还能用，而且 `_1` 只能当「开」。
     *
     * 这条断言就是这次 bug 的回归守门：任何把 `_1` 解释成弱档 / 关闭的实现都会红。
     */
    @Test
    fun `老命令名 _1 与 _2 都按开处理`() {
        assertEquals(false, SteeringWheelHeat.targetOf("steeringWheelHeating_0"))
        assertEquals(true, SteeringWheelHeat.targetOf("steeringWheelHeating_1"))
        assertEquals(true, SteeringWheelHeat.targetOf("steeringWheelHeating_2"))
        assertNull(SteeringWheelHeat.targetOf("steeringWheelHeating_fast"))
        assertNull(SteeringWheelHeat.targetOf("driverSeatHeating_1"))
        assertNull(SteeringWheelHeat.targetOf(null))
    }

    @Test
    fun `Commands 走同一套语义`() {
        assertEquals("2", state(Commands.build(SteeringWheelHeat.ON)).getString("level"))
        assertEquals("2", state(Commands.build("steeringWheelHeating_1")).getString("level"))
        assertEquals("0", state(Commands.build(SteeringWheelHeat.OFF)).getString("level"))
        assertEquals("0", state(Commands.build("steeringWheelHeating_0")).getString("level"))
        // 老的便捷入口也收敛到两态
        assertEquals("2", state(Commands.buildSteeringWheelHeating(2)).getString("level"))
        assertEquals("0", state(Commands.buildSteeringWheelHeating(0)).getString("level"))
    }

    // ---------------- 车况读取 ----------------

    @Test
    fun `1816 为主，1624 兜底`() {
        assertEquals(0, SteeringWheelHeat.levelOf(level = 0, minutes = 0))
        assertEquals(1, SteeringWheelHeat.levelOf(level = 1, minutes = 0))
        assertEquals(2, SteeringWheelHeat.levelOf(level = 2, minutes = 0))
        // 只报剩余分钟数的车型：>0 就是开着
        assertEquals(2, SteeringWheelHeat.levelOf(level = null, minutes = 15))
        assertNull(SteeringWheelHeat.levelOf(level = null, minutes = 0))
        assertNull(SteeringWheelHeat.levelOf(level = null, minutes = null))
        // 1816 说关、1624 说还在加热：以「真的在热」为准
        assertEquals(2, SteeringWheelHeat.levelOf(level = 0, minutes = 8))
    }

    @Test
    fun `开关判定只看是否大于零`() {
        assertFalse(SteeringWheelHeat.isOn(0))
        assertTrue(SteeringWheelHeat.isOn(1))
        assertTrue(SteeringWheelHeat.isOn(2))
        assertFalse(SteeringWheelHeat.isOn(null))
    }
}
