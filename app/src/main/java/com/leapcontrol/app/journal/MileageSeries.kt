package com.leapcontrol.app.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 里程趋势的粒度（日 / 周 / 月 / 年）。
 *
 * 枚举顺序即 UI 上切换按钮的顺序，[label] 直接当按钮文字用。
 */
enum class KmRange(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

/**
 * 折线图上的一个点。
 *
 * [key] 是聚合桶的唯一标识（日期串 / `w2026-10-26` / `y2026`），用于动画与去重；
 * [label] 是横轴文字；[km] 是该桶内的里程合计。
 */
data class KmPoint(val key: String, val label: String, val km: Double)

/**
 * 把「日期 → 当日里程」的全量记录聚合成折线图用的固定格数序列。
 *
 * ## 为什么固定格数
 * 折线图的横轴是等距的，格数必须固定（不能随数据多少伸缩），否则用户切一次粒度
 * 就得重新适应一版横轴。所以每个粒度都有一个固定的窗口长度：
 * 日 14 天 / 周 8 周 / 月 12 个月 / 年至少 3 年。
 *
 * ## 缺数据怎么显示
 * 某天没有记录（车没开 / App 那几天没打开）按 0 计入。折线图上不区分「真的 0」
 * 与「没记录」，这点在卡片说明里讲清楚 —— 宁可少画一条虚线造成误解，也不要
 * 在折线里塞两种语义。
 *
 * [daily] 里的键宽容解析（`2026-10-28` / `2026/10/28` / `20261028` 都认），
 * 因为服务端两种格式都出现过；解析不出来的键直接丢弃，不让一个脏日期毁掉整张图。
 */
object MileageSeries {

    /** 日视图窗口：14 天，两周足够看出工作日 / 周末的节奏。 */
    const val DAYS = 14

    /** 周视图窗口：8 周。 */
    const val WEEKS = 8

    /** 月视图窗口：12 个月。 */
    const val MONTHS = 12

    /** 年视图最少画几年：新车只有今年有数据，只画一个点看不出「趋势」。 */
    const val MIN_YEARS = 3

    /** 年视图上限：再长的跨度画在手机屏上就没法读了。 */
    private const val MAX_YEARS = 30

    fun of(
        daily: Map<String, Double>,
        range: KmRange,
        today: LocalDate = JournalDates.today(),
    ): List<KmPoint> {
        val parsed = daily.mapNotNull { (key, km) ->
            val date = JournalDates.parseDayKey(key) ?: return@mapNotNull null
            if (!km.isFinite() || km < 0.0) return@mapNotNull null
            date to km
        }
        return when (range) {
            KmRange.DAY -> (DAYS - 1 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                KmPoint(
                    key = JournalDates.dayKey(date),
                    label = CostStats.dayLabel(date, today),
                    km = sumBetween(parsed, date, date.plusDays(1)),
                )
            }

            KmRange.WEEK -> {
                val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (WEEKS - 1 downTo 0).map { back ->
                    val start = thisWeek.minusWeeks(back.toLong())
                    KmPoint(
                        key = "w" + JournalDates.dayKey(start),
                        label = when (back) {
                            0 -> "本周"
                            1 -> "上周"
                            else -> "${start.monthValue}/${start.dayOfMonth}"
                        },
                        km = sumBetween(parsed, start, start.plusWeeks(1)),
                    )
                }
            }

            KmRange.MONTH -> {
                val thisMonth = today.withDayOfMonth(1)
                (MONTHS - 1 downTo 0).map { back ->
                    val start = thisMonth.minusMonths(back.toLong())
                    KmPoint(
                        key = JournalDates.yearMonthKey(JournalDates.startOfDay(start)),
                        label = "${start.monthValue}月",
                        km = sumBetween(parsed, start, start.plusMonths(1)),
                    )
                }
            }

            KmRange.YEAR -> {
                val earliest = parsed.minOfOrNull { it.first.year } ?: today.year
                val span = (today.year - earliest + 1)
                    .coerceAtLeast(MIN_YEARS)
                    .coerceAtMost(MAX_YEARS)
                (span - 1 downTo 0).map { back ->
                    val year = today.year - back
                    val start = LocalDate.of(year, 1, 1)
                    KmPoint(
                        key = "y$year",
                        label = "$year",
                        km = sumBetween(parsed, start, start.plusYears(1)),
                    )
                }
            }
        }
    }

    /** 窗口内合计。 */
    fun total(points: List<KmPoint>): Double = points.fold(0.0) { acc, p -> acc + p.km }

    /** 窗口内平均（按格数除，含空档）。 */
    fun average(points: List<KmPoint>): Double =
        if (points.isEmpty()) 0.0 else total(points) / points.size

    /** 窗口内最高的那一格；全是 0 时返回 null（调用方据此显示「—」）。 */
    fun peak(points: List<KmPoint>): KmPoint? = points.filter { it.km > 0.0 }.maxByOrNull { it.km }

    fun hasData(points: List<KmPoint>): Boolean = points.any { it.km > 0.0 }

    private fun sumBetween(
        points: List<Pair<LocalDate, Double>>,
        fromInclusive: LocalDate,
        toExclusive: LocalDate,
    ): Double {
        var sum = 0.0
        points.forEach { (date, km) ->
            if (!date.isBefore(fromInclusive) && date.isBefore(toExclusive)) sum += km
        }
        return sum
    }
}
