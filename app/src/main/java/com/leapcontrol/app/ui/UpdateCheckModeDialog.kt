package com.leapcontrol.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.UpdateCheckMode
import com.leapcontrol.app.UpdateCheckPolicy

/**
 * 「设置更新频率」弹层：关闭 / 每次启动 / 每 7 天 / 每 14 天 / 每 30 天。
 *
 * 入口挂在「我的 → 检查更新」那一行的右侧（贴着它要影响的功能），不再藏在设置里：
 * 用户产生「更新太频繁」的念头时人正在看这一行，让他在设置里翻三层找开关是说不过去的。
 *
 * 选中即生效、不设「保存」按钮 —— 这只是一条偏好，没有需要整体确认的连带后果。
 */
@Composable
fun UpdateCheckModeDialog(
    runtime: AppRuntime,
    onDismiss: () -> Unit,
    /** 选完之后回调一句提示文案；调用方负责弹 Toast / 显示提示。 */
    onPicked: (String) -> Unit = {},
) {
    val C = LocalAppColors.current
    // 当前档位提到循环外读一次：五个选项各读一遍 prefs 没必要
    val currentMode = runtime.updateCheckMode()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelBg,
        title = { Text("设置更新频率", color = C.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    UpdateCheckPolicy.statusText(
                        mode = currentMode,
                        lastCheckAt = runtime.lastUpdateCheckAt(),
                        now = System.currentTimeMillis(),
                    ) + "\n" +
                        "只在打开 App 时判断一次，不会后台定时联网；无论选哪档，都可以随时手动检查。",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                UpdateCheckMode.entries.forEach { mode ->
                    SettingOptionRow(
                        label = mode.label,
                        desc = mode.desc,
                        selected = currentMode == mode,
                    ) {
                        runtime.setUpdateCheckMode(mode)
                        onPicked(
                            if (mode == UpdateCheckMode.OFF) {
                                "已关闭自动检测，之后请在「检查更新」那一行手动查"
                            } else {
                                "已设为${mode.label}检测一次"
                            },
                        )
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = C.textSecondary) }
        },
    )
}
