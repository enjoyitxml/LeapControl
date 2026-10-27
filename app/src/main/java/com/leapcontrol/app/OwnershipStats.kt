package com.leapcontrol.app

import java.util.Locale

/**
 * 「与爱车相伴」卡片的口径换算。
 *
 * 两个数字都只从接口取，不在本地估算：
 *  - 天数：里程/能耗累计接口返回的提车天数（[MileageEnergySummary.ownershipDays]）；
 *  - 公里：同一份汇总里的总里程（[MileageEnergySummary.totalMileageKm]），
 *    取不到时退回车况报文里的 `totalMileage` 文本。
 *
 * 任何一个取不到就是 null，UI 显示「--」，不拿 0 或猜测值糊上去。
 */
object OwnershipStats {

    /** 40 年封顶：超过这个数一定是脏数据（时间戳被当成天数之类）。 */
    private const val MAX_DAYS = 40 * 365

    /** 200 万公里封顶，同理。 */
    private const val MAX_KM = 2_000_000.0

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
     * 卡片上显示的公里数：千分位、四舍五入到整数。
     * 卡片只有半屏宽，带小数会挤成两行，且总里程的小数位本来也没有参考价值。
     */
    fun formatKm(km: Double?): String? {
        val value = km?.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_KM } ?: return null
        return String.format(Locale.CHINA, "%,d", Math.round(value))
    }
}
