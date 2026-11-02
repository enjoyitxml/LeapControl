package com.leapcontrol.app

/**
 * 充电枪三条指令（192 解锁枪 / 193 开始、停止充电）的结果判定。
 *
 * 官方抓包（2026-10-31）确认了三件事，都在这里落地：
 *
 * 1. **停止充电的入参是 `{"value":"finish"}`，不是 `"stop"`**。车端 OBC 只识别
 *    `start` / `finish`；传 `stop` 时云端照样回 `code=0`（受理），车端却完全不理，
 *    所以界面上表现为「点了没反应」。参数本身在 [Commands.build] 里。
 * 2. **`/appremotectl/query` 的 `data` 字段表示车端执行进度**：`0` = 车端还在执行，
 *    `1` = 执行完成。只看 `result`/`code` 时网关一受理就宣布成功，
 *    等于把「已收到」当成了「已做完」。
 * 3. **光有回执不够，还得看车况**。OBC 断电 + 车况上报有十几秒延迟，
 *    这期间回执一直是 `data=0`。以前问满 10 秒就放弃、只回读一次车况，
 *    于是明明车已经停了，界面还在说「停止充电未被执行，车辆仍在充电」——
 *    用户看到的就是「点了没反应」。现在改成在 [CONFIRM_ROUNDS] 轮里
 *    交替问回执和车况，车况一翻篇立刻按成功返回，离开充电中才算停成。
 *
 * 纯函数，不碰网络与状态，单测盯住这层就够了。
 */
object ChargingControlRules {

    /** 车况里的「充电中」取值（`chargeState=1`）。 */
    const val STATE_CHARGING = 1

    /** 车况里的「充电故障」取值，既不算充电中也不算停成功。 */
    const val STATE_FAULT = 3

    /**
     * 起停充电下发后最多确认几轮，每轮 [CONFIRM_INTERVAL_MS]。
     *
     * 8 轮 × 2 秒 = 16 秒，覆盖实测的「OBC 断电 + 车况上报」时延；
     * 比上游那套「24 秒回执 + 4×2 秒车况」短一些 —— 我们的按钮在等待期间
     * 是禁用的，锁太久用户会更确信「点了没反应」。
     */
    const val CONFIRM_ROUNDS = 8

    /** 两次确认之间的间隔；第一轮之前也等这么久，让车端先受理。 */
    const val CONFIRM_INTERVAL_MS = 2_000L

    /** 网关是否受理（`result` 或 `code` 为 0/200）。缺失一律算未受理。 */
    fun gatewayAccepted(result: Any?, code: Any?): Boolean {
        val value = (result?.takeIf { it != "null" } ?: code)?.toString()?.trim().orEmpty()
        return value == "0" || value == "200"
    }

    /**
     * 查询回执能否判定为「车端已执行完成」。
     *
     * 网关结果必须成功；`data=0` 表示车端仍在执行，不能算完成。
     * `data` 缺失或为 null 时按老网关的行为兼容（只要网关成功就算完成）。
     */
    fun queryConfirmed(result: Any?, code: Any?, data: Any?): Boolean {
        if (!gatewayAccepted(result, code)) return false
        return data?.toString()?.trim() != "0"
    }

    /**
     * 停止充电的验收目标：车况已离开「充电中」。
     *
     * 取值与上游一致：`0` 未插枪 / `2` 充满 / `4` 预约等待 / `6` 暂停。
     * **`5` 不在其中**（上游同样不认），它既不是充电中也不能证明已停；
     * 故障 `3`、未知、缺失都不能报「已停止」。
     */
    fun stopConfirmed(state: Int?): Boolean = state != null && state in setOf(0, 2, 4, 6)

    /** 开始充电的验收目标：车况确实进入了「充电中」。 */
    fun startConfirmed(state: Int?): Boolean = state == STATE_CHARGING

    /** 车况能否证明这条指令已经生效（解锁枪没有可验证的信号，恒为 false）。 */
    fun telemetryMatches(action: String, state: Int?): Boolean = when (action) {
        "startCharging" -> startConfirmed(state)
        "stopCharging" -> stopConfirmed(state)
        else -> false
    }

    /**
     * 下发结果的文案。
     *
     * @param action 指令名（startCharging / stopCharging / unlockCharger）
     * @param confirmed 回执或车况已确认车端执行完成
     * @param state 未确认时补读到的 chargeState（读不到为 null）
     */
    fun resultMessage(action: String, confirmed: Boolean, state: Int?): String = when (action) {
        "unlockCharger" ->
            if (confirmed) "解锁充电枪成功" else "解锁指令已下发，请在车上确认充电枪是否解锁"
        "startCharging" -> when {
            confirmed -> "开始充电成功"
            startConfirmed(state) -> "开始充电成功"
            else -> "指令已发送，车端暂未进入充电状态"
        }
        else -> when {
            confirmed -> "已停止充电"
            // 没拿到回执，但车况已经不在充电：以车况为准
            stopConfirmed(state) -> "已停止充电"
            // 明确还在充：不要糊成「待确认」，让用户知道这条没生效、可以再点一次
            state == STATE_CHARGING -> "指令已发送，车辆仍在充电，请稍后再试"
            else -> "指令已发送，执行结果待确认"
        }
    }
}
