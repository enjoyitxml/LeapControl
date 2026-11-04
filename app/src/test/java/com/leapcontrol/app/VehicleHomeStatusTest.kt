package com.leapcontrol.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「车辆是否已下电 / 人已离车」的判定。
 *
 * 这个布尔值直接决定首页状态胶囊说「行驶中」还是「已驻车」，说错一次用户就会来问。
 * 2026-11-04 连着两条反馈都出在这里：
 * 1. 车已停好锁车，胶囊显示「N 空挡」；
 * 2. 修完之后变成行驶中也显示「已驻车」—— 因为把 `vehicleState == 1` 当成了下电。
 */
class VehicleHomeStatusTest {

    @Test
    fun `闭锁且非行驶挡位算离车`() {
        assertTrue(VehicleHomeStatus.isVehicleShutDown(locked = true, gearStatus = "P"))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(locked = true, gearStatus = "N"))
        // 车端熄火后有时不再刷挡位信号，挡位缺失也得认闭锁
        assertTrue(VehicleHomeStatus.isVehicleShutDown(locked = true, gearStatus = null))
    }

    /** 行车自动落锁：车一开起来就自动上锁，这时人明明在车上。 */
    @Test
    fun `闭锁但挂 D 或 R 挡不算离车`() {
        assertFalse(VehicleHomeStatus.isVehicleShutDown(locked = true, gearStatus = "D"))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(locked = true, gearStatus = "R"))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(locked = true, gearStatus = "D挡"))
    }

    /**
     * 核心回归：文档写 `1944 = 1` 是 Ready / 行车状态。
     *
     * 上游把这个 1 当成「已下电」，于是行驶中的车被判成驻车 —— 用户第二次反馈的根因。
     */
    @Test
    fun `vehicleState 为 1 是行车就绪不是下电`() {
        assertFalse(VehicleHomeStatus.isVehicleShutDown(vehicleState = 1))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(vehicleState = "1"))
        // 未知取值（3、2、9…）也不猜成下电，宁可判不出来
        assertFalse(VehicleHomeStatus.isVehicleShutDown(vehicleState = 3))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(vehicleState = 2))
    }

    /** 文档明确 `1944 = 0` 是下电休眠，这一条要认。 */
    @Test
    fun `vehicleState 为 0 是下电休眠`() {
        assertTrue(VehicleHomeStatus.isVehicleShutDown(vehicleState = 0))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(vehicleState = "0"))
    }

    /** BCM 电源 OFF 是最硬的「人已离车」信号，压过一切。 */
    @Test
    fun `BCM 电源 OFF 算离车`() {
        assertTrue(VehicleHomeStatus.isVehicleShutDown(bcmKeyPositionOn3 = "0"))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(bcmKeyPositionOn3 = 0))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(bcmKeyPositionOn3 = "1"))
    }

    /** EPB 拉起 = 驻车制动生效，说「已驻车」没错。 */
    @Test
    fun `电子手刹拉起算驻车`() {
        assertTrue(VehicleHomeStatus.isVehicleShutDown(parkingBrakeState = "1"))
        assertTrue(VehicleHomeStatus.isVehicleShutDown(parkingBrakeState = 1))
        assertFalse(VehicleHomeStatus.isVehicleShutDown(parkingBrakeState = "0"))
    }

    /** 什么信号都没有时不能瞎猜：宁可不显示状态。 */
    @Test
    fun `信号全缺时不判为离车`() {
        assertFalse(VehicleHomeStatus.isVehicleShutDown())
    }

    /** 组合场景：行车中（落锁 + D 挡 + 整车 Ready）必须判成「没离车」。 */
    @Test
    fun `行驶中多信号组合不判离车`() {
        assertFalse(
            VehicleHomeStatus.isVehicleShutDown(
                bcmKeyPositionOn3 = "1",
                vehicleState = 1,
                parkingBrakeState = "0",
                locked = true,
                gearStatus = "D",
            )
        )
    }

    @Test
    fun `行驶挡位识别覆盖字母中文与数字编码`() {
        listOf("D", "d", "D挡", "DRIVE", "前进", "3", "R", "r", "R挡", "倒车", "1")
            .forEach { assertTrue("$it 应识别为行驶挡位", VehicleHomeStatus.isDrivingGear(it)) }
        listOf("P", "N", "P挡", "空挡", "", null, "x")
            .forEach { assertFalse("$it 不应识别为行驶挡位", VehicleHomeStatus.isDrivingGear(it)) }
    }
}
