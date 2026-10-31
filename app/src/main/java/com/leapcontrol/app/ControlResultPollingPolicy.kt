package com.leapcontrol.app

object ControlResultPollingPolicy {
    const val INITIAL_DELAY_MS = 500L
    const val INTERVAL_MS = 500L
    const val APP_MAX_WAIT_MS = 24_000L

    /**
     * 充电枪类指令（`192` 解锁锁止销 / `193` 起停充电）的回执窗口。
     *
     * 这两条是机械动作，车端确认比锁车这类即时指令慢得多，实测 3~8 秒才回 `result=0`。
     * 旧实现只问 4 次（约 2 秒）必然问不到，于是每次都落到
     * 「指令已下发，请在车辆上确认执行结果」——用户看到的就是"点了没反应"。
     * 10 秒覆盖实测时延，同时不让按钮卡过久（`gunBusy` 期间按钮是禁用的）。
     */
    const val CHARGE_GUN_MAX_WAIT_MS = 10_000L

    val appMaxAttempts: Int = attemptsWithin(APP_MAX_WAIT_MS)
    val chargeGunMaxAttempts: Int = attemptsWithin(CHARGE_GUN_MAX_WAIT_MS)

    fun delayBeforeAttempt(attemptIndex: Int): Long {
        require(attemptIndex >= 0) { "attemptIndex must be non-negative" }
        return if (attemptIndex == 0) INITIAL_DELAY_MS else INTERVAL_MS
    }

    internal fun attemptsWithin(maxWaitMs: Long): Int {
        if (maxWaitMs < INITIAL_DELAY_MS) return 0
        return 1 + ((maxWaitMs - INITIAL_DELAY_MS) / INTERVAL_MS).toInt()
    }
}

/**
 * Non-blocking schedule used only by app climate commands. Values are elapsed
 * time from POST acceptance; MainActivity posts one request per scheduled tick.
 */
object ClimateControlConfirmationSchedule {
    private val resultQueryElapsedMs = longArrayOf(
        1_000L,
        2_000L,
        4_000L,
        8_000L,
        12_000L,
        16_000L,
        20_000L,
        24_000L
    )
    private val telemetryRefreshScheduleMs = longArrayOf(
        1_000L,
        3_000L,
        6_000L,
        12_000L
    )

    val resultQueryAttempts: Int = resultQueryElapsedMs.size
    val telemetryRefreshAttempts: Int = telemetryRefreshScheduleMs.size

    fun resultQueryDelayMs(attemptIndex: Int): Long? = relativeDelay(resultQueryElapsedMs, attemptIndex)

    fun telemetryRefreshElapsedMs(attemptIndex: Int): Long? =
        telemetryRefreshScheduleMs.getOrNull(attemptIndex)

    private fun relativeDelay(schedule: LongArray, attemptIndex: Int): Long? {
        val elapsed = schedule.getOrNull(attemptIndex) ?: return null
        return if (attemptIndex == 0) elapsed else elapsed - schedule[attemptIndex - 1]
    }
}
