package com.leapcontrol.app

/**
 * 方向盘加热（cmdid=320）的唯一出口。这一版**完全照上游 leapauto v4.3.6** 实现。
 *
 * ## 上游怎么做（三处互证）
 *
 * - `API/API.md` 指令表：`steeringWheelHeating_1..2` → `{"level":"2"}`，注明「1弱/2强/0关」；
 *   信号表 `1816` = `steeringWheelHeating` Int(0..2)，`0`=关、`1`=弱档、`2`=强档。
 * - `Models.buildSteeringWheelHeating(level)` = `level.coerceIn(0, 2)`，payload `{"level":"$cleanLevel"}`。
 * - `LeapAutoScreen` 的开关只发两条：
 *   `onControl(if (it) "steeringWheelHeating_2" else "steeringWheelHeating_0")`。
 *
 * 结论：**开 = `2`，关 = `0`，一次点击只发一条**。`1` 是弱档（仍在加热），上游从来不拿它当「关」。
 *
 * ## 上游怎么给结果
 *
 * 下发前先把界面乐观地翻到目标档位（`optimisticSteeringWheelHeating = lvl > 0`，
 * `comfortProtected` 保护 15 秒），网关受理就显示「方向盘加热已关闭」，
 * 之后按 1.0 / 1.8 / 2.5 秒阶梯静默刷新车况，让真值自然追上。
 * **上游不回读 `1816` 去判断成败，也绝不补发第二条指令。**
 *
 * 本项目的等价实现是 [com.leapcontrol.app.ui.ComfortOptimistic]（窗口 25 秒，因为
 * `AppRuntime.control()` 是挂起等网关回执的），方向盘加热走的是**通用 control() 路径**。
 * 唯一差别：我们在 `sendControl` 被网关收下之后才写乐观值，比上游晚一两秒 ——
 * 这样发送失败时界面不会假装已经关掉。
 *
 * ## 前两次事故（别再写回来）
 *
 * - v1.1.5：把 `1` 当成「关」的补发值，回读没关掉就补 `{"level":"1"}` —— 那是弱档，
 *   等于把刚关掉的加热重新打开，用户「怎么都关不掉」。
 * - v1.1.6：补发改成重发 `0`，但**仍然在补发**，而且「关」的分支提前 return，
 *   跳过了 `rememberComfortTarget` —— 界面从点下去那一刻起就没有乐观值，
 *   车况刷新一直把旧值（还开着）盖回来，看起来还是关不掉。
 *
 * ## 状态从哪读
 *
 * 只读 `1816`。`1624`（`steeringWheelHeaterMinutes`，剩余加热分钟数）上游只当独立字段，
 * **从不拿来判开关**：关掉后它常有残值，拿它兜底会让界面永远显示「开」。
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

    /** `1816` → 档位（0 关 / 1 弱 / 2 强）；信号没上报返回 null。 */
    fun levelOf(level: Int?): Int? = level?.coerceIn(0, 2)

    /** 界面口径：`>0` 就是开着（1 = 弱档也算开着）。 */
    fun isOn(level: Int?): Boolean = (level ?: 0) > 0
}
