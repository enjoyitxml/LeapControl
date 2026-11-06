package com.leapcontrol.app.map

import com.leapcontrol.app.AmapPoint
import com.leapcontrol.app.AmapWebApi
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 把行程里稀疏采样点之间的直线，换成高德**驾车导航路线**。
 *
 * ## 为什么
 * 60 秒一个采样点，两个点之间可能拐了四五个弯。直接连直线，地图上就会穿楼、穿河、穿小区，
 * 用户一眼看出「这不是我走的路」。用路径规划把相邻两点之间按真实道路补出来，观感才对。
 *
 * ## 必须说清楚的边界
 * 这不是车辆的真实行驶轨迹 —— 车当时到底走哪条路，我们只有那几个点，无从得知。
 * 这里画的是「两点之间最合理的开车路线」，所以图上写的是「按导航路线补齐的示意」，
 * 不是「实际走过」。里程仍然以表显总里程差为准，**绝不用这条规划路线的长度冒充里程**。
 *
 * ## 成本
 * 驾车路径规划是高德 Web 服务的收费 / 限额接口，所以：
 *  - 一次最多取 [MAX_STOPS] 个采样点当途经点（点太密反而会逐点绕路，画出来更假）；
 *  - 结果按「采样点序列」缓存，同一段来回展开不会重复请求；
 *  - 任何一段失败就整体退回直线，不画一半路线、也不重试。
 * 没有 Key 的用户完全不受影响：直接走直线，图上照旧标注是直线示意。
 */
object TripRoute {

    /** 一次规划最多用这么多个采样点（含首末）。高德途经点上限 16，这里更保守。 */
    const val MAX_STOPS = 10

    /** 相邻两个采样点近于这个距离就不单独当途经点：原地打转的点会让路线拧成麻花。 */
    private const val MIN_STOP_KM = 0.15

    /** 最多缓存多少条路线（一条行程一条），够覆盖当前浏览的月份。 */
    private const val CACHE_SIZE = 24

    /**
     * 纯 JVM 的 LRU：`android.util.LruCache` 在单元测试里会直接抛
     * 「not mocked」，而这个类的挑点逻辑是要单测的，所以自己维护一份。
     */
    private val cache = object : LinkedHashMap<String, List<AmapPoint>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<AmapPoint>>?,
        ): Boolean = size > CACHE_SIZE
    }

    /**
     * 从采样点里挑出参与路径规划的途经点：包含首末，最多 [maxStops] 个，均匀取样。
     *
     * 均匀取样而不是取前 10 个：一条 200 个点的长行程如果只取开头 10 个点，
     * 后半段就退化成一条长直线，等于没补。纯函数，可单测。
     */
    fun stops(points: List<AmapPoint>, maxStops: Int = MAX_STOPS): List<AmapPoint> {
        if (points.size <= 2) return points
        val wanted = maxStops.coerceIn(2, AmapWebApi.MAX_WAYPOINTS + 2)

        // 先滤掉挪了几十米就原地打转的点
        val filtered = mutableListOf<AmapPoint>()
        for (p in points) {
            val last = filtered.lastOrNull()
            if (last == null || km(last, p) >= MIN_STOP_KM) filtered += p
        }
        // 终点必须保留：它跟前一个点可能只差几十米（开进车位），但不能因此丢掉终点
        if (filtered.lastOrNull() != points.last()) filtered += points.last()
        if (filtered.size < 2) return listOf(points.first(), points.last())
        if (filtered.size <= wanted) return filtered

        val step = (filtered.size - 1).toDouble() / (wanted - 1)
        return (0 until wanted)
            .map { i -> filtered[(i * step).toInt().coerceIn(0, filtered.lastIndex)] }
            .distinctBy { "${it.latitude},${it.longitude}" }
    }

    /**
     * 请求整条路线。返回 null = 没 Key / 没网 / 额度用尽 / 任一段失败，调用方退回直线。
     * 同步阻塞，必须在 IO 线程调用。
     */
    fun build(key: String, points: List<AmapPoint>): List<AmapPoint>? {
        if (key.isBlank() || points.size < 2) return null
        val stops = stops(points)
        if (stops.size < 2) return null
        val cacheKey = stops.joinToString(";") { "${it.latitude},${it.longitude}" }
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val out = mutableListOf<AmapPoint>()
        var index = 0
        // 每段：起点 + 最多 16 个途经点 + 终点 = 18 个点，正好是高德的上限
        while (index < stops.lastIndex) {
            val end = minOf(index + AmapWebApi.MAX_WAYPOINTS + 1, stops.lastIndex)
            val chunk = stops.subList(index, end + 1)
            val segment = AmapWebApi.drivingRoute(
                key = key,
                origin = chunk.first(),
                destination = chunk.last(),
                waypoints = chunk.subList(1, chunk.size - 1),
            ) ?: return null
            // 拼接时去掉与上一段重复的首点
            out += if (out.isEmpty()) segment else segment.drop(1)
            index = end
        }
        if (out.size < 2) return null
        synchronized(cache) { cache[cacheKey] = out }
        return out
    }

    /** 清空缓存（换车 / 退出登录时调用；瓦片缓存不用动）。 */
    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    /** 两点间大圆距离（km）。只用于「这两个采样点够不够远」的判断。 */
    private fun km(a: AmapPoint, b: AmapPoint): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
            sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(h.coerceAtMost(1.0)))
    }
}
