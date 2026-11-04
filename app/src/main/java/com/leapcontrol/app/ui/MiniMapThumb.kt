package com.leapcontrol.app.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.map.MiniTileMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 首页「车辆位置」卡左边那张 96dp 方图 —— 瓦片缩略图，不需要高德 Key。
 *
 * 画法见 [MiniTileMap]：固定 17 级、把需要的那两三块瓦片拼成一张位图再贴上来。
 * 这里只负责「什么时候去拼」和「拼出来之后怎么画」：
 * - key 是（坐标 + 像素边长 + 深色 + 主题色），任何一个变了才重新拼；
 * - 拼图在 IO 线程，结果进 `produceState`；同一进程里瓦片有内存缓存，来回切 Tab 不再联网。
 *
 * ## 为什么不是 MapView
 * 这张卡在首页的滚动列里。`MapView` 会把单指拖动全吃掉 —— 想滑首页变成拖地图。
 * 96dp 的缩略图只需要"能看"，不需要"能操作"，所以干脆退化成一张图。
 */
@Composable
internal fun MiniMapThumb(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    // 96dp = 卡片里那个方框的边长；按屏幕密度换成像素，瓦片才能按 1:1 贴上去不糊
    val sizePx = with(LocalDensity.current) { THUMB_SIDE.roundToPx() }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        latitude,
        longitude,
        sizePx,
        C.isDark,
        C.accent,
    ) {
        value = withContext(Dispatchers.IO) {
            val marker = (carMarkerIcon(context, C.accent.toArgb(), sizeDp = 22) as? BitmapDrawable)
                ?.bitmap
            runCatching {
                MiniTileMap.render(
                    latitude = latitude,
                    longitude = longitude,
                    sizePx = sizePx,
                    dark = C.isDark,
                    marker = marker,
                )
            }.getOrNull()
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            contentDescription = "车辆位置地图",
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        // 还没拼出来（或一块瓦片都没取到）：给一句"在加载"，别留一块纯色
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "地图加载中",
                color = C.textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 和卡片里那个方框一致（`VehicleLocationCard` 的 `THUMB_SIZE`）。 */
private val THUMB_SIDE = 96.dp
