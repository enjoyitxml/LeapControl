package com.leapcontrol.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** 近 7 日里程明细中的一天。 */
data class DailyMileage(
    val day: String,
    val km: Double,
)

/**
 * 行驶里程 / 能耗汇总。
 *
 * 数据来源是两个老服务接口（drivingrecord/mileage/energy/detail）：
 *  - 累计口径：提车至今，给总里程、综合能耗、累计能耗、提车天数；
 *  - 近 7 日口径：给每天的行驶里程。
 * 两个接口的字段命名都没有公开文档，所以解析一律走「别名匹配 + 取不到就是 null」，
 * 不在 UI 上凭空造数字。
 */
data class MileageEnergySummary(
    /** 车辆总里程（km）。 */
    val totalMileageKm: Double? = null,
    /** 综合能耗（kWh/100km）。 */
    val overallConsumption: Double? = null,
    /** 累计能耗（kWh）。 */
    val cumulativeEnergyKwh: Double? = null,
    /** 提车天数。 */
    val ownershipDays: Int? = null,
    /** 近 7 日每日里程（按服务端顺序，通常升序）。 */
    val recentDays: List<DailyMileage> = emptyList(),
    val capturedAt: Long = 0L,
) {
    /** 近 7 日合计里程（km）。 */
    val recent7DaysKm: Double?
        get() = if (recentDays.isEmpty()) null else recentDays.fold(0.0) { acc, d -> acc + d.km }

    val hasAny: Boolean
        get() = totalMileageKm != null || overallConsumption != null ||
            cumulativeEnergyKwh != null || ownershipDays != null || recentDays.isNotEmpty()

    /** 用另一份结果补齐本对象缺失的字段（累计口径与近 7 日口径合并展示）。 */
    fun merge(other: MileageEnergySummary): MileageEnergySummary = MileageEnergySummary(
        totalMileageKm = totalMileageKm ?: other.totalMileageKm,
        overallConsumption = overallConsumption ?: other.overallConsumption,
        cumulativeEnergyKwh = cumulativeEnergyKwh ?: other.cumulativeEnergyKwh,
        ownershipDays = ownershipDays ?: other.ownershipDays,
        recentDays = recentDays.ifEmpty { other.recentDays },
        capturedAt = maxOf(capturedAt, other.capturedAt),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        totalMileageKm?.let { put("totalMileageKm", it) }
        overallConsumption?.let { put("overallConsumption", it) }
        cumulativeEnergyKwh?.let { put("cumulativeEnergyKwh", it) }
        ownershipDays?.let { put("ownershipDays", it) }
        if (recentDays.isNotEmpty()) {
            put("recentDays", JSONArray().apply {
                recentDays.forEach { d ->
                    put(JSONObject().put("day", d.day).put("km", d.km))
                }
            })
        }
        put("capturedAt", capturedAt)
    }

    companion object {
        fun fromJson(raw: JSONObject?): MileageEnergySummary? {
            if (raw == null) return null
            val days = mutableListOf<DailyMileage>()
            raw.optJSONArray("recentDays")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val day = item.optString("day").takeIf { it.isNotBlank() } ?: continue
                    val km = item.opt("km")?.toString()?.toDoubleOrNull() ?: continue
                    days += DailyMileage(day, km)
                }
            }
            val summary = MileageEnergySummary(
                totalMileageKm = raw.opt("totalMileageKm")?.toString()?.toDoubleOrNull(),
                overallConsumption = raw.opt("overallConsumption")?.toString()?.toDoubleOrNull(),
                cumulativeEnergyKwh = raw.opt("cumulativeEnergyKwh")?.toString()?.toDoubleOrNull(),
                ownershipDays = raw.opt("ownershipDays")?.toString()?.toIntOrNull(),
                recentDays = days,
                capturedAt = raw.opt("capturedAt")?.toString()?.toLongOrNull() ?: 0L,
            )
            return summary.takeIf { it.hasAny }
        }

        val EMPTY: MileageEnergySummary = MileageEnergySummary()
    }
}

/**
 * 里程 / 能耗响应解析。
 *
 * 服务端字段命名未公开，这里沿用参考实现验证过的两套接法：
 * 近 7 日接口是固定契约 `data.detail[]`（day + accumulatedMileage）；
 * 累计接口字段不固定，按归一化后的 key 做别名匹配。
 */
object MileageEnergyParser {

    private const val MAX_VALUE = 1e12

    private val overallKeys = setOf(
        "comprehensiveenergyconsumption",
        "overallenergyconsumption",
        "averageenergyconsumption",
        "energyconsumptionper100km",
        "energyconsumption",
    )
    private val totalMileageKeys = setOf(
        "totalmileage", "totalmile", "mileagetotal", "totaldrivingmileage",
    )
    private val cumulativeEnergyKeys = setOf(
        "cumulativeenergy", "totalenergy", "totalenergyconsumption", "energytotal",
    )
    /**
     * 提车天数的候选字段名。
     * 服务端没有公开这套命名，所以尽量把「同一个意思的常见写法」都列上：
     * 只要界面显示成「--」，基本就是这里没命中，补一个别名即可。
     */
    private val ownershipDaysKeys = setOf(
        "ownershipdays", "dayssincepurchase", "pickupdays", "deliverydays", "carage",
        "takecardays", "usagedays", "holddays", "owneddays", "ownerdays",
        "drivingdays", "purchasedays", "daysowned", "useday",
    )
    /** 只认这几个字段，其余（含位置/身份类）一律不读。 */
    private val sensitiveKey = Regex(
        "(token|cookie|authorization|password|secret|sign|device|imei|vin|phone|mobile|account|longitude|latitude|address|location)",
        RegexOption.IGNORE_CASE,
    )

    /** 近 7 日里程：data.detail[] + data.totalmileage / totalEnergy / deliveryDays。 */
    fun parseRecent(response: JSONObject): MileageEnergySummary? {
        if (isBusinessFailure(response)) return null
        val data = response.optJSONObject("data") ?: return null
        val days = mutableListOf<DailyMileage>()
        data.optJSONArray("detail")?.let { detail ->
            for (i in 0 until detail.length()) {
                val item = detail.optJSONObject(i) ?: continue
                val day = item.opt("day")?.takeIf { it != JSONObject.NULL }?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: continue
                val km = item.opt("accumulatedMileage")?.toString()?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 0.0 && it < MAX_VALUE } ?: continue
                days += DailyMileage(day, km)
            }
        }
        val summary = MileageEnergySummary(
            totalMileageKm = firstNumber(data, totalMileageKeys),
            cumulativeEnergyKwh = firstNumber(data, setOf("totalenergy", "totalEnergy")),
            // 先看 data 第一层，取不到再在嵌套里找一次：
            // 部分车型把提车天数塞在 data.vehicleInfo / data.summary 之类的子对象里。
            ownershipDays = firstNumber(data, ownershipDaysKeys)?.toInt()
                ?: nestedOwnershipDays(data),
            recentDays = days.takeLast(7),
            capturedAt = System.currentTimeMillis(),
        )
        return summary.takeIf { it.hasAny }
    }

    /** 在 data 的嵌套对象里找提车天数（只认 [ownershipDaysKeys] 里的明确别名）。 */
    private fun nestedOwnershipDays(data: JSONObject): Int? {
        val leaves = mutableListOf<Pair<String, String>>()
        collectLeaves(data, "", leaves)
        return pick(leaves, ownershipDaysKeys)?.toInt()
    }

    /** 累计里程 / 能耗：字段不固定，按别名在整棵 JSON 里找标量。 */
    fun parseLifetime(response: JSONObject): MileageEnergySummary? {
        if (isBusinessFailure(response)) return null
        val leaves = mutableListOf<Pair<String, String>>()
        collectLeaves(response, "", leaves)
        val summary = MileageEnergySummary(
            totalMileageKm = pick(leaves, totalMileageKeys),
            overallConsumption = pick(leaves, overallKeys),
            cumulativeEnergyKwh = pick(leaves, cumulativeEnergyKeys),
            ownershipDays = pick(leaves, ownershipDaysKeys)?.toInt(),
            capturedAt = System.currentTimeMillis(),
        )
        return summary.takeIf { it.hasAny }
    }

    /** 近 7 日 X 轴标签：今天 / 昨天 / M/D。 */
    fun dayLabel(day: String): String {
        val parts = day.trim().split("-", "/")
        if (parts.size < 3) return day.trim()
        val month = parts[1].toIntOrNull() ?: return day.trim()
        val date = parts[2].toIntOrNull() ?: return day.trim()
        val today = java.time.LocalDate.now()
        val target = runCatching { java.time.LocalDate.of(parts[0].toInt(), month, date) }
            .getOrNull() ?: return "$month/$date"
        return when (target) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "$month/$date"
        }
    }

    private fun pick(leaves: List<Pair<String, String>>, keys: Set<String>): Double? {
        val hit = leaves.firstOrNull { normalize(lastKey(it.first)) in keys } ?: return null
        return hit.second.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it < MAX_VALUE }
    }

    private fun firstNumber(obj: JSONObject, keys: Set<String>): Double? {
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (normalize(key) !in keys) continue
            val value = obj.opt(key)?.takeIf { it != JSONObject.NULL }?.toString()
                ?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it < MAX_VALUE }
            if (value != null) return value
        }
        return null
    }

    private fun collectLeaves(value: Any?, path: String, out: MutableList<Pair<String, String>>) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (sensitiveKey.containsMatchIn(key)) continue
                    val child = value.opt(key)
                    val childPath = if (path.isBlank()) key else "$path.$key"
                    if (child == null || child == JSONObject.NULL) continue
                    if (child is JSONObject || child is JSONArray) collectLeaves(child, childPath, out)
                    else out += childPath to child.toString()
                }
            }

            is JSONArray -> for (i in 0 until value.length()) {
                collectLeaves(value.opt(i), "$path[$i]", out)
            }
        }
    }

    private fun isBusinessFailure(response: JSONObject): Boolean {
        val result = response.opt("result")?.takeIf { it != JSONObject.NULL }?.toString()
        val code = response.opt("code")?.takeIf { it != JSONObject.NULL }?.toString()
        val success = response.opt("success")
        return (result != null && result !in setOf("0", "200")) ||
            (code != null && code !in setOf("0", "200")) ||
            (success is Boolean && !success)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[_\\-\\s]"), "")

    private fun lastKey(path: String): String =
        path.substringAfterLast('.').substringBefore('[')
}
