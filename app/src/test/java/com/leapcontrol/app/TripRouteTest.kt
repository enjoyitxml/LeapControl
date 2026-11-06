package com.leapcontrol.app

import com.leapcontrol.app.map.TripRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航路线补齐的挑点逻辑。
 *
 * 只测纯函数：真正发请求的那部分要联网、要 Key，不在 JVM 单测里跑。
 * 这里守的是「首末一定在、总数不超上限、原地打转的点不进去」——这三条错了，
 * 要么路线从半路开始（地图上缺一截），要么一次请求把额度打满。
 */
class TripRouteTest {

    private fun p(lat: Double, lng: Double) = AmapPoint(lat, lng)

    /** 200 个采样点：均匀取样后仍要保住首末，且不超过高德的途经点上限。 */
    @Test
    fun `途经点包含首末且不超过上限`() {
        val line = (0..199).map { p(30.0 + it * 0.001, 120.0) }
        val stops = TripRoute.stops(line)
        assertTrue(stops.size in 2..TripRoute.MAX_STOPS)
        assertEquals(line.first(), stops.first())
        assertEquals(line.last(), stops.last())
    }

    /** 只有一两个点时不折腾，原样返回（让调用方直接跳过规划）。 */
    @Test
    fun `点数不足时原样返回`() {
        val two = listOf(p(30.0, 120.0), p(30.1, 120.1))
        assertEquals(two, TripRoute.stops(two))
        val one = listOf(p(30.0, 120.0))
        assertEquals(one, TripRoute.stops(one))
    }

    /** 原地打转的采样点会让路线拧成麻花，近于 150 米的点不进去。 */
    @Test
    fun `原地打转的点会被过滤掉`() {
        val line = listOf(
            p(30.0, 120.0),
            p(30.00001, 120.0), // 约 1 米，跟上一个点是同一个位置
            p(30.02, 120.0),    // 约 2.2 公里
        )
        val stops = TripRoute.stops(line)
        assertEquals(2, stops.size)
        assertEquals(line.first(), stops.first())
        assertEquals(line.last(), stops.last())
    }

    /** 终点跟前一个点只差几十米（开进车位）时也不能丢：丢了地图就停在半路。 */
    @Test
    fun `终点和前一点很近也要保留`() {
        val line = listOf(
            p(30.0, 120.0),
            p(30.05, 120.0),
            p(30.0501, 120.0), // 距上一个约 11 米
        )
        assertEquals(line.last(), TripRoute.stops(line).last())
    }
}
