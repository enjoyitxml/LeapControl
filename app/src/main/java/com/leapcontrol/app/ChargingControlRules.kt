package com.leapcontrol.app

/**
 * 充电枪三条指令（192 解锁枪 / 193 开始、停止充电）的结果判定。
 *
 * 官方抓包（2026-10-31）确认了两件事，都在这里落地：
 *
 * 1. **停止充电的入参是 `{"value":"finish"}`，不是 `"stop"`**。车端 OBC 只识别
 *    `start` / `finish`；传 `stop` 时云端照样回 `code=0`（受理），车端却完全不理，
 *    所以界面上表现为「点了没反应」。参数本身在 [Commands.build] 里。
 * 2. **`/appremotectl/query` 的 `data` 字段表示车端执行进度**：`0` = 车端还在执行，
 *    `1` = 执行完成。以前只看 `result`/`code`，网关一受理就宣布成功，
 *    等于把「已收到」当成了「已做完」。
 *
 * 纯函数，不碰网络与状态，单测盯住这层就够了。
 */
object ChargingControlRules {

    /** 车况里的「充电中」取值（`chargeState=1`）。 */
    const val STATE_CHARGING = 1

    /** 车况里的「充电故障」取值，既不算充电中也不算停成功。 */
    const val STATE_FAULT = 3

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

    /** 停止充电的验收目标：车况已离开「充电中」。故障 / 未知 / 缺失都不算成功。 */
    fun stopConfirmed(state: Int?): Boolean = state != null && state in setOf(0, 2, 4, 5, 6)

    /** 开始充电的验收目标：车况确实进入了「充电中」。 */
    fun startConfirmed(state: Int?): Boolean = state == STATE_CHARGING

    /**
     * 下发结果的文案。
     *
     * @param action 指令名（startCharging / stopCharging / unlockCharger）
     * @param confirmed 回执确认车端已执行完成
     * @param state 未确认时补读到的 chargeState（读不到为 null）
     */
    fun resultMessage(action: String, confirmed: Boolean, state: Int?): String = when (action) {
        "unlockCharger" ->
            if (confirmed) "解锁充电枪成功" else "解锁指令已下发，请在车上确认充电枪是否解锁"
        "startCharging" -> when {
            confirmed -> "开始充电成功"
            startConfirmed(state) -> "开始充电成功"
            else -> "开始充电指令已下发，车端暂未进入充电状态"
        }
        else -> when {
            confirmed -> "停止充电成功"
            // 没拿到回执，但车况已经不在充电：以车况为准
            stopConfirmed(state) -> "已停止充电"
            state == STATE_CHARGING -> "停止充电未被执行，车辆仍在充电"
            else -> "停止充电指令已下发，执行结果待确认"
        }
    }
}
