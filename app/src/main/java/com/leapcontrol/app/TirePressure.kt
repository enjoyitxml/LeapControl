package com.leapcontrol.app

import kotlin.math.roundToInt

/**
 * 胎压的归一化与「是否偏低」判定。纯函数，可单测（见 TirePressureTest）。
 *
 * 单位：接口文档写的是 Double(bar)（如 2.4），但同名字段在不同车型/固件上出现过
 * 240（kPa）与 24（0.1 bar）两种写法，所以按量级自动归一：
 *   - ≤10        → 本身就是 bar
 *   - 10 ~ 60    → 0.1 bar 单位，除以 10
 *   - >60        → kPa，除以 100
 *
 * 偏低判定线取 2.0 bar：GB 26149-2017《乘用车轮胎气压监测系统》要求的欠压报警条件是
 * 「低于推荐值 ×75% − 7kPa」，按零跑常见推荐胎压 2.3~2.5 bar 折算约 1.66~1.81 bar。
 * 这里用 2.0 bar 这条更保守的线做「提前预警」，同时车端自带的欠压/过压告警一律照红色显示，
 * 不跟车端判断打架。
 */
object TirePressure {

    /** 低于该值（bar）即视为胎压不足。 */
    const val LOW_BAR: Double = 2.0

    enum class State { Ok, Low, Unknown }

    /**
     * 把原始胎压值折算成 bar。
     * 无法解析（null / 非数字 / 小于等于 0）时返回 null。
     */
    fun toBar(value: Any?): Double? {
        val d = when (value) {
            null -> return null
            is Number -> value.toDouble()
            else -> value.toString().trim().replace(',', '.').toDoubleOrNull() ?: return null
        }
        if (d <= 0.0) return null
        val bar = when {
            d <= 10.0 -> d
            d <= 60.0 -> d / 10.0
            else -> d / 100.0
        }
        // 0.24 bar 这种量级明显是脏数据，当作没有
        return bar.takeIf { it >= 0.5 }
    }

    /** 折算成 bar 并保留一位小数的展示文本（不带单位，单位由调用处统一拼）。 */
    fun formatBar(value: Any?): String? = toBar(value)?.let { "%.1f".format(it) }

    /**
     * 折算成 kPa 整数的展示文本（不带单位）。
     *
     * App 里所有界面的胎压统一按 kPa 显示（车主看中控和打气泵都是 kPa / bar 两制里优先 kPa，
     * 且整数没有「2.3 还是 2.35」的小数歧义）：2.4 bar → "240"。
     */
    fun formatKpa(value: Any?): String? = toBar(value)?.let { (it * 100).roundToInt().toString() }

    /**
     * 单个车轮的状态。
     * @param pressureText 已经格式化过的胎压文本（如 "2.4"）
     * @param vehicleWarning 车端上报的胎压告警位（欠压或过压）
     */
    fun stateOf(pressureText: String?, vehicleWarning: Boolean): State {
        if (vehicleWarning) return State.Low
        val bar = toBar(pressureText) ?: return State.Unknown
        return if (bar < LOW_BAR) State.Low else State.Ok
    }
}
