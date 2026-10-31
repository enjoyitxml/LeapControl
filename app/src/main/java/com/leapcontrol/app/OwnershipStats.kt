package com.leapcontrol.app

import java.util.Locale

/**
 * 「与爱车相伴」卡片的口径换算。
 *
 * 三个数字都只从接口取，不在本地估算：
 *  - 天数：里程/能耗累计接口返回的提车天数（[MileageEnergySummary.ownershipDays]）；
 *  - 公里：同一份汇总里的总里程（[MileageEnergySummary.totalMileageKm]），
 *    取不到时退回车况报文里的 `totalMileage` 文本；
 *  - 累计用电 / 综合能耗：同一份汇总里的累计 kWh 与 kWh/100km（[energyLine]）。
 *
 * 任何一个取不到就是 null，UI 显示「--」（或整行不出现），不拿 0 或猜测值糊上去。
 */
object OwnershipStats {

    /** 40 年封顶：超过这个数一定是脏数据（时间戳被当成天数之类）。 */
    private const val MAX_DAYS = 40 * 365

    /** 200 万公里封顶，同理。 */
    private const val MAX_KM = 2_000_000.0

    /**
     * 累计用电上限：100 万度。
     * 比这个还大基本就是把里程、金额之类的字段认成了电量，宁可显示「--」也别报个离谱数字。
     */
    private const val MAX_KWH = 1_000_000.0

    /** 综合能耗的合理区间（kWh/100km）：低于 2 或高于 40 的乘用车都不存在。 */
    private val EC_RANGE = 2.0..40.0

    private val FIRST_NUMBER = Regex("""\d+(?:\.\d+)?""")

    /** 提车天数：负数/0 不认（0 天多半是缺省值），超上限也不认。 */
    fun days(ownershipDays: Int?): Int? = ownershipDays?.takeIf { it in 1..MAX_DAYS }

    /**
     * 从车况里的里程文本抠出数字。
     * 实测同一字段在不同车型上会带千分位或单位，如 `12345`、`12,345`、`12,345 km`、`12345.6公里`，
     * 所以先去掉分隔符，再取第一个数字。
     */
    fun kmFromText(raw: String?): Double? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cleaned = text
            .replace(",", "")
            .replace("，", "")
            .replace(" ", "")
            .replace("\u00A0", "")
        val matched = FIRST_NUMBER.find(cleaned)?.value?.toDoubleOrNull() ?: return null
        return matched.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_KM }
    }

    /**
     * 「当前总里程」的唯一口径：**车况报文里的表显里程优先**，取不到才退回里程接口的累计值。
     *
     * 优先表显不是拍脑袋：里程接口（`drivingrecord/mileage/energy/detail`）的累计口径
     * 由服务端按行驶记录汇总，实测滞后一天以上，还有 15 分钟本地缓存；而车况报文里的
     * `totalMileage` 就是仪表上那个数，随每次车况刷新同步变化。剩余保养里程要是拿滞后的
     * 那个算，用户开一个月都不会少一公里 —— 这就是「剩余保养里程不更新」的直接体感来源。
     *
     * 两个都取不到就是 null（UI 显示「--」），不拿 0 兜底。
     */
    fun currentKm(statusText: String?, energyKm: Double?): Double? =
        kmFromText(statusText)?.takeIf { it > 0.0 } ?: energyKm?.takeIf { it > 0.0 }

    /**
     * 卡片上显示的公里数：千分位、四舍五入到整数。
     * 卡片只有半屏宽，带小数会挤成两行，且总里程的小数位本来也没有参考价值。
     */
    fun formatKm(km: Double?): String? {
        val value = km?.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_KM } ?: return null
        return String.format(Locale.CHINA, "%,d", Math.round(value))
    }

    /**
     * 累计用电（kWh）：千分位 + 取整。
     *
     * 和公里同一套写法：这个数字是「提车至今用了多少电」，小数位没有任何参考价值，
     * 带小数反而让一行小字变长。0 与负数一律当缺失 —— 那多半是字段没返回而不是真的没用过电。
     */
    fun formatKwh(kwh: Double?): String? {
        val value = kwh?.takeIf { it.isFinite() && it > 0.0 && it <= MAX_KWH } ?: return null
        return String.format(Locale.CHINA, "%,d", Math.round(value))
    }

    /**
     * 综合能耗（kWh/100km）：一位小数。
     *
     * 只保留 [EC_RANGE] 内的值：服务端偶尔会把「剩余电量百分比」之类的字段认成能耗，
     * 直接显示会出现「百公里 87 kWh」这种一眼错的数。
     */
    fun formatConsumption(ec: Double?): String? {
        val value = ec?.takeIf { it.isFinite() && it in EC_RANGE } ?: return null
        return String.format(Locale.CHINA, "%.1f", value)
    }

    /**
     * 相伴卡第三行的文案：累计用电 + 综合能耗。
     *
     * 两个数各自可能缺失，所以按「有几个写几个」拼，**都没有就返回 null**
     * （整行不出现，而不是留一行「累计用电 --」破坏这张风景卡的版面）。
     */
    fun energyLine(cumulativeKwh: Double?, overallConsumption: Double?): String? {
        val kwh = formatKwh(cumulativeKwh)
        val ec = formatConsumption(overallConsumption)
        return when {
            kwh != null && ec != null -> "累计用电 $kwh kWh · 百公里 $ec kWh"
            kwh != null -> "累计用电 $kwh kWh"
            ec != null -> "百公里平均 $ec kWh"
            else -> null
        }
    }
}
