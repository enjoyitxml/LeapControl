package com.leapcontrol.app

/**
 * 空调**实时运行状态**的解析：风量（`1941` acAirVolume）与循环方式（`1943` recirculationMode）。
 *
 * ## 为什么不能只读 acSetting
 * 车况里的 `acSetting`（`2183`）是车端的**设定块**，不是当前实际运行状态。2026-11-03 用户实测：
 * 车上风量已经 7 挡，App 却一直显示 2 挡（2 正是 `acOn` 指令里的默认风量）。
 * 官方 App 与上游 leapauto 的当前风量都读 `acAirVolume` 这个实时信号，
 * 所以这里以实时信号为准、`acSetting` 只做兜底。
 *
 * 循环方式同理：`recirculationMode` 是实时信号，`acSetting.circle` 只是设定值。
 *
 * 两个函数都是纯函数（只吃原始值），方便单测。
 */
object AcLiveState {

    /** 车端风量范围：1~7 挡（`Commands.MIN_AC_WIND_LEVEL..MAX_AC_WIND_LEVEL`）。 */
    private val WIND_RANGE = Commands.MIN_AC_WIND_LEVEL..Commands.MAX_AC_WIND_LEVEL

    /**
     * 当前风量（挡位字符串）。实时信号在合法范围内就用它，否则退回 `acSetting` 的设定值。
     *
     * 实时信号为 `0`（空调关着 / 车端不上报）时不采用，避免把「关着」显示成「0 挡」；
     * 两边都取不到才返回 null —— 界面宁可写「未上报」，也不要凭空显示 2 挡。
     */
    fun windLevel(live: Any?, fallback: Any?): String? =
        wholeNumber(live)?.takeIf { it in WIND_RANGE }?.toString()
            ?: wholeNumber(fallback)?.takeIf { it in WIND_RANGE }?.toString()

    /**
     * 当前循环方式：`1`=内循环、`0`=外循环；解析不出就退回 `acSetting` 里的 `in`/`out` 文本。
     */
    fun circle(live: Any?, fallback: String?): String? = when (wholeNumber(live)) {
        1 -> "内循环"
        0 -> "外循环"
        else -> fallback
    }

    private fun wholeNumber(value: Any?): Int? {
        if (value == null || value == org.json.JSONObject.NULL) return null
        return when (value) {
            is Number -> value.toInt()
            is Boolean -> if (value) 1 else 0
            else -> value.toString().trim().toDoubleOrNull()?.toInt()
        }
    }
}
