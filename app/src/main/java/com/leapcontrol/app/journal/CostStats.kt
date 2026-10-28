package com.leapcontrol.app.journal

import java.time.LocalDate
import java.time.YearMonth

/** 月度支出柱状图的一根柱子。 */
data class MonthPoint(val key: String, val label: String, val amount: Double)

/**
 * 用车花销的汇总口径。
 *
 * [perDay] 的分母是**用车天数**（提车至今），不是「记账覆盖的天数」——
 * 用户想知道的是「这辆车每天花我多少钱」，没记账的那几天也是要摊的。
 * 没有设置用车目标时，退化成「最早一条记录 → 今天」，并在文案里说明。
 */
data class CostSummary(
    val total: Double,
    val days: Int,
    val perDay: Double,
    val thisMonth: Double,
    val lastMonth: Double,
    val thisYear: Double,
    val entryCount: Int,
    val byCategory: List<Pair<CostCategory, Double>>,
    val months: List<MonthPoint>,
    /** 日均的分母是不是「提车至今」（false = 用最早记录推算的近似值）。 */
    val daysFromDelivery: Boolean,
) {
    val empty: Boolean get() = entryCount == 0

    /** 本月比上月多花多少（负数 = 省了）。两个月都没记账时为 null。 */
    val monthDelta: Double?
        get() = if (thisMonth <= 0.0 && lastMonth <= 0.0) null else thisMonth - lastMonth
}

object CostStats {

    /** 月度柱状图显示几个月（含当月）。 */
    const val MONTHS = 6

    fun of(
        data: JournalData,
        goal: CarGoal?,
        today: LocalDate = JournalDates.today(),
    ): CostSummary {
        val total = data.costTotal
        val deliveryDays = goal?.let { GoalProgress.of(it, today).daysOwned }
        val earliest = data.costs.minOfOrNull { it.date }
        val fallbackDays = earliest?.let {
            (JournalDates.toLocalDate(it).let { d -> java.time.temporal.ChronoUnit.DAYS.between(d, today) } + 1)
                .toInt().coerceAtLeast(1)
        } ?: 1
        val days = deliveryDays ?: fallbackDays
        val thisMonthKey = JournalDates.yearMonthKey(JournalDates.startOfDay(today))
        val lastMonthKey = JournalDates.yearMonthKey(
            JournalDates.startOfDay(today.withDayOfMonth(1).minusDays(1)),
        )
        val months = buildMonths(data, today)
        return CostSummary(
            total = total,
            days = days,
            perDay = if (days > 0) total / days else 0.0,
            thisMonth = data.costOfMonth(thisMonthKey),
            lastMonth = data.costOfMonth(lastMonthKey),
            thisYear = data.costOfYear(today.year),
            entryCount = data.costs.size,
            byCategory = data.costByCategory(),
            months = months,
            daysFromDelivery = deliveryDays != null,
        )
    }

    private fun buildMonths(data: JournalData, today: LocalDate): List<MonthPoint> {
        val current = YearMonth.from(today)
        return (MONTHS - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            val key = String.format(java.util.Locale.CHINA, "%04d-%02d", ym.year, ym.monthValue)
            MonthPoint(
                key = key,
                label = "${ym.monthValue}月",
                amount = data.costOfMonth(key),
            )
        }
    }

    /** 金额文案：`1,280` / `86.5`，超过一万折成「1.2 万」（卡片放不下五位数）。 */
    fun money(value: Double): String {
        val safe = if (value.isFinite() && value > 0) value else 0.0
        return when {
            safe >= 10_000.0 -> String.format(java.util.Locale.CHINA, "%.1f万", safe / 10_000.0)
            safe >= 100.0 -> String.format(java.util.Locale.CHINA, "%,d", Math.round(safe))
            else -> String.format(java.util.Locale.CHINA, "%.1f", safe).removeSuffix(".0")
        }
    }

    /** 日均这类小数字保留两位：一天 8.6 元比「8.6 元/天」更精确的需求不大，但差额明显。 */
    fun moneyFine(value: Double): String {
        val safe = if (value.isFinite() && value > 0) value else 0.0
        return String.format(java.util.Locale.CHINA, "%.2f", safe)
    }
}
