package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.ChargeAnchor
import com.leapcontrol.app.journal.ChargeCycle
import com.leapcontrol.app.journal.ChargeCycleUi
import com.leapcontrol.app.journal.JournalDates

/**
 * 零控首页的「自上次充电行驶里程」卡：**这次充电之后真实开了多少公里**。
 *
 * ## 这条数据是怎么来的
 * 车端没有充电记录接口，只有「实时充电状态 + 累计总里程」两个信号。所以这个数字是
 * 零控在拿到车况时**观测**出来的：充电时车停着不动，那一刻的表显里程就是准确起点；
 * 之后拿当前里程一减，就是这段时间跑的路。判定细节见 [ChargeCycle]。
 *
 * ## 为什么要有「从现在起算」
 * 观测式的短板是漏检：整夜慢充、充完直接开走、期间一次没打开 App，这次充电就看不见，
 * 起点会停在上一次，数字只会偏大。这个漏检没法从数据里补回来（没有充电记录可查），
 * 所以点进弹层给一个把起点拉回当下的按钮 —— 比让用户盯着一个越来越离谱的数强。
 *
 * ## 版式（左右分栏）
 * 左栏是主数字「128.4 km / 真实行驶」，右栏是「3 天 / 82% → 46%」，中间一条 1dp 竖线；
 * 顶部标题行右侧只放日期。和旧版相比去掉了数字右边那句「自上次充电」——
 * 标题已经说了，重复一次反而把主数字挤小。
 */
@Composable
fun HomeChargeCycleCard(
    anchor: ChargeAnchor?,
    currentKm: Double?,
    soc: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val ui = ChargeCycle.summary(anchor, currentKm)
    val ready = ui as? ChargeCycleUi.Ready
    // 只跟起点有关：起点没变就不重取时钟，避免每次重组都读一次系统时间
    val now = remember(anchor?.at) { System.currentTimeMillis() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自上次充电行驶里程",
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (ready != null) {
                Text(
                    text = JournalDates.shortLabel(ready.at),
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(9.dp))

        if (ready == null) {
            // 还没记过起点 / 读不到当前里程：把「该做什么」直接写在数字的位置上，
            // 比在角落里挂一行小字更容易被看见
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = emptyHint(ui),
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            return@Column
        }

        val days = daysBetween(ready.at, now)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1.3f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatKm(ready.km),
                        color = C.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "km",
                        color = C.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "真实行驶",
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 11.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(C.border),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = durationLabel(days),
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                val trail = socTrail(ready.soc, soc)
                if (trail != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = trail,
                        color = C.textSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = footerText(ready.km, days),
                color = C.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "›",
                color = C.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/** 自然日差（不是 24 小时的倍数）：「昨天充的」就该显示 1 天。 */
private fun daysBetween(from: Long, to: Long): Long {
    if (from <= 0L) return 0L
    val diff = JournalDates.startOfDay(to) - JournalDates.startOfDay(from)
    return (diff / 86_400_000L).coerceAtLeast(0L)
}

/** 右栏第一行：跑了几天。 */
private fun durationLabel(days: Long): String = when {
    days <= 0L -> "今天"
    days == 1L -> "1 天"
    else -> "$days 天"
}

/** 右栏第二行：电量变化。两个电量缺一个就不显示，不猜。 */
private fun socTrail(from: Int?, to: Int?): String? {
    if (from == null || to == null) return null
    return "$from% → $to%"
}

/** 底部那行：当天不写日均（分母是 0，写出来的数没意义）。 */
private fun footerText(km: Double, days: Long): String =
    if (days <= 0L) "今天开始算" else "日均 ${formatKm(km / days)} km"

/** 没有数字时，数字位置上的那句话。 */
private fun emptyHint(ui: ChargeCycleUi): String = when (ui) {
    ChargeCycleUi.NoAnchor -> "充电时打开一次零控，即可自动记录"
    else -> "刷新车况后显示"
}

/**
 * 点卡片弹出的说明层：讲清数字怎么来的、为什么可能是旧的，并提供「从现在起算」。
 *
 * 文案刻意压到三句、每句一件事：数字是什么 → 起点从哪来 → 旧了怎么办。
 * 之前那版把「车端没有历史接口」写成了两段技术解释，用户读不完就关了。
 */
@Composable
fun ChargeCycleSheet(
    anchor: ChargeAnchor?,
    currentKm: Double?,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    val C = LocalAppColors.current
    val canRestart = currentKm != null && currentKm > 0.0

    val current = currentKm?.takeIf { it > 0.0 }

    SheetShell(title = "充电里程", onDismiss = onDismiss) {
        Text(
            "零控记的是仪表盘上的总里程（保养单上那个数），充电时把它存成起点。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "以后拿当前总里程减这个起点，就是真实开掉的公里，跟续航怎么变无关。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )

        if (anchor != null && current != null) {
            Spacer(Modifier.height(12.dp))
            ChargeCycleDiagram(anchor = anchor, currentKm = current)
        }

        Spacer(Modifier.height(11.dp))
        Text(
            text = when {
                anchor == null -> "现在按下面的按钮，就从当前里程开始算。"
                current == null -> "读不到当前总里程，刷新车况后再试。"
                else -> "错过那次充电的话，起点还停在上一次，数字会偏大。按下面可以把起点改到现在。"
            },
            color = C.textSecondary,
            fontSize = 10.sp,
            lineHeight = 15.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.chipBg)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("关闭", color = C.textSecondary, fontSize = 14.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canRestart) C.accent else C.chipBg)
                    .clickable(enabled = canRestart, onClick = onRestart)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "从现在起算",
                    color = if (canRestart) readableOn(C.accent) else C.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 弹层里的「减法示意」：起点 —— 现在 —— 差值。
 *
 * 光靠文字解释「总里程相减」，没算过这道题的人还是要绕一下。这里直接把算式画出来：
 * 左上一个带日期的起点里程、右上一个当前里程、中间一个减号，横线下面就是结果。
 * 数字全部来自真实信号，没有示意值。
 */
@Composable
private fun ChargeCycleDiagram(anchor: ChargeAnchor, currentKm: Double) {
    val C = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(C.chipBg)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "充电时 · ${JournalDates.shortLabel(anchor.at)}",
                    color = C.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatKm(anchor.km)} km",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Text(
                text = "−",
                color = C.textSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 1.dp),
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = "现在",
                    color = C.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatKm(currentKm)} km",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(C.border))
        Spacer(Modifier.height(9.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "真实行驶",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatKm(currentKm - anchor.km)} km",
                color = C.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
            )
        }
    }
}
