package com.leapcontrol.app.journal

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 勋章图案。全部用 Canvas 画（见 `ui/JournalCommon.kt`），不引图片资源。 */
enum class MedalGlyph {
    SPROUT,
    FLAG,
    ROAD,
    SUN,
    MOON,
    STAR,
    MOUNTAIN,
    HEART,
    CROWN,
}

/** 勋章的类型：进度 / 陪伴天数 / 行驶里程。 */
enum class MedalKind { PROGRESS, DAYS, DISTANCE }

/**
 * 一枚勋章。
 *
 * 解锁条件只有一条：对应口径的数值 ≥ [threshold]。
 * 进度按 0~1（或更大的百分比）比，天数按天比，里程按 km 比。
 */
data class Medal(
    val id: String,
    val kind: MedalKind,
    val threshold: Double,
    val title: String,
    val desc: String,
    val glyph: MedalGlyph,
) {
    fun unlocked(days: Int, fraction: Float, km: Double?): Boolean = when (kind) {
        MedalKind.PROGRESS -> fraction + 1e-4f >= threshold.toFloat()
        MedalKind.DAYS -> days >= threshold.roundToInt()
        MedalKind.DISTANCE -> km != null && km >= threshold
    }

    /** 还差多少（「差 12 天」「差 30%」「差 1,200 公里」）；已解锁为 null。 */
    fun gapText(days: Int, fraction: Float, km: Double?): String? = when (kind) {
        MedalKind.PROGRESS -> {
            val gap = (threshold - fraction) * 100f
            if (gap <= 0.5f) null else "差 ${gap.roundToInt()}% 进度"
        }

        MedalKind.DAYS -> {
            val gap = threshold.roundToInt() - days
            if (gap <= 0) null else "差 $gap 天"
        }

        MedalKind.DISTANCE -> {
            if (km == null) "还没有里程数据"
            else {
                val gap = (threshold - km).roundToInt()
                if (gap <= 0) null else "差 $gap 公里"
            }
        }
    }
}

/**
 * 勋章表。
 *
 * 顺序就是展示顺序：先「里程碑」（陪伴天数 / 里程），再「进度」——
 * 因为天数与里程是实打实走过的东西，进度条只是把目标切成段。
 */
object Medals {

    val ALL: List<Medal> = listOf(
        Medal("day_1", MedalKind.DAYS, 1.0, "初见", "提车的第一天", MedalGlyph.SPROUT),
        Medal("day_100", MedalKind.DAYS, 100.0, "百日相伴", "一百天，从陌生到熟悉", MedalGlyph.SUN),
        Medal("day_365", MedalKind.DAYS, 365.0, "一年之约", "完整走过四季", MedalGlyph.ROAD),
        Medal("day_1095", MedalKind.DAYS, 1095.0, "三年同路", "三年，车也是家人", MedalGlyph.MOON),
        Medal("day_1825", MedalKind.DAYS, 1825.0, "五年之约", "五年，值得纪念", MedalGlyph.STAR),
        Medal("day_3650", MedalKind.DAYS, 3650.0, "十年长情", "十年，长期主义的奖杯", MedalGlyph.CROWN),
        Medal("km_1000", MedalKind.DISTANCE, 1_000.0, "千公里", "第一段长路", MedalGlyph.FLAG),
        Medal("km_10000", MedalKind.DISTANCE, 10_000.0, "万里同行", "一万公里，足够跨越山海", MedalGlyph.MOUNTAIN),
        Medal("km_50000", MedalKind.DISTANCE, 50_000.0, "五万公里", "绕地球一圈还多", MedalGlyph.HEART),
        Medal("km_100000", MedalKind.DISTANCE, 100_000.0, "十万公里", "这辆车已经写进生活", MedalGlyph.CROWN),
        Medal("p_10", MedalKind.PROGRESS, 0.10, "出发", "走过目标的十分之一", MedalGlyph.SPROUT),
        Medal("p_25", MedalKind.PROGRESS, 0.25, "同行", "四分之一，已经有了默契", MedalGlyph.FLAG),
        Medal("p_50", MedalKind.PROGRESS, 0.50, "半程", "一半的路，一起走完了", MedalGlyph.MOUNTAIN),
        Medal("p_75", MedalKind.PROGRESS, 0.75, "笃行", "四分之三，越走越稳", MedalGlyph.HEART),
        Medal("p_100", MedalKind.PROGRESS, 1.00, "长情", "目标抵达，继续相伴", MedalGlyph.CROWN),
    )

    fun unlockedIds(days: Int, fraction: Float, km: Double?): Set<String> =
        ALL.filter { it.unlocked(days, fraction, km) }.map { it.id }.toSet()

    /** 下一枚还没拿到的勋章（按表顺序第一枚），全部拿到就是 null。 */
    fun next(days: Int, fraction: Float, km: Double?): Medal? =
        ALL.firstOrNull { !it.unlocked(days, fraction, km) }
}

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
