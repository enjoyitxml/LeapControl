package com.leapcontrol.app

import com.leapcontrol.app.journal.JournalDates
import com.leapcontrol.app.journal.MaintenancePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剩余保养里程的取数与口径。
 *
 * 用户反馈「填了上次保养的公里数，剩余保养里程不更新」，锁定两个问题：
 *  1. `MileageEnergySummary` 刷新时把新数据合进缓存的方向反了（缓存优先），
 *     总里程从第一次拉取之后就不再变化；
 *  2. 剩余里程算的是「里程接口的累计值」，那份数据服务端滞后一天以上，
 *     且本地还有 15 分钟缓存 —— 用它算，车开一个月都不会掉一公里。
 *
 * 这组用例把两件事钉死：刷新必须新值优先、当前里程必须表显优先。
 */
class MaintenanceReminderTest {

    private fun plan(
        mode: MaintenancePlan.Mode = MaintenancePlan.Mode.BOTH,
        lastKm: Double = 5_000.0,
        months: Int = 12,
        km: Double = 10_000.0,
        lastDate: Long = JournalDates.todayStart(),
    ) = MaintenancePlan(mode = mode, lastDate = lastDate, lastKm = lastKm, months = months, km = km)

    // ---------------- 剩余里程的算法 ----------------

    @Test
    fun `剩余里程随当前表显里程递减`() {
        val p = plan(lastKm = 5_000.0, km = 10_000.0)
        assertEquals(15_000.0, p.nextKm()!!, 0.001)
        // 刚保养完：还剩整整一个周期
        assertEquals(10_000.0, p.remainingKm(5_000.0)!!, 0.001)
        // 开了 3000 公里：剩 7000
        assertEquals(7_000.0, p.remainingKm(8_000.0)!!, 0.001)
        // 开过头：负数，卡上显示「已超」
        assertEquals(-1_000.0, p.remainingKm(16_000.0)!!, 0.001)
    }

    @Test
    fun `拿不到当前里程时剩余里程为 null 而不是 0`() {
        val p = plan()
        assertNull("当前里程缺失不能算成 0，否则卡片会报「还剩 15 000 km」这种假数据", p.remainingKm(null))
    }

    @Test
    fun `按时间模式不算里程`() {
        assertNull(plan(mode = MaintenancePlan.Mode.TIME).remainingKm(8_000.0))
        assertTrue(plan(mode = MaintenancePlan.Mode.TIME).nextDate() != null)
    }

    // ---------------- 主数字报哪个维度 ----------------

    @Test
    fun `里程更紧时里程比例更小`() {
        // 周期 12 个月 / 10 000 km，刚保养完：两个维度比例都 ≈ 1
        val fresh = plan(lastDate = JournalDates.todayStart())
        val daysFraction = fresh.remainingDaysFraction()!!
        assertEquals(1.0, daysFraction, 0.02)
        assertEquals(1.0, fresh.remainingKmFraction(5_000.0)!!, 0.001)

        // 时间才过一半、里程已经快用光：里程比例明显更小 → 卡上报公里
        val halfYearAgo = JournalDates.addMonths(JournalDates.todayStart(), -6)
        val tightKm = plan(lastDate = halfYearAgo)
        val kmFraction = tightKm.remainingKmFraction(14_000.0)!!  // 只剩 1000 km
        assertEquals(0.1, kmFraction, 0.001)
        assertTrue("里程比例应小于时间比例", kmFraction < tightKm.remainingDaysFraction()!!)

        // 反过来：里程还很宽裕，时间更紧 → 报天数
        val tightDays = plan(lastDate = JournalDates.addMonths(JournalDates.todayStart(), -11))
        assertTrue(tightDays.remainingDaysFraction()!! < tightDays.remainingKmFraction(5_000.0)!!)
    }

    @Test
    fun `周期为 0 时不参与比例比较`() {
        // km = 0 说明没设里程周期，比例算不出来（null），交给时间维度
        assertNull(plan(km = 0.0).remainingKmFraction(8_000.0))
        // 当前里程缺失同理
        assertNull(plan().remainingKmFraction(null))
    }

    // ---------------- 当前里程的取数口径 ----------------

    @Test
    fun `表显里程优先于里程接口的累计值`() {
        assertEquals(12_345.0, OwnershipStats.currentKm("12,345", 9_000.0)!!, 0.001)
        assertEquals(12_345.6, OwnershipStats.currentKm("12345.6km", null)!!, 0.001)
    }

    @Test
    fun `表显缺失或为 0 时退回里程接口`() {
        assertEquals(9_000.0, OwnershipStats.currentKm(null, 9_000.0)!!, 0.001)
        assertEquals(9_000.0, OwnershipStats.currentKm("", 9_000.0)!!, 0.001)
        assertEquals("表显 0 视为没读到，不能把剩余里程凭空拉满", 9_000.0, OwnershipStats.currentKm("0", 9_000.0)!!, 0.001)
    }

    @Test
    fun `两个来源都没有就是 null`() {
        assertNull(OwnershipStats.currentKm(null, null))
        assertNull(OwnershipStats.currentKm("--", 0.0))
    }

    // ---------------- 刷新方向（本次的根因） ----------------

    @Test
    fun `刷新时新值覆盖旧缓存`() {
        val cached = MileageEnergySummary(
            totalMileageKm = 8_000.0,
            overallConsumption = 15.0,
            ownershipDays = 100,
            capturedAt = 1_000L,
        )
        val fresh = MileageEnergySummary(totalMileageKm = 8_250.0, capturedAt = 2_000L)
        val merged = cached.refreshedBy(fresh)
        assertEquals("总里程必须跟着新值走", 8_250.0, merged.totalMileageKm!!, 0.001)
        assertEquals("新响应没给的字段才用旧值补", 15.0, merged.overallConsumption!!, 0.001)
        assertEquals(100, merged.ownershipDays)
        assertEquals(2_000L, merged.capturedAt)
    }

    @Test
    fun `默认方向仍是用另一份补空缺`() {
        // merge 的语义没变：本对象优先、other 补空。两道口子别写混。
        val a = MileageEnergySummary(totalMileageKm = 1_000.0, capturedAt = 1_000L)
        val b = MileageEnergySummary(totalMileageKm = 2_000.0, overallConsumption = 12.0, capturedAt = 2_000L)
        val merged = a.merge(b)
        assertEquals(1_000.0, merged.totalMileageKm!!, 0.001)
        assertEquals(12.0, merged.overallConsumption!!, 0.001)
    }

    @Test
    fun `近 7 日明细不会被空响应抹掉`() {
        val cached = MileageEnergySummary(
            totalMileageKm = 8_000.0,
            recentDays = listOf(DailyMileage("2026-10-29", 42.0)),
            capturedAt = 1_000L,
        )
        // 累计接口不带 recentDays，合完必须还在
        val merged = cached.refreshedBy(MileageEnergySummary(totalMileageKm = 8_100.0, capturedAt = 2_000L))
        assertEquals(1, merged.recentDays.size)
        assertEquals(42.0, merged.recentDays.first().km, 0.001)
        assertEquals(8_100.0, merged.totalMileageKm!!, 0.001)
    }
}
