package com.leapcontrol.app

import com.leapcontrol.app.ui.advanceScenePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动态背景的相位推进。
 *
 * 这段逻辑直接决定首页背景动画会不会「跳一下」或「停住不动」，
 * 但它藏在 composable 里的 `withFrameNanos` 回调中，真机上很难复现问题，
 * 所以把数学部分抽成纯函数放在这里测。
 */
class ScenePhaseTest {

    private val cycleNs = 24_000_000_000L

    @Test
    fun `走完一整圈回到原点`() {
        assertEquals(0f, advanceScenePhase(0f, cycleNs), 1e-6f)
    }

    @Test
    fun `半圈正好到零点五`() {
        assertEquals(0.5f, advanceScenePhase(0f, cycleNs / 2), 1e-6f)
    }

    @Test
    fun `跨过一圈时回绕而不是越界`() {
        // 0.9 + 0.25 = 1.15 → 应回绕成 0.15，而不是停在 1.15
        assertEquals(0.15f, advanceScenePhase(0.9f, (cycleNs * 0.25).toLong()), 1e-5f)
    }

    @Test
    fun `两圈后仍是零`() {
        assertEquals(0f, advanceScenePhase(0f, cycleNs * 2), 1e-6f)
    }

    @Test
    fun `时间没走或走反时不推进`() {
        assertEquals(0.42f, advanceScenePhase(0.42f, 0L), 0f)
        assertEquals(0.42f, advanceScenePhase(0.42f, -500_000L), 0f)
    }

    @Test
    fun `连续推进始终落在零到一之间`() {
        var phase = 0.97f
        repeat(500) {
            // 模拟 30fps 的一帧，走 500 帧 ≈ 16.6 秒
            phase = advanceScenePhase(phase, 33_000_000L)
            assertTrue("phase=$phase 越界", phase >= 0f && phase < 1f)
        }
    }

    @Test
    fun `长间隔（切回前台）也能正确回绕`() {
        // 息屏十分钟后回来，相位应该仍然合法，而不是一个巨大的数
        val phase = advanceScenePhase(0.3f, 600_000_000_000L)
        assertTrue("phase=$phase 越界", phase >= 0f && phase < 1f)
    }
}
