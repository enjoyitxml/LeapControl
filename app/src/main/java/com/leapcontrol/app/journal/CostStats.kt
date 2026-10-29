package com.leapcontrol.app.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 月度支出柱状图的一根柱子。 */
data class MonthPoint(val key: String, val label: String, val amount: Double)

/** 趋势图上的一根柱子：日 / 周 / 月 / 年都归一成这个结构。 */
data class TrendPoint(val key: String, val label: String, val amount: Double)

/**
 * 统计区间：成本页顶部那排药丸。
 *
 * 「日」「周」是后来补的 —— 记账是当天做的事，看完「今天花了 260」
 * 比看「本月花了 1,800」更有感觉。[trendTitle] 是趋势图跟着换的标题。
 */
enum class CostRange(val label: String, val desc: String, val trendTitle: String) {
    DAY("今日", "今天的支出", "近 7 天"),
    WEEK("本周", "本周支出", "近 8 周"),
    MONTH("本月", "本月支出", "近 6 个月"),
    YEAR("今年", "今年支出", "近 5 年"),
    ALL("全部", "提车以来", "近 6 年"),
}

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
    val today: Double,
    val yesterday: Double,
    val thisWeek: Double,
    val lastWeek: Double,
    val lastYear: Double,
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

    /** 某个区间花掉的钱。 */
    fun amountOf(range: CostRange): Double = when (range) {
        CostRange.DAY -> today
        CostRange.WEEK -> thisWeek
        CostRange.MONTH -> thisMonth
        CostRange.YEAR -> thisYear
        CostRange.ALL -> total
    }

    /** 与「上一期」的差额；两期都没有记录时返回 null（画「--」而不是假装持平）。 */
    fun deltaOf(range: CostRange): Double? = when (range) {
        CostRange.DAY -> if (today <= 0.0 && yesterday <= 0.0) null else today - yesterday
        CostRange.WEEK -> if (thisWeek <= 0.0 && lastWeek <= 0.0) null else thisWeek - lastWeek
        CostRange.MONTH -> monthDelta
        CostRange.YEAR -> if (thisYear <= 0.0 && lastYear <= 0.0) null else thisYear - lastYear
        CostRange.ALL -> null
    }

    fun deltaLabel(range: CostRange): String = when (range) {
        CostRange.DAY -> "较昨日"
        CostRange.WEEK -> "较上周"
        CostRange.MONTH -> "较上月"
        CostRange.YEAR -> "较去年"
        CostRange.ALL -> "较上期"
    }
}

object CostStats {

    /** 月度柱状图显示几个月（含当月）。 */
    const val MONTHS = 6

    private const val DAY_MS = 86_400_000L

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

        val todayStart = JournalDates.startOfDay(today)
        val weekStart = startOfWeek(today)
        val months = buildMonths(data, today)
        return CostSummary(
            total = total,
            days = days,
            perDay = if (days > 0) total / days else 0.0,
            thisMonth = data.costOfMonth(thisMonthKey),
            lastMonth = data.costOfMonth(lastMonthKey),
            thisYear = data.costOfYear(today.year),
            today = data.costBetween(todayStart, todayStart + DAY_MS),
            yesterday = data.costBetween(todayStart - DAY_MS, todayStart),
            thisWeek = data.costBetween(weekStart, weekStart + 7 * DAY_MS),
            lastWeek = data.costBetween(weekStart - 7 * DAY_MS, weekStart),
            lastYear = data.costOfYear(today.year - 1),
            entryCount = data.costs.size,
            byCategory = data.costByCategory(),
            months = months,
            daysFromDelivery = deliveryDays != null,
        )
    }

    /** 本周一 0 点。Compose 侧不需要知道「周从哪天开始」，统一按周一切。 */
    private fun startOfWeek(today: LocalDate): Long =
        JournalDates.startOfDay(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

    /**
     * 区间边界 `[from, to)`：日 / 周 / 月 / 年 / 全部。
     *
     * 全部用 Long 的极值兜住 —— `costBetween` 是半开区间，极值两端都不会漏。
     */
    fun boundsOf(range: CostRange, today: LocalDate = JournalDates.today()): Pair<Long, Long> {
        val todayStart = JournalDates.startOfDay(today)
        return when (range) {
            CostRange.DAY -> todayStart to todayStart + DAY_MS
            CostRange.WEEK -> {
                val start = startOfWeek(today)
                start to start + 7 * DAY_MS
            }
            CostRange.MONTH -> {
                val first = today.withDayOfMonth(1)
                JournalDates.startOfDay(first) to JournalDates.startOfDay(first.plusMonths(1))
            }
            CostRange.YEAR -> {
                val first = today.withDayOfYear(1)
                JournalDates.startOfDay(first) to JournalDates.startOfDay(first.plusYears(1))
            }
            CostRange.ALL -> Long.MIN_VALUE to Long.MAX_VALUE
        }
    }

    /** 某个区间内的分类汇总（金额降序，0 元的不出现）。 */
    fun byCategoryOf(
        data: JournalData,
        range: CostRange,
        today: LocalDate = JournalDates.today(),
    ): List<Pair<CostCategory, Double>> {
        val (from, to) = boundsOf(range, today)
        val scoped = data.costs.filter { it.date in from until to }
        if (scoped.isEmpty()) return emptyList()
        return CostCategory.entries
            .map { cat -> cat to scoped.filter { it.category == cat }.fold(0.0) { a, e -> a + e.amount } }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
    }

    private fun buildMonths(data: JournalData, today: LocalDate): List<MonthPoint> {
        val current = YearMonth.from(today)
        return (MONTHS - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            val key = String.format(Locale.CHINA, "%04d-%02d", ym.year, ym.monthValue)
            MonthPoint(
                key = key,
                label = "${ym.monthValue}月",
                amount = data.costOfMonth(key),
            )
        }
    }

    /**
     * 趋势数据：跟着 [range] 换粒度。
     *
     * 日 → 近 7 天按天；周 → 近 8 周按周；月 → 近 6 个月；年/全部 → 近 5/6 年。
     * 都是「从旧到新」，最后一根是当期，画图时用主题色高亮。
     */
    fun trendOf(
        data: JournalData,
        range: CostRange,
        today: LocalDate = JournalDates.today(),
    ): List<TrendPoint> = when (range) {
        CostRange.DAY -> (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val start = JournalDates.startOfDay(day)
            TrendPoint(JournalDates.dayKey(day), dayLabel(day, today), data.costBetween(start, start + DAY_MS))
        }

        CostRange.WEEK -> {
            val thisWeek = startOfWeek(today)
            (7 downTo 0).map { back ->
                val start = thisWeek - back * 7 * DAY_MS
                val label = when (back) {
                    0 -> "本周"
                    1 -> "上周"
                    else -> JournalDates.shortLabel(start).replace("月", "/").replace("日", "")
                }
                TrendPoint("w$start", label, data.costBetween(start, start + 7 * DAY_MS))
            }
        }

        CostRange.MONTH -> buildMonths(data, today).map { TrendPoint(it.key, it.label, it.amount) }

        CostRange.YEAR, CostRange.ALL -> {
            val span = if (range == CostRange.YEAR) 5 else MONTHS
            (span - 1 downTo 0).map { back ->
                val year = today.year - back
                TrendPoint(
                    key = "$year",
                    label = if (back == 0) "今年" else "$year",
                    amount = data.costOfYear(year),
                )
            }
        }
    }

    /** 每天柱状图的横轴标签：今天 / 昨天 / 10/26。 */
    fun dayLabel(date: LocalDate, today: LocalDate = JournalDates.today()): String = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }

    /**
     * 金额文案：最多两位小数，整数不拖 `.00`。
     *
     * 记账讲究分位，四舍五入到整数会让 86.53 显示成 87，多笔累加后对不上账；
     * 但整数金额拖两个零（¥50.00）在列表里又太吵，所以只在真有小数时才显示两位。
     * 过万仍折成「万」，同样保留两位小数（1.23万）。
     */
    fun money(value: Double): String {
        val safe = if (value.isFinite() && value > 0) value else 0.0
        if (safe >= 10_000.0) {
            val wan = String.format(Locale.CHINA, "%.2f", safe / 10_000.0)
                .trimEnd('0')
                .trimEnd('.')
            return "${wan}万"
        }
        val text = String.format(Locale.CHINA, "%,.2f", safe)
        return if (text.endsWith(".00")) text.dropLast(3) else text
    }

    /** 日均这类小数字保留两位：一天 8.6 元比「8.6 元/天」更精确的需求不大，但差额明显。 */
    fun moneyFine(value: Double): String {
        val safe = if (value.isFinite() && value > 0) value else 0.0
        return String.format(Locale.CHINA, "%.2f", safe)
    }
}
