package com.leapcontrol.app.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CarGoal
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.GoalProgress
import com.leapcontrol.app.journal.JournalDates
import com.leapcontrol.app.journal.Medal
import com.leapcontrol.app.journal.MedalGroup
import com.leapcontrol.app.journal.MedalStats
import com.leapcontrol.app.journal.MedalTier
import com.leapcontrol.app.journal.Medals
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 用车目标页。
 *
 * 第一次进来是「填表」：打算陪它多久、落地多少钱、哪天提的车；
 * 填完就变成「进度」：一条环、三格数字、一面勋章墙。
 *
 * 提车日期默认用服务端给的提车天数反推（`ownershipDays`），所以多数人只需要选年限、
 * 填价格两项。落价只用来算「每天摊掉多少」，不参与任何统计上报。
 */
@Composable
fun JournalGoalScreen(runtime: AppRuntime, onBack: () -> Unit) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val journal = runtime.journal
    val data by journal.data
    val goal = data.goal

    val energy by runtime.mileageEnergyFlow.collectAsState()
    // 订阅车况，别读 statusFlow.value：那样拿的是组合期的一次快照，车况变了也不会重组
    val status by runtime.statusFlow.collectAsState()
    val vehicleKm = com.leapcontrol.app.OwnershipStats.currentKm(
        status?.totalMileage,
        energy?.totalMileageKm,
    )

    var editing by remember { mutableStateOf(goal == null) }

    val defaultDelivery = remember(data.ownershipDays) {
        data.ownershipDays?.let { GoalProgress.deliveryFromOwnershipDays(it) }
            ?: JournalDates.todayStart()
    }
    var yearsText by remember(goal) { mutableStateOf((goal?.targetYears ?: 10).toString()) }
    var priceText by remember(goal) {
        mutableStateOf(goal?.landingPrice?.takeIf { it > 0.0 }?.let { plainNumber(it) } ?: "")
    }
    var delivery by remember(goal, defaultDelivery) {
        mutableStateOf(goal?.deliveryDate ?: defaultDelivery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        JournalTopBar(
            title = "用车目标",
            onBack = onBack,
            trailing = {
                if (goal != null) {
                    Text(
                        if (editing) "取消" else "调整",
                        color = C.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { editing = !editing }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))

        if (editing || goal == null) {
            GoalForm(
                yearsText = yearsText,
                onYearsChange = { yearsText = it },
                priceText = priceText,
                onPriceChange = { priceText = it },
                delivery = delivery,
                ownershipDays = data.ownershipDays,
                onPickDate = {
                    showDatePicker(context, delivery) { picked -> delivery = picked }
                },
                onSave = {
                    val years = yearsText.trim().toIntOrNull()
                    val price = priceText.trim().replace(",", "").toDoubleOrNull()
                    when {
                        years == null || years !in 1..40 -> toast(context, "用车时长填 1~40 年")
                        price == null || price <= 0.0 -> toast(context, "落地价填个数字，比如 189800")
                        delivery > JournalDates.todayStart() -> toast(context, "提车日期不能晚于今天")
                        else -> {
                            journal.setGoal(CarGoal(years, price, delivery))
                            editing = false
                            toast(context, "目标已设好，慢慢来")
                        }
                    }
                },
            )
        } else {
            val progress = GoalProgress.of(goal)
            GoalProgressView(
                goal = goal,
                progress = progress,
                stats = MedalStats.of(data, progress.daysOwned, progress.fraction, vehicleKm),
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalForm(
    yearsText: String,
    onYearsChange: (String) -> Unit,
    priceText: String,
    onPriceChange: (String) -> Unit,
    delivery: Long,
    ownershipDays: Int?,
    onPickDate: () -> Unit,
    onSave: () -> Unit,
) {
    val C = LocalAppColors.current
    val years = yearsText.trim().toIntOrNull()
    val price = priceText.trim().replace(",", "").toDoubleOrNull()

    SettingsSection(title = "用车时长", desc = "希望爱车陪你多久") {
        SettingsCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CarGoal.SUGGESTED_YEARS.forEach { y ->
                    JournalPill(
                        text = "$y 年",
                        selected = years == y,
                        onClick = { onYearsChange(y.toString()) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            JournalInput(
                value = yearsText,
                onValueChange = { onYearsChange(it.filter { ch -> ch.isDigit() }.take(2)) },
                placeholder = "也可以自己填，1~40 年",
                suffix = "年",
                numeric = true,
            )
        }
    }

    Spacer(Modifier.height(18.dp))

    SettingsSection(title = "购车落地价", desc = "含购置税、保险等全部费用") {
        SettingsCard {
            JournalInput(
                value = priceText,
                onValueChange = { onPriceChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                placeholder = "例如 189800",
                suffix = "元",
                numeric = true,
            )
            if (price != null && price > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "≈ ${String.format(Locale.CHINA, "%.2f", price / 10_000.0)} 万元",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    SettingsSection(title = "提车日期", desc = "进度从这一天开始算") {
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, C.border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onPickDate)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    JournalDates.shortLabel(delivery),
                    color = C.textPrimary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("修改", color = C.accent, fontSize = 12.sp)
            }
            if (ownershipDays != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "已从车辆数据里读到提车天数 $ownershipDays 天，日期是据此推算的，可手动修正",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(22.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.accent)
            .clickable(onClick = onSave)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("保存目标", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 进度视图（刻度版）。
 *
 * 原来是一圈 168dp 的圆环 + 三格数字 + 两张价格小卡：圆环只能表达「占比」，
 * 而用户真正想知道的是「我在这十年里的哪一段」。所以换成一把刻度尺 ——
 * 每小格一季度、每 4 格一年，指针落在当前位置，尺子本身就是信息，不是装饰。
 *
 * 价格从两张独立小卡降级成主卡底部的一格读数：它不该和进度抢同一个视觉层级。
 */
@Composable
private fun GoalProgressView(
    goal: CarGoal,
    progress: GoalProgress,
    stats: MedalStats,
) {
    val C = LocalAppColors.current
    val unlockedIds = Medals.unlockedIds(stats)
    val next = Medals.next(stats)

    // ---- 主卡：读数 + 十年刻度 ----
    JournalCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("已陪伴", color = C.textSecondary, fontSize = 11.sp)
            Text("目标 ${goal.targetDays} 天", color = C.textSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${progress.daysOwned}",
                color = C.textPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.2).sp,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                "天",
                color = C.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .alignByBaseline(),
            )
        }
        Spacer(Modifier.height(6.dp))
        GoalRuler(fraction = progress.barFraction, years = goal.targetYears)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                JournalDates.shortLabel(goal.deliveryDate),
                color = C.textSecondary,
                fontSize = 10.sp,
            )
            Text("${goal.targetYears} 年为期", color = C.textSecondary, fontSize = 10.sp)
            Text(
                progress.etaDate?.let { JournalDates.shortLabel(it) } ?: "已达成年限",
                color = C.textSecondary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(15.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(C.border),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            GoalReading(
                label = if (progress.reached) "目标" else "还差",
                value = if (progress.reached) "已达成" else "${progress.remainingDays}",
                unit = if (progress.reached) "" else "天",
                valueColor = if (progress.reached) C.accent else C.textPrimary,
                modifier = Modifier.weight(1f),
            )
            GoalReading(
                label = "每天摊掉",
                value = CostStats.moneyFine(progress.pricePerDay),
                unit = "元",
                modifier = Modifier.weight(1f),
            )
            GoalReading(
                label = "落地价",
                value = String.format(Locale.CHINA, "%.1f", goal.landingPrice / 10000.0),
                unit = "万",
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // ---- 下一枚勋章 ----
    if (next != null) {
        JournalCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedalBadge(medal = next, unlocked = false, size = 42.dp)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "下一枚 · ${next.title}",
                        color = C.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOfNotNull(
                            next.gapText(stats),
                            next.group.label + "组",
                            tierLabel(next.tier),
                        ).joinToString(" · "),
                        color = C.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                val ratio = next.ratio(stats) ?: 0f
                Text(
                    "${(ratio * 100f).roundToInt()}%",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(11.dp))
            // 用这枚章自己的档位色做进度条：青铜 -> 白银 -> 黄金 -> 铂金，
            // 「还差多少」和「这枚有多贵重」合进同一根线里。
            JournalProgressLine(
                fraction = next.ratio(stats) ?: 0f,
                height = 3.dp,
                color = medalPalette(next.tier, C.isDark).plateDeep,
                track = C.barTrack,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    // ---- 勋章墙 ----
    MedalWall(unlockedIds = unlockedIds, stats = stats)
}

/**
 * 刻度尺的分格数。
 *
 * 每 4 格一年是固定语义（一格一个季度）；20 年以上格子会挤成一条灰带，退化成 40 格。
 */
internal fun goalRulerDivisions(years: Int): Int {
    val quarters = years.coerceIn(1, 40) * 4
    return if (quarters <= 80) quarters else 40
}

/**
 * 十年刻度尺。
 *
 * 单位是「格」不是像素：每 4 格一年，格子数 = 目标年数 × 4，
 * 所以 3 年目标的后半段不会因为尺子太长而失真。已走过的格子转成主题色，
 * 指针位置同时给出百分比读数 —— 尺子量位置，百分比量程度。
 */
@Composable
private fun GoalRuler(
    fraction: Float,
    years: Int,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val f = fraction.coerceIn(0f, 1f)
    val label = "${(f * 100f).roundToInt()}%"
    val labelStyle = TextStyle(
        color = C.accent,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val divisions = goalRulerDivisions(years)
    val majorEvery = if (divisions % 4 == 0) 4 else 5

    Canvas(
        modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        val w = size.width
        val rulerH = 38.dp.toPx()
        val top = size.height - rulerH
        val s = rulerH / 40f            // 设计稿以 40 单位为高度，这里等比缩放
        val base = top + 33f * s
        val x = f * w

        // 已走过的区间：一层极淡的底色，让「到这儿了」在扫一眼时就成立
        if (x > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(C.accent.copy(alpha = 0.11f), C.accent.copy(alpha = 0.03f)),
                    startX = 0f,
                    endX = x,
                ),
                topLeft = Offset(0f, base - 15f * s),
                size = Size(x, 15f * s),
            )
        }

        // 基线
        drawLine(
            color = C.border,
            start = Offset(0f, base),
            end = Offset(w, base),
            strokeWidth = 1.dp.toPx(),
        )

        for (i in 0..divisions) {
            val tx = w * i / divisions
            val major = i % majorEvery == 0
            val th = (if (major) 12f else 5.5f) * s
            val on = tx <= x + 0.5f
            drawLine(
                color = if (on) {
                    C.accent.copy(alpha = 0.72f)
                } else {
                    C.textSecondary.copy(alpha = 0.38f)
                },
                start = Offset(tx, base),
                end = Offset(tx, base - th),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 已用进度：压在基线上，3dp 圆头
        if (x > 0f) {
            drawLine(
                color = C.accent,
                start = Offset(0f, base),
                end = Offset(x, base),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 指针 + 游标：一圈白底套主题色描边，压在瓦片/卡面上也看得清
        drawLine(
            color = C.accent,
            start = Offset(x, top + 1f * s),
            end = Offset(x, base + 4f * s),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val dot = 3.4f * s
        drawCircle(C.panelBg, dot, Offset(x, base))
        drawCircle(C.accent, dot, Offset(x, base), style = Stroke(2.dp.toPx()))

        // 百分比读数：贴着指针，靠边时夹住不让它出界
        val laid = measurer.measure(AnnotatedString(label), labelStyle)
        val lx = (x - laid.size.width / 2f).coerceIn(0f, (w - laid.size.width).coerceAtLeast(0f))
        drawText(laid, topLeft = Offset(lx, 0f))
    }
}

/** 主卡底部的一格读数：小标签 + 大数字 + 单位。 */
@Composable
private fun GoalReading(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalAppColors.current.textPrimary,
) {
    val C = LocalAppColors.current
    Column(modifier) {
        Text(label, color = C.textSecondary, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = valueColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                unit,
                color = C.textSecondary,
                fontSize = 9.5.sp,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .alignByBaseline(),
            )
        }
    }
}

/**
 * 勋章墙。
 *
 * 108 枚不能平铺：一条灰点长龙既看不到进度、也找不到目标。所以按 8 组做横向筛选，
 * 一次只显示一组（最多 18 枚，4 列 5 行），组内顺序就是解锁顺序 ——
 * 用户永远看到「我已经到这儿了，下一枚在这儿」。
 */
@Composable
private fun MedalWall(unlockedIds: Set<String>, stats: MedalStats) {
    val C = LocalAppColors.current
    var group by remember { mutableStateOf(MedalGroup.DAYS) }
    var detail by remember { mutableStateOf<Medal?>(null) }

    val list = Medals.of(group)
    val got = list.count { unlockedIds.contains(it.id) }

    JournalCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("勋章墙", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${unlockedIds.size} / ${Medals.ALL.size}",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "已解锁 ${(unlockedIds.size * 100f / Medals.ALL.size).roundToInt()}%",
                color = C.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(12.dp))

        // 分组：横向可滚（8 组一屏放不下，硬挤会变成指甲盖大的字）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedalGroup.entries.forEach { g ->
                val gGot = Medals.unlockedCount(g, stats)
                JournalPill(
                    text = "${g.label} $gGot/${Medals.of(g).size}",
                    selected = group == g,
                    onClick = { group = g },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        JournalProgressLine(
            fraction = if (list.isEmpty()) 0f else got.toFloat() / list.size,
            height = 5.dp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Medals.nextIn(group, stats)?.let { "本组下一枚：${it.title} · ${it.gapText(stats).orEmpty()}" }
                    ?: "这一组已经全部拿到",
                color = C.textSecondary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(14.dp))

        list.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { medal ->
                    val on = unlockedIds.contains(medal.id)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { detail = medal }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MedalBadge(medal = medal, unlocked = on, size = 46.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            medal.title,
                            color = if (on) C.textPrimary else C.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (on) "已获得" else "未解锁",
                            color = if (on) C.accent else C.textSecondary.copy(alpha = 0.55f),
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                }
                // 最后一行不足 4 个时补空，避免勋章被拉宽
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    detail?.let { medal ->
        MedalDetailDialog(
            medal = medal,
            unlocked = unlockedIds.contains(medal.id),
            stats = stats,
            onDismiss = { detail = null },
        )
    }
}

/** 勋章详情：点开才知道「这枚是干什么的、我还差多少」。 */
@Composable
private fun MedalDetailDialog(
    medal: Medal,
    unlocked: Boolean,
    stats: MedalStats,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(C.dialogBg)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MedalBadge(medal = medal, unlocked = unlocked, size = 76.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                medal.title,
                color = C.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${medal.group.label} · ${tierLabel(medal.tier)}",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                medal.desc,
                color = C.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (unlocked) C.accent.copy(alpha = 0.12f) else C.chipBg)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unlocked) "已获得" else (medal.gapText(stats) ?: "暂无数据"),
                    color = if (unlocked) C.accent else C.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "知道了",
                color = C.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
    }
}

private fun tierLabel(tier: MedalTier): String = when (tier) {
    MedalTier.BRONZE -> "青铜"
    MedalTier.SILVER -> "白银"
    MedalTier.GOLD -> "黄金"
    MedalTier.PLATINUM -> "铂金"
}

/** 通用输入框（数字/文本），外观与二级页的其它控件一致。 */
@Composable
fun JournalInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
) {
    val C = LocalAppColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = C.textSecondary, fontSize = 14.sp) },
        suffix = suffix?.let { { Text(it, color = C.textSecondary, fontSize = 13.sp) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = C.textPrimary, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent,
            unfocusedBorderColor = C.border,
            cursorColor = C.accent,
            focusedContainerColor = C.panelBg,
            unfocusedContainerColor = C.panelBg,
        ),
    )
}

/** 原生日期选择器（M3 的 DatePickerDialog 在深色主题下容易跟本 App 的配色打架）。 */
fun showDatePicker(context: android.content.Context, initial: Long, onPick: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    val dialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val millis = LocalDate.of(year, month + 1, day)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            onPick(millis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    )
    dialog.datePicker.maxDate = System.currentTimeMillis()
    dialog.show()
}

fun toast(context: android.content.Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private fun plainNumber(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else value.toString()
