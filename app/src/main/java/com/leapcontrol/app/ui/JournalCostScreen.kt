package com.leapcontrol.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostEntry
import com.leapcontrol.app.journal.CostParser
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.JournalDates
import java.util.Locale

/**
 * 用车成本页。
 *
 * 三块内容：**钱花在哪**（分类占比）、**每月多少**（6 个月柱状图）、**每一笔**（流水）。
 * 记账入口只有一个原则：能一句话说完的就别填表 —— 输入框里写「昨天停车 15」，
 * 分类、金额、日期全部自动认出来；认错的地方点一下就能改。
 */
@Composable
fun JournalCostScreen(runtime: AppRuntime, onBack: () -> Unit) {
    val C = LocalAppColors.current
    val journal = runtime.journal
    val data by journal.data
    val summary = CostStats.of(data, data.goal)

    var showAdd by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(CostScope.MONTH) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val mainAmount = when (scope) {
        CostScope.MONTH -> summary.thisMonth
        CostScope.YEAR -> summary.thisYear
        CostScope.ALL -> summary.total
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            JournalTopBar(
                title = "用车成本",
                onBack = onBack,
                trailing = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(C.accent)
                            .clickable { showAdd = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("记一笔", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            // ---- 总额 + 日均 ----
            JournalCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CostScope.entries.forEach { item ->
                        JournalPill(
                            text = item.label,
                            selected = scope == item,
                            onClick = { scope = item },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "¥${CostStats.money(mainAmount)}",
                        color = C.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        scope.desc,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                JournalProgressLine(
                    fraction = if (summary.total > 0) (mainAmount / summary.total).toFloat() else 0f,
                    color = C.accent,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    JournalStat(
                        value = "¥${CostStats.moneyFine(summary.perDay)}",
                        label = if (summary.daysFromDelivery) "日均（${summary.days} 天）" else "日均（记账 ${summary.days} 天）",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = if (summary.entryCount == 0) "0" else "${summary.entryCount}",
                        label = "累计笔数",
                        modifier = Modifier.weight(1f),
                    )
                    val delta = summary.monthDelta
                    JournalStat(
                        value = delta?.let { (if (it >= 0) "+" else "-") + CostStats.money(kotlin.math.abs(it)) }
                            ?: "--",
                        label = "较上月",
                        modifier = Modifier.weight(1f),
                        valueColor = when {
                            delta == null -> C.textSecondary
                            delta > 0 -> androidx.compose.ui.graphics.Color(0xFFD98324)
                            else -> androidx.compose.ui.graphics.Color(0xFF1FA45B)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 近 6 个月 ----
            JournalCard {
                Text("近 6 个月", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                val max = summary.months.maxOfOrNull { it.amount } ?: 0.0
                JournalBars(
                    values = summary.months.map { if (max > 0) (it.amount / max).toFloat() else 0f },
                    labels = summary.months.map { it.label },
                    valueTexts = summary.months.map { if (it.amount > 0) CostStats.money(it.amount) else "" },
                    barHeight = 58.dp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- 分类 ----
            JournalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("花在哪", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("按全部记录统计", color = C.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (summary.byCategory.isEmpty()) {
                    JournalEmpty("还没有记录。说一句「充电 50」，这里就会出现第一笔。")
                } else {
                    CategoryStackBar(summary.byCategory, summary.total)
                    Spacer(Modifier.height(14.dp))
                    summary.byCategory.forEach { (cat, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryDot(short = cat.short, color = categoryColor(cat.hue), size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(cat.label, color = C.textPrimary, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(amount / summary.total * 100).toInt()}%",
                                color = C.textSecondary,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "¥${CostStats.money(amount)}",
                                color = C.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 流水 ----
            JournalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("流水", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (data.costs.size > 40) {
                        Text("最近 40 笔", color = C.textSecondary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (data.costs.isEmpty()) {
                    JournalEmpty("记一笔试试：点右上角「记一笔」，或者直接说一句。")
                } else {
                    data.costs.take(40).forEach { entry ->
                        CostRow(
                            entry = entry,
                            onDelete = { confirmDelete = entry.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        if (showAdd) {
            AddCostSheet(
                onDismiss = { showAdd = false },
                onSave = { category, amount, date, note ->
                    journal.addCost(category, amount, date, note)
                    showAdd = false
                },
            )
        }

        val deleting = confirmDelete
        if (deleting != null) {
            JournalConfirm(
                title = "删掉这一笔？",
                desc = "删除后统计会立刻更新，不能撤销。",
                confirmText = "删除",
                onConfirm = {
                    journal.deleteCost(deleting)
                    confirmDelete = null
                },
                onDismiss = { confirmDelete = null },
            )
        }
    }
}

private enum class CostScope(val label: String, val desc: String) {
    MONTH("本月", "本月支出"),
    YEAR("今年", "今年支出"),
    ALL("全部", "提车以来"),
}

@Composable
private fun CostRow(entry: CostEntry, onDelete: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(short = entry.category.short, color = categoryColor(entry.category.hue), size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.note.ifBlank { entry.category.label },
                color = C.textPrimary,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${JournalDates.shortLabel(entry.date)} · ${entry.category.label}",
                color = C.textSecondary,
                fontSize = 10.sp,
            )
        }
        Text(
            "¥${CostStats.money(entry.amount)}",
            color = C.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "删",
            color = C.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/**
 * 「记一笔」弹层。
 *
 * 输入 → 实时解析 → 可改 → 保存。语音与手输走同一个解析器，
 * 所以「充电 50」无论是说的还是打的，结果都一样。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCostSheet(
    onDismiss: () -> Unit,
    onSave: (CostCategory, Double, Long, String) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    val parsed = remember(input) { CostParser.parse(input) }

    var category by remember { mutableStateOf<CostCategory?>(null) }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(JournalDates.todayStart()) }

    // 输入变化 → 重新解析并灌进表单。
    // 放在 LaunchedEffect 里而不是组合期直接写：组合期改状态会多走一轮重组，
    // 输入框每敲一个字都触发，手感会发涩。
    LaunchedEffect(input) {
        val hit = CostParser.parse(input)
        if (hit != null) {
            category = hit.category
            amountText = plainAmount(hit.amount)
            date = hit.date
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                input = text
                if (CostParser.parse(text) == null) {
                    toast(context, "没听清金额，再补一个数字就行")
                }
            }
        }
    }

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
                // 点弹层内部不触发外层的「关闭」：用空点击消费掉事件
                .consumeClicks()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(C.dialogBg)
                // 键盘弹出后内容会顶出屏幕：让弹层自身可滚，按钮永远够得到
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("记一笔", color = C.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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

            // 一句话输入 + 语音
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    JournalInput(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "充电 50、昨天停车 15、保养 1200",
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(C.accent.copy(alpha = 0.15f))
                        .border(1.dp, C.accent.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "说出花费，比如「充电五十元」")
                            }
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure { toast(context, "这台手机没有语音识别，直接打字吧") }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("语音", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // 分类
            Text("分类", color = C.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CostCategory.ordered.forEach { cat ->
                    JournalPill(
                        text = cat.label,
                        selected = category == cat,
                        onClick = { category = cat },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    JournalInput(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        placeholder = "金额",
                        suffix = "元",
                        numeric = true,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.panelBg)
                        .border(1.dp, C.border, RoundedCornerShape(14.dp))
                        .clickable {
                            showDatePicker(context, date) { date = it }
                        }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                ) {
                    Text(JournalDates.shortLabel(date), color = C.textPrimary, fontSize = 13.sp)
                }
            }

            if (input.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        parsed == null -> "没认出金额：可以直接在上面填数字"
                        parsed.hasExplicitDate ->
                            "已识别：${parsed.category.label} · ${JournalDates.shortLabel(parsed.date)}"
                        else -> "已识别：${parsed.category.label} · 今天"
                    },
                    color = if (parsed == null) C.textSecondary else C.accent,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.accent)
                    .clickable {
                        val amount = amountText.trim().toDoubleOrNull()
                        when {
                            amount == null || amount <= 0.0 -> toast(context, "金额填一个大于 0 的数")
                            else -> onSave(
                                category ?: CostCategory.OTHER,
                                amount,
                                date,
                                // 备注只存去掉金额的原文（「充电」），不把整句「充电 50」塞进流水
                                parsed?.note.orEmpty(),
                            )
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("保存", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 通用二次确认（删除这类不可撤销的动作才用）。 */
@Composable
fun JournalConfirm(
    title: String,
    desc: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIALOG_SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(C.dialogBg)
                .consumeClicks()
                .padding(20.dp),
        ) {
            Text(title, color = C.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(desc, color = C.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.chipBg)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("再想想", color = C.textPrimary, fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.accent)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(confirmText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun plainAmount(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.CHINA, "%.2f", value)

/**
 * 吃掉点击事件，不让它穿透到背后的遮罩。
 *
 * `clickable(enabled = false)` 不消费事件，弹层内部的空白处一点就会把弹层关掉；
 * 这里用一个「有交互源、无涟漪、空回调」的可点区域，既消费事件又没有视觉副作用。
 */
fun Modifier.consumeClicks(): Modifier = this.then(
    Modifier.clickable(
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
        indication = null,
        onClick = {},
    ),
)
