package com.leapbuddy.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 车辆可选配置识别。
 *
 * 这组用例锁住的是「判据本身」，尤其是天窗：不带天窗的车网关**照样下发** key，
 * 只是值是 CAN SNA 占位（15 / 150 / 255）。一旦有人把天窗改成「key 存在即具备」，
 * 这里会立刻红。
 */
class VehicleCapabilityDetectorTest {

    private fun signals(vararg pairs: Pair<String, Any>) = JSONObject().apply {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    /** 基础信号：任意一辆车都会上报的最小组。 */
    private val baseline = signals(
        "soc" to 80,
        "totalMileage" to 12000,
    )

    // ---------------- 天窗：必须看值域，而不是有没有 key ----------------

    @Test
    fun sunroof_absentSignalMeansNoSunroof() {
        assertFalse(VehicleCapabilityDetector.detect(baseline).sunroof)
    }

    @Test
    fun sunroof_validOpeningRangeMeansSupported() {
        // 0 = 全关，10 = 全开，都在有效 0..10 内
        assertTrue(VehicleCapabilityDetector.detect(signals("1718" to 0)).sunroof)
        assertTrue(VehicleCapabilityDetector.detect(signals("1718" to 5)).sunroof)
        assertTrue(VehicleCapabilityDetector.detect(signals("1718" to 10)).sunroof)
    }

    @Test
    fun sunroof_snaPlaceholderMeansUnsupported() {
        // 未选装的车网关返回 SNA 占位值；这些都不能当成「有天窗」
        listOf(15, 150, 255).forEach { raw ->
            assertFalse(
                "SNA 占位值 $raw 不应被判为有天窗",
                VehicleCapabilityDetector.detect(signals("1718" to raw)).sunroof,
            )
        }
    }

    @Test
    fun sunroof_namedSignalUsesSamePolicy() {
        assertTrue(VehicleCapabilityDetector.detect(signals("sunroofOpening" to 3)).sunroof)
        assertFalse(VehicleCapabilityDetector.detect(signals("sunroofOpening" to 15)).sunroof)
    }

    // ---------------- 后装 ECU 类：信号缺失 = 无此硬件 ----------------

    @Test
    fun fridge_detectedOnlyWhenSignalGroupPresent() {
        assertFalse(VehicleCapabilityDetector.detect(baseline).fridge)
        assertTrue(VehicleCapabilityDetector.detect(signals("fridgeSwitch" to 0)).fridge)
        // 数值型 key 也要能识别（网关可能直接下发信号号）
        assertTrue(VehicleCapabilityDetector.detect(signals("10709" to 1)).fridge)
    }

    @Test
    fun fridge_zeroValueStillMeansPresent() {
        // 关键区分点：值是 0 只表示「冰箱关着」，不代表没有冰箱
        assertTrue(VehicleCapabilityDetector.detect(signals("fridgeSwitch" to 0)).fridge)
    }

    @Test
    fun sentry_detectedOnlyWhenSignalPresent() {
        assertFalse(VehicleCapabilityDetector.detect(baseline).sentry)
        assertTrue(VehicleCapabilityDetector.detect(signals("sentryMode" to 0)).sentry)
        assertTrue(VehicleCapabilityDetector.detect(signals("3636" to 1)).sentry)
    }

    // ---------------- 座椅：精确到座位 ----------------

    @Test
    fun seat_frontOnlyCarHasNoRearRows() {
        val caps = VehicleCapabilityDetector.detect(
            signals("2100" to 0, "2118" to 0, "2101" to 0, "2119" to 0),
        )
        assertTrue(caps.seatHeating.hasFront)
        assertFalse(caps.seatHeating.hasRear)
        assertTrue(caps.seatVentilation.hasFront)
        assertFalse(caps.seatVentilation.hasRear)
    }

    @Test
    fun seat_fullSaleSeatsDetectedByTheirOwnSignals() {
        val caps = VehicleCapabilityDetector.detect(
            signals(
                "2100" to 1, "2118" to 1, "1879" to 1, "1880" to 1,
                "2101" to 1, "2119" to 1, "3727" to 1, "3728" to 1,
            ),
        )
        assertTrue(caps.seatHeating.hasRear)
        assertTrue(caps.seatVentilation.hasRear)
        assertTrue(caps.hasAnySeatFeature)
    }

    @Test
    fun steeringWheelAndMirrorAreIndependent() {
        val caps = VehicleCapabilityDetector.detect(signals("1816" to 0, "49" to 0))
        assertTrue(caps.steeringWheelHeating)
        assertTrue(caps.mirrorHeating)
        // 光有方向盘/后视镜加热，座椅本身仍应判为没有
        assertFalse(caps.hasAnySeatFeature)
        assertTrue(caps.hasSeatComfort)
    }

    @Test
    fun rearWindowHeatingDoesNotImplyMirrorHeating() {
        // 后风挡加热几乎全系都有，绝不能拿来当「有后视镜加热」的依据
        assertFalse(VehicleCapabilityDetector.detect(signals("rearWindowHeating" to 1)).mirrorHeating)
    }

    // ---------------- 前备箱：只有 D19 ----------------

    @Test
    fun frunk_onlyD19Series() {
        assertTrue(VehicleCapabilityDetector.detect(baseline, "D19").frunk)
        assertTrue(VehicleCapabilityDetector.detect(baseline, "D19 增程版").frunk)
        assertFalse(VehicleCapabilityDetector.detect(baseline, "C16").frunk)
        // D190 这种不能被 D19 前缀规则误伤（前后都得是非字母数字边界）
        assertFalse(VehicleCapabilityDetector.detect(baseline, "D190").frunk)
    }

    // ---------------- 粘滞合并 ----------------

    @Test
    fun merge_latchesObservedCapabilities() {
        val once = VehicleCapabilityDetector.detect(signals("fridgeSwitch" to 1, "1718" to 3))
        val later = VehicleCapabilityDetector.detect(baseline) // 车休眠，信号临时没了
        assertEquals(once, VehicleCapabilityDetector.merge(once, later))
    }

    @Test
    fun merge_unionAcrossSamples() {
        val withHeat = VehicleCapabilityDetector.detect(signals("2100" to 0))
        val withRearVent = VehicleCapabilityDetector.detect(signals("3727" to 0))
        val merged = VehicleCapabilityDetector.merge(withHeat, withRearVent)
        assertTrue(merged.seatHeating.driver)
        assertTrue(merged.seatVentilation.leftRear)
    }

    @Test
    fun noFeatureAtAllYieldsEmptyCapabilities() {
        val caps = VehicleCapabilityDetector.detect(baseline)
        assertEquals(VehicleCapabilities.EMPTY, caps)
        assertFalse(caps.hasSeatComfort)
    }
}
