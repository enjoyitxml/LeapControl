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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.OfficialPhotoStatus
import com.leapcontrol.app.ParkingPhotoRules
import com.leapcontrol.app.ParkingPhotoSource
import com.leapcontrol.app.ParkingPhotoSourceRules

/**
 * 驻车拍照（首页「驻车拍照」胶囊进入的二级页）。
 *
 * ## 为什么单独开一页
 * 这张照片原本只在「车辆位置」页和首页车辆位置卡上露一点头 —— 那两处的主题是**位置**，
 * 照片排在导航按钮后面，用户想「先拍一张」得先意识到它在那儿。停车拍照是下车前
 * 顺手做的一个动作，入口要能在首页一眼看到，所以给它单独一颗胶囊。
 *
 * ## 两种来源
 * 一页里放两种方案，用户自己选（见 [ParkingPhotoSource]）：
 *  - **手机拍照**：一直以来的做法，任何车都能用；
 *  - **车端照片**：车辆环视摄像头驻车后自动上传的俯视图，能看到车位号，但**部分车型不支持**。
 *
 * ## 为什么只显示一种
 * 早先这里是两段等宽的切换（两个方案永远并排占着屏幕）。偏好一旦定下来，
 * 另一个方案对用户就是噪音：他每次进页面都得重新确认「我选的是哪个」，
 * 而真正需要改的时候（换车、车端不支持）其实只有一两次。
 * 所以收敛成「当前方案 + 一个切换入口」——两种都还在，页面安静一大截。
 */
@Composable
fun ParkingPhotoScreen(runtime: AppRuntime, onBack: () -> Unit, onMessage: (String) -> Unit = {}) {
    val C = LocalAppColors.current
    // 1400 与找车页一致：这一页的图也接近整屏，再高只是白吃内存
    val photo = rememberParkingPhoto(runtime, onMessage, thumbSize = 1400)
    val source by runtime.parkingPhotoSource.collectAsState()
    val official by runtime.officialPhotoStatus.collectAsState()
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

        SourceHeader(
            selected = source,
            onSwitch = { photo.useSource(it) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (source == ParkingPhotoSource.OFFICIAL) {
                ParkingPhotoSourceRules.SUPPORT_NOTE
            } else {
                "照片只保存在这台手机上，重拍会覆盖上一张；退出登录时一并清除。"
            },
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (source == ParkingPhotoSource.OFFICIAL) {
            OfficialPanel(
                photo = photo,
                status = official,
                onPreview = { previewPhoto = true },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "照片从车辆云端取回后缓存在本机，换车或退出登录会一并清除。" +
                    "车辆不支持时，请改用手机拍照。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        } else {
            if (photo.hasPhoto) {
                TakenPhoto(photo = photo, onPreview = { previewPhoto = true })
            } else {
                EmptyPhoto(onCapture = { photo.capture() })
            }
            Spacer(Modifier.height(14.dp))
            Text(
                // 上面已经说过「只存本机、退出登录清除」，这里只补一句「别处也能看到」，
                // 同一句说明写两遍会让页脚显得啰嗦
                text = "拍好后在首页「车辆位置」卡和找车页里也会显示同一张。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    val thumb = photo.thumb
    if (previewPhoto && thumb != null) {
        PhotoPreviewDialog(bitmap = thumb, onDismiss = { previewPhoto = false })
    }
}

/**
 * 方案标题行：只摆**当前方案**，另一种收进右侧那一个文字入口。
 *
 * 「部分车型」那句必须挂在入口上：它在切过去之后才说明，等于用户点了才知道，
 * 而那时已经站在「取不到」的界面上了。宁可在链接里先挤一句，也不让人白跑一趟。
 */
@Composable
private fun SourceHeader(
    selected: ParkingPhotoSource,
    onSwitch: (ParkingPhotoSource) -> Unit,
) {
    val C = LocalAppColors.current
    val other = if (selected == ParkingPhotoSource.PHONE) {
        ParkingPhotoSource.OFFICIAL
    } else {
        ParkingPhotoSource.PHONE
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected == ParkingPhotoSource.PHONE) {
                Icons.Filled.PhotoCamera
            } else {
                Icons.Filled.CloudDownload
            },
            contentDescription = null,
            tint = C.accent,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = ParkingPhotoSourceRules.label(selected),
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onSwitch(other) }
                // 竖向内边距撑到 8dp：这一行字只有 12sp，不做够高度很难点中
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (other == ParkingPhotoSource.OFFICIAL) {
                    "换用车端照片（部分车型）"
                } else {
                    "换用手机拍照"
                },
                color = C.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 车端照片那一块内容。
 *
 * 三种界面：取到了（大图）、正在取（转圈）、取不到（说明 + 两条出路）。
 * 「取不到」不说成「功能坏了」：既可能是车型不支持，也可能只是刚停车还没上传，
 * 所以除了重试，永远同时给一个「改用手机拍照」。
 */
@Composable
private fun OfficialPanel(
    photo: ParkingPhotoState,
    status: OfficialPhotoStatus,
    onPreview: () -> Unit,
) {
    val C = LocalAppColors.current
    val loading = status == OfficialPhotoStatus.Loading
    // 状态那句话是「正在取 / 还没上传 / 不支持 / 网络失败」，没有时才退回泛泛的提示
    val statusLine = ParkingPhotoSourceRules.statusText(status)
    val caption = ParkingPhotoRules.officialTakenAt(
        hasPhoto = photo.hasPhoto,
        uploadTime = photo.savedAt,
        now = System.currentTimeMillis(),
    )

    if (photo.hasPhoto) {
        Column {
            // 车端图是环视俯视图，构图比例不固定：按图片真实比例占位，再用 Fit，
            // 保证车位号那种贴在画面边上的字不会被裁掉
            val ratio = photo.thumb
                ?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
                ?.coerceIn(0.72f, 1.9f)
                ?: (4f / 3f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.chipBg)
                    .clickable(onClick = onPreview),
            ) {
                val thumb = photo.thumb
                if (thumb != null) {
                    Image(
                        bitmap = remember(thumb) { thumb.asImageBitmap() },
                        contentDescription = "车端驻车照片，点击放大",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = "照片读取失败，点「重新获取」",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (loading) {
                    // 刷新时旧图不动，只在右上角放一个小转圈 —— 整块变灰会让人以为图没了
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(C.panelBg.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = C.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = caption ?: "车端照片",
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
            // 方案切换只在顶上留一个入口，这里不再并排一颗「改用手机拍照」——
            // 照片正常时那是噪音；真的取不到（不支持）时才把它提成主操作，见下面的空态
            PageAction(
                text = if (loading) "获取中…" else "重新获取",
                primary = true,
                icon = Icons.Filled.Refresh,
                onClick = { if (!loading) photo.refreshOfficial() },
            )
        }
        return
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(C.panelBg)
                .clickable(enabled = !loading) { photo.refreshOfficial() },
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
                    if (loading) {
                        CircularProgressIndicator(
                            color = C.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = C.accent,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    // 明确「不支持」时不能还写「还没取到」—— 那是两件不同的事，
                    // 一个值得再试，一个只能换方案
                    text = if (status is OfficialPhotoStatus.Unsupported) {
                        "这台车不支持车端照片"
                    } else {
                        ParkingPhotoSourceRules.emptyTitle(ParkingPhotoSource.OFFICIAL)
                    },
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = statusLine ?: ParkingPhotoSourceRules.emptyHint(ParkingPhotoSource.OFFICIAL),
                    color = if (statusLine != null) C.accent else C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (status is OfficialPhotoStatus.Unsupported) {
            // 唯一允许出现第二颗「改用手机拍照」的地方：接口已经明确说不支持了，
            // 这时顶部那个切换入口虽然还在，但不该让用户自己回去找 —— 直接把出路摆在面前
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PageAction(
                    text = "改用手机拍照",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { photo.useSource(ParkingPhotoSource.PHONE) },
                )
                PageAction(
                    text = "再试一次",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Refresh,
                    onClick = { photo.refreshOfficial() },
                )
            }
        } else {
            PageAction(
                text = if (loading) "获取中…" else "重新获取",
                primary = true,
                icon = Icons.Filled.Refresh,
                onClick = { if (!loading) photo.refreshOfficial() },
            )
        }
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
    icon: ImageVector = Icons.Filled.PhotoCamera,
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
                    icon,
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
