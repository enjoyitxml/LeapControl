package com.leapcontrol.app

import org.json.JSONObject

/**
 * 四个座位各自是否具备某项功能（加热 / 通风分开描述）。
 *
 * 之所以要精确到座位，是因为同一辆车常见「前排有、二排没有」的情况，
 * 整组隐藏会误伤、整组显示又会给出点了没反应的按钮。
 */
data class SeatCapability(
    val driver: Boolean = false,
    val passenger: Boolean = false,
    val leftRear: Boolean = false,
    val rightRear: Boolean = false,
) {
    val hasFront: Boolean get() = driver || passenger
    val hasRear: Boolean get() = leftRear || rightRear
    val hasAny: Boolean get() = hasFront || hasRear

    fun toJson(): JSONObject = JSONObject().apply {
        put("driver", driver)
        put("passenger", passenger)
        put("leftRear", leftRear)
        put("rightRear", rightRear)
    }

    companion object {
        fun fromJson(o: JSONObject?): SeatCapability = SeatCapability(
            driver = o?.optBoolean("driver") ?: false,
            passenger = o?.optBoolean("passenger") ?: false,
            leftRear = o?.optBoolean("leftRear") ?: false,
            rightRear = o?.optBoolean("rightRear") ?: false,
        )
    }
}

/**
 * 车辆**实际具备**的可选配置。
 *
 * ## 为什么需要它
 * 车载冰箱、天窗、哨兵、座椅舒适都不是全系标配，给不带该硬件的车显示入口，
 * 点了只会得到一条失败回执。
 *
 * ## 判据从哪来
 * 服务端**没有**「车型配置清单」接口（`/vehicle/list` 只给了 `funcConfig.HVAC` 温区能力），
 * 所以唯一可信的来源是车况信号本身，分两种情况：
 *
 * 1. **后装 ECU 类**（冰箱、哨兵、座椅/方向盘/后视镜控制器）：
 *    不带该硬件时网关根本不下发这组信号 → **信号缺失 = 无此配置**。
 * 2. **车身网关常驻信号**（天窗 `1718`）：
 *    未选装可开启天窗的车（纯天幕或未选装）网关**仍会下发**占位值，
 *    即 CAN SNA（Signal Not Available）的 15 / 150 / 255 → 必须做**值域**判定，
 *    只看「有没有这个 key」会把所有车都判成有天窗。
 *
 * ## 为什么需要粘滞（latch）
 * 车休眠或离线时某些信号可能临时缺失，若严格按当帧信号判定，模块会忽有忽无。
 * 因此采用「观测到一次即永久生效」的合并策略，见 [VehicleCapabilityDetector.merge]。
 */
data class VehicleCapabilities(
    /** 车载冰箱（信号组 10707-10712）。 */
    val fridge: Boolean = false,
    /** 可开启天窗（1718，值域有效才算）。 */
    val sunroof: Boolean = false,
    /** 哨兵模式（3636）。 */
    val sentry: Boolean = false,
    /** 座椅加热，按座位。 */
    val seatHeating: SeatCapability = SeatCapability(),
    /** 座椅通风，按座位。 */
    val seatVentilation: SeatCapability = SeatCapability(),
    /** 方向盘加热（1816）。 */
    val steeringWheelHeating: Boolean = false,
    /** 后视镜加热（49 / 50）。 */
    val mirrorHeating: Boolean = false,
    /** 电动前备箱：仅 D19 系列，靠车型名识别。 */
    val frunk: Boolean = false,
) {
    /** 座椅相关的加热或通风，只要有任意一项就值得给「座椅舒适」入口。 */
    val hasAnySeatFeature: Boolean get() = seatHeating.hasAny || seatVentilation.hasAny

    /** 「座椅舒适」整页是否值得显示：座椅 + 方向盘 + 后视镜任一即可。 */
    val hasSeatComfort: Boolean get() = hasAnySeatFeature || steeringWheelHeating || mirrorHeating

    fun toJson(): JSONObject = JSONObject().apply {
        put("fridge", fridge)
        put("sunroof", sunroof)
        put("sentry", sentry)
        put("seatHeating", seatHeating.toJson())
        put("seatVentilation", seatVentilation.toJson())
        put("steeringWheelHeating", steeringWheelHeating)
        put("mirrorHeating", mirrorHeating)
        put("frunk", frunk)
    }

    companion object {
        val EMPTY: VehicleCapabilities = VehicleCapabilities()

        fun fromJson(o: JSONObject?): VehicleCapabilities = VehicleCapabilities(
            fridge = o?.optBoolean("fridge") ?: false,
            sunroof = o?.optBoolean("sunroof") ?: false,
            sentry = o?.optBoolean("sentry") ?: false,
            seatHeating = SeatCapability.fromJson(o?.optJSONObject("seatHeating")),
            seatVentilation = SeatCapability.fromJson(o?.optJSONObject("seatVentilation")),
            steeringWheelHeating = o?.optBoolean("steeringWheelHeating") ?: false,
            mirrorHeating = o?.optBoolean("mirrorHeating") ?: false,
            frunk = o?.optBoolean("frunk") ?: false,
        )
    }
}

object VehicleCapabilityDetector {

    /**
     * 天窗开度信号的有效范围：CAN 上用 0(全关) .. 10(全开 100%) 表示。
     * 未选装的车网关会回 SNA 占位值 15 / 150 / 255，都落在这个区间之外。
     */
    private val VALID_SUNROOF_OPENING = 0..10

    /** 只有 D19 系列带电动前备箱。前后用非字母数字定界，避免误匹配 D190 之类。 */
    private val FRUNK_MODEL = Regex("(?i)(^|[^A-Za-z0-9])D19([^A-Za-z0-9]|$)")

    fun supportsFrunk(carType: String?): Boolean = FRUNK_MODEL.containsMatchIn(carType.orEmpty())

    /**
     * 从一次车况采样（已解码的 signalMap，命名 key 与数字 key 都在）中识别车辆配置。
     *
     * @param signals 解码后的车况 JSONObject，**必须保留原始 key**（缺失 key = 无此硬件）
     * @param carType 车型短名，用于少数无法从信号判断的配置（目前只有前备箱）
     */
    fun detect(signals: JSONObject, carType: String? = null): VehicleCapabilities {
        val sunroofRaw = intOf(signals, "sunroofOpening", "1718")
        return VehicleCapabilities(
            fridge = hasAny(
                signals,
                "fridgeSwitch", "fridgeMode", "fridgeTargetTemp", "fridgeStatus", "fridgeStyle",
                "10707", "10708", "10709", "10711",
            ),
            // 天窗必须看值域：不带天窗的车网关一样下发 key，只是值是 SNA 占位
            sunroof = sunroofRaw != null && sunroofRaw in VALID_SUNROOF_OPENING,
            sentry = hasAny(signals, "sentryMode", "sentryStatus", "sentrySwitch", "3636"),
            seatHeating = SeatCapability(
                driver = hasAny(signals, "driverSeatHeating", "2100"),
                passenger = hasAny(signals, "passengerSeatHeating", "2118"),
                leftRear = hasAny(signals, "leftRearSeatHeating", "1879"),
                rightRear = hasAny(signals, "rightRearSeatHeating", "1880"),
            ),
            seatVentilation = SeatCapability(
                driver = hasAny(signals, "driverSeatVentilation", "2101"),
                passenger = hasAny(signals, "passengerSeatVentilation", "2119"),
                leftRear = hasAny(signals, "leftRearSeatVentilation", "3727"),
                rightRear = hasAny(signals, "rightRearSeatVentilation", "3728"),
            ),
            // 注意不要顺带兜底 rearWindowHeating（后风挡加热）：那几乎全系都有，会把判据污染成恒真
            steeringWheelHeating = hasAny(signals, "steeringWheelHeating", "1816"),
            mirrorHeating = hasAny(
                signals,
                "rearviewMirrorHeating", "leftMirrorHeating", "rightMirrorHeating", "49", "50",
            ),
            frunk = supportsFrunk(carType),
        )
    }

    /**
     * 粘滞合并：任一位一旦观测为真就保持为真。
     *
     * 目的是抵抗信号抖动（车休眠时不报某些信号），代价是不会因为「后来没了」
     * 而隐藏入口——对硬件配置而言这是正确的取舍：车不会开着开着把冰箱拆了。
     */
    fun merge(previous: VehicleCapabilities?, current: VehicleCapabilities): VehicleCapabilities {
        if (previous == null) return current
        return VehicleCapabilities(
            fridge = previous.fridge || current.fridge,
            sunroof = previous.sunroof || current.sunroof,
            sentry = previous.sentry || current.sentry,
            seatHeating = mergeSeat(previous.seatHeating, current.seatHeating),
            seatVentilation = mergeSeat(previous.seatVentilation, current.seatVentilation),
            steeringWheelHeating = previous.steeringWheelHeating || current.steeringWheelHeating,
            mirrorHeating = previous.mirrorHeating || current.mirrorHeating,
            frunk = previous.frunk || current.frunk,
        )
    }

    private fun mergeSeat(previous: SeatCapability, current: SeatCapability): SeatCapability = SeatCapability(
        driver = previous.driver || current.driver,
        passenger = previous.passenger || current.passenger,
        leftRear = previous.leftRear || current.leftRear,
        rightRear = previous.rightRear || current.rightRear,
    )

    /** 存在性判定：只要任一候选 key 存在且不是 JSONObject.NULL，即认为该硬件在车上。 */
    private fun hasAny(signals: JSONObject, vararg keys: String): Boolean =
        keys.any { signals.has(it) && !signals.isNull(it) }

    private fun intOf(signals: JSONObject, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { signals.opt(it) }
            ?.toString()?.trim()?.toDoubleOrNull()?.toInt()
}
