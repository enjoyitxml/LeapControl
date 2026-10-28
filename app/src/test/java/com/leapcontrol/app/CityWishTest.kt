package com.leapcontrol.app.journal

import com.leapcontrol.app.journal.CityCatalog
import com.leapcontrol.app.journal.CityWish
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostEntry
import com.leapcontrol.app.journal.CostRange
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.JournalDates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 心愿单的存储与统计口径。 */
class CityWishTest {

    private val hz = CityCatalog.byId("浙江/杭州")!!
    private val cd = CityCatalog.byId("四川/成都")!!

    @Test
    fun `心愿单 JSON 往返不丢字段`() {
        val data = JournalData(
            wishes = listOf(
                CityWish(hz.id, hz.name, hz.province, 1000L),
                CityWish(cd.id, cd.name, cd.province, 2000L, "吃火锅"),
            ),
        )
        val back = JournalData.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(2, back.wishes.size)
        assertEquals(listOf("杭州", "成都"), back.wishes.map { it.city })
        assertEquals("吃火锅", back.wishes[1].note)
        assertEquals(1000L, back.wishes[0].added)
    }

    @Test
    fun `老版本文件没有 wishes 字段也不会崩`() {
        val legacy = JSONObject("""{"v":1,"ownershipDays":120}""")
        val data = JournalData.fromJson(legacy)
        assertTrue(data.wishes.isEmpty())
        assertEquals(0, data.wishCount)
    }

    @Test
    fun `心愿单按加入时间正序`() {
        val data = JournalData(
            wishes = listOf(
                CityWish(cd.id, cd.name, cd.province, 5000L),
                CityWish(hz.id, hz.name, hz.province, 1000L),
            ),
        )
        val back = JournalData.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(listOf("杭州", "成都"), back.wishes.map { it.city })
    }

    @Test
    fun `心愿不算点亮城市`() {
        val data = JournalData(
            wishes = listOf(CityWish(hz.id, hz.name, hz.province, 1L)),
        )
        assertEquals(0, data.cityCount)
        assertEquals(1, data.wishCount)
        assertEquals(0, data.provinceCount)
    }

    @Test
    fun `坏条目单独丢弃`() {
        val raw = JSONObject(
            """{"wishes":[{"id":"浙江/杭州","city":"杭州","province":"浙江","added":1},{"id":"四川/成都","province":"四川"}]}""",
        )
        val data = JournalData.fromJson(raw)
        assertEquals(1, data.wishes.size)
    }

    @Test
    fun `城市表能按 id 找到`() {
        assertEquals("杭州", CityCatalog.byId(hz.id)?.name)
        assertNull(CityCatalog.byId("不存在/城市"))
    }
}

/** 日 / 周 / 月 / 年 / 全部五种口径。 */
class CostStatsRangeTest {

    private val today = LocalDate.of(2026, 10, 28) // 周三

    private fun entry(daysAgo: Long, amount: Double, category: CostCategory = CostCategory.CHARGE): CostEntry =
        CostEntry(
            id = "e$daysAgo-$amount",
            date = JournalDates.startOfDay(today.minusDays(daysAgo)),
            category = category,
            amount = amount,
        )

    private fun dataOf(vararg costs: CostEntry) = JournalData(costs = costs.toList())

    @Test
    fun `今日只算今天`() {
        val data = dataOf(entry(0, 50.0), entry(1, 20.0))
        val summary = CostStats.of(data, null, today)
        assertEquals(50.0, summary.today, 0.001)
        assertEquals(20.0, summary.yesterday, 0.001)
        assertEquals(50.0, summary.amountOf(CostRange.DAY), 0.001)
    }

    @Test
    fun `本周按周一切`() {
        // 2026-10-28 是周三，本周一是 10-26；10-25（周日）属于上周
        val data = dataOf(entry(0, 10.0), entry(2, 5.0), entry(3, 7.0))
        val summary = CostStats.of(data, null, today)
        assertEquals(15.0, summary.thisWeek, 0.001)
        assertEquals(7.0, summary.lastWeek, 0.001)
    }

    @Test
    fun `较上期差额在没有记录时为 null`() {
        val empty = CostStats.of(JournalData.EMPTY, null, today)
        assertNull(empty.deltaOf(CostRange.DAY))
        assertNull(empty.deltaOf(CostRange.WEEK))
        assertNull(empty.deltaOf(CostRange.ALL))
    }

    @Test
    fun `分类汇总跟着区间走`() {
        val data = dataOf(
            entry(0, 50.0, CostCategory.CHARGE),
            entry(0, 10.0, CostCategory.PARKING),
            entry(40, 300.0, CostCategory.FUEL),
        )
        val month = CostStats.byCategoryOf(data, CostRange.MONTH, today)
        assertEquals(listOf(CostCategory.CHARGE, CostCategory.PARKING), month.map { it.first })
        val all = CostStats.byCategoryOf(data, CostRange.ALL, today)
        assertEquals(3, all.size)
        assertEquals(CostCategory.FUEL, all.first().first)
    }

    @Test
    fun `趋势长度与粒度对得上`() {
        val data = dataOf(entry(0, 50.0))
        assertEquals(7, CostStats.trendOf(data, CostRange.DAY, today).size)
        assertEquals(8, CostStats.trendOf(data, CostRange.WEEK, today).size)
        assertEquals(6, CostStats.trendOf(data, CostRange.MONTH, today).size)
        assertEquals(5, CostStats.trendOf(data, CostRange.YEAR, today).size)
        assertEquals(6, CostStats.trendOf(data, CostRange.ALL, today).size)
    }

    @Test
    fun `趋势最后一根是当期`() {
        val data = dataOf(entry(0, 50.0))
        val trend = CostStats.trendOf(data, CostRange.DAY, today)
        assertEquals("今天", trend.last().label)
        assertEquals(50.0, trend.last().amount, 0.001)
        assertEquals(0.0, trend.first().amount, 0.001)
    }

    @Test
    fun `区间边界左闭右开`() {
        val (from, to) = CostStats.boundsOf(CostRange.DAY, today)
        val data = dataOf(entry(0, 50.0), entry(1, 20.0))
        // 左闭：今天的记录落在 [from, to) 里
        assertEquals(50.0, data.costBetween(from, to), 0.001)
        // 右开：空区间恒为 0
        assertEquals(0.0, data.costBetween(from, from), 0.001)
        // 昨天整天只在 [from-1天, from) 里
        assertEquals(20.0, data.costBetween(from - 86_400_000L, from), 0.001)
    }
}
