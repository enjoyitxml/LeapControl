package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 静态地图取景换算。
 *
 * 回归的是「找车页地图太远」：以前 zoom 写死 16、大图 size 1024，等于 2.1 公里见方，
 * 车钉在中间认不出是哪一栋。现在由「想看多少米」反推 size，这里锁住几个关键换算。
 */
class MapFramingTest {

    private val nanchangLat = 28.68

    @Test
    fun `zoom 17 在南昌纬度约 1 米一像素`() {
        val mpp = MapFraming.metersPerPixel(nanchangLat)
        // 156543.03 × cos(28.68°) / 2^17 ≈ 1.047
        assertEquals(1.047, mpp, 0.01)
    }

    @Test
    fun `纬度越高每像素米数越小`() {
        val south = MapFraming.metersPerPixel(20.0)
        val north = MapFraming.metersPerPixel(45.0)
        assertTrue("高纬度同一 zoom 下覆盖的地面更少", north < south)
    }

    @Test
    fun `首页缩略图 200 米对应的源图不小于方框像素`() {
        val size = MapFraming.requestSize(MapFraming.THUMB_SPAN_METERS, nanchangLat)
        // scale=2 时源图像素是这个 size 的两倍，96dp 方框在高密度屏上约 288 像素
        assertTrue("size=$size 源图 ${size * 2}px 应不小于 288px", size * 2 >= 288)
        assertEquals(200.0, MapFraming.spanOfSize(size, nanchangLat), 5.0)
    }

    @Test
    fun `找车页大图 350 米对应的尺寸在高德上限之内`() {
        val size = MapFraming.requestSize(MapFraming.FULL_SPAN_METERS, nanchangLat)
        assertTrue("size=$size", size in 300..400)
        assertTrue("不能超过高德 1024 的上限", size <= MapFraming.MAX_SIZE)
    }

    @Test
    fun `同样的取景在哈尔滨要的像素更多`() {
        // 高纬度每像素米数更小，同样 350 米就得要多一些像素才装得下
        val nanchang = MapFraming.requestSize(350.0, 28.68)
        val harbin = MapFraming.requestSize(350.0, 45.75)
        assertTrue("$harbin 应大于 $nanchang", harbin > nanchang)
    }

    @Test
    fun `异常输入不会算出越界的 size`() {
        assertEquals(MapFraming.MIN_SIZE, MapFraming.requestSize(0.0, nanchangLat))
        assertEquals(MapFraming.MIN_SIZE, MapFraming.requestSize(-5.0, nanchangLat))
        assertEquals(MapFraming.MIN_SIZE, MapFraming.requestSize(Double.NaN, nanchangLat))
        // 要 100 公里：直接顶到上限，不能把 4096 这种值发给高德
        assertEquals(MapFraming.MAX_SIZE, MapFraming.requestSize(100_000.0, nanchangLat))
    }

    @Test
    fun `对比旧版取景确实是拉近了`() {
        // 旧版：zoom 16、size 1024 → 约 2.1 公里
        val old = MapFraming.spanOfSize(1024, nanchangLat, zoom = 16)
        assertTrue("旧版约 $old 米", old > 2000.0)
        // 新版：350 米，相当于拉近 6 倍
        assertTrue(MapFraming.FULL_SPAN_METERS * 5 < old)
    }
}
