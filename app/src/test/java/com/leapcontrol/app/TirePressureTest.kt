package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 胎压归一化与「偏低」判定。
 *
 * 这组用例锁两件事：
 *  1. 单位换算 —— 车端同名字段出现过 bar(2.4) / 0.1bar(24) / kPa(240) 三种写法，
 *     一旦有人把它改回「直接当 bar 用」，24 会变成 24 bar（永远显示正常）、240 会变成 240 bar。
 *  2. 报警线 —— 2.0 bar，低于它标红；车端自己的告警位置位时无论数值多少都标红。
 */
class TirePressureTest {

    @Test
    fun `bar 值原样保留`() {
        assertEquals(2.4, TirePressure.toBar(2.4)!!, 0.001)
        assertEquals(2.0, TirePressure.toBar(2.0)!!, 0.001)
    }

    @Test
    fun `0点1bar 单位除以10`() {
        assertEquals(2.4, TirePressure.toBar(24)!!, 0.001)
        assertEquals(1.8, TirePressure.toBar(18)!!, 0.001)
    }

    @Test
    fun `kPa 单位除以100`() {
        assertEquals(2.4, TirePressure.toBar(240)!!, 0.001)
        assertEquals(2.35, TirePressure.toBar(235)!!, 0.001)
    }

    @Test
    fun `字符串与逗号小数也能解析`() {
        assertEquals(2.4, TirePressure.toBar("2.4")!!, 0.001)
        assertEquals(2.4, TirePressure.toBar("2,4")!!, 0.001)
        assertEquals(2.4, TirePressure.toBar(" 240 ")!!, 0.001)
    }

    @Test
    fun `无效值返回 null`() {
        assertNull(TirePressure.toBar(null))
        assertNull(TirePressure.toBar("--"))
        assertNull(TirePressure.toBar(""))
        // 0 通常是「信号缺失」的占位，不能当成 0 bar 的严重欠压
        assertNull(TirePressure.toBar(0))
        assertNull(TirePressure.toBar(0.0))
        // 量级离谱的脏数据（0.24 bar 这种连轮胎都算不上）
        assertNull(TirePressure.toBar(0.24))
    }

    @Test
    fun `格式化后只留一位小数且不带单位`() {
        assertEquals("2.4", TirePressure.formatBar(240))
        assertEquals("2.4", TirePressure.formatBar(2.4))
        assertEquals("2.4", TirePressure.formatBar(24))
        assertNull(TirePressure.formatBar(null))
    }

    @Test
    fun `低于 2bar 判为不足`() {
        assertEquals(TirePressure.State.Low, TirePressure.stateOf("1.9", vehicleWarning = false))
        assertEquals(TirePressure.State.Low, TirePressure.stateOf("1.6", vehicleWarning = false))
    }

    @Test
    fun `2bar 及以上判为正常`() {
        assertEquals(TirePressure.State.Ok, TirePressure.stateOf("2.0", vehicleWarning = false))
        assertEquals(TirePressure.State.Ok, TirePressure.stateOf("2.4", vehicleWarning = false))
    }

    @Test
    fun `车端告警位置位时一律按不足显示`() {
        // 数值看着正常但车端已报警（例如过压、传感器判定欠压）
        assertEquals(TirePressure.State.Low, TirePressure.stateOf("2.6", vehicleWarning = true))
        assertEquals(TirePressure.State.Low, TirePressure.stateOf(null, vehicleWarning = true))
    }

    @Test
    fun `没有数据时是未知`() {
        assertEquals(TirePressure.State.Unknown, TirePressure.stateOf(null, vehicleWarning = false))
        assertEquals(TirePressure.State.Unknown, TirePressure.stateOf("--", vehicleWarning = false))
    }
}
