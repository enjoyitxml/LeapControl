package com.leapcontrol.app

import java.util.Locale
import kotlin.math.abs
import org.json.JSONObject

/** Shared formatting and interpretation for the verified charging signals. */
object ChargeStatus {
    fun state(status: JSONObject): Int? = state(status.opt("chargeState"))

    fun state(values: Map<String, Any?>): Int? = state(values["chargeState"])

    fun label(state: Int?): String = when (state) {
        0 -> "未插枪"
        1 -> "充电中"
        2 -> "充电完成"
        3 -> "充电故障"
        4 -> "预约充电等待"
        6 -> "充电暂停"
        else -> "充电状态未知"
    }

    fun power(status: JSONObject): String? {
        return power(state(status), status.opt("batteryVoltage"), status.opt("batteryCurrent"))
    }

    fun power(values: Map<String, Any?>): String? =
        power(state(values), values["batteryVoltage"], values["batteryCurrent"])

    /** Returns the absolute battery electrical power for charging or driving displays. */
    fun electricalPower(voltageValue: Any?, currentValue: Any?): String? {
        val voltage = electricalValue(voltageValue, "V") ?: return null
        val current = electricalValue(currentValue, "A") ?: return null
        return String.format(Locale.US, "%.1f kW", abs(voltage * current) / 1000.0)
    }

    /**
     * Formats the charging current for display as a non-negative value.
     *
     * The vehicle reports charging current with a negative sign in some states
     * (for example, -8.299). The sign indicates direction and is not useful in
     * the user-facing current field, so only the display value is normalized.
     */
    fun displayCurrent(status: JSONObject): String? = displayCurrent(status.opt("batteryCurrent"))

    fun displayCurrent(values: Map<String, Any?>): String? = displayCurrent(values["batteryCurrent"])

    private fun displayCurrent(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim()
        if (raw.isBlank()) return null

        val numericText = raw.removeSuffix("A").trim()
        val numeric = numericText.toDoubleOrNull()
        if (numeric == null || !numeric.isFinite()) return numericText
        return if (numericText.startsWith("-")) numericText.removePrefix("-") else numericText
    }

    private fun power(state: Int?, voltageValue: Any?, currentValue: Any?): String? {
        if (state != 1) return null
        return electricalPower(voltageValue, currentValue)
    }

    private fun electricalValue(value: Any?, unit: String): Double? {
        if (value == null || value == JSONObject.NULL) return null
        return value
            .toString()
            .trim()
            .removeSuffix(unit)
            .trim()
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() }
    }

    fun remainingTime(value: Any?): String? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim()
        if (raw.isBlank()) return null
        val numericText = raw
            .removePrefix("约")
            .removeSuffix("分钟")
            .trim()
        val minutes = numericText.toDoubleOrNull()
        if (minutes == null || !minutes.isFinite()) return raw

        val roundedMinutes = String.format(Locale.CHINA, "%.0f", minutes).toIntOrNull() ?: return raw
        if (roundedMinutes < 0) return raw
        return when {
            roundedMinutes == 0 -> "已充满"
            roundedMinutes < 60 -> "${roundedMinutes}分钟"
            else -> "${roundedMinutes / 60}时${roundedMinutes % 60}分"
        }
    }

    fun type(status: JSONObject): String? = type(status.opt("dcInputFastCharge"))

    fun type(values: Map<String, Any?>): String? = type(values["dcInputFastCharge"])

    /**
     * 判断当前是否已插上充电枪。
     *
     * 最硬的证据是交流 / 直流输入信号（`acInputSlowCharge` / `dcInputFastCharge`）：
     * 任一为真就是插着枪。两路都没有信号时只能靠 `chargeState` 猜，这时按宽松口径
     * 认 `1` 充电中 / `2` 充满 / `4` 预约等待 / `5` 直流充电中 / `6` 暂停 ——
     * 否则车端一进预约等待、或手动停充后停在 `6`，整排「解锁充电枪 / 开始充电」会消失。
     *
     * **但车端明确报了「两路都没插」（ac、dc 都是 0）时要收紧**（上游 4.5.12 的修法）：
     * 这时只剩 `1` / `2` / `6` 三种「插着枪才可能出现」的状态可信，
     * `4` 预约等待不再当作插枪证据 —— 预约了充电但人还没插枪的车，
     * 以前会凭 `chargeState=4` 在充电中心冒出一排按钮，点了必然空转。
     */
    fun gunConnected(acInputSlowCharge: Any?, dcInputFastCharge: Any?, state: Int?): Boolean {
        val ac = boolean(acInputSlowCharge)
        val dc = boolean(dcInputFastCharge)
        if (ac == true || dc == true) return true
        // 明确「两路都没插」：只认插着枪才可能出现的状态
        if (ac == false && dc == false) return state != null && state in GUN_PLUGGED_STATES
        // 信号缺失（车型不上报）：退回宽松口径
        return state != null && state in GUN_CONNECTED_STATES
    }

    fun isGunConnected(status: JSONObject?): Boolean {
        if (status == null) return false
        return gunConnected(
            acInputSlowCharge = status.opt("acInputSlowCharge"),
            dcInputFastCharge = status.opt("dcInputFastCharge"),
            state = state(status),
        )
    }

    fun isGunConnected(values: Map<String, Any?>?): Boolean {
        if (values == null) return false
        return gunConnected(
            acInputSlowCharge = values["acInputSlowCharge"],
            dcInputFastCharge = values["dcInputFastCharge"],
            state = state(values),
        )
    }

    /**
     * 电池是否真的到顶。
     *
     * `chargeState=2` 只代表「这次充电结束了」，不代表 100%：开了健康充电
     * （充电上限 90%）的车充到 90% 就停在 `2`，被拔枪 / 手动停充也会停在 `2`。
     * 文案只在电量确实到 100% 时才敢写「已充满」，其余一律按「充电结束」说。
     */
    fun fullConfirmed(state: Int?, soc: Int?): Boolean = state == FULL_STATE && soc != null && soc >= 100

    private const val FULL_STATE = 2

    /** 车端明确未插枪时，仍然可信的「插着枪才会出现」状态。 */
    private val GUN_PLUGGED_STATES = setOf(1, 2, 6)

    private val GUN_CONNECTED_STATES = setOf(1, 2, 4, 5, 6)

    private fun type(value: Any?): String? = when (boolean(value)) {
        true -> "直流快充"
        false -> "交流充电"
        null -> null
    }

    /**
     * `chargeState=2` is the verified service-side charging-complete state.
     *
     * Some vehicle variants can expose a non-zero `chargeCompleted` field while
     * the reported charging state is still active. It is retained as decoded
     * telemetry, but must not independently produce a "full" notification.
     */
    fun completed(status: JSONObject): Boolean = state(status) == 2

    /**
     * Determines whether the traction battery is full for the configured power type.
     *
     * Range-extender vehicles expose fuel and combined range values in the 325x/326x
     * group, but those values are not charging-completion signals. The charging
     * notification must only follow the traction-battery state (1149/3736), just as
     * it does for pure-electric vehicles.
     */
    fun completed(status: JSONObject, powerType: VehicleStatusMapper.PowerType?): Boolean =
        when (powerType) {
            VehicleStatusMapper.PowerType.RANGE_EXTENDER,
            VehicleStatusMapper.PowerType.PURE_ELECTRIC,
            null -> completed(status)
        }

    fun completed(values: Map<String, Any?>): Boolean = state(values) == 2

    private fun state(value: Any?): Int? = value?.toString()?.toIntOrNull()

    private fun boolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }
}
