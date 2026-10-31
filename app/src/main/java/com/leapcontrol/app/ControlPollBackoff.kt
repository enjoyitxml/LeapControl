package com.leapcontrol.app

/**
 * 控车指令「等车端回执」的轮询节奏。
 *
 * ## 为什么是递进而不是匀速
 * 原来的实现是**每 1 秒问一次、问满 20 秒**，也就是一条指令最多 20 次请求。
 * 但实测车端的回执几乎都落在前 6 秒（T-Box 本来就醒着的话 1~3 秒就回），
 * 后面那十几次请求全是空问 —— 用户感觉不到任何区别，只是白白多花流量。
 *
 * 车端越晚回，说明越可能在等 T-Box 唤醒（慢场景），这种时候 1 秒频次也问不出更早的结果，
 * 所以放宽到 5 秒一档。累计落在 20 秒窗口内的探询约 5 次，请求量降到原来的 1/4，
 * 而**最早能拿到结果的时刻不变**（第 1 次仍是 1 秒）。
 *
 * ## 与 [ControlResultPollingPolicy] 的区别
 * 那边管的是充电枪 / 空调这类机械动作（500ms 一档、窗口 10~24 秒），
 * 机械件确认慢且需要密一点才抓得住瞬间状态；这里是锁车、后备箱这类即时指令，
 * 递进就够了。两者不要合并成一个常量表。
 */
object ControlPollBackoff {
    /** 首次探询：官方约定首包 1 秒，保持不动，免得慢半拍。 */
    const val FIRST_MS = 1_000L

    /** 尾部档位：再往后都是「可能压根不会回」，不值得继续密集问。 */
    const val TAIL_MS = 5_000L

    /**
     * 第 [attemptIndex] 次探询之前该睡多久（0 起算）。
     *
     * 累加（1+2+3+5+5…）在 20 秒窗口里落在 1 / 3 / 6 / 11 / 16 秒，
     * 共 5 次；第 6 次累计 21 秒已经越过 deadline，循环会自己退出。
     */
    fun delayMs(attemptIndex: Int): Long {
        require(attemptIndex >= 0) { "attemptIndex must be non-negative" }
        return when (attemptIndex) {
            0 -> FIRST_MS
            1 -> 2_000L
            2 -> 3_000L
            else -> TAIL_MS
        }
    }

    /** 在 [windowMs] 窗口内实际会发出的探询次数（含首次）。 */
    fun attemptsWithin(windowMs: Long): Int {
        var elapsed = 0L
        var attempts = 0
        while (true) {
            val next = elapsed + delayMs(attempts)
            if (next > windowMs) return attempts
            elapsed = next
            attempts++
        }
    }
}
