package com.leapcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap

/**
 * 头像选择器。
 *
 * 20 个内置卡通头像按 5 列 4 行铺开，底部一行是「从相册选择」。
 * 点任意一项立即生效并关闭——头像属于「看一眼就知道要不要」的设置，
 * 不需要额外的「确定」按钮再确认一次。
 *
 * @param customBitmap 已经设过的自定义头像（没有则 null）
 * @param onPickPreset 选中内置头像
 * @param onPickCustom 请求从相册挑一张
 * @param onClearCustom 删除自定义头像、回到默认
 */
@Composable
fun AvatarPickerDialog(
    current: AvatarChoice,
    customBitmap: Bitmap?,
    onPickPreset: (Int) -> Unit,
    onPickCustom: () -> Unit,
    onClearCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = C.cardBg,
                shadowElevation = 18.dp,
                // 卡片自身吃掉点击，避免穿透到遮罩把弹窗关掉
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clickable(onClick = { }),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "选择头像",
                                color = C.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "挑一个合眼缘的，也可以换成自己的照片",
                                color = C.textSecondary,
                                fontSize = 11.5.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(C.chipBg)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = C.textSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 5 列 4 行：用 weight + aspectRatio 自适应屏宽，小屏也不会挤出边界
                    for (row in 0 until (AvatarCatalog.count + 4) / 5) {
                        if (row > 0) Spacer(Modifier.height(9.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            for (col in 0 until 5) {
                                val index = row * 5 + col
                                if (index >= AvatarCatalog.count) {
                                    Spacer(Modifier.weight(1f))
                                    continue
                                }
                                val selected = current is AvatarChoice.Preset &&
                                    current.index == index
                                AvatarSlot(
                                    selected = selected,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPickPreset(index) },
                                ) {
                                    Image(
                                        painterResource(AvatarCatalog.drawableAt(index)),
                                        contentDescription = "内置头像 ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 自定义：整行按钮。已经是自定义状态时右侧多一个「移除」
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(C.chipBg)
                                .clickable(onClick = onPickCustom)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (customBitmap != null) {
                                Image(
                                    bitmap = customBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape),
                                )
                                Spacer(Modifier.width(10.dp))
                            } else {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    tint = C.textPrimary,
                                    modifier = Modifier.size(19.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = if (customBitmap != null) "更换自定义照片" else "从相册选择照片",
                                color = C.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (customBitmap != null) {
                            Spacer(Modifier.width(9.dp))
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(C.chipBg)
                                    .clickable(onClick = onClearCustom),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除自定义头像",
                                    tint = C.textSecondary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "自定义照片只保存在本机，不会上传",
                        color = C.textSecondary.copy(alpha = 0.7f),
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 一个头像格：选中时套一圈主题色描边，未选中时保持素面（全 App 只有选中态才描边）。
 *
 * 结构上刻意分成「外层方形 + 内层圆形」：内层负责圆裁与点击涟漪，
 * 外层的右下角留给选中角标——如果角标也放在被裁圆的层里，
 * 它会被圆弧切掉一块，看着像缺了个口子。
 */
@Composable
private fun AvatarSlot(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val C = LocalAppColors.current
    Box(modifier = modifier.aspectRatio(1f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.border(2.dp, C.accent, CircleShape) else Modifier,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (selected) 3.dp else 0.dp)
                    .clip(CircleShape),
            ) {
                content()
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(C.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
