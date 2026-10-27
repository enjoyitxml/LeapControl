package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「近 7 天行程」卡的换算单测：合计文案 + 柱状图归一化。 */
class WeeklyTripStatsTest {

    private fun day(d: String, km: Double) = DailyMileage(d, km)

    private val week = listOf(
        day("2026-10-21", 12.0),
        day("2026-10-22", 0.0),
        day("2026-10-23", 30.0),
        day("2026-10-24", 6.0),
        day("2026-10-25", 48.0),
        day("2026-10-26", 24.0),
        day("2026-10-27", 6.0),
    )

    @Test
    fun `合计七日里程`() {
        assertEquals(126.0, WeeklyTripStats.totalKm(week)!!, 1e-6)
    }

    @Test
    fun `整数合计不显示小数位`() {
        assertEquals("126", WeeklyTripStats.totalText(week))
    }

    @Test
    fun `一位小数保留`() {
        val days = listOf(day("2026-10-26", 10.25), day("2026-10-27", 20.0))
        // 30.25 → 30.3（四舍五入到一位）
        assertEquals("30.3", WeeklyTripStats.totalText(days))
    }

    @Test
    fun `四位数带千分位`() {
        val days = listOf(day("a", 1200.0), day("b", 345.0))
        assertEquals("1,545", WeeklyTripStats.totalText(days))
    }

    @Test
    fun `没有明细返回 null 而不是 0`() {
        assertNull(WeeklyTripStats.totalKm(emptyList()))
        assertNull(WeeklyTripStats.totalText(emptyList()))
    }

    @Test
    fun `脏数据被过滤`() {
        val days = listOf(
            day("a", -5.0),
            day("b", Double.NaN),
            day("c", 9000.0), // 超过单日上限
            day("d", 10.0),
        )
        assertEquals(10.0, WeeklyTripStats.totalKm(days)!!, 1e-6)
    }

    @Test
    fun `柱状图恒为七根且峰值顶满`() {
        val series = WeeklyTripStats.series(week)
        assertEquals(WeeklyTripStats.BARS, series.size)
        assertEquals(1f, series.max(), 1e-6f)
        // 峰值出现在 10-25（48km），即倒数第三根
        assertEquals(1f, series[4], 1e-6f)
        // 0km 的那天是 0
        assertEquals(0f, series[1], 1e-6f)
    }

    @Test
    fun `不足七天时左侧补零且最后一根是最近一天`() {
        val two = listOf(day("2026-10-26", 10.0), day("2026-10-27", 20.0))
        val series = WeeklyTripStats.series(two)
        assertEquals(7, series.size)
        assertEquals(0f, series[0], 1e-6f)
        assertEquals(0.5f, series[5], 1e-6f)
        assertEquals(1f, series[6], 1e-6f)
    }

    @Test
    fun `全部为零时返回全零而不是除零`() {
        val zeros = listOf(day("a", 0.0), day("b", 0.0))
        val series = WeeklyTripStats.series(zeros)
        assertEquals(7, series.size)
        assertTrue(series.all { it == 0f })
    }

    @Test
    fun `values 恒为七根且脏数据归零`() {
        val mixed = listOf(
            day("a", -3.0),
            day("b", Double.NaN),
            day("c", 9000.0),
            day("d", 12.0),
        )
        val v = WeeklyTripStats.values(mixed)
        assertEquals(7, v.size)
        assertEquals(0.0, v[3], 1e-6) // -3
        assertEquals(0.0, v[4], 1e-6) // NaN
        assertEquals(0.0, v[5], 1e-6) // 9000
        assertEquals(12.0, v[6], 1e-6)
    }

    @Test
    fun `柱顶标注整数不带小数`() {
        assertEquals("48", WeeklyTripStats.dayText(48.0))
        assertEquals("0", WeeklyTripStats.dayText(0.0))
        assertEquals("12", WeeklyTripStats.dayText(12.4))
    }

    @Test
    fun `柱顶标注小里程保留一位小数`() {
        assertEquals("6.5", WeeklyTripStats.dayText(6.5))
        assertEquals("0.4", WeeklyTripStats.dayText(0.4))
    }

    @Test
    fun `超过七条时只取最近七天`() {
        val many = (1..10).map { day("d$it", it.toDouble()) }
        val series = WeeklyTripStats.series(many)
        assertEquals(7, series.size)
        // 最近七天是 4..10，峰值 10
        assertEquals(1f, series[6], 1e-6f)
        assertEquals(0.4f, series[0], 1e-6f)
        assertEquals(49.0, WeeklyTripStats.totalKm(many.takeLast(7))!!, 1e-6)
    }
}
