package com.leapcontrol.app

import com.leapcontrol.app.map.AmapTileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.MapTileIndex

/**
 * 瓦片 URL 的回归测试。
 *
 * 这一组守的是两个**编译期看不出来、运行时才炸**的坑：
 *  1. osmdroid 自带的 `XYTileSource` 不认 `{x}/{y}/{z}`，会拼成
 *     `…?x={x}&y={y}&z={z}/17/1/2.png`，高德回 404 + HTML，界面上是一片空白格子；
 *  2. `scl=2` 的 512 高清图**没有文字注记**，换上去整张地图一个路名都没有。
 *
 * 两次都是用户先发现的，所以这里直接断言 URL 长什么样。
 */
class AmapTileSourceTest {

    @Test
    fun `占位符全部被替换`() {
        listOf(AmapTileSource.road, AmapTileSource.satellite).forEach { source ->
            val url = source.getTileURLString(MapTileIndex.getTileIndex(17, 107000, 53000))
            assertFalse("还有没替换的占位符：$url", url.contains("{"))
            assertFalse("还有没替换的占位符：$url", url.contains("}"))
        }
    }

    @Test
    fun `路网地址带 x y z 三个查询参数`() {
        val url = AmapTileSource.road.getTileURLString(MapTileIndex.getTileIndex(17, 107000, 53000))
        assertTrue(url, url.startsWith("https://webrd0"))
        assertTrue(url, url.contains("style=8"))
        assertTrue(url, url.contains("x=107000"))
        assertTrue(url, url.contains("y=53000"))
        assertTrue(url, url.contains("z=17"))
    }

    @Test
    fun `卫星走另一套域名和 style`() {
        val url = AmapTileSource.satellite.getTileURLString(MapTileIndex.getTileIndex(18, 107000, 53000))
        assertTrue(url, url.startsWith("https://webst0"))
        assertTrue(url, url.contains("style=6"))
        assertTrue(url, url.contains("x=107000"))
        assertTrue(url, url.contains("z=18"))
    }

    @Test
    fun `不会把层级拼成路径`() {
        // XYTileSource 的拼法是 baseUrl + "/z/x/y"，路径段一旦出现就是走错源了
        val url = AmapTileSource.road.getTileURLString(MapTileIndex.getTileIndex(16, 53500, 26500))
        assertFalse(url, url.endsWith("/16/53500/26500.png"))
        assertFalse(url, url.contains("/16/"))
    }

    @Test
    fun `子域轮询覆盖四个域名`() {
        val hosts = (1..80).map {
            AmapTileSource.road.getTileURLString(MapTileIndex.getTileIndex(17, it, it))
                .substringAfter("https://").substringBefore(".")
        }.toSet()
        assertEquals(setOf("webrd01", "webrd02", "webrd03", "webrd04"), hosts)
    }

    @Test
    fun `tileUrl 按 z x y 顺序替换`() {
        assertEquals(
            "https://a/b?z=3&x=1&y=2",
            AmapTileSource.tileUrl("https://a/b?z={z}&x={x}&y={y}", 3, 1, 2),
        )
    }

    /**
     * **不能**用 `scl=2`。它确实返回 512×512，但整层文字注记被剥掉了 ——
     * 实测 z=15/16/17/18 × 四个子域 × 各种 style / ltype / size 组合，
     * 512 图里都是 0 个墨色像素，用户看到的是「有路没字，根本不知道是哪儿」。
     * 可读性优先：宁可 256 按屏幕密度放大，也不能没字。
     */
    @Test
    fun `路网必须用带注记的 scl=1`() {
        val url = AmapTileSource.road.getTileURLString(MapTileIndex.getTileIndex(17, 107000, 53000))
        assertTrue("scl=1 才有文字注记：$url", url.contains("scl=1"))
        assertFalse("scl=2 的图没有文字，别再切回去：$url", url.contains("scl=2"))
        assertEquals(256, AmapTileSource.road.tileSizePixels)
    }

    @Test
    fun `瓦片尺寸声明为 256`() {
        assertEquals(256, AmapTileSource.road.tileSizePixels)
        assertEquals(256, AmapTileSource.satellite.tileSizePixels)
    }

    @Test
    fun `最大层级与服务端能力一致`() {
        // z=19 高德只回纯色空图，地图会整片白
        assertEquals(18, AmapTileSource.road.maximumZoomLevel)
        assertEquals(18, AmapTileSource.satellite.maximumZoomLevel)
    }
}
