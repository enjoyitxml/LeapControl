package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.JournalAutoBackup
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.JournalRecoveryNote
import com.leapcontrol.app.journal.JournalSource
import com.leapcontrol.app.journal.JournalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 设置 →「零记」里的**数据安全**部分。
 *
 * 和 [JournalBackupRows]（手动导出/导入）分开，因为这里处理的是「不想让用户操心」的两件事：
 *  1. **每天自动备份到公共下载目录** —— 卸载重装也还在；
 *  2. **找回历史数据** —— 更新后数据看起来丢了时，把磁盘上残留的那几份捞回来。
 *
 * 第二项是这次「更新后零记全空」的直接补救入口：数据八成还在，只是当前 VIN 的 key
 * 对不上（或者主文件坏了、走的是备份）。列表里会列出主文件、上一版备份、日快照、
 * 旧版一车一文件、写入中断的临时文件，用户选一份合并回来即可，**只增不减**。
 */
@Composable
fun JournalSafetyRows(
    runtime: AppRuntime,
    onMessage: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoBackup by remember { mutableStateOf(JournalAutoBackup.isEnabled(context)) }
    var busy by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<JournalSource>?>(null) }
    var picked by remember { mutableStateOf<JournalSource?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }

    // 进设置时读一次存储状态：读来源异常 / 写失败要在这里就看得见
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { runtime.journal.refreshStatus() } }
    }

    SwitchRow(
        label = "每天自动备份",
        desc = JournalAutoBackup.summary(context),
        checked = autoBackup,
        onCheckedChange = { on ->
            autoBackup = on
            JournalAutoBackup.setEnabled(context, on)
            if (on) {
                scope.launch {
                    val failure = withContext(Dispatchers.IO) {
                        runtime.journal.backupToDownloadsNow().exceptionOrNull()
                    }
                    dialog = if (failure == null) {
                        "已写入 下载/LeapControl，之后每天自动更新一份。"
                    } else {
                        "立即备份失败：${failure.message ?: "无法写入下载目录"}"
                    }
                }
            }
        },
    )

    Spacer(Modifier.height(2.dp))

    ClickRow(
        title = "立即备份到下载目录",
        desc = if (busy) "处理中…" else "写一份完整零记到 下载/LeapControl，卸载重装也不丢",
        icon = Icons.Filled.FolderOpen,
    ) {
        if (busy) return@ClickRow
        busy = true
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runtime.journal.backupToDownloadsNow().exceptionOrNull()
            }
            busy = false
            dialog = if (failure == null) {
                "已写入 下载/LeapControl。这个目录不属于零控，卸载 App 不会删掉它。"
            } else {
                "备份失败：${failure.message ?: "无法写入下载目录"}"
            }
        }
    }

    Spacer(Modifier.height(2.dp))

    ClickRow(
        title = "找回历史数据",
        desc = if (busy) "扫描中…" else "更新后数据不见了？扫描本机残留的零记并合并回来",
        icon = Icons.Filled.Search,
    ) {
        if (busy) return@ClickRow
        busy = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { runtime.journal.sources() } }
            busy = false
            found
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        dialog = "本机没有找到可恢复的零记数据。\n\n" +
                            "如果之前做过导出，可以用上面的「导入数据」选那个文件。"
                    } else {
                        sources = list
                    }
                }
                .onFailure { dialog = "扫描失败：${it.message ?: "读不出本机数据"}" }
        }
    }

    // ---- 可恢复来源列表 ----
    sources?.let { list ->
        AlertDialog(
            onDismissRequest = { if (!busy) sources = null },
            containerColor = C.panelBg,
            title = { Text("找回历史数据", color = C.textPrimary) },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "下面这些是本机还能读出来的零记数据。选一份合并到当前车辆，" +
                            "只新增、不会删掉已经记下的内容。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    list.forEach { source ->
                        val selected = picked?.id == source.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) C.accent.copy(alpha = 0.12f) else C.chipBg)
                                .clickable { picked = source }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                "${source.label} · ${sourceCounts(source.data)}",
                                color = C.textPrimary,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${timeLabel(source.updatedAt)} · 车辆 ${source.carKey}",
                                color = C.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = picked ?: return@TextButton
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            val changed = withContext(Dispatchers.IO) {
                                runCatching { runtime.journal.adoptSource(target) }
                            }
                            busy = false
                            sources = null
                            changed
                                .onSuccess { applied ->
                                    val text = if (applied) {
                                        "已合并：${sourceCounts(target.data)}"
                                    } else {
                                        "这份数据本机都已经有了，没有需要新增的内容"
                                    }
                                    dialog = text
                                    onMessage(text)
                                }
                                .onFailure { dialog = "合并失败：${it.message ?: "未知错误"}" }
                        }
                    },
                ) {
                    Text(
                        if (busy) "合并中…" else "合并到当前车辆",
                        color = if (picked == null) C.textSecondary else C.accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { sources = null }) { Text("取消", color = C.textSecondary) }
            },
        )
    }

    dialog?.let { text ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = C.panelBg,
            title = { Text("零记数据", color = C.textPrimary) },
            text = { Text(text, color = C.textSecondary, fontSize = 12.sp, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("好", color = C.accent) }
            },
        )
    }
}

/** 一行说清一份数据里有多少东西。 */
private fun sourceCounts(data: JournalData): String {    val parts = buildList {
        if (data.costs.isNotEmpty()) add("记账 ${data.costs.size} 笔")
        if (data.checkins.isNotEmpty()) add("打卡 ${data.checkins.size} 城")
        if (data.dailyKm.isNotEmpty()) add("里程 ${data.dailyKm.size} 天")
        if (data.goal != null) add("用车目标")
    }
    return if (parts.isEmpty()) "无内容" else parts.joinToString(" · ")
}

private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA))
}

/**
 * 零记页顶部的「已接续历史数据」提示。
 *
 * 只在真的发生了接续、或本机有多份数据需要认领时出现。写清楚「原数据仍在」，
 * 是因为用户此刻的第一反应是「我的账是不是被吞了」—— 需要当场给一个可核对的结论，
 * 而不是让他自己去设置里翻。
 */
@Composable
fun JournalRecoveryBanner(note: JournalRecoveryNote, onDismiss: () -> Unit) {
    val C = LocalAppColors.current
    val title = if (note.auto) "已接续历史零记数据" else "本机有可找回的零记数据"
    val body = if (note.auto) {
        buildString {
            val parts = buildList {
                if (note.costs > 0) add("记账 ${note.costs} 笔")
                if (note.checkins > 0) add("打卡 ${note.checkins} 城")
                if (note.kmDays > 0) add("里程 ${note.kmDays} 天")
            }
            append(if (parts.isEmpty()) "已把本机原有数据接到当前车辆。" else "已接到当前车辆：" + parts.joinToString(" · "))
            append("\n原来的数据仍然保留在本机，没有删除。可在 设置 → 零记 里再核对一次。")
        }
    } else {
        "当前车辆还没有记录，但本机存在多份历史数据（${note.fromKey}）。\n" +
            "到 设置 → 零记 → 找回历史数据，选一份合并回来即可。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.chipBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, color = C.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = C.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "知道了",
            color = C.accent,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

/** 存储层读来源的提示文案；正常（主文件）时返回 null。 */
fun journalStorageWarning(source: JournalStore.Source?): String? = when (source) {
    JournalStore.Source.BACKUP -> "上次写入似乎没完成，已自动改用上一版备份。建议去「导出数据」存一份。"
    JournalStore.Source.SNAPSHOT -> "主数据文件读不出来，已自动改用最近一份日快照。"
    JournalStore.Source.FAILED -> "零记数据文件已损坏，为避免覆盖已暂停写入。可用「找回历史数据」恢复。"
    else -> null
}

/** 供设置页显示的一行小字（无异常时为空）。 */
@Composable
fun JournalStorageWarningRow(runtime: AppRuntime) {
    val C = LocalAppColors.current
    val status by runtime.journal.status
    val text = journalStorageWarning(status?.source) ?: status?.lastWriteError?.let { "零记保存失败：$it" }
    if (text == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = C.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
