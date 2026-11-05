package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 位置校准的算法。
 *
 * 这块决定的是「用户拖完车标，钉子到底落在哪」。算错了不会崩，但会让人以为
 * 校准功能是坏的 —— 而且错误方向还看不出来（钉子照样跟着手指走，只是每次都差一点点）。
 *
 * 基准点用上海（31.2°N, 121.5°E）：经度方向 1 度约 95.2 km（= 111.32 × cos31.2），
 * 纬度方向 1 度约 111.3 km。所有用例都按这两个数验算。
 */
class LocationCalibrationTest {

    private val lat = 31.2
    private val lng = 121.5

    @Test
    fun `没校准就原样返回`() {
        val (outLat, outLng) = LocationCalibrator.apply(lat, lng, LocationCalibration.NONE)
        assertEquals(lat, outLat, 0.0)
        assertEquals(lng, outLng, 0.0)
    }

    @Test
    fun `向东一千米经度增加约 0 0105 度`() {
        val (outLat, outLng) = LocationCalibrator.shift(lat, lng, east = 1000, north = 0)
        assertEquals(lat, outLat, 1e-9)
        // 1000 / (111320 × cos31.2) ≈ 0.010505
        assertEquals(0.010505, outLng - lng, 1e-5)
    }

    @Test
    fun `向北五百米只动纬度`() {
        val (outLat, outLng) = LocationCalibrator.shift(lat, lng, east = 0, north = 500)
        assertEquals(500.0 / LocationCalibrator.METERS_PER_DEGREE_LAT, outLat - lat, 1e-9)
        assertEquals(lng, outLng, 0.0)
    }

    @Test
    fun `同样的东移在高纬度要走更多经度`() {
        val (_, lowLng) = LocationCalibrator.shift(20.0, lng, east = 1000, north = 0)
        val (_, highLng) = LocationCalibrator.shift(45.7, lng, east = 1000, north = 0)
        // 哈尔滨 cos 更小 → 同样 1000 米对应的经度差更大（同样的经度差走的地面更少）
        assertTrue("高纬度的经度位移应该更大：${highLng - lng} vs ${lowLng - lng}", highLng - lng > lowLng - lng)
    }

    @Test
    fun `位移和反算能对上`() {
        val (shiftedLat, shiftedLng) = LocationCalibrator.shift(lat, lng, east = 320, north = -140)
        val (east, north) = LocationCalibrator.deltaEastNorth(lat, lng, shiftedLat, shiftedLng)
        // 只允许 ±1 米：两个方向的换算系数不完全可逆（经度按中纬度算），
        // 但也不该差出量级来
        assertTrue("东移反算应约为 320，实际 $east", abs(east - 320) <= 1)
        assertTrue("北移反算应约为 -140，实际 $north", abs(north + 140) <= 1)
    }

    @Test
    fun `拖动量按米折算`() {
        // 向北挪 0.001 度 ≈ 111 米；向东挪 0.001 度 ≈ 95 米
        val (east, north) = LocationCalibrator.deltaEastNorth(lat, lng, lat + 0.001, lng + 0.001)
        assertTrue("0.001 度经度 ≈ 95 米，实际 $east", abs(east - 95) <= 2)
        assertTrue("0.001 度纬度 ≈ 111 米，实际 $north", abs(north - 111) <= 2)
    }

    @Test
    fun `先换算再叠加手动偏移`() {
        val calibration = LocationCalibration(convert = true, east = 500, north = 0)
        val (convertedLat, convertedLng) = ChinaCoordinate.wgs84ToGcj02(lat, lng)
        val expected = LocationCalibrator.shift(convertedLat, convertedLng, east = 500, north = 0)
        val actual = LocationCalibrator.apply(lat, lng, calibration)
        assertEquals(expected.first, actual.first, 1e-9)
        assertEquals(expected.second, actual.second, 1e-9)
    }

    @Test
    fun `换算单独打开也算改过`() {
        val calibration = LocationCalibration(convert = true)
        assertTrue(calibration.isIdentity.not())
        assertFalse(calibration.hasOffset)
        assertEquals(0, calibration.distanceMeters)
    }

    @Test
    fun `合位移按勾股算`() {
        assertEquals(50, LocationCalibration(east = 30, north = 40).distanceMeters)
    }

    @Test
    fun `描述文案带方向`() {
        assertEquals("未校准", LocationCalibrator.describe(0, 0))
        assertEquals("向东 120 米", LocationCalibrator.describe(120, 0))
        assertEquals("向西 120 米 · 向南 40 米", LocationCalibrator.describe(-120, -40))
        assertEquals("向北 40 米", LocationCalibrator.describe(0, 40))
    }

    @Test
    fun `地图角上的短文案`() {
        // 未校准那句必须自带动作：早先是「位置不准？校准」，读起来像说明而不是按钮
        assertEquals(
            "位置不准？点这里校准",
            LocationCalibrator.shortLabel(LocationCalibration.NONE),
        )
        assertEquals("已换算坐标系", LocationCalibrator.shortLabel(LocationCalibration(convert = true)))
        assertEquals(
            "已校准 50m",
            LocationCalibrator.shortLabel(LocationCalibration(east = 30, north = 40)),
        )
    }

    @Test
    fun `落盘格式能来回`() {
        val calibration = LocationCalibration(convert = true, east = -1200, north = 340)
        assertEquals(calibration, LocationCalibrator.parse(LocationCalibrator.serialize(calibration)))
    }

    @Test
    fun `脏数据一律退回未校准`() {
        listOf(null, "", "  ", "abc", "1|120", "1|x|40", "1|120|y", "1|120|40|7").forEach { raw ->
            assertEquals("解析 $raw 应该退回未校准", LocationCalibration.NONE, LocationCalibrator.parse(raw))
        }
    }

    @Test
    fun `超范围的偏移会被夹住`() {
        val parsed = LocationCalibrator.parse("1|99999|-99999")
        assertEquals(LocationCalibrator.MAX_OFFSET_METERS, parsed.east)
        assertEquals(-LocationCalibrator.MAX_OFFSET_METERS, parsed.north)
    }

    @Test
    fun `非有限坐标不参与计算`() {
        val (outLat, outLng) = LocationCalibrator.apply(
            Double.NaN,
            lng,
            LocationCalibration(east = 500, north = 500),
        )
        assertTrue(outLat.isNaN())
        assertEquals(lng, outLng, 0.0)
    }
}
