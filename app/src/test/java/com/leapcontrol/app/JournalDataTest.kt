package com.leapcontrol.app

import com.leapcontrol.app.journal.CarGoal
import com.leapcontrol.app.journal.CityCatalog
import com.leapcontrol.app.journal.CityCheckin
import com.leapcontrol.app.journal.CityProjection
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostEntry
import com.leapcontrol.app.journal.CostRange
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.JournalDates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 零记的数据层：JSON 往返、分类 / 月度汇总、城市表完整性。
 *
 * JSON 往返必须测 —— 账本一旦序列化错，用户记了半年的花费就全变空数组了。
 */
class JournalDataTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun entry(day: LocalDate, cat: CostCategory, amount: Double, note: String = "") = CostEntry(
        id = "$cat-${JournalDates.dayKey(day)}-$amount",
        date = JournalDates.startOfDay(day),
        category = cat,
        amount = amount,
        note = note,
    )

    @Test
    fun `空数据序列化后仍是空数据`() {
        val round = JournalData.fromJson(JSONObject(JournalData.EMPTY.toJson().toString()))
        assertTrue(round.isEmpty)
        assertNull(round.goal)
    }

    @Test
    fun `目标与花费可以完整往返`() {
        val data = JournalData(
            goal = CarGoal(10, 189_800.0, JournalDates.startOfDay(LocalDate.of(2026, 1, 1))),
            ownershipDays = 301,
            costs = listOf(
                entry(LocalDate.of(2026, 10, 26), CostCategory.CHARGE, 52.5, "家充"),
                entry(LocalDate.of(2026, 10, 1), CostCategory.INSURANCE, 3800.0),
            ),
            checkins = listOf(
                CityCheckin("浙江/杭州", "杭州", "浙江", JournalDates.startOfDay(LocalDate.of(2026, 5, 1))),
            ),
            dailyKm = mapOf("2026-10-27" to 43.2),
        )
        val round = JournalData.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(10, round.goal?.targetYears)
        assertEquals(189_800.0, round.goal!!.landingPrice, 0.001)
        assertEquals(301, round.ownershipDays)
        assertEquals(2, round.costs.size)
        assertEquals(CostCategory.CHARGE, round.costs.first { it.note == "家充" }.category)
        assertEquals(1, round.checkins.size)
        assertEquals(43.2, round.dailyKm["2026-10-27"]!!, 0.001)
    }

    @Test
    fun `坏数据不会让整份账本丢光`() {
        val raw = JSONObject(
            """{"v":1,"costs":[{"id":"a","date":1,"cat":"charge","amount":50},{"id":"b"},null],
               "checkins":[{"id":"x"}],"daily":{"2026-10-27":"abc","2026-10-26":12}}""",
        )
        val data = JournalData.fromJson(raw)
        // 缺金额的那条丢弃；合法的那条保留
        assertEquals(1, data.costs.size)
        assertEquals(0, data.checkins.size)
        // 非数字的里程丢弃，数字的保留
        assertEquals(12.0, data.dailyKm["2026-10-26"]!!, 0.001)
        assertNull(data.dailyKm["2026-10-27"])
    }

    @Test
    fun `分类汇总按金额从大到小`() {
        val data = JournalData(
            costs = listOf(
                entry(LocalDate.of(2026, 10, 1), CostCategory.CHARGE, 100.0),
                entry(LocalDate.of(2026, 10, 2), CostCategory.CHARGE, 50.0),
                entry(LocalDate.of(2026, 10, 3), CostCategory.INSURANCE, 900.0),
            ),
        )
        val byCategory = data.costByCategory()
        assertEquals(2, byCategory.size)
        assertEquals(CostCategory.INSURANCE, byCategory[0].first)
        assertEquals(900.0, byCategory[0].second, 0.001)
        assertEquals(CostCategory.CHARGE, byCategory[1].first)
        assertEquals(150.0, byCategory[1].second, 0.001)
    }

    @Test
    fun `月度与年度统计只算对应区间`() {
        val data = JournalData(
            costs = listOf(
                entry(LocalDate.of(2026, 10, 1), CostCategory.CHARGE, 100.0),
                entry(LocalDate.of(2026, 9, 30), CostCategory.CHARGE, 200.0),
                entry(LocalDate.of(2025, 12, 31), CostCategory.INSURANCE, 3000.0),
            ),
        )
        assertEquals(100.0, data.costOfMonth("2026-10"), 0.001)
        assertEquals(200.0, data.costOfMonth("2026-09"), 0.001)
        assertEquals(300.0, data.costOfYear(2026), 0.001)
        assertEquals(3300.0, data.costTotal, 0.001)
    }

    @Test
    fun `有目标时日均按用车天数摊`() {
        val data = JournalData(
            goal = CarGoal(10, 100_000.0, JournalDates.startOfDay(today.minusDays(9))),
            costs = listOf(entry(today, CostCategory.CHARGE, 100.0)),
        )
        val summary = CostStats.of(data, data.goal, today)
        // 10 天（含提车当天）
        assertEquals(10, summary.days)
        assertEquals(10.0, summary.perDay, 0.001)
        assertTrue(summary.daysFromDelivery)
    }

    @Test
    fun `没有目标时日均按记账首日到今天摊`() {
        val data = JournalData(
            costs = listOf(
                entry(today.minusDays(4), CostCategory.CHARGE, 50.0),
                entry(today, CostCategory.CHARGE, 50.0),
            ),
        )
        val summary = CostStats.of(data, null, today)
        // 5 天（含首日与今天）
        assertEquals(5, summary.days)
        assertEquals(20.0, summary.perDay, 0.001)
        assertTrue(!summary.daysFromDelivery)
    }

    @Test
    fun `近六个月柱子数量固定且以当月结尾`() {
        val summary = CostStats.of(JournalData(), null, today)
        assertEquals(CostStats.MONTHS, summary.months.size)
        assertEquals("2026-10", summary.months.last().key)
        assertEquals("2026-05", summary.months.first().key)
    }

    @Test
    fun `日均跟着区间换口径`() {
        // 提车 1000 天、只在今天花了 200：本月看日均不能按 1000 天摊
        val data = JournalData(
            goal = CarGoal(10, 100_000.0, JournalDates.startOfDay(today.minusDays(999))),
            costs = listOf(entry(today, CostCategory.PARKING, 200.0)),
        )
        val summary = CostStats.of(data, data.goal, today)

        val month = summary.averageOf(CostRange.MONTH)
        assertEquals(28, month.divisor) // 10 月 28 号，本月已过 28 天
        assertEquals(200.0 / 28, month.value, 0.001)
        assertEquals("本月日均（28 天）", month.label)

        // 本周从周一（10/26）数到今天 = 3 天
        assertEquals(3, summary.averageOf(CostRange.WEEK).divisor)
        // 今年到今天是第 301 天
        assertEquals(301, summary.averageOf(CostRange.YEAR).divisor)
        // 全部仍然是提车天数
        assertEquals(1000, summary.averageOf(CostRange.ALL).divisor)
        assertEquals(0.2, summary.averageOf(CostRange.ALL).value, 0.001)

        // 今日分摊到一天等于支出本身，没有信息量 → 改看笔均
        val day = summary.averageOf(CostRange.DAY)
        assertEquals(1, day.divisor)
        assertEquals(200.0, day.value, 0.001)
        assertEquals("今日笔均", day.label)
    }

    @Test
    fun `月初与周一当天分母不会退化成零`() {
        // 2026-06-01 既是月初又是周一，两个分母都该是 1
        val firstDay = LocalDate.of(2026, 6, 1)
        val data = JournalData(
            costs = listOf(entry(firstDay, CostCategory.PARKING, 50.0)),
        )
        val summary = CostStats.of(data, null, firstDay)
        assertEquals(1, summary.averageOf(CostRange.MONTH).divisor)
        assertEquals(50.0, summary.averageOf(CostRange.MONTH).value, 0.001)
        assertEquals(1, summary.averageOf(CostRange.WEEK).divisor)
        assertEquals("本周日均（1 天）", summary.averageOf(CostRange.WEEK).label)
    }

    @Test
    fun `今日没记账时笔均不会除零`() {
        val data = JournalData(costs = listOf(entry(today.minusDays(2), CostCategory.PARKING, 10.0)))
        val summary = CostStats.of(data, null, today)
        assertEquals(0, summary.averageOf(CostRange.DAY).divisor)
        assertEquals(0.0, summary.averageOf(CostRange.DAY).value, 0.001)
    }

    @Test
    fun `城市表覆盖三百座以上且 id 唯一`() {
        assertTrue(CityCatalog.ALL.size >= 300)
        assertEquals(CityCatalog.ALL.size, CityCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `每个城市都能投影到画布内`() {
        CityCatalog.ALL.forEach { city ->
            val x = CityProjection.x(city.lon)
            val y = CityProjection.y(city.lat)
            assertTrue(city.name, x in 0f..1f)
            assertTrue(city.name, y in 0f..1f)
        }
    }

    @Test
    fun `搜索同时支持城市与省份`() {
        assertTrue(CityCatalog.search("杭州").any { it.name == "杭州" })
        assertTrue(CityCatalog.search("云南").any { it.province == "云南" })
        assertTrue(CityCatalog.search("").isEmpty())
    }

    @Test
    fun `省份分组不丢城市`() {
        val grouped = CityCatalog.BY_PROVINCE.sumOf { it.second.size }
        assertEquals(CityCatalog.ALL.size, grouped)
        assertEquals(CityCatalog.BY_PROVINCE.size, CityCatalog.PROVINCES.size)
    }

    @Test
    fun `自定义城市落在所属省份上`() {
        val custom = CityCatalog.custom("浙江", "乌镇")
        assertNotNull(custom)
        val anchor = CityCatalog.of("浙江", "杭州")!!
        assertEquals(anchor.lat.toDouble(), custom!!.lat.toDouble(), 0.001)
        // 表里已有的城市直接返回原坐标，不套用省份锚点
        assertEquals(anchor.lon.toDouble(), CityCatalog.custom("浙江", "杭州")!!.lon.toDouble(), 0.001)
    }
}
