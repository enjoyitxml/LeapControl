package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.trip.TripEnergy
import com.leapcontrol.app.trip.TripCloseResult
import com.leapcontrol.app.trip.TripFormat
import com.leapcontrol.app.trip.TripLive
import com.leapcontrol.app.trip.TripRules
import kotlinx.coroutines.delay

/**
 * 「正在记录的行程」实时面板。首页小卡和行程二级页共用同一份。
 *
 * 关键约束：**显示 ≠ 结束**。这里只把 `TripOverview.open` 里的点算成读数给人看，
 * 不写盘、不收尾、也不额外拉车况。本地时钟每 10 秒重算一次时长和「距上次采样」，
 * 纯 UI 计时，不是后台轮询车况。二级页那张地图会取高德公开瓦片（不需要 Key、不唤醒车机），
 * 但它只是把已经存下来的采样点画出来，不是新的一次车况采集。
 * 结束行程必须用户点按钮，结果通过 [onResult] 回传给外层显示，因为成功后面板会被移除，
 * 「已结束」那句反馈得挂在还在的父卡片上才看得到。
 */
@Composable
fun TripLivePanel(
    live: TripLive,
    onFinish: () -> TripCloseResult,
    onResult: (TripCloseResult) -> Unit,
    /**
     * 用户确认「这趟不要了」：清掉这一段、不写历史。
     *
     * 不到 30 秒 / 没读到位移的段建不了档，但也不能一直卡在记录中 ——
     * 返回 false 表示当前已经没有进行中的行程（可能已被锁车自动收尾）。
     */
    onDiscard: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** 高德 Web Key：有它才能把采样点之间的直线换成真实导航路线。 */
    amapKey: String = "",
) {
    val C = LocalAppColors.current
    // 只驱动 UI 计时的本地时钟：不请求车况，离开页面时协程自动取消。
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }
    var confirm by remember { mutableStateOf(false) }
    // 点结束之前先把结果算一次：这段会不会真的建档。
    // 不到 30 秒 / 点数不够 / 没位移时，确认框直接写清「结束后不会记录」，
    // 主按钮换成「仍要结束」—— 一次确认就能丢掉，不用点了再弹一层。
    val guard = TripRules.closeGuard(live)
    // 结束失败（点数不够 / 太短 / 算不出里程）时：这段会**留在记录中**，按钮也还在。
    // 只在卡片顶部闪一行小字，用户看到的就是「点了没反应」—— 所以失败一律弹一张说明卡。
    var failed by remember { mutableStateOf<TripCloseResult?>(null) }

    val freshMs = (now - live.lastAt).coerceAtLeast(0L)
    val stale = freshMs > 5 * 60_000L
    val status = when {
        live.paused -> "已停车 · 可结束"
        stale -> "等待新的车况刷新"
        else -> "正在记录"
    }
    val statusColor = if (stale) C.textSecondary else C.accent

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordingDot(color = statusColor, slow = live.paused || stale)
            Spacer(Modifier.width(7.dp))
            Text(
                text = status,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                // 出发时间从车辆开始行进算，不能用打开 App 时刷到的那帧静止快照
                text = TripFormat.departureLabel(live),
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(if (compact) 9.dp else 11.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            LiveMetric(TripFormat.clock(now - live.effectiveStartAt), "已记录")
            LiveMetric(
                value = live.distanceKm?.let { "${TripFormat.km(it)} km" } ?: "--",
                label = if (live.distanceEstimated) "里程 · 估算" else "里程",
            )
            // 能耗主值优先给度数（有容量时），没有容量退回电量百分比，都没有就 --。
            // 采样点数从指标行挪到下面那行文字里 —— 四格放不下五项，而采样数是辅助信息。
            LiveMetric(energyValue(live.energy?.kwh, live.energy?.usedPercent), "耗电")
            LiveMetric(
                value = live.lastSpeedKmh?.let { "${TripFormat.speed(it)} km/h" } ?: "--",
                label = "最后车速",
            )
        }

        Spacer(Modifier.height(8.dp))
        TripSampleStrip(
            points = live.points,
            accent = C.accent,
            track = C.barTrack,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 14.dp else 18.dp),
        )
        // 能耗明细单起一行：电量从多少掉到多少、以及百公里折算，指标格里塞不下。
        energyDetail(live.energy)?.let { detail ->
            Spacer(Modifier.height(5.dp))
            Text(
                text = detail,
                color = C.textSecondary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))

        // 首页小卡保持轻量（设计方案 ② 行程条，不放地图）；二级页给一张实时轨迹图，
        // 和历史清单里每一段展开后的地图是同一套渲染。
        if (!compact) {
            Spacer(Modifier.height(9.dp))
            TripMapPreview(
                points = live.points,
                amapKey = amapKey,
            )
            Spacer(Modifier.height(9.dp))
        }

        Text(
            text = when {
                freshMs < 60_000L -> "刚刚采样 · ${TripFormat.time(live.lastAt)} · ${live.sampleCount} 个点"
                stale -> "已 ${TripFormat.duration(freshMs)} 没采到新点 · 切回零控或下拉刷新补点"
                else -> "最近采样 ${TripFormat.time(live.lastAt)} · ${TripFormat.duration(freshMs)} 前 · " +
                    "${live.sampleCount} 个点"
            },
            color = if (stale) C.accent else C.textSecondary,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // 这两行是「为什么看着像没记到」的直接答案：车机没上报新数据，界面就老实说。
        if (live.distanceKm == null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "车机还没上报新的里程或坐标变化，这趟暂时算不出里程",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        } else if (!live.hasLocation) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "这趟还没读到坐标，起终点可能显示不全",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.height(if (compact) 9.dp else 11.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(C.accent.copy(alpha = 0.14f))
                .clickable { confirm = true }
                .padding(vertical = if (compact) 9.dp else 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "结束行程",
                color = C.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        // 首页小卡要精简（设计方案 A：口径说明只在二级页出现），这句留在二级页就够了。
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这段还在记录中，结束后才进入历史清单；锁车或停稳约 5 分钟后也会自动收尾。",
                color = C.textSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }

    if (confirm) {
        FinishTripDialog(
            live = live,
            guard = guard,
            onConfirm = {
                confirm = false
                if (guard == null) {
                    val result = onFinish()
                    onResult(result)
                    // 成功不进这里：open 已被清掉，面板下一帧就变成「开始记录」，
                    // 失败则 open 仍在、按钮还在，必须明说为什么没结束。
                    if (result != TripCloseResult.SAVED) failed = result
                } else if (onDiscard()) {
                    onResult(TripCloseResult.DISCARDED)
                } else {
                    // 已经没有进行中的段了（多半是锁车时自动收尾）：这不是「丢了」，是「早就结束」
                    onResult(TripCloseResult.NO_OPEN)
                    failed = TripCloseResult.NO_OPEN
                }
            },
            onDismiss = { confirm = false },
        )
    }
    failed?.let { result ->
        TripCloseNoticeDialog(
            title = result.title(),
            body = result.hint(),
            // 点数不够 / 太短 / 没位移都会把这一段留在记录中：给一条「不要了」的明确出口，
            // 否则用户只能干等满 30 秒，或者反复点同一个按钮。
            offerDiscard = result.canDiscard(),
            onDiscard = {
                failed = null
                onResult(
                    if (onDiscard()) TripCloseResult.DISCARDED else TripCloseResult.NO_OPEN,
                )
            },
            onDismiss = { failed = null },
        )
    }
}

/** 这一种「结束失败」能不能直接丢掉：能，就多给一个「仍要结束」的按钮。 */
internal fun TripCloseResult.canDiscard(): Boolean = when (this) {
    TripCloseResult.TOO_FEW_POINTS,
    TripCloseResult.TOO_SHORT,
    TripCloseResult.NO_DISTANCE,
    -> true
    TripCloseResult.SAVED,
    TripCloseResult.NO_OPEN,
    TripCloseResult.DISCARDED,
    -> false
}

/** 结束失败的标题：一句话说清是哪种原因，别让用户从正文里找。 */
internal fun TripCloseResult.title(): String = when (this) {
    TripCloseResult.SAVED -> "已结束"
    TripCloseResult.NO_OPEN -> "这段已经结束了"
    TripCloseResult.TOO_FEW_POINTS -> "还差一个采样点"
    TripCloseResult.TOO_SHORT -> "这趟不到 30 秒"
    TripCloseResult.NO_DISTANCE -> "还没读到位移"
    TripCloseResult.DISCARDED -> "已结束，这段没记录"
}

/**
 * 结束失败之后该做什么。标题已经说了原因，这里只说下一步 —— 不重复「还不到 30 秒」。
 */
internal fun TripCloseResult.hint(): String = when (this) {
    TripCloseResult.SAVED -> ""
    TripCloseResult.NO_OPEN ->
        "可能已经因为锁车或停车自动收尾了。回首页下拉刷新一次车况，这段就会出现在历史清单里。"
    TripCloseResult.TOO_FEW_POINTS ->
        "只有 1 个点建不了一段。等下一次车况刷新补一个点再结束；" +
            "也可以点「仍要结束」，直接丢掉这一段。"
    TripCloseResult.TOO_SHORT ->
        "不到 30 秒的行程不建档。再等一会儿，或者点「仍要结束」直接丢掉这一段。"
    TripCloseResult.NO_DISTANCE ->
        "车机还没上报行驶状态或位移。回首页刷新一次车况再来结束；" +
            "也可以点「仍要结束」，直接丢掉这一段。"
    TripCloseResult.DISCARDED -> ""
}

/**
 * 预判不会建档时，确认框的正文：先说清「会发生什么」，再让用户选。
 * 原因写在标题里了，这里不重复，只交代后果与另一条路。
 */
internal fun TripCloseResult.previewBody(): String = when (this) {
    TripCloseResult.TOO_FEW_POINTS ->
        "现在只有 1 个采样点，建不了一段行程。点「仍要结束」这一段会直接丢掉，不进历史清单。"
    TripCloseResult.TOO_SHORT ->
        "不到 30 秒的行程不建档。点「仍要结束」这一段会直接丢掉；" +
            "想留下这段就点「取消」，再记录一会儿后结束。"
    TripCloseResult.NO_DISTANCE ->
        "车机还没上报行驶状态或位移，这趟建不了档。点「仍要结束」会直接丢掉，不进历史清单。"
    else -> ""
}

/**
 * 结束失败的说明卡。
 *
 * [offerDiscard] 为 true 时多给一个「仍要结束」：这一段本来就不会被记录，
 * 用户想现在收手就该能收手，不能只剩一个「知道了」把人按在记录态里。
 */
@Composable
private fun TripCloseNoticeDialog(
    title: String,
    body: String,
    offerDiscard: Boolean,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(enabled = true, onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = DIALOG_SCRIM))
                .clickable(onClick = onDismiss),
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
                if (!offerDiscard) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.accent.copy(alpha = 0.16f))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "知道了",
                            color = C.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.iconBg)
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("继续记录", color = C.textPrimary, fontSize = 13.sp, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.accent.copy(alpha = 0.16f))
                                .clickable(onClick = onDiscard)
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "仍要结束",
                                color = C.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「正在录制」指示灯：实心里跳 + 外圈光晕一起呼吸，纯本地动画、不联网。
 * 有进行中的行程时才组合进界面；已停车 / 等刷新时闪慢一点，别看着像还在跑。
 */
@Composable
internal fun RecordingDot(
    color: Color,
    modifier: Modifier = Modifier,
    slow: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "trip_recording")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (slow) 0.55f else 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (slow) 1_600 else 900,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trip_recording_pulse",
    )
    Box(modifier = modifier.size(10.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f * pulse)),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulse.coerceIn(0.25f, 1f))),
        )
    }
}

/** 能耗主值：有度数给度数，只有百分比给百分比，都没有给 `--`（不是 0）。 */
internal fun energyValue(kwh: Double?, usedPercent: Double?): String = when {
    kwh != null -> "${TripFormat.kwh(kwh)} kWh"
    usedPercent != null -> TripFormat.percent(usedPercent)
    else -> "--"
}

/**
 * 能耗明细一句话：`电量 78% → 73% · 百公里 14.1 kWh`。
 *
 * 电量没下降（中途充过电）时明说，不给假数字；只有表显续航时退回「续航少了多少」。
 * 没有电池容量时主值只有百分比，这里不再解释 —— 设置页那一行已经写清楚了。
 */
internal fun energyDetail(energy: TripEnergy?): String? {
    if (energy == null) return null
    val parts = mutableListOf<String>()
    if (energy.startSoc != null) {
        parts += "电量 ${TripFormat.percent(energy.startSoc)} → ${TripFormat.percent(energy.endSoc)}"
    } else if (energy.rangeUsedKm != null) {
        parts += "表显续航少了 ${TripFormat.km(energy.rangeUsedKm)} km"
    }
    when {
        energy.per100Km != null -> parts += "百公里 ${TripFormat.kwh(energy.per100Km)} kWh"
        energy.kwh != null -> parts += "耗电 ${TripFormat.kwh(energy.kwh)} kWh"
        energy.usedPercent == null && energy.startSoc != null && energy.endSoc != null ->
            parts += "这趟电量没有下降（可能中途充过电）"
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** 结束结果的通俗文案。点数太少 / 太短 / 没里程时保留 open，要告诉用户下一步做什么。 */
internal fun TripCloseResult.message(): String = when (this) {
    TripCloseResult.SAVED -> "已结束，这段行程已存进历史清单"
    TripCloseResult.NO_OPEN -> "当前没有正在记录的行程"
    TripCloseResult.TOO_FEW_POINTS ->
        "还只有 1 个采样点：等下一次车况刷新补一个点再来结束，也可以直接丢掉这一段"
    TripCloseResult.TOO_SHORT -> "这趟还不到 30 秒：再等一会儿，也可以直接丢掉这一段"
    TripCloseResult.NO_DISTANCE ->
        "这趟还没读到行驶状态或位移：刷新一次车况再来结束，也可以直接丢掉"
    TripCloseResult.DISCARDED -> "已结束，这段没有存进历史"
}

@Composable
private fun RowScope.LiveMetric(value: String, label: String) {
    val C = LocalAppColors.current
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = value,
            color = C.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = C.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 居中确认卡片：结束会清掉 open，是本地数据动作，点错了没法撤销。 */
@Composable
private fun FinishTripDialog(
    live: TripLive,
    /** 预判结果：非 null 表示这段不会建档，确认框要提前说清并换成「仍要结束」。 */
    guard: TripCloseResult?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(enabled = true, onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = DIALOG_SCRIM))
                .clickable(onClick = onDismiss),
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
                    text = if (guard == null) "结束当前行程？" else "这趟不会存进历史",
                    color = C.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (guard == null) {
                        "这段会立刻存进历史清单；结束时间按最后一次车况刷新" +
                            "（${TripFormat.time(live.lastAt)}）算，之后采到的点会另起一段。"
                    } else {
                        guard.previewBody()
                    },
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
                            .clickable(onClick = onDismiss)
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
                            .background(C.accent.copy(alpha = 0.16f))
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (guard == null) "结束行程" else "仍要结束",
                            color = C.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
