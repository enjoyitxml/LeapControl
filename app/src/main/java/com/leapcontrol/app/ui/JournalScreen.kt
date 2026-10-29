package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.OwnershipStats
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.CostSummary
import com.leapcontrol.app.journal.GoalProgress
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.JournalCardId
import com.leapcontrol.app.journal.JournalCardLayout
import com.leapcontrol.app.journal.JournalRow
import com.leapcontrol.app.journal.MedalGroup
import com.leapcontrol.app.journal.MedalStats
import com.leapcontrol.app.journal.Medals
import java.util.Locale

/** 零记的四个页面：概览 + 三个功能页。 */
enum class JournalPage {
    GOAL,
    COST,
    CHECKIN,
}

/**
 * 「零记」首页（底部导航第二格）。
 *
 * 由几张可自定义的卡片组成：**还要一起多久**（用车目标）、**该花钱了**（保险 / 保养提醒）、
 * **一起花了多少**（用车成本）、**一起去过哪儿**（城市打卡）。卡片顺序与显隐都能改
 * （最下面的「编辑」），存在 prefs 里、按卡片名当协议。
 *
 * 没设目标也不拦人进页面：目标卡会留成一张空卡，随时能补 ——
 * 一进来就被表单挡住，对「只想先记一笔」的人来说是纯粹的阻力。
 */
@Composable
fun JournalScreen(runtime: AppRuntime) {
    val C = LocalAppColors.current
    val journal = runtime.journal
    val data by journal.data
    val order by journal.cardOrder
    // 里程勋章看的是**车辆总里程**，不是本地那几天的累计：
    // 本地 daily 表刚开始只有近 7 天，用它会让人均「千公里」勋章卡住不动。
    val energy by runtime.mileageEnergyFlow.collectAsState()
    val vehicleKm = energy?.totalMileageKm
        ?: OwnershipStats.kmFromText(runtime.statusFlow.value?.totalMileage)

    var page by remember { mutableStateOf<JournalPage?>(null) }
    var loaded by remember { mutableStateOf(false) }
    // 两张提醒卡的编辑弹层
    var showMaintenance by remember { mutableStateOf(false) }
    var showInsurance by remember { mutableStateOf(false) }
    // 卡片区的编辑态（长按拖动排序 / 删除 / 添加）
    var editingCards by remember { mutableStateOf(false) }
    // 拖拽到屏幕边缘时要能驱动同一个滚动容器，所以状态得提到这里
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        journal.ensureLoaded()
        loaded = true

        // 顺手把「提车天数 + 近 7 日里程 + 总里程」同步进来：
        // 这些数据零记自己不产生，全靠控车链路已有的接口，调一次就够（15 分钟缓存）。
        runCatching {
            val summary = runtime.refreshMileageEnergy()
            if (summary != null) {
                journal.setOwnershipDays(summary.ownershipDays)
                journal.mergeRecentDays(summary.recentDays)
            }
            journal.recordOdometer(summary?.totalMileageKm ?: currentOdometer(runtime))
        }
    }

    // 系统返回（返回键 / 左右滑手势）：先退回零记概览，别直接退出 App
    BackHandler(enabled = page != null) { page = null }
    // 编辑态里的返回：先退出编辑，而不是切到别的页面或退出 App
    BackHandler(enabled = page == null && editingCards) { editingCards = false }

    val target = page
    if (loaded && target != null) {
        when (target) {
            JournalPage.GOAL -> JournalGoalScreen(
                runtime = runtime,
                onBack = { page = null },
            )

            JournalPage.COST -> JournalCostScreen(
                runtime = runtime,
                onBack = { page = null },
            )

            JournalPage.CHECKIN -> JournalCheckinScreen(
                runtime = runtime,
                onBack = { page = null },
            )
        }
        return
    }

    val goal = data.goal
    val progress = goal?.let { GoalProgress.of(it) }
    val costSummary = CostStats.of(data, goal)
    val stats = MedalStats.of(
        data = data,
        days = progress?.daysOwned ?: (data.ownershipDays ?: 0),
        fraction = progress?.fraction ?: 0f,
        km = vehicleKm,
    )
    val unlocked = Medals.unlockedIds(stats)
    val totalKm = data.dailyKm.values.fold(0.0) { a, b -> a + b }
    // 卡片顺序 → 排版行：相邻两张半屏卡并成一行（保险左、保养右）
    val rows = remember(order) { JournalCardLayout.rows(order) }

    // 外层 Box 只为了那枚悬浮球：内容照旧滚动，球固定在底部正中
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("零记", color = C.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(
                    goal?.let { "和${runtime.displayName()}一起的第 ${progress?.daysOwned ?: 1} 天" }
                        ?: "记录和爱车的长期主义",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- 卡片区：顺序 / 显隐都可改，最下面是「编辑」 ----
            ReorderableCardArea(
                cards = rows,
                editing = editingCards,
                onEditingChange = { editingCards = it },
                onCardsChange = { newRows ->
                    journal.setCardOrder(JournalCardLayout.flatten(newRows))
                },
                scrollState = scrollState,
                hidden = JournalCardLayout.hidden(order).map { JournalRow(listOf(it)) },
                titleOf = { row -> row.cards.joinToString(" + ") { it.title } },
                // 一行里可能摆着两张半屏卡，删除键得各归各的，交给下面自己画
                showDelete = false,
                overlay = { row ->
                    RowDeleteButtons(
                        row = row,
                        onRemove = { id -> journal.setCardOrder(order.filterNot { it == id }) },
                        modifier = Modifier.matchParentSize(),
                    )
                },
                onAdd = { row ->
                    val added = row.cards.first()
                    journal.setCardOrder(JournalCardLayout.withAdded(order, added))
                },
            ) { row ->
                val second = row.cards.getOrNull(1)
                if (second != null) {
                    Row(Modifier.fillMaxWidth()) {
                        JournalCardById(
                            id = row.cards[0],
                            data = data,
                            progress = progress,
                            unlockedIds = unlocked,
                            costSummary = costSummary,
                            totalKm = totalKm,
                            vehicleKm = vehicleKm?.takeIf { it > 0 },
                            style = ReminderStyle.HALF,
                            modifier = Modifier.weight(1f),
                            onGoal = { page = JournalPage.GOAL },
                            onCost = { page = JournalPage.COST },
                            onCheckin = { page = JournalPage.CHECKIN },
                            onInsurance = { showInsurance = true },
                            onMaintenance = { showMaintenance = true },
                        )
                        Spacer(Modifier.width(10.dp))
                        JournalCardById(
                            id = second,
                            data = data,
                            progress = progress,
                            unlockedIds = unlocked,
                            costSummary = costSummary,
                            totalKm = totalKm,
                            vehicleKm = vehicleKm?.takeIf { it > 0 },
                            style = ReminderStyle.HALF,
                            modifier = Modifier.weight(1f),
                            onGoal = { page = JournalPage.GOAL },
                            onCost = { page = JournalPage.COST },
                            onCheckin = { page = JournalPage.CHECKIN },
                            onInsurance = { showInsurance = true },
                            onMaintenance = { showMaintenance = true },
                        )
                    }
                } else {
                    JournalCardById(
                        id = row.cards[0],
                        data = data,
                        progress = progress,
                        unlockedIds = unlocked,
                        costSummary = costSummary,
                        totalKm = totalKm,
                        vehicleKm = vehicleKm?.takeIf { it > 0 },
                        // 半屏卡落单时撑成整行，不在旁边留一块空白
                        style = ReminderStyle.WIDE,
                        modifier = Modifier.fillMaxWidth(),
                        onGoal = { page = JournalPage.GOAL },
                        onCost = { page = JournalPage.COST },
                        onCheckin = { page = JournalPage.CHECKIN },
                        onInsurance = { showInsurance = true },
                        onMaintenance = { showMaintenance = true },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "零记的数据只存在这台手机上，不上传、不统计。",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(OrbReservedHeight))
        }

        JournalOrb(
            runtime = runtime,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )

        if (showMaintenance) {
            MaintenanceSheet(
                plan = data.maintenance,
                currentKm = vehicleKm?.takeIf { it > 0 },
                onDismiss = { showMaintenance = false },
                onSave = { plan ->
                    journal.setMaintenance(plan)
                    showMaintenance = false
                    toast(context, if (plan == null) "已关闭保养提醒" else "保养提醒已保存")
                },
            )
        }

        if (showInsurance) {
            InsuranceSheet(
                plan = data.insurance,
                onDismiss = { showInsurance = false },
                onSave = { plan ->
                    journal.setInsurance(plan)
                    showInsurance = false
                    toast(context, if (plan == null) "已关闭保险提醒" else "保险提醒已保存")
                },
            )
        }
    }
}

/** 给右下角悬浮的灵感球留白：球 104 高，少一点会把页脚压在球底下。 */
private val OrbReservedHeight = 134.dp

/** 车况里的总里程（拿不到就 null，不让 recordOdometer 收到 0 把基线拉低）。 */
private fun currentOdometer(runtime: AppRuntime): Double? =
    OwnershipStats.kmFromText(runtime.statusFlow.value?.totalMileage)

/**
 * 按 id 渲染零记的一张卡。
 *
 * 保险 / 保养由调用方给 [ReminderStyle]：两张并排时是 HALF，落单时撑成 WIDE；
 * 另外三张本来就是整行卡，样式参数对它们没影响。
 */
@Composable
private fun JournalCardById(
    id: JournalCardId,
    data: JournalData,
    progress: GoalProgress?,
    unlockedIds: Set<String>,
    costSummary: CostSummary,
    totalKm: Double,
    vehicleKm: Double?,
    style: ReminderStyle,
    modifier: Modifier,
    onGoal: () -> Unit,
    onCost: () -> Unit,
    onCheckin: () -> Unit,
    onInsurance: () -> Unit,
    onMaintenance: () -> Unit,
) {
    when (id) {
        JournalCardId.GOAL ->
            GoalOverviewCard(
                data = data,
                progress = progress,
                unlockedIds = unlockedIds,
                modifier = modifier,
                onClick = onGoal,
            )

        JournalCardId.COST ->
            CostOverviewCard(summary = costSummary, modifier = modifier, onClick = onCost)

        JournalCardId.FOOTPRINT ->
            FootprintOverviewCard(data = data, totalKm = totalKm, modifier = modifier, onClick = onCheckin)

        JournalCardId.INSURANCE ->
            InsuranceCard(
                plan = data.insurance,
                modifier = modifier,
                style = style,
                onClick = onInsurance,
            )

        JournalCardId.MAINTENANCE ->
            MaintenanceCard(
                plan = data.maintenance,
                currentKm = vehicleKm,
                modifier = modifier,
                style = style,
                onClick = onMaintenance,
            )
    }
}

/**
 * 编辑态里每一张卡的删除键。
 *
 * 不能用卡片区自带的那个（它只能钉在整行右上角）：一行并排两张半屏卡时，
 * 左卡的删除键会跑到右卡头上。这里按行内卡片数量均分，各归各位。
 */
@Composable
private fun RowDeleteButtons(
    row: JournalRow,
    onRemove: (JournalCardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        row.cards.forEachIndexed { index, id ->
            if (index > 0) Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onRemove(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "删除${id.title}",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalOverviewCard(
    data: JournalData,
    progress: GoalProgress?,
    unlockedIds: Set<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val goal = data.goal
    if (goal == null) {
        // 没设目标就留一张空卡，和保养 / 保险的引导卡同一个样子：
        // 不逼着人一进零记就填表单，想记笔账的人可以直接记账。
        JournalEmptyCard(
            badge = "定",
            title = "设定用车目标",
            desc = "写下打算陪它多久，进度自动累计",
            modifier = modifier,
            onClick = onClick,
        )
        return
    }

    JournalCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                JournalRing(
                    fraction = progress?.barFraction ?: 0f,
                    modifier = Modifier.size(94.dp),
                    stroke = 9.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${progress?.percent ?: 0}%",
                        color = C.textPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("目标进度", color = C.textSecondary, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${goal.targetYears} 年之约",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "已陪伴 ${progress?.daysOwned ?: 0} 天 / ${goal.targetDays} 天",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (progress?.reached == true) "目标已达成，继续相伴"
                    else "还差 ${progress?.remainingDays ?: 0} 天" +
                        (progress?.etaDate?.let { " · 预计 ${yearOf(it)} 年达成" } ?: ""),
                    color = C.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 概览只放 6 枚，而且**每组先挑一枚**：108 枚按组分块排列，
                    // 直接取前 6 个已解锁的会全是「陪伴」那几枚，看起来像同一枚勋章换了数字。
                    // 每组各来一枚，最后用没拿到的补齐 —— 既显得有广度，也留了钩子。
                    val preview = buildList {
                        MedalGroup.entries.forEach { g ->
                            Medals.of(g).firstOrNull { unlockedIds.contains(it.id) }?.let { add(it) }
                        }
                        if (size < 6) {
                            addAll(Medals.ALL.filterNot { unlockedIds.contains(it.id) }.take(6 - size))
                        }
                    }.take(6)
                    preview.forEach { medal ->
                        MedalBadge(
                            medal = medal,
                            unlocked = unlockedIds.contains(medal.id),
                            size = 26.dp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "已解锁 ${unlockedIds.size} / ${Medals.ALL.size}",
                        color = C.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CostOverviewCard(
    summary: CostSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    JournalCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本月用车", color = C.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("明细", color = C.accent, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "¥${CostStats.money(summary.thisMonth)}",
                color = C.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (summary.empty) "还没有记过一笔"
                else "日均 ¥${CostStats.moneyFine(summary.perDay)} · 近半年 ¥${CostStats.money(summary.months.sumOf { it.amount })}",
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (summary.empty) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JournalPill(
                    text = "记第一笔",
                    selected = true,
                    onClick = onClick,
                )
                Spacer(Modifier.width(10.dp))
                Text("说一句「充电 50」就行", color = C.textSecondary, fontSize = 11.sp)
            }
        } else {
            // 分类堆叠条：一眼看出钱花在哪，占比不写数字也不影响判断
            CategoryStackBar(byCategory = summary.byCategory, total = summary.total)
            Spacer(Modifier.height(10.dp))
            Row {
                summary.byCategory.take(3).forEachIndexed { index, (cat, amount) ->
                    if (index > 0) Spacer(Modifier.width(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(category = cat, size = 20.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${cat.label} ¥${CostStats.money(amount)}",
                            color = C.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FootprintOverviewCard(
    data: JournalData,
    totalKm: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    JournalCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("足迹", color = C.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("打卡", color = C.accent, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${data.cityCount}",
                color = C.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                " 座城市 · ${data.provinceCount} 个省份",
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "累计 ${formatKm(totalKm)} km",
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        FootprintMap(
            checkins = data.checkins,
            wishes = data.wishes,
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp),
            compact = true,
        )
        if (data.wishes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "心愿单 ${data.wishCount} 座 · " +
                    data.wishes.take(3).joinToString("、") { it.city } +
                    if (data.wishes.size > 3) " …" else "",
                color = C.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/** 分类占比堆叠条（横向）。 */
@Composable
fun CategoryStackBar(
    byCategory: List<Pair<com.leapcontrol.app.journal.CostCategory, Double>>,
    total: Double,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val barHeight = 8.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(CircleShape)
            .background(C.barTrack),
    ) {
        if (total <= 0.0) return@Row
        byCategory.forEach { (cat, amount) ->
            val f = (amount / total).toFloat().coerceIn(0f, 1f)
            if (f > 0.004f) {
                Box(
                    Modifier
                        .fillMaxWidth(f)
                        .height(barHeight)
                        .background(categoryColor(cat.hue)),
                )
            }
        }
    }
}

/** 分类色：按 hue 生成一个有辨识度、又不刺眼的颜色。 */
@Composable
fun categoryColor(hue: Float): androidx.compose.ui.graphics.Color {
    val dark = LocalAppColors.current.isDark
    return if (dark) {
        androidx.compose.ui.graphics.Color.hsl(hue * 360f, 0.52f, 0.66f)
    } else {
        androidx.compose.ui.graphics.Color.hsl(hue * 360f, 0.58f, 0.46f)
    }
}

private fun yearOf(millis: Long): Int =
    com.leapcontrol.app.journal.JournalDates.toLocalDate(millis).year

/** 里程文案：整数带千分位，小数保留一位。 */
fun formatKm(km: Double): String {
    if (!km.isFinite() || km < 0.0) return "0"
    val rounded = Math.round(km * 10) / 10.0
    return if (rounded == Math.floor(rounded)) {
        String.format(Locale.CHINA, "%,d", rounded.toLong())
    } else {
        String.format(Locale.CHINA, "%,.1f", rounded)
    }
}
