package com.leapcontrol.app

import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * 静态地图的取景换算：把「画面里想看多大一片地」换成高德的 `size` 参数。
 *
 * ## 为什么不再直接写 size / zoom
 * 原来缩略图固定 `size=480&zoom=16`、大图 `size=1024&zoom=16`，等于写死了「取景 1 公里」
 * 和「取景 2.1 公里」两个数字，改一个还没法保证另一个的观感一致。结果就是用户点开找车页，
 * 看到的是一张两公里见方的图 —— 车钉在中间，周围十几条街都进来了，反而认不出车在哪一栋。
 *
 * 现在反过来算：先说「我要 350 米」，再由 [requestSize] 推该向高德要多大的图。
 *
 * ## 为什么 zoom 定死在 17
 * 高德静态图 `zoom` 上限就是 17（文档明确写 [1,17]），往下调只会更远，没有余地。
 * 所以「每像素多少米」的下限由 zoom 17 决定，能调的只剩 size —— 图要得越小，
 * 同样的 zoom 下覆盖的地面越少。这就是 [requestSize] 的全部逻辑。
 *
 * 另外 `scale=2`（高清图）在 zoom 已经顶到 17 时不再抬级别，只把像素翻一倍，
 * 所以**取景范围由 size 决定、清晰度由 scale 决定**，两者互不干扰。
 *
 * 坐标系仍是 GCJ-02，这里的公式只用来算「多少米对应多少像素」，不碰坐标本身。
 */
object MapFraming {
    /** 高德静态地图的 zoom 上限。再远就是「看不清路」，再近它不给。 */
    const val MAX_ZOOM = 17

    /** 赤道处一个 256px 瓦片在 zoom 0 覆盖的米数（Web Mercator 标准常数）。 */
    private const val EQUATOR_METERS_PER_TILE = 156543.03392

    /**
     * 首页卡缩略图（96dp 方框）的取景宽度：200 米。
     *
     * 96dp 在高密度屏上约 288 物理像素，而 zoom 17 能给的像素密度是 0.52 米/像素 ——
     * 200 米对应约 380 像素的源图，放进方框是**缩小**，线条最干净。
     * 再往里收到 120 米只剩一栋楼和一枚图钉，看不出「从哪条路进去」；
     * 200 米刚好装下小区出入口 + 相邻两条路，这也是找车时最先要判断的事。
     */
    const val THUMB_SPAN_METERS = 200.0

    /**
     * 找车页大图的取景宽度：350 米。
     *
     * 找车页铺满整宽（约 1080 像素），350 米对应约 670 像素的图，放大 1.6 倍，
     * 线条仍然干净；再往下收到 200 米时源图只剩 380 像素，铺到整屏就是一片糊。
     * 而这个尺度足够看见小区出入口、邻近的两三条路和停车楼的位置。
     */
    const val FULL_SPAN_METERS = 350.0

    /** 高德 size 参数的上下限。太小会返回失败，超过 1024 不返回。 */
    const val MIN_SIZE = 32
    const val MAX_SIZE = 1024

    /**
     * 指定 zoom 与纬度下，1 像素代表多少米。
     *
     * Mercator 投影在高纬度被横向拉伸，同一 zoom 下高纬度每像素米数更小，
     * 所以必须按实际纬度算 —— 哈尔滨和海口同 zoom 的取景能差三分之一。
     */
    fun metersPerPixel(latitude: Double, zoom: Int = MAX_ZOOM): Double {
        val lat = latitude.coerceIn(-85.0, 85.0)
        return EQUATOR_METERS_PER_TILE * cos(Math.toRadians(lat)) / (1 shl zoom.coerceIn(1, 20))
    }

    /** 想覆盖 [spanMeters] 米，该要多大的图（高德 `size` 的边长）。 */
    fun requestSize(spanMeters: Double, latitude: Double, zoom: Int = MAX_ZOOM): Int {
        if (!spanMeters.isFinite() || spanMeters <= 0.0) return MIN_SIZE
        val raw = spanMeters / metersPerPixel(latitude, zoom)
        return raw.roundToInt().coerceIn(MIN_SIZE, MAX_SIZE)
    }

    /** [requestSize] 的反函数：这个 size 实际覆盖多少米（用来核对 / 单测）。 */
    fun spanOfSize(size: Int, latitude: Double, zoom: Int = MAX_ZOOM): Double =
        size.coerceIn(MIN_SIZE, MAX_SIZE) * metersPerPixel(latitude, zoom)
}
