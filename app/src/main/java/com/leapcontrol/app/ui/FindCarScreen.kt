package com.leapcontrol.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.MapIntents
import com.leapcontrol.app.ParkingPhotoRules
import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleLocationRules
import kotlinx.coroutines.launch

/**
 * 找车页（首页「车辆位置」卡点进来）。
 *
 * ## 这个页面存在的理由
 * 首页卡只有 2:1 的一条窄图，能看出「在哪条街」但看不清路口。找车时人已经站在楼下、
 * 需要的是「往左还是往右」，所以这里给一张能拖动缩放的地图（[FindCarMap]），
 * 外加一个整行的「导航到车辆」——那是这个功能真正的出口。
 *
 * ## 和首页卡是同一个组件的两级
 * 卡片上的两个动作（导航到车辆 / 发送目的地）在这一页原样保留、而且更大：
 * 卡片是「扫一眼」，这一页是「真要动手」。两处动作集合必须一致，
 * 免得用户为了发个地址还要退回去翻卡片。
 *
 * ## 位置为什么可能不新鲜
 * 熄火后车机会休眠，车况不再更新，位置就冻结在熄火点。这对找车恰恰是准确的，
 * 但如果车是三天前停的、期间又被人挪过（比如代客泊车、拖车），这个位置就不作数了。
 * 所以「位置更新于 x 前」必须显眼，而不是藏在一行小字里。
 *
 * ## 不做的事
 * - 不用高德地图 SDK：Key 要绑签名，用户自己申请的填不进这个包。底图改用开源控件 +
 *   高德公开栅格瓦片（[FindCarMap]），不需要任何 Key；Key 只留给地址与目的地搜索。
 * - 不主动申请手机定位权限：那会把「看一眼车在哪」变成一次权限弹窗，
 *   而距离数字对找车的实际帮助有限（真实场景里人已经在附近了）。
 */
@Composable
fun FindCarScreen(
    runtime: AppRuntime,
    status: CarStatus?,
    reportedAt: Long,
    /** 下发结果提示（Toast / 顶部横幅由首页统一弹）。 */
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val latitude = status?.latitude
    val longitude = status?.longitude
    // 刷新按钮除了重查车况，还要能把「上次没取到的地址 / 地图」再请求一次：
    // 只重查车况的话坐标没变，加载逻辑不会重跑，按钮看着像没生效
    var retry by remember { mutableStateOf(0) }
    val seenAt = status?.locationSeenAt ?: 0L
    val ui = rememberVehiclePlace(
        runtime = runtime,
        latitude = latitude,
        longitude = longitude,
        reportedAt = reportedAt,
        seenAt = seenAt,
        full = true,
        // 这一页的底图已经换成瓦片地图（FindCarMap），不再需要高德静态图：
        // 跳过那张图能让每次进页面少一次请求，额度留给首页卡
        needMap = false,
        retry = retry,
    )
    val place = ui as? VehiclePlaceUi.Ready
    // 展示 / 导航用坐标：**车况优先**，没有才退回缓存或这次取图时用的那个点。
    // 地图是进页面那一刻取的，之后不再自动重拉（见 rememberVehiclePlace），
    // 但导航要走当前坐标 —— 图上那个点是「拍下这张图时的车」。
    val shownLat = latitude ?: place?.latitude
    val shownLng = longitude ?: place?.longitude
    // 位置的年龄看「这条坐标从什么时候起没变过」，不看我们拉取车况的时刻（见 [LocationAnchorRule]）
    val shownAt = place?.cachedAt ?: seenAt.takeIf { it > 0L } ?: reportedAt
    val fromCache = place?.cachedAt != null
    val canNavigate = VehicleLocationRules.isValid(shownLat, shownLng)
    val coordinate = if (VehicleLocationRules.isValid(shownLat, shownLng)) {
        VehicleLocationRules.coordinateText(shownLat!!, shownLng!!)
    } else {
        null
    }
    val ageMs = VehicleLocationRules.ageMs(shownAt, System.currentTimeMillis())
    val age = VehicleLocationRules.ageText(ageMs)

    var refreshing by remember { mutableStateOf(false) }
    // 目的地搜索弹层挂在这一页上：用户在这一页想「把地址发过去」时不必退回首页
    var showDestination by remember { mutableStateOf(false) }
    // 出行方式弹层：和首页卡同一个，步行排在第一位（见 [NavModeDialog]）
    var navPicker by remember { mutableStateOf(false) }
    // 点导航时先取一次最新车况，期间把按钮改成「正在取最新位置」并挡住重复点击
    var navPreparing by remember { mutableStateOf(false) }
    // 右上角「?」：高德 Key 的申请与填写。放在这一页而不是首页卡上，
    // 是因为「地址只有经纬度」的现场就在这里 —— 用户正卡在这一步，
    // 再让他退回设置里找「车辆位置与目的地」是没有道理的。
    var showKeyHelp by remember { mutableStateOf(false) }

    // 停车位照片：和首页卡共用同一套存储与拍照流程，这一页只是把图放大到 1400 好认
    val photo = rememberParkingPhoto(onMessage, thumbSize = 1400)
    var previewPhoto by remember { mutableStateOf(false) }

    // 返回键先把全屏照片 / 搜索弹层收掉，再退出本页 —— 否则一按就整页返回，
    // 用户以为「退出了找车」，其实只想关掉那张大图
    BackHandler(enabled = previewPhoto) { previewPhoto = false }

    // 地图不进滚动容器：MapView 要吃掉单指拖动，塞在 verticalScroll 里会变成
    // 「想拖地图，结果整页跟着滚」。所以顶栏 + 地图固定在上面（地图占上半屏），
    // 地址卡 / 出口按钮 / 车位照片这些在下面自己滚。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 地图高度跟着屏幕走：宽屏取「屏宽 - 两侧留白」，矮屏取屏高的 46%，
        // 保底下半屏至少还能露出地址卡的两行字
        val mapHeight = minOf(maxWidth - 40.dp, maxHeight * 0.46f).coerceAtLeast(200.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                ScreenTopBar("车辆位置", onBack) {
                    // 没配 Key 时按钮走主题色，配好了退回中性色 —— 它是「帮助」而不是「提醒」，
                    // 只在真正卡住的时候才该抢注意力
                    val needsKey = !runtime.hasAmapKey()
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (needsKey) C.accent.copy(alpha = 0.16f) else C.iconBg)
                            .clickable { showKeyHelp = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "?",
                            color = if (needsKey) C.accent else C.textSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // ---- 地图 ----
            // 有坐标就铺瓦片地图（可拖动 / 双指缩放），没坐标仍画模拟路网占位，
            // 不留一块空白 —— 那种情况用户更需要的是「为什么没有位置」这句话
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.accent.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (canNavigate && shownLat != null && shownLng != null) {
                    FindCarMap(
                        latitude = shownLat,
                        longitude = shownLng,
                        // 坐标真变了 / 用户点了刷新 → 视角回到车上；
                        // 用户自己拖到别处看路的时候不会被拽回来
                        recenterKey = "$shownLat,$shownLng,$retry",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SimulatedMapBackdrop(Modifier.matchParentSize(), centerFade = true)
                    FindCarPlaceholder(ui)
                }

                // 刷新：找车时第一反应就是「再看看车在哪」，给个明确入口，
                // 不用退出去下拉首页。位置为空时也保留，用户可以先拉一次车况试试
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(C.panelBg.copy(alpha = 0.94f))
                        .clickable(enabled = !refreshing) {
                            refreshing = true
                            scope.launch {
                                val ok = runCatching { runtime.refreshStatus() }.isSuccess
                                // 无论车况是否取到都重试一次地址 / 地图：
                                // 上一次可能只是高德请求超时，和车况没有关系
                                retry += 1
                                refreshing = false
                                if (!ok) {
                                    Toast.makeText(context, "刷新失败，请检查网络", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            color = C.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新位置",
                            tint = C.accent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // 下半部分自己滚：内容比一屏长（车位照片能到 300dp），整体滚动会把地图顶走
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                // ---- 地址与时间 ----
                InfoCard {
                    Text(
                        text = place?.address ?: coordinate ?: "还没有可用的车辆位置",
                        color = C.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when (ui) {
                            VehiclePlaceUi.NoLocation -> "车机还没上报过位置，下次上车启动一次后再看"
                            VehiclePlaceUi.NoKey ->
                                "地图不再依赖 Key，但中文地址还缺一把：点右上角「?」，" +
                                    "填两分钟就有地址和目的地搜索"
                            // 走到这里只有地址失败（底图是瓦片、不经过这两个接口）
                            VehiclePlaceUi.Failed ->
                                "中文地址没取到：检查网络，或确认高德 Key 还有额度，再点右上角刷新"
                            else -> when {
                                fromCache && age != null -> "上次记录的位置 · 更新于 $age"
                                fromCache -> "上次记录的位置"
                                age != null -> "位置更新于 $age"
                                else -> "位置更新时间未知"
                            }
                        },
                        color = C.textSecondary,
                        fontSize = 12.sp,
                    )

                    if (coordinate != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(coordinate))
                                    Toast.makeText(context, "坐标已复制", Toast.LENGTH_SHORT).show()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = coordinate,
                                color = C.textSecondary,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "复制坐标",
                                color = C.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- 两个出口：导航到车辆（有坐标才给）+ 发送目的地（与坐标无关） ----
                // 和首页卡同一套动作，只是放大到整行。两个都没有时才什么都不显示。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (canNavigate) {
                        BigAction(
                            text = if (navPreparing) "正在取最新位置" else "导航到车辆",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Filled.Navigation, contentDescription = null, tint = C.accent, modifier = Modifier.size(17.dp)) },
                        ) {
                            if (navPreparing) return@BigAction
                            // 走到车位还是开过去，先问一句。开车去「找车」在楼下这个位置上说不通。
                            // 问之前先查一次车况：车可能是几小时前停的，手上这份坐标是那时拉到的
                            navPreparing = true
                            scope.launch {
                                runCatching { runtime.refreshStatus() }
                                // 顺手把地址/地图也重取一次：位置真变了，那张图就是旧地方
                                retry += 1
                                navPreparing = false
                                navPicker = true
                            }
                        }
                    }
                    BigAction(
                        text = "发送目的地",
                        primary = !canNavigate,
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_send_to_car),
                                contentDescription = null,
                                tint = C.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) { showDestination = true }
                }

                Spacer(Modifier.height(14.dp))

                // ---- 停车位照片 ----
                // 排在两个出口**之后**：这张卡有图时接近 300dp，放在按钮前面会把「导航到车辆」
                // 推到第二屏 —— 而那两个才是到楼下时第一时间要按的。照片是确认具体车位的第二步。
                ParkingPhotoCard(photo = photo, onPreview = { previewPhoto = true })

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "位置来自车机最后一次上报。熄火后车机休眠，位置会停在熄火的地方；" +
                        "若车停放较久，请注意上方的时间。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }

    // 全屏看照片：地库光线差，缩略图里常看不清柱子上的号码，这种时候必须能放大
    val preview = photo.thumb
    if (previewPhoto && preview != null) {
        PhotoPreviewDialog(bitmap = preview, onDismiss = { previewPhoto = false })
    }

    // 搜索弹层：搜到地点后可「发送到车机」或在手机地图里直接导航
    if (showDestination) {
        DestinationSearchSheet(
            runtime = runtime,
            onDismiss = { showDestination = false },
            onMessage = onMessage,
        )
    }

    // 出行方式：步行（推荐）/ 骑行 / 驾车，选完再调起高德
    if (navPicker && canNavigate) {
        NavModeDialog(
            destination = place?.address,
            onDismiss = { navPicker = false },
            onPick = { mode ->
                navPicker = false
                val lat = shownLat
                val lng = shownLng
                if (lat != null && lng != null) {
                    // 同首页卡片：坐标型目的地不能传地址当 dname，会被高德按 POI 吸附
                    val ok = MapIntents.navigate(context, lat, lng, mode = mode)
                    if (!ok) {
                        Toast.makeText(context, "没有找到可用的地图应用", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    // 右上角「?」：高德 Key 的申请教程 + 当场填写
    if (showKeyHelp) {
        AmapKeyDialog(runtime = runtime, onDismiss = { showKeyHelp = false })
    }
}

/**
 * 页面底部的大按钮（50dp 高）。
 *
 * 主按钮同样不用 accent 实底 + 白字：主题色是用户自己选的，浅黄 / 浅绿时白字会糊，
 * 统一「淡主题色底 + 主题色字」，深浅主题都稳。
 */
@Composable
private fun BigAction(
    text: String,
    primary: Boolean,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) C.accent.copy(alpha = 0.18f) else C.chipBg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = if (primary) C.accent else C.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 没有坐标时的占位：和首页卡同一套说法，但空间更大，可以说得更完整。 */
@Composable
private fun FindCarPlaceholder(ui: VehiclePlaceUi) {
    val C = LocalAppColors.current
    val (title, desc) = when (ui) {
        VehiclePlaceUi.Loading -> "正在获取位置" to "通常一秒内完成"
        VehiclePlaceUi.NoLocation -> "没有可用的车辆位置" to "上车启动一次车机后就会有"
        // 走到这几个分支时一定是「没有坐标」：有坐标的话上面已经画瓦片地图了，
        // 而瓦片地图不依赖高德 Key（只有地址才依赖）
        VehiclePlaceUi.NoKey -> "没有可用的车辆位置" to "地图不用 Key，但得先有坐标"
        VehiclePlaceUi.Failed -> "位置没取到" to "检查网络后点右上角刷新"
        is VehiclePlaceUi.Ready -> "没有可用的车辆位置" to "点右上角刷新试试"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(C.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(text = title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(text = desc, color = C.textSecondary, fontSize = 12.sp)
    }
}

/**
 * 停车位照片卡。
 *
 * 两副面孔：
 *  - 没拍过：一行「拍一张」的引导，点哪里都能拍（这是停车后第一件该做的事）；
 *  - 拍过：图 + 「拍摄于 x 前」+ 重拍 / 删除。
 *
 * 图高度固定 180dp + `Crop`：照片是 4:3 还是 16:9 由相机决定，我们不猜，
 * 统一裁成一条横向的观感（地库里拍的多是横向的柱子与地面标识）。
 */
@Composable
private fun ParkingPhotoCard(photo: ParkingPhotoState, onPreview: () -> Unit) {
    val C = LocalAppColors.current

    if (!photo.hasPhoto) {
        InfoCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { photo.capture() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(C.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = C.accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "拍一张停车位照片",
                        color = C.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "地库里坐标再准也找不到那一格，柱子上的编号才认得出",
                        color = C.textSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = C.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        return
    }

    val caption = ParkingPhotoRules.caption(
        hasPhoto = true,
        savedAt = photo.savedAt,
        now = System.currentTimeMillis(),
    )

    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "停车位照片",
                color = C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    color = C.accent,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
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

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PhotoAction(
                text = "重拍",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = { photo.capture() },
            )
            PhotoAction(
                text = "删除",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = { photo.remove() },
            )
        }
    }
}

/** 照片卡里的小按钮（40dp 高）。配色与 [BigAction] 同一套：淡色底 + 主题色字。 */
@Composable
internal fun PhotoAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) C.accent.copy(alpha = 0.16f) else C.chipBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            // 「删除」用次要色：它和「重拍」并排同权，但一个是常规动作、一个是破坏性动作，
            // 颜色上拉开一档，手滑的代价小一点
            color = if (primary) C.accent else C.textSecondary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 全屏看那一张照片。
 *
 * 地库灯暗、照片又是斜着拍的，缩略图里常认不出柱子上的字 —— 找车站在那里时，
 * 放大看是唯一能确认「是不是这格」的办法。
 */
@Composable
internal fun PhotoPreviewDialog(bitmap: android.graphics.Bitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = "停车位照片",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentScale = ContentScale.Fit,
            )
            // 关闭按钮不贴顶边：留出状态栏那一截，不然手指够不到还会被手势区吃掉
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(14.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
