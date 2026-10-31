package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WGS-84 → GCJ-02 换算。
 *
 * 算法是公开的、各地实现一致，容易「看着对」，所以测试只盯两件事：
 * 1. **公开参照点**要对得上（`coordtransform` 文档里的那组北京坐标）；
 * 2. **偏移量级**必须是几百米 —— 量级错了说明椭球参数用错，换算后反而把位置推得更远。
 */
class ChinaCoordinateTest {

    /** 参照点：WGS-84 (39.915, 116.404) → GCJ-02 (39.91640428150164, 116.41024449916938)。 */
    private val wgsLat = 39.915
    private val wgsLng = 116.404
    private val gcjLat = 39.91640428150164
    private val gcjLng = 116.41024449916938

    @Test
    fun `公开参照点换算误差在十分之一米以内`() {
        val (lat, lng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        assertEquals(gcjLat, lat, 1e-6)
        assertEquals(gcjLng, lng, 1e-6)
    }

    @Test
    fun `偏移量在几百米这个量级`() {
        val (lat, lng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val meters = ChinaCoordinate.offsetMeters(wgsLat, wgsLng, lat, lng)
        assertTrue("偏移 $meters 米", meters in 300.0..800.0)
    }

    @Test
    fun `华北方向是往东北挪`() {
        // 北京一带纬向、经向都偏大；华东华南纬向会偏小，所以这条只钉北京，不要推广到全国
        val (lat, lng) = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        assertTrue(lat > wgsLat)
        assertTrue(lng > wgsLng)
    }

    @Test
    fun `南昌一带的偏移也有几百米`() {
        // 用户常在南昌，量级要在同一档：几十米说明参数用错，几公里说明算错
        val lat = 28.68
        val lng = 115.86
        val shifted = ChinaCoordinate.wgs84ToGcj02(lat, lng)
        val meters = ChinaCoordinate.offsetMeters(lat, lng, shifted.first, shifted.second)
        assertTrue("偏移 $meters 米", meters in 300.0..800.0)
    }

    @Test
    fun `境外坐标原样返回`() {
        // 东京：中国境外的地图不使用这套偏移，换算了反而错
        val tokyo = 35.6812 to 139.7671
        assertEquals(tokyo, ChinaCoordinate.wgs84ToGcj02(tokyo.first, tokyo.second))
        assertTrue(ChinaCoordinate.isOutOfChina(tokyo.first, tokyo.second))
    }

    @Test
    fun `非法坐标不抛异常`() {
        val nan = Double.NaN
        val (lat, lng) = ChinaCoordinate.wgs84ToGcj02(nan, 120.0)
        assertTrue(lat.isNaN())
        assertEquals(120.0, lng, 0.0)
    }

    @Test
    fun `同一地点重复换算结果稳定`() {
        val once = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        val twice = ChinaCoordinate.wgs84ToGcj02(wgsLat, wgsLng)
        assertEquals(once, twice)
    }
}
