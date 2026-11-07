package com.leapcontrol.app

/**
 * 方向盘加热（cmdid=320）的唯一出口。
 *
 * ## 三档语义（两次实车反馈之后定稿）
 *
 * 车端是 **`0` 关 / `1` 弱 / `2` 强** 三档 —— 上游 `API/API.md` 的信号表（`1816`
 * 「方向盘电加热档位：0=关，1=弱档，2=强档」）与指令表（`steeringWheelHeating_1..2` /
 * 「1弱/2强/0关」）两处都这么写，上游 `buildSteeringWheelHeating` 也是 `coerceIn(0, 2)`：
 *  - 开 → 发 `{"level":"2"}`。弱档在部分车型上根本不上热，所以不做「弱/强」给用户挑，
 *    点一下就是强档。
 *  - 关 → 发 `{"level":"0"}`，**只有这一条**。
 *
 * ## 为什么「关」绝不能再补发 `1`（v1.1.5 的事故）
 *
 * v1.1.5 把 `1` 误当成「关」，在回读仍显示开着时补发一条 `{"level":"1"}`。两个错误叠在一起：
 *  1. 车端执行要 5~15 秒，而第一次回读在 2.2 秒后 —— 几乎每次点「关」都会读到「还开着」，
 *     于是几乎每次都补发；
 *  2. `1` 是**弱档 = 还在加热**，补发等于把刚关掉的加热重新打开（最强档降到弱档）。
 *
 * 用户看到的就是「方向盘加热一直显示打开，怎么都关不掉」，而且越点越热。
 *
 * 现在的规则：**关就是发 0**；回读还开着就**重发 0**（幂等，绝不会把车打开），
 * 重发一次仍不生效就如实告诉用户「车端还显示在加热」，让人上车确认。
 * 别再把任何 `1` 写进「关」的路径 —— `SteeringWheelHeatTest` 里有守门断言。
 *
 * ## 状态从哪读
 *
 * 主用 `1816`（`0` 关 / `1` 弱 / `2` 强，`>0` 就是开着）。有的车型只报 `1624`
 * （剩余加热分钟数），**只有 `1816` 缺失时才用它兜底** —— `1816` 一旦报 0 就是真关了，
 * 再拿 `1624` 的残值把它翻回「开」，界面就永远关不掉。
 */
object SteeringWheelHeat {

    const val CMDID = "320"

    /** 开：车端只认这个值能真的加热。 */
    const val LEVEL_ON = "2"

    /** 关：唯一的关闭值。 */
    const val LEVEL_OFF = "0"

    /** 弱档。**只用于读状态**，永远不要拿它当「关」发出去。 */
    const val LEVEL_WEAK = "1"

    const val ON = "steeringWheelHeating_on"
    const val OFF = "steeringWheelHeating_off"

    private const val PREFIX = "steeringWheelHeating"

    /**
     * 指令名 → 是开还是关；不是这条指令返回 null。
     *
     * `_0` 是关；`_1`（弱档）/ `_2`（强档）/ `_on` 都是开。
     */
    fun targetOf(commandName: String?): Boolean? {
        if (commandName == null || !commandName.startsWith(PREFIX)) return null
        return when (commandName) {
            OFF, "${PREFIX}_0" -> false
            ON, "${PREFIX}_1", "${PREFIX}_2" -> true
            else -> null
        }
    }

    /** 组一条开 / 关指令。关只有 `0` 这一个值。 */
    fun build(on: Boolean): ControlCommand = ControlCommand(
        CMDID,
        """{"level":"${if (on) LEVEL_ON else LEVEL_OFF}"}""",
        if (on) "开启方向盘加热" else "关闭方向盘加热",
    )

    /**
     * 车况 → 档位（0 关 / 1 弱 / 2 强）。
     *
     * @param level `1816` 的读数；没上报传 null
     * @param minutes `1624` 剩余加热分钟数；**仅**在 `1816` 缺失时兜底
     */
    fun levelOf(level: Int?, minutes: Int?): Int? {
        if (level != null) return level.coerceIn(0, 2)
        return if ((minutes ?: 0) > 0) 2 else null
    }

    /** 界面口径：`>0` 就是开着（1 = 弱档也算开着）。 */
    fun isOn(level: Int?): Boolean = (level ?: 0) > 0
}
