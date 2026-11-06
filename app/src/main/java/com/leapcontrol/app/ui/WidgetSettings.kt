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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.widget.VehicleWidget
import com.leapcontrol.app.widget.WidgetStyle
import com.leapcontrol.app.widget.WidgetWallpaper

/**
 * 「个性化 → 桌面小组件」。
 *
 * 两套版式（素纸 / 沉浸）是两份独立的 RemoteViews 布局，选了立刻重画桌面上已有的卡片；
 * 沉浸样式额外给一个背景图入口 —— 素纸那张卡没有「底图」这个概念，选了也没用，所以只在 D 下出现。
 *
 * 缩略图是**示意图**不是真实渲染：真正的卡片要由桌面进程用 RemoteViews 画出来，
 * Compose 里再实现一遍只会跟实际效果不一致。这里只用色块交代「哪边是车模、下面几格」。
 */
@Composable
fun WidgetSettingsGroup() {
    val context = LocalContext.current
    val C = LocalAppColors.current
    var style by remember { mutableStateOf(WidgetStyle.style(context)) }
    var revision by remember { mutableIntStateOf(WidgetStyle.revision(context)) }
    var customBg by remember { mutableStateOf(WidgetWallpaper.exists(context)) }
    var ink by remember { mutableIntStateOf(WidgetStyle.ink(context)) }
    var ctrl by remember { mutableIntStateOf(WidgetStyle.ctrl(context)) }

    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val bitmap = decodeScaled(context, uri, 1600) ?: return@runCatching false
            WidgetWallpaper.save(context, bitmap)
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (ok) "背景已保存，桌面上立刻生效" else "这张图读不出来，换一张试试",
            Toast.LENGTH_SHORT,
        ).show()
        if (ok) {
            revision = WidgetStyle.revision(context)
            customBg = true
            VehicleWidget.render(context)
        }
    }

    WidgetSection(
        style = style,
        revision = revision,
        customBg = customBg,
        ink = ink,
        ctrl = ctrl,
        onSelectStyle = { picked ->
            style = picked
            WidgetStyle.setStyle(context, picked)
            VehicleWidget.render(context)
        },
        onPickInk = { color ->
            ink = color
            WidgetStyle.setInk(context, color)
            VehicleWidget.render(context)
        },
        onPickCtrl = { color ->
            ctrl = color
            WidgetStyle.setCtrl(context, color)
            VehicleWidget.render(context)
        },
        onResetColors = {
            WidgetStyle.resetColors(context)
            ink = WidgetStyle.ink(context)
            ctrl = WidgetStyle.ctrl(context)
            VehicleWidget.render(context)
            Toast.makeText(context, "已恢复默认颜色", Toast.LENGTH_SHORT).show()
        },
        onPickBg = {
            pickBg.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onClearBg = {
            WidgetWallpaper.clear(context)
            revision = WidgetStyle.revision(context)
            customBg = false
            VehicleWidget.render(context)
            Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
        },
    )

    Spacer(Modifier.height(10.dp))
    Text(
        "三套版式随时可以换，桌面上已有的卡片会立刻跟着变，不用删掉重加。",
        color = C.textSecondary,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun WidgetSection(
    style: String,
    revision: Int,
    customBg: Boolean,
    ink: Int,
    ctrl: Int,
    onSelectStyle: (String) -> Unit,
    onPickInk: (Int) -> Unit,
    onPickCtrl: (Int) -> Unit,
    onResetColors: () -> Unit,
    onPickBg: () -> Unit,
    onClearBg: () -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    SettingsSection(title = "桌面小组件", desc = "续航 · 五个控车按键") {
        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                WidgetStyle.ALL.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.width(7.dp))
                    WidgetStyleThumb(
                        style = item,
                        selected = style == item,
                        ink = ink,
                        ctrl = ctrl,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectStyle(item) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                when (style) {
                    WidgetStyle.STYLE_D -> "沉浸：一张图铺满卡片，下半部分压暗保证白字读得清。"
                    WidgetStyle.STYLE_T -> "全透明：没有卡片底，字和按键直接浮在壁纸上，下面可以自己调颜色。"
                    else -> "素纸：浅色卡 + 发丝分隔线，跟系统自带的卡片最接近。"
                },
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            // ---- 全透明主题的颜色：只在选中它时出现 ----
            // 素纸 / 沉浸的配色是跟着「卡片底」定的，开放了反而会把可读性调坏，
            // 所以这两个不摆入口，界面也就不必解释「为什么这里不能调」。
            if (style == WidgetStyle.STYLE_T) {
                Spacer(Modifier.height(14.dp))
                WidgetColorRow(
                    title = "字体颜色",
                    desc = "车名、续航数字、百分比，以及五个按键图标的常态色。壁纸偏深就选浅色，反之选深色。",
                    color = ink,
                    onPick = onPickInk,
                )
                Spacer(Modifier.height(14.dp))
                WidgetColorRow(
                    title = "控件颜色",
                    desc = "按键按下生效时的亮色，以及按键之间的分隔线。",
                    color = ctrl,
                    onPick = onPickCtrl,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "恢复默认颜色",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onResetColors() }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                )
            }

            // 背景图只在沉浸样式下有意义，别的版式里不摆这个入口
            if (style == WidgetStyle.STYLE_D) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val preview: ImageBitmap? = remember(revision, customBg) {
                        runCatching { WidgetWallpaper.preview(context)?.asImageBitmap() }.getOrNull()
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF3F5266), Color(0xFF9DB3C9)),
                                ),
                            )
                            .border(1.dp, C.border, RoundedCornerShape(8.dp)),
                    ) {
                        if (preview != null) {
                            Image(
                                bitmap = preview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("卡片背景", color = C.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (customBg) "已用自定义图 · 下半部分会自动压暗" else "默认黄昏渐变 · 可以换成自己的图",
                            color = C.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPickBg() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = C.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("换图", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (customBg) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "恢复默认背景",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClearBg() }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // 这里曾经是「添加到桌面」按钮（走 AppWidgetManager.requestPinAppWidget）。
            // 那个接口要求桌面自己实现「一键钉卡片」，MIUI / 澎湃桌面不实现 ——
            // 有的版本直接返回 false，有的返回 true 却毫无反应，且它不需要任何权限，
            // 应用这一侧无从修复。所以改成写清楚手动路径，比一个点了没反应的按钮诚实。
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = C.textSecondary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("放到桌面", color = C.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "长按桌面空白处 → 添加小部件 → 选「零控」→ 拖到桌面。" +
                            "列表里有 4×2 和 5×2 两种：5 列的桌面选「零控 · 5×2」才铺满一行，" +
                            "其余用 4×2。放着就能看续航，五个按键点一下直接控车。",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

/**
 * 一张版式卡：上面是示意图，下面是名字。
 *
 * 示意图只画结构（标题行 / 续航 / 右侧车模 / 底部五格），不画文字 —— 在 56dp 高的框里
 * 任何文字都只会变成噪点，反而看不出几者的差别。
 *
 * 全透明那张用「棋盘格」当底：这是设计工具里表示透明的通用画法，
 * 比涂成纯色更能一眼看出「这一版没有卡片底」。格子里的字块与按键块直接用用户选的两个颜色，
 * 换色时缩略图跟着变，不用先应用到桌面才知道效果。
 */
@Composable
private fun WidgetStyleThumb(
    style: String,
    selected: Boolean,
    ink: Int,
    ctrl: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    val immersive = style == WidgetStyle.STYLE_D
    val clear = style == WidgetStyle.STYLE_T
    val barInk = when {
        clear -> Color(ink)
        immersive -> Color.White
        else -> C.textPrimary
    }
    val barCtrl = when {
        clear -> Color(ctrl)
        immersive -> Color.White
        else -> C.accent
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .then(
                    when {
                        clear -> Modifier
                        immersive -> Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF35506B), Color(0xFF8FA9C4))),
                        )
                        else -> Modifier.background(C.panelBg)
                    },
                )
                .border(1.5.dp, if (selected) C.accent else C.border, shape)
                .clickable(onClick = onClick)
                .padding(6.dp),
        ) {
            if (clear) {
                CheckerBoard(Modifier.matchParentSize())
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Bar(13.dp, 3.dp, barInk.copy(alpha = 0.85f))
                            Spacer(Modifier.width(3.dp))
                            Bar(8.dp, 3.dp, barInk.copy(alpha = 0.45f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Bar(26.dp, 9.dp, barInk.copy(alpha = 0.9f))
                        Spacer(Modifier.height(3.dp))
                        Bar(30.dp, 2.dp, barCtrl.copy(alpha = 0.75f))
                    }
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    clear -> barInk.copy(alpha = 0.10f)
                                    immersive -> Color.White.copy(alpha = 0.16f)
                                    else -> Color.Transparent
                                },
                            ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.height(9.dp)) {
                    repeat(5) { index ->
                        if (index > 0) Spacer(Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        // 透明主题的按键是一层淡玻璃，缩略图照这个比例示意
                                        clear -> barCtrl.copy(alpha = 0.22f)
                                        immersive -> Color.White.copy(alpha = 0.26f)
                                        else -> barInk.copy(alpha = 0.07f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            WidgetStyle.displayName(style),
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 透明底纹：8dp 方格交替，两色都取自当前主题，深浅色模式下都不刺眼。 */
@Composable
private fun CheckerBoard(modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Canvas(modifier = modifier) {
        val cell = 8.dp.toPx()
        val light = C.panelBg
        val dark = C.border.copy(alpha = 0.55f)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = 0f
            var col = 0
            while (x < size.width) {
                drawRect(
                    color = if ((row + col) % 2 == 0) light else dark,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color),
    )
}

/** 把相册里那张图按最长边 [maxSize] 采样解码 —— 原图动辄 4000px，整个读进来会直接 OOM。 */
private fun decodeScaled(context: Context, uri: Uri, maxSize: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
        sample *= 2
    }
    resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()
