package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 位置判定 / 新鲜度文案（纯逻辑，不碰文件与网络）。
 *
 * 这套规则实际决定两件事，写错都会很难受：
 *  - 判定太严 → GPS 漂几米就当成「车挪了」，每次进首页都重新请求高德，额度飞快烧完；
 *  - 判定太松 → 车真的换了地方却不刷新，显示上一站的地址。
 *
 * 顺带守着一个曾经踩过的坑：早期用「坐标取整到 3 位小数」比相等，
 * 车停在网格边界附近时 30 米漂移就会跨格，被误判成换了地方。现在改成距离判定。
 */
class VehicleLocationRulesTest {

    private val shanghaiLat = 31.230416
    private val shanghaiLng = 121.473701

    @Test
    fun `同一栋楼下的 GPS 漂移不算换地方`() {
        // 约 33 米的漂移（0.0003° 纬度）
        val stored = VehiclePlace(shanghaiLat, shanghaiLng, reportedAt = 1_000L)
        assertTrue(VehicleLocationRules.sameSpot(stored, shanghaiLat + 0.0003, shanghaiLng))
        assertTrue(VehicleLocationRules.sameSpot(stored, shanghaiLat, shanghaiLng - 0.0002))
    }

    @Test
    fun `漂移跨过坐标取整边界时仍算同一个地方`() {
        // 31.2304 → 31.2307 取整后是 31.230 与 31.231，旧实现会判成「换了地方」
        val stored = VehiclePlace(31.2304, shanghaiLng, reportedAt = 1_000L)
        assertTrue(VehicleLocationRules.sameSpot(stored, 31.2307, shanghaiLng))
    }

    @Test
    fun `跨了一个网格就算换了地方`() {
        val stored = VehiclePlace(shanghaiLat, shanghaiLng, reportedAt = 1_000L)
        // 0.002° 纬度 ≈ 220 米，肯定跨网格
        assertFalse(VehicleLocationRules.sameSpot(stored, shanghaiLat + 0.002, shanghaiLng))
        assertFalse(VehicleLocationRules.sameSpot(stored, shanghaiLat, shanghaiLng + 0.002))
    }

    @Test
    fun `没有缓存时不算命中`() {
        assertFalse(VehicleLocationRules.sameSpot(null, shanghaiLat, shanghaiLng))
    }

    @Test
    fun `坐标无效时一律不算命中`() {
        val stored = VehiclePlace(shanghaiLat, shanghaiLng, reportedAt = 1_000L)
        assertFalse(VehicleLocationRules.sameSpot(stored, null, shanghaiLng))
        assertFalse(VehicleLocationRules.sameSpot(stored, shanghaiLat, null))
        // 车端偶尔上报 0,0：这是「没定位」而不是「在几内亚湾」
        assertFalse(VehicleLocationRules.sameSpot(stored, 0.0, 0.0))
    }

    @Test
    fun `坐标合法性`() {
        assertTrue(VehicleLocationRules.isValid(shanghaiLat, shanghaiLng))
        assertTrue(VehicleLocationRules.isValid(-33.86, 151.20))
        assertFalse(VehicleLocationRules.isValid(0.0, 0.0))
        assertFalse(VehicleLocationRules.isValid(91.0, 121.0))
        assertFalse(VehicleLocationRules.isValid(31.0, 181.0))
        assertFalse(VehicleLocationRules.isValid(Double.NaN, 121.0))
        assertFalse(VehicleLocationRules.isValid(31.0, Double.POSITIVE_INFINITY))
        assertFalse(VehicleLocationRules.isValid(null, null))
    }

    @Test
    fun `距离用公里做单位`() {
        // 纬度相差 0.001° ≈ 111 米
        val km = VehicleLocationRules.distanceKm(shanghaiLat, shanghaiLng, shanghaiLat + 0.001, shanghaiLng)
        assertTrue("实际 $km", km in 0.10..0.12)
        assertEquals(0.0, VehicleLocationRules.distanceKm(shanghaiLat, shanghaiLng, shanghaiLat, shanghaiLng), 1e-9)
    }

    @Test
    fun `新鲜度文案按分钟小时天分档`() {
        assertEquals("刚刚", VehicleLocationRules.ageText(0L))
        assertEquals("刚刚", VehicleLocationRules.ageText(59_000L))
        assertEquals("1 分钟前", VehicleLocationRules.ageText(60_000L))
        assertEquals("12 分钟前", VehicleLocationRules.ageText(12 * 60_000L))
        assertEquals("1 小时前", VehicleLocationRules.ageText(60 * 60_000L))
        assertEquals("23 小时前", VehicleLocationRules.ageText(23 * 3_600_000L))
        assertEquals("1 天前", VehicleLocationRules.ageText(24 * 3_600_000L))
        assertEquals("3 天前", VehicleLocationRules.ageText(3 * 86_400_000L))
    }

    @Test
    fun `时间取不到时不编造 freshness`() {
        assertNull(VehicleLocationRules.ageText(null))
        // 车况时间缺失（0）或比现在还晚（时钟回拨）都算「不知道」
        assertNull(VehicleLocationRules.ageMs(0L, 1_000L))
        assertNull(VehicleLocationRules.ageMs(5_000L, 1_000L))
        assertEquals(1_000L, VehicleLocationRules.ageMs(1_000L, 2_000L))
    }

    @Test
    fun `坐标兜底文案是五位小数`() {
        assertEquals("31.23042, 121.47370", VehicleLocationRules.coordinateText(shanghaiLat, shanghaiLng))
    }
}
