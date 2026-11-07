package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 坐标系换算与「车端到底报的哪一套」的自动判定。
 *
 * 这块决定的是 B10 / C01 用户报的「定位偏几百米」能不能一次修好：
 *  - 换算公式写错 → 打开开关反而更偏，用户会认为这个功能是坏的；
 *  - 判定逻辑误判 → 把本来就是 GCJ-02 的坐标又换算一次，等于主动偏移 500 米。
 *
 * 基准点用上海人民广场（31.2304°N, 121.4737°E）：GCJ-02 在这里的偏移约 500 米，
 * 与用户反馈的「偏几百米」量级一致。
 */
class CoordinateDatumTest {

    private val wgsLat = 31.2304
    private val wgsLng = 121.4737

    // ---------------- 换算公式 ----------------

    @Test
    fun `WGS84 换算到 GCJ02 偏移量在几百米级`() {
        val (gcjLat, gcjLng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val gap = ChinaCoordinate.offsetMeters(wgsLat, wgsLng, gcjLat, gcjLng)
        assertTrue("上海的 GCJ-02 偏移应落在 200~800 米，实际 $gap", gap in 200.0..800.0)
        // 国内偏移方向固定：纬度变小、经度变大
        assertTrue(gcjLat < wgsLat)
        assertTrue(gcjLng > wgsLng)
    }

    @Test
    fun `GCJ02 反算回 WGS84 误差小于一米`() {
        val (gcjLat, gcjLng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val (backLat, backLng) = ChinaCoordinate.gcj02ToWgs84(gcjLat, gcjLng)
        val error = ChinaCoordinate.offsetMeters(wgsLat, wgsLng, backLat, backLng)
        assertTrue("往返误差应小于 1 米，实际 $error", error < 1.0)
    }

    @Test
    fun `百度坐标与 GCJ02 之间能往返`() {
        val (gcjLat, gcjLng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val (bdLat, bdLng) = ChinaCoordinate.gcj02ToBd09(gcjLat, gcjLng)
        val (backLat, backLng) = ChinaCoordinate.bd09ToGcj02(bdLat, bdLng)
        val error = ChinaCoordinate.offsetMeters(gcjLat, gcjLng, backLat, backLng)
        assertTrue("BD-09 往返误差应小于 5 米，实际 $error", error < 5.0)
        // 百度那层偏移本身也有几百米，别写成恒等变换
        val bdGap = ChinaCoordinate.offsetMeters(gcjLat, gcjLng, bdLat, bdLng)
        assertTrue("BD-09 与 GCJ-02 应差几百米，实际 $bdGap", bdGap > 100.0)
    }

    @Test
    fun `境外坐标不做任何换算`() {
        val lat = 35.6812 // 东京
        val lng = 139.7671
        val (outLat, outLng) = ChinaCoordinate.wgs84ToGcj02(lat, lng)
        assertEquals(lat, outLat, 0.0)
        assertEquals(lng, outLng, 0.0)
        assertEquals(lat to lng, ChinaCoordinate.bd09ToGcj02(lat, lng))
    }

    // ---------------- 自动判定：两套坐标互证 ----------------

    @Test
    fun `一对 WGS84 加一对 GCJ02 能认出后者`() {
        val (gcjLat, gcjLng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        // 3724/3725 报的是 GPS 原始值，2/3 报的是火星坐标
        val detected = VehicleDatumDetector.detect(
            mapOf(
                "3725" to wgsLat,
                "3724" to wgsLng,
                "3" to gcjLat,
                "2" to gcjLng,
            ),
        )
        assertNotNull("两套坐标互证应当判定成功", detected)
        assertEquals(gcjLat, detected!!.latitude, 1e-9)
        assertEquals(gcjLng, detected.longitude, 1e-9)
        assertTrue(detected.note.contains("GCJ-02"))
    }

    @Test
    fun `命名键是 GCJ02 时也能认出命名键`() {
        val (gcjLat, gcjLng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val detected = VehicleDatumDetector.detect(
            mapOf(
                "latitude" to gcjLat,
                "longitude" to gcjLng,
                "3" to wgsLat,
                "2" to wgsLng,
            ),
        )
        assertNotNull(detected)
        assertEquals(gcjLat, detected!!.latitude, 1e-9)
        assertEquals(gcjLng, detected.longitude, 1e-9)
    }

    @Test
    fun `两对坐标完全一样时不判定`() {
        assertNull(
            VehicleDatumDetector.detect(
                mapOf("latitude" to wgsLat, "longitude" to wgsLng, "3" to wgsLat, "2" to wgsLng),
            ),
        )
    }

    @Test
    fun `只有一对坐标时不判定`() {
        assertNull(VehicleDatumDetector.detect(mapOf("3" to wgsLat, "2" to wgsLng)))
        assertNull(VehicleDatumDetector.detect(emptyMap()))
    }

    @Test
    fun `两对相差几十公里时保持沉默`() {
        // 一对是上次熄火的位置：换算公式套不上，硬判会把车标画到别的城市
        val detected = VehicleDatumDetector.detect(
            mapOf("3725" to wgsLat, "3724" to wgsLng, "3" to (wgsLat + 0.5), "2" to (wgsLng + 0.5)),
        )
        assertNull(detected)
    }

    @Test
    fun `0 0 不参与判定`() {
        assertNull(
            VehicleDatumDetector.detect(
                mapOf("3725" to 0.0, "3724" to 0.0, "3" to wgsLat, "2" to wgsLng),
            ),
        )
    }

    // ---------------- 校准语义 ----------------

    @Test
    fun `自动模式没有证据时原样输出`() {
        val (outLat, outLng) = LocationCalibrator.apply(wgsLat, wgsLng, LocationCalibration.NONE)
        assertEquals(wgsLat, outLat, 0.0)
        assertEquals(wgsLng, outLng, 0.0)
    }

    @Test
    fun `自动模式拿到判定结果后不再换算`() {
        // detect 给出的那一对本身就是 GCJ-02，再套一次 WGS 换算就偏回去了
        val calibration = LocationCalibration(datum = CoordinateDatum.AUTO)
        val (outLat, outLng) = LocationCalibrator.apply(wgsLat, wgsLng, calibration, CoordinateDatum.RAW)
        assertEquals(wgsLat, outLat, 0.0)
        assertEquals(wgsLng, outLng, 0.0)
    }

    @Test
    fun `选中 WGS84 就按 WGS84 换算`() {
        val calibration = LocationCalibration(datum = CoordinateDatum.WGS84)
        val expected = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val actual = LocationCalibrator.apply(wgsLat, wgsLng, calibration)
        assertEquals(expected.first, actual.first, 1e-9)
        assertEquals(expected.second, actual.second, 1e-9)
    }

    @Test
    fun `选中百度就按百度换算`() {
        val calibration = LocationCalibration(datum = CoordinateDatum.BD09)
        val expected = ChinaCoordinate.bd09ToGcj02(wgsLat, wgsLng)
        val actual = LocationCalibrator.apply(wgsLat, wgsLng, calibration)
        assertEquals(expected.first, actual.first, 1e-9)
        assertEquals(expected.second, actual.second, 1e-9)
    }

    @Test
    fun `换算之后仍然叠加手动偏移`() {
        val calibration = LocationCalibration(datum = CoordinateDatum.WGS84, east = 40, north = -20)
        val (gcjLat, gcjLng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val expected = LocationCalibrator.shift(gcjLat, gcjLng, 40, -20)
        val actual = LocationCalibrator.apply(wgsLat, wgsLng, calibration)
        assertEquals(expected.first, actual.first, 1e-9)
        assertEquals(expected.second, actual.second, 1e-9)
    }

    // ---------------- 落盘兼容 ----------------

    @Test
    fun `老版本的零和一还能读`() {
        assertEquals(CoordinateDatum.RAW, LocationCalibrator.parse("0|120|-40").datum)
        assertEquals(CoordinateDatum.WGS84, LocationCalibrator.parse("1|120|-40").datum)
        assertEquals(120, LocationCalibrator.parse("1|120|-40").east)
        assertEquals(-40, LocationCalibrator.parse("1|120|-40").north)
    }

    @Test
    fun `百度与自动能存能读`() {
        listOf(CoordinateDatum.AUTO, CoordinateDatum.BD09).forEach { datum ->
            val calibration = LocationCalibration(datum = datum, east = 15, north = -25)
            val round = LocationCalibrator.parse(LocationCalibrator.serialize(calibration))
            assertEquals(calibration, round)
        }
    }

    @Test
    fun `没校准过的落盘是自动档`() {
        assertEquals("3|0|0", LocationCalibrator.serialize(LocationCalibration.NONE))
        assertTrue(LocationCalibrator.parse("3|0|0").isIdentity)
    }

    @Test
    fun `短文案区分换算与手动校准`() {
        assertEquals("已换算坐标系", LocationCalibrator.shortLabel(LocationCalibration(datum = CoordinateDatum.WGS84)))
        assertEquals("已换算坐标系", LocationCalibrator.shortLabel(LocationCalibration(datum = CoordinateDatum.BD09)))
        assertEquals("已校准 50m", LocationCalibrator.shortLabel(LocationCalibration(east = 30, north = 40)))
    }

    @Test
    fun `数值越界仍然被夹住`() {
        val parsed = LocationCalibrator.parse("2|99999|-99999")
        assertEquals(LocationCalibrator.MAX_OFFSET_METERS, parsed.east)
        assertEquals(-LocationCalibrator.MAX_OFFSET_METERS, parsed.north)
    }

    @Test
    fun `反算不会把 NaN 传下去`() {
        val (lat, lng) = ChinaCoordinate.gcj02ToWgs84(Double.NaN, wgsLng)
        assertTrue(lat.isNaN())
        assertEquals(wgsLng, lng, 0.0)
        assertTrue(abs(lng - wgsLng) < 1e-12)
    }
}
