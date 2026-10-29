package com.leapcontrol.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
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
import com.leapcontrol.app.journal.CostRange
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.JournalDates
import java.util.Locale
import kotlin.math.abs

/**
 * 用车成本页。
 *
 * 四块内容：**这一期花了多少**（日 / 周 / 月 / 年 / 全部切换）、**每期走势**、
 * **钱花在哪**（占比环 + 分类排行）、**每一笔**（流水）。
 *
 * 记账入口只有一个原则：能一句话说完的就别填表 —— 输入框里写「昨天停车 15」，
 * 分类、金额、日期全部自动认出来；认错的地方点一下就能改。
 */
@Composable
fun JournalCostScreen(runtime: AppRuntime, onBack: () -> Unit) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val journal = runtime.journal
    val data by journal.data
    val summary = CostStats.of(data, data.goal)

    var showAdd by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(CostRange.MONTH) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    // 正在编辑的那一笔：null = 不是编辑态。用整条 entry 而不是 id，
    // 弹层里就能直接拿到初始值，删改过程中列表被别处改动也不至于读到空。
    var editing by remember { mutableStateOf<CostEntry?>(null) }

    val amount = summary.amountOf(range)
    val delta = summary.deltaOf(range)
    val byCategory = remember(data, range) { CostStats.byCategoryOf(data, range) }
    val trend = remember(data, range) { CostStats.trendOf(data, range) }
    val trendMax = trend.maxOfOrNull { it.amount } ?: 0.0

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
                            .clickable {
                                addPrefill = ""
                                showAdd = true
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("记一笔", color = readableOn(C.accent), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            // ---- 区间 + 总额 + 日均 ----
            JournalCard {
                JournalSegmented(
                    options = CostRange.entries.map { it.label },
                    selectedIndex = range.ordinal,
                    onSelect = { range = CostRange.entries[it] },
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "¥${CostStats.money(amount)}",
                        color = C.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        range.desc,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                // 相对「全部」的占比：看得出现在这一期在总开销里的分量
                JournalProgressLine(
                    fraction = if (summary.total > 0) (amount / summary.total).toFloat() else 0f,
                    color = C.accent,
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    JournalStat(
                        value = "¥${CostStats.moneyFine(summary.perDay)}",
                        label = if (summary.daysFromDelivery) "日均（${summary.days} 天）" else "日均（记账 ${summary.days} 天）",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = "${summary.entryCount}",
                        label = "累计笔数",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = delta?.let { (if (it >= 0) "+" else "-") + CostStats.money(abs(it)) } ?: "--",
                        label = summary.deltaLabel(range),
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

            // ---- 趋势：粒度跟着区间走 ----
            JournalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(range.trendTitle, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (range) {
                            CostRange.DAY -> "每天"
                            CostRange.WEEK -> "每周"
                            CostRange.MONTH -> "每月"
                            else -> "每年"
                        },
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "最高 ¥${CostStats.money(trendMax)}",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (trendMax <= 0.0) {
                    JournalEmpty("这一期还没有支出。记一笔之后，这里就会出现走势。")
                } else {
                    JournalLineChart(
                        values = trend.map { (it.amount / trendMax).toFloat() },
                        labels = trend.map { it.label },
                        height = 132.dp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 支出分析：占比环 + 前三名 ----
            JournalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("支出分析", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(range.desc, color = C.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(14.dp))
                if (byCategory.isEmpty()) {
                    JournalEmpty("这一期还没有支出。点右上角「记一笔」，或者按住首页那颗球说一句。")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JournalDonut(
                            slices = byCategory,
                            total = amount,
                            centerTitle = "¥${CostStats.money(amount)}",
                            centerSub = range.label,
                            modifier = Modifier.size(124.dp),
                        )
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            byCategory.take(3).forEach { (category, money) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryIcon(category = category, size = 26.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            category.label,
                                            color = C.textPrimary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        JournalProgressLine(
                                            fraction = if (amount > 0) (money / amount).toFloat() else 0f,
                                            height = 4.dp,
                                            color = categoryColor(category.hue),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${(money / amount * 100).toInt()}%",
                                        color = C.textSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 分类明细 ----
            JournalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("花在哪", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${byCategory.size} 类", color = C.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (byCategory.isEmpty()) {
                    JournalEmpty("还没有记录。说一句「充电 50」，这里就会出现第一笔。")
                } else {
                    Spacer(Modifier.height(4.dp))
                    CategoryStackBar(byCategory = byCategory, total = amount)
                    Spacer(Modifier.height(10.dp))
                    byCategory.forEach { (category, money) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(category = category, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(category.label, color = C.textPrimary, fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "¥${CostStats.money(money)}",
                                        color = C.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${(money / amount * 100).toInt()}%",
                                        color = C.textSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                JournalProgressLine(
                                    fraction = if (amount > 0) (money / amount).toFloat() else 0f,
                                    height = 4.dp,
                                    color = categoryColor(category.hue),
                                )
                            }
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
                    JournalEmpty("记一笔试试：点右上角「记一笔」，或者按住零记首页底部那颗球说一句。")
                } else {
                    data.costs.take(40).forEach { entry ->
                        CostRow(
                            entry = entry,
                            onEdit = { editing = entry },
                            onDelete = { confirmDelete = entry.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        if (showAdd) {
            CostSheet(
                initial = addPrefill,
                onDismiss = { showAdd = false },
                onVoice = {
                    showAdd = false
                    showVoice = true
                },
                onSave = { category, amountValue, date, note ->
                    journal.addCost(category, amountValue, date, note)
                    showAdd = false
                    toast(context, "已记 ${category.label} ¥${CostStats.money(amountValue)}")
                },
            )
        }

        // 编辑已有的一笔：金额、分类、日期、备注都能改，改完立刻回写并刷新统计
        val editingEntry = editing
        if (editingEntry != null) {
            CostSheet(
                initial = "",
                editing = editingEntry,
                onDismiss = { editing = null },
                onSave = { category, amountValue, date, note ->
                    journal.updateCost(
                        editingEntry.copy(
                            category = category,
                            amount = Math.round(amountValue * 100) / 100.0,
                            date = JournalDates.startOfDay(date),
                            note = note,
                        ),
                    )
                    editing = null
                    toast(context, "已改成 ${category.label} ¥${CostStats.money(amountValue)}")
                },
            )
        }

        if (showVoice) {
            JournalVoiceSheet(
                onlineKeys = runtime.baiduAsrKeys(),

                onSaveKeys = { api, secret -> runtime.setBaiduAsrKeys(api, secret) },
                onCancel = { showVoice = false },
                onTypeInstead = { reason ->
                    showVoice = false
                    addPrefill = ""
                    if (reason.isNotBlank()) toast(context, reason)
                    showAdd = true
                },
                onResult = { text ->
                    showVoice = false
                    val parsed = CostParser.parse(text)
                    if (parsed == null) {
                        addPrefill = text
                        showAdd = true
                        toast(context, "没听清金额，补一个数字就行")
                    } else {
                        journal.addCost(parsed.category, parsed.amount, parsed.date, parsed.note)
                        toast(
                            context,
                            "已记 ${parsed.category.label} ¥${CostStats.money(parsed.amount)}",
                        )
                    }
                },
            )
        }

        val deleting = confirmDelete
        if (deleting != null) {
            // 确认框里把那一条的具体内容念一遍：列表里行挨行，「删掉这一笔」很容易删错行
            val target = data.costs.firstOrNull { it.id == deleting }
            JournalConfirm(
                title = "删除这一笔？",
                desc = buildString {
                    if (target != null) {
                        append("${target.category.label} ¥${CostStats.money(target.amount)}")
                        append(" · ${JournalDates.shortLabel(target.date)}")
                        if (target.note.isNotBlank()) append(" · ${target.note}")
                        append("\n\n")
                    }
                    append("删除后统计会立刻更新，且无法撤销。")
                },
                confirmText = "确认删除",
                onConfirm = {
                    journal.deleteCost(deleting)
                    confirmDelete = null
                    toast(context, "已删除")
                },
                onDismiss = { confirmDelete = null },
            )
        }
    }
}

@Composable
private fun CostRow(
    entry: CostEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行可点＝改这一笔：比再塞一个「编辑」按钮更省地方，也不容易点错
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category = entry.category, size = 32.dp)
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
        // 删除键：一个带底的圆形垃圾桶，而不是光秃秃一个「删」字 ——
        // 圆形底既是可点区域（30dp）也是「这是按钮」的提示，眼睛扫过去就能定位
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(C.chipBg)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除这一笔",
                tint = C.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 「记一笔 / 改这一笔」弹层。
 *
 * 输入 → 实时解析 → 可改 → 保存。语音走的是同一个解析器（Siri 式浮层收音），
 * 所以「充电 50」无论是说的还是打的，结果都一样。
 *
 * [editing] 非空时进入编辑态：表单预填那一条的现值，标题与按钮文案跟着换，
 * 语音按钮隐藏（已经有一条记录在手，再念一句只会把金额盖掉，不如直接改数字）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostSheet(
    initial: String,
    editing: CostEntry? = null,
    onDismiss: () -> Unit,
    onVoice: (() -> Unit)? = null,
    onSave: (CostCategory, Double, Long, String) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val isEdit = editing != null

    var input by remember { mutableStateOf(initial) }
    val parsed = remember(input) { CostParser.parse(input) }

    var category by remember { mutableStateOf(editing?.category) }
    var amountText by remember { mutableStateOf(editing?.let { plainAmount(it.amount) }.orEmpty()) }
    var date by remember { mutableStateOf(editing?.date ?: JournalDates.todayStart()) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    // 用户手动改过备注之后，就不要再被一句话解析的结果覆盖
    var noteEdited by remember { mutableStateOf(false) }

    // 输入变化 → 重新解析并灌进表单。
    // 放在 LaunchedEffect 里而不是组合期直接写：组合期改状态会多走一轮重组，
    // 输入框每敲一个字都触发，手感会发涩。
    LaunchedEffect(input) {
        val hit = CostParser.parse(input)
        if (hit != null) {
            category = hit.category
            amountText = plainAmount(hit.amount)
            date = hit.date
            if (!noteEdited) note = hit.note
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
                Text(
                    if (isEdit) "改这一笔" else "记一笔",
                    color = C.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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

            // 一句话输入 + 语音（编辑态不显示语音：见函数注释）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    JournalInput(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = if (isEdit) "也可以重新说一句，自动改填" else "充电 50、昨天停车 15、保养 1200",
                    )
                }
                if (onVoice != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(C.accent.copy(alpha = 0.15f))
                            .border(1.dp, C.accent.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onVoice),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("语音", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
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

            Spacer(Modifier.height(10.dp))
            JournalInput(
                value = note,
                onValueChange = {
                    note = it
                    noteEdited = true
                },
                placeholder = "备注（可选）：家附近快充",
            )

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
                        val amountValue = amountText.trim().toDoubleOrNull()
                        when {
                            amountValue == null || amountValue <= 0.0 -> toast(context, "金额填一个大于 0 的数")
                            else -> onSave(
                                category ?: CostCategory.OTHER,
                                amountValue,
                                date,
                                // 备注只存去掉金额的原文（「充电」），不把整句「充电 50」塞进流水
                                note.trim(),
                            )
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isEdit) "保存修改" else "保存",
                    color = readableOn(C.accent),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    Text(
                        confirmText,
                        color = readableOn(C.accent),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
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
