package com.leapcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.R
import android.widget.Toast

/**
 * 版本更新提醒弹窗：检测到 GitHub 有新版本时弹出。
 * - 「去下载」：App 内下载安装包，下载中可最小化到悬浮条，不影响继续用车；
 * - 「稍后」：关闭弹窗，下次启动仍会提醒（未忽略该版本）；
 * - 「不再提醒此版本」：记下该版本，之后不再自动弹窗（设置页红点仍保留）；
 * - 「更新说明」：读取 Release 正文，默认折叠，点开才展开（长说明不占满屏）。
 */
@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    currentVersion: String,
    onOpenDownload: () -> Unit,
    onOpenGithub: () -> Unit,
    onDismiss: () -> Unit,
    onNeverAgain: () -> Unit,
    /** 最小化到悬浮条：下载继续，弹窗收起。 */
    onMinimize: () -> Unit = {},
    /** 正在 App 内下载安装包：按钮锁住，显示进度。 */
    downloading: Boolean = false,
    /** 下载进度 0f..1f；null 表示总长未知。 */
    progress: Float? = null,
    /** 下载失败的提示文案。 */
    downloadError: String? = null,
) {
    val C = LocalAppColors.current
    var notesExpanded by remember { mutableStateOf(false) }
    // 半透明遮罩 + 居中卡片；点遮罩 = 稍后
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIALOG_SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = C.dialogBg,
            shadowElevation = 20.dp,
            // 卡片自身消费点击，避免穿透到遮罩误关
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clickable(onClick = { }),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_system_update),
                        contentDescription = null,
                        tint = C.accent,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(22.dp),
                    )
                    Text(
                        text = "发现新版本 v${info.version}",
                        color = C.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    // 最小化：下载中可以边下边用，不占着整屏
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(C.chipBg)
                            .clickable(onClick = onMinimize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("—", color = C.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前版本 v$currentVersion。已有新版本发布，前往 GitHub 下载最新安装包可体验新功能与问题修复。",
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )

                // ---- 更新说明：默认折叠，点开才展开 ----
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.chipBg)
                            .clickable { notesExpanded = !notesExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "更新说明",
                                color = C.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (notesExpanded) "收起" else "展开",
                                color = C.textSecondary,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = C.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(if (notesExpanded) 180f else 0f),
                            )
                        }
                        AnimatedVisibility(visible = notesExpanded) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = plainNotes(info.notes),
                                    color = C.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier
                                        // 说明可能很长：限高 + 内部滚动，别把弹窗顶出屏幕
                                        .heightIn(max = 170.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                // ---- 下载通道说明 ----
                // 安装包放在 GitHub Releases，国内网络时不时卡在几百 KB/s 甚至直连失败。
                // 这句话就是为了让用户知道「下不动不是 App 坏了」，并给出加群这条兜底路径。
                // 整行可点：点一下复制群号，省得手抄 9 位数字（和「我的」页页脚一致）。
                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(QQ_GROUP))
                            Toast.makeText(context, "已复制群号 $QQ_GROUP", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = C.textSecondary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("更新包走 GitHub 官方通道。下载缓慢或无法下载时，请切换网络重试，或加 QQ 群 ")
                            withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.Medium)) {
                                append(QQ_GROUP)
                            }
                            append(" 在群内下载（点此复制群号）。")
                        },
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                // 下载进度 / 失败提示：只在走 App 内下载时出现
                if (downloading || downloadError != null) {
                    if (downloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(C.chipBg),
                        ) {
                            val fraction = (progress ?: 0f).coerceIn(0f, 1f)
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(C.accent),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (progress != null) {
                                "正在下载安装包 ${(progress * 100).toInt()}%"
                            } else {
                                "正在下载安装包…"
                            },
                            color = C.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    if (downloadError != null) {
                        Text(
                            text = downloadError,
                            color = Color(0xFFD32F2F),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // 主操作：去下载（App 内下载后直接唤起安装器）/ GitHub（查看发布页与更新说明）
                Row {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (downloading) C.accent.copy(alpha = 0.55f) else C.accent)
                            .clickable(enabled = !downloading, onClick = onOpenDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                downloading -> "下载中"
                                downloadError != null -> "重新下载"
                                else -> "去下载"
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.chipBg)
                            .clickable(onClick = onOpenGithub),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("GitHub", color = C.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 次要操作：稍后（下载中＝后台下载，转成悬浮条而不是彻底关掉）
                Row {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.chipBg)
                            .clickable(onClick = if (downloading) onMinimize else onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (downloading) "后台下载" else "稍后",
                            color = C.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onNeverAgain),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("不再提醒此版本", color = C.textSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * 最小化后的更新悬浮条：贴在底部导航上方，点了重新打开完整弹窗。
 *
 * 存在的意义只有一个 —— 安装包 25 MB 要下几十秒，不该让用户一直盯着一块挡住界面的弹窗。
 * 收起后仍能看到进度，下载在后台继续。
 */
@Composable
fun UpdateMiniBar(
    version: String,
    downloading: Boolean,
    progress: Float?,
    error: String?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    // 实心面板 + 发丝描边：半透明 + elevation 会「四周发灰、中间发白」，压在渐变背景上尤其脏
    Row(
        modifier = modifier
            .clip(shape)
            .background(C.panelBg)
            .border(1.dp, C.border, shape)
            .clickable(onClick = onExpand)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 进度环：没有具体百分比时用一小段弧，表示「在转」
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = C.chipBg,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                val sweep = when {
                    error != null -> 0f
                    downloading -> (progress ?: 0.25f).coerceIn(0.02f, 1f) * 360f
                    else -> 360f
                }
                if (sweep > 0f) {
                    drawArc(
                        color = if (error != null) Color(0xFFD32F2F) else C.accent,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                when {
                    error != null -> "更新下载失败"
                    downloading -> "正在下载 v$version"
                    else -> "新版本 v$version 可更新"
                },
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (downloading) {
                Text(
                    if (progress != null) "${(progress * 100).toInt()}%" else "下载中…",
                    color = C.textSecondary,
                    fontSize = 10.sp,
                )
            } else if (error != null) {
                Text("点开重试", color = C.textSecondary, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("展开", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 把 Release 正文里的 Markdown 记号去掉，只留可读的纯文本。
 *
 * 弹窗里不引 Markdown 渲染器（为一个更新说明引一整个库不值得），
 * 这里做最小清理：标题井号、加粗星号、行内代码反引号、列表符号。
 */
private fun plainNotes(raw: String): String = raw
    .replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")
    .replace(Regex("""\*\*([^*\n]+)\*\*"""), "$1")
    .replace(Regex("""`([^`\n]+)`"""), "$1")
    .replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "· ")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()
