package com.leapcontrol.app

import com.leapcontrol.app.ui.CarStatus
import com.leapcontrol.app.ui.FridgeSnapshot
import com.leapcontrol.app.ui.SeatLevels
import com.leapcontrol.app.ui.VehiclePowerWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 后台耗电功能监测（VehiclePowerWatch）：判定、关闭指令、读数解析。 */
class VehiclePowerWatchTest {

    private fun status(
        acOn: Boolean? = null,
        acTargetTemp: String? = null,
        interiorTemp: String? = null,
        seatHeating: SeatLevels? = null,
        seatVentilation: SeatLevels? = null,
        steeringWheelHeating: Int? = null,
        rearviewMirrorHeating: Boolean? = null,
        fridge: FridgeSnapshot? = null,
        sentryOn: Boolean? = null,
    ): CarStatus = CarStatus(
        soc = 60,
        range = null,
        electricRange = null,
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = null,
        interiorTemp = interiorTemp,
        locked = null,
        acOn = acOn,
        acTargetTemp = acTargetTemp,
        seatHeating = seatHeating,
        seatVentilation = seatVentilation,
        steeringWheelHeating = steeringWheelHeating,
        rearviewMirrorHeating = rearviewMirrorHeating,
        fridge = fridge,
        sentryOn = sentryOn,
    )

    @Test
    fun `车况为空时没有耗电项`() {
        assertTrue(VehiclePowerWatch.drains(null).isEmpty())
    }

    @Test
    fun `一切关闭时没有耗电项`() {
        val s = status(
            acOn = false,
            seatHeating = SeatLevels(0, 0, 0, 0),
            seatVentilation = SeatLevels(0, 0, 0, 0),
            steeringWheelHeating = 0,
            rearviewMirrorHeating = false,
            fridge = FridgeSnapshot(enabled = false),
        )
        assertTrue(VehiclePowerWatch.drains(s).isEmpty())
    }

    @Test
    fun `信号缺失不当作开启`() {
        // 全 null：不能因为「读不到」就提示耗电，否则一键关闭会误关用户的功能
        assertTrue(VehiclePowerWatch.drains(status()).isEmpty())
    }

    @Test
    fun `空调开启时给出关闭空调指令`() {
        val drains = VehiclePowerWatch.drains(
            status(acOn = true, acTargetTemp = "24", interiorTemp = "26.5"),
        )
        assertEquals(1, drains.size)
        assertEquals("ac", drains[0].id)
        assertEquals("空调", drains[0].label)
        assertEquals("acOff", drains[0].offCommand)
        assertEquals("设定 24℃", drains[0].detail)
    }

    @Test
    fun `四个座位的加热与通风各自算一项`() {
        val drains = VehiclePowerWatch.drains(
            status(
                seatHeating = SeatLevels(driver = 2, passenger = 1, leftRear = 0, rightRear = 3),
                seatVentilation = SeatLevels(driver = 1, passenger = 0, leftRear = 2, rightRear = 0),
            ),
        )
        assertEquals(
            listOf(
                "seatHeatDriver",
                "seatHeatPassenger",
                "seatHeatRightRear",
                "seatVentDriver",
                "seatVentLeftRear",
            ),
            drains.map { it.id },
        )
        assertEquals("2 档", drains[0].detail)
        assertEquals("driverSeatHeating_0", drains[0].offCommand)
        // 通风不能被写成加热指令（信号串位会让「关通风」变成关加热）
        assertEquals("driverSeatVentilation_0", drains[3].offCommand)
    }

    @Test
    fun `方向盘加热按档位给文案`() {
        val low = VehiclePowerWatch.drains(status(steeringWheelHeating = 1)).single()
        assertEquals("低档", low.detail)
        assertEquals("steeringWheelHeating_0", low.offCommand)

        val high = VehiclePowerWatch.drains(status(steeringWheelHeating = 2)).single()
        assertEquals("高档", high.detail)
    }

    @Test
    fun `后视镜加热与冰箱也能关`() {
        val mirror = VehiclePowerWatch.drains(status(rearviewMirrorHeating = true)).single()
        assertEquals("rearviewMirrorHeating_off", mirror.offCommand)

        val fridge = VehiclePowerWatch.drains(
            status(fridge = FridgeSnapshot(enabled = true, mode = FridgeMode.COLD, temp = 6)),
        ).single()
        assertEquals("fridge", fridge.id)
        assertEquals("fridgeOff", fridge.offCommand)
        assertEquals("制冷 · 6℃", fridge.detail)
    }

    @Test
    fun `哨兵模式不算耗电项`() {
        // 哨兵是安防功能，被「一键关闭」连带关掉会让车失去守护
        assertTrue(VehiclePowerWatch.drains(status(sentryOn = true)).isEmpty())
    }

    @Test
    fun `读数文本能被解析成数值`() {
        assertEquals(28.0, VehiclePowerWatch.parseReading("28")!!, 0.0001)
        assertEquals(28.5, VehiclePowerWatch.parseReading("28.5")!!, 0.0001)
        assertEquals(-8.299, VehiclePowerWatch.parseReading("-8.299")!!, 0.0001)
        assertEquals(368.0, VehiclePowerWatch.parseReading("368 V")!!, 0.0001)
        assertNull(VehiclePowerWatch.parseReading(null))
        assertNull(VehiclePowerWatch.parseReading("--"))
        assertNull(VehiclePowerWatch.parseReading(""))
    }

    @Test
    fun `电池温度越界才给提示`() {
        assertEquals("偏高", VehiclePowerWatch.batteryTempHint("46"))
        assertEquals("偏低", VehiclePowerWatch.batteryTempHint("-2"))
        assertNull(VehiclePowerWatch.batteryTempHint("28"))
        assertNull(VehiclePowerWatch.batteryTempHint(null))
    }
}
