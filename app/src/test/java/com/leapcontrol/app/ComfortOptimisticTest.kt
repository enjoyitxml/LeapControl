package com.leapcontrol.app

import com.leapcontrol.app.ui.CarStatus
import com.leapcontrol.app.ui.ComfortOptimistic
import com.leapcontrol.app.ui.SeatLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 舒适件乐观覆盖：用户 11-03 反馈「方向盘加热点了关闭，最后还是会在弱档」。
 * 车端 1816 回报滞后，下发后的刷新会把界面弹回旧值，这里守住三条规则。
 */
class ComfortOptimisticTest {

    private val t0 = 1_000_000L

    /** CarStatus 前 11 个参数没有默认值，统一在这里给一组合法的基础值。 */
    private fun baseStatus(
        steering: Int? = 1,
        mirror: Boolean? = null,
        heat: SeatLevels? = null,
        vent: SeatLevels? = null,
    ) = CarStatus(
        soc = 50,
        range = "300",
        electricRange = "300",
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = "1000",
        interiorTemp = "20",
        locked = true,
        acOn = false,
        seatHeating = heat,
        seatVentilation = vent,
        steeringWheelHeating = steering,
        rearviewMirrorHeating = mirror,
    )

    // ---------------- 指令名解析 ----------------

    @Test
    fun steeringTargetIsOnOffNotLevels() {
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 0, t0),
            ComfortOptimistic.targetOf("steeringWheelHeating_0", t0),
        )
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 0, t0),
            ComfortOptimistic.targetOf(SteeringWheelHeat.OFF, t0),
        )
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 2, t0),
            ComfortOptimistic.targetOf(SteeringWheelHeat.ON, t0),
        )
        // 老命令名的 `_1` 是车端的弱档（仍在加热），界面上按「开」处理；
        // 别让它变成一次无声的关闭（那是 2026-11-07 第一次「点开没反应」的根因）
        assertEquals(2, ComfortOptimistic.targetOf("steeringWheelHeating_1", t0)?.level)
        assertEquals(2, ComfortOptimistic.targetOf("steeringWheelHeating_2", t0)?.level)
    }

    @Test
    fun seatAndMirrorTargetsParse() {
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.DRIVER_HEAT, 3, t0),
            ComfortOptimistic.targetOf("driverSeatHeating_3", t0),
        )
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.RIGHT_REAR_VENT, 0, t0),
            ComfortOptimistic.targetOf("rightRearSeatVentilation_0", t0),
        )
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.MIRROR, 1, t0),
            ComfortOptimistic.targetOf("rearviewMirrorHeating_on", t0),
        )
        assertEquals(
            ComfortOptimistic.Pending(ComfortOptimistic.MIRROR, 0, t0),
            ComfortOptimistic.targetOf("rearviewMirrorHeating_off", t0),
        )
    }

    @Test
    fun nonComfortCommandsAreIgnored() {
        assertNull(ComfortOptimistic.targetOf("lock", t0))
        assertNull(ComfortOptimistic.targetOf("acOn", t0))
        assertNull(ComfortOptimistic.targetOf("horn", t0))
        assertNull(ComfortOptimistic.targetOf("stopCharging", t0))
    }

    // ---------------- 合并 ----------------

    @Test
    fun staleTelemetryIsOverriddenInsideWindow() {
        val status = baseStatus(steering = 1) // 车端还报弱档
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 0, t0))

        val merged = ComfortOptimistic.merge(status, pending, t0 + 3_000L)

        assertEquals(0, merged.status.steeringWheelHeating)
        assertEquals(1, merged.pending.size)
    }

    @Test
    fun vehicleCatchUpReleasesTheGuard() {
        val status = baseStatus(steering = 0) // 车端真的关了
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 0, t0))

        val merged = ComfortOptimistic.merge(status, pending, t0 + 3_000L)

        assertEquals(0, merged.status.steeringWheelHeating)
        assertTrue("车端已追上就该让位", merged.pending.isEmpty())
    }

    @Test
    fun guardExpiresAfterWindowAndTelemetryWins() {
        val status = baseStatus(steering = 1)
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 0, t0))

        val merged = ComfortOptimistic.merge(
            status,
            pending,
            t0 + ComfortOptimistic.WINDOW_MS + 1,
        )

        assertEquals("窗口过了就得如实显示车端值", 1, merged.status.steeringWheelHeating)
        assertTrue(merged.pending.isEmpty())
    }

    @Test
    fun missingSignalStillShowsTargetThenExpires() {
        // 个别车型根本不报 1816：窗口内先按用户点的显示，过期后回到「未上报」
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.STEERING, 2, t0))

        val inside = ComfortOptimistic.merge(baseStatus(steering = null), pending, t0 + 1_000L)
        assertEquals(2, inside.status.steeringWheelHeating)

        val outside = ComfortOptimistic.merge(
            baseStatus(steering = null),
            pending,
            t0 + ComfortOptimistic.WINDOW_MS + 1,
        )
        assertNull(outside.status.steeringWheelHeating)
    }

    @Test
    fun seatGuardOnlyTouchesItsOwnSlot() {
        val base = baseStatus(
            heat = SeatLevels(driver = 0, passenger = 2, leftRear = 0, rightRear = 0),
            vent = SeatLevels(driver = 1),
        )
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.DRIVER_HEAT, 3, t0))

        val merged = ComfortOptimistic.merge(base, pending, t0 + 500L)

        assertEquals(3, merged.status.seatHeating?.driver)
        assertEquals("别的座位不能跟着动", 2, merged.status.seatHeating?.passenger)
        assertEquals(1, merged.status.seatVentilation?.driver)
    }

    @Test
    fun seatHeatingIsBuiltWhenVehicleReportsNothing() {
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.PASSENGER_VENT, 2, t0))

        val merged = ComfortOptimistic.merge(baseStatus(heat = null, vent = null), pending, t0 + 500L)

        assertEquals(2, merged.status.seatVentilation?.passenger)
    }

    @Test
    fun mirrorOffHidesStaleOnState() {
        val mirrorStatus = baseStatus(mirror = true)
        val pending = listOf(ComfortOptimistic.Pending(ComfortOptimistic.MIRROR, 0, t0))

        val merged = ComfortOptimistic.merge(mirrorStatus, pending, t0 + 2_000L)

        assertEquals(false, merged.status.rearviewMirrorHeating)
    }

    @Test
    fun emptyPendingIsPassedThroughUntouched() {
        val base = baseStatus(steering = 1)
        val merged = ComfortOptimistic.merge(base, emptyList(), t0)
        assertSame(base, merged.status)
        assertTrue(merged.pending.isEmpty())
    }
}
