package com.leapcontrol.app

import com.leapcontrol.app.ui.AcTempDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空调设定温度的解析与「关机态只改草稿」规则。
 *
 * 这几条规则决定了「点一下箭头会不会把空调打开」，值得钉住。
 */
class AcTempDraftTest {

    // ---- parse ----

    @Test
    fun parseReadsPlainInteger() {
        assertEquals(24, AcTempDraft.parse("24"))
    }

    @Test
    fun parseHandlesDecimalFromSignal() {
        // 车端有时候给 "24.0" / "24.5"，取整后仍要能用
        assertEquals(24, AcTempDraft.parse("24.0"))
        assertEquals(25, AcTempDraft.parse("24.6"))
    }

    @Test
    fun parseStripsUnits() {
        assertEquals(26, AcTempDraft.parse("26℃"))
        assertEquals(26, AcTempDraft.parse("26°C"))
        assertEquals(26, AcTempDraft.parse("26°"))
        assertEquals(26, AcTempDraft.parse("  26  "))
    }

    @Test
    fun parseRejectsMissingAndPlaceholderValues() {
        assertNull(AcTempDraft.parse(null))
        assertNull(AcTempDraft.parse(""))
        assertNull(AcTempDraft.parse("   "))
        assertNull(AcTempDraft.parse("--"))
    }

    @Test
    fun parseRejectsOutOfRangeValues() {
        // 0 / 255 之类的占位值不能显示成 "0°"
        assertNull(AcTempDraft.parse("0"))
        assertNull(AcTempDraft.parse("255"))
        assertNull(AcTempDraft.parse("15"))
        assertNull(AcTempDraft.parse("33"))
    }

    // ---- shown ----

    @Test
    fun defaultsTo24WhenNothingIsKnown() {
        assertEquals(24, AcTempDraft.shown(acOn = false, remote = null, pending = null))
        assertEquals(24, AcTempDraft.shown(acOn = true, remote = null, pending = null))
    }

    @Test
    fun runningAcFollowsTheCar() {
        // 开着的时候以车端为准：车机上改了温度，界面要跟上
        assertEquals(21, AcTempDraft.shown(acOn = true, remote = 21, pending = 26))
    }

    @Test
    fun runningAcFallsBackToDraftBeforeCarReports() {
        // 刚点开启、车端还没回报设定温度时，不能把用户选的温度丢掉
        assertEquals(26, AcTempDraft.shown(acOn = true, remote = null, pending = 26))
    }

    @Test
    fun stoppedAcKeepsUserDraft() {
        // 关机态刷新车况不该把用户刚调的数字冲回车端旧值
        assertEquals(18, AcTempDraft.shown(acOn = false, remote = 24, pending = 18))
    }

    @Test
    fun stoppedAcWithoutDraftUsesCarValue() {
        assertEquals(27, AcTempDraft.shown(acOn = false, remote = 27, pending = null))
    }

    // ---- step / canStep ----

    @Test
    fun stepMovesOneDegree() {
        assertEquals(25, AcTempDraft.step(24, 1))
        assertEquals(23, AcTempDraft.step(24, -1))
    }

    @Test
    fun stepClampsToSupportedRange() {
        assertEquals(AcTempDraft.MIN, AcTempDraft.step(AcTempDraft.MIN, -1))
        assertEquals(AcTempDraft.MAX, AcTempDraft.step(AcTempDraft.MAX, 1))
    }

    @Test
    fun canStepStopsAtBothEnds() {
        assertFalse(AcTempDraft.canStep(AcTempDraft.MIN, -1))
        assertTrue(AcTempDraft.canStep(AcTempDraft.MIN, 1))
        assertFalse(AcTempDraft.canStep(AcTempDraft.MAX, 1))
        assertTrue(AcTempDraft.canStep(AcTempDraft.MAX, -1))
    }

    @Test
    fun supportedRangeIs16To32() {
        assertEquals(16, AcTempDraft.MIN)
        assertEquals(32, AcTempDraft.MAX)
    }

    // ---- sendOnArrow ----

    @Test
    fun arrowSendsOnlyWhenAcIsRunning() {
        // 这是本次修复的核心：空调关着时调温不能下发（否则会顺手开空调）
        assertFalse(AcTempDraft.sendOnArrow(acOn = false))
        assertTrue(AcTempDraft.sendOnArrow(acOn = true))
    }
}
