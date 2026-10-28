package com.leapcontrol.app.ui

import kotlin.math.roundToInt

/**
 * 空调设定温度的「待下发草稿」规则。
 *
 * 为什么需要草稿：车端接口里**没有单独的「改温度」指令**——`Commands.buildAc()` 发的是
 * `170 开空调`，温度只是它的一个字段。所以以前在首页点 `−/+` 调一格温度，实际发出去的
 * 是一条「开空调（xx℃）」，空调关着的时候会被直接打开。用户的预期是：
 * 调温度就只是调温度，点右侧风扇开关才真正开空调。
 *
 * 于是把温度拆成两层：
 *  - **车端真值**（`CarStatus.acTargetTemp`）：空调运行时以它为准；
 *  - **本地草稿**（pendingTemp）：空调关着时用户改的值，只存在界面上，不发指令；
 *    点风扇开启空调时，才带着这个温度下发。
 *
 * 这里只放纯计算，不碰 Android，方便直接跑 JVM 单测。
 */
object AcTempDraft {

    /** 车端支持的设定温度范围（与 [com.leapcontrol.app.Commands.buildAc] 的校验一致）。 */
    const val MIN = 16
    const val MAX = 32

    /** 没读到车端温度时的默认设定值。 */
    const val DEFAULT = 24

    /**
     * 解析车端上报的设定温度。
     *
     * 信号实际格式不统一：`"24"` / `"24.0"` / `"24℃"` / `"24.0°C"` 都见过，
     * 车端没上报时可能是空串、`"--"`、`"0"`。取整后落在 [MIN]..[MAX] 之外的
     * 一律当作「没有值」，交给调用处回落默认 24，避免界面出现 `0°` 这种读数。
     */
    fun parse(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val cleaned = text
            .removeSuffix("℃")
            .removeSuffix("°C")
            .removeSuffix("°")
            .removeSuffix("C")
            .trim()
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        val rounded = value.roundToInt()
        return if (rounded in MIN..MAX) rounded else null
    }

    /**
     * 界面上该显示多少度。
     *
     * @param acOn 空调当前是否开启（含「点击后意图开启、车端尚未回报」的那几秒）
     * @param remote 车端上报的设定温度
     * @param pending 用户刚点出来的目标温度（点击即下发，等车端回报的那几秒就是它顶着）
     *
     * **草稿优先**：点一下加减号，界面必须立刻变成新温度，否则用户看到的是「点了没反应」，
     * 要等两三秒车端回报才有变化。草稿什么时候交还给车端由调用方判断（HomeScreen 里
     * 车端回报同值 = 生效、回报了别的值 = 有人在车机上改了，两种情况都会清掉草稿）。
     * [acOn] 保留在签名里是为了调用处语义清楚，判定不再依赖它。
     */
    @Suppress("UNUSED_PARAMETER")
    fun shown(acOn: Boolean, remote: Int?, pending: Int?): Int = when {
        pending != null -> pending
        remote != null -> remote
        else -> DEFAULT
    }

    /**
     * 在 [current] 基础上加减，并夹到 [MIN]..[MAX]。
     *
     * 不用 `coerceIn`：Int 上偶发重载歧义（项目里踩过），这里用 maxOf/minOf 更稳。
     */
    fun step(current: Int, delta: Int): Int = maxOf(MIN, minOf(MAX, current + delta))

    /** 还能不能继续降低 / 升高，用于箭头置灰。 */
    fun canStep(current: Int, delta: Int): Boolean =
        if (delta < 0) current > MIN else current < MAX
}
