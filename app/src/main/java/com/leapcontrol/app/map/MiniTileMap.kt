package com.leapcontrol.app.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.util.MapTileIndex
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * 首页「车辆位置」卡那张 96dp 缩略图的瓦片渲染（纯逻辑 + 一次网络取图）。
 *
 * ## 为什么不直接用 MapView
 * 那张卡在首页的滚动列里，而 `MapView` 会接管单指拖动 —— 用户想滑首页，结果在拖地图。
 * 找车页当初就是踩了这个坑才把地图移出滚动容器的；首页这张只有 96dp，为一个缩略图
 * 再去铺一层"挡住触摸"的遮罩，不值得，也容易把滚动手感搞坏。
 * 所以这里改成**一次性把需要的几块瓦片拼成一张位图**：它就是个图片，天然不吃手势、
 * 不占线程、切页也不用重建地图控件。
 *
 * ## 取图规则
 * 固定 [ZOOM] 级、屏幕像素大小 [sizePx]（96dp × 屏幕密度）。以车辆坐标为中心，
 * 算出需要哪几块瓦片（通常 2×2），逐块取回后按原生像素 1:1 贴到画布上 ——
 * 1:1 意味着缩放屏上不会有二次放大的糊。
 *
 * ## 没有 Key 也能出图
 * 瓦片走的是高德公开的栅格地址（见 [AmapTileSource]），不需要 Web 服务 Key。
 * 这正是这次改造的目的：原来没配 Key 的用户，卡片左边永远是个"未配置 Key"的空框。
 */
object MiniTileMap {

    /** 缩略图层级：17 级在 31°N 约 1.02 米/像素，288px 宽的图正好装下约 290 米。 */
    const val ZOOM = 17

    /** 瓦片边长（高德这套地址固定 256）。 */
    const val TILE = 256

    /** 内存里最多留这么多块瓦片：一张缩略图 2×2，够覆盖十来次页面切换。 */
    private const val CACHE_TILES = 32

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /** LRU：`accessOrder=true` 的 LinkedHashMap，读一次就挪到队尾。 */
    private val cache = object : LinkedHashMap<String, Bitmap>(CACHE_TILES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > CACHE_TILES
    }

    /** 需要渲染的瓦片范围（含首尾），配合像素偏移把瓦片贴到画布上。 */
    data class TileRange(
        val zoom: Int,
        val minCol: Int,
        val maxCol: Int,
        val minRow: Int,
        val maxRow: Int,
        /** 视口左上角在「全局瓦片像素坐标系」里的位置。 */
        val originX: Double,
        val originY: Double,
    ) {
        val cols: Int get() = maxCol - minCol + 1
        val rows: Int get() = maxRow - minRow + 1
        val tileCount: Int get() = cols * rows
    }

    /**
     * 算出中心点 (lat, lng)、边长 [sizePx] 的视口需要哪些瓦片。
     *
     * 纯计算，不碰网络也不碰 Bitmap，所以可以单测；跨瓦片边界的取整是这里最容易错的地方
     * （右边 / 下边要减一个极小值，否则正好压在边界上会多取一列）。
     */
    fun tileRange(latitude: Double, longitude: Double, zoom: Int, sizePx: Int): TileRange {
        // 全局像素坐标：zoom 级世界图是 (n × 256) 见方
        val (fx, fy) = globalPixel(latitude, longitude, zoom)
        val left = fx - sizePx / 2.0
        val top = fy - sizePx / 2.0
        val right = left + sizePx
        val bottom = top + sizePx
        val eps = 1e-6
        return TileRange(
            zoom = zoom,
            minCol = floor(left / TILE).toInt(),
            maxCol = floor((right - eps) / TILE).toInt(),
            minRow = floor(top / TILE).toInt(),
            maxRow = floor((bottom - eps) / TILE).toInt(),
            originX = left,
            originY = top,
        )
    }

    /**
     * Web Mercator 全局像素坐标（zoom 级世界图边长 = 2^zoom × [TILE]）。
     *
     * 纯计算，行程地图按包围盒取景时也要用同一套公式，所以拎出来共用，别各自抄一份。
     * 车端坐标已经是 GCJ-02，高德瓦片也是 GCJ-02 切图 —— **绝不能套 ChinaCoordinate**。
     */
    fun globalPixel(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
        val n = 1 shl zoom
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val x = (longitude + 180.0) / 360.0 * n * TILE
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * TILE
        return x to y
    }

    /**
     * 渲染一张缩略图：取瓦片 → 贴图 → 叠夜间滤镜。
     *
     * 失败（断网、单块瓦片 404）时返回已经画上的部分：哪怕只有一块，也比一块空白强。
     * 一块都没取到时返回 null，调用方继续用占位图。
     */
    fun render(
        latitude: Double,
        longitude: Double,
        sizePx: Int,
        dark: Boolean,
        marker: Bitmap?,
    ): Bitmap? {
        if (sizePx <= 0) return null
        val range = tileRange(latitude, longitude, ZOOM, sizePx)
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (dark) paint.colorFilter = nightFilter()

        var drawn = 0
        for (row in range.minRow..range.maxRow) {
            for (col in range.minCol..range.maxCol) {
                val tile = load(col, row) ?: continue
                val x = (col * TILE - range.originX).toFloat()
                val y = (row * TILE - range.originY).toFloat()
                canvas.drawBitmap(tile, x, y, paint)
                drawn++
            }
        }
        if (drawn == 0) return null

        // 车标永远在正中央：视口就是围着车建的
        if (marker != null) {
            canvas.drawBitmap(
                marker,
                (sizePx - marker.width) / 2f,
                (sizePx - marker.height) / 2f,
                null,
            )
        }
        return out
    }

    private fun load(col: Int, row: Int): Bitmap? = tile(ZOOM, col, row)

    /**
     * 取一块瓦片：内存缓存 → 网络。失败返回 null（不写缓存，下次还能重试）。
     * 行程地图要按包围盒换层级，所以 zoom 是参数；缓存 key 也带上 zoom，别把
     * 14 级的图当 17 级贴上去。
     */
    fun tile(zoom: Int, col: Int, row: Int): Bitmap? {
        val n = 1 shl zoom
        if (col !in 0 until n || row !in 0 until n) return null
        val key = "$zoom/$col/$row"
        synchronized(cache) { cache[key]?.let { return it } }
        return runCatching {
            val url = AmapTileSource.road.getTileURLString(
                MapTileIndex.getTileIndex(zoom, col, row),
            )
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()?.also { bitmap ->
            synchronized(cache) { cache[key] = bitmap }
        }
    }

    /**
     * 夜间滤镜：灰度 + 反相 + 拉对比，和找车页的 `nightTint` 是同一套参数。
     *
     * 这里复制一份而不是共用，是因为那边是 `ui` 包里的 Compose 侧常量 —— 让 map 层
     * 反过来依赖 ui 层会把依赖方向搞乱。
     */
    internal fun nightFilter(): ColorMatrixColorFilter {
        val k = 1.6f
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -0.213f * k, -0.715f * k, -0.072f * k, 0f, 255f * k,
                    -0.213f * k, -0.715f * k, -0.072f * k, 0f, 255f * k,
                    -0.213f * k, -0.715f * k, -0.072f * k, 0f, 255f * k,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
}
