package com.leapcontrol.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.OwnershipStats
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.CostSummary
import com.leapcontrol.app.journal.GoalProgress
import com.leapcontrol.app.journal.JournalData
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
 * 三段构成一个人和车的关系：**还要一起多久**（用车目标）、**一起花了多少**（用车成本）、
 * **一起去过哪儿**（城市打卡）。三张卡都可点进详情页；一个数都没有的时候也不会空着，
 * 每张卡都会说明「这里能记什么」。
 *
 * 首次进入且没设过目标时，直接跳到目标页 —— 这是「长期主义」的起点：
 * 没有目标就没有进度，后面的勋章与日均成本都无从谈起。
 */
@Composable
fun JournalScreen(runtime: AppRuntime) {
    val C = LocalAppColors.current
    val journal = runtime.journal
    val data by journal.data
    // 里程勋章看的是**车辆总里程**，不是本地那几天的累计：
    // 本地 daily 表刚开始只有近 7 天，用它会让人均「千公里」勋章卡住不动。
    val energy by runtime.mileageEnergyFlow.collectAsState()
    val vehicleKm = energy?.totalMileageKm
        ?: OwnershipStats.kmFromText(runtime.statusFlow.value?.totalMileage)

    var page by remember { mutableStateOf<JournalPage?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loadedData = journal.ensureLoaded()
        loaded = true
        // 第一次进来就要求「设一个目标」：没有目标，进度和勋章都无从谈起。
        // 允许返回（有人只想记账），返回后概览会留一张引导卡，随时能补。
        if (loadedData.goal == null && !journal.onboardingShown()) {
            journal.markOnboardingShown()
            page = JournalPage.GOAL
        }

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

    // 外层 Box 只为了那枚悬浮球：内容照旧滚动，球固定在底部正中
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
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

            GoalOverviewCard(
                data = data,
                progress = progress,
                unlockedIds = unlocked,
                onClick = { page = JournalPage.GOAL },
            )

            Spacer(Modifier.height(12.dp))

            CostOverviewCard(
                summary = costSummary,
                onClick = { page = JournalPage.COST },
            )

            Spacer(Modifier.height(12.dp))

            FootprintOverviewCard(
                data = data,
                totalKm = data.dailyKm.values.fold(0.0) { a, b -> a + b },
                onClick = { page = JournalPage.CHECKIN },
            )

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
                .padding(bottom = 8.dp),
        )
    }
}

/** 给悬浮的灵感球留白：球 104 + 提示条 + 间距，少一点会把页脚压在球底下。 */
private val OrbReservedHeight = 150.dp

/** 车况里的总里程（拿不到就 null，不让 recordOdometer 收到 0 把基线拉低）。 */
private fun currentOdometer(runtime: AppRuntime): Double? =
    OwnershipStats.kmFromText(runtime.statusFlow.value?.totalMileage)

@Composable
private fun GoalOverviewCard(
    data: JournalData,
    progress: GoalProgress?,
    unlockedIds: Set<String>,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val goal = data.goal
    if (goal == null) {
        JournalCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(C.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("定", color = C.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("定一个用车目标", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "写下打算陪它多久，剩下的日子会被算成进度",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Text("去设置", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    JournalCard(onClick = onClick) {
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
private fun CostOverviewCard(summary: CostSummary, onClick: () -> Unit) {
    val C = LocalAppColors.current
    JournalCard(onClick = onClick) {
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
private fun FootprintOverviewCard(data: JournalData, totalKm: Double, onClick: () -> Unit) {
    val C = LocalAppColors.current
    JournalCard(onClick = onClick) {
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
