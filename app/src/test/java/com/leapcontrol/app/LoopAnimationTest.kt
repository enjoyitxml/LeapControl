package com.leapcontrol.app

import com.leapcontrol.app.ui.advanceLoopPhase
import com.leapcontrol.app.ui.advanceScenePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 循环相位推进的边界测试。
 *
 * 这几个用例不是凑数：循环动画真正会翻车的只有三处 —— 刚好走满一圈、跨过 1.0、
 * 以及时钟/config 异常。全部用纯函数测，不需要跑真机。
 */
class LoopAnimationTest {

    private val tolerance = 1e-4f

    @Test
    fun `一帧推进量与周期成正比`() {
        // 150ms / 1500ms = 0.1
        val phase = advanceLoopPhase(0f, 150_000_000L, 1500)
        assertEquals(0.1f, phase, tolerance)
    }

    @Test
    fun `跨过一圈后回到 0 而不是超过 1`() {
        // 0.95 + 300/1500 = 1.15 → 应折回 0.15
        val phase = advanceLoopPhase(0.95f, 300_000_000L, 1500)
        assertTrue("相位必须落在 [0,1)，实际 $phase", phase >= 0f && phase < 1f)
        assertEquals(0.15f, phase, tolerance)
    }

    @Test
    fun `刚好走满一圈回到 0`() {
        // 1500ms 正好一个周期，不能停在 1.0（那样下一帧会从 1.0 起跳）
        val phase = advanceLoopPhase(0f, 1_500_000_000L, 1500)
        assertTrue("相位必须落在 [0,1)，实际 $phase", phase >= 0f && phase < 1f)
        assertEquals(0f, phase, tolerance)
    }

    @Test
    fun `时钟异常时相位不动`() {
        // delta = 0：同一帧被回调两次
        assertEquals(0.3f, advanceLoopPhase(0.3f, 0L, 1500), tolerance)
        // delta < 0：时钟被回拨，绝不能倒着走
        assertEquals(0.3f, advanceLoopPhase(0.3f, -5_000_000L, 1500), tolerance)
    }

    @Test
    fun `周期配置错误时不推成 NaN`() {
        val phase = advanceLoopPhase(0.3f, 16_000_000L, 0)
        assertEquals(0.3f, phase, tolerance)
        assertTrue(!phase.isNaN())
    }

    @Test
    fun `连续推进不会漂到区间外`() {
        var phase = 0f
        repeat(3000) { i ->
            phase = advanceLoopPhase(phase, 16_000_000L, 1500)
            assertTrue("#$i 相位越界：$phase", phase >= 0f && phase < 1f)
        }
    }

    @Test
    fun `场景的 24 秒周期沿用同一套推进`() {
        // 2400ms / 24000ms = 0.1
        val phase = advanceScenePhase(0f, 2_400_000_000L)
        assertEquals(0.1f, phase, tolerance)
    }
}
