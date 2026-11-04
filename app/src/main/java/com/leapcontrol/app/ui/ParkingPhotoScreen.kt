package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.ParkingPhotoRules

/**
 * 驻车拍照（首页「驻车拍照」胶囊进入的二级页）。
 *
 * ## 为什么单独开一页
 * 这张照片原本只在「车辆位置」页和首页车辆位置卡上露一点头 —— 那两处的主题是**位置**，
 * 照片排在导航按钮后面，用户想「先拍一张」得先意识到它在那儿。停车拍照是下车前
 * 顺手做的一个动作，入口要能在首页一眼看到，所以给它单独一颗胶囊。
 *
 * ## 内容还是同一份
 * 存储、拍照流程、全屏预览全部复用找车页那一套（[rememberParkingPhoto] /
 * [ParkingPhotoStore]）：这里只是把图放大、把「拍 / 重拍 / 删除」摆成一页的主角。
 * 两处共用同一个文件，拍完回到车辆位置页也能看到同一张。
 */
@Composable
fun ParkingPhotoScreen(onBack: () -> Unit, onMessage: (String) -> Unit = {}) {
    val C = LocalAppColors.current
    // 1400 与找车页一致：这一页的图也接近整屏，再高只是白吃内存
    val photo = rememberParkingPhoto(onMessage, thumbSize = 1400)
    var previewPhoto by remember { mutableStateOf(false) }

    // 大图先收掉再退页：否则点返回键会连带退出整页，用户以为只想关那张图
    BackHandler(enabled = previewPhoto) { previewPhoto = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ScreenTopBar(title = "驻车拍照", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "地库里坐标再准也找不到那一格，柱子上的编号才认得出。",
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (photo.hasPhoto) {
            TakenPhoto(
                photo = photo,
                onPreview = { previewPhoto = true },
            )
        } else {
            EmptyPhoto(onCapture = { photo.capture() })
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "照片只保存在这台手机上，重拍会覆盖上一张；退出登录时一并清除。" +
                "拍好后在首页「车辆位置」卡和找车页里也会显示同一张。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(20.dp))
    }

    val thumb = photo.thumb
    if (previewPhoto && thumb != null) {
        PhotoPreviewDialog(bitmap = thumb, onDismiss = { previewPhoto = false })
    }
}

/** 还没有照片：一块大占位（点哪儿都能拍）+ 一颗整行主按钮。 */
@Composable
private fun EmptyPhoto(onCapture: () -> Unit) {
    val C = LocalAppColors.current
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(C.panelBg)
                .clickable(onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 26.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(C.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = C.accent,
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "还没有车位照片",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "停车后随手拍一张，回来一眼认出是哪一格",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PageAction(text = "拍一张停车位照片", primary = true, onClick = onCapture)
    }
}

/** 已拍过：大图（点开全屏）+ 拍摄时间 + 重拍 / 删除。 */
@Composable
private fun TakenPhoto(photo: ParkingPhotoState, onPreview: () -> Unit) {
    val C = LocalAppColors.current
    val caption = ParkingPhotoRules.takenAt(
        hasPhoto = photo.hasPhoto,
        savedAt = photo.savedAt,
        now = System.currentTimeMillis(),
    )
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(C.chipBg)
                .clickable(onClick = onPreview),
        ) {
            val thumb = photo.thumb
            if (thumb != null) {
                Image(
                    bitmap = remember(thumb) { thumb.asImageBitmap() },
                    contentDescription = "停车位照片，点击放大",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // 文件在但解码失败（极少数机型写出的 HEIC 之类）：给出可操作的出路
                Text(
                    text = "照片读取失败，重拍一张",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = caption ?: "车位已拍照",
                color = C.accent,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "点图放大看编号",
                color = C.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PageAction(
                text = "重拍",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = { photo.capture() },
            )
            PageAction(
                text = "删除",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = { photo.remove() },
            )
        }
    }
}

/**
 * 本页的大按钮（48dp 高）。
 *
 * 和 [PhotoAction] 同一套配色（淡主题色底 + 主题色字，删除用次要色），只是高一档 ——
 * 这一页它就是唯一的主操作，不该和卡片里的小按钮一样大。
 */
@Composable
private fun PageAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) C.accent.copy(alpha = 0.18f) else C.chipBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (primary) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = C.accent,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text,
                color = if (primary) C.accent else C.textSecondary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
