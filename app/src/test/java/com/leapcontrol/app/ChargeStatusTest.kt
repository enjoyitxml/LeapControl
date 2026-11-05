package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 充电状态的判定口径。
 *
 * 两条都是用户报过的错：
 * 1. 预约了充电但**没插枪**的车，充电中心冒出一排「开始充电 / 解锁充电枪」——
 *    因为 `chargeState=4` 曾被无条件当成「插着枪」（上游 4.5.12 收紧）。
 * 2. 开了健康充电（上限 90%）充到 90% 停充，车上报 `chargeState=2`，
 *    界面却写「已充满」——`2` 只代表这次充电结束，不代表到顶（上游 4.4.2 修法）。
 */
class ChargeStatusTest {

    // ---------------- 插枪判定 ----------------

    @Test
    fun `交流或直流输入为真就是插着枪`() {
        // 有硬证据时，chargeState 说什么都不影响结论
        assertTrue(ChargeStatus.gunConnected("1", "0", 0))
        assertTrue(ChargeStatus.gunConnected("0", "1", 0))
        assertTrue(ChargeStatus.gunConnected(true, false, null))
        assertTrue(ChargeStatus.gunConnected("1", "1", 3))
    }

    @Test
    fun `明确未插枪时预约等待不再算插枪`() {
        // 这是收紧的核心：两路都报 0 = 车明确说没插枪
        assertFalse(ChargeStatus.gunConnected("0", "0", 4))
        assertFalse(ChargeStatus.gunConnected("0", "0", 5))
        assertFalse(ChargeStatus.gunConnected("0", "0", 0))
        assertFalse(ChargeStatus.gunConnected("0", "0", 3))
        assertFalse(ChargeStatus.gunConnected("0", "0", null))
        // 插着枪才可能出现的那几个状态仍然认
        assertTrue(ChargeStatus.gunConnected("0", "0", 1))
        assertTrue(ChargeStatus.gunConnected("0", "0", 2))
        assertTrue(ChargeStatus.gunConnected("0", "0", 6))
    }

    @Test
    fun `信号缺失时退回宽松口径`() {
        // 车型不上报 ac / dc：只能靠 chargeState 猜，宁可多显示按钮也别让按钮整排消失
        assertTrue(ChargeStatus.gunConnected(null, null, 4))
        assertTrue(ChargeStatus.gunConnected(null, null, 5))
        assertTrue(ChargeStatus.gunConnected(null, null, 6))
        assertFalse(ChargeStatus.gunConnected(null, null, 0))
        assertFalse(ChargeStatus.gunConnected(null, null, 3))
        assertFalse(ChargeStatus.gunConnected(null, null, null))
    }

    @Test
    fun `JSON 与 Map 两条入口口径一致`() {
        val raw = JSONObject("""{"acInputSlowCharge":0,"dcInputFastCharge":0,"chargeState":4}""")
        assertFalse(ChargeStatus.isGunConnected(raw))
        assertFalse(
            ChargeStatus.isGunConnected(
                mapOf("acInputSlowCharge" to 0, "dcInputFastCharge" to 0, "chargeState" to 4)
            )
        )
        val plugged = JSONObject("""{"acInputSlowCharge":1,"chargeState":0}""")
        assertTrue(ChargeStatus.isGunConnected(plugged))
        assertTrue(ChargeStatus.isGunConnected(mapOf("acInputSlowCharge" to 1, "chargeState" to 0)))
        assertFalse(ChargeStatus.isGunConnected(null as JSONObject?))
        assertFalse(ChargeStatus.isGunConnected(null as Map<String, Any?>?))
    }

    // ---------------- 「已充满」判定 ----------------

    @Test
    fun `充电结束但没到一百不算已充满`() {
        assertFalse(ChargeStatus.fullConfirmed(2, 90))
        assertFalse(ChargeStatus.fullConfirmed(2, 99))
        assertTrue(ChargeStatus.fullConfirmed(2, 100))
        // 电量未知时也不敢说满了
        assertFalse(ChargeStatus.fullConfirmed(2, null))
    }

    @Test
    fun `只有充电结束状态才可能算充满`() {
        assertFalse(ChargeStatus.fullConfirmed(1, 100))
        assertFalse(ChargeStatus.fullConfirmed(0, 100))
        assertFalse(ChargeStatus.fullConfirmed(null, 100))
    }

    // ---------------- 其余文案不受影响 ----------------

    @Test
    fun `状态文案映射`() {
        assertEquals("未插枪", ChargeStatus.label(0))
        assertEquals("充电中", ChargeStatus.label(1))
        assertEquals("充电完成", ChargeStatus.label(2))
        assertEquals("预约充电等待", ChargeStatus.label(4))
        assertEquals("充电暂停", ChargeStatus.label(6))
        assertNull(ChargeStatus.state(JSONObject("{}")))
    }
}
