package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 车辆状态胶囊的判定规则。
 *
 * 每条用例对应一种「车在干什么」的现场：在动、倒车、挂 D 没走、P 挡、充电中、信号全缺。
 * 这些字会被用户当成事实来读，宁可整条不显示，也不能说错。
 */
class DrivingStateRulesTest {

    @Test
    fun `D 挡有车速就是行驶中并带车速`() {
        val badge = DrivingStateRules.badge(gearStatus = "D", speedKmh = 42.0)!!
        assertEquals("行驶中", badge.label)
        assertEquals("42 km/h", badge.detail)
        assertEquals("D", badge.gear)
        assertEquals(DrivingTone.MOVING, badge.tone)
    }

    @Test
    fun `车速带小数时保留一位`() {
        assertEquals("42.5 km/h", DrivingStateRules.badge("D", 42.5)!!.detail)
    }

    @Test
    fun `R 挡行驶单独标倒车`() {
        val badge = DrivingStateRules.badge(gearStatus = "R", speedKmh = 5.0)!!
        assertEquals("倒车", badge.label)
        assertEquals("5 km/h", badge.detail)
        assertEquals(DrivingTone.REVERSING, badge.tone)
    }

    /** 挂着 D 挡但没走：不是「行驶中」，也不是「已驻车」——可能正在等红灯。 */
    @Test
    fun `D 挡车速为零是待行`() {
        val badge = DrivingStateRules.badge(gearStatus = "D", speedKmh = 0.0)!!
        assertEquals("待行", badge.label)
        assertNull(badge.detail)
        assertEquals(DrivingTone.PARKED, badge.tone)
    }

    @Test
    fun `P 挡是已驻车`() {
        val badge = DrivingStateRules.badge(gearStatus = "P", speedKmh = 0.0)!!
        assertEquals("已驻车", badge.label)
        assertEquals("P", badge.gear)
    }

    @Test
    fun `N 挡是空挡`() {
        assertEquals("空挡", DrivingStateRules.badge("N", 0.0)!!.label)
    }

    /** 0.1 / 0.2 这种残值不能算在行驶 —— 否则停车后状态会一直抖「行驶中」。 */
    @Test
    fun `速度残值按静止算`() {
        assertEquals("已驻车", DrivingStateRules.badge("P", 0.4)!!.label)
    }

    @Test
    fun `没有挡位但有车速也算行驶中`() {
        val badge = DrivingStateRules.badge(gearStatus = null, speedKmh = 12.0)!!
        assertEquals("行驶中", badge.label)
        assertNull(badge.gear)
    }

    @Test
    fun `没有挡位但车速为零是已驻车`() {
        assertEquals("已驻车", DrivingStateRules.badge(null, 0.0)!!.label)
    }

    /** 挡位和车速都没有：判不出来，整条不占位（用户选定的口径）。 */
    @Test
    fun `信号全缺时返回 null`() {
        assertNull(DrivingStateRules.badge(gearStatus = null, speedKmh = null))
    }

    @Test
    fun `充电中带剩余分钟`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "P",
            speedKmh = 0.0,
            chargeState = 1,
            chargeRemainMinutes = 42,
        )!!
        assertEquals("充电中", badge.label)
        assertEquals("剩 42 分钟", badge.detail)
        assertEquals(DrivingTone.CHARGING, badge.tone)
    }

    @Test
    fun `充电剩余超过一小时按小时分钟说`() {
        assertEquals(
            "剩 1 小时 15 分钟",
            DrivingStateRules.badge("P", 0.0, chargeState = 1, chargeRemainMinutes = 75)!!.detail,
        )
        assertEquals(
            "剩 2 小时",
            DrivingStateRules.badge("P", 0.0, chargeState = 1, chargeRemainMinutes = 120)!!.detail,
        )
    }

    @Test
    fun `剩余零分钟说即将充满`() {
        assertEquals(
            "即将充满",
            DrivingStateRules.badge("P", 0.0, chargeState = 1, chargeRemainMinutes = 0)!!.detail,
        )
    }

    @Test
    fun `只有格式化文案时也带出剩余时间`() {
        assertEquals(
            "剩 1 小时 23 分",
            DrivingStateRules.badge(
                "P", 0.0,
                chargeState = 1,
                chargeTimeLeft = "1 小时 23 分",
            )!!.detail,
        )
    }

    @Test
    fun `充电完成与预约等待不带剩余时间`() {
        val done = DrivingStateRules.badge("P", 0.0, chargeState = 2, chargeRemainMinutes = 30)!!
        assertEquals("充电完成", done.label)
        assertNull(done.detail)
        assertEquals("预约充电等待", DrivingStateRules.badge("P", 0.0, chargeState = 4)!!.label)
    }

    /** 充电故障不能用「一切正常」的绿点糊弄过去。 */
    @Test
    fun `充电故障走警示色调`() {
        val badge = DrivingStateRules.badge("P", 0.0, chargeState = 3)!!
        assertEquals("充电故障", badge.label)
        assertEquals(DrivingTone.WARNING, badge.tone)
    }

    /** 万一同上报「充电中」与车速，以行驶为准：车在动这件事更紧急。 */
    @Test
    fun `行驶优先于充电`() {
        val badge = DrivingStateRules.badge("D", 30.0, chargeState = 1)!!
        assertEquals("行驶中", badge.label)
        assertEquals(DrivingTone.MOVING, badge.tone)
    }

    @Test
    fun `挡位兼容中文与数字编码`() {
        assertEquals("P", DrivingStateRules.normalizeGear("驻车"))
        assertEquals("R", DrivingStateRules.normalizeGear("倒车"))
        assertEquals("N", DrivingStateRules.normalizeGear("2"))
        assertEquals("D", DrivingStateRules.normalizeGear("d"))
        assertNull(DrivingStateRules.normalizeGear("X"))
        assertNull(DrivingStateRules.normalizeGear(""))
    }

    @Test
    fun `车速原始值可以是带单位的文本`() {
        assertEquals(42.0, DrivingStateRules.parseSpeed("42 km/h")!!, 0.0)
        assertEquals(42.5, DrivingStateRules.parseSpeed(" 42.5 ")!!, 0.0)
        assertNull(DrivingStateRules.parseSpeed("--"))
        assertNull(DrivingStateRules.parseSpeed(null))
        assertNull(DrivingStateRules.parseSpeed("NaN"))
    }

    // ---- 熄火 / 闭锁后挡位字母不可信（2026-11-04 用户反馈「已锁车却显示 N 空挡」）----

    /** 真实现场：车已停好、已上锁，车端 1010 仍报 N。不能直译成「空挡」。 */
    @Test
    fun `已上锁的 N 挡说已驻车且不画挡位徽标`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "N",
            speedKmh = 0.0,
            isShutDown = true,
        )!!
        assertEquals("已驻车", badge.label)
        assertNull(badge.gear)
        assertEquals(DrivingTone.PARKED, badge.tone)
    }

    /** 熄火后车速残留 3km/h 也不能说「行驶中」：车在开绝不可能闭锁。 */
    @Test
    fun `已上锁时忽略残留车速`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "N",
            speedKmh = 3.0,
            isShutDown = true,
        )!!
        assertEquals("已驻车", badge.label)
        assertNull(badge.detail)
    }

    /** 没闭锁但已下电（电源 OFF / EPB 拉起）：同样是驻车，别把 D 挡念成「待行」。 */
    @Test
    fun `已下电的 D 挡也归为已驻车`() {
        val badge = DrivingStateRules.badge(gearStatus = "D", speedKmh = 0.0, isShutDown = true)!!
        assertEquals("已驻车", badge.label)
        assertNull(badge.gear)
    }

    /** P 挡是真实信号，不是猜的，徽标照留。 */
    @Test
    fun `已上锁的 P 挡保留 P 徽标`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "P",
            speedKmh = 0.0,
            isShutDown = true,
        )!!
        assertEquals("已驻车", badge.label)
        assertEquals("P", badge.gear)
    }

    /** 上电、没闭锁、挂 N、人在车里：这时「空挡」是有意义的提醒，不能吞掉。 */
    @Test
    fun `上电状态下的 N 挡仍然说空挡`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "N",
            speedKmh = 0.0,
            isShutDown = false,
        )!!
        assertEquals("空挡", badge.label)
        assertEquals("N", badge.gear)
    }

    /**
     * D / R 挡有车速时，「已下电」不能推翻它。
     *
     * 这条是用户第二次反馈（「车在行驶中却显示已驻车」）的第一重保险：
     * 下电判据再怎么算错，只要挡位说车在动、车速也支持，就必须说「行驶中」。
     */
    @Test
    fun `D 挡有车速时下电判据不能推翻`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "D",
            speedKmh = 60.0,
            isShutDown = true,
        )!!
        assertEquals("行驶中", badge.label)
        assertEquals("60 km/h", badge.detail)
        assertEquals(DrivingTone.MOVING, badge.tone)
    }

    /** 倒车同理：R 挡 + 车速，任何「已下电」都不该把它压成已驻车。 */
    @Test
    fun `R 挡有车速时下电判据不能推翻`() {
        val badge = DrivingStateRules.badge(gearStatus = "R", speedKmh = 4.0, isShutDown = true)!!
        assertEquals("倒车", badge.label)
        assertEquals(DrivingTone.REVERSING, badge.tone)
    }

    /** 挂着 P 却报 60km/h 只能是报文没刷新：P 挡的车速一律当残值。 */
    @Test
    fun `P 挡的车速一律当残值`() {
        val badge = DrivingStateRules.badge(gearStatus = "P", speedKmh = 60.0)!!
        assertEquals("已驻车", badge.label)
        assertNull(badge.detail)
        assertEquals("P", badge.gear)
    }

    /**
     * 行车自动落锁后 `locked == true` 且挡位是 D、车速非零 —— 用户在开车，不是驻车。
     *
     * 上一版拿 locked 压车速，结果这种情况被判成「已驻车」，用户 2026-11-04 第二次反馈。
     * 现在 locked 不再参与胶囊判定（闭锁怎么算由 [VehicleHomeStatus.isVehicleShutDown]
     * 按挡位区分），这里守住「D 挡 + 车速就是行驶中」。
     */
    @Test
    fun `行车自动落锁时依然是行驶中`() {
        val badge = DrivingStateRules.badge(gearStatus = "D", speedKmh = 88.0)!!
        assertEquals("行驶中", badge.label)
        assertEquals("88 km/h", badge.detail)
        assertEquals(DrivingTone.MOVING, badge.tone)
    }

    /** 充电时车必然驻车，挡位字母同样不该念出来。 */
    @Test
    fun `充电中不念熄火后不可信的挡位`() {
        val badge = DrivingStateRules.badge(
            gearStatus = "N",
            speedKmh = 0.0,
            chargeState = 1,
            chargeRemainMinutes = 42,
            isShutDown = true,
        )!!
        assertEquals("充电中", badge.label)
        assertNull(badge.gear)
    }
}
