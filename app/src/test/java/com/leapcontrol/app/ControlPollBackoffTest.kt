package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 控车结果轮询节奏。改动的目的是「省请求但别晚拿到结果」，两条都要守住。
 */
class ControlPollBackoffTest {

    @Test
    fun `首次探询是一秒`() {
        assertEquals(1_000L, ControlPollBackoff.delayMs(0))
    }

    @Test
    fun `节奏单调不减`() {
        var last = 0L
        for (i in 0..8) {
            val d = ControlPollBackoff.delayMs(i)
            assertTrue("第 $i 次 $d 小于上一次 $last", d >= last)
            last = d
        }
    }

    @Test
    fun `尾部收敛到五秒不再继续放大`() {
        // 收敛到一个上限而不是无限退避：窗口只有 20 秒，退得太狠就等于只问一两次
        assertEquals(5_000L, ControlPollBackoff.delayMs(3))
        assertEquals(5_000L, ControlPollBackoff.delayMs(20))
    }

    @Test
    fun `探询时刻落在 1 3 6 11 16 秒`() {
        var elapsed = 0L
        val stamps = mutableListOf<Long>()
        var attempt = 0
        while (true) {
            elapsed += ControlPollBackoff.delayMs(attempt)
            if (elapsed > 20_000L) break
            stamps += elapsed
            attempt++
        }
        assertEquals(listOf(1_000L, 3_000L, 6_000L, 11_000L, 16_000L), stamps)
    }

    @Test
    fun `二十秒窗口内只发五次请求`() {
        // 原来是 20 次：同样等到 20 秒，请求量降到 1/4
        assertEquals(5, ControlPollBackoff.attemptsWithin(20_000L))
    }

    @Test
    fun `窗口放大也不会退化成一点点`() {
        // 官方窗口最长 24 秒：多出来的 4 秒还要再问一次，别把尾巴全丢掉
        val attempts = ControlPollBackoff.attemptsWithin(24_000L)
        assertEquals(6, attempts)
    }

    @Test
    fun `首包时刻不受递进影响`() {
        // 车端执行快的场景（1~3 秒就回）必须和原来一样早拿到结果
        assertEquals(1_000L, ControlPollBackoff.delayMs(0))
        assertEquals(3_000L, ControlPollBackoff.delayMs(0) + ControlPollBackoff.delayMs(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `负数下标直接报错`() {
        ControlPollBackoff.delayMs(-1)
    }
}
