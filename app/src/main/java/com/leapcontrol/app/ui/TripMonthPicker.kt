package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.trip.TripFormat

/**
 * 行程页的月份选择：日期本身就是按钮，点开直接勾选年月。
 *
 * 原来是一左一右两个 `‹` `›` 小方块，和顶栏那个圆形返回箭头长得很像，
 * 用户反馈「两个箭头不协调」；而且想看三个月前得连点三次。改成一枚居中的胶囊，
 * 点开是 4×3 的月份格子，年份横排随便切，带小圆点的是有记录的月份。
 *
 * 弹层一律居中卡片（不贴底边）：底部抽屉在 `decorFitsSystemWindows = false` 下
 * 拿不到导航栏 inset，手势区会吃掉最后一行月份。
 */
@Composable
internal fun MonthChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(C.chipBg)
            .border(1.dp, C.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DateRange,
            contentDescription = null,
            tint = C.accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = "选择月份",
            tint = C.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun MonthPickerDialog(
    selected: String,
    /** 有行程记录的月份，格式 yyyy-MM，用来在格子上点一个小圆点。 */
    availableMonths: List<String>,
    now: Long,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    val currentKey = TripFormat.monthNow(now)
    val parts = currentKey.split("-")
    val currentYear = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val currentMonth = parts.getOrNull(1)?.toIntOrNull() ?: 1
    // 数据只留 90 天，给最近 4 年已经足够；有记录的年份也并进来，防止跨年数据被挡在门外
    val years = remember(currentYear, availableMonths) {
        ((currentYear - 3)..currentYear).toList() +
            availableMonths.mapNotNull { it.substringBefore("-").toIntOrNull() }
    }.distinct().sortedDescending()
    var year by remember(selected) {
        mutableStateOf(selected.substringBefore("-").toIntOrNull() ?: currentYear)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // 弹层开着时，返回只收弹层，不要把整个行程页退掉
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
                    "选择月份",
                    color = C.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "点一下年月，直接跳到那个月的行程",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(15.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    years.forEach { y ->
                        val on = y == year
                        Text(
                            text = "$y 年",
                            color = if (on) C.accent else C.textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) C.accent.copy(alpha = 0.16f) else C.chipBg)
                                .clickable { year = y }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (col in 0 until 4) {
                            val month = row * 4 + col + 1
                            val key = TripFormat.monthKeyOf(year, month)
                            MonthCell(
                                label = "$month 月",
                                modifier = Modifier.weight(1f),
                                // 未来的月份没数据，也不该让人白点一下
                                enabled = year < currentYear ||
                                    (year == currentYear && month <= currentMonth),
                                chosen = key == selected,
                                hasData = availableMonths.contains(key),
                                onClick = { onPick(key) },
                            )
                        }
                    }
                    if (row < 2) Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(C.accent),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("有记录的月份", color = C.textSecondary, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "回到本月",
                        color = C.accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(currentKey) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "取消",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    label: String,
    enabled: Boolean,
    chosen: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (chosen) C.accent.copy(alpha = 0.18f) else C.chipBg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = when {
                    !enabled -> C.textSecondary.copy(alpha = 0.45f)
                    chosen -> C.accent
                    else -> C.textPrimary
                },
                fontSize = 12.5.sp,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            if (hasData) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(C.accent),
                )
            }
        }
    }
}
