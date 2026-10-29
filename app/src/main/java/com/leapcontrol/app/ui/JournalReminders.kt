package com.leapcontrol.app.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.InsurancePlan
import com.leapcontrol.app.journal.JournalDates
import com.leapcontrol.app.journal.MaintenancePlan

/**
 * 零记顶部的两张「该花钱了」卡片：保养到期、保险续费。
 *
 * 这两件事的共同点是**不记就会忘**：保养拖几千公里伤车，保险忘了续上路就是裸奔。
 * 所以它们不藏在某个详情页里，直接摆在零记首页最上面；临期（≤30 天 / ≤500 公里）
 * 整卡转为警示色，不看数字也能察觉。
 */

/** 临期阈值：时间 30 天、里程 500 公里。 */
private const val DUE_SOON_DAYS = 30
private const val DUE_SOON_KM = 500.0

private val WarnOrange = Color(0xFFD98324)
private val DangerRed = Color(0xFFE2503C)

// 排版：整行横向 —— 左边角标 + 标题 + 一行说明，右边主数字。
//
// 早先在零记里是两张并排的半屏卡，靠一个固定 126dp 的高度把底边对齐，
// 结果「还剩 10 000 km」这种稍长的副信息直接被卡片下沿切掉。半屏宽度下
// 要么砍信息、要么加高到两张卡不齐，都不成立；干脆整行铺开，和首页统一。

/** 状态色：过期红、临期橙、其余用主题色。 */
@Composable
private fun reminderColor(expired: Boolean, soon: Boolean): Color {
    val C = LocalAppColors.current
    return when {
        expired -> DangerRed
        soon -> WarnOrange
        else -> C.accent
    }
}

/** 右上角的胶囊小标（已过期 / 即将到期 / 正常）。 */
@Composable
private fun ReminderChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 保养提醒卡。
 *
 * 主数字只报一个维度：**时间优先**（天数比公里数更容易感知），
 * 只有「按里程」模式才报公里；另一个维度写在标题下面那行小字里。
 */
@Composable
fun MaintenanceCard(
    plan: MaintenancePlan?,
    currentKm: Double?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (plan == null) {
        ReminderEmpty(
            category = CostCategory.SERVICE,
            title = "添加保养提醒",
            // 一行放得下才不至于把引导卡撑成两行（11sp 下 284dp 约合 25 个汉字）
            desc = "填上次保养的时间与里程",
            modifier = modifier,
            onClick = onClick,
        )
        return
    }

    val days = plan.remainingDays()
    val km = plan.remainingKm(currentKm)
    // 取「更紧急」的那个维度做整卡状态：时间先到就按时间，里程先到就按里程
    val expired = (days != null && days < 0) || (km != null && km < 0)
    val soon = !expired && ((days != null && days <= DUE_SOON_DAYS) || (km != null && km <= DUE_SOON_KM))
    val status = reminderColor(expired, soon)
    val chip = when {
        expired -> "已过期"
        soon -> "即将到期"
        else -> "正常"
    }

    val value: String
    val unit: String
    when {
        days != null -> {
            value = if (days >= 0) "$days" else "${-days}"
            unit = if (days >= 0) "天" else "天前已到期"
        }
        km != null -> {
            value = formatKm(kotlin.math.abs(km))
            unit = if (km >= 0) "km" else "km 已超"
        }
        else -> {
            value = "--"
            unit = ""
        }
    }
    val label = when {
        days == null && km == null -> "拿不到当前总里程"
        expired -> "已超期"
        else -> "距下次保养"
    }
    // 副信息：主数字报的是天数，这里补「还剩多少公里」—— 保养最该被看见的两个维度；
    // 没设里程时就退回「设定周期」或「上次保养」。都控制在一行内，超了走省略号。
    val subtitle = when {
        km != null -> if (km >= 0) "还剩 ${formatKm(km)} km" else "已超 ${formatKm(kotlin.math.abs(km))} km"
        plan.nextDate() != null -> "每 ${plan.months} 个月一次"
        plan.nextKm() != null -> "每 ${formatKm(plan.km)} km 一次"
        else -> "上次 ${JournalDates.shortLabel(plan.lastDate)} · ${formatKm(plan.lastKm)} km"
    }

    ReminderBody(
        category = CostCategory.SERVICE,
        title = "保养提醒",
        chip = chip,
        status = status,
        value = value,
        unit = unit,
        label = label,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
    )
}

/** 保险续费卡。 */
@Composable
fun InsuranceCard(
    plan: InsurancePlan?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (plan == null) {
        ReminderEmpty(
            category = CostCategory.INSURANCE,
            title = "添加保险续费",
            desc = "记下购买时间，到期前提醒你续费",
            modifier = modifier,
            onClick = onClick,
        )
        return
    }

    val days = plan.remainingDays()
    val expired = days < 0
    val soon = !expired && days <= DUE_SOON_DAYS
    val status = reminderColor(expired, soon)
    val chip = when {
        expired -> "已过期"
        soon -> "即将到期"
        else -> "保障中"
    }
    val expiry = JournalDates.shortLabel(plan.expireDate())
    // 副信息「公司 · 到期日」；保费留给设置弹层，塞进来会把这一行挤到省略号后面。
    // 右边那列已经写着「距到期」，日期前不再重复「到期」两个字 ——
    // 省下的 24dp 让「平安车险 · 2026年12月31日」在窄屏上也能整行放下。
    val subtitle = if (plan.company.isNotBlank()) "${plan.company} · $expiry" else "到期 $expiry"

    ReminderBody(
        category = CostCategory.INSURANCE,
        title = "保险续费",
        chip = chip,
        status = status,
        value = if (days >= 0) "$days" else "${-days}",
        unit = if (days >= 0) "天" else "天前已到期",
        label = if (days >= 0) "距到期" else "已过期",
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
    )
}

/** 卡片外壳：整行横向 —— 左边角标 + 标题 + 一行说明，右边主数字。 */
@Composable
private fun ReminderBody(
    category: CostCategory,
    title: String,
    chip: String,
    status: Color,
    value: String,
    unit: String,
    label: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current

    JournalCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(category = category, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(7.dp))
                    ReminderChip(chip, status)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, color = C.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(2.dp))
                    Text(unit, color = status, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(label, color = C.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

/** 还没设置时的引导卡。 */
@Composable
private fun ReminderEmpty(
    category: CostCategory,
    title: String,
    desc: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current

    JournalCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(category = category, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(desc, color = C.textSecondary, fontSize = 11.sp)
            }
            Text("去设置", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * 整行的「空卡」：一个角标 + 标题 + 一行说明 + 「去设置」。
 *
 * 保养、保险、用车目标都用这一套观感 —— 没填的时候它们本来就该长得像，
 * 用户扫一眼就知道「这几张都是待填的表」，而不是三张风格各异的引导卡。
 */
@Composable
fun JournalEmptyCard(
    badge: String,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    JournalCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(C.accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(desc, color = C.textSecondary, fontSize = 11.sp)
            }
            Text("去设置", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------- 编辑弹层 ----------------

/** 保养计划设置弹层。 */
@Composable
fun MaintenanceSheet(
    plan: MaintenancePlan?,
    currentKm: Double?,
    onDismiss: () -> Unit,
    onSave: (MaintenancePlan?) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current

    var mode by remember { mutableStateOf(plan?.mode ?: MaintenancePlan.Mode.BOTH) }
    var lastDate by remember {
        mutableStateOf(plan?.lastDate ?: JournalDates.addMonths(JournalDates.todayStart(), -6))
    }
    var lastKmText by remember {
        mutableStateOf(
            plan?.lastKm?.takeIf { it > 0 }?.let { formatKm(it).replace(",", "") }
                ?: currentKm?.let { formatKm(it).replace(",", "") }
                ?: "",
        )
    }
    var monthsText by remember { mutableStateOf((plan?.months ?: 12).toString()) }
    var kmText by remember {
        mutableStateOf((plan?.km ?: 10_000.0).let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() })
    }

    SheetShell(title = "保养提醒", onDismiss = onDismiss) {
        Text("提醒方式", color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        JournalSegmented(
            options = listOf("按时间", "按里程", "都提醒"),
            selectedIndex = mode.ordinal,
            onSelect = { mode = MaintenancePlan.Mode.entries[it] },
        )

        Spacer(Modifier.height(14.dp))
        Text("上次保养", color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.panelBg)
                    .border(1.dp, C.border, RoundedCornerShape(14.dp))
                    .clickable { showDatePicker(context, lastDate) { lastDate = it } }
                    .padding(horizontal = 14.dp, vertical = 15.dp),
            ) {
                Text(JournalDates.shortLabel(lastDate), color = C.textPrimary, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                JournalInput(
                    value = lastKmText,
                    onValueChange = { lastKmText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    placeholder = "当时表显里程",
                    suffix = "km",
                    numeric = true,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("保养周期", color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                JournalInput(
                    value = monthsText,
                    onValueChange = { monthsText = it.filter { ch -> ch.isDigit() }.take(3) },
                    placeholder = "间隔",
                    suffix = "个月",
                    numeric = true,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                JournalInput(
                    value = kmText,
                    onValueChange = { kmText = it.filter { ch -> ch.isDigit() } },
                    placeholder = "间隔",
                    suffix = "km",
                    numeric = true,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "电车常见「一年或 1 万公里」，两个都填就会两个都提醒。",
            color = C.textSecondary,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (plan != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.chipBg)
                        .clickable { onSave(null) }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("关闭提醒", color = C.textSecondary, fontSize = 14.sp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.accent)
                    .clickable {
                        val months = monthsText.toIntOrNull()
                        val km = kmText.toDoubleOrNull()
                        val lastKm = lastKmText.toDoubleOrNull()
                        when {
                            months == null || months !in 1..120 ->
                                toast(context, "间隔月数填 1~120")
                            mode != MaintenancePlan.Mode.TIME && (km == null || km <= 0.0) ->
                                toast(context, "间隔公里数填一个大于 0 的数")
                            mode != MaintenancePlan.Mode.KM && (lastKm == null || lastKm < 0.0) ->
                                toast(context, "填一下上次保养时的表显里程")
                            else -> onSave(
                                MaintenancePlan(
                                    mode = mode,
                                    lastDate = lastDate,
                                    lastKm = lastKm ?: 0.0,
                                    months = months,
                                    km = km ?: 0.0,
                                ),
                            )
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("保存", color = readableOn(C.accent), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 保险计划设置弹层。 */
@Composable
fun InsuranceSheet(
    plan: InsurancePlan?,
    onDismiss: () -> Unit,
    onSave: (InsurancePlan?) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current

    var company by remember { mutableStateOf(plan?.company.orEmpty()) }
    var startDate by remember { mutableStateOf(plan?.startDate ?: JournalDates.todayStart()) }
    var monthsText by remember { mutableStateOf((plan?.months ?: 12).toString()) }
    var premiumText by remember {
        mutableStateOf(
            plan?.premium?.takeIf { it > 0 }?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }
                .orEmpty(),
        )
    }

    SheetShell(title = "保险续费提醒", onDismiss = onDismiss) {
        JournalInput(
            value = company,
            onValueChange = { company = it },
            placeholder = "保险公司（可选）：平安车险",
        )

        Spacer(Modifier.height(14.dp))
        Text("购买 / 起保时间", color = C.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(C.panelBg)
                .border(1.dp, C.border, RoundedCornerShape(14.dp))
                .clickable { showDatePicker(context, startDate) { startDate = it } }
                .padding(horizontal = 14.dp, vertical = 15.dp),
        ) {
            Text(JournalDates.shortLabel(startDate), color = C.textPrimary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                JournalInput(
                    value = monthsText,
                    onValueChange = { monthsText = it.filter { ch -> ch.isDigit() }.take(3) },
                    placeholder = "保期",
                    suffix = "个月",
                    numeric = true,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                JournalInput(
                    value = premiumText,
                    onValueChange = { premiumText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    placeholder = "保费（可选）",
                    suffix = "元",
                    numeric = true,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "到期日按「起保时间 + 保期」自动算，续保后把起保时间改成新的即可。",
            color = C.textSecondary,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (plan != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.chipBg)
                        .clickable { onSave(null) }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("关闭提醒", color = C.textSecondary, fontSize = 14.sp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.accent)
                    .clickable {
                        val months = monthsText.toIntOrNull()
                        when {
                            months == null || months !in 1..120 ->
                                toast(context, "保期填 1~120 个月")
                            else -> onSave(
                                InsurancePlan(
                                    company = company.trim(),
                                    startDate = startDate,
                                    months = months,
                                    premium = premiumText.toDoubleOrNull() ?: 0.0,
                                ),
                            )
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("保存", color = readableOn(C.accent), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 弹层外壳：底部抽屉 + 遮罩 + 可滚动 + 键盘跟随。
 *
 * 与「记一笔」那层保持同一套观感（圆角 26dp、顶部标题 + 取消、点外部关闭）。
 */
@Composable
private fun SheetShell(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIALOG_SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .consumeClicks()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(C.dialogBg)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(6.dp))
        }
    }
}
