package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.widget.VehicleWidget
import com.leapcontrol.app.widget.WallpaperCrop
import com.leapcontrol.app.widget.WidgetStyle
import com.leapcontrol.app.widget.WidgetWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 「个性化 → 桌面小组件」：背景 + 三组配色。
 *
 * ## 背景只有两档
 * 内置风景图库已经整套删掉（用户要的不是「我替你挑好 10 张」，而是「默认干净、想要自己传」）：
 * - **简约**：跟随系统深浅的两张纯色卡，默认就是它；
 * - **自定义**：从相册选一张图 → 现场拖动缩放到满意 → 存进私有目录。
 *
 * ## 为什么还要三组颜色
 * 一旦换成自定义壁纸，默认的深色字/灰图标几乎必然看不清。与其自动猜一个（猜错更难受），
 * 不如把「按钮 / 文字 / 车标」三个色槽都交给用户，默认「自动」= 跟系统深浅。
 */
@Composable
fun WidgetStyleSettings(runtime: AppRuntime) {
    val context = LocalContext.current
    val C = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(WidgetStyle.wallpaper(context)) }
    var rev by remember { mutableIntStateOf(0) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var pendingSave by remember { mutableStateOf(false) }

    fun refresh() {
        rev += 1
        VehicleWidget.render(context, runtime)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadPick(context, uri) }
            if (bitmap == null) {
                Toast.makeText(context, "这张图片读不出来，换一张试试", Toast.LENGTH_SHORT).show()
            } else {
                cropSource = bitmap
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- 背景 ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WallpaperCell(
                modifier = Modifier.weight(1f),
                title = "简约",
                selected = mode == WidgetStyle.WALLPAPER_DEFAULT,
                onClick = {
                    WidgetWallpaper.useDefault(context)
                    mode = WidgetStyle.WALLPAPER_DEFAULT
                    refresh()
                },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF7FAFF), Color(0xFFE9F0FA)),
                            ),
                        ),
                )
            }
            WallpaperCell(
                modifier = Modifier.weight(1f),
                title = if (WidgetWallpaper.hasFile(context)) "自定义（点击更换）" else "自定义",
                selected = mode == WidgetStyle.WALLPAPER_CUSTOM,
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                val thumb = remember(rev) {
                    WidgetWallpaper.loadBitmap(context, 480)?.asImageBitmap()
                }
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(C.chipBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = C.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Text(
            if (WidgetWallpaper.hasFile(context)) {
                "点「自定义」可以重新选图并重新裁剪。"
            } else {
                "默认是跟随系统深浅的简约卡；想换成自己的图，点「自定义」从相册选。"
            },
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )

        // ---- 配色 ----
        ToneRow(
            label = "按钮颜色",
            desc = "底部五个按键的图标",
            slot = WidgetStyle.SLOT_BUTTON,
            onChanged = { refresh() },
        )
        ToneRow(
            label = "文字颜色",
            desc = "车名 / 续航 / 更新时间",
            slot = WidgetStyle.SLOT_TEXT,
            onChanged = { refresh() },
        )
        ToneRow(
            label = "零跑车标颜色",
            desc = "左上角车标",
            slot = WidgetStyle.SLOT_LOGO,
            onChanged = { refresh() },
        )

        if (WidgetStyle.hasAnyTone(context)) {
            Text(
                "恢复默认配色",
                color = C.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    WidgetStyle.resetTones(context)
                    refresh()
                },
            )
        }

        Text(
            "用了自定义壁纸时，建议把文字和车标改成浅色（深色壁纸）或深色（浅色壁纸），" +
                "否则会看不清。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }

    // ---- 裁剪 ----
    cropSource?.let { source ->
        WallpaperCropDialog(
            source = source,
            saving = pendingSave,
            onCancel = { cropSource = null },
            onDone = { cropped ->
                pendingSave = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { WidgetWallpaper.save(context, cropped) }
                    pendingSave = false
                    cropSource = null
                    if (ok) {
                        mode = WidgetStyle.WALLPAPER_CUSTOM
                        rev += 1
                        VehicleWidget.render(context, runtime)
                        Toast.makeText(context, "壁纸已应用", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "保存失败，换个位置再试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

/** 一张背景缩略图（简约 / 自定义）。 */
@Composable
private fun WallpaperCell(
    modifier: Modifier,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(WallpaperCrop.ASPECT)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) C.accent else C.border,
                    shape = shape,
                )
                .clickable(onClick = onClick),
        ) {
            content()
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            title,
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 一组色板：第一颗是「自动」，其余是预设色。 */
@Composable
private fun ToneRow(
    label: String,
    desc: String,
    slot: String,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val C = LocalAppColors.current
    var current by remember(slot) { mutableStateOf(WidgetStyle.tone(context, slot)) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = C.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(desc, color = C.textSecondary, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(8.dp))
        // 自动 + 9 个预设，分两行排（一行 6 颗）
        val entries = listOf(WidgetStyle.AUTO to null) + PRESET_COLORS.map { it to it }
        entries.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                row.forEach { (value, color) ->
                    Swatch(
                        color = color,
                        selected = current == value,
                        onClick = {
                            current = value
                            WidgetStyle.setTone(context, slot, value)
                            onChanged()
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .background(if (color == null) C.chipBg else Color(color))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) C.accent else C.border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) {
            // 「自动」：左深右浅的小方块示意「跟随系统深浅」
            Row(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))) {
                Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFF12141A)))
                Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFF7FAFF)))
            }
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = readableOn(color),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** 勾选图标的对比色：亮底配黑勾，暗底配白勾。 */
private fun readableOn(argb: Int): Color {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return if (luminance > 0.6) Color(0xFF12141A) else Color.White
}

/** 预设色：黑白灰 + 六个常用色，足够覆盖绝大多数壁纸。 */
private val PRESET_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFF12141A.toInt(),
    0xFF8A9099.toInt(),
    0xFF2AC06A.toInt(),
    0xFF3B82F6.toInt(),
    0xFFF59E0B.toInt(),
    0xFFEF4444.toInt(),
    0xFFF472B6.toInt(),
    0xFF14B8A6.toInt(),
)

/**
 * 裁剪弹窗：拖动平移、双指缩放，框里看到什么就存什么。
 *
 * 用 [Dialog] 而不是自绘全屏遮罩，是因为它挂在当前 Activity 的窗口上，
 * 不受个性化页那个纵向滚动的 Column 约束（自绘遮罩放在滚动容器里会被无界约束压扁）。
 */
@Composable
private fun WallpaperCropDialog(
    source: Bitmap,
    saving: Boolean,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit,
) {
    val C = LocalAppColors.current
    var userScale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val image = remember(source) { source.asImageBitmap() }

    // 裁剪框尺寸：屏幕宽 - 左右各 16dp。先算成 px，绘制和导出共用同一组值 ——
    // 两处各算一遍的话，裁剪出来的和框里看到的会差一截（用户拖到车头，存下来却是车尾）。
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val boxW = with(density) { (screenWidthDp.dp - 32.dp).toPx() }
    val boxH = boxW / WallpaperCrop.ASPECT
    val boxHeightDp = with(density) { boxH.toDp() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1014))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "取消",
                    color = Color(0xFF9BA5B2),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(enabled = !saving, onClick = onCancel),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "裁剪壁纸",
                    color = Color(0xFFF2F5FA),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (saving) "保存中…" else "完成",
                    color = if (saving) Color(0xFF6C7480) else C.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !saving) {
                        onDone(renderCrop(source, boxW, boxH, userScale, offset))
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                val base = WallpaperCrop.coverScale(
                    source.width.toFloat(),
                    source.height.toFloat(),
                    boxW,
                    boxH,
                )
                val shape = RoundedCornerShape(16.dp)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(boxHeightDp)
                        .clip(shape)
                        .background(Color(0xFF15181D))
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (userScale * zoom).coerceIn(1f, 4f)
                                val clamped = WallpaperCrop.clampOffset(
                                    source.width.toFloat(),
                                    source.height.toFloat(),
                                    boxW,
                                    boxH,
                                    base * nextScale,
                                    offset.x + pan.x,
                                    offset.y + pan.y,
                                )
                                userScale = nextScale
                                offset = Offset(clamped.first, clamped.second)
                            }
                        },
                ) {
                    val total = base * userScale
                    val drawW = source.width * total
                    val drawH = source.height * total
                    drawImage(
                        image = image,
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            (size.width / 2f + offset.x - drawW / 2f).roundToInt(),
                            (size.height / 2f + offset.y - drawH / 2f).roundToInt(),
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(
                            drawW.roundToInt().coerceAtLeast(1),
                            drawH.roundToInt().coerceAtLeast(1),
                        ),
                        filterQuality = FilterQuality.High,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "拖动调整位置，双指缩放。框里看到的就是桌面上的效果。",
                color = Color(0xFF9BA5B2),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 把当前预览状态烘焙成一张 1440px 宽的位图。 */
private fun renderCrop(
    source: Bitmap,
    boxW: Float,
    boxH: Float,
    userScale: Float,
    offset: Offset,
): Bitmap {
    // 直接沿用预览时的框尺寸算：sourceRect 只依赖「框的宽高比 + 缩放 + 位移」，
    // 与框的绝对像素大小无关，所以预览和导出必然裁到同一块。
    val base = WallpaperCrop.coverScale(source.width.toFloat(), source.height.toFloat(), boxW, boxH)
    val rect = WallpaperCrop.sourceRect(
        imageW = source.width.toFloat(),
        imageH = source.height.toFloat(),
        boxW = boxW,
        boxH = boxH,
        scale = base * userScale,
        offsetX = offset.x,
        offsetY = offset.y,
    )
    val cropped = Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
    return Bitmap.createScaledBitmap(
        cropped,
        WallpaperCrop.OUT_WIDTH,
        WallpaperCrop.outputHeight(WallpaperCrop.OUT_WIDTH),
        true,
    )
}

/** 从相册 URI 读图，按最长边降到 [maxSide] 以内（原图动辄 4000px，没必要全解进内存）。 */
private fun loadPick(context: Context, uri: Uri, maxSide: Int = 1920): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(
            it,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }
}.getOrNull()
