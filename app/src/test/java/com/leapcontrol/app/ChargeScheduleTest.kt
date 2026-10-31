package com.leapcontrol.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 充电中心的时间/周期逻辑。
 *
 * 这组用例锁三件事：
 *  1. **跨天判断** —— 23:00→07:00 必须显示「次日」，否则卡片上会写成「23:00-07:00」，
 *     用户会以为只充 0 分钟；09:00→18:00 又不能标次日。
 *  2. **重复日文案与掩码** —— 190 的 `cycles` 是 7 位 0/1 掩码（索引 0=周一），
 *     161 的 `days` 是整数数组（0=周日），两套编号规则不同，混用会把周一设成周日。
 *  3. **充电上限对齐** —— 车端只收整数百分比，滑杆是 5% 一档，越界值必须夹到 80..100。
 *     下界是 80 而不是 50：官方 App 与车机都只有 80~100，低于 80 的档位不存在。
 */
class ChargeScheduleTest {

    @Test
    fun `结束早于开始算跨天`() {
        assertTrue(ChargeSchedule.isOvernight("23:00", "07:00"))
        assertTrue(ChargeSchedule.isOvernight("22:30", "06:00"))
        // 相等按一整天处理（车端也是当日 +1）
        assertTrue(ChargeSchedule.isOvernight("23:00", "23:00"))
    }

    @Test
    fun `同日时段不标次日`() {
        assertFalse(ChargeSchedule.isOvernight("09:00", "18:00"))
        assertFalse(ChargeSchedule.isOvernight("00:00", "08:00"))
        // 时间格式不合法时不做任何假设
        assertFalse(ChargeSchedule.isOvernight("", "07:00"))
        assertFalse(ChargeSchedule.isOvernight("25:00", "07:00"))
    }

    @Test
    fun `时段文案带次日标记`() {
        assertEquals("23:00-次日07:00", ChargeSchedule.rangeText("23:00", "07:00"))
        assertEquals("09:00-18:00", ChargeSchedule.rangeText("09:00", "18:00"))
    }

    @Test
    fun `重复日文案`() {
        assertEquals("每天", ChargeSchedule.daysLabel(ChargeSchedule.ALL_DAYS))
        assertEquals("工作日", ChargeSchedule.daysLabel(setOf(1, 2, 3, 4, 5)))
        assertEquals("周末", ChargeSchedule.daysLabel(setOf(6, 7)))
        assertEquals("周一、周四", ChargeSchedule.daysLabel(setOf(1, 4)))
        // 空集合按全周处理：车端 cycles 为空等价于每天
        assertEquals("每天", ChargeSchedule.daysLabel(emptySet()))
    }

    @Test
    fun `卡片副标题`() {
        assertEquals(
            "每天 23:00-次日07:00",
            ChargeSchedule.summaryText("23:00", "07:00", ChargeSchedule.ALL_DAYS),
        )
        assertEquals(
            "工作日 09:00-18:00",
            ChargeSchedule.summaryText("09:00", "18:00", setOf(1, 2, 3, 4, 5)),
        )
    }

    @Test
    fun `充电上限夹取并对齐5的倍数`() {
        // 下界 80：低于它的值（包括老版本存下来的 50）一律抬到 80
        assertEquals(80, ChargeSchedule.sanitizeSoc(20))
        assertEquals(80, ChargeSchedule.sanitizeSoc(50))
        assertEquals(80, ChargeSchedule.sanitizeSoc(80))
        assertEquals(100, ChargeSchedule.sanitizeSoc(120))
        assertEquals(90, ChargeSchedule.sanitizeSoc(90))
        assertEquals(95, ChargeSchedule.sanitizeSoc(93))
        assertEquals(100, ChargeSchedule.sanitizeSoc(98))
        // 80..100 之间只有 5 个档位
        assertEquals(listOf(80, 85, 90, 95, 100), ChargeSchedule.LIMIT_STEPS)
    }

    @Test
    fun `滑杆位置与上限互转`() {
        assertEquals(0f, ChargeSchedule.socToFraction(80), 0.001f)
        assertEquals(1f, ChargeSchedule.socToFraction(100), 0.001f)
        assertEquals(0.5f, ChargeSchedule.socToFraction(90), 0.001f)
        assertEquals(80, ChargeSchedule.fractionToSoc(0f))
        assertEquals(90, ChargeSchedule.fractionToSoc(0.5f))
        assertEquals(100, ChargeSchedule.fractionToSoc(1f))
    }

    @Test
    fun `时间格式统一两位`() {
        assertEquals("07:05", ChargeSchedule.formatTime(7, 5))
        assertEquals("23:00", ChargeSchedule.formatTime(23, 0))
        assertEquals("07:30", ChargeSchedule.timeOfDay("2026-10-28 07:30:00"))
        assertEquals("23:00", ChargeSchedule.timeOfDay("23:00"))
        assertNull(ChargeSchedule.timeOfDay(""))
    }

    @Test
    fun `161 的 days 数组把0映射成周日`() {
        val days = ChargeSchedule.daysFrom161(JSONArray(listOf(0, 1, 2)))
        assertEquals(setOf(7, 1, 2), days)
        // 空数组/缺失按每天
        assertEquals(ChargeSchedule.ALL_DAYS, ChargeSchedule.daysFrom161(null))
        assertEquals(ChargeSchedule.ALL_DAYS, ChargeSchedule.daysFrom161(JSONArray()))
    }

    @Test
    fun `解析161预约项`() {
        val payload = JSONObject()
            .put(
                "controls",
                JSONArray().put(
                    JSONObject()
                        .put("on", "1")
                        .put("start_time", "2026-10-28 07:30:00")
                        .put("days", JSONArray(listOf(1, 2, 3, 4, 5))),
                ),
            )
            .toString()

        val state = ChargeSchedule.parsePreheatState(payload)!!
        assertTrue(state.hasSchedule)
        assertEquals("07:30", state.startTime)
        assertEquals("1,1,1,1,1,0,0", state.days)
    }

    @Test
    fun `161 没有预约项时不当作开启`() {
        val payload = JSONObject().put("controls", JSONArray()).toString()
        val state = ChargeSchedule.parsePreheatState(payload)!!
        assertFalse(state.hasSchedule)
        assertNull(state.startTime)
        assertNull(state.days)
    }

    @Test
    fun `161 返回结构异常时返回空让调用方保留草稿`() {
        assertNull(ChargeSchedule.parsePreheatState(null))
        assertNull(ChargeSchedule.parsePreheatState(""))
        assertNull(ChargeSchedule.parsePreheatState("not-json"))
        assertNull(ChargeSchedule.parsePreheatState(JSONObject().toString()))
    }

    @Test
    fun `草稿默认值与官方默认一致`() {
        val draft = ChargeCenterDraft()
        assertEquals(100, draft.limitSoc)
        assertEquals("23:00", draft.scheduledStart)
        assertEquals("07:00", draft.scheduledEnd)
        assertTrue(draft.continueUntilLimit)
        assertEquals(ChargeSchedule.ALL_DAYS, draft.scheduledDays)
    }
}
