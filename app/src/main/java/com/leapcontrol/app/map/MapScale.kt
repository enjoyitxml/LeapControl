package com.leapcontrol.app.map

import kotlin.math.cos
import kotlin.math.log2

/**
 * osmdroid 地图的缩放换算（纯逻辑，可单测）。
 *
 * ## 为什么不用 [com.leapcontrol.app.MapFraming]
 * 那个是**高德静态图**的换算：zoom 被高德锁死在 17，只能靠 `size` 调取景，
 * 所以它的 `requestSize()` 是「想覆盖 N 米 → 要多大一张图」。
 * 这里反过来：瓦片地图的 zoom 是连续的、用户还能双指改，需要的是
 * 「这个 zoom 下每像素多少米」「想一屏看 N 米该用哪个 zoom」。
 * 两者共用同一套 Web Mercator 常数，但语义不同，混用会让两边都难改。
 *
 * ## 瓦片被按屏幕密度放大
 * osmdroid 的 `setTilesScaledToDpi(true)` 会把 256px 的瓦片按 `density` 放大绘制
 * （3x 屏上画成 768px）。好处是道路名、POI 名字大小正常；代价是一屏覆盖的地面
 * 比「不放大」时少 [tileScale] 倍。所有换算都必须带上这个系数，否则算出来的
 * 「一屏 350 米」会变成 1 公里。
 */
object MapScale {

    /**
     * 高德栅格瓦片的层级范围。12 级约 40 公里视野，18 级是街道级。
     *
     * **上限压在 18 而不是 19**：实测 z=19 在多数区域高德只回一张 200 字节的纯色空图
     * （`scl=1` 与 `scl=2` 都是），用户放大到那一级看到的是整片白，比"最多只能放到 18"
     * 更让人困惑。
     */
    const val MIN_ZOOM = 12.0
    const val MAX_ZOOM = 18.0

    /**
     * 进页面时的默认层级：配合 3x 屏的瓦片放大，一屏约 350 米 ——
     * 和原来静态图找车页的取景（[com.leapcontrol.app.MapFraming.FULL_SPAN_METERS]）基本一致，
     * 用户升级后看到的第一眼不该是另一个尺度。
     */
    const val DEFAULT_ZOOM = 17.0

    /** 一屏想看多大一片地（米），用于在小屏 / 大屏上把默认视野拉齐。 */
    const val DEFAULT_SPAN_METERS = 350.0

    /** 赤道处一个 256px 瓦片在 zoom 0 覆盖的米数（与 MapFraming 同一个常数）。 */
    private const val EQUATOR_METERS_PER_TILE = 156543.03392

    /**
     * 一个瓦片的**逻辑**边长（像素）。
     *
     * 高德对第三方只给 256 的完整图（带文字注记）。`scl=2` 虽然回 512×512，
     * 但整层注记被剥掉了，路名楼栋号全没有 —— 详见 [AmapTileSource] 类注释里的实测记录。
     * 所以这里就是 256，osmdroid 按 `256 × density`（3x 屏 = 768px）绘制，
     * 清晰度只能靠用户放大到 18 级，不能靠换瓦片。
     */
    const val TILE_PX = 256.0

    /** 这个 zoom 下，屏幕上一个物理像素代表多少米。 */
    fun metersPerPixel(latitude: Double, zoom: Double): Double {
        val lat = latitude.coerceIn(-85.0, 85.0)
        return EQUATOR_METERS_PER_TILE * cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
    }

    /** zoom 夹到 [MIN_ZOOM]..[MAX_ZOOM]：越界时 osmdroid 会白屏，别把控制权交给它。 */
    fun clampZoom(zoom: Double): Double =
        if (!zoom.isFinite()) DEFAULT_ZOOM else zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * 想让一屏装下 [spanMeters] 米，该用哪个 zoom。
     *
     * [viewportPx] 是地图控件的宽度（物理像素），[tileScale] 是瓦片按屏幕密度放大的倍数
     * （见类注释）。返回的 zoom 已经夹进 [MIN_ZOOM]..[MAX_ZOOM]。
     */
    fun zoomForSpan(
        spanMeters: Double,
        viewportPx: Float,
        latitude: Double,
        tileScale: Float = 1f,
    ): Double {
        if (!spanMeters.isFinite() || spanMeters <= 0.0) return DEFAULT_ZOOM
        if (viewportPx <= 0f || tileScale <= 0f) return DEFAULT_ZOOM
        // 一屏覆盖的米数 = viewportPx × (米/像素) / tileScale
        val metersPerPixel = spanMeters * tileScale / viewportPx
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return DEFAULT_ZOOM
        val lat = latitude.coerceIn(-85.0, 85.0)
        val zoom = log2(EQUATOR_METERS_PER_TILE * cos(Math.toRadians(lat)) / metersPerPixel)
        return clampZoom(zoom)
    }

    /** [zoomForSpan] 的反函数：当前 zoom 下一屏覆盖多少米（用来核对 / 单测）。 */
    fun spanOfZoom(zoom: Double, viewportPx: Float, latitude: Double, tileScale: Float = 1f): Double {
        if (viewportPx <= 0f) return 0.0
        return viewportPx * metersPerPixel(latitude, zoom) / tileScale
    }
}
