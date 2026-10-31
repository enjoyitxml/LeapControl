package com.leapcontrol.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航 URI 的回归测试。
 *
 * 起因：找车/位置卡把逆地理出的详细地址（「…榆林路万科金域传奇」）当 `dname` 传给高德，
 * 高德拿它去检索 POI，命中后就用 POI 坐标覆盖了我们传的 `dlat/dlon` —— 用户点「导航到车辆」，
 * 终点变成了小区中心。这几条断言就是为了让这个坑不会再回来。
 */
class MapIntentsTest {

    private val lat = 28.729933
    private val lng = 115.816674

    @Test
    fun `步行默认名是通用词而不是地址`() {
        val uri = MapIntents.amapUri(lat, lng, MapIntents.COORD_DEST_LABEL, NavMode.WALK)

        assertTrue(uri.startsWith("androidamap://route/plan/"))
        assertTrue(uri.contains("dlat=28.729933"))
        assertTrue(uri.contains("dlon=115.816674"))
        // 「车辆位置」的 UTF-8 百分号编码，固定不变
        assertTrue(uri.contains("dname=%E8%BD%A6%E8%BE%86%E4%BD%8D%E7%BD%AE"))
        assertTrue(uri.contains("t=2"))
    }

    @Test
    fun `坐标型目的地名不该长得像地址`() {
        // 一旦有人把「车辆位置」改成地址模板（含 省市区路号 之类），这条会先炸
        val label = MapIntents.COORD_DEST_LABEL
        assertFalse(label.any { it in "省市区县镇乡村路街道号栋幢小区苑府湾" })
        assertFalse(label.contains("江西"))
        assertFalse(label.contains("万科"))
    }

    @Test
    fun `起点一律留空让高德用手机定位`() {
        val walk = MapIntents.amapUri(lat, lng, MapIntents.COORD_DEST_LABEL, NavMode.WALK)
        val car = MapIntents.amapUri(lat, lng, MapIntents.COORD_DEST_LABEL, NavMode.CAR)

        assertFalse(walk.contains("slat="))
        assertFalse(walk.contains("slon="))
        assertFalse(car.contains("slat="))
        assertFalse(car.contains("slon="))
    }

    @Test
    fun `骑行用 t=3`() {
        val uri = MapIntents.amapUri(lat, lng, MapIntents.COORD_DEST_LABEL, NavMode.BIKE)
        assertTrue(uri.contains("t=3"))
    }

    @Test
    fun `驾车仍走 navi 且经纬度不颠倒`() {
        val uri = MapIntents.amapUri(lat, lng, MapIntents.COORD_DEST_LABEL, NavMode.CAR)

        assertTrue(uri.startsWith("androidamap://navi?"))
        assertTrue(uri.contains("lat=28.729933"))
        assertTrue(uri.contains("lon=115.816674"))
    }

    @Test
    fun `网页兜底的 to 是经度在前`() {
        val uri = MapIntents.webNavUri(lat, lng, MapIntents.COORD_DEST_LABEL, NavMode.WALK)

        assertTrue(uri.contains("to=115.816674,28.729933"))
        assertTrue(uri.contains("mode=walk"))
    }

    @Test
    fun `搜索结果的 POI 名字仍然照传`() {
        // 「发送目的地」那条线目的地本身就是一个 POI，名字有意义、吸附也无害
        val uri = MapIntents.amapUri(lat, lng, "万科金域传奇", NavMode.CAR)
        assertTrue(uri.contains("poiname=%E4%B8%87%E7%A7%91%E9%87%91%E5%9F%9F%E4%BC%A0%E5%A5%87"))
    }
}
