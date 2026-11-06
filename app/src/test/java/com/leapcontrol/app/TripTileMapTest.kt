package com.leapcontrol.app

import com.leapcontrol.app.map.MiniTileMap
import com.leapcontrol.app.map.TripTileMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行程地图的取景换算（纯数学，不联网也不碰 Bitmap）。
 *
 * 出错的样子很直观：层级选高了 → 折线两端被裁掉；选低了 → 一趟 3 公里的路缩成
 * 图中间一个小点。所以这里直接把每个采样点投回视口，检查它连留白一起装得下。
 */
class TripTileMapTest {

    private fun pt(lat: Double, lng: Double) = TripTileMap.Point(lat, lng)

    @Test
    fun `空点集和越界坐标不取景`() {
        assertNull(TripTileMap.view(emptyList(), 960, 480))
        assertNull(TripTileMap.view(listOf(pt(28.7, 115.8)), 0, 480))
        assertNull(TripTileMap.view(listOf(pt(91.0, 115.8)), 960, 480))
    }

    @Test
    fun `短途所有采样点连留白一起装进视口`() {
        val points = listOf(
            pt(28.70428, 115.79606),
            pt(28.70650, 115.79910),
            pt(28.71000, 115.80300),
        )
        val width = 960
        val height = 480
        val view = TripTileMap.view(points, width, height)!!
        assertTrue(view.zoom in TripTileMap.MIN_ZOOM..TripTileMap.MAX_ZOOM)
        for (p in points) {
            val (gx, gy) = MiniTileMap.globalPixel(p.lat, p.lng, view.zoom)
            val x = gx - view.originX
            val y = gy - view.originY
            assertTrue("x=$x 应在留白内", x >= TripTileMap.PADDING_PX - 1 &&
                x <= width - TripTileMap.PADDING_PX + 1)
            assertTrue("y=$y 应在留白内", y >= TripTileMap.PADDING_PX - 1 &&
                y <= height - TripTileMap.PADDING_PX + 1)
        }
    }

    @Test
    fun `跨城行程自动降层级 单点行程不贴到街道级`() {
        val short = TripTileMap.view(
            listOf(pt(28.70428, 115.79606), pt(28.70650, 115.79910)), 960, 480,
        )!!
        val long = TripTileMap.view(
            listOf(pt(39.90, 116.40), pt(31.23, 121.47)), 960, 480,
        )!!
        assertTrue("跨城层级应更低：short=${short.zoom} long=${long.zoom}", long.zoom < short.zoom)
        assertTrue(long.zoom >= TripTileMap.MIN_ZOOM)

        val single = TripTileMap.view(listOf(pt(28.70428, 115.79606)), 960, 480)!!
        assertEquals(TripTileMap.SINGLE_POINT_ZOOM, single.zoom)
        val (gx, gy) = MiniTileMap.globalPixel(28.70428, 115.79606, single.zoom)
        assertEquals(960 / 2.0, gx - single.originX, 0.001)
        assertEquals(480 / 2.0, gy - single.originY, 0.001)
    }
}
