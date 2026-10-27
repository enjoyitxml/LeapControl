package com.leapcontrol.app

import java.util.Locale

/**
 * 「近 7 天行程」卡的换算（纯函数，无 Android 依赖，可单测）。
 *
 * 数据来自 [MileageEnergySummary.recentDays]：服务端逐日明细，通常 7 条、按日期升序。
 * 这里只做三件事：
 *  - 合计求一个总数（[totalText]），
 *  - 把每日里程整理成固定 7 个值（[values]，缺的天补 0），
 *  - 归一化成 0~1 的柱高（[series]）与柱顶标注文案（[dayText]）。
 *
 * 取不到数据时一律返回 null / 空序列，由 UI 显示「--」和灰色占位柱，不猜数字。
 */
object WeeklyTripStats {

    /** 柱状图固定 7 根；接口少给几天就在左侧补 0，保持「最后一根 = 今天」。 */
    const val BARS = 7

    /** 单日上限：超过这个数一定是脏数据（累计里程被当成单日之类）。 */
    private const val MAX_DAY_KM = 5_000.0

    /** 合计上限：7 天不可能超过这个数。 */
    private const val MAX_TOTAL_KM = 35_000.0

    /** 合计里程（km）；没有明细就是 null。 */
    fun totalKm(recentDays: List<DailyMileage>): Double? {
        val valid = recentDays.filter { it.km.isFinite() && it.km in 0.0..MAX_DAY_KM }
        if (valid.isEmpty()) return null
        return valid.fold(0.0) { acc, d -> acc + d.km }.takeIf { it <= MAX_TOTAL_KM }
    }

    /**
     * 卡片上的合计文案：一位小数，整数则省略小数位，四位数以上带千分位。
     * 例：`126` / `126.4` / `1,234`。
     */
    fun totalText(recentDays: List<DailyMileage>): String? {
        val total = totalKm(recentDays) ?: return null
        val rounded = Math.round(total * 10) / 10.0
        return if (rounded == Math.floor(rounded)) {
            String.format(Locale.CHINA, "%,d", rounded.toLong())
        } else {
            String.format(Locale.CHINA, "%,.1f", rounded)
        }
    }

    /**
     * 柱状图用的逐日数值：长度恒为 [bars]，最后一根是最近的一天，缺的天补 0。
     * 脏数据（负数 / NaN / 离谱大值）按 0 处理，不让一根异常柱把其它天的比例压扁。
     */
    fun values(recentDays: List<DailyMileage>, bars: Int = BARS): List<Double> {
        if (bars <= 0) return emptyList()
        val tail = recentDays
            .takeLast(bars)
            .map { it.km.takeIf { km -> km.isFinite() && km in 0.0..MAX_DAY_KM } ?: 0.0 }
        return List((bars - tail.size).coerceAtLeast(0)) { 0.0 } + tail.takeLast(bars)
    }

    /**
     * 每根柱子的相对高度（0~1），长度恒为 [bars]，最后一根是最近的一天。
     * 最大值那根顶满，其余按比例；全为 0 时返回全 0（UI 画灰色占位）。
     */
    fun series(recentDays: List<DailyMileage>, bars: Int = BARS): List<Float> {
        val list = values(recentDays, bars)
        val max = list.maxOrNull() ?: 0.0
        if (max <= 0.0) return List(bars) { 0f }
        return list.map { (it / max).toFloat().coerceIn(0f, 1f) }
    }

    /**
     * 柱顶标注：每天多少公里。
     * 整数不带小数（`48`），不足 10 的带一位小数（`6.5`），避免「0.4 公里」被显示成「0」。
     */
    fun dayText(km: Double): String {
        val safe = if (km.isFinite() && km >= 0.0) km else 0.0
        val rounded = Math.round(safe * 10) / 10.0
        return if (rounded == Math.floor(rounded) || rounded >= 10.0) {
            String.format(Locale.CHINA, "%,.0f", rounded)
        } else {
            String.format(Locale.CHINA, "%.1f", rounded)
        }
    }
}
