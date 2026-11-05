package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CostRange
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.CostSummary
import com.leapcontrol.app.journal.GoalProgress
import com.leapcontrol.app.journal.InsurancePlan
import com.leapcontrol.app.journal.JournalCardId
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.MaintenancePlan
import com.leapcontrol.app.journal.MedalGroup
import com.leapcontrol.app.journal.Medals

/** 仪表盘的左右留白。地图那一块要贴到屏幕边缘，所以留白不能挂在整列上。 */
private val Gutter = 22.dp

/** 块与块之间的间距。 */
private val BlockGap = 12.dp

/**
 * 零记首页的「数据仪表盘」版式。
 *
 * ## 为什么是这个结构
 * 整页只保留**一个**视觉主角：顶部那三格数字。其余内容按「仪表读数」的读法排：
 * 目标是一条横向进度条（不是环），成本是一条分类占比条，足迹是一张铺到屏幕两边的地图。
 * 圆环、卡片阴影、彩色角标全部撤掉 —— 高级感来自克制的层级，不来自装饰。
 *
 * ## 顺序还是用户的
 * 三格表头是固定的（它汇总的是「这辆车」的状态，不属于任何一张卡），
 * 下面的目标 / 成本 / 足迹 / 提醒仍按 [JournalCardId] 的顺序渲染：
 * 相邻的两张提醒会合并进同一块面板，用发丝线分隔，看起来像一组待办。
 */
@Composable
fun JournalDashBoard(
    cards: List<JournalCardId>,
    data: JournalData,
    progress: GoalProgress?,
    unlockedIds: Set<String>,
    costSummary: CostSummary,
    vehicleKm: Double?,
    onEdit: () -> Unit,
    onGoal: () -> Unit,
    onCost: () -> Unit,
    onCheckin: () -> Unit,
    onInsurance: () -> Unit,
    onMaintenance: () -> Unit,
) {
    val days = progress?.daysOwned ?: data.ownershipDays ?: 0
    // 累计里程只认表显总里程：dailyKm 之和是「最近这些天跑了多少」，不是累计值
    val km = vehicleKm?.takeIf { it > 0.0 }

    Column(Modifier.fillMaxWidth()) {
        DashStats(days = days, km = km, monthCost = costSummary.thisMonth)
        Spacer(Modifier.height(BlockGap))

        var index = 0
        while (index < cards.size) {
            val id = cards[index]
            if (id == JournalCardId.INSURANCE || id == JournalCardId.MAINTENANCE) {
                // 连续的提醒合成一块面板：一条发丝线分隔两行，比两张独立小卡更稳
                val run = ArrayList<JournalCardId>(2)
                while (
                    index < cards.size &&
                    (cards[index] == JournalCardId.INSURANCE || cards[index] == JournalCardId.MAINTENANCE)
                ) {
                    run.add(cards[index])
                    index++
                }
                DashReminderPanel(
                    ids = run,
                    data = data,
                    currentKm = vehicleKm,
                    onInsurance = onInsurance,
                    onMaintenance = onMaintenance,
                )
                Spacer(Modifier.height(BlockGap))
            } else {
                when (id) {
                    JournalCardId.GOAL -> DashGoal(
                        progress = progress,
                        targetYears = data.goal?.targetYears ?: 0,
                        unlockedIds = unlockedIds,
                        onClick = onGoal,
                    )

                    JournalCardId.COST -> DashCost(summary = costSummary, onClick = onCost)
                    JournalCardId.FOOTPRINT -> DashFootprint(data = data, onClick = onCheckin)

                    else -> Unit
                }
                index++
                Spacer(Modifier.height(BlockGap))
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(Modifier.padding(horizontal = Gutter)) {
            EditCardsButton(onClick = onEdit)
        }
    }
}

/**
 * 顶部三格读数：陪伴天数 / 累计里程 / 本月花费。
 *
 * 三格等宽、之间只有一条 1dp 竖线 —— 用它代替三张独立小卡，
 * 整页因此少掉两条描边和两个圆角，「一块表盘」比「三块瓷砖」更像仪表。
 */
@Composable
private fun DashStats(days: Int, km: Double?, monthCost: Double) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(shape)
            .background(C.panelBg)
            .border(1.dp, C.border, shape)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(
            value = if (days > 0) "$days" else "--",
            unit = "天",
            label = "陪伴",
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatCell(
            value = km?.let { formatKm(it) } ?: "--",
            unit = "km",
            label = "累计里程",
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatCell(
            value = "¥" + CostStats.money(monthCost),
            unit = null,
            label = "本月用车",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(
    value: String,
    unit: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = C.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit != null) {
                Text(
                    " $unit",
                    color = C.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = C.textSecondary, fontSize = 10.5.sp, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(LocalAppColors.current.border),
    )
}

/** 仪表盘里的进度条：比普通进度条粗一档，用主题色到浅色的横向渐变收尾。 */
@Composable
private fun DashBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val C = LocalAppColors.current
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(C.barTrack),
    ) {
        if (f > 0.01f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(C.accent.copy(alpha = 0.62f), C.accent),
                        ),
                    ),
            )
        }
    }
}

/**
 * 目标：一条横向进度条。
 *
 * 百分数是这一块的主数字（24sp），进度条紧贴它下面 —— 视线从上到下走一趟，
 * 就知道「还剩多少」。勋章压在底部，作为「陪伴」的注脚，不单独占一块。
 */
@Composable
private fun DashGoal(
    progress: GoalProgress?,
    targetYears: Int,
    unlockedIds: Set<String>,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    if (progress == null) {
        Box(Modifier.padding(horizontal = Gutter)) {
            JournalEmptyCard(
                badge = "定",
                title = "设定用车目标",
                desc = "写下打算陪它多久，进度自动累计",
                onClick = onClick,
            )
        }
        return
    }

    Box(Modifier.padding(horizontal = Gutter)) {
        JournalCard(padding = 16.dp, onClick = onClick) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$targetYears 年之约",
                        color = C.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "已陪伴 ${progress.daysOwned} / ${progress.targetDays} 天",
                        color = C.textSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${progress.percent}%",
                    color = C.accent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))
            DashBar(fraction = progress.barFraction)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (progress.reached) {
                        "目标已达成，继续相伴"
                    } else {
                        val eta = progress.etaDate
                            ?.let { " · 预计 ${com.leapcontrol.app.journal.JournalDates.toLocalDate(it).year} 年" }
                            .orEmpty()
                        "还差 ${progress.remainingDays} 天$eta"
                    },
                    color = C.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "详情 ›",
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(C.border),
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 概览只放 5 枚，而且**每组先挑一枚**：108 枚按组分块排列，
                // 直接取前几个已解锁的会全是「陪伴」那几枚，看起来像同一枚换了数字。
                val preview = buildList {
                    MedalGroup.entries.forEach { g ->
                        Medals.of(g).firstOrNull { unlockedIds.contains(it.id) }?.let { add(it) }
                    }
                    if (size < 5) {
                        addAll(Medals.ALL.filterNot { unlockedIds.contains(it.id) }.take(5 - size))
                    }
                }.take(5)
                preview.forEach { medal ->
                    MedalBadge(
                        medal = medal,
                        unlocked = unlockedIds.contains(medal.id),
                        size = 22.dp,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "勋章 ${unlockedIds.size} / ${Medals.ALL.size}",
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 成本：分类占比条 + 前三个分类的读数列。
 *
 * 总额已经在顶部三格里了，这里不再重复一遍，只回答「钱花在哪」：
 * 一条堆叠占比条，下面三个「分类 + 金额」，最后一行日均与笔数。
 */
@Composable
private fun DashCost(summary: CostSummary, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(Modifier.padding(horizontal = Gutter)) {
        JournalCard(padding = 16.dp, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本月用车",
                    color = C.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text("明细 ›", color = C.textSecondary, fontSize = 10.5.sp, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))

            if (summary.empty) {
                Text(
                    "还没有记过一笔",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "说一句「充电 50」就能记上",
                    color = C.accent,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                )
                return@JournalCard
            }

            CategoryStackBar(byCategory = summary.byCategory, total = summary.total)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                summary.byCategory.take(3).forEach { (category, amount) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(categoryColor(category.hue)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            category.label,
                            color = C.textSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "¥${CostStats.money(amount)}",
                            color = C.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 这张卡的标题是「本月用车」，日均只能按**本月已经过去的天数**摊。
                // 用提车以来的 1022 天去除本月支出，11 月 5 号那天 200 元会被摊成
                // 0.20 元/天，看着像这个月压根没花钱
                val monthAvg = summary.averageOf(CostRange.MONTH)
                Text(
                    // 「本月 5 天」把分母摊开写：不然「日均 40」会让人以为是按提车天数算的
                    "日均 ¥${CostStats.moneyFine(monthAvg.value)} · 本月 ${monthAvg.divisor} 天",
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                summary.deltaOf(CostRange.MONTH)?.let { delta ->
                    val (text, color) = when {
                        delta < -0.5 -> "较上月省 ¥${CostStats.money(-delta)}" to C.accent
                        delta > 0.5 -> "较上月多 ¥${CostStats.money(delta)}" to C.textSecondary
                        else -> "与上月持平" to C.textSecondary
                    }
                    Text(text, color = color, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 足迹：标题一行 + 铺到屏幕左右边缘的地图。
 *
 * 地图不套圆角、不进卡片：整幅横向铺满，上下各一条发丝线收边。
 * 这是整页唯一「出血」的块，看起来像仪表盘上嵌了一块屏幕。
 */
@Composable
private fun DashFootprint(data: JournalData, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "足迹",
                color = C.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${data.cityCount} 座 · ${data.provinceCount} 省",
                color = C.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            // 这一块不用 JournalCard（地图要出血），点击本该由下面的地图承载；
            // 但右上角的「查看 ›」在标题行里，离地图还隔着 10dp 间距，
            // 手指按在字上事件落不到地图那层，所以它必须自己挂一次点击。
            // padding 写在 clickable 之后 = 触摸区往外扩；只扩左边和上下，
            // 右边不留内边距，字才能继续贴着 Gutter 这条线（跟其他卡片的「明细 ›」对齐）。
            Text(
                if (data.checkins.isEmpty()) "去打卡 ›" else "查看 ›",
                color = C.accent,
                fontSize = 10.5.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClick)
                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(C.panelBg),
                contentAlignment = Alignment.Center,
            ) {
                if (data.checkins.isEmpty()) {
                    Text(
                        "还没有点亮任何城市，去打卡试试",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                } else {
                    FootprintMap(
                        checkins = data.checkins,
                        wishes = data.wishes,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        compact = true,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
        }
    }
}

/**
 * 保险 / 保养：一块面板里的紧凑行。
 *
 * 一行一件事：左边两个字的名目、中间一句副信息、右边「数字 + 单位」，
 * 数字的颜色就是状态（正常主题色、临期橙、过期红），不用再挂一个小胶囊。
 */
@Composable
private fun DashReminderPanel(
    ids: List<JournalCardId>,
    data: JournalData,
    currentKm: Double?,
    onInsurance: () -> Unit,
    onMaintenance: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(shape)
            .background(C.panelBg)
            .border(1.dp, C.border, shape),
    ) {
        ids.forEachIndexed { index, id ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(C.border),
                )
            }
            when (id) {
                JournalCardId.INSURANCE -> DashInsuranceRow(
                    plan = data.insurance,
                    onClick = onInsurance,
                )

                JournalCardId.MAINTENANCE -> DashMaintenanceRow(
                    plan = data.maintenance,
                    currentKm = currentKm,
                    onClick = onMaintenance,
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun DashInsuranceRow(plan: InsurancePlan?, onClick: () -> Unit) {
    val C = LocalAppColors.current
    if (plan == null) {
        DashReminderShell(
            label = "保险",
            subtitle = "记下购买时间，到期前提醒你",
            value = null,
            unit = null,
            valueColor = C.textSecondary,
            onClick = onClick,
        )
        return
    }
    val head = insuranceHeadline(plan)
    DashReminderShell(
        label = "保险",
        subtitle = head.subtitle,
        value = head.value,
        unit = head.unit,
        valueColor = reminderColor(head.expired, head.soon),
        onClick = onClick,
    )
}

@Composable
private fun DashMaintenanceRow(plan: MaintenancePlan?, currentKm: Double?, onClick: () -> Unit) {
    val C = LocalAppColors.current
    if (plan == null) {
        DashReminderShell(
            label = "保养",
            subtitle = "填上次保养的时间与里程",
            value = null,
            unit = null,
            valueColor = C.textSecondary,
            onClick = onClick,
        )
        return
    }
    val head = maintenanceHeadline(plan, currentKm)
    DashReminderShell(
        label = "保养",
        subtitle = head.subtitle,
        value = head.value,
        unit = head.unit,
        valueColor = reminderColor(head.expired, head.soon),
        onClick = onClick,
    )
}

@Composable
private fun DashReminderShell(
    label: String,
    subtitle: String,
    value: String?,
    unit: String?,
    valueColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = C.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            subtitle,
            color = C.textSecondary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value == null) {
            Spacer(Modifier.width(8.dp))
            Text("去设置 ›", color = C.accent, fontSize = 10.5.sp, maxLines = 1)
        } else {
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = valueColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (unit != null) {
                    Text(
                        " $unit",
                        color = C.textSecondary,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
            Text("›", color = C.textSecondary, fontSize = 13.sp)
        }
    }
}
