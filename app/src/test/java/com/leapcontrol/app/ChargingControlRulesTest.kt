package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 充电枪指令的回归测试。
 *
 * 这里盯的是一条真机上踩过的坑：停止充电的入参必须是 `{"value":"finish"}`。
 * 用 `{"value":"stop"}` 时云端照样回 `code=0`，车端 OBC 却完全不动作，
 * 界面表现为「点了没反应」——单测必须在提交前把它拦住。
 */
class ChargingControlRulesTest {

    // ---------------- 指令参数 ----------------

    @Test
    fun `停止充电用 finish 而不是 stop`() {
        val stop = Commands.build("stopCharging")
        assertEquals("193", stop.cmdid)
        assertEquals("""{"value":"finish"}""", stop.stateJson)
        assertEquals("停止充电", stop.label)
        assertNotEquals("""{"value":"stop"}""", stop.stateJson)
    }

    @Test
    fun `开始充电仍是 start，解锁枪仍是 192`() {
        val start = Commands.build("startCharging")
        assertEquals("193", start.cmdid)
        assertEquals("""{"value":"start"}""", start.stateJson)

        val unlock = Commands.build("unlockCharger")
        assertEquals("192", unlock.cmdid)
        assertEquals("""{"operation":"unlock"}""", unlock.stateJson)
    }

    // ---------------- 回执判定 ----------------

    @Test
    fun `网关结果码 0 与 200 才算受理`() {
        assertTrue(ChargingControlRules.gatewayAccepted(0, null))
        assertTrue(ChargingControlRules.gatewayAccepted("0", null))
        assertTrue(ChargingControlRules.gatewayAccepted(null, 200))
        assertTrue(ChargingControlRules.gatewayAccepted("200", null))
        assertFalse(ChargingControlRules.gatewayAccepted(1, 0))
        assertFalse(ChargingControlRules.gatewayAccepted(null, null))
        assertFalse(ChargingControlRules.gatewayAccepted("", null))
    }

    @Test
    fun `data 为 0 表示车端还在执行，不能算完成`() {
        assertFalse(ChargingControlRules.queryConfirmed(0, 0, 0))
        assertFalse(ChargingControlRules.queryConfirmed(0, 0, "0"))
        assertFalse(ChargingControlRules.queryConfirmed("0", "0", 0))

        assertTrue(ChargingControlRules.queryConfirmed(0, 0, 1))
        assertTrue(ChargingControlRules.queryConfirmed(0, 0, "1"))

        // 老网关不带 data：只要网关成功就算完成
        assertTrue(ChargingControlRules.queryConfirmed(0, null, null))
        assertTrue(ChargingControlRules.queryConfirmed(null, 200, null))

        // 网关失败一律不确认，哪怕 data 说完成
        assertFalse(ChargingControlRules.queryConfirmed(1, 0, 1))
        // 只有 code 没有 result 时退回看 code：200 也算受理，data=1 说明已做完
        assertTrue(ChargingControlRules.queryConfirmed(null, 200, 1))
        assertFalse(ChargingControlRules.queryConfirmed(null, null, 1))
    }

    // ---------------- 车况验收 ----------------

    @Test
    fun `停止充电以离开充电中为准`() {
        listOf(0, 2, 4, 6).forEach { state ->
            assertTrue("chargeState=$state 应算已停止", ChargingControlRules.stopConfirmed(state))
        }
        // 1 仍在充电、3 是故障、5 不能证明已停、null 是没读到 —— 都不能报「已停止」
        assertFalse(ChargingControlRules.stopConfirmed(1))
        assertFalse(ChargingControlRules.stopConfirmed(ChargingControlRules.STATE_FAULT))
        assertFalse(ChargingControlRules.stopConfirmed(5))
        assertFalse(ChargingControlRules.stopConfirmed(null))
    }

    @Test
    fun `开始充电以进入充电中为准`() {
        assertTrue(ChargingControlRules.startConfirmed(1))
        listOf(0, 2, 3, 4, 6).forEach { assertFalse(ChargingControlRules.startConfirmed(it)) }
        assertFalse(ChargingControlRules.startConfirmed(null))
    }

    @Test
    fun `车况验收目标与指令一一对应`() {
        assertTrue(ChargingControlRules.telemetryMatches("stopCharging", 0))
        assertTrue(ChargingControlRules.telemetryMatches("stopCharging", 6))
        assertFalse(ChargingControlRules.telemetryMatches("stopCharging", 1))

        assertTrue(ChargingControlRules.telemetryMatches("startCharging", 1))
        assertFalse(ChargingControlRules.telemetryMatches("startCharging", 0))

        // 解锁枪没有可验证的车况信号
        assertFalse(ChargingControlRules.telemetryMatches("unlockCharger", 0))
        assertFalse(ChargingControlRules.telemetryMatches("unlockCharger", 1))
    }

    // ---------------- 文案 ----------------

    @Test
    fun `停止充电没被执行时要说实话`() {
        assertEquals("已停止充电", ChargingControlRules.resultMessage("stopCharging", true, null))
        assertEquals("已停止充电", ChargingControlRules.resultMessage("stopCharging", false, 0))
        assertTrue(
            ChargingControlRules.resultMessage("stopCharging", false, 1).contains("车辆仍在充电"),
        )
        assertTrue(
            ChargingControlRules.resultMessage("stopCharging", false, null).contains("待确认"),
        )
    }

    @Test
    fun `开始与解锁的文案`() {
        assertEquals("开始充电成功", ChargingControlRules.resultMessage("startCharging", true, null))
        assertEquals("开始充电成功", ChargingControlRules.resultMessage("startCharging", false, 1))
        assertTrue(
            ChargingControlRules.resultMessage("startCharging", false, 0).contains("暂未进入充电状态"),
        )
        assertEquals("解锁充电枪成功", ChargingControlRules.resultMessage("unlockCharger", true, null))
        assertTrue(
            ChargingControlRules.resultMessage("unlockCharger", false, null).contains("已下发"),
        )
    }
}
