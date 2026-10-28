package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.ui.theme.statusGood
import com.leapcontrol.app.ui.theme.statusGoodDark
import com.leapcontrol.app.ui.theme.statusWarn
import com.leapcontrol.app.ui.theme.statusWarnDark

/**
 * 「车辆健康」卡：电池三项读数 + 后台耗电功能监测 + 一键关闭。
 *
 * 读数直接取车况快照（`batteryVoltage` 1177 / `batteryCurrent` 1178 / `minBatteryTemp` 1182），
 * 也就是说**每次首页刷新都会跟着更新**，不额外打接口。
 *
 * 耗电项由 [VehiclePowerWatch] 判定后传进来，这里只管排版：
 * 有耗电项就把它们摊成胶囊 + 一个「一键关闭」；没有就给一行绿色「状态正常」。
 * 卡片本身不判断车况、不拼指令，方便 [VehiclePowerWatch] 单测。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleHealthCard(
    status: CarStatus?,
    drains: List<PowerDrain>,
    busy: Boolean = false,
    /** 关闭进度（已完成, 总数）；busy 时显示在按钮上。 */
    progress: Pair<Int, Int>? = null,
    onShutDownAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val good = if (C.isDark) statusGoodDark else statusGood
    val warn = if (C.isDark) statusWarnDark else statusWarn

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // ---- 标题 + 状态徽标 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "车辆健康",
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (drains.isEmpty()) good else warn, CircleShape),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (drains.isEmpty()) "状态正常" else "${drains.size} 项耗电",
                color = if (drains.isEmpty()) good else warn,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---- 电池三项读数 ----
        Row(modifier = Modifier.fillMaxWidth()) {
            BatteryCell(
                label = "电池温度",
                raw = status?.batteryTemp,
                unit = "℃",
                hint = VehiclePowerWatch.batteryTempHint(status?.batteryTemp),
                warnColor = warn,
                modifier = Modifier.weight(1f),
            )
            BatteryCell(
                label = "电压",
                raw = status?.chargeVoltage,
                unit = "V",
                hint = null,
                warnColor = warn,
                modifier = Modifier.weight(1f),
            )
            BatteryCell(
                label = "电流",
                raw = status?.chargeCurrent,
                unit = "A",
                hint = null,
                warnColor = warn,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        if (drains.isEmpty()) {
            // 没耗电项：一行绿字说明，不给按钮（没有可关的东西）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "没有额外耗电功能在运行",
                    color = good,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "电池状态良好",
                    color = C.textSecondary,
                    fontSize = 10.5.sp,
                )
            }
            return@Column
        }

        // ---- 耗电项 ----
        Text(
            text = if (status?.isShutDown == true || status?.locked == true) {
                "锁车后仍有 ${drains.size} 项功能在耗电，建议关闭"
            } else {
                "以下 ${drains.size} 项功能正在运行，会持续消耗电量"
            },
            color = C.textSecondary,
            fontSize = 10.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            drains.forEach { drain ->
                DrainChip(drain = drain, warnColor = warn)
            }
        }

        Spacer(Modifier.height(10.dp))
        ShutDownButton(
            busy = busy,
            progress = progress,
            enabled = !busy,
            accent = C.accent,
            onClick = onShutDownAll,
        )
    }
}

/** 电池读数格：小标签 + 大数值 + 小单位；读不到就是「--」。 */
@Composable
private fun BatteryCell(
    label: String,
    raw: String?,
    unit: String,
    hint: String?,
    warnColor: Color,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val value = VehiclePowerWatch.parseReading(raw)
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = C.textSecondary, fontSize = 10.sp)
            if (hint != null) {
                Spacer(Modifier.width(3.dp))
                Text(hint, color = warnColor, fontSize = 9.5.sp)
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value?.let { formatBatteryValue(it) } ?: "--",
                color = if (hint != null) warnColor else C.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                color = C.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** 读数去掉多余小数：28.0 → 28，2.43 → 2.4（电流精度到 0.1 就够了）。 */
internal fun formatBatteryValue(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
}

/** 一项耗电功能的小胶囊：左侧名字、右侧档位。 */
@Composable
private fun DrainChip(drain: PowerDrain, warnColor: Color) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(warnColor.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = drain.label,
            color = if (C.isDark) warnColor else Color(0xFF8A5312),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
        )
        if (drain.detail.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = drain.detail,
                color = C.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/** 「一键关闭」按钮：整行宽，按下即把全部耗电项逐条下发。 */
@Composable
private fun ShutDownButton(
    busy: Boolean,
    progress: Pair<Int, Int>?,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(accent, RoundedCornerShape(9.dp))
            .alpha(if (enabled) 1f else 0.62f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                busy && progress != null -> "正在关闭 ${progress.first}/${progress.second}"
                busy -> "正在关闭…"
                else -> "一键关闭全部"
            },
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
