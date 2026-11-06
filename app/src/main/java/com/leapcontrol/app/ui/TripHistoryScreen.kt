package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.trip.TripCloseResult
import com.leapcontrol.app.trip.TripFormat
import com.leapcontrol.app.trip.TripMergeResult
import com.leapcontrol.app.trip.TripOverview
import com.leapcontrol.app.trip.TripRecord
import com.leapcontrol.app.trip.TripRules
import com.leapcontrol.app.trip.TripStartResult
import com.leapcontrol.app.trip.TripTotals
import kotlinx.coroutines.delay

/**
 * 行程轨迹二级页：按月浏览的全部历史行程。
 *
 * 版式是五个方案里选定的 **B（时间轴）**：
 *  - 页顶一行三个胶囊：自动开始 / 电池容量 / 开始记录。原来这四个设置项各占一行、
 *    把首屏吃掉一整格，现在压成一行，看一眼就知道现在是手动还是自动；
 *  - 左侧一条竖轴：只写出发时间，节点连着上下段。时间只出现一次，靠轴表达顺序，
 *    这是 B 相对「按天分组大卡」（E）唯一的、也是最大的区别；
 *  - 段内：50dp 轨迹缩略图 + 右侧「到 09:40 · 28 分」/ 里程主数字，第二行平均速度与耗电；
 *  - 起终点**各占一整行**（缩略图挤在左边时 23 个字的地址只能折成「…216 号」，很难看）。
 *
 * 交互：
 *  - 排序默认「最近在前」（结束时间倒序），点月份行右边切成「最早在前」；只在本页记，不落盘；
 *  - 点一段原地展开完整地图；长按任意一段直接进多选（不用先去点右上角「选择」）；
 *  - 删除、合并都要过居中确认卡 —— 这两个动作会改本地数据，删了找不回来。
 */
@Composable
fun TripHistoryScreen(
    overview: TripOverview?,
    onBack: () -> Unit,
    onFinishTrip: () -> TripCloseResult,
    /** 手动开始一段新行程。默认手动模式，这里没有入口的话整页就没法开始记录。 */
    onStartTrip: () -> TripStartResult = { TripStartResult.NO_STATUS },
    /** 用户确认「这趟不要了」：清掉进行中的一段、不写历史（不到 30 秒那种）。 */
    onDiscardTrip: () -> Boolean = { false },
    /** 高德 Web Key：有它才能把采样点之间的直线换成真实导航路线。留空就只画直线。 */
    amapKey: String = "",
    /**
     * 电池容量（kWh）。有它才把「掉了几个百分点」换算成度数；留空只显示百分比。
     * 入口就在页顶胶囊里 —— 之前只藏在「设置 → 高级设置」，用户根本找不到。
     */
    batteryKwh: Double? = null,
    onSetBatteryKwh: (Double?) -> Unit = {},
    /** 自动开始记录：默认 false（手动）。开关就在这一页，用户自己选。 */
    autoStart: Boolean = false,
    onSetAutoStart: (Boolean) -> Unit = {},
    /** 删除一段历史行程，返回是否真的删掉了。 */
    onDeleteTrip: (Long) -> Boolean = { false },
    /** 把勾选的几段合并成一段。 */
    onMergeTrips: (Collection<Long>) -> TripMergeResult = { TripMergeResult.NOT_FOUND },
    modifier: Modifier = Modifier,
) {
    // 二级页必须自己注册返回，否则侧滑会直接 finish Activity
    BackHandler(enabled = true) { onBack() }

    val C = LocalAppColors.current
    val live = overview?.let { TripRules.live(it) }
    // 结束 / 删除 / 合并的反馈放在面板外面：成功后面板会被移除，这句话得留在本页上才看得到。
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4_000)
            notice = null
        }
    }
    val now = remember { System.currentTimeMillis() }
    val months = remember(overview) { overview?.monthKeys().orEmpty() }
    // 没手动选过就跟随「有记录的最新一个月」；overview 是异步读盘下发的，首帧可能为 null。
    // 用月份 key 而不是下标，月份选择器里选了哪个月就固定是哪个月，不会被新数据顶走。
    var pickedMonth by remember { mutableStateOf<String?>(null) }
    val monthKey = pickedMonth ?: months.firstOrNull() ?: TripFormat.monthNow(now)
    var showMonthPicker by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    val trips = remember(overview, monthKey) { overview?.tripsOfMonth(monthKey).orEmpty() }
    val totals = remember(trips) { TripTotals.of(trips) }

    // ---- 排序：默认最近的在最上面 ----
    // 只在本页记住，不落盘：离开再进来回到「最近在前」这个默认值，不会让人下次找不到自己的位置。
    var newestFirst by remember { mutableStateOf(true) }
    // 分组顺序直接吃排序结果：groupBy 保留插入顺序，所以倒序时日期自然也是从新到旧，
    // 同一天内的段也跟着倒序，不需要再各自排一遍。
    val groups = remember(trips, newestFirst) {
        val ordered = if (newestFirst) {
            trips.sortedByDescending { it.effectiveStartAt }
        } else {
            trips.sortedBy { it.effectiveStartAt }
        }
        ordered.groupBy { TripFormat.dayKey(it.endAt) }.entries.toList()
    }

    var expandedId by remember { mutableStateOf<Long?>(null) }
    // 进行中的面板默认**收起成一行**：完整面板带地图有 400dp 高，会把下面的历史清单
    // 整块挤到屏幕外（用户 2026-11-06 截图反馈「历史行程完全被遮挡」）。
    var liveExpanded by remember { mutableStateOf(false) }
    // 紧凑条上的计时也要走：本地 10 秒 tick，不请求车况。
    var tickNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            tickNow = System.currentTimeMillis()
        }
    }

    // ---- 电池容量（这一页也能填）----
    // 外部（设置页）改过要跟上；本页填完先落到本地，不等父级重组，数字立刻从 % 变成 kWh。
    var kwh by remember(batteryKwh) { mutableStateOf(batteryKwh) }
    var showBatteryKwh by remember { mutableStateOf(false) }

    // ---- 多选 / 删除 / 合并 ----
    // 选中的段用 id 记，换月自动清空（否则会把上个月的 id 带到这个月，勾了看不见）。
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(monthKey) {
        picked = emptySet()
        selecting = false
    }
    // 待确认删除的 id：单段删除是 1 个，选择模式下批量删是一组，同一个确认卡片复用
    var pendingDelete by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingMerge by remember { mutableStateOf(false) }
    // 合并前的预览：最早一段的出发 → 最后一段的结束，勾了几段、共多少公里。
    val mergePreview = remember(picked, trips) {
        val chosen = trips.filter { it.id in picked }.sortedBy { it.startAt }
        if (chosen.size < 2) null else {
            val km = chosen.sumOf { it.distanceKm }
            "${TripFormat.time(chosen.first().effectiveStartAt)} → " +
                "${TripFormat.time(chosen.last().endAt)} · ${chosen.size} 段 · " +
                "${TripFormat.km(km)} km"
        }
    }
    // 选择模式下返回键先退出选择，而不是直接离开这一页
    BackHandler(enabled = selecting) {
        selecting = false
        picked = emptySet()
    }

    // 长按 = 直接进多选并勾上这一段；已经在选择态里就按普通勾选处理。
    val onLongPress: (Long) -> Unit = { id ->
        if (!selecting) {
            selecting = true
            picked = setOf(id)
            // 展开态会把整行顶高，勾选时先收起来，免得刚勾的两段被地图挤出屏幕
            expandedId = null
        } else {
            picked = if (id in picked) picked - id else picked + id
        }
    }
    val onTogglePick: (Long) -> Unit = { id ->
        picked = if (id in picked) picked - id else picked + id
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(C.bgTop)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            ScreenTopBar(
                title = "行程轨迹",
                onBack = onBack,
                // 右上角：「怎么记」+「选择」。
                // 「怎么记」原来是一条常显横幅，占一整行还把月合计挤下去；用户要求挪到右上角。
                // 选择模式下**始终**留着「取消」：万一这个月的段被删空了，
                // 只剩空态时用户还得能退出选择态，不能卡在里面。
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!selecting) {
                            Text(
                                text = "怎么记",
                                color = C.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showGuide = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        if (selecting || trips.isNotEmpty()) {
                            Text(
                                text = if (selecting) "取消" else "选择",
                                color = if (selecting) C.textSecondary else C.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selecting = !selecting
                                        picked = emptySet()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                },
            )
        }

        // 月份 + 排序：左边选月，右边切正序 / 倒序，两件事都只影响「怎么看」，不碰数据。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthChip(
                label = TripFormat.monthLabel(monthKey, now),
                onClick = { showMonthPicker = true },
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { newestFirst = !newestFirst }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (newestFirst) "最近在前" else "最早在前",
                    color = C.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (newestFirst) "↓" else "↑",
                    color = C.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }
        }

        // 月合计压成一行小字：原来是一张 90dp 的卡，B 方案要把首屏让给设置胶囊和行程。
        // 左边距与月份胶囊、设置胶囊、日期头统一成 16dp —— 差这 4dp 在真机上看得出来。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (monthKey == TripFormat.monthNow(now)) "本月" else "该月") +
                    " ${TripFormat.km(totals.km)} km · ${totals.count} 段",
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                // 耗电合计只统计「确实算出度数」的段；一段都没有就不写，免得出现 0.0
                text = TripFormat.duration(totals.durationMs) +
                    (totals.kwh?.let { " · ${TripFormat.kwh(it)} kWh" } ?: ""),
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ---- 记录设置：一行三个胶囊（B 方案）----
        // 自动开始 / 电池容量 / 开始记录。原来是一张两行的设置卡，B 把它压成一行，
        // 首屏省下的 60dp 正好用来多显示一段行程。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            // 三块必须齐顶齐底：只有 weight 相等还不够，垂直居中兜住文字行高差异
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingPill(
                label = "自动开始",
                value = if (autoStart) "开" else "关",
                onClick = {
                    val next = !autoStart
                    onSetAutoStart(next)
                    notice = if (next) "已开启自动开始：车一开起来就自己记，锁车自动结束"
                    else "已改为手动：每次上车点一下「开始记录」"
                },
                modifier = Modifier.weight(1f),
            )
            SettingPill(
                label = "电池容量",
                value = kwh?.let { "${TripFormat.kwh(it)} kWh" } ?: "未填写",
                onClick = { showBatteryKwh = true },
                modifier = Modifier.weight(1f),
            )
            if (live == null) {
                ActionPill(
                    text = "开始记录",
                    onClick = { notice = startTripNotice(onStartTrip()) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                StatusPill(text = "正在记录", modifier = Modifier.weight(1f))
            }
        }

        // 进行中的一段不属于任何月份，也不进下面的历史清单；放在设置胶囊下面单独显示。
        // 默认只占一行，点开才展开实时面板（地图、采样条、结束按钮都在展开态里）：
        // 用户要的是「能看见历史清单」，不是让进行中的面板吃掉整个首屏。
        live?.let { current ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.panelBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { liveExpanded = !liveExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecordingDot(color = C.accent, slow = current.paused)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = if (current.paused) "已停车" else "正在记录",
                            color = C.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = TripFormat.clock(
                                (tickNow - current.effectiveStartAt).coerceAtLeast(0L),
                            ) + " · " + (current.distanceKm?.let { "${TripFormat.km(it)} km" } ?: "--") +
                                " · " + energyValue(
                                current.energy?.kwh,
                                current.energy?.usedPercent,
                            ),
                            color = C.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (liveExpanded) "收起" else "展开",
                            color = C.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    if (liveExpanded) {
                        Spacer(Modifier.height(10.dp))
                        TripLivePanel(
                            live = current,
                            onFinish = onFinishTrip,
                            onResult = { notice = it.message() },
                            onDiscard = onDiscardTrip,
                            amapKey = amapKey,
                        )
                    }
                }
            }
        }

        if (notice != null) {
            Text(
                text = notice!!,
                color = C.accent,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (trips.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "这个月还没有行程记录",
                        color = C.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "如果刚跑完一趟却是空的，按这三步看看：",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "1  默认手动：上车前点一次「开始记录」；也可以在上面打开「自动开始」",
                        color = C.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = "2  记录期间有车况刷新就记一个点；停在首页前台时约 60 秒一个",
                        color = C.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = "3  锁车或点「结束行程」就收一段；停车办事被拆成几段可以勾选合并",
                        color = C.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "正在记录的那段显示在页面顶部，不会混进清单里。切到别的 App / 锁屏后没有自动刷新，" +
                            "只在切回来时零星补点；地库没信号也会缺。",
                        color = C.textSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "查看完整说明 ›",
                        color = C.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showGuide = true }
                            .padding(horizontal = 2.dp, vertical = 5.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                items(groups, key = { it.key }) { entry ->
                    DayTimeline(
                        day = entry.key,
                        now = now,
                        dayTrips = entry.value,
                        expandedId = expandedId,
                        selecting = selecting,
                        picked = picked,
                        amapKey = amapKey,
                        // 选择模式下不展开详情：勾选时用户看的是「哪几段」，
                        // 展开一张地图会把刚勾的两段顶出屏幕。
                        onClick = { trip ->
                            if (selecting) {
                                onTogglePick(trip.id)
                            } else {
                                expandedId = if (expandedId == trip.id) null else trip.id
                            }
                        },
                        onLongClick = { trip -> onLongPress(trip.id) },
                        onDelete = { trip -> pendingDelete = setOf(trip.id) },
                        onEditBattery = { showBatteryKwh = true },
                    )
                }
                item(key = "footnote") {
                    // 这里原来把「手动/自动、稀疏采样、能耗口径、保留 90 天」整段铺了 5 行，
                    // 一屏被它吃掉四分之一，而右上角「怎么记」点开就是同一份说明。
                    // 收成一行入口：想读的人点得进去，不想读的人不用滚过一大片灰字。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showGuide = true }
                            .padding(vertical = 14.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "采样方式、里程与能耗怎么算",
                            color = C.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "怎么记 ›",
                            color = C.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 选择模式的底栏：固定在列表下面（不占列表滚动区），勾够两段才亮起「合并」。
        if (selecting && trips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.panelBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (picked.isEmpty()) "点选或长按勾选行程" else "已选 ${picked.size} 段",
                        color = C.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = mergePreview
                            ?: "中途停车办事被拆成几段时，连着勾两段以上再合并",
                        color = C.textSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 批量删：勾了就能删，但一定要过二次确认（删了找不回来）
                Text(
                    text = "删除",
                    color = if (picked.isEmpty()) C.textSecondary else AlertRed,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = picked.isNotEmpty()) { pendingDelete = picked }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                )
                Spacer(Modifier.width(4.dp))
                val ready = picked.size >= 2
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (ready) C.accent.copy(alpha = 0.16f) else C.iconBg,
                        )
                        .clickable(enabled = ready) { pendingMerge = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "合并",
                        color = if (ready) C.accent else C.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (showMonthPicker) {
            MonthPickerDialog(
                selected = monthKey,
                availableMonths = months,
                now = now,
                onPick = {
                    pickedMonth = it
                    showMonthPicker = false
                },
                onDismiss = { showMonthPicker = false },
            )
        }
        if (showGuide) {
            TripGuideDialog(onDismiss = { showGuide = false })
        }
        if (showBatteryKwh) {
            BatteryKwhDialog(
                current = kwh,
                onSave = {
                    kwh = it
                    onSetBatteryKwh(it)
                    notice = if (it == null) "已清除电池容量：耗电改回只显示百分比"
                    else "已存入 ${TripFormat.kwh(it)} kWh：历史行程的度数也一起补算好了"
                },
                onDismiss = { showBatteryKwh = false },
            )
        }
        if (pendingDelete.isNotEmpty()) {
            val one = trips.firstOrNull { it.id in pendingDelete && pendingDelete.size == 1 }
            ConfirmDialog(
                title = if (one != null) "删除这段行程？" else "删除 ${pendingDelete.size} 段行程？",
                body = if (one != null) {
                    "${TripFormat.dayTitle(TripFormat.dayKey(one.endAt), now)} " +
                        "${TripFormat.startTime(one)} → ${TripFormat.time(one.endAt)} · " +
                        "${TripFormat.km(one.distanceKm)} km\n" +
                        "整段轨迹与能耗会一起删掉，删了找不回来。"
                } else {
                    "勾选的 ${pendingDelete.size} 段会一起删掉，轨迹与能耗都不保留，删了找不回来。"
                },
                confirm = "删除",
                danger = true,
                onCancel = { pendingDelete = emptySet() },
                onConfirm = {
                    val ids = pendingDelete
                    pendingDelete = emptySet()
                    picked = picked - ids
                    val removed = ids.count { onDeleteTrip(it) }
                    notice = if (removed > 0) "已删除 $removed 段行程" else "这些行程已经不在了"
                },
            )
        }
        if (pendingMerge) {
            val count = picked.size
            ConfirmDialog(
                title = "合并 $count 段行程？",
                body = (mergePreview ?: "") + "\n" +
                    "合成一段后：起点沿用最早那段、终点沿用最后一段，" +
                    "中间停车的这段时间也会算进总时长和里程。",
                confirm = "合并",
                danger = false,
                onCancel = { pendingMerge = false },
                onConfirm = {
                    pendingMerge = false
                    val ids = picked
                    picked = emptySet()
                    // 合并完就退出选择模式：列表只剩一段合成后的行程，还停在选择态会让人以为没生效
                    selecting = false
                    notice = when (onMergeTrips(ids)) {
                        TripMergeResult.MERGED -> "已合并成一段行程"
                        TripMergeResult.TOO_FEW -> "至少要勾两段才能合并"
                        TripMergeResult.NOT_FOUND -> "勾的行程已经不在了，重新选一次"
                    }
                },
            )
        }
    }
}

/**
 * 页顶一个小胶囊：上面一行标签、下面一行当前值。
 *
 * B 方案把「自动开始 / 电池容量」压成这种 48dp 的胶囊排成一行 —— 它们都是「一眼就能看完、
 * 偶尔需要改一次」的设置，不值得各占一整行把首屏吃掉。
 */
@Composable
private fun SettingPill(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(C.panelBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = C.textSecondary,
            fontSize = 9.5.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = C.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 主色胶囊（开始记录）：和两个设置胶囊同高，视觉上更主动一点。 */
@Composable
private fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(C.accent.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = C.accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 只读状态胶囊：正在记录时占住「开始记录」那一格，不让人重复点。 */
@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(C.accent.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = C.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 一天的时间轴：日期头 + 轴上若干段。
 *
 * 为什么把时间放在左侧独立的 38dp 列而不是写在卡片里：B 方案的核心就是让「几点出发」
 * 形成一条竖排的节奏，卡片里再重复一次时间会让两列时间打架，也白白吃掉宽度。
 */
@Composable
private fun DayTimeline(
    day: String,
    now: Long,
    dayTrips: List<TripRecord>,
    expandedId: Long?,
    selecting: Boolean,
    picked: Set<Long>,
    amapKey: String,
    onClick: (TripRecord) -> Unit,
    onLongClick: (TripRecord) -> Unit,
    onDelete: (TripRecord) -> Unit,
    onEditBattery: () -> Unit,
) {
    val C = LocalAppColors.current
    val totals = remember(dayTrips) { TripTotals.of(dayTrips) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TripFormat.dayTitle(day, now),
                color = C.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${dayTrips.size} 段 · ${TripFormat.duration(totals.durationMs)}",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${TripFormat.km(totals.km)} km",
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        dayTrips.forEachIndexed { index, trip ->
            TimelineRow(
                trip = trip,
                last = index == dayTrips.lastIndex,
                expanded = !selecting && expandedId == trip.id,
                amapKey = amapKey,
                selecting = selecting,
                checked = trip.id in picked,
                onClick = { onClick(trip) },
                onLongClick = { onLongClick(trip) },
                onDelete = { onDelete(trip) },
                onEditBattery = onEditBattery,
            )
        }
    }
}

/**
 * 轴上一段行程：左侧出发时间 → 竖轴与节点 → 卡片。
 *
 * 长按直接进多选：这是用户明确要的手势，不用先点右上角「选择」。
 * 选择态下缩略图右上角压一个勾选角标 —— 单独在行首挤一个 19dp 圆圈会把右侧文本压窄、
 * 地址被迫折行，角标不占布局。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    trip: TripRecord,
    last: Boolean,
    expanded: Boolean,
    amapKey: String,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onEditBattery: () -> Unit,
) {
    val C = LocalAppColors.current
    // 0 km 的段（车机没读到里程）：节点变灰，一眼就能和正常段区分开
    val empty = trip.distanceKm == 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Text(
            text = TripFormat.startTime(trip),
            color = C.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(38.dp)
                .padding(top = 12.dp),
        )
        Spacer(Modifier.width(5.dp))
        // 竖轴 + 节点：线画满整行高度（最后一行不画，轴就停在最后一个节点上）
        Box(
            modifier = Modifier
                .width(11.dp)
                .fillMaxHeight(),
        ) {
            if (!last) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(C.border.copy(alpha = 0.55f)),
                )
            }
            // 节点：外圈用页面底色垫一圈，把竖线断开（同设计稿的 3px 描边）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 15.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(C.bgTop),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (empty) C.textSecondary.copy(alpha = 0.45f) else C.accent),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (selecting && checked) C.accent.copy(alpha = 0.07f) else C.panelBg,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 11.dp, vertical = 10.dp),
        ) {
            // 缩略图 48dp，右侧两行文字**垂直居中**：之前顶对齐时，图下面空出一截，
            // 看着像排版没做完（2026-11-06 用户截图反馈）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TripThumbMap(points = trip.points, side = 48.dp)
                    if (selecting) {
                        PickBadge(checked = checked)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 时间只写「到 09:40」：出发时间已经在左边的轴上，卡片里不再重复
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "到 ${TripFormat.time(trip.endAt)} · " +
                                TripFormat.duration(trip.durationMs),
                            color = C.textSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${TripFormat.km(trip.distanceKm)} km",
                            color = C.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    // 平均速度与耗电并成一行小字：B 方案把三宫格换成这一行，
                    // 卡片矮了近 30dp，一屏能多放小半段。
                    Text(
                        text = segmentFacts(trip),
                        color = if (empty) AlertRed else C.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            // 起终点各占整行：缩略图挤在左边时 23 个字的地址只能折成两行、末行剩「216 号」。
            // **不设 maxLines**：地址整段是用户要看的信息，宁可多占一行也不许出现省略号
            //（2026-11-06 反馈里最刺眼的就是那一串「…」）。
            EndPointRow(start = true, label = trip.startLabel)
            Spacer(Modifier.height(4.dp))
            EndPointRow(start = false, label = trip.endLabel)

            if (expanded) {
                Spacer(Modifier.height(11.dp))
                TripMapPreview(points = trip.points, amapKey = amapKey)
                Spacer(Modifier.height(11.dp))
                TripSampleStrip(
                    points = trip.points,
                    accent = C.accent,
                    track = C.barTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${trip.sampleCount} 个采样点 · 行程条按时间分桶示意（最多 6 个刻度），不是连续轨迹",
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                // 展开后把能耗拆开讲：起点电量、终点电量、折算的百公里电耗。
                // 没有电池容量时只有百分比，绝不凭空补一个度数额。
                energyDetail(trip.energy)?.let { detail ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = detail,
                        color = C.textPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    // 只给了百分比：直接给一个能点的入口，别让人再去设置里翻（2026-11-06 反馈）
                    if (trip.energy?.kwh == null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "填上电池容量就能换算成度数 ›",
                            color = C.accent,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onEditBattery)
                                .padding(vertical = 3.dp),
                        )
                    }
                }
                if (trip.startLabel == null || trip.endLabel == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "填写高德 Web Key 后，起终点会显示中文地址",
                        color = C.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                // 删除放在展开态最下面：不展开点不到，避免手滑把一整段删掉。
                // 点它只弹出确认卡，真正删除要再点一次。
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "删除这段行程",
                    color = AlertRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 2.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/**
 * 段内那行补充事实：`平均 6 km/h · 耗电 0.4 kWh`。
 *
 * 里程没读到时只说这一件事（其余数字都是 0，摆出来只会误导）；里程是估算的才补一句「估算」。
 */
internal fun segmentFacts(trip: TripRecord): String {
    if (trip.distanceKm == 0.0) return "里程未读到"
    val parts = mutableListOf<String>()
    trip.averageKmh?.let { parts += "平均 ${TripFormat.speed(it)} km/h" }
    val energy = energyValue(trip.energy?.kwh, trip.energy?.usedPercent)
    if (energy != "--") parts += "耗电 $energy"
    if (trip.distanceEstimated) parts += "里程估算"
    return parts.joinToString(" · ").ifEmpty { "均速未读到" }
}

/** 缩略图右上角的勾选角标：选中 = 实心主色 + 白勾，未选 = 半透明黑底空心圈。 */
@Composable
private fun BoxScope.PickBadge(checked: Boolean) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(3.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(if (checked) C.accent else Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        } else {
            Box(
                Modifier
                    .size(18.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            )
        }
    }
}

/**
 * 起点 / 终点一行：小圆点 + 地址。
 *
 * **不限制行数、不加省略号**：地址是用户真要读的信息，被截成「…幸福路 1…」
 * 等于没显示。地址最长也就 25 个字，最多折两行，省这一行不值得。
 */
@Composable
private fun EndPointRow(start: Boolean, label: String?) {
    val C = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(if (start) C.textPrimary else C.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label ?: if (start) "起点未记录到地址" else "终点未记录到地址",
            color = if (label == null) C.textSecondary else C.textPrimary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 居中的二次确认卡片（删除 / 合并共用）。
 *
 * 两个动作都会改本地数据、且删了找不回来，所以必须点两下；
 * [danger] 只是把确认按钮换成警示红，措辞与非危险操作保持一致。
 */
@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    danger: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val C = LocalAppColors.current
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(enabled = true, onBack = onCancel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = DIALOG_SCRIM))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .consumeClicks()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.dialogBg)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = title,
                    color = C.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.iconBg)
                            .clickable(onClick = onCancel)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消", color = C.textPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (danger) AlertRed.copy(alpha = 0.14f)
                                else C.accent.copy(alpha = 0.16f),
                            )
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = confirm,
                            color = if (danger) AlertRed else C.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
