package com.leapcontrol.app

import com.leapcontrol.app.journal.KmRange
import com.leapcontrol.app.journal.MileageSeries
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 里程趋势聚合单测：日 / 周 / 月 / 年四种粒度的窗口长度、分桶边界与统计。
 *
 * 固定「今天」= 2026-10-30（周五），这样周 / 月的边界是确定的，
 * 不会因为跑测试的日期不同而时好时坏。
 */
class MileageSeriesTest {

    private val today = LocalDate.of(2026, 10, 30)

    private fun of(vararg pairs: Pair<String, Double>, range: KmRange) =
        MileageSeries.of(pairs.toMap(), range, today)

    @Test
    fun dayWindowHasFourteenBucketsAndGapsAreZero() {
        val points = of(
            "2026-10-30" to 30.0,
            "2026-10-24" to 12.0,
            range = KmRange.DAY,
        )

        assertEquals(MileageSeries.DAYS, points.size)
        // 最后一格是今天，第一格是 13 天前
        assertEquals("2026-10-30", points.last().key)
        assertEquals("今天", points.last().label)
        assertEquals(30.0, points.last().km, 0.001)
        assertEquals("2026-10-17", points.first().key)

        // 中间没有记录的日子补 0，而不是沿用前一天的值
        assertEquals(0.0, points.first { it.key == "2026-10-25" }.km, 0.001)
        assertEquals(12.0, points.first { it.key == "2026-10-24" }.km, 0.001)
        assertEquals("昨天", points.first { it.key == "2026-10-29" }.label)
    }

    @Test
    fun weekBucketsStartOnMonday() {
        val points = of(
            // 周日 10/25 属于「上周」（10/19 周一 ~ 10/25 周日）
            "2026-10-25" to 50.0,
            // 本周一 10/26 起算新的一周
            "2026-10-26" to 20.0,
            "2026-10-30" to 5.0,
            range = KmRange.WEEK,
        )

        assertEquals(MileageSeries.WEEKS, points.size)
        assertEquals("本周", points.last().label)
        assertEquals(25.0, points.last().km, 0.001)
        assertEquals("上周", points[points.lastIndex - 1].label)
        assertEquals(50.0, points[points.lastIndex - 1].km, 0.001)
    }

    @Test
    fun weekBucketKeyIsTheMondayOfThatWeek() {
        val points = of("2026-10-30" to 1.0, range = KmRange.WEEK)
        assertEquals("w2026-10-26", points.last().key)
    }

    @Test
    fun monthBucketsCoverTwelveMonthsAcrossYearBoundary() {
        val points = of(
            "2026-10-01" to 100.0,
            "2026-10-31" to 50.0,
            "2025-11-15" to 40.0,
            range = KmRange.MONTH,
        )

        assertEquals(MileageSeries.MONTHS, points.size)
        // 最后一格是 2026-10，往前 11 格是 2025-11
        assertEquals("2025-11", points.first().key)
        assertEquals("2026-10", points.last().key)
        assertEquals("10月", points.last().label)
        // 同月的两天要合并
        assertEquals(150.0, points.last().km, 0.001)
        assertEquals(40.0, points.first().km, 0.001)
    }

    @Test
    fun yearWindowShowsAtLeastThreeYears() {
        val points = of("2026-03-01" to 70.0, range = KmRange.YEAR)

        // 只有今年有数据时也要画 3 格，否则「趋势」只剩一个孤立点
        assertEquals(MileageSeries.MIN_YEARS, points.size)
        assertEquals(listOf("2024", "2025", "2026"), points.map { it.label })
        assertEquals(70.0, points.last().km, 0.001)
        assertEquals(0.0, points.first().km, 0.001)
    }

    @Test
    fun yearWindowExtendsToEarliestRecord() {
        val points = of(
            "2023-06-01" to 10.0,
            "2026-06-01" to 20.0,
            range = KmRange.YEAR,
        )
        assertEquals(4, points.size) // 2023..2026
        assertEquals("2023", points.first().label)
        assertEquals(10.0, points.first().km, 0.001)
        assertEquals(20.0, points.last().km, 0.001)
    }

    @Test
    fun dirtyKeysAndNegativeValuesAreIgnored() {
        val points = of(
            "not-a-date" to 999.0,
            "2026-10-30" to -5.0,
            "2026/10/29" to 8.0, // 斜杠格式要能解析
            range = KmRange.DAY,
        )
        assertEquals(8.0, points.first { it.key == "2026-10-29" }.km, 0.001)
        assertEquals(0.0, points.last().km, 0.001)
    }

    @Test
    fun statisticsOverWindow() {
        val points = of(
            "2026-10-30" to 30.0,
            "2026-10-28" to 60.0,
            range = KmRange.DAY,
        )

        assertEquals(90.0, MileageSeries.total(points), 0.001)
        // 平均按格数除（含空档），不是只除「有记录的天数」
        assertEquals(90.0 / MileageSeries.DAYS, MileageSeries.average(points), 0.001)
        val peak = MileageSeries.peak(points)
        assertNotNull(peak)
        assertEquals("2026-10-28", peak!!.key)
        assertTrue(MileageSeries.hasData(points))
    }

    @Test
    fun emptyWindowHasNoPeak() {
        val points = of(range = KmRange.DAY)
        assertNull(MileageSeries.peak(points))
        assertFalse(MileageSeries.hasData(points))
        assertEquals(0.0, MileageSeries.average(points), 0.001)
    }
}
