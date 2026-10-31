package com.leapcontrol.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.journal.JournalBackup
import com.leapcontrol.app.journal.JournalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 设置 →「零记」里的备份与迁移入口。
 *
 * 零记的数据只在本机（`filesDir/journal/journal_<vin>.json`），卸载重装或换手机就没了。
 * 这里给两条出口：
 *  - **导出**：走系统的「保存到…」，把全部车辆的数据写成一个 JSON，位置由用户自己挑；
 *  - **导入**：走系统的「打开文件」，读回那份 JSON 并**合并**进本机（不覆盖已有记录）。
 *
 * 全程不联网、不申请存储权限 —— SAF 拿到的是一次性的 uri，
 * 用户的账本不会被顺手传到别处，这也和零记「数据不出手机」的定位一致。
 */
@Composable
fun JournalBackupRows(
    runtime: AppRuntime,
    onMessage: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by runtime.journal.data
    var busy by remember { mutableStateOf(false) }
    // 读完文件先不落盘，弹确认框让用户看清楚这份备份是什么
    var pending by remember { mutableStateOf<JournalBackup.Payload?>(null) }
    // 结果 / 错误都用同一个居中卡片报，不依赖页面底部那行提示（用户在页面中部时看不见）
    var result by remember { mutableStateOf<String?>(null) }
    var lastExport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    val text = runtime.journal.exportBackup(BuildConfig.VERSION_NAME)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("写不进这个位置")
                }.exceptionOrNull()
            }
            busy = false
            if (failure == null) {
                val stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
                lastExport = stamp
                onMessage("零记数据已导出（$stamp）")
            } else {
                result = "导出失败：${failure.message ?: "无法写入所选位置"}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw JournalBackup.BackupException("打不开这个文件")
                    JournalBackup.decode(text).getOrThrow()
                }
            }
            busy = false
            decoded
                .onSuccess { pending = it }
                .onFailure { result = it.message ?: "这个文件读不出来" }
        }
    }

    ClickRow(
        title = "导出数据",
        desc = when {
            busy -> "处理中…"
            lastExport != null -> "上次导出 $lastExport，再导一次会另存一个文件"
            else -> localSummary(data)
        },
        icon = Icons.Filled.Save,
    ) {
        if (!busy) exportLauncher.launch(JournalBackup.fileName())
    }

    Spacer(Modifier.height(2.dp))

    ClickRow(
        title = "导入数据",
        desc = if (busy) "处理中…" else "选一个备份文件，与本机数据合并，不覆盖已有记录",
        icon = Icons.Filled.Restore,
    ) {
        // 不同文件管理器给 .json 的 MIME 不一样（application/json / text/plain / octet-stream），
        // 限定类型会把文件变灰选不中，干脆全放开、由识别逻辑把关
        if (!busy) importLauncher.launch(arrayOf("*/*"))
    }

    Spacer(Modifier.height(10.dp))

    Text(
        "账单、打卡、里程都只存在这台手机上。换手机时：旧机「导出数据」→ 把文件带过去 → " +
            "新机装好零控后「导入数据」。文件里有你的花费与行程，别随手发给别人。",
        color = C.textSecondary,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )

    // ---- 导入确认 ----
    pending?.let { payload ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            containerColor = C.panelBg,
            title = { Text("导入零记数据", color = C.textPrimary) },
            text = {
                Column {
                    Text(
                        JournalBackup.describe(payload),
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "会合并进这台手机现有的零记，不会删掉已经记下的内容。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        pending = null
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                runCatching { runtime.journal.importBackup(payload) }
                            }
                            busy = false
                            outcome
                                .onSuccess {
                                    val text = JournalBackup.describe(it)
                                    result = text
                                    onMessage(text)
                                }
                                .onFailure { result = "导入失败：${it.message ?: "未知错误"}" }
                        }
                    },
                ) { Text(if (busy) "导入中…" else "导入", color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text("取消", color = C.textSecondary)
                }
            },
        )
    }

    // ---- 结果 / 错误 ----
    result?.let { text ->
        AlertDialog(
            onDismissRequest = { result = null },
            containerColor = C.panelBg,
            title = { Text("零记数据", color = C.textPrimary) },
            text = {
                Text(text, color = C.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            },
            confirmButton = {
                TextButton(onClick = { result = null }) { Text("好", color = C.accent) }
            },
        )
    }
}

/** 导出按钮的副标题：本机现在有多少东西可导。只算当前这辆车，和零记页显示的口径一致。 */
private fun localSummary(data: JournalData): String {
    val parts = buildList {
        if (data.costs.isNotEmpty()) add("记账 ${data.costs.size} 笔")
        if (data.checkins.isNotEmpty()) add("打卡 ${data.checkins.size} 城")
        if (data.dailyKm.isNotEmpty()) add("里程 ${data.dailyKm.size} 天")
    }
    return if (parts.isEmpty()) "本机还没有零记数据" else "本机：" + parts.joinToString(" · ")
}
