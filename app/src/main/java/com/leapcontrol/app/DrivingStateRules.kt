package com.leapcontrol.app

import java.util.Locale

/**
 * 车辆状态胶囊的语义色调：决定状态点的颜色，以及要不要做「活着」的呼吸动画。
 */
enum class DrivingTone {
    /** 行驶中：主题色 + 呼吸点。 */
    MOVING,

    /** 倒车：琥珀色，和前进区分开。 */
    REVERSING,

    /** 已驻车 / 待行 / 空挡：静点，不抢注意力。 */
    PARKED,

    /** 充电相关：绿色（与首页电量条同色）。 */
    CHARGING,

    /** 需要看一眼的异常（充电故障）：琥珀色。 */
    WARNING,
}

/**
 * 车辆正下方那颗状态胶囊要显示的内容。
 *
 * [gear] 是挡位徽标（P/R/N/D），车端没报挡位时为空、不画徽标；
 * [detail] 是结论后面的关键数值（车速 / 充电剩余），没有就不画。
 */
data class DrivingBadge(
    val label: String,
    val detail: String? = null,
    val gear: String? = null,
    val tone: DrivingTone,
)

/**
 * 「车在干什么」的判定规则（纯逻辑，可单测）。
 *
 * ## 只用三个信号
 * 挡位 1010、车速 1319、充电状态 1149 —— 都是车况报文里现成的字段，不新增任何请求。
 * `vehicleState`(1944) 刻意**不用**：文档写「1=Ready、0=下电休眠」，上游代码却把 0/1/3 当成下电、
 * 2 当成运行，两套口径互相矛盾且没有实车核验，拿它下结论会给出错的状态。
 *
 * ## 挡位字母不等于车辆状态
 * 2026-11-04 用户反馈「车已经停好锁车了，这里显示 N 空挡，不合理」。根因是车端熄火后
 * 并不总把 1010 刷成 P：有的车型停止刷新（残值还是熄火前的挡位），有的车主习惯
 * **挂 N + 拉电子手刹**再锁车。这时按挡位直译成「空挡」，对远程看手机的人传达的是
 * 「没挂 P、可能溜车」，纯属吓人。
 *
 * 所以已熄火（[isShutDown]：闭锁 / BCM 电源 OFF / 整车状态非运行 / EPB 拉起任一成立）时
 * **状态压过挡位**，一律说「已驻车」；挡位徽标只在它是真的 P 时才画
 * （N / D / R 熄火后不可信，画出来会与「已驻车」自相矛盾）。
 * 上电状态下的 N 挡仍然老老实实说「空挡」—— 人还在车上，提醒他挂 P 是有意义的。
 *
 * ## 判不出来就什么都不显示
 * 挡位和车速都没有时返回 null，首页整条不占位 —— 与其写「状态未知」，不如不出现
 * （用户 2026-11-04 选定了这个口径）。
 *
 * ## 车速的时效
 * 这不是仪表盘：车速跟着车况刷新走。首页已有「行驶中每 60 秒静默回拉一次」的机制
 * （见 HomeScreen），所以行驶时大约一分钟跳一次；驻车后不再自动拉。
 */
object DrivingStateRules {

    /** 低于这个速度按静止算：GPS / 报文抖动会给出 0.1、0.2 这种残值。 */
    const val MOVING_THRESHOLD_KMH = 0.5

    /** 会被当作「正在充电」的状态：1 充电中 / 2 完成 / 3 故障 / 4 预约等待 / 6 暂停。 */
    private val CHARGE_STATES = setOf(1, 2, 3, 4, 6)

    /**
     * 由原始信号推出状态胶囊；判不出来返回 null。
     *
     * [chargeRemainMinutes] 优先于 [chargeTimeLeft]：前者是信号 1200 的原值（分钟），
     * 后者只是同一个信号格式化后的文案，拿到原值就能自己控制「剩 2 小时 5 分钟」这种说法。
     *
     * [isShutDown] 见 [VehicleHomeStatus.isVehicleShutDown]：闭锁 / 电源 OFF / 非运行 / EPB 拉起。
     * [locked] 单独传一份是为了压车速：车在开绝不可能闭锁，所以闭锁状态下的车速一定是报文残值。
     * （不能反过来拿 [isShutDown] 压车速 —— 它把 `vehicleState==1` 当下电，万一行驶中报 1，
     * 正在开的车会被说成「已驻车」，那比残留车速严重得多。）
     */
    fun badge(
        gearStatus: String?,
        speedKmh: Double?,
        chargeState: Int? = null,
        chargeRemainMinutes: Int? = null,
        chargeTimeLeft: String? = null,
        isShutDown: Boolean = false,
        locked: Boolean? = null,
    ): DrivingBadge? {
        val gear = normalizeGear(gearStatus)
        val speed = speedKmh?.takeIf { it.isFinite() && it >= 0.0 }
        val moving = locked != true && speed != null && speed > MOVING_THRESHOLD_KMH

        // 熄火后挡位字母不可信：只有 P 是真信号，N / D / R 一律不画
        val trustedGear = if (isShutDown && gear != "P") null else gear

        // 1. 在动就是最高优先级：充电状态下不可能有车速，万一同上报以行驶为准
        if (moving) {
            val kmh = formatSpeed(speed!!)
            return if (gear == "R") {
                DrivingBadge(label = "倒车", detail = kmh, gear = gear, tone = DrivingTone.REVERSING)
            } else {
                DrivingBadge(label = "行驶中", detail = kmh, gear = gear, tone = DrivingTone.MOVING)
            }
        }

        // 2. 充电：车必然停着，这条比「已驻车」信息量大
        if (chargeState != null && chargeState in CHARGE_STATES) {
            return DrivingBadge(
                label = ChargeStatus.label(chargeState),
                detail = chargeDetail(chargeState, chargeRemainMinutes, chargeTimeLeft),
                gear = trustedGear,
                // 充电故障不能一路绿下去：充电桩真出问题时，那颗点得换个颜色
                tone = if (chargeState == 3) DrivingTone.WARNING else DrivingTone.CHARGING,
            )
        }

        // 3. 已熄火 / 已闭锁：车停稳了，挡位不再作为判据（见类注释）
        if (isShutDown) {
            return DrivingBadge(label = "已驻车", gear = trustedGear, tone = DrivingTone.PARKED)
        }

        // 4. 没有挡位但有车速（0）：静止，按驻车说
        if (gear == null) {
            return if (speed != null) {
                DrivingBadge(label = "已驻车", gear = null, tone = DrivingTone.PARKED)
            } else {
                null
            }
        }

        return when (gear) {
            "P" -> DrivingBadge(label = "已驻车", gear = gear, tone = DrivingTone.PARKED)
            "N" -> DrivingBadge(label = "空挡", gear = gear, tone = DrivingTone.PARKED)
            // 挂着 D 挡没走：不是「行驶中」，也不是「已驻车」（可能正等红灯）
            "D" -> DrivingBadge(label = "待行", gear = gear, tone = DrivingTone.PARKED)
            "R" -> DrivingBadge(label = "倒车", gear = gear, tone = DrivingTone.REVERSING)
            else -> null
        }
    }

    /** 车速原始值可能是 `"42"`、`"42.5"`、`"42 km/h"`，统一解析成数字；拿不到返回 null。 */
    fun parseSpeed(raw: String?): Double? = raw
        ?.trim()
        ?.removeSuffix("km/h")
        ?.trim()
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }

    /** 整数值不显示小数点（42 km/h 而不是 42.0 km/h）。 */
    fun formatSpeed(kmh: Double): String =
        if (kmh % 1.0 == 0.0) {
            String.format(Locale.CHINA, "%.0f km/h", kmh)
        } else {
            String.format(Locale.CHINA, "%.1f km/h", kmh)
        }

    /** 挡位归一化：服务端可能下发字母、中文或数字编码。 */
    fun normalizeGear(gearStatus: String?): String? =
        when (gearStatus?.trim()?.uppercase()) {
            "P", "P挡", "PARK", "驻车", "停车", "0" -> "P"
            "R", "R挡", "REVERSE", "倒车", "1" -> "R"
            "N", "N挡", "NEUTRAL", "空挡", "2" -> "N"
            "D", "D挡", "DRIVE", "前进", "3" -> "D"
            else -> null
        }

    /**
     * 充电剩余时间：只有「充电中」才有意义。
     * 0 分钟不是「剩 0 分钟」而是马上就要满，所以单独说一句「即将充满」。
     */
    fun chargeDetail(
        chargeState: Int?,
        remainMinutes: Int?,
        timeLeft: String?,
    ): String? {
        if (chargeState != 1) return null
        if (remainMinutes != null) {
            return when {
                remainMinutes <= 0 -> "即将充满"
                remainMinutes < 60 -> "剩 $remainMinutes 分钟"
                remainMinutes % 60 == 0 ->
                    String.format(Locale.CHINA, "剩 %d 小时", remainMinutes / 60)
                else ->
                    String.format(
                        Locale.CHINA,
                        "剩 %d 小时 %d 分钟",
                        remainMinutes / 60,
                        remainMinutes % 60,
                    )
            }
        }
        val text = timeLeft?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "剩 $text"
    }
}
