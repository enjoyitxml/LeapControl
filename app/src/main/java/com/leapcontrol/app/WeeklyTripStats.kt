package com.leapcontrol.app

import java.util.Locale

/**
 * 「近 7 天行程」卡的换算（纯函数，无 Android 依赖，可单测）。
 *
 * 数据来自 [MileageEnergySummary.recentDays]：服务端逐日明细，通常 7 条、按日期升序。
 * 这里只做两件事：
 *  - 合计求一个总数（[totalText]），
 *  - 把每日里程归一化成 0~1 的柱高（[series]）。
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
     * 每根柱子的相对高度（0~1），长度恒为 [bars]，最后一根是最近的一天。
     * 最大值那根顶满，其余按比例；全为 0 时返回全 0（UI 画灰色占位）。
     */
    fun series(recentDays: List<DailyMileage>, bars: Int = BARS): List<Float> {
        if (bars <= 0) return emptyList()
        val tail = recentDays
            .takeLast(bars)
            .filter { it.km.isFinite() && it.km in 0.0..MAX_DAY_KM }
            .map { it.km }
        val padded = List((bars - tail.size).coerceAtLeast(0)) { 0.0 } + tail.takeLast(bars)
        val max = padded.maxOrNull() ?: 0.0
        if (max <= 0.0) return List(bars) { 0f }
        return padded.map { (it / max).toFloat().coerceIn(0f, 1f) }
    }
}
