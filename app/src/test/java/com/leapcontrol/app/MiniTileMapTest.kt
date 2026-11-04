package com.leapcontrol.app

import com.leapcontrol.app.map.MiniTileMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页缩略图的瓦片范围计算。
 *
 * 这段是纯坐标数学，错了的表现很隐蔽：少取一块 → 图边上一条空白；
 * 多取一列 → 白花一次请求（而瓦片是别人的公共服务，不该白嫖流量）。
 */
class MiniTileMapTest {

    @Test
    fun `上海 288 像素视口跨两列三行`() {
        val r = MiniTileMap.tileRange(31.2304, 121.4737, 17, 288)
        assertEquals(109762, r.minCol)
        assertEquals(109763, r.maxCol)
        assertEquals(53555, r.minRow)
        assertEquals(53557, r.maxRow)
        assertEquals(6, r.tileCount)
    }

    @Test
    fun `南昌 288 像素视口是标准的两行两列`() {
        val r = MiniTileMap.tileRange(28.70428, 115.79606, 17, 288)
        assertEquals(107695, r.minCol)
        assertEquals(107696, r.maxCol)
        assertEquals(54617, r.minRow)
        assertEquals(54618, r.maxRow)
        assertEquals(4, r.tileCount)
    }

    /** 视口比一块瓦片小的时候，任何位置都不该超过 2×2。 */
    @Test
    fun `小视口最多四块瓦片`() {
        val samples = listOf(
            31.2304 to 121.4737,
            45.75 to 126.65,
            20.04 to 110.32,
            39.90 to 116.40,
        )
        samples.forEach { (lat, lng) ->
            val r = MiniTileMap.tileRange(lat, lng, 17, 200)
            assertTrue("$lat,$lng 取了 ${r.cols} 列", r.cols <= 2)
            assertTrue("$lat,$lng 取了 ${r.rows} 行", r.rows <= 2)
        }
    }

    /**
     * 视口右下角正好压在瓦片边界上时，不能多取一列 / 一行。
     * zoom 0 的世界图正好是一块 256 像素的瓦片，视口也是 256 —— 边界情况的最短构造。
     */
    @Test
    fun `正好压边界时不多取一块`() {
        val r = MiniTileMap.tileRange(0.0, 0.0, 0, 256)
        assertEquals(0, r.minCol)
        assertEquals(0, r.maxCol)
        assertEquals(0, r.minRow)
        assertEquals(0, r.maxRow)
        assertEquals(1, r.tileCount)
    }

    @Test
    fun `视口原点就是左上角算出来的位置`() {
        val r = MiniTileMap.tileRange(31.2304, 121.4737, 17, 288)
        // 原点 + 视口宽 = 右下角，必须落在最后一块瓦片里
        val right = r.originX + 288
        assertTrue(right <= (r.maxCol + 1) * MiniTileMap.TILE)
        assertTrue(r.originX >= r.minCol * MiniTileMap.TILE)
    }
}
