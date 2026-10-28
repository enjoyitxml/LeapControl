package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.ChargeSchedule

/**
 * 预约充电三级页（从「充电中心 → 预约充电 → 时段」进入）。
 *
 * 版式对齐官方 App 的滚轮弹层：开始时间 / 结束时间各一组「时 : 分」滚轮，
 * 跨零点时结束时间左侧标「次日」；下面是重复日与「未达上限继续充电」开关。
 *
 * ## 为什么分钟按 5 分钟一档
 * 车端计划只存 `HH:mm`，官方交互也按 5 分钟滚动；60 档滚轮在手机上要划很久才能
 * 从 23:00 挪到 07:00，实际没人会去设 23:03。
 *
 * ## 确定只回写草稿
 * 这里改的是 [ChargeCenterDraft] 里的时段，点「保存并下发设置」才真正发 190 指令。
 * 三级页自己不发请求，避免用户在滚轮上转两下就打一次车控。
 */
@Composable
fun ScheduledChargeScreen(
    start: String,
    end: String,
    days: Set<Int>,
    continueUntilLimit: Boolean,
    onConfirm: (start: String, end: String, days: Set<Int>, continueUntilLimit: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val C = LocalAppColors.current
    val startParts = ChargeSchedule.parseTime(start) ?: (23 to 0)
    val endParts = ChargeSchedule.parseTime(end) ?: (7 to 0)

    var startHour by remember { mutableIntStateOf(startParts.first) }
    var startMinute by remember { mutableIntStateOf(startParts.second) }
    var endHour by remember { mutableIntStateOf(endParts.first) }
    var endMinute by remember { mutableIntStateOf(endParts.second) }
    var selectedDays by remember { mutableStateOf(days.ifEmpty { ChargeSchedule.ALL_DAYS }) }
    var continueOn by remember { mutableStateOf(continueUntilLimit) }

    val overnight = ChargeSchedule.isOvernight(
        ChargeSchedule.formatTime(startHour, startMinute),
        ChargeSchedule.formatTime(endHour, endMinute),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ChargeSubTopBar(title = "预约充电", onClose = onClose)

        Spacer(Modifier.height(6.dp))
        Text(
            text = "开始时间",
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TimeWheel(
            hour = startHour,
            minute = startMinute,
            onHourChange = { startHour = it },
            onMinuteChange = { startMinute = it },
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "结束时间",
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TimeWheel(
            hour = endHour,
            minute = endMinute,
            onHourChange = { endHour = it },
            onMinuteChange = { endMinute = it },
            // 结束时间早于开始时间 = 充到第二天，官方也是在结束时间前标「次日」
            leadingLabel = if (overnight) "次日" else null,
        )

        Spacer(Modifier.height(22.dp))
        Text("重复", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        WeekdayPicker(days = selectedDays, onChange = { selectedDays = it })

        Spacer(Modifier.height(20.dp))
        ContinueChargeOption(
            checked = continueOn,
            onToggle = { continueOn = !continueOn },
        )

        Spacer(Modifier.height(24.dp))
        ChargeConfirmButton(
            label = "确定",
            onClick = {
                onConfirm(
                    ChargeSchedule.formatTime(startHour, startMinute),
                    ChargeSchedule.formatTime(endHour, endMinute),
                    selectedDays,
                    continueOn,
                )
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 预约电池预热三级页。
 *
 * 预热计划（cmdid=161）只有「开始时间 + 重复日」，没有结束时间——加热到目标温度
 * 由车端自己停，所以这里只放一组滚轮。
 */
@Composable
fun ScheduledPreheatScreen(
    start: String,
    days: Set<Int>,
    onConfirm: (start: String, days: Set<Int>) -> Unit,
    onClose: () -> Unit,
) {
    val C = LocalAppColors.current
    val parts = ChargeSchedule.parseTime(start) ?: (7 to 30)

    var hour by remember { mutableIntStateOf(parts.first) }
    var minute by remember { mutableIntStateOf(parts.second) }
    var selectedDays by remember { mutableStateOf(days.ifEmpty { ChargeSchedule.ALL_DAYS }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ChargeSubTopBar(title = "电池预热", onClose = onClose)

        Spacer(Modifier.height(6.dp))
        Text(
            text = "开始时间",
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TimeWheel(
            hour = hour,
            minute = minute,
            onHourChange = { hour = it },
            onMinuteChange = { minute = it },
        )

        Spacer(Modifier.height(22.dp))
        Text("重复", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        WeekdayPicker(days = selectedDays, onChange = { selectedDays = it })

        Spacer(Modifier.height(24.dp))
        ChargeConfirmButton(
            label = "确定",
            onClick = {
                onConfirm(ChargeSchedule.formatTime(hour, minute), selectedDays)
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 三级页顶栏：居中标题 + 右上角关闭（不做返回箭头，避免与二级页的「返回」混淆）。 */
@Composable
private fun ChargeSubTopBar(title: String, onClose: () -> Unit) {
    val C = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .clip(CircleShape)
                .background(C.panelBg)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            val tint = C.textSecondary
            Canvas(modifier = Modifier.size(13.dp)) {
                val w = 1.8.dp.toPx()
                drawLine(tint, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
            }
        }
    }
}

/**
 * 一组「时 : 分」滚轮。
 *
 * [leadingLabel] 会在左侧显示「次日」之类的标记（结束时间跨零点时用）。
 */
@Composable
private fun TimeWheel(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    leadingLabel: String? = null,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingLabel != null) {
            Box(modifier = Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                Text(text = leadingLabel, color = C.textSecondary, fontSize = 13.sp)
            }
        }
        WheelColumn(
            items = HOURS,
            selected = hour,
            onSelect = onHourChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ":",
            color = C.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        WheelColumn(
            items = MINUTES,
            selected = minute,
            onSelect = onMinuteChange,
            modifier = Modifier.weight(1f),
        )
        if (leadingLabel != null) {
            Spacer(Modifier.width(34.dp))
        }
    }
}

/**
 * 单列滚轮：上下各留一行高度的 padding，让第一项就能停到中间；配合
 * [rememberSnapFlingBehavior] 松手自动吸附到最近一格。
 *
 * 选中值直接取 `firstVisibleItemIndex`——因为 contentPadding 恰好等于行高，
 * 第一个可见项就是正中间那一行，不需要额外换算中心坐标。
 */
@Composable
private fun WheelColumn(
    items: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = items.nearestIndex(selected),
    )
    val fling = rememberSnapFlingBehavior(listState)
    val centerIndex = listState.firstVisibleItemIndex

    // 滚动停止后把中间那一格回写成选中值，避免「视觉停在 07，实际还是 06」
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .collect { (scrolling, index) ->
                if (!scrolling) {
                    items.getOrNull(index)?.let { if (it != selected) onSelect(it) }
                }
            }
    }

    Box(
        modifier = modifier
            .height(WheelItemHeight * 3)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        // 中间选中行的浅底，让「当前值」一眼可见
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .align(Alignment.Center)
                .background(C.chipBg, RoundedCornerShape(10.dp)),
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = WheelItemHeight),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.size) { index ->
                val active = index == centerIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%02d", items[index]),
                        color = if (active) C.textPrimary else C.textSecondary.copy(alpha = 0.55f),
                        fontSize = if (active) 26.sp else 20.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** 重复日选择：周日打头，选中项浅绿底 + 主题色文字。 */
@Composable
private fun WeekdayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ChargeSchedule.WEEKDAY_ORDER.forEach { day ->
            val active = days.contains(day)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (active) C.accent.copy(alpha = 0.16f) else C.chipBg,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable {
                        // 允许全部取消吗？车端 cycles 为空会被当成全周，所以至少保留一天
                        val next = if (active) days - day else days + day
                        onChange(next.ifEmpty { setOf(day) })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "周${ChargeSchedule.weekdayShort(day)}",
                    color = if (active) C.accent else C.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 「到停止时间未达到充电上限，将继续充电」（190 的 recharge 字段）。 */
@Composable
private fun ContinueChargeOption(checked: Boolean, onToggle: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (checked) C.accent else C.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(C.accent))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "到停止时间未达到充电上限，将继续充电",
            color = C.textSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
        )
    }
}

/** 三级页底部「确定」。 */
@Composable
private fun ChargeConfirmButton(label: String, onClick: () -> Unit) {
    val C = LocalAppColors.current
    val bg = if (C.isDark) Color(0xFFF2F2F4) else Color(0xFF1C1C1E)
    val fg = if (C.isDark) Color(0xFF16181C) else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private val WheelItemHeight: Dp = 40.dp
private val HOURS: List<Int> = (0..23).toList()
private val MINUTES: List<Int> = (0..55 step 5).toList()

/**
 * 选中值在滚轮里的落点：精确值优先，否则取最近的一档。
 *
 * 车端计划里的分钟不一定是 5 的倍数（例如别的手工录入 "07:33"），
 * 直接 `indexOf` 会得到 -1 → 滚轮回退到 00，看起来像「我的设置丢了」。
 */
private fun List<Int>.nearestIndex(value: Int): Int {
    if (isEmpty()) return 0
    val exact = indexOf(value)
    if (exact >= 0) return exact
    return indices.minByOrNull { kotlin.math.abs(this[it] - value) } ?: 0
}
