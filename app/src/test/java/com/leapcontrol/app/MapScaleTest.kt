package com.leapcontrol.app

import com.leapcontrol.app.map.MapScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 瓦片地图的缩放换算。
 *
 * 这块是纯数学习题，但它决定了两件用户能直接看到的事：
 *  - 进页面的默认视野是不是「和以前一样大」（糊一屏或者只剩一栋楼都算错）；
 *  - 不同纬度（哈尔滨 / 海口）同 zoom 的取景差三分之一。
 *
 * 用上海（31.2°）当基准：高德瓦片在 zoom 17、纬度 31° 时约 1.02 米/像素。
 */
class MapScaleTest {

    private val shanghai = 31.2

    @Test
    fun `zoom 17 在上海约每像素 1 米`() {
        assertEquals(1.02, MapScale.metersPerPixel(shanghai, 17.0), 0.02)
    }

    @Test
    fun `高纬度同 zoom 覆盖更少地面`() {
        val hainan = MapScale.metersPerPixel(20.0, 17.0)
        val harbin = MapScale.metersPerPixel(45.7, 17.0)
        assertTrue("哈尔滨应该比海口每像素更少米：$harbin vs $hainan", harbin < hainan)
    }

    /** 1080 宽的 3x 屏上，默认 zoom 应该正好覆盖约 350 米 —— 和原来的静态图取景对齐。 */
    @Test
    fun `默认 zoom 在 3x 屏上约 350 米视野`() {
        val span = MapScale.spanOfZoom(MapScale.DEFAULT_ZOOM, 1080f, shanghai, tileScale = 3f)
        assertEquals(350.0, span, 25.0)
    }

    @Test
    fun `zoomForSpan 与 spanOfZoom 互为反函数`() {
        val zoom = MapScale.zoomForSpan(800.0, 1080f, shanghai, tileScale = 3f)
        val back = MapScale.spanOfZoom(zoom, 1080f, shanghai, tileScale = 3f)
        assertEquals(800.0, back, 1.0)
    }

    /** 想要同样的视野，屏幕越宽（像素越多）zoom 就得越大：每个像素分到的地面更少。 */
    @Test
    fun `视野不变时屏宽翻倍就升一级`() {
        // 用 1.5 公里这个量级：350 米那档加大屏会顶到 MAX_ZOOM 被夹住，夹住后差值就不止一级了
        val small = MapScale.zoomForSpan(1500.0, 1080f, shanghai, tileScale = 3f)
        val large = MapScale.zoomForSpan(1500.0, 2160f, shanghai, tileScale = 3f)
        assertEquals(1.0, large - small, 0.02)
    }

    @Test
    fun `zoom 越界会被夹住`() {
        assertEquals(MapScale.MAX_ZOOM, MapScale.clampZoom(25.0), 0.0)
        assertEquals(MapScale.MIN_ZOOM, MapScale.clampZoom(3.0), 0.0)
        assertEquals(MapScale.DEFAULT_ZOOM, MapScale.clampZoom(Double.NaN), 0.0)
    }

    /** 参数不合法时退回默认值，绝不返回 NaN（NaN 进 osmdroid 会直接白屏）。 */
    @Test
    fun `脏参数不会算出 NaN`() {
        assertEquals(MapScale.DEFAULT_ZOOM, MapScale.zoomForSpan(0.0, 1080f, shanghai), 0.0)
        assertEquals(MapScale.DEFAULT_ZOOM, MapScale.zoomForSpan(350.0, 0f, shanghai), 0.0)
        assertEquals(0.0, MapScale.spanOfZoom(17.0, 0f, shanghai), 0.0)
    }

    @Test
    fun `默认缩放范围覆盖街道级到全城`() {
        val near = MapScale.spanOfZoom(MapScale.MAX_ZOOM, 1080f, shanghai, tileScale = 3f)
        val far = MapScale.spanOfZoom(MapScale.MIN_ZOOM, 1080f, shanghai, tileScale = 3f)
        assertTrue("最放大时应该还在街区级（约 180 米）：$near", near < 250.0)
        assertTrue("最缩小时应该能看到几十公里：$far", far > 10_000.0)
    }

    /**
     * 上限锁在 18 级：19 级高德在多数区域只回一张 200 字节的纯色空图，
     * 用户一路放大到那儿看到的是整片白（2026-11-05 用户截图反馈）。
     */
    @Test
    fun `最大层级不超过 18`() {
        assertEquals(18.0, MapScale.MAX_ZOOM, 0.0)
    }
}
