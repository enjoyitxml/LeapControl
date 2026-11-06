package com.leapcontrol.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AmapPoint
import com.leapcontrol.app.map.TripRoute
import com.leapcontrol.app.map.TripTileMap
import com.leapcontrol.app.trip.TripSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 一次渲染的产物：位图 + 到底有没有用上导航路线（决定底下那句口径怎么写）。 */
private data class TripMapState(val bitmap: Bitmap?, val routed: Boolean)

/**
 * 一段行程的地图轨迹卡：静态位图 + 起终点 + 折线 + 高德署名 + 口径说明。
 *
 * 复用 [MiniMapThumb] 那套「IO 线程拼瓦片 → produceState 出 Bitmap」的做法：
 *  - key 是（采样点列表 + 视口像素 + 深浅主题 + 主题色 + Key），不随重组反复联网；
 *  - 返回的是**普通图片**，不是 MapView —— 行程清单在 LazyColumn 里，MapView 会抢滚动手势；
 *  - 瓦片走高德公开栅格地址，不需要 Web Key；**只有「把直线补成道路」这一步要 Key**
 *    （[TripRoute] 调驾车路径规划）。没有 Key 或请求失败就退回直线，图上照实写。
 *
 * 两种画法的口径必须分开写，否则用户会把规划路线当成车辆真实轨迹：
 *  - 有 Key：「采样点之间按高德导航路线补齐，非实际行驶轨迹」；
 *  - 没 Key：「采样点直线连接示意」。
 */
@Composable
internal fun TripMapPreview(
    points: List<TripSample>,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 168.dp,
    /** 高德 Web Key：有它才把采样点之间的直线换成真实道路。 */
    amapKey: String = "",
    /** 自定义口径文案；null 时按「有没有用上导航路线」自动选一句。 */
    caption: String? = null,
) {
    val C = LocalAppColors.current
    val density = LocalDensity.current
    val dark = C.isDark
    val accentArgb = C.accent.toArgb()
    val strokePx = with(density) { 4.dp.toPx() }
    val dotPx = with(density) { 7.dp.toPx() }
    val coords = remember(points) {
        points.mapNotNull { p ->
            val lat = p.latitude
            val lng = p.longitude
            if (lat != null && lng != null) TripTileMap.Point(lat, lng) else null
        }
    }
    val routeKey = remember(coords) {
        // 采样点变了才重新规划；同一段来回展开不重复吃额度
        coords.joinToString(";") { "${it.lat},${it.lng}" }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        var routed by remember(routeKey, amapKey) { mutableStateOf(false) }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(C.barTrack.copy(alpha = 0.45f)),
        ) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            val heightPx = with(density) { mapHeight.roundToPx() }
            val state by produceState<TripMapState?>(
                initialValue = null,
                routeKey,
                widthPx,
                heightPx,
                dark,
                accentArgb,
                amapKey,
            ) {
                value = null
                value = withContext(Dispatchers.IO) {
                    if (coords.isEmpty() || widthPx <= 0 || heightPx <= 0) {
                        TripMapState(null, routed = false)
                    } else {
                        // 先规划路线：多花一次网络请求，但画出来的是路而不是「穿楼的直线」。
                        // 失败（没 Key / 没网 / 额度用尽）就退回直线，绝不因此不画图。
                        val route = if (amapKey.isNotBlank() && coords.size >= 2) {
                            runCatching {
                                TripRoute.build(
                                    amapKey,
                                    coords.map { AmapPoint(it.lat, it.lng) },
                                )
                            }.getOrNull()?.map { TripTileMap.Point(it.latitude, it.longitude) }
                        } else null
                        val bitmap = runCatching {
                            TripTileMap.render(
                                points = coords,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                dark = dark,
                                lineColor = accentArgb,
                                strokePx = strokePx,
                                dotPx = dotPx,
                                route = route,
                            )
                        }.getOrNull()
                        TripMapState(bitmap, routed = route != null)
                    }
                }
            }

            // 瓦片全 404 / 断网时不能一直停在「加载中」，给一句能看懂的兜底。
            val timedOut = remember(routeKey, widthPx, heightPx, dark, accentArgb) {
                mutableStateOf(false)
            }
            LaunchedEffect(routeKey, widthPx, heightPx, dark, accentArgb) {
                timedOut.value = false
                delay(15_000)
                timedOut.value = true
            }

            val image = state?.bitmap
            // 是否用上了导航路线要提到 Box 外面给底下的说明用
            LaunchedEffect(state) { routed = state?.routed == true }
            if (image != null) {
                Image(
                    bitmap = remember(image) { image.asImageBitmap() },
                    contentDescription = "这一段行程的地图轨迹",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            coords.isEmpty() -> "这一段没有读到坐标，画不出地图"
                            timedOut.value -> "地图暂时取不到，稍后再试"
                            else -> "地图加载中"
                        },
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                text = "© 高德地图",
                color = C.textSecondary,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(C.panelBg.copy(alpha = 0.85f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = caption ?: if (routed) {
                "采样点之间按高德导航路线补齐 · 不是车辆实际行驶轨迹"
            } else {
                "采样点直线连接示意，非实际行驶路线"
            },
            color = C.textSecondary,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
    }
}

/**
 * 行程清单左侧的方形轨迹缩略图（方案 E：一段一张）。
 *
 * 跟 [TripMapPreview] 的区别：
 *  - **不走高德路径规划**。清单里一个月可能有几十段，每段都请求一次驾车路径既慢又吃额度；
 *    66dp 的方图只需要「看得出这趟往哪边开」，采样点连线就够了。
 *  - 不带署名与口径说明（那两行在展开后的完整地图里有），形状只有 66dp，塞不下。
 *
 * 取不到坐标（老数据 / 地库没信号）时给一句「无轨迹」，不留一块空白 —— 空白会被当成加载中。
 */
@Composable
internal fun TripThumbMap(
    points: List<TripSample>,
    modifier: Modifier = Modifier,
    side: Dp = 66.dp,
) {
    val C = LocalAppColors.current
    val density = LocalDensity.current
    val dark = C.isDark
    val accentArgb = C.accent.toArgb()
    val sidePx = with(density) { side.roundToPx() }
    val strokePx = with(density) { 2.2.dp.toPx() }
    val dotPx = with(density) { 3.6.dp.toPx() }
    val coords = remember(points) {
        points.mapNotNull { p ->
            val lat = p.latitude
            val lng = p.longitude
            if (lat != null && lng != null) TripTileMap.Point(lat, lng) else null
        }
    }
    val routeKey = remember(coords) { coords.joinToString(";") { "${it.lat},${it.lng}" } }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        routeKey,
        sidePx,
        dark,
        accentArgb,
    ) {
        value = withContext(Dispatchers.IO) {
            if (coords.isEmpty() || sidePx <= 0) {
                null
            } else {
                runCatching {
                    TripTileMap.render(
                        points = coords,
                        widthPx = sidePx,
                        heightPx = sidePx,
                        dark = dark,
                        lineColor = accentArgb,
                        strokePx = strokePx,
                        dotPx = dotPx,
                    )
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .size(side)
            .clip(RoundedCornerShape(8.dp))
            .background(C.barTrack.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = remember(image) { image.asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = if (coords.isEmpty()) "无轨迹" else "加载中",
                color = C.textSecondary.copy(alpha = 0.65f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
