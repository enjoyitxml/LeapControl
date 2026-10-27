package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.R

/**
 * 版本更新提醒弹窗：检测到 GitHub 有新版本时弹出。
 * - 「去下载」：跳转 GitHub 发布页下载最新安装包；
 * - 「稍后」：关闭弹窗，下次启动仍会提醒（未忽略该版本）；
 * - 「不再提醒此版本」：记下该版本，之后不再自动弹窗（设置页红点仍保留）。
 */
@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    currentVersion: String,
    onOpenDownload: () -> Unit,
    onOpenGithub: () -> Unit,
    onDismiss: () -> Unit,
    onNeverAgain: () -> Unit,
) {
    val C = LocalAppColors.current
    // 半透明遮罩 + 居中卡片；点遮罩 = 稍后
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = C.cardBg,
            shadowElevation = 18.dp,
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
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前版本 v$currentVersion。已有新版本发布，前往 GitHub 下载最新安装包可体验新功能与问题修复。",
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(22.dp))
                // 主操作：去下载（默认直接下载安装包）/ GitHub（查看发布页与更新说明）
                Row {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.accent)
                            .clickable(onClick = onOpenDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("去下载", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
                // 次要操作：稍后 / 不再提醒此版本
                Row {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.chipBg)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("稍后", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
