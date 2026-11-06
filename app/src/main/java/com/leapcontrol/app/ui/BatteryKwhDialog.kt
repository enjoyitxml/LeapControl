package com.leapcontrol.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.trip.TripRules

/**
 * 电池容量输入框（kWh）。
 *
 * 行程能耗要用它把「电量掉了几个百分点」换算成度数，所以入口有两处：
 * ① 设置 → 高级设置 → 电池容量；② 行程轨迹页月合计下面那一行。
 * 两处共用同一个对话框 —— 之前只在设置里，用户翻不到（2026-11-06 反馈「没看到哪里能填」）。
 *
 * 不按车型猜：同一款车常有多个电池版本，猜错就是把估算写成实测。
 * 留空 = 清除，只显示百分比不换算度数。
 */
@Composable
fun BatteryKwhDialog(
    current: Double?,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val C = LocalAppColors.current
    var input by remember { mutableStateOf(current?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelBg,
        title = { Text("电池容量", color = C.textPrimary) },
        text = {
            Column {
                Text(
                    "行程耗电靠起点与终点的电量差算。填了容量才能换算成度数（kWh）和百公里电耗；" +
                        "同一款车常有多个电池版本，所以不按车型猜，请照车书或官方 App 填。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { c -> c.isDigit() || c == '.' }.take(6)
                        error = null
                    },
                    placeholder = { Text("如 78.5", color = C.textSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("kWh", color = C.textSecondary, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.textPrimary,
                        unfocusedTextColor = C.textPrimary,
                        cursorColor = C.accent,
                        focusedBorderColor = C.accent,
                        unfocusedBorderColor = C.border,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error
                        ?: "留空并保存 = 清除，只显示耗电百分比。",
                    color = if (error != null) AlertRed else C.textSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val text = input.trim()
                if (text.isEmpty()) {
                    onSave(null)
                    onDismiss()
                    return@TextButton
                }
                val value = text.toDoubleOrNull()
                if (value == null || value !in TripRules.MIN_BATTERY_KWH..TripRules.MAX_BATTERY_KWH) {
                    error = "请填 ${TripRules.MIN_BATTERY_KWH.toInt()}–" +
                        "${TripRules.MAX_BATTERY_KWH.toInt()} 之间的数字（kWh）"
                } else {
                    onSave(value)
                    onDismiss()
                }
            }) {
                Text("保存", color = C.accent, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = C.textSecondary)
            }
        },
    )
}
