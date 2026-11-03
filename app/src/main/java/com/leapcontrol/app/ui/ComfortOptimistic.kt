package com.leapcontrol.app.ui

/**
 * 座舱舒适件（座椅加热 / 通风、方向盘加热、后视镜加热）下发后的「乐观覆盖」。
 *
 * ## 为什么需要这一层
 * 车端这几个信号（2100 / 2101 / 2118 / 2119 / 1879 / 1880 / 3727 / 3728 / 1816 / 49）回报
 * 明显滞后，个别车型干脆不上报。点完「关闭」到车端真值翻篇之间，任何一次车况刷新都会把
 * 界面弹回旧档位 —— 用户看到的就是「点了关闭，过一会儿又变回弱档」。
 *
 * 上游 leapauto 的做法就是在保护窗口内用目标值盖住车端旧值
 * （`MainActivity` 的 `optimisticSteeringWheelHeating` + `comfortProtected`，15 秒），
 * 并在窗口内阶梯轮询，一旦车端真值追上目标就立刻让位。指令参数本身和我们完全一样
 * （cmdid=320 `{"level":"0"}`），差别只在这一层。
 *
 * ## 三条规则
 * ① 下发后 [WINDOW_MS] 内，该字段一律显示目标档位；
 * ② 车端真值一旦等于目标，立刻释放（之后完全听车端的）；
 * ③ 窗口到期仍不一致 → 如实回到车端值。指令确实没生效就不能一直骗人，
 *    界面上「过一会儿又变回弱档」是正确答案，而不是 bug。
 *
 * 纯函数，时间由调用方传入，方便单测。
 */
object ComfortOptimistic {

    /**
     * 保护窗口。
     *
     * 上游用 15 秒，因为它的下发是异步的、发完立刻返回，轮询在 15 秒内阶梯跑完。
     * 我们的 [AppRuntime.control] 是**挂起等回执**的：非空调指令最长等 20 秒
     * （`CONTROL_RESULT_MAX_WAIT_MS`），返回后还要在 3 秒后再回拉一次车况
     * （`AC_SETTLE_DELAY_MS`）。窗口按 15 秒算会在第一次回拉时就已经过期，
     * 界面照样弹回旧档位 —— 所以这里按我们的时序放到 25 秒。
     */
    const val WINDOW_MS = 25_000L

    const val DRIVER_HEAT = "driverSeatHeating"
    const val DRIVER_VENT = "driverSeatVentilation"
    const val PASSENGER_HEAT = "passengerSeatHeating"
    const val PASSENGER_VENT = "passengerSeatVentilation"
    const val LEFT_REAR_HEAT = "leftRearSeatHeating"
    const val LEFT_REAR_VENT = "leftRearSeatVentilation"
    const val RIGHT_REAR_HEAT = "rightRearSeatHeating"
    const val RIGHT_REAR_VENT = "rightRearSeatVentilation"
    const val STEERING = "steeringWheelHeating"
    const val MIRROR = "rearviewMirrorHeating"

    /** 一条待确认的下发记录：期望哪个字段变成哪一档。 */
    data class Pending(val field: String, val level: Int, val at: Long)

    /** 合并结果：该显示的状态 + 还没被车端证实的记录。 */
    data class Merged(val status: CarStatus, val pending: List<Pending>)

    /**
     * 指令名 → 待确认记录。非舒适件（锁车、空调、鸣笛…）返回 null。
     *
     * 方向盘档位是 0/1/2，座椅是 0..3，后视镜只有开关两态；
     * 注意别照抄上游写死的 `if (level > 0) 2 else 0` —— 那会把「弱档 1」显示成强档。
     */
    fun targetOf(commandName: String, now: Long): Pending? {
        val field = when {
            commandName.startsWith("driverSeatHeating_") -> DRIVER_HEAT
            commandName.startsWith("driverSeatVentilation_") -> DRIVER_VENT
            commandName.startsWith("passengerSeatHeating_") -> PASSENGER_HEAT
            commandName.startsWith("passengerSeatVentilation_") -> PASSENGER_VENT
            commandName.startsWith("leftRearSeatHeating_") -> LEFT_REAR_HEAT
            commandName.startsWith("leftRearSeatVentilation_") -> LEFT_REAR_VENT
            commandName.startsWith("rightRearSeatHeating_") -> RIGHT_REAR_HEAT
            commandName.startsWith("rightRearSeatVentilation_") -> RIGHT_REAR_VENT
            commandName.startsWith("steeringWheelHeating") -> STEERING
            commandName.startsWith("rearviewMirrorHeating") -> MIRROR
            else -> return null
        }
        val level = when (field) {
            STEERING -> when {
                commandName.endsWith("_off") -> 0
                commandName.endsWith("_on") -> 2
                else -> suffix(commandName).coerceIn(0, 2)
            }
            MIRROR -> when {
                commandName.endsWith("_off") -> 0
                commandName.endsWith("_0") -> 0
                else -> 1
            }
            else -> suffix(commandName).coerceIn(0, 3)
        }
        return Pending(field, level, now)
    }

    /**
     * 把待确认记录盖到车况上，并顺手剔掉「已过期」「车端已追上」的记录。
     *
     * 同一字段只可能有最后一条记录（[com.leapcontrol.app.ui.AppRuntime] 侧按字段去重），
     * 所以这里不去处理重复项；即便有，后面的记录会覆盖前面的，结果一样。
     */
    fun merge(status: CarStatus, pending: List<Pending>, now: Long): Merged {
        if (pending.isEmpty()) return Merged(status, pending)
        var heat = status.seatHeating
        var vent = status.seatVentilation
        var steering = status.steeringWheelHeating
        var mirror = status.rearviewMirrorHeating
        val keep = ArrayList<Pending>(pending.size)
        for (p in pending) {
            // 规则③：窗口之外不再干预
            if (now - p.at >= WINDOW_MS) continue
            // 规则②：车端真值已经等于目标，交还给车端
            if (actualLevel(heat, vent, steering, mirror, p.field) == p.level) continue
            // 规则①：先用目标值顶上
            when (p.field) {
                DRIVER_HEAT -> heat = (heat ?: SeatLevels()).copy(driver = p.level)
                DRIVER_VENT -> vent = (vent ?: SeatLevels()).copy(driver = p.level)
                PASSENGER_HEAT -> heat = (heat ?: SeatLevels()).copy(passenger = p.level)
                PASSENGER_VENT -> vent = (vent ?: SeatLevels()).copy(passenger = p.level)
                LEFT_REAR_HEAT -> heat = (heat ?: SeatLevels()).copy(leftRear = p.level)
                LEFT_REAR_VENT -> vent = (vent ?: SeatLevels()).copy(leftRear = p.level)
                RIGHT_REAR_HEAT -> heat = (heat ?: SeatLevels()).copy(rightRear = p.level)
                RIGHT_REAR_VENT -> vent = (vent ?: SeatLevels()).copy(rightRear = p.level)
                STEERING -> steering = p.level
                MIRROR -> mirror = p.level > 0
                else -> continue
            }
            keep += p
        }
        return Merged(
            status.copy(
                seatHeating = heat,
                seatVentilation = vent,
                steeringWheelHeating = steering,
                rearviewMirrorHeating = mirror,
            ),
            keep,
        )
    }

    /** 车端当前档位；信号缺失返回 null（此时不能判定「已追上」）。 */
    private fun actualLevel(
        heat: SeatLevels?,
        vent: SeatLevels?,
        steering: Int?,
        mirror: Boolean?,
        field: String,
    ): Int? = when (field) {
        DRIVER_HEAT -> heat?.driver
        DRIVER_VENT -> vent?.driver
        PASSENGER_HEAT -> heat?.passenger
        PASSENGER_VENT -> vent?.passenger
        LEFT_REAR_HEAT -> heat?.leftRear
        LEFT_REAR_VENT -> vent?.leftRear
        RIGHT_REAR_HEAT -> heat?.rightRear
        RIGHT_REAR_VENT -> vent?.rightRear
        STEERING -> steering
        MIRROR -> mirror?.let { if (it) 1 else 0 }
        else -> null
    }

    private fun suffix(commandName: String): Int =
        commandName.substringAfterLast('_').toIntOrNull()
            ?: if (commandName.endsWith("_on")) 1 else 0
}
