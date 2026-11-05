package com.leapcontrol.app

/**
 * 电池预热（PTC 电芯加热）当前是否在工作（纯函数，可单测）。
 *
 * 车端信号 1186 `batteryThermalRequest` 只有两个取值：
 * `4` = 正在加热，`0` = 空闲。其余（缺失、`null`、别的新值）一律返回 null ——
 * 「读不到」不等于「关着」，首页那颗按钮宁可不高亮，也不要给出错的亮灭。
 *
 * 口径与上游一致（`leapauto-ref` 的 `BatteryPreheatState`），改这里之前先确认车端没换值。
 */
object BatteryPreheatState {

    private const val HEATING_REQUEST = "4"
    private const val IDLE_REQUEST = "0"

    fun fromRaw(raw: Any?): Boolean? {
        if (raw == null) return null
        return when (raw.toString().trim()) {
            HEATING_REQUEST -> true
            IDLE_REQUEST -> false
            else -> null
        }
    }
}
