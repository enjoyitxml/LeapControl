package com.leapcontrol.app

import org.json.JSONObject

/**
 * 从车况原始 JSON 里解析 App 自己关心的几个派生信号：锁车、行驶、车窗。
 *
 * 这里放的都是**纯函数**（只吃 JSON、不碰网络与缓存），所以首页状态卡、
 * 锁车后安全提醒都直接用它，也方便单测。
 */
object VehicleSignals {
    fun locked(status: JSONObject): Boolean? = locked(status.opt("driverDoorLockStatus"))

    fun locked(values: Map<String, Any?>): Boolean? = locked(values["driverDoorLockStatus"])

    /**
     * 三态布尔解析：`true/false`、`1/0`、`"1"/"0"` 都认，缺失或无法解析 → null。
     *
     * 车端的开关类信号（除霜、加热…）在不同车型上既有数字也有字符串，
     * 而且**缺信号不等于关着**，所以这里宁可返回 null 让 UI 不显示，
     * 也不要猜成 false。
     */
    fun bool(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "on", "open", "opened" -> true
            "false", "0", "off", "close", "closed" -> false
            else -> null
        }
        else -> null
    }

    /** 返回开着的那几扇车窗的名称，用于锁车后的安全提醒。 */
    fun openWindowLabels(status: JSONObject, carType: String? = null): List<String> =
        openWindowLabels { key -> status.opt(key) }
            .takeUnless { carType?.contains("T03", ignoreCase = true) == true }
            .orEmpty()

    /** 只有车况里真的带车窗信号时才为 true（缺信号 ≠ 车窗关着）。 */
    fun hasWindowTelemetry(status: JSONObject, carType: String? = null): Boolean {
        if (carType?.contains("T03", ignoreCase = true) == true) return false
        return WINDOW_SIGNAL_KEYS.any { key -> status.has(key) && status.opt(key) != JSONObject.NULL }
    }

    /**
     * 是否在行驶：以车速为准（停车后可能长时间保持同一个锁车状态，
     * 车门锁不能当判据）；老车况没有 speed 字段时退回挡位判断。
     */
    fun isDriving(status: JSONObject): Boolean = when {
        status.has("speed") -> parseSpeed(status.opt("speed"))?.let { it > 0.0 } == true
        else -> isDrivingGear(status.opt("gearStatus"))
    }

    fun isDriving(values: Map<String, Any?>): Boolean = when {
        values.containsKey("speed") -> parseSpeed(values["speed"])?.let { it > 0.0 } == true
        else -> isDrivingGear(values["gearStatus"])
    }

    private fun isDrivingGear(value: Any?): Boolean = when (value?.toString()?.trim()?.uppercase()) {
        "D", "DRIVE", "前进", "D挡", "3", "R", "REVERSE", "倒车", "R挡", "1" -> true
        else -> false
    }

    private fun parseSpeed(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        else -> value?.toString()?.trim()?.removeSuffix("km/h")?.trim()?.toDoubleOrNull()
    }

    private fun openWindowLabels(valueForKey: (String) -> Any?): List<String> {
        val percentageKeys = listOf(
            "左前" to "leftFrontWindowPercent",
            "右前" to "rightFrontWindowPercent",
            "左后" to "leftRearWindowPercent",
            "右后" to "rightRearWindowPercent"
        )
        val statusKeys = listOf(
            "左前" to "driverWindowStatus",
            "右前" to "rightFrontWindowStatus",
            "左后" to "leftRearWindowStatus",
            "右后" to "rightRearWindowStatus"
        ).toMap()
        return percentageKeys.mapNotNull { (label, key) ->
            val statusKey = statusKeys.getValue(label)
            if (windowPercentageOpen(valueForKey(key)) || windowStatusOpen(valueForKey(statusKey))) label else null
        }
    }

    private val WINDOW_SIGNAL_KEYS = listOf(
        "leftFrontWindowPercent",
        "rightFrontWindowPercent",
        "leftRearWindowPercent",
        "rightRearWindowPercent",
        "driverWindowStatus",
        "rightFrontWindowStatus",
        "leftRearWindowStatus",
        "rightRearWindowStatus"
    )

    private fun windowPercentageOpen(value: Any?): Boolean =
        value?.toString()?.trim()?.removeSuffix("%")?.toDoubleOrNull()?.let { it > 0.0 } == true

    private fun windowStatusOpen(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "open", "opened", "opening" -> true
            else -> false
        }
        else -> false
    }

    private fun locked(value: Any?): Boolean? = when (value) {        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "locked", "lock" -> true
            "false", "0", "unlocked", "unlock" -> false
            else -> null
        }
        else -> null
    }
}
