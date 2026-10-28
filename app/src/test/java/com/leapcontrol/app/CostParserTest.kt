package com.leapcontrol.app

import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostParser
import com.leapcontrol.app.journal.JournalDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 「一句话记账」解析器。
 *
 * 这是零记里唯一「猜用户心思」的地方，也是最容易出错的地方：
 * 金额被油号抢走、日期被当成金额、分类把「机油」认成「加油」……
 * 所以每条规则都钉一个用例在这里。
 */
class CostParserTest {

    private val today = LocalDate.of(2026, 10, 28)

    private fun parse(text: String) = CostParser.parse(text, today)

    @Test
    fun `最简写法能认出分类和金额`() {
        val hit = parse("充电 50")
        assertNotNull(hit)
        assertEquals(CostCategory.CHARGE, hit!!.category)
        assertEquals(50.0, hit.amount, 0.001)
        assertEquals(JournalDates.startOfDay(today), hit.date)
    }

    @Test
    fun `带单位的金额优先`() {
        assertEquals(300.0, parse("加油花了 300 块")!!.amount, 0.001)
        assertEquals(3800.0, parse("保险 3800 元")!!.amount, 0.001)
        assertEquals(68.0, parse("¥68 洗车")!!.amount, 0.001)
    }

    @Test
    fun `万会被换算`() {
        assertEquals(12_000.0, parse("充了 1.2 万元")!!.amount, 0.001)
    }

    @Test
    fun `油号不会被当成金额`() {
        val hit = parse("92号汽油 300")
        assertEquals(CostCategory.FUEL, hit!!.category)
        assertEquals(300.0, hit.amount, 0.001)
    }

    @Test
    fun `升数不会被当成金额`() {
        val hit = parse("加了 40 升 320")
        assertEquals(320.0, hit!!.amount, 0.001)
    }

    @Test
    fun `昨天会落到前一天`() {
        val hit = parse("昨天停车 15")
        assertEquals(CostCategory.PARKING, hit!!.category)
        assertEquals(JournalDates.startOfDay(today.minusDays(1)), hit.date)
        assertTrue(hit.hasExplicitDate)
    }

    @Test
    fun `月日日期可用且不会把日期数字当金额`() {
        val hit = parse("10月26日 洗车 40")
        assertNotNull(hit)
        assertEquals(CostCategory.WASH, hit!!.category)
        assertEquals(40.0, hit.amount, 0.001)
        assertEquals(JournalDates.startOfDay(LocalDate.of(2026, 10, 26)), hit.date)
    }

    @Test
    fun `未来的月日按去年处理`() {
        // 今天是 10/28，说「12月1日」指的是去年 12 月
        val hit = parse("12月1日 保险 4200")
        assertEquals(JournalDates.startOfDay(LocalDate.of(2025, 12, 1)), hit!!.date)
    }

    @Test
    fun `分类按具体词优先`() {
        assertEquals(CostCategory.SERVICE, parse("换机油 480")!!.category)
        assertEquals(CostCategory.SERVICE, parse("换轮胎 800")!!.category)
        assertEquals(CostCategory.INSURANCE, parse("交强险 950")!!.category)
        assertEquals(CostCategory.TOLL, parse("高速费 120")!!.category)
        assertEquals(CostCategory.CHARGE, parse("超充 90")!!.category)
    }

    @Test
    fun `没有金额就返回空`() {
        assertNull(parse("充电"))
        assertNull(parse(""))
        assertNull(parse("今天心情不错"))
    }

    @Test
    fun `金额为 0 不认`() {
        assertNull(parse("洗车 0 元"))
    }

    @Test
    fun `备注去掉金额与日期`() {
        val hit = parse("昨天 充电 50 元")!!
        assertEquals("充电", hit.note)
    }

    @Test
    fun `全角数字也能识别`() {
        assertEquals(50.0, parse("充电 ５０")!!.amount, 0.001)
    }
}
