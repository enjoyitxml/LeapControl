package com.leapcontrol.app

import com.leapcontrol.app.journal.ChargeWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 充电期间采样间隔的规则。
 *
 * 这块决定两件事：**能不能抓住「充到多少」的最后一格**，以及**会不会把车端唤醒太多次**。
 * 所以两头都要钉：只剩 10 分钟时必须加密到 3 分钟；只剩几个小时时不能还每 3 分钟一次；
 * 拔枪以后必须彻底停掉。
 */
class ChargeWatchTest {

    @Test
    fun `快充满时加密采样`() {
        assertEquals(ChargeWatch.FAST_MS, ChargeWatch.intervalMs(1, 5, 0L))
        assertEquals(ChargeWatch.FAST_MS, ChargeWatch.intervalMs(1, 10, 0L))
    }

    @Test
    fun `常规阶段十分钟一次`() {
        assertEquals(ChargeWatch.NORMAL_MS, ChargeWatch.intervalMs(1, 11, 0L))
        assertEquals(ChargeWatch.NORMAL_MS, ChargeWatch.intervalMs(1, 60, 0L))
    }

    @Test
    fun `还剩几个小时就别那么勤`() {
        assertEquals(ChargeWatch.SLOW_MS, ChargeWatch.intervalMs(1, 61, 0L))
        assertEquals(ChargeWatch.SLOW_MS, ChargeWatch.intervalMs(1, 240, 0L))
        assertEquals(ChargeWatch.LAZY_MS, ChargeWatch.intervalMs(1, 241, 0L))
    }

    @Test
    fun `读不到剩余时间时按常规间隔`() {
        assertEquals(ChargeWatch.NORMAL_MS, ChargeWatch.intervalMs(1, null, 0L))
    }

    /** 直流快充（state=5）与交流（1）走同一套节奏 —— 漏掉 5 会让快充整段记不到起点。 */
    @Test
    fun `直流快充也要采样`() {
        assertEquals(ChargeWatch.FAST_MS, ChargeWatch.intervalMs(5, 3, 0L))
        assertEquals(ChargeWatch.NORMAL_MS, ChargeWatch.intervalMs(5, null, 0L))
    }

    @Test
    fun `预约等待慢点看，暂停也别放弃`() {
        assertEquals(ChargeWatch.IDLE_MS, ChargeWatch.intervalMs(4, null, 0L))
        assertEquals(ChargeWatch.LAZY_MS, ChargeWatch.intervalMs(6, null, 0L))
    }

    @Test
    fun `没插枪或已充满就不看了`() {
        assertNull(ChargeWatch.intervalMs(0, null, 0L))
        assertNull(ChargeWatch.intervalMs(2, null, 0L))
        assertNull(ChargeWatch.intervalMs(3, null, 0L))
        // 读不到充电状态时更不能一直唤醒车端
        assertNull(ChargeWatch.intervalMs(null, null, 0L))
    }

    @Test
    fun `观察超过上限就收手`() {
        // 14 小时还在报「充电中」→ 报文异常，别再每十分钟叫一次
        assertNull(ChargeWatch.intervalMs(1, null, 14 * 60 * 60 * 1000L))
        // 上限之内照常
        assertEquals(ChargeWatch.NORMAL_MS, ChargeWatch.intervalMs(1, null, 3 * 60 * 60 * 1000L))
    }
}
