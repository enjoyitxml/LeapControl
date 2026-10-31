package com.leapcontrol.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.leapcontrol.app.AmapWebApi
import com.leapcontrol.app.MapFraming
import com.leapcontrol.app.MapIntents
import com.leapcontrol.app.ParkingPhotoStore
import com.leapcontrol.app.R
import com.leapcontrol.app.VehicleLocationRules
import com.leapcontrol.app.VehicleLocationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 车辆位置卡的加载结果。
 *
 * 「没有图」的几种原因要分开：没配 Key 和车端没报位置是两件事，
 * 给同一句「暂无位置」会让用户一直等一个永远不会来的东西。
 */
sealed interface VehiclePlaceUi {
    /** 首帧：磁盘缓存还没读完（通常一闪而过）。 */
    data object Loading : VehiclePlaceUi

    /** 车况里没有可信坐标：没上报过，或上报的是 0,0 / 越界值。 */
    data object NoLocation : VehiclePlaceUi

    /** 有坐标但没配高德 Key：能显示坐标，但画不出地图和地址。 */
    data object NoKey : VehiclePlaceUi

    /** Key 和坐标都有，但请求全失败（没网 / 额度用尽 / Key 失效）。 */
    data object Failed : VehiclePlaceUi

    /**
     * 有坐标，地图和地址可能只到一半（缺图或缺口地址时先画已有的）。
     *
     * [latitude]/[longitude] 一定带出来：本次报文没给坐标、显示的是本地缓存时，
     * 「导航到车辆」照样要能点。
     *
     * [cachedAt] 非 null = 这一帧**没有**新坐标，展示的是上次记下的那个点，
     * 值是那次报文的时刻，用来算「更新于 x 前」。
     */
    data class Ready(
        val map: Bitmap?,
        val address: String?,
        val latitude: Double,
        val longitude: Double,
        val cachedAt: Long? = null,
    ) : VehiclePlaceUi
}

/**
 * 卡片缩略图边长。取 96dp 是为了让整张卡的高度落在「车辆健康」卡那一档（约 120dp）：
 * 原来是 2:1 的通栏大图，加上地址与按钮将近 280dp，首页一条卡就占掉大半屏。
 */
private val THUMB_SIZE = 96.dp

/**
 * 首页「车辆位置与目的地」卡：左侧方形地图缩略图 + 右侧地址、更新时间与**两个动作**，
 * 右上角还能给车位拍一张照。
 *
 * ## 为什么是这么小的版式
 * 第一版做成了 2:1 的通栏大图 + 按钮，整张卡近 280dp —— 一张卡吃掉大半屏，
 * 首页其余内容全被挤到第二屏。现在把地图压成 96dp 见方放在左边，
 * 整卡约 120dp，和「车辆健康」卡同一档高度，滚动节奏才是连续的。
 * 想看大图点缩略图进找车页（那里是 1024 的正方图）。
 *
 * ## 为什么把「发送目的地」并进来
 * 原来首页是两张卡：一张「将目的地发送到车机」的 74dp 横条、一张 2:1 的位置图。
 * 它们其实是同一件事的两头 —— 都靠高德 Web Key、都在回答「我在哪 / 我要去哪」，
 * 而且是同一个人在同一时刻会用的两个动作（上车前）。拆成两张卡时，
 * 位置图那张只解决「车停哪」，而「送个地址过去」却要往上翻到另一张卡，白白多一次滚动。
 * 合并后：一张卡里「看车在哪」和「把目的地交给车」并列，点缩略图进大图页。
 *
 * ## 为什么加「拍车位」
 * 坐标能把人带到地库入口，但带不到 B2 层 12 号柱 —— 那只能靠当时拍的照片。
 * 所以这道动作和位置并列，而不是藏进找车页：**停完车就该拍**，等要找车时才想起来已经晚了。
 * 拍完按钮直接变成那张照片的缩略，一眼看出「拍过了」。
 *
 * ## 数据
 * 位置来自**车况报文里的经纬度**（信号 2/3，GCJ-02），和高德同坐标系，不转换。
 * 熄火后车机休眠，位置就冻结在熄火点 —— 这恰好就是「车停在哪」的答案，
 * 但必须把时间亮出来：刚熄火是准的，离线三天的位置可能已经不作数了。
 *
 * 请求策略见 [rememberVehiclePlace]：同位置不重复请求，断网走缓存。
 */
@Composable
fun VehicleLocationCard(
    runtime: AppRuntime,
    status: CarStatus?,
    /** 车况报文时间，首页已经订阅了 `lastUpdatedFlow`，这里直接接过来免得重复订阅。 */
    reportedAt: Long,
    /** 点地图 / 地址进找车页看大图。 */
    onOpen: () -> Unit,
    /** 「发送目的地」：打开搜索弹层（见 [DestinationSearchSheet]）。 */
    onSendDestination: () -> Unit,
    /** 拍照 / 保存的一次性提示，由首页统一弹横幅。 */
    onMessage: (String) -> Unit = {},
    /**
     * 用户手动刷新（下拉刷新 / 顶部刷新按钮 / 切换车辆）的次数。
     * 位置没变也要能重新问一次高德 —— 上一次可能只是超时，卡片不能一直停在失败态。
     */
    refreshKey: Int = 0,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val latitude = status?.latitude
    val longitude = status?.longitude
    // 这条坐标从什么时候起没变过（见 [LocationAnchorRule]）。没有坐标时是 0
    val seenAt = status?.locationSeenAt ?: 0L
    val ui = rememberVehiclePlace(
        runtime = runtime,
        latitude = latitude,
        longitude = longitude,
        reportedAt = reportedAt,
        seenAt = seenAt,
        refreshKey = refreshKey,
    )
    val place = ui as? VehiclePlaceUi.Ready

    // 展示用坐标：**车况优先**，没有才退回缓存里那份。
    // 地图与地址可能是进页面那一刻取的（车后来开走了也不会自动重拉，见 [rememberVehiclePlace]），
    // 但「导航到车辆」必须去当前坐标 —— 拿一张旧图上的点导航，是会被投诉到找错地方的那种错。
    // （[VehiclePlaceUi.Ready.cachedAt] 非 null 时车况里本来就没有坐标，只能退回缓存）
    val shownLat = latitude ?: place?.latitude
    val shownLng = longitude ?: place?.longitude
    // 「更新于 x 前」说的是**这条位置本身**的年龄，不是我们拉取车况的时刻。
    // 车况里的坐标没有自己的时间戳，所以只能用「从什么时候起没变过」来推；
    // 实在推不出来（新装、刚换车）才退回报文时间，至少不比原来更假
    val shownAt = place?.cachedAt ?: seenAt.takeIf { it > 0L } ?: reportedAt

    // 坐标是否可用：不可用时只禁用「导航到车辆」，
    // 「发送目的地」与车况无关（它只查高德 + 调起高德 App），没定位也必须能点
    val canNavigate = VehicleLocationRules.isValid(shownLat, shownLng)

    // 停车位照片和位置是两条线：车在地库里坐标再准也没用，
    // 照片里那个「B2 · C 区 · 12 号柱」才是找车时真正要看的东西
    val photo = rememberParkingPhoto(onMessage)

    // 出行方式弹层：「导航到车辆」的第一步
    var navPicker by remember { mutableStateOf(false) }
    // 正在为导航取最新车况：按钮按下去到弹出层之间有一瞬，防重复点
    var navPreparing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen),
    ) {
        // 模拟路网只在右端露一小块，当点缀：铺满整卡时那几条斜线会横穿地址与按钮，
        // 看着像卡片被划了几道；缩到右边 2/3 再四边渐隐，才像「角落里有张地图」。
        // 强度 0.45 —— 这块是纹理，不能和地址、按钮抢注意力
        SimulatedMapBackdrop(
            modifier = Modifier.matchParentSize(),
            intensity = 0.45f,
            insetSpan = 0.66f,
        )

        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左侧方图：和「车辆健康」卡里的读数区同高，整张卡才和它一般大。
                // 点图进找车页看 1024 大图 —— 96dp 里能看出「在哪条街」，看不出该往左还是往右
                Box(
                    modifier = Modifier
                        .size(THUMB_SIZE)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.accent.copy(alpha = 0.06f))
                        // 方图里的路网和整卡那层是同一种线条，不描一道边就分不出「图」和「底」，
                        // 看起来只是卡片左边比右边密一点
                        .border(1.dp, C.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val map = place?.map
                    if (map != null) {
                        Image(
                            bitmap = remember(map) { map.asImageBitmap() },
                            contentDescription = "车辆位置地图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        // 没有真地图时方框里不是一块空色块：铺上路网，
                        // 中心压一层径向渐变让「无位置」三个字读得清
                        SimulatedMapBackdrop(Modifier.matchParentSize(), centerFade = true)
                        ThumbPlaceholder(ui)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // 地址允许折两行：右侧只剩约 210dp，中文地址一行放不下十几个字，
                    // 单行省略号恰好会把「几号楼 / 几号院」这种最要紧的后半截吃掉。
                    // 卡高由左边 96dp 方图兜底，折成两行也不会把卡片撑高。
                    // 右侧留 34dp：右上角那个拍照按钮要压在同一行上，文字不能钻到它下面去
                    Text(
                        text = place?.address
                            ?: if (VehicleLocationRules.isValid(shownLat, shownLng)) {
                                VehicleLocationRules.coordinateText(shownLat!!, shownLng!!)
                            } else {
                                "车辆位置"
                            },
                        color = C.textPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 34.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle(ui, shownAt, fromCache = place?.cachedAt != null),
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(8.dp))

                    // 两个动作等宽并排：它们地位相同（一个是「去车那儿」，一个是「把目的地交给车」），
                    // 上下堆叠会让卡片长出一截，也看不出这是同一件事的两头
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CardAction(
                            // 卡片只有约 100dp 宽，长句会被裁掉 —— 这里只给两个字
                            text = if (navPreparing) "定位中" else "导航到车辆",
                            enabled = canNavigate && !navPreparing,
                            modifier = Modifier.weight(1f),
                            icon = { tint ->
                                Icon(
                                    Icons.Filled.Navigation,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(13.dp),
                                )
                            },
                        ) {
                            // 先问「走去还是开过去」：车就停在几百米外的车位上，
                            // 直接跳驾车导航是在教用户开车去找车（见 [NavModeDialog]）
                            // 但在问之前**先查一次车况**：车可能是几小时前停的，
                            // 手上这份坐标是那时拉到的，导航到旧点等于把人带错地方。
                            // 这一步是用户主动点的，不违反「不频繁拉取」的约定
                            navPreparing = true
                            scope.launch {
                                runCatching { runtime.refreshStatus() }
                                navPreparing = false
                                navPicker = true
                            }
                        }
                        CardAction(
                            text = "发送目的地",
                            enabled = true,
                            modifier = Modifier.weight(1f),
                            icon = { tint ->
                                Icon(
                                    painter = painterResource(R.drawable.ic_send_to_car),
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            onClick = onSendDestination,
                        )
                    }
                }
            }
        }

        // 右上角：拍车位。拍过之后按钮本身就是那张照片，比任何文字都更说明「已经拍过了」
        PhotoCornerButton(
            thumb = photo.thumb,
            onClick = {
                // 有照片 = 已拍过，这一点进的是「看/管理」，不是直接覆盖 ——
                // 站在车边手一抖就把唯一一张照片盖掉，代价太大
                if (photo.hasPhoto) onOpen() else photo.capture()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 9.dp, end = 11.dp),
        )
    }

    // 弹层挂在卡片**外层**：它是独立窗口，放在 Box 里既没意义，
    // 又会跟着卡片的 clip / clickable 一起被裁掉点击区域
    if (navPicker && canNavigate) {
        NavModeDialog(
            destination = place?.address,
            onDismiss = { navPicker = false },
            onPick = { mode ->
                navPicker = false
                val lat = shownLat
                val lng = shownLng
                if (lat != null && lng != null) {
                    // 目的地是坐标、不是 POI：名字用默认的「车辆位置」，
                    // 传地址（…万科金域传奇）会被高德按 POI 检索后把钉子挪到小区中心
                    val ok = MapIntents.navigate(context, lat, lng, mode = mode)
                    if (!ok) {
                        Toast.makeText(context, "没有找到可用的地图应用", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

/**
 * 卡片右上角的拍照按钮。
 *
 * 没拍过时是淡主题色圆底 + 相机图标（暗示「这能点」），
 * 拍过之后直接换成那张照片的圆形缩略 —— 比加一个勾选角标更省地方，也更难看错。
 */
@Composable
private fun PhotoCornerButton(
    thumb: Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            // 同样先垫不透明底：按钮坐在卡片的路网底纹上，半透明会让线条钻进来
            .background(C.panelBg)
            .background(if (thumb == null) C.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = remember(thumb) { thumb.asImageBitmap() },
                contentDescription = "停车位照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = "拍一张停车位照片",
                tint = C.accent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 卡片里的动作按钮。
 *
 * 不用 accent 实底 + 白字：主题色是用户自己选的，浅黄 / 浅绿时白字会糊；
 * 统一走「淡主题色底 + 主题色字」，深浅主题都稳（与弹层里的 [ActionButton] 同一套做法）。
 */
@Composable
private fun CardAction(
    text: String,
    enabled: Boolean,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val fg = if (enabled) C.accent else C.textSecondary.copy(alpha = 0.55f)
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            // 先垫一层不透明的面板色再上淡主题色：卡片底层现在是半透的路网，
            // 只叠一层 alpha 0.14 的话线条会从按钮里穿过去，看着像脏了
            .background(C.panelBg)
            .background(
                if (enabled) C.accent.copy(alpha = 0.14f) else C.chipBg.copy(alpha = 0.6f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(fg)
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 方图里的占位：只有图标 + 三两个字。
 *
 * 原来那版是「图标 + 标题 + 一句说明」，那是给 2:1 通栏大图用的；
 * 96dp 的方框里塞两行字只会挤成一团，详细原因交给右侧那行副标题。
 */
@Composable
private fun ThumbPlaceholder(ui: VehiclePlaceUi) {
    val C = LocalAppColors.current
    val label = when (ui) {
        VehiclePlaceUi.Loading -> "加载中"
        VehiclePlaceUi.NoLocation -> "无位置"
        VehiclePlaceUi.NoKey -> "未配 Key"
        VehiclePlaceUi.Failed -> "加载失败"
        is VehiclePlaceUi.Ready -> "无地图"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = C.accent.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = C.textSecondary, fontSize = 10.5.sp, maxLines = 1)
    }
}

/**
 * 副标题：位置多久前更新的，以及什么时候该怀疑它。
 *
 * 卡片改成方图之后右侧只剩约 210dp，长句会被省略号吃掉，
 * 所以这里只留「更新于 x 前」这个关键信息，原因交给方图里的占位字去说。
 *
 * [fromCache] = 这一帧车况里没有坐标，画的是上次记下的点。这时必须换个说法：
 * 用户看到地图、却以为那是「刚刚」的位置，是比没有地图更糟的事。
 */
private fun subtitle(ui: VehiclePlaceUi, reportedAt: Long, fromCache: Boolean = false): String {
    val age = VehicleLocationRules.ageText(
        VehicleLocationRules.ageMs(reportedAt, System.currentTimeMillis()),
    )
    return when (ui) {
        VehiclePlaceUi.NoLocation -> "车辆还没上报过位置"
        VehiclePlaceUi.NoKey -> "配好高德 Key 后显示地图"
        VehiclePlaceUi.Failed -> if (age != null) "地址获取失败 · 更新于 $age" else "地址获取失败"
        else -> when {
            fromCache && age != null -> "上次记录 · 更新于 $age"
            fromCache -> "上次记录的位置"
            age != null -> "更新于 $age"
            else -> "位置更新时间未知"
        }
    }
}

/**
 * 位置加载：**先亮缓存，再决定要不要联网**。
 *
 * ### 什么时候会请求（这条是「别烧车电池」的硬约束）
 * 只在三种情况下请求高德：
 *  1. **进入页面**（进首页 / 点进找车页）——组合是新建的，自然跑一次；
 *  2. **用户手动刷新**（下拉 / 顶部按钮 / 找车页那个刷新钮，见 [refreshKey] / [retry]）；
 *  3. 车况从「没有可信坐标」变成「有坐标」的那一下（冷启动报文晚到，得补一次）。
 *
 * **坐标变化本身不触发请求** —— 这是本条的关键：车在跑的时候车况可能十几秒刷新一次，
 * 位置每次都变，若把经纬度当 key，一趟一小时的车程就是几百次逆地理 + 几百张静态地图，
 * 既吃高德额度又白白把手机和车机都叫醒。所以 key 取的是「有没有坐标」这个布尔值，
 * 不是坐标本身：车开走了，卡片继续显示进页面那一刻的位置，直到用户自己刷一次。
 * 想导航就点「导航到车辆」——它用的是**车况里的当前坐标**，不是这张图上的点。
 *
 * ### 缓存与额度
 *  1. 坐标落在同一个 100 米网格内（[VehicleLocationRules.sameSpot]）且缓存里有地址、
 *     有图 → 直接返回，一个请求都不发。车停在家里，一天翻十次首页就是零次请求。
 *  2. 缓存命中但缺地址或缺图 → 先把已有的画出来，再补差的那部分。
 *  3. 位置变了 / 从来没缓存过 → 逆地理 + 静态地图都请求一次，拿到什么存什么。
 *
 * 只有 [reportedAt] 变化不会触发重载（它只在刷新车况时变，位置可能没动），
 * 所以它通过 [rememberUpdatedState] 取值，避免写缓存时把旧时间写进去。
 */
@Composable
internal fun rememberVehiclePlace(
    runtime: AppRuntime,
    latitude: Double?,
    longitude: Double?,
    reportedAt: Long,
    /**
     * 这条坐标从什么时候起没变过（[com.leapcontrol.app.LocationAnchorRule] 的结论）。
     *
     * **不能当 produceState 的 key**：它只在坐标真的变了时才变，加了也不会多跑一次请求；
     * 但它必须跟着写进缓存，否则断网重进首页时「更新于」会退回成「我们上次拉取的时刻」。
     */
    seenAt: Long = 0L,
    /** true = 找车页要的大图；false = 首页卡缩略图。两者各自独立缓存。 */
    full: Boolean = false,
    /**
     * 强制重试计数。找车页点「刷新」时 +1 —— 位置没变，但上次可能是网络失败，
     * 不给一个能重新发请求的开关，那个刷新按钮就只是重查车况，地址还是不会动。
     */
    retry: Int = 0,
    /** 首页的手动刷新计数，含义同 [retry]，只是从首页卡这一侧传进来。 */
    refreshKey: Int = 0,
): VehiclePlaceUi {
    val context = LocalContext.current
    val latestReportedAt by rememberUpdatedState(reportedAt)
    // 同 reportedAt：它不参与重启判断，但落盘时要用**最新**那个（车可能刚挪了位置）
    val latestSeenAt by rememberUpdatedState(seenAt)
    val hasKey = runtime.hasAmapKey()
    // 深色主题下要一份翻过配色的图（高德静态图没有夜间样式，见 [MapDarkTint]）。
    // 直接读 LocalAppColors 而不是加参数：所有调用点都已经被 ProvideAppColors 包住，
    // 多传一个 boolean 只会在两处调用里各写一遍 isDark，迟早漏一处
    val dark = LocalAppColors.current.isDark
    // key 用「有没有坐标」而不是坐标本身：见上面策略第 3 段
    val hasCoords = VehicleLocationRules.isValid(latitude, longitude)

    val ui by produceState<VehiclePlaceUi>(
        initialValue = VehiclePlaceUi.Loading,
        hasCoords,
        hasKey,
        full,
        retry,
        refreshKey,
        dark,
    ) {
        if (!VehicleLocationRules.isValid(latitude, longitude)) {
            // 这一帧没拿到可信坐标（车机休眠 / 还没上报 / 报了 0,0）：先别急着说「没有位置」。
            // 熄火后位置本来就应该冻结在熄火点，本地缓存里那个点就是答案 ——
            // 用 cachedAt 把「这是上次记的」说清楚，不假装是刚刚的。
            val cached = withContext(Dispatchers.IO) { VehicleLocationStore.load(context) }
            val cachedMap = if (cached != null) {
                withContext(Dispatchers.IO) { VehicleLocationStore.loadMap(context, full = full, dark = dark) }
            } else {
                null
            }
            value = if (cached != null) {
                VehiclePlaceUi.Ready(
                    map = cachedMap,
                    address = cached.address,
                    latitude = cached.latitude,
                    longitude = cached.longitude,
                    // 缓存里如果记着「这条坐标从什么时候起没变」，用它；没有才退回落盘时刻
                    cachedAt = cached.seenAt.takeIf { it > 0L } ?: cached.reportedAt,
                )
            } else {
                VehiclePlaceUi.NoLocation
            }
            return@produceState
        }
        val lat = latitude ?: return@produceState
        val lng = longitude ?: return@produceState

        val cached = withContext(Dispatchers.IO) { VehicleLocationStore.load(context) }
        val sameSpot = VehicleLocationRules.sameSpot(cached, lat, lng)
        val cachedMap = if (sameSpot) {
            withContext(Dispatchers.IO) { VehicleLocationStore.loadMap(context, full = full, dark = dark) }
        } else {
            null
        }

        if (sameSpot) {
            value = VehiclePlaceUi.Ready(cachedMap, cached?.address, lat, lng)
            // 图 + 地址都齐了：这就是终态，不再联网
            if (cachedMap != null && cached?.address != null) return@produceState
        }
        if (!hasKey) {
            value = VehiclePlaceUi.NoKey
            return@produceState
        }

        val key = runtime.amapKey()
        // 同一个点上已经有地址了就不再问逆地理：从首页卡点进找车页时通常只缺那张大图，
        // 地址在首页那次就取到并存下来了，白问一次就是白烧一次 5000/日 的额度
        val knownAddress = cached?.address?.takeIf { sameSpot }
        val address = knownAddress ?: withContext(Dispatchers.IO) {
            runCatching { AmapWebApi.reverseGeocode(key, lat, lng)?.formatted }.getOrNull()
        }
        val fetched = if (cachedMap == null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    // 取景按「米」给：首页缩略图 120 米、找车页大图 350 米。
                    // 原来是写死 zoom=16，大图等于 2.1 公里，车钉在中间根本认不出是哪栋楼
                    AmapWebApi.staticMap(
                        key = key,
                        latitude = lat,
                        longitude = lng,
                        spanMeters = if (full) MapFraming.FULL_SPAN_METERS else MapFraming.THUMB_SPAN_METERS,
                        dark = dark,
                    )
                }.getOrNull()
            }
        } else {
            null
        }

        withContext(Dispatchers.IO) {
            VehicleLocationStore.save(
                context = context,
                latitude = lat,
                longitude = lng,
                reportedAt = latestReportedAt,
                address = address,
                thumb = if (full) null else fetched,
                full = if (full) fetched else null,
                dark = dark,
                seenAt = latestSeenAt,
            )
        }

        value = if (fetched == null && cachedMap == null && address == null) {
            VehiclePlaceUi.Failed
        } else {
            VehiclePlaceUi.Ready(fetched ?: cachedMap, address, lat, lng)
        }
    }
    return ui
}

/**
 * 停车位照片的状态与三个动作，首页卡和找车页共用这一份。
 *
 * 做成对象而不是三个平行参数：两处都要「有没有 / 缩略图 / 拍 / 删」，
 * 散着传迟早两边各写一套，最后一边忘了刷新状态。
 */
@Stable
internal class ParkingPhotoState(
    val hasPhoto: Boolean,
    /** 拍摄时间（毫秒），没有照片是 0。 */
    val savedAt: Long,
    /** 已解码的缩略图；[thumbSize] 决定分辨率。 */
    val thumb: Bitmap?,
    /** 调起系统相机拍一张（覆盖旧照，但旧照要等新照落盘成功才被替换）。 */
    val capture: () -> Unit,
    /** 删掉照片（找车页的「删除」用）。 */
    val remove: () -> Unit,
    /** 磁盘上的照片被别处改动时强制重读。 */
    val refresh: () -> Unit,
)

/** 一次磁盘读取的结果，只用来当 [remember] 的 key。 */
private data class ParkingPhotoSlot(val hasPhoto: Boolean, val savedAt: Long)

/**
 * 停车位照片：读状态 + 拉起系统相机 + 拍完转正。
 *
 * ## 为什么走系统相机而不是 CameraX
 * 我们只需要「拍一张、存下来」，不需要实时滤镜、也不需要预览界面。
 * 调 `ACTION_IMAGE_CAPTURE` 由系统相机应用完成取景与对焦，用户用的是自己熟悉的界面，
 * 而且**不需要申请 CAMERA 权限**（只要不往清单里声明它，就不触发运行时权限弹窗）——
 * 为了拍一张车位照要用户额外授权摄像头，不值得。
 *
 * ## 临时文件先写再转正
 * 相机把照片写进 `parking/capture.jpg`，成功后才 rename 成 `space.jpg`。
 * 这样用户中途取消、或者相机写失败时，上一次拍的旧照片还在 ——
 * 直接往 `space.jpg` 上覆盖的话，一次误触就把唯一那张车位照毁了。
 */
@Composable
internal fun rememberParkingPhoto(
    onMessage: (String) -> Unit = {},
    /** 解码尺寸：首页卡 240 够用，找车页给 1400（整行大图）。 */
    thumbSize: Int = 240,
): ParkingPhotoState {
    val context = LocalContext.current
    val latestMessage by rememberUpdatedState(onMessage)
    val version = remember { mutableIntStateOf(0) }

    // 回调在 Activity 返回结果时执行，此时组合可能已经重建过，
    // 所以消息用 rememberUpdatedState 取最新那份 lambda（见 latestMessage）
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        when {
            // 用户自己点了取消：旧照片原样留着，也不必弹提示（他知道自己取消了）
            !ok -> ParkingPhotoStore.discard(context)
            ParkingPhotoStore.commit(context) -> {
                version.intValue++
                latestMessage("停车位已拍照")
            }
            // 相机返回成功但文件是空的：少数 ROM 的相机会无视给的输出路径
            else -> {
                ParkingPhotoStore.discard(context)
                latestMessage("照片没保存成功，请重试")
            }
        }
    }

    val slot = remember(version.intValue) {
        ParkingPhotoSlot(
            hasPhoto = ParkingPhotoStore.hasPhoto(context),
            savedAt = ParkingPhotoStore.savedAt(context),
        )
    }
    val thumb by produceState<Bitmap?>(null, version.intValue) {
        value = withContext(Dispatchers.IO) { ParkingPhotoStore.load(context, thumbSize) }
    }

    return remember(slot, thumb) {
        ParkingPhotoState(
            hasPhoto = slot.hasPhoto,
            savedAt = slot.savedAt,
            thumb = thumb,
            capture = {
                val file = ParkingPhotoStore.captureFile(context)
                // FileProvider：Android 7+ 不能把 file:// 交给别的应用，
                // 这条 URI 只在 parking/ 目录上、只对这一次拍照有效
                val uri: Uri? = runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                }.getOrNull()
                if (uri == null) {
                    latestMessage("拍照失败：创建照片文件出错")
                } else if (runCatching { launcher.launch(uri) }.isFailure) {
                    // 没装相机应用 / 被 ROM 拦截
                    ParkingPhotoStore.discard(context)
                    latestMessage("没有找到可用的相机应用")
                }
            },
            remove = {
                ParkingPhotoStore.delete(context)
                version.intValue++
                latestMessage("已删除停车位照片")
            },
            refresh = { version.intValue++ },
        )
    }
}
