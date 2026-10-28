package com.leapcontrol.app.journal

import com.leapcontrol.app.journal.CityCatalog
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.JournalDates
import com.leapcontrol.app.journal.QuickLog
import com.leapcontrol.app.journal.QuickPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 灵感球「一句话记账 / 打卡」的解析测试。
 *
 * 这些用例对应提示语里教用户说的那几句，改动 [QuickLog] 或 [CostParser] 前先跑一遍。
 */
class QuickLogTest {

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun `说了花费就只记账`() {
        val plan = QuickLog.parse("今天充电花了 50", today)
        assertNotNull(plan)
        assertEquals(1, plan!!.actions.size)
        val cost = plan.cost
        assertNotNull(cost)
        assertEquals(CostCategory.CHARGE, cost!!.category)
        assertEquals(50.0, cost.amount, 0.001)
        assertEquals(JournalDates.startOfDay(today), cost.date)
        assertNull(plan.checkin)
    }

    @Test
    fun `说了去城市就只打卡`() {
        val plan = QuickLog.parse("今天点亮了杭州", today)
        assertNotNull(plan)
        assertNull(plan!!.cost)
        val checkin = plan.checkin
        assertNotNull(checkin)
        assertEquals("杭州", checkin!!.city.name)
        assertEquals("浙江", checkin.city.province)
        assertEquals(JournalDates.startOfDay(today), checkin.date)
    }

    @Test
    fun `昨天去的城市记在昨天`() {
        val plan = QuickLog.parse("昨天到了成都", today)
        val checkin = plan?.checkin
        assertNotNull(checkin)
        assertEquals("成都", checkin!!.city.name)
        assertEquals(JournalDates.startOfDay(today.minusDays(1)), checkin.date)
    }

    @Test
    fun `一句话里两件事都记`() {
        val plan = QuickLog.parse("今天去了杭州，充电 50", today)
        assertNotNull(plan)
        assertEquals(2, plan!!.actions.size)
        assertEquals("杭州", plan.checkin?.city?.name)
        assertEquals(CostCategory.CHARGE, plan.cost?.category)
        assertEquals(50.0, plan.cost!!.amount, 0.001)
    }

    @Test
    fun `城市只是地点状语时只记账`() {
        // 「在杭州停车 20」没有「去了 / 打卡」这类词，用户想记的是停车费
        val plan = QuickLog.parse("在杭州停车 20", today)
        assertNotNull(plan)
        assertNull(plan!!.checkin)
        assertEquals(CostCategory.PARKING, plan.cost?.category)
        assertEquals(20.0, plan.cost!!.amount, 0.001)
    }

    @Test
    fun `只说城市名也能打卡`() {
        val plan = QuickLog.parse("西安", today)
        assertEquals("西安", plan?.checkin?.city?.name)
        assertNull(plan?.cost)
    }

    @Test
    fun `认不出的句子返回 null`() {
        assertNull(QuickLog.parse("今天天气不错", today))
        assertNull(QuickLog.parse("   ", today))
    }

    @Test
    fun `没有金额的句子不会瞎猜一个数字`() {
        val plan = QuickLog.parse("今天洗车", today)
        assertNull(plan)
    }

    @Test
    fun `描述文案能同时说清两件事`() {
        val plan = QuickLog.parse("今天去了杭州，充电 50", today)!!
        val text = plan.describe()
        assertTrue(text.contains("杭州打卡"))
        assertTrue(text.contains("充电"))
        assertTrue(text.contains("50"))
    }

    @Test
    fun `城市名取最长匹配`() {
        // 「乌鲁木齐」不能被更短的「鲁」之类截走
        assertEquals("乌鲁木齐", CityCatalog.findInText("今天到了乌鲁木齐")?.name)
        assertNull(CityCatalog.findInText("今天没出门"))
    }

    @Test
    fun `空计划不算有内容`() {
        assertTrue(QuickPlan(emptyList()).isEmpty)
        assertFalse(QuickLog.parse("充电 30", today)!!.isEmpty)
    }
}
