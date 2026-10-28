package com.leapcontrol.app.ui

/**
 * 一项正在耗电的车载功能。
 *
 * [offCommand] 直接喂给 [com.leapcontrol.app.Commands.build]（如 `acOff` / `driverSeatHeating_0`），
 * 「一键关闭」就是把这些指令逐条下发，不需要在这里拼请求体。
 */
data class PowerDrain(
    /** 稳定标识（UI 的 key / 单测断言用），不要当文案。 */
    val id: String,
    val label: String,
    val detail: String,
    val offCommand: String,
)

/**
 * 后台耗电功能监测。
 *
 * 从一份车况快照里挑出「现在正在运行、会持续吃电池」的舒适类功能：
 * 空调、四座加热 / 通风、方向盘加热、外后视镜加热、车载冰箱。
 *
 * **哨兵模式不算耗电项**：它本身就是防盗功能，被「一键关闭」连带关掉会让车失去守护，
 * 所以宁可漏报也不收进来。
 *
 * 纯函数、只读 [CarStatus]，信号缺失（null）一律不作为「正在运行」，
 * 避免把没上报当开启、误关用户的功能。
 */
object VehiclePowerWatch {

    fun drains(status: CarStatus?): List<PowerDrain> {
        if (status == null) return emptyList()
        val out = mutableListOf<PowerDrain>()

        if (status.acOn == true) {
            out += PowerDrain("ac", "空调", acDetail(status), "acOff")
        }

        // 四座：加热与通风各自独立，档位 0 表示关（信号缺失也是关，不猜）
        val heat = status.seatHeating
        val vent = status.seatVentilation
        addSeat(out, "seatHeatDriver", "主驾加热", heat?.driver, "driverSeatHeating_0")
        addSeat(out, "seatHeatPassenger", "副驾加热", heat?.passenger, "passengerSeatHeating_0")
        addSeat(out, "seatHeatLeftRear", "左后加热", heat?.leftRear, "leftRearSeatHeating_0")
        addSeat(out, "seatHeatRightRear", "右后加热", heat?.rightRear, "rightRearSeatHeating_0")
        addSeat(out, "seatVentDriver", "主驾通风", vent?.driver, "driverSeatVentilation_0")
        addSeat(out, "seatVentPassenger", "副驾通风", vent?.passenger, "passengerSeatVentilation_0")
        addSeat(out, "seatVentLeftRear", "左后通风", vent?.leftRear, "leftRearSeatVentilation_0")
        addSeat(out, "seatVentRightRear", "右后通风", vent?.rightRear, "rightRearSeatVentilation_0")

        // 方向盘加热：0 关 / 1 弱 / 2 强
        val steering = status.steeringWheelHeating ?: 0
        if (steering > 0) {
            out += PowerDrain(
                "steeringWheel",
                "方向盘加热",
                if (steering >= 2) "高档" else "低档",
                "steeringWheelHeating_0",
            )
        }

        if (status.rearviewMirrorHeating == true) {
            out += PowerDrain("mirror", "后视镜加热", "电加热除雾中", "rearviewMirrorHeating_off")
        }

        val fridge = status.fridge
        if (fridge?.enabled == true) {
            val mode = fridge.mode?.label ?: "运行"
            val temp = fridge.temp?.let { "$it℃" }
            out += PowerDrain(
                "fridge",
                "车载冰箱",
                listOfNotNull(mode, temp).joinToString(" · "),
                "fridgeOff",
            )
        }

        return out
    }

    private fun addSeat(
        out: MutableList<PowerDrain>,
        id: String,
        label: String,
        level: Int?,
        offCommand: String,
    ) {
        val lv = level ?: 0
        if (lv <= 0) return
        out += PowerDrain(id, label, "$lv 档", offCommand)
    }

    private fun acDetail(status: CarStatus): String {
        // 只报「设定温度」：胶囊后面还要塞「车内 xx℃」的话，一行只放得下一个，
        // 而车内温度对「要不要关空调」没有决策价值。
        val target = parseReading(status.acTargetTemp)
        return if (target != null) "设定 ${trimZero(target)}℃" else "运行中"
    }

    /**
     * 从读数文本里取数值：车端给的是 "28" / "28.5" / "-8.3" / "28 °C" 这类混合形态，
     * 只取第一段数字（含负号），解析不出来返回 null。
     */
    fun parseReading(text: String?): Double? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching {
            val match = Regex("""-?\d+(\.\d+)?""").find(raw) ?: return null
            match.value.toDouble()
        }.getOrNull()
    }

    /**
     * 电池温度是否偏离舒适区（磷酸铁锂 / 三元锂的通用区间取 0~45℃）。
     * 返回 `偏高` / `偏低` / null（正常或读不到）。只做提示，不参与「一键关闭」。
     */
    fun batteryTempHint(text: String?): String? {
        val celsius = parseReading(text) ?: return null
        return when {
            celsius >= 45.0 -> "偏高"
            celsius <= 0.0 -> "偏低"
            else -> null
        }
    }

    private fun trimZero(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()
}
