package com.leapcontrol.app

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 充电中心页面上的全部可编辑项（本地草稿）。
 *
 * 页面上的开关、滑杆、时段都只改这份草稿，点「保存并下发设置」时才真正下发：
 * - 健康充电 + 充电上限 → `healthyCharging/control`
 * - 预约充电 → `cmdid=190` 充电计划
 * - 电池预热 → `cmdid=161` 预约 PTC 加热计划
 *
 * 之所以草稿化而不是「拨一下发一条」：三条指令各有网关限流，逐项下发很容易撞
 * 「系统繁忙」；而且充电上限同时是健康充电与预约充电的 chargesoc 字段，
 * 必须一次性带上同一个值，否则两边会互相覆盖成不同数字。
 */
data class ChargeCenterDraft(
    /** 健康充电（电池养护）开关。 */
    val healthy: Boolean = false,
    /** 充电上限 50..100，5% 一档。健康充电与预约充电共用同一个值。 */
    val limitSoc: Int = ChargeSchedule.MAX_LIMIT_SOC,
    val scheduledEnabled: Boolean = false,
    val scheduledStart: String = "23:00",
    val scheduledEnd: String = "07:00",
    /** 预约充电重复日（1=周一 … 7=周日）。 */
    val scheduledDays: Set<Int> = ChargeSchedule.ALL_DAYS,
    /** 到结束时间仍未达到充电上限时是否继续充电（190 的 recharge 字段）。 */
    val continueUntilLimit: Boolean = true,
    val preheatEnabled: Boolean = false,
    val preheatStart: String = "07:30",
    val preheatDays: Set<Int> = ChargeSchedule.ALL_DAYS,
)

/**
 * 车端已保存的电池预热计划（`cmdid=161` 的 getappointment 查询结果）。
 *
 * [hasSchedule] = false 表示车端确实没有预约项（不是查询失败）；
 * 查询失败时调用方拿到 null，应保留本地草稿而不是清空。
 */
data class ScheduledPreheatState(
    val hasSchedule: Boolean,
    val startTime: String?,
    val days: String?,
)

/**
 * 充电时间/周期的纯逻辑：跨天判断、摘要文案、SOC 对齐、161 days 解码。
 *
 * 这些规则都要被「页面回显」和「指令下发」同时使用，写在 UI 里两边容易算得不一样
 * （最典型的是「23:00-07:00 到底算不算次日」），所以集中在这里并加单测。
 */
object ChargeSchedule {

    /** 1=周一 … 7=周日（与 [ChargePlanCyclesHelper] 的 daySet 一致）。 */
    val ALL_DAYS: Set<Int> = (1..7).toSet()

    const val MIN_LIMIT_SOC = 50
    const val MAX_LIMIT_SOC = 100
    const val SOC_STEP = 5

    /** 官方把 90% 标注为「最佳限值」，页面在滑杆下方画一个小三角指到它。 */
    const val BEST_LIMIT_SOC = 90

    /** 星期选择条的显示顺序：周日打头，与官方 App 一致。 */
    val WEEKDAY_ORDER: List<Int> = listOf(7, 1, 2, 3, 4, 5, 6)

    private val TIME_REGEX = Regex("(\\d{1,2}):(\\d{2})")

    /** "23:00" → (23, 0)；格式不对返回 null（调用方用默认值兜底）。 */
    fun parseTime(text: String?): Pair<Int, Int>? {
        val match = TIME_REGEX.find(text?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    /** (7, 5) → "07:05"。 */
    fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /**
     * 结束时间是否落在次日。
     *
     * 只按「结束 <= 开始」判断：充电计划是每天循环的时段，不存在跨两天以上的情况，
     * 相等（23:00-23:00）按整车逻辑视为一整天 → 次日，与 161 的当日+1 处理一致。
     */
    fun isOvernight(start: String?, end: String?): Boolean {
        val s = parseTime(start) ?: return false
        val e = parseTime(end) ?: return false
        return e.first * 60 + e.second <= s.first * 60 + s.second
    }

    /** "23:00-次日07:00" / "09:00-18:00"。 */
    fun rangeText(start: String?, end: String?): String {
        val from = parseTime(start)?.let { formatTime(it.first, it.second) } ?: "--:--"
        val to = parseTime(end)?.let { formatTime(it.first, it.second) } ?: "--:--"
        return if (isOvernight(start, end)) "$from-次日$to" else "$from-$to"
    }

    /** 单日短名："一" … "日"。 */
    fun weekdayShort(day: Int): String = when (day) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }

    /** 重复日文案："每天" / "工作日" / "周末" / "周一、周四"。 */
    fun daysLabel(days: Set<Int>): String {
        val effective = days.ifEmpty { ALL_DAYS }
        return when {
            effective == ALL_DAYS -> "每天"
            effective == setOf(1, 2, 3, 4, 5) -> "工作日"
            effective == setOf(6, 7) -> "周末"
            else -> effective.sorted().joinToString("、") { "周${weekdayShort(it)}" }
        }
    }

    /** 卡片副标题一行说完："每天 23:00-次日07:00"。 */
    fun summaryText(start: String?, end: String?, days: Set<Int>): String =
        "${daysLabel(days)} ${rangeText(start, end)}"

    /** 充电上限对齐到 50..100 的 5 的倍数（车端与健康充电接口都按整数百分比收）。 */
    fun sanitizeSoc(value: Int): Int {
        val clamped = value.coerceIn(MIN_LIMIT_SOC, MAX_LIMIT_SOC)
        val snapped = ((clamped + SOC_STEP / 2) / SOC_STEP) * SOC_STEP
        return snapped.coerceIn(MIN_LIMIT_SOC, MAX_LIMIT_SOC)
    }

    /** 滑杆位置（0f..1f）↔ SOC。 */
    fun socToFraction(soc: Int): Float {
        val span = (MAX_LIMIT_SOC - MIN_LIMIT_SOC).toFloat()
        return ((sanitizeSoc(soc) - MIN_LIMIT_SOC) / span).coerceIn(0f, 1f)
    }

    fun fractionToSoc(fraction: Float): Int {
        val span = (MAX_LIMIT_SOC - MIN_LIMIT_SOC).toFloat()
        return sanitizeSoc(MIN_LIMIT_SOC + (fraction.coerceIn(0f, 1f) * span).toInt())
    }

    /**
     * 解 161 计划里的 `days` 数组。
     *
     * 官方约定 `0`=周日、`1`=周一 … `6`=周六；本项目的 daySet 是 1=周一 … 7=周日，
     * 所以 0 要映射成 7。数组为空时返回全周（与车端「每天」的默认行为一致）。
     */
    fun daysFrom161(array: JSONArray?): Set<Int> {
        if (array == null || array.length() == 0) return ALL_DAYS
        val days = (0 until array.length()).mapNotNull { index ->
            array.optInt(index, -1).takeIf { it in 0..6 }
        }.map { if (it == 0) 7 else it }.toSet()
        return days.ifEmpty { ALL_DAYS }
    }

    /** 把 161 的 `start_time`（"2026-10-28 07:30:00"）压成 "07:30"。 */
    fun timeOfDay(text: String?): String? {
        val match = TIME_REGEX.find(text?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return formatTime(hour, minute)
    }

    /**
     * 解析 `cmdid=161` 的 getappointment 返回。
     *
     * data 可能是 JSON 字符串或对象；`controls` 为空表示车端没有预约项。
     * 解析不出结构时返回 null（查询失败），调用方保留本地草稿。
     */
    fun parsePreheatState(payload: Any?): ScheduledPreheatState? {
        val text = when (payload) {
            null, JSONObject.NULL -> return null
            is JSONObject -> payload.toString()
            is JSONArray -> return null
            else -> payload.toString()
        }.trim()
        if (text.isBlank()) return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val controls = root.optJSONArray("controls") ?: return null
        if (controls.length() == 0) {
            return ScheduledPreheatState(hasSchedule = false, startTime = null, days = null)
        }
        val item = controls.optJSONObject(0)
            ?: return ScheduledPreheatState(hasSchedule = false, startTime = null, days = null)
        val days = daysFrom161(item.optJSONArray("days"))
        return ScheduledPreheatState(
            hasSchedule = item.optString("on") != "0",
            startTime = timeOfDay(item.optString("start_time")),
            days = ChargePlanCyclesHelper.toVehicleMask(days),
        )
    }
}
