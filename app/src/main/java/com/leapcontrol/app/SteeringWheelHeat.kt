package com.leapcontrol.app

/**
 * 方向盘加热（cmdid=320）的唯一出口。
 *
 * ## 为什么它不是「关 / 弱 / 强」三档
 *
 * 2026-11-07 用户反馈「方向盘加热按钮按了没有用，打不开」。查下来不是下发链路断了，
 * 而是**档位语义搞反了**：车端这条指令是**两态开关**，`{"level":"2"}` 开、`{"level":"1"}` 关，
 * `0` 在部分车型上等于「关」、部分车型直接不认。
 *
 * 而我们之前按「0 关 / 1 弱 / 2 强」实现，三个入口（空调页、座椅舒适页、车内俯视图）
 * 第一次点击一律发 `1` —— 车端把它当「关」执行，方向盘当然一点都不热。界面上却是乐观
 * 显示的「弱档」亮点，看着像点着了，于是用户的实际感受就是「按了没用」。
 *
 * 证据来源（三条独立资料指向同一结论）：
 *  - 零跑中国区云 API 协议笔记（C10 实车复核）：「320 方向盘加热 `{"level":"2"}` 开 /
 *    `{"level":"1"}` 关」，且复核表里 320 发 `2` 后信号 `1816` 由 0 → 2；
 *  - 后视镜加热（440）用的是同一套编码习惯：`{"value":"2"}` 开 / `{"value":"1"}` 关；
 *  - 官方 App 里方向盘加热只有「一键备车」里的一个开关，没有档位可选。
 *
 * ## 关闭为什么还要发两次
 *
 * 「0 是不是有效的关」各家说法不一：文档写 0=关，C10 实测 1=关。稳妥做法是**先发 0、
 * 回读信号仍是开着的再补一条 1**（见 `AppRuntime.runSteeringWheelHeat`）。只有真的读到
 * 「还在加热」才补发 —— 信号不上报的车型补发等于瞎猜，宁可在界面上说「已下发」。
 *
 * ## 状态从哪读
 *
 * 主用 `1816`（0 关 / >0 开）。有的车型只报 `1624`（剩余加热分钟数，>0 即开着），
 * 两条都读，`1816` 缺失或为 0 时用 `1624` 兜底 —— 否则这类车的按钮永远显示「关」，
 * 也就没法判断指令到底有没有生效。
 */
object SteeringWheelHeat {

    const val CMDID = "320"

    /** 开。车端只认这个值能真的加热。 */
    const val LEVEL_ON = "2"

    /** 关（多数车型）。 */
    const val LEVEL_OFF = "0"

    /** 关（C10 等把 1 当关的车型）。只在回读确认「还开着」时补发。 */
    const val LEVEL_OFF_ALT = "1"

    const val ON = "steeringWheelHeating_on"
    const val OFF = "steeringWheelHeating_off"

    private const val PREFIX = "steeringWheelHeating"

    /**
     * 指令名 → 是开还是关；不是这条指令返回 null。
     *
     * 兼容老的 `steeringWheelHeating_0/1/2`：`_0` 当关；`_1`/`_2` 一律当**开** ——
     * 老版本把 `_1` 当「弱档」，正是它让第一次点击变成了关闭指令，别再放回去。
     */
    fun targetOf(commandName: String?): Boolean? {
        if (commandName == null || !commandName.startsWith(PREFIX)) return null
        return when (commandName) {
            OFF, "${PREFIX}_0" -> false
            ON, "${PREFIX}_1", "${PREFIX}_2" -> true
            else -> null
        }
    }

    /** 组一条开 / 关指令。 */
    fun build(on: Boolean): ControlCommand = ControlCommand(
        CMDID,
        """{"level":"${if (on) LEVEL_ON else LEVEL_OFF}"}""",
        if (on) "开启方向盘加热" else "关闭方向盘加热",
    )

    /** 关闭的补发：车端把 `1` 当关的那批车型用。 */
    fun buildOffAlt(): ControlCommand = ControlCommand(
        CMDID,
        """{"level":"$LEVEL_OFF_ALT"}""",
        "关闭方向盘加热",
    )

    /**
     * 车况 → 档位（0 关 / >0 开）。
     *
     * @param level `1816` 的读数；没上报传 null
     * @param minutes `1624` 剩余加热分钟数；没上报传 null
     */
    fun levelOf(level: Int?, minutes: Int?): Int? {
        val byMinutes = (minutes ?: 0) > 0
        if (level == null) return if (byMinutes) 2 else null
        if (level > 0) return level.coerceAtMost(2)
        // 1816 说关、1624 说还在加热：以「在加热」为准（用户看到的热是真的）
        return if (byMinutes) 2 else 0
    }

    /** 界面口径：>0 就是开着。 */
    fun isOn(level: Int?): Boolean = (level ?: 0) > 0
}
