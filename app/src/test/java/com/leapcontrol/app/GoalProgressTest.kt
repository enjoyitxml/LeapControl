package com.leapcontrol.app

import com.leapcontrol.app.journal.CarGoal
import com.leapcontrol.app.journal.GoalProgress
import com.leapcontrol.app.journal.JournalDates
import com.leapcontrol.app.journal.MedalStats
import com.leapcontrol.app.journal.Medals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 用车目标的进度与勋章。
 *
 * 这里最容易翻车的是「提车当天算第几天」和「进度百分比与勋章档位对不齐」，
 * 所以两个都写成显式断言。
 */
class GoalProgressTest {

    private val today = LocalDate.of(2026, 10, 28)
    private fun goal(years: Int = 10, price: Double = 189_800.0, delivery: LocalDate = LocalDate.of(2026, 1, 1)) =
        CarGoal(years, price, JournalDates.startOfDay(delivery))

    @Test
    fun `提车当天算第一天`() {
        val progress = GoalProgress.of(goal(delivery = today), today)
        assertEquals(1, progress.daysOwned)
        assertTrue(progress.fraction > 0f)
        // 十年目标的第一天，四舍五入后就是 0%，不该被「美化成 1%」
        assertEquals(0, progress.percent)
    }

    @Test
    fun `按提车日期算已陪伴天数`() {
        // 2026-01-01 → 2026-10-28 相差 300 天，提车当天算第 1 天，所以是 301
        val progress = GoalProgress.of(goal(), today)
        assertEquals(301, progress.daysOwned)
        assertEquals(3650, progress.targetDays)
        assertEquals(3349, progress.remainingDays)
        assertEquals(8, progress.percent)
    }

    @Test
    fun `日均摊掉落地价`() {
        val progress = GoalProgress.of(goal(price = 100_000.0, delivery = LocalDate.of(2026, 10, 27)), today)
        assertEquals(2, progress.daysOwned)
        assertEquals(50_000.0, progress.pricePerDay, 0.01)
    }

    @Test
    fun `目标达成后没有预计日期且进度封顶`() {
        val progress = GoalProgress.of(goal(years = 1, delivery = LocalDate.of(2024, 1, 1)), today)
        assertTrue(progress.reached)
        assertNull(progress.etaDate)
        assertEquals(1f, progress.barFraction, 0.0001f)
        assertTrue(progress.fraction > 1f)
    }

    @Test
    fun `提车日期由提车天数反推`() {
        val millis = GoalProgress.deliveryFromOwnershipDays(100, today)
        assertEquals(LocalDate.of(2026, 7, 21), JournalDates.toLocalDate(millis))
        // 反推出来的日期再算回去，应当还是 100 天
        assertEquals(100, GoalProgress.of(goal(delivery = JournalDates.toLocalDate(millis)), today).daysOwned)
    }

    @Test
    fun `天数勋章按天数解锁`() {
        val stats = MedalStats(days = 301, fraction = 0.08f, km = null)
        val unlocked = Medals.unlockedIds(stats)
        assertTrue(unlocked.contains("day_1"))
        assertTrue(unlocked.contains("day_100"))
        assertFalse(unlocked.contains("day_365"))
    }

    @Test
    fun `里程勋章需要里程数据`() {
        assertFalse(Medals.unlockedIds(MedalStats(days = 1, km = null)).contains("km_1000"))
        assertTrue(Medals.unlockedIds(MedalStats(days = 1, km = 1_200.0)).contains("km_1000"))
        assertFalse(Medals.unlockedIds(MedalStats(days = 1, km = 1_200.0)).contains("km_10000"))
    }

    @Test
    fun `进度勋章按百分比解锁`() {
        assertTrue(Medals.unlockedIds(MedalStats(days = 1, fraction = 0.12f)).contains("p_10"))
        assertFalse(Medals.unlockedIds(MedalStats(days = 1, fraction = 0.12f)).contains("p_25"))
        assertTrue(Medals.unlockedIds(MedalStats(days = 1, fraction = 1.2f)).contains("p_100"))
    }

    @Test
    fun `下一枚勋章是最接近拿到的那枚`() {
        val stats = MedalStats(days = 301, fraction = 0.08f, km = null)
        val next = Medals.next(stats)
        assertNotNull(next)
        // 301 天时「一年」的完成度（82.5%）高于「10% 进度」（80%），所以下一枚是一年
        assertEquals("day_365", next!!.id)
        assertEquals("差 64 天", next.gapText(stats))
    }
}
