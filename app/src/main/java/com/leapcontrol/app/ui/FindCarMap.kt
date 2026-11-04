package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.leapcontrol.app.R
import com.leapcontrol.app.map.AmapTileSource
import com.leapcontrol.app.map.MapScale
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * 找车页的交互地图（高德瓦片 + osmdroid）：能单指拖动、双指缩放、+/− 按钮、
 * 一键回到车辆。
 *
 * ## 为什么换掉静态图
 * 原来的高德静态地图是一张请求回来就固定的 PNG：zoom 被高德锁在 17，只能靠 `size`
 * 改取景，放大会糊，更没法拖动。想「看看车停在哪条路、周边是什么」时，只能跳高德 App。
 * 换成瓦片地图之后这些都不再是限制。
 *
 * ## 坐标系：不要纠偏
 * 车端上报的坐标是 GCJ-02，高德瓦片也是 GCJ-02 切图，**原样交给 MapView 就是对的**。
 * 这里绝不能套 [com.leapcontrol.app.ChinaCoordinate]（那是 WGS-84 → GCJ-02 的换算），
 * 否则整张图和车点会一起偏掉几百米。找车页原来那个「整张图偏了几百米？点这里纠偏」
 * 的手动开关也随这次换方案一起删了：地图、地址、导航目的地现在用的是同一套坐标，
 * 屏幕上不会有「车标压着马路却整体漂移」这种现象需要用户自救。
 *
 * ## 深色主题
 * 高德瓦片只有白天的图，没有夜间样式参数。静态图时代是逐像素翻明度（[com.leapcontrol.app.MapDarkTint]），
 * 但瓦片是每次滚动都会换的，逐像素处理来不及。这里改用 [ColorMatrix]：
 * 先按亮度压成灰阶、再反相、再拉对比，得到「深底 + 浅色路网」。
 * 代价是丢了绿地/水面的颜色 —— 夜间地图这么处理是常规做法，比原色反相后出现的紫色路面好得多。
 *
 * ## 触摸
 * 这一页的地图**不放在滚动容器里**（见 FindCarScreen 的布局）：MapView 要吃掉所有
 * 单指拖动，放进 verticalScroll 会导致「想拖地图结果页面在滚」。
 */
@Composable
internal fun FindCarMap(
    latitude: Double,
    longitude: Double,
    /**
     * 外部要求「重新对准车辆」的计数（刷新车况、换车都会 +1）。
     * 用它当 key：车真的挪了才动画过去，用户自己拖到别处看路时不会被拽回来。
     */
    recenterKey: Any,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    // 路网 / 卫星。不落盘：默认永远是路网（找车看路名比看屋顶有用），
    // 用户这次切到卫星，下次进来还是路网
    var satellite by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ensureMapConfig(ctx)
                MapView(ctx).apply {
                    setTileSource(AmapTileSource.road)
                    // 让 update 知道当前挂的是哪套瓦片，避免每次重组都重设一遍
                    tag = AmapTileSource.road.name()
                    // 单指拖动 + 双指缩放，这两条是用户要的全部
                    setMultiTouchControls(true)
                    // 256px 瓦片按屏幕密度放大，否则 3x 屏上道路名小到看不清（见 MapScale 注释）
                    setTilesScaledToDpi(true)
                    setUseDataConnection(true)
                    minZoomLevel = MapScale.MIN_ZOOM
                    maxZoomLevel = MapScale.MAX_ZOOM
                    // 内置的 +/- 按钮不要：按钮用 Compose 自己画，
                    // 内置那套是固定像素尺寸，在深色主题里也压不住
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    // 瓦片还没加载出来时的底色（无网 / 缩放过头时露出来的那块）；
                    // 同时补一层 View 背景：osmdroid 自己是白底，深色主题切进这一页会闪一下白
                    overlayManager.tilesOverlay.setLoadingBackgroundColor(C.panelBg.toArgb())
                    setBackgroundColor(C.panelBg.toArgb())
                    // 首次定位必须等一次布局：factory 里控件尺寸还是 0，
                    // 这时候 setCenter 会被 osmdroid 记成「无效视口」，首帧是一片空白
                    post {
                        controller.setZoom(MapScale.DEFAULT_ZOOM)
                        controller.setCenter(GeoPoint(latitude, longitude))
                    }
                    onResume()
                }.also { mapView = it }
            },
            update = { view ->
                // 主题切换时 factory 不会重跑，颜色/滤镜要在这儿补
                view.setBackgroundColor(C.panelBg.toArgb())
                view.overlayManager.tilesOverlay
                    .setLoadingBackgroundColor(C.panelBg.toArgb())
                val source = if (satellite) AmapTileSource.satellite else AmapTileSource.road
                // 只在真的换了图层时重设：直接重复 setTileSource 会让 osmdroid
                // 把整屏瓦片丢掉重下，来回切主题就会一直闪
                if (view.tag != source.name()) {
                    view.setTileSource(source)
                    view.tag = source.name()
                }
                view.overlayManager.tilesOverlay.setColorFilter(
                    // 卫星影像不翻色：本来偏暗，反相后像底片
                    if (C.isDark && !satellite) nightTint else null,
                )
            },
        )

        // 车辆标记：主题色圆底 + 白色车形。坐标变了重建一个，别只改 position ——
        // 图标颜色跟着主题走，重建比维护两套状态简单。
        // **必须把 mapView 写进 key**：首帧组合时它还是 null（factory 还没跑），
        // 少了这一项，factory 赋值引发的重组不会让本 effect 重跑，标记就永远加不上。
        LaunchedEffect(latitude, longitude, C.accent, mapView) {
            val view = mapView ?: return@LaunchedEffect
            view.overlays.removeAll { it is Marker }
            view.overlays.add(
                Marker(view).apply {
                    position = GeoPoint(latitude, longitude)
                    icon = carMarkerIcon(context, C.accent.toArgb())
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    // 点了不弹气泡：这里只有一个点，弹出来也是空的
                    setOnMarkerClickListener { _, _ -> true }
                },
            )
            view.invalidate()
        }

        // 车挪了 / 用户点了刷新：把视野拉回车上
        LaunchedEffect(recenterKey, mapView) {
            val view = mapView ?: return@LaunchedEffect
            view.post {
                view.controller.animateTo(GeoPoint(latitude, longitude), MapScale.DEFAULT_ZOOM, 450L)
            }
        }

        MapControls(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            satellite = satellite,
            onZoomIn = { mapView?.controller?.zoomIn() },
            onZoomOut = { mapView?.controller?.zoomOut() },
            onToggleLayer = { satellite = !satellite },
            onRecenter = {
                mapView?.controller?.animateTo(
                    GeoPoint(latitude, longitude),
                    MapScale.DEFAULT_ZOOM,
                    450L,
                )
            },
        )

        // 署名不是客气：瓦片来自高德，界面上得说出来
        Text(
            text = "© 高德地图",
            color = C.textSecondary.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 5.dp),
        )
    }

    // 页面关闭时必须 detach：osmdroid 的瓦片线程和缓存不清，切几次页面就会攒一堆
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
            mapView?.onDetach()
            mapView = null
        }
    }
}

/** 右侧竖排四个圆钮：放大 / 缩小 / 图层 / 回到车辆。样式沿用首页那几个圆形入口。 */
@Composable
private fun MapControls(
    modifier: Modifier = Modifier,
    satellite: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleLayer: () -> Unit,
    onRecenter: () -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapRoundButton(Icons.Filled.Add, "放大", onZoomIn)
        MapRoundButton(Icons.Filled.Remove, "缩小", onZoomOut)
        MapRoundButton(
            icon = Icons.Filled.Layers,
            description = if (satellite) "切回路网" else "切换到卫星图",
            onClick = onToggleLayer,
            // 卫星开着时按钮高亮，不然「我现在看的是哪一层」只能靠猜
            active = satellite,
        )
        MapRoundButton(Icons.Filled.MyLocation, "回到车辆", onRecenter)
    }
}

@Composable
private fun MapRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active) C.accent.copy(alpha = 0.22f) else C.panelBg.copy(alpha = 0.94f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = C.accent,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * 灰度 + 反相 + 拉对比的夜间滤镜。
 *
 * 矩阵前三行相同 = 按 Rec.601 亮度取灰阶，乘 -1.6、加 408，
 * 等价于 `(255 - 亮度) × 1.6`：纯白底 → 0（近黑），纯黑字 → 255（白字），
 * 中间的路缘线落在 60~90，正好成为「深底 + 浅线」。
 */
internal val nightTint: ColorMatrixColorFilter = run {
    val k = 1.6f
    val matrix = ColorMatrix(
        floatArrayOf(
            -0.213f * k, -0.715f * k, -0.072f * k, 0f, 255f * k,
            -0.213f * k, -0.715f * k, -0.072f * k, 0f, 255f * k,
            -0.213f * k, -0.715f * k, -0.072f * k, 0f, 255f * k,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    ColorMatrixColorFilter(matrix)
}

/**
 * 车辆标记：主题色圆 + 白描边 + 白色车形。默认直径 36dp（找车页大图用），
 * 首页 96dp 缩略图传 22dp —— 同一个图标缩到 1/4 面积时，描边和车形都得等比缩，
 * 不然小图上只剩一个白饼。
 */
internal fun carMarkerIcon(context: Context, accent: Int, sizeDp: Int = 36): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val ring = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(accent)
        // 描边 7%、车形内缩 28%（都是相对直径），36dp 时正好是原来的 2.5dp / 10dp
        setStroke((sizeDp * 0.07f * density).coerceAtLeast(1f).toInt(), Color.WHITE)
    }
    val layers = ArrayList<Drawable>(2)
    layers.add(ring)
    ContextCompat.getDrawable(context, R.drawable.ic_nav_car)?.mutate()?.let { car ->
        car.setTint(Color.WHITE)
        val inset = (sizeDp * 0.28f * density).toInt()
        layers.add(InsetDrawable(car, inset, inset, inset, inset))
    }
    val layer = LayerDrawable(layers.toTypedArray())
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    layer.setBounds(0, 0, size, size)
    layer.draw(canvas)
    return BitmapDrawable(context.resources, bitmap)
}

/**
 * osmdroid 的全局配置。必须在 `MapView(...)` 构造**之前**设好，否则它拿默认值建缓存目录。
 *
 * - `userAgentValue` 用包名：瓦片服务靠它识别客户端，留默认的 "osmdroid" 容易被限流；
 * - 缓存目录指到 `filesDir`：osmdroid 默认往外部存储根目录写，Android 10+ 上会被拒，
 *   表现是「每次进页面都重新下载瓦片」。指到私有目录后切页面/重进都是秒开。
 */
private var mapConfigReady = false

internal fun ensureMapConfig(context: Context) {
    if (mapConfigReady) return
    val cfg = Configuration.getInstance()
    cfg.userAgentValue = context.packageName
    val base = File(context.filesDir, "osmdroid")
    cfg.osmdroidBasePath = base
    cfg.osmdroidTileCache = File(base, "tiles")
    mapConfigReady = true
}
