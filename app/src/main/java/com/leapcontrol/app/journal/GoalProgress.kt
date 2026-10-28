package com.leapcontrol.app.journal

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 用车目标的进度。
 *
 * [daysOwned] 是「已经陪伴多少天」——含提车当天（提车那天算第 1 天，
 * 否则提车当天进度是 0，第一天永远拿不到「初见」勋章）。
 */
data class GoalProgress(
    val daysOwned: Int,
    val targetDays: Int,
    val fraction: Float,
    /** 达成目标的日期（提车日 + 目标天数 - 1）；已达成时为 null。 */
    val etaDate: Long?,
    /** 落地价摊到「已经陪伴的每一天」，用车成本的另一面。 */
    val pricePerDay: Double,
) {
    val percent: Int get() = (fraction * 100f).roundToInt()

    val remainingDays: Int get() = (targetDays - daysOwned).coerceAtLeast(0)

    /** 进度条宽度：超过目标后封顶，不让进度条溢出容器。 */
    val barFraction: Float get() = fraction.coerceIn(0f, 1f)

    val reached: Boolean get() = daysOwned >= targetDays

    companion object {
        /**
         * 按提车日期算进度。
         *
         * 天数是「提车日 → 今天」的日历天数 + 1，所以提车当天进度是 1 天而不是 0 ——
         * 否则刚提车时「初见」勋章要等到第二天才亮。
         */
        fun of(goal: CarGoal, today: LocalDate = JournalDates.today()): GoalProgress {
            val delivery = JournalDates.toLocalDate(goal.deliveryDate)
            val days = (ChronoUnit.DAYS.between(delivery, today) + 1).toInt().coerceAtLeast(1)
            val target = goal.targetDays.coerceAtLeast(1)
            val eta = if (days < target) {
                JournalDates.startOfDay(delivery.plusDays((target - 1).toLong()))
            } else {
                null
            }
            val perDay = if (goal.landingPrice > 0.0) goal.landingPrice / days else 0.0
            return GoalProgress(
                daysOwned = days,
                targetDays = target,
                fraction = days.toFloat() / target.toFloat(),
                etaDate = eta,
                pricePerDay = perDay,
            )
        }

        /** 服务端天数 → 提车日期（手头没有提车日期时用它反推）。 */
        fun deliveryFromOwnershipDays(ownershipDays: Int, today: LocalDate = JournalDates.today()): Long =
            JournalDates.startOfDay(today.minusDays((ownershipDays - 1).toLong()))
    }
}
