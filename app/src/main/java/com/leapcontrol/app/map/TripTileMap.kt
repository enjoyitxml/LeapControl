package com.leapcontrol.app.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.floor

/**
 * 把一段行程的稀疏采样点画成一张**静态地图轨迹**。
 *
 * ## 为什么还是拼瓦片，不用 MapView
 * 行程清单在 LazyColumn 里，`MapView` 会抢走单指拖动（项目踩过这个坑，见
 * [MiniTileMap] 的类注释）。这里跟首页位置缩略图一样：一次性把需要的瓦片拼成一张
 * Bitmap，叠上折线后当普通图片显示 —— 不吃手势、不占 UI 线程、不需要高德 Key。
 *
 * ## 数据口径（界面上必须同时写清楚）
 * 折线是**采样点之间的直线连接**，不是车实际走过的路。零控只在车况刷新时拿一帧快照，
 * 60 秒一个点，中间拐了几个弯根本不知道。所以这张图只能叫「轨迹示意」，
 * 不能冒充导航轨迹，也不能拿去算路线里程。
 *
 * ## 坐标
 * 车端上报的就是 GCJ-02，高德瓦片也是 GCJ-02 切图 —— **绝不能套 ChinaCoordinate**。
 * 层级封顶 18（z=19 高德只回纯色空图），瓦片固定 `scl=1` 的 256 带字图。
 */
object TripTileMap {

    const val TILE = 256
    const val MIN_ZOOM = 3
    const val MAX_ZOOM = 18
    /** 一个坐标点时别贴到街道级，给点周边参照。 */
    const val SINGLE_POINT_ZOOM = 16
    /** 路线四周留白，避免起终点贴边被圆点裁掉。 */
    const val PADDING_PX = 32
    /** 视口最多取这么多块瓦片，防止极端数据一次发几十个请求。 */
    const val MAX_TILES = 24

    data class Point(val lat: Double, val lng: Double)

    /** 取景结果：层级 + 视口左上角在全局像素坐标系里的位置。 */
    data class View(
        val zoom: Int,
        val originX: Double,
        val originY: Double,
        val widthPx: Int,
        val heightPx: Int,
    )

    /**
     * 按包围盒自动选层级：从 18 级往下找，第一个能把整条路线连留白一起装进视口的层级。
     * 纯函数，不碰 Bitmap 也不联网，可在 JVM 单测里守「远近都能装下」。
     */
    fun view(points: List<Point>, widthPx: Int, heightPx: Int): View? {
        val valid = points.filter {
            it.lat.isFinite() && it.lng.isFinite() &&
                it.lat in -85.0..85.0 && it.lng in -180.0..180.0
        }
        if (valid.isEmpty() || widthPx <= 0 || heightPx <= 0) return null
        val pixels = valid.map { MiniTileMap.globalPixel(it.lat, it.lng, MAX_ZOOM) }
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        val minY = pixels.minOf { it.second }
        val maxY = pixels.maxOf { it.second }

        val spanX = maxX - minX
        val spanY = maxY - minY
        val zoom = if (valid.size < 2 || (spanX < 1.0 && spanY < 1.0)) {
            SINGLE_POINT_ZOOM
        } else {
            var picked = MIN_ZOOM
            for (z in MAX_ZOOM downTo MIN_ZOOM) {
                val scale = 1.0 / (1 shl (MAX_ZOOM - z)).toDouble()
                if (spanX * scale + 2 * PADDING_PX <= widthPx &&
                    spanY * scale + 2 * PADDING_PX <= heightPx
                ) {
                    picked = z
                    break
                }
            }
            picked
        }

        val scale = 1.0 / (1 shl (MAX_ZOOM - zoom)).toDouble()
        val centerX = (minX + maxX) / 2.0 * scale
        val centerY = (minY + maxY) / 2.0 * scale
        return View(
            zoom = zoom,
            originX = centerX - widthPx / 2.0,
            originY = centerY - heightPx / 2.0,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }

    /**
     * 渲染：底图 → 折线（先白描边再主色）→ 起终点圆点。
     * 必须在 IO 线程调用：取瓦片是同步网络请求。一块瓦片都没取到就返回 null，
     * 调用方继续显示占位说明，而不是给一张纯色空图。
     */
    fun render(
        points: List<Point>,
        widthPx: Int,
        heightPx: Int,
        dark: Boolean,
        lineColor: Int,
        strokePx: Float,
        dotPx: Float,
        /**
         * 高德导航补齐后的路线点。null = 只按采样点直线连（没 Key / 请求失败）。
         * 有它时取景和画线都用它，但起终点圆点仍然落在真实采样点上。
         */
        route: List<Point>? = null,
    ): Bitmap? {
        val line = route?.takeIf { it.size >= 2 } ?: points
        val view = view(line, widthPx, heightPx) ?: return null
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(if (dark) Color.rgb(28, 30, 34) else Color.rgb(238, 240, 238))

        val eps = 1e-6
        val minCol = floor(view.originX / TILE).toInt()
        val maxCol = floor((view.originX + widthPx - eps) / TILE).toInt()
        val minRow = floor(view.originY / TILE).toInt()
        val maxRow = floor((view.originY + heightPx - eps) / TILE).toInt()
        val count = (maxCol - minCol + 1) * (maxRow - minRow + 1)
        if (count <= 0 || count > MAX_TILES) return null

        val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (dark) tilePaint.colorFilter = MiniTileMap.nightFilter()
        var drawn = 0
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                val tile = MiniTileMap.tile(view.zoom, col, row) ?: continue
                canvas.drawBitmap(
                    tile,
                    (col * TILE - view.originX).toFloat(),
                    (row * TILE - view.originY).toFloat(),
                    tilePaint,
                )
                drawn++
            }
        }
        if (drawn == 0) return null

        val coords = line.mapNotNull { p ->
            if (!p.lat.isFinite() || !p.lng.isFinite() ||
                p.lat !in -85.0..85.0 || p.lng !in -180.0..180.0
            ) return@mapNotNull null
            val (x, y) = MiniTileMap.globalPixel(p.lat, p.lng, view.zoom)
            (x - view.originX).toFloat() to (y - view.originY).toFloat()
        }
        if (coords.isEmpty()) return out

        val path = Path().apply {
            coords.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        // 白描边让主色折线在深色反相底图和浅色路网上都看得清
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx + 3f
            color = Color.WHITE
            alpha = 220
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = lineColor
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        })

        // 起点：白底 + 主色芯；终点：实心主色。都用白圈跟底图分开。
        // 端点取**真实采样点**的首末，不用 route 的首末：路线是「最合理的一条路」，
        // 端点必须钉在车真的停过的地方。
        val startPoint = points.firstOrNull() ?: line.first()
        val endPoint = points.lastOrNull() ?: line.last()
        project(startPoint, view)?.let { endpoint(canvas, it, lineColor, dotPx, start = true) }
        if (startPoint != endPoint) {
            project(endPoint, view)?.let { endpoint(canvas, it, lineColor, dotPx, start = false) }
        }
        return out
    }

    /** 经纬度 → 视口内像素坐标；越界 / 非法坐标返回 null（宁可少画一个点）。 */
    private fun project(point: Point, view: View): Pair<Float, Float>? {
        if (!point.lat.isFinite() || !point.lng.isFinite() ||
            point.lat !in -85.0..85.0 || point.lng !in -180.0..180.0
        ) return null
        val (x, y) = MiniTileMap.globalPixel(point.lat, point.lng, view.zoom)
        return (x - view.originX).toFloat() to (y - view.originY).toFloat()
    }

    private fun endpoint(
        canvas: Canvas,
        at: Pair<Float, Float>,
        lineColor: Int,
        dotPx: Float,
        start: Boolean,
    ) {
        val (x, y) = at
        canvas.drawCircle(x, y, dotPx + 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        })
        canvas.drawCircle(x, y, dotPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (start) Color.WHITE else lineColor
        })
        if (start) {
            canvas.drawCircle(x, y, (dotPx - 2f).coerceAtLeast(1f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
            })
        }
    }
}
