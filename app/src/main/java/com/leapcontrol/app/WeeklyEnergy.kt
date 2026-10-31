package com.leapcontrol.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * 近 N 周百公里能耗的一个点。
 *
 * [weekStart] / [weekEnd] 是服务端原样返回的 ISO 日期（`2026-10-19`），
 * 展示前统一走 [EnergyFormat.weekTick] 取这一周的右端点（周日）转成 `10/25`；
 * 折线图横轴最右一根改用 [EnergyFormat.weekAxisLabel]，会写成「上周」。
 */
data class WeeklyEnergyPoint(
    val weekStart: String,
    val weekEnd: String?,
    val hundredKmEc: Double,
)

/**
 * 「近 6 周百公里能耗」卡的数据：近几周的百公里电耗 + 综合电耗 + 同车型全国排行。
 *
 * [rankPercent] 保存**归一化后**的百分比文本（`91%`），原始报文可能是 `91`、`91%`、
 * `0.91` 这类写法，统一在解析层收敛，UI 只管拼「超越全国 91% 的C11车主」。
 */
data class WeeklyEnergyRank(
    val averageEc: Double? = null,
    val rankPercent: String? = null,
    val weeks: List<WeeklyEnergyPoint> = emptyList(),
) {
    val hasAny: Boolean get() = averageEc != null || weeks.isNotEmpty()
}

/** 上周能耗构成里的一项（行车 / 空调 / 其他），单位 kWh。 */
data class EnergySlice(val label: String, val kwh: Double)

/**
 * 「周能耗分布」卡的数据。
 *
 * 三项由接口直接给出（`driverEC` / `acEC` / `otherEC`），不自造拆分；
 * 总量是三项之和 —— 官方 App 也是这么算的，没有独立的「总能耗」字段。
 */
data class EnergyComposition(val slices: List<EnergySlice>) {
    val totalKwh: Double get() = slices.sumOf { if (it.kwh > 0.0) it.kwh else 0.0 }
    val hasAny: Boolean get() = totalKwh > 0.0
}

/** 两张能耗卡合并后的一份结果（两个接口各管一半，谁失败谁的字段留空）。 */
data class WeeklyEnergyData(
    val rank: WeeklyEnergyRank? = null,
    val composition: EnergyComposition? = null,
    val capturedAt: Long = 0L,
) {
    val hasAny: Boolean get() = rank?.hasAny == true || composition?.hasAny == true

    fun merge(other: WeeklyEnergyData): WeeklyEnergyData = WeeklyEnergyData(
        rank = other.rank ?: rank,
        composition = other.composition ?: composition,
        capturedAt = maxOf(capturedAt, other.capturedAt),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("capturedAt", capturedAt)
        rank?.let { r ->
            put(
                "rank",
                JSONObject().apply {
                    r.averageEc?.let { put("averageEc", it) }
                    r.rankPercent?.let { put("rankPercent", it) }
                    put(
                        "weeks",
                        JSONArray().apply {
                            r.weeks.forEach { w ->
                                put(
                                    JSONObject()
                                        .put("weekStart", w.weekStart)
                                        .put("weekEnd", w.weekEnd ?: "")
                                        .put("ec", w.hundredKmEc),
                                )
                            }
                        },
                    )
                },
            )
        }
        composition?.let { c ->
            put(
                "composition",
                JSONArray().apply {
                    c.slices.forEach { s ->
                        put(JSONObject().put("label", s.label).put("kwh", s.kwh))
                    }
                },
            )
        }
    }

    companion object {
        fun fromJson(raw: JSONObject): WeeklyEnergyData? {
            val rankObj = raw.optJSONObject("rank")
            val rank = rankObj?.let { r ->
                WeeklyEnergyRank(
                    averageEc = r.opt("averageEc")?.toString()?.toDoubleOrNull(),
                    rankPercent = r.optString("rankPercent").takeIf { it.isNotBlank() },
                    weeks = r.optJSONArray("weeks")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val o = arr.optJSONObject(i) ?: return@mapNotNull null
                            WeeklyEnergyPoint(
                                weekStart = o.optString("weekStart"),
                                weekEnd = o.optString("weekEnd").takeIf { it.isNotBlank() },
                                hundredKmEc = o.opt("ec")?.toString()?.toDoubleOrNull()
                                    ?: return@mapNotNull null,
                            )
                        }
                    }.orEmpty(),
                )
            }
            val composition = raw.optJSONArray("composition")?.let { arr ->
                EnergyComposition(
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        EnergySlice(
                            label = o.optString("label").takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null,
                            kwh = o.opt("kwh")?.toString()?.toDoubleOrNull()
                                ?: return@mapNotNull null,
                        )
                    },
                )
            }
            if (rank == null && composition == null) return null
            return WeeklyEnergyData(
                rank = rank,
                composition = composition,
                capturedAt = raw.optLong("capturedAt", 0L),
            )
        }
    }
}

/** 解析失败（业务码非 0/200、缺 data、字段全空）时抛出，调用方 runCatching 静默忽略。 */
class WeeklyEnergyException(message: String) : Exception(message)

/**
 * 两个能耗接口的解析器。
 *
 * 契约来自官方抓包：
 *  - 近 6 周百公里能耗与排行：`data.rankResult.{hundredKmEC, rank}` + `data.weeklyEC[{weekStart, weekEnd, hundredKmEC}]`
 *  - 上周能耗构成：`data.{driverEC, acEC, otherEC}`
 *
 * 字段缺失一律当「没有」，不用 0 冒充 —— 0 是我们的显示语义（真的是 0 kWh），
 * 两者在图上长得不一样（有值画柱 / 无值写「--」）。
 */
object WeeklyEnergyParser {

    /** 百分数归一化：`91` / `91%` / `0.91` → `91%`；解析不出来返回 null。 */
    fun normalizeRank(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val number = Regex("\\d+(?:\\.\\d+)?").find(text)?.value?.toDoubleOrNull() ?: return null
        // 0.91 这种「比例」写法换算成百分数；91 这种已经是百分数，保持原样
        val percent = if (number > 0.0 && number <= 1.0) number * 100.0 else number
        return if (percent % 1.0 == 0.0) {
            "${percent.toInt()}%"
        } else {
            String.format(Locale.US, "%.1f%%", percent)
        }
    }

    fun parseRank(response: JSONObject): WeeklyEnergyRank {
        checkBusinessResult(response)
        val data = response.optJSONObject("data")
            ?: throw WeeklyEnergyException("近 6 周能耗响应缺少 data")
        val rankResult = data.optJSONObject("rankResult")
        val average = rankResult?.opt("hundredKmEC")?.toString()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
        val percent = normalizeRank(rankResult?.optString("rank"))
        val weeks = data.optJSONArray("weeklyEC")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val start = item.optString("weekStart").trim().takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val value = item.opt("hundredKmEC")?.toString()?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?: return@mapNotNull null
                WeeklyEnergyPoint(
                    weekStart = start,
                    weekEnd = item.optString("weekEnd").trim().takeIf { it.isNotEmpty() },
                    hundredKmEc = value,
                )
            }
        }.orEmpty()
        if (average == null && percent == null && weeks.isEmpty()) {
            throw WeeklyEnergyException("近 6 周能耗响应没有可用字段")
        }
        return WeeklyEnergyRank(averageEc = average, rankPercent = percent, weeks = weeks)
    }

    fun parseComposition(response: JSONObject): EnergyComposition {
        checkBusinessResult(response)
        val data = response.optJSONObject("data")
            ?: throw WeeklyEnergyException("上周能耗构成响应缺少 data")
        val slices = COMPOSITION_FIELDS.mapNotNull { (key, label) ->
            val value = data.opt(key)?.toString()?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?: return@mapNotNull null
            EnergySlice(label, value)
        }
        if (slices.isEmpty()) throw WeeklyEnergyException("上周能耗构成响应没有可用字段")
        return EnergyComposition(slices)
    }

    /** 字段名 → 展示名。顺序即卡片里的行顺序（行车在最上面，和官方 App 一致）。 */
    private val COMPOSITION_FIELDS = listOf(
        "driverEC" to "行车",
        "acEC" to "空调",
        "otherEC" to "其他",
    )

    private fun checkBusinessResult(response: JSONObject) {
        val result = response.opt("result")
        val code = response.opt("code")
        val failed = (result != null && result != JSONObject.NULL &&
            result.toString() !in setOf("0", "200")) ||
            (code != null && code != JSONObject.NULL &&
                code.toString() !in setOf("0", "200"))
        if (failed) throw WeeklyEnergyException("能耗接口返回失败")
    }
}

/** 两张能耗卡的文本格式化（纯函数、有单测）。 */
object EnergyFormat {

    /** 能耗数值：固定一位小数，`9.8` / `0.1` / `10.7`；空值 `--`。 */
    fun kwh(value: Double?): String {
        if (value == null || !value.isFinite() || value < 0.0) return "--"
        return String.format(Locale.US, "%.1f", value)
    }

    /**
     * 占比百分数（整数），保证三项加起来正好 100。
     *
     * 直接各自四舍五入会出现 92 + 1 + 7 = 100 之外的情况（例如 91.5/0.5/8 → 92+1+8=101），
     * 这里用最大余数法把差额补给小数部分最大的那项，避免出现「加起来不是 100」的尴尬。
     */
    fun percentInts(values: List<Double>): List<Int> {
        val clean = values.map { if (it.isFinite() && it > 0.0) it else 0.0 }
        val total = clean.sum()
        if (total <= 0.0) return clean.map { 0 }
        val exact = clean.map { it / total * 100.0 }
        val floors = exact.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var remain = 100 - floors.sum()
        if (remain > 0) {
            val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
            var i = 0
            while (remain > 0 && order.isNotEmpty()) {
                floors[order[i % order.size]] += 1
                remain -= 1
                i += 1
            }
        } else if (remain < 0) {
            val order = exact.indices.sortedBy { exact[it] - floors[it] }
            var i = 0
            while (remain < 0 && order.isNotEmpty()) {
                val idx = order[i % order.size]
                if (floors[idx] > 0) {
                    floors[idx] -= 1
                    remain += 1
                }
                i += 1
                if (i > order.size * 4) break
            }
        }
        return floors
    }

    /** `2026-10-19` / `2026/10/19` / `10-19` → `10/19`；解析不了原样返回。 */
    fun weekLabel(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return "--"
        val match = Regex("^(?:\\d{4}[-/])?(\\d{1,2})[-/](\\d{1,2})").find(text)
            ?: return text
        val month = match.groupValues[1].toIntOrNull() ?: return text
        val day = match.groupValues[2].toIntOrNull() ?: return text
        return "$month/$day"
    }

    /**
     * 柱下的周标签：**这一周的最后一天**（周日），不是周一。
     *
     * 服务端的 `weeklyEC` 只下发「完整的自然周」，最后一项是上一个完整周（周一 → 周日）。
     * 早先用 `weekStart` 当标签，于是 10/30 打开首页，最后一根柱下面写着 `10/19`，
     * 看上去像「半个月没更新」—— 其实那一周是 10/19 ~ 10/25，数据本来就到 10/25 为止。
     * 取右端点之后，最右的标签就是「数据统计到哪一天」。
     *
     * 服务端的 `weekEnd` 是闭区间还是右开区间不确定（有的接口给 10-25、有的给 10-26），
     * 所以**优先用 weekStart + 6 天**算右端点，只有 weekStart 解析不出来时才退回 weekEnd。
     */
    fun weekTick(weekStart: String?, weekEnd: String?): String {
        val last = weekEndDate(weekStart, weekEnd)
        if (last != null) return "${last.monthValue}/${last.dayOfMonth}"
        return weekLabel(weekStart ?: weekEnd)
    }

    /** 横轴上「最近一周」的标签文案。 */
    const val LAST_WEEK_LABEL = "上周"

    /**
     * 折线图横轴标签：普通周写 `10/19`，**最近一周写「上周」**。
     *
     * 用户原话：最近一周不要显示具体日期，改成「上周」。理由很直接 —— 那一根柱子
     * 表示的本来就是「上周」这回事，`10/25` 只是它的右端点，写在轴上反而要多想一步。
     *
     * 但只在它**确实是上周**时才这么写：接口偶尔晚一天出数，最后一根可能还停在
     * 上上周，那时标「上周」就是错的（用户会以为数据没更新），这种情况退回日期，
     * 让它自己暴露「数据只到 10/18」这件事。
     */
    fun weekAxisLabel(
        weekStart: String?,
        weekEnd: String?,
        isLast: Boolean,
        now: LocalDate = LocalDate.now(),
    ): String {
        val fallback = weekTick(weekStart, weekEnd)
        if (!isLast) return fallback
        val last = weekEndDate(weekStart, weekEnd) ?: return fallback
        val lastSunday = now.with(DayOfWeek.MONDAY).minusWeeks(1).plusDays(6)
        return if (last == lastSunday) LAST_WEEK_LABEL else fallback
    }

    /** 这一周的右端点：`weekStart + 6` 优先，解析不出来才退回 `weekEnd`。 */
    private fun weekEndDate(weekStart: String?, weekEnd: String?): LocalDate? =
        parseWeekDate(weekStart)?.plusDays(6) ?: parseWeekDate(weekEnd)

    /**
     * 「上周」的日期区间（周一 → 周日），形如 `10.19 – 10.25`。
     *
     * 服务端的周能耗构成只下发三项度数、不带日期，可卡片上不写清楚是哪一周，
     * 用户就分不清「上周」是刚到的那一周还是上上周。日期按设备本地日期倒推：
     * 上周一 = 本周一 − 7 天（ISO 周，周一为首日）。
     */
    fun lastWeekRange(now: LocalDate = LocalDate.now()): String {
        val monday = now.with(DayOfWeek.MONDAY).minusWeeks(1)
        val sunday = monday.plusDays(6)
        return "${monday.monthValue}.${monday.dayOfMonth} – " +
            "${sunday.monthValue}.${sunday.dayOfMonth}"
    }

    /** `2026-10-19` / `2026/10/19` / `10-19`（补当前年份）→ [LocalDate]；解析不了返回 null。 */
    private fun parseWeekDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val full = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").find(text)
        if (full != null) {
            return runCatching {
                LocalDate.of(
                    full.groupValues[1].toInt(),
                    full.groupValues[2].toInt(),
                    full.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
        val short = Regex("^(\\d{1,2})[-/](\\d{1,2})$").find(text) ?: return null
        return runCatching {
            LocalDate.of(LocalDate.now().year, short.groupValues[1].toInt(), short.groupValues[2].toInt())
        }.getOrNull()
    }

    /**
     * 车型名归一化：「零跑C11」→「C11」，空则退回「零跑」。
     *
     * ### 为什么还要一张别名表
     * 车辆列表接口下发的 `carType` 是**内部项目代号**，不一定是宣传名：
     * Lafa5 下发的是 `B05`（预研代号），直接显示会出现「超越全国 50% 的 B05 车主」
     * 这种车主自己都不认识的车型。这里把已知代号折回宣传名，其余原样显示。
     */
    fun carModelName(raw: String?): String {
        val text = raw?.trim().orEmpty()
            .removePrefix("零跑")
            .trim()
        if (text.isEmpty()) return "零跑"
        return MODEL_ALIASES[text.uppercase()] ?: text
    }

    /** 内部代号 → 宣传名。新增车型时在这里补一行即可（键用大写比较）。 */
    private val MODEL_ALIASES = mapOf(
        "B05" to "Lafa5",   // 零跑 Lafa5：车辆接口下发的是项目代号 B05
        "LAFA5" to "Lafa5", // 已经是宣传名时统一成官方写法
    )
}
