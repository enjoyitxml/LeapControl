package com.leapcontrol.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 底部默认提示：整页壁纸的用法。
 *
 * 首页上半屏是车名与车模、下半屏会被一排卡片压住，所以「主体偏上 + 下半屏留空」
 * 是唯一能既看清画面又不挡字的位置。相伴卡是另一套版式，调用方会换掉这句。
 */
const val DEFAULT_HINT =
    "建议把画面主体放在上半屏、下半屏尽量留空：首页上半屏是车名与车模，下半屏会被卡片盖住"

/**
 * 壁纸裁切页。
 *
 * 为什么自己实现而不用系统相册的裁切：
 *  - 系统裁切是自由比例，出来的图不一定铺得满整屏，边上会留白或被迫压扁；
 *  - 这里两张图的用途是固定的（整页壁纸 / 3:1 的相伴卡），框按用途给死，
 *    用户只需要「挪一挪、放大缩小」，拖到哪块就留哪块，不用再理解一遍比例。
 *
 * 交互只有三个：单指拖动、双指缩放、右上角「完成」。
 * 缩放范围 1x~3x —— 1x 就是「刚好铺满裁切框」，再缩小会露出底色，没有意义。
 */
@Composable
fun WallpaperCropDialog(
    uri: Uri?,
    /** 裁切框宽高比（宽 / 高）。壁纸给屏幕比例，相伴卡给 3:1。 */
    aspectRatio: Float,
    title: String,
    /** 输出图的宽度上限（px）：壁纸 1080、相伴卡 1080。 */
    outputWidth: Int,
    /**
     * 底部第二行提示：这张图将来压在什么版式上、主体该往哪放。
     *
     * 两种用途差别很大（整页壁纸被下半屏的卡片盖住；3:1 卡片右侧才留白），
     * 所以由调用方给，不在这里写死一句。
     */
    hint: String = DEFAULT_HINT,
    onDone: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    // 相册原图可能 4000px 起步，先按「目标宽度的 2 倍」采样解码：
    // 缩放到 3 倍也还有余量，而内存只有原图的几分之一
    LaunchedEffect(uri) {
        if (uri == null) {
            failed = true
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = AvatarStore.sampleSizeFor(w, h, target = outputWidth * 2)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
        source = bitmap
        failed = bitmap == null
    }

    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    // 裁切框实际像素尺寸：拖动/缩放的边界要靠它算
    var frame by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(enabled = true, onBack = onCancel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---- 顶部操作条 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "取消",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "完成",
                        color = Color(0xFF7ED0A2),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = source != null) {
                                val bmp = source ?: return@clickable
                                WallpaperCropMath.crop(
                                    source = bmp,
                                    frameWidth = frame.width,
                                    frameHeight = frame.height,
                                    zoom = zoom,
                                    offset = offset,
                                    outputWidth = outputWidth,
                                )?.let(onDone)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }

                // ---- 裁切区 ----
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // 框尺寸统一由 WallpaperCropMath 算（宽 88% / 高 78% 两个约束取更紧的那个）
                    val (frameWidth, frameHeight) = WallpaperCropMath.frameSizeDp(
                        availableWidth = maxWidth.value,
                        availableHeight = maxHeight.value,
                        aspectRatio = aspectRatio,
                    )

                    Box(
                        modifier = Modifier
                            .width(frameWidth.dp)
                            .height(frameHeight.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141414))
                            .onSizeChanged { frame = it },
                    ) {
                        val bmp = source
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                                    // 拖动与双指缩放一起处理：单指时 zoom 不变，等价于平移
                                    .pointerInput(bmp, frame) {
                                        detectTransformGestures { _, pan, zoomChange, _ ->
                                            val newZoom = (zoom * zoomChange).coerceIn(1f, 3f)
                                            // 缩放时以框中心为锚，平移量要跟着缩放一起放大，
                                            // 否则放大后手指位置会「甩」出去
                                            val scaled = offset * (newZoom / zoom)
                                            val limitX = max(0f, (frame.width * (newZoom - 1f)) / 2f)
                                            val limitY = max(0f, (frame.height * (newZoom - 1f)) / 2f)
                                            zoom = newZoom
                                            offset = Offset(
                                                x = (scaled.x + pan.x).coerceIn(-limitX, limitX),
                                                y = (scaled.y + pan.y).coerceIn(-limitY, limitY),
                                            )
                                        }
                                    },
                            )
                        } else if (failed) {
                            Text(
                                "这张图读不出来，换一张试试",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 16.dp),
                            )
                        } else {
                            Text(
                                "正在读取…",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        // 框线：白色细描边，压在图上也能看清边界（框内即结果）
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                        )
                    }
                }

                // ---- 底部提示 ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "双指缩放，单指拖动，框里的部分就是最终效果",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        hint,
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 裁切换算：把「屏幕上看到的框」映射回原图的像素区域。
 *
 * 单独抽出来是为了能单测 —— 这段全是反算，缩放加平移叠一起最容易差一个符号，
 * 表现就是「保存下来的图和预览差半屏」。
 *
 * 约定（与 UI 层的 graphicsLayer 完全一致）：
 *  · 图以 `ContentScale.Crop` 铺满裁切框，再叠加 [zoom] 缩放与 [offset] 平移；
 *  · 缩放与平移都以**框中心**为锚点。
 */
object WallpaperCropMath {

    /**
     * @param frameWidth/frameHeight 裁切框的像素尺寸
     * @param zoom 1f~3f
     * @param offset 平移量（像素，正方向与屏幕一致）
     * @return 裁切后的 Bitmap；参数不合法或算出来是空区域时返回 null
     */
    fun crop(
        source: Bitmap,
        frameWidth: Int,
        frameHeight: Int,
        zoom: Float,
        offset: Offset,
        outputWidth: Int,
    ): Bitmap? {
        if (frameWidth <= 0 || frameHeight <= 0 || source.width <= 0 || source.height <= 0) return null
        val z = zoom.coerceIn(1f, 3f)

        // Crop：先让图刚好盖满框
        val baseScale = max(
            frameWidth.toFloat() / source.width,
            frameHeight.toFloat() / source.height,
        )
        val scale = baseScale * z

        // 框中心对应到原图的哪一点：先扣掉平移，再除回缩放
        val visibleWidth = frameWidth / scale
        val visibleHeight = frameHeight / scale
        val centerX = source.width / 2f - offset.x / scale
        val centerY = source.height / 2f - offset.y / scale

        var left = (centerX - visibleWidth / 2f).toInt()
        var top = (centerY - visibleHeight / 2f).toInt()
        var width = visibleWidth.toInt()
        var height = visibleHeight.toInt()

        // 边界收口：浮点误差会让取整后的区域越界 1px，createBitmap 直接抛异常
        width = width.coerceIn(1, source.width)
        height = height.coerceIn(1, source.height)
        left = left.coerceIn(0, source.width - width)
        top = top.coerceIn(0, source.height - height)

        val cropped = runCatching {
            Bitmap.createBitmap(source, left, top, width, height)
        }.getOrNull() ?: return null

        // 统一输出宽度：壁纸存 1080 宽，相伴卡也存 1080 宽（高度随比例）
        if (outputWidth <= 0 || cropped.width == outputWidth) return cropped
        val targetHeight = (outputWidth.toFloat() * cropped.height / cropped.width).toInt()
            .coerceAtLeast(1)
        val scaled = runCatching {
            Bitmap.createScaledBitmap(cropped, outputWidth, targetHeight, true)
        }.getOrNull() ?: cropped
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /**
     * 裁切框尺寸（dp）：宽不超过可用宽度的 88%，高不超过 78%，再按比例反推。
     *
     * 3:1 这种很扁的比例永远受宽度约束，壁纸这种竖长比例则可能撞到高度上限，
     * 所以两个约束都要试一遍，取更小的那个。
     */
    fun frameSizeDp(
        availableWidth: Float,
        availableHeight: Float,
        aspectRatio: Float,
    ): Pair<Float, Float> {
        if (aspectRatio <= 0f) return 0f to 0f
        val byWidth = availableWidth * 0.88f
        val byHeight = availableHeight * 0.78f * aspectRatio
        val width = min(byWidth, byHeight)
        return width to width / aspectRatio
    }
}
