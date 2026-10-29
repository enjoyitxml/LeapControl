package com.leapcontrol.app

import org.json.JSONObject

/** Shared display policy; telemetry and old snapshots cannot override a configured power type. */
object VehiclePowerTypeResolver {
    fun fromVehicleConfig(
        vin: String,
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?,
        defaultPowerType: SessionStore.VehiclePowerType?,
        vehicles: List<Vehicle>
    ): SessionStore.VehiclePowerType? {
        val vehicle = vehicles.firstOrNull { vin.isNotBlank() && it.vin == vin }
        return resolve(
            configuredPowerType = configuredPowerType,
            carType = carType?.takeIf { it.isNotBlank() } ?: vehicle?.carType,
            cachedPowerType = defaultPowerType ?: vehicle?.powerType
        )
    }

    fun resolve(
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?,
        cachedPowerType: SessionStore.VehiclePowerType? = null,
        hasFuelTelemetry: Boolean = false
    ): SessionStore.VehiclePowerType? = configuredPowerType
        ?: fromCarType(carType)
        ?: cachedPowerType
        ?: SessionStore.VehiclePowerType.RANGE_EXTENDER.takeIf { hasFuelTelemetry }

    fun fromStatus(
        status: JSONObject,
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?
    ): SessionStore.VehiclePowerType? = resolve(
        configuredPowerType = configuredPowerType,
        carType = carType,
        hasFuelTelemetry = hasFuelTelemetry(status)
    )

    /**
     * 是不是增程车 —— 首页要按这个决定显示「电 + 油」两行还是只显示电量一行。
     *
     * 优先级：用户手动指定 > 车型名里的「增程 / REEV」> 报文里真的有油。
     * **不能拿「fuelSoc 字段存在」当判据**：纯电车（纯电 C01 已确认）也会下发
     * 3235 = 0，按「存在即增程」判会把纯电车显示成增程，主续航还会去取综合
     * 续航（纯电没有这一路）→ 界面上续航直接变成「--」。
     */
    fun isRangeExtender(
        status: JSONObject,
        configuredPowerType: SessionStore.VehiclePowerType?,
        carType: String?,
    ): Boolean = when (configuredPowerType) {
        SessionStore.VehiclePowerType.RANGE_EXTENDER -> true
        SessionStore.VehiclePowerType.PURE_ELECTRIC -> false
        null -> fromCarType(carType) == SessionStore.VehiclePowerType.RANGE_EXTENDER ||
            hasFuelTelemetry(status)
    }

    /** 报文里确实有油：有燃油续航信号，或燃油比例大于 0。 */
    fun hasFuelTelemetry(status: JSONObject): Boolean =
        VehicleStatusMapper.fuelRange(status) != null ||
            (VehicleStatusMapper.fuelSocPercent(status) ?: 0) > 0

    fun fromCarType(carType: String?): SessionStore.VehiclePowerType? = when {
        // REEV also contains EV, so the range-extender marker must be checked first.
        carType?.contains("\u589e\u7a0b") == true ||
            carType?.contains("REEV", ignoreCase = true) == true -> SessionStore.VehiclePowerType.RANGE_EXTENDER
        carType?.contains("\u7eaf\u7535") == true ||
            carType?.contains("EV", ignoreCase = true) == true -> SessionStore.VehiclePowerType.PURE_ELECTRIC
        else -> null
    }
}

fun SessionStore.VehiclePowerType?.toStatusPowerType(): VehicleStatusMapper.PowerType? = when (this) {
    SessionStore.VehiclePowerType.PURE_ELECTRIC -> VehicleStatusMapper.PowerType.PURE_ELECTRIC
    SessionStore.VehiclePowerType.RANGE_EXTENDER -> VehicleStatusMapper.PowerType.RANGE_EXTENDER
    null -> null
}
