package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这条坐标从什么时候起没变过」的推进规则。
 *
 * 它存在的理由是：车况报文里的经纬度**没有自己的时间戳**，而界面原来拿「我们拉取的时刻」
 * 当位置时间，于是永远是「刚刚」。这里的规则要保证两件事：
 *  - 车真在跑（坐标每次都在变）时，时间就是现在；
 *  - 车停着不动（坐标一直是同一个值）时，时间一直往回长，长到能被看见。
 */
class LocationAnchorRuleTest {

    private val lat = 30.123456
    private val lng = 120.123456

    @Test
    fun `第一次看到坐标就从这一刻开始计时`() {
        val anchor = LocationAnchorRule.next(null, lat, lng, now = 1_000L)
        assertEquals(LocationAnchor(lat, lng, 1_000L), anchor)
    }

    @Test
    fun `同一个点反复上报时起点不变`() {
        val first = LocationAnchorRule.next(null, lat, lng, now = 1_000L)
        val later = LocationAnchorRule.next(first, lat, lng, now = 9_000_000L)
        assertEquals(1_000L, later!!.since)
    }

    @Test
    fun `几十米的定位漂移不算挪车`() {
        val first = LocationAnchorRule.next(null, lat, lng, now = 1_000L)
        // 往北挪 30 米（纬度约 +0.00027），仍算同一个点
        val drifted = LocationAnchorRule.next(first, lat + 0.00027, lng, now = 5_000L)
        assertEquals(1_000L, drifted!!.since)
        // 起点不变，但坐标要跟着更新，下次才对新的位置比
        assertEquals(lat + 0.00027, drifted.latitude, 1e-9)
    }

    @Test
    fun `真的开走了就重新计时`() {
        val first = LocationAnchorRule.next(null, lat, lng, now = 1_000L)
        // 往北 1 公里
        val moved = LocationAnchorRule.next(first, lat + 0.009, lng, now = 2_000L)
        assertEquals(2_000L, moved!!.since)
    }

    @Test
    fun `这一帧没有坐标时保留原来的起点`() {
        val first = LocationAnchorRule.next(null, lat, lng, now = 1_000L)
        // 车机休眠 / 报 0,0：不代表车动了，不能把「几小时前」重置成「刚刚」
        assertEquals(first, LocationAnchorRule.next(first, null, null, now = 9_000L))
        assertEquals(first, LocationAnchorRule.next(first, 0.0, 0.0, now = 9_000L))
    }

    @Test
    fun `从来没有坐标时返回空`() {
        assertNull(LocationAnchorRule.next(null, null, null, now = 1_000L))
        assertNull(LocationAnchorRule.next(null, 0.0, 0.0, now = 1_000L))
    }

    @Test
    fun `判断阈值是五十米`() {
        assertTrue(LocationAnchorRule.SAME_POSITION_KM in 0.01..0.1)
    }

    @Test
    fun `锚点能说明位置有多久没变`() {
        val anchor = LocationAnchorRule.next(null, lat, lng, now = 1_000L)
        assertNotNull(anchor)
        val age = VehicleLocationRules.ageMs(anchor!!.since, now = 1_000L + 3 * 3_600_000L)
        assertEquals("3 小时前", VehicleLocationRules.ageText(age))
    }
}
