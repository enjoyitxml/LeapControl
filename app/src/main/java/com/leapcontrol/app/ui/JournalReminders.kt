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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostStats
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

/** 保养提醒卡。 */
@Composable
fun MaintenanceCard(
    plan: MaintenancePlan?,
    currentKm: Double?,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current

    if (plan == null) {
        JournalCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = CostCategory.SERVICE, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("添加保养提醒", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "填上次保养的时间与里程，到期前在这里提醒",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Text("去设置", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    val days = plan.remainingDays()
    val km = plan.remainingKm(currentKm)
    // 取「更紧急」的那个维度做整卡状态：时间先到就按时间，里程先到就按里程
    val expired = (days != null && days < 0) || (km != null && km < 0)
    val soon = !expired && ((days != null && days <= DUE_SOON_DAYS) || (km != null && km <= DUE_SOON_KM))
    val statusColor = when {
        expired -> DangerRed
        soon -> WarnOrange
        else -> C.accent
    }

    JournalCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(category = CostCategory.SERVICE, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("保养提醒", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            when {
                                expired -> "已过期"
                                soon -> "即将到期"
                                else -> "正常"
                            },
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "上次 ${JournalDates.shortLabel(plan.lastDate)} · ${formatKm(plan.lastKm)} km",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 剩余量：时间与里程都是「还剩多少」，过期时写「已超」
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            days?.let { d ->
                RemainingStat(
                    value = if (d >= 0) "$d" else "${-d}",
                    unit = if (d >= 0) "天" else "天前已到期",
                    label = if (d >= 0) "距下次保养" else "已超期",
                    color = statusColor,
                )
            }
            km?.let { k ->
                RemainingStat(
                    value = formatKm(kotlin.math.abs(k)),
                    unit = if (k >= 0) "km" else "km 已超",
                    label = if (k >= 0) "距下次保养" else "已超里程",
                    color = statusColor,
                )
            }
            if (days == null && km == null) {
                // 设了保养计划但车况总里程拿不到：只报时间那一侧，别显示一个假的公里数
                Text("拿不到当前总里程，先按时间提醒", color = C.textSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("设定：")
                if (plan.nextDate() != null) {
                    append("每 ${plan.months} 个月")
                }
                if (plan.nextDate() != null && plan.nextKm() != null) append(" / ")
                if (plan.nextKm() != null) append("每 ${formatKm(plan.km)} km")
            },
            color = C.textSecondary,
            fontSize = 10.sp,
        )
    }
}

/** 保险续费卡。 */
@Composable
fun InsuranceCard(plan: InsurancePlan?, onClick: () -> Unit) {
    val C = LocalAppColors.current

    if (plan == null) {
        JournalCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = CostCategory.INSURANCE, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("添加保险续费提醒", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "记下购买时间，到期前提醒你续费",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Text("去设置", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    val days = plan.remainingDays()
    val expired = days < 0
    val soon = !expired && days <= DUE_SOON_DAYS
    val statusColor = when {
        expired -> DangerRed
        soon -> WarnOrange
        else -> C.accent
    }

    JournalCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(category = CostCategory.INSURANCE, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.company.ifBlank { "保险续费" },
                        color = C.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            when {
                                expired -> "已过期"
                                soon -> "即将到期"
                                else -> "保障中"
                            },
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "${JournalDates.shortLabel(plan.startDate)} 起保 · 保期 ${plan.months} 个月" +
                        if (plan.premium > 0) " · 保费 ¥${CostStats.money(plan.premium)}" else "",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RemainingStat(
                value = if (days >= 0) "$days" else "${-days}",
                unit = if (days >= 0) "天" else "天前已到期",
                label = if (days >= 0) "距到期" else "已过期",
                color = statusColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    expired -> "请尽快续保"
                    soon -> "该续费了"
                    else -> "到期日 ${JournalDates.shortLabel(plan.expireDate())}"
                },
                color = if (expired || soon) statusColor else C.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (expired || soon) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun RemainingStat(value: String, unit: String, label: String, color: Color) {
    val C = LocalAppColors.current
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(2.dp))
            Text(unit, color = color, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, color = C.textSecondary, fontSize = 10.sp)
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
