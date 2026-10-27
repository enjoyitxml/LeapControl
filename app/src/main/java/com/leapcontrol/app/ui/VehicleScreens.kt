package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.ChargePlanCyclesHelper
import com.leapcontrol.app.ScheduledAcMode
import com.leapcontrol.app.ScheduledAcPlan
import com.leapcontrol.app.VehicleOtaInfo
import java.util.Locale

// ---------------------------------------------------------------------------
// 首页入口卡片：定时空调 / 车辆体检 / 系统升级
// ---------------------------------------------------------------------------

/** 通用入口卡片：图标 + 标题 + 副标题（副标题缺失时留空）。 */
@Composable
internal fun FeatureEntryTile(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(C.cardBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = C.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle.orEmpty(),
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** 定时空调入口。 */
@Composable
fun ScheduledAcTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    FeatureEntryTile(
        icon = Icons.Filled.Schedule,
        title = "定时空调",
        subtitle = "设定每天自动开启空调的时间与模式",
        modifier = modifier,
        onClick = onClick,
    )
}

/** 车辆体检入口：[subtitle] 显示最近一次体检摘要。 */
@Composable
fun VehicleHealthTile(modifier: Modifier = Modifier, subtitle: String?, onClick: () -> Unit) {
    FeatureEntryTile(
        icon = Icons.Filled.MonitorHeart,
        title = "车辆体检",
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
    )
}

/** 系统升级（OTA）入口。 */
@Composable
fun OtaTile(modifier: Modifier = Modifier, subtitle: String?, onClick: () -> Unit) {
    FeatureEntryTile(
        icon = Icons.Filled.SystemUpdate,
        title = "系统升级",
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
    )
}

// ---------------------------------------------------------------------------
// 公共控件
// ---------------------------------------------------------------------------

/** 二级页面顶栏：居中标题 + 左侧返回。 */
@Composable
internal fun ScreenTopBar(title: String, onBack: () -> Unit) {
    val C = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = C.iconBg,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onBack),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "返回", tint = C.textPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** 分区容器（左侧标题 + 右侧内容），用于体检项等需要横向布局的场景。 */
@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.panelBg, RoundedCornerShape(18.dp))
            .border(1.dp, C.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

/** 时间选择行：小时 / 分钟两个下拉，分钟按 step 步进。 */
@Composable
internal fun TimePickerRow(
    label: String,
    time: String,
    minuteStep: Int = 5,
    onTimeChange: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TimeDropdown(
            value = hour,
            options = (0..23).toList(),
            onValueChange = { onTimeChange(String.format(Locale.CHINA, "%02d:%02d", it, minute)) },
        )
        Text(":", color = C.textPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
        TimeDropdown(
            value = minute,
            options = (0..59 step minuteStep).toList(),
            onValueChange = { onTimeChange(String.format(Locale.CHINA, "%02d:%02d", hour, it)) },
        )
    }
}

/** 单个时间的下拉选择器（数字补零显示）。 */
@Composable
internal fun TimeDropdown(
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val display = String.format(Locale.CHINA, "%02d", value.takeIf { it in options } ?: options.first())
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(C.iconBg)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = display, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = C.textSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(String.format(Locale.CHINA, "%02d", option)) },
                    onClick = { onValueChange(option); expanded = false },
                )
            }
        }
    }
}

/** 横向排列的胶囊选项组（单选）。 */
@Composable
internal fun <T> ChoicePillRow(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    val C = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) C.chipBgActive else C.chipBg)
                    .clickable(enabled = enabled) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) C.accent else C.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 带 +/- 步进的数值行。 */
@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = C.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        RoundStepButton("-", enabled && value > range.first) { onValueChange((value - 1).coerceIn(range)) }
        Text(
            text = "$value$unit",
            color = C.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        RoundStepButton("+", enabled && value < range.last) { onValueChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun RoundStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.iconBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) C.textPrimary else C.textSecondary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// 定时空调（cmdid 171）：参考官网「预约空调」
// ---------------------------------------------------------------------------

/**
 * 定时空调二级页。
 *
 * [plan] 为当前编辑中的草稿；进入页面时由调用方通过 [onLoad] 拉回车端已保存的计划回填。
 * 保存下发后车端按计划自动开启空调，plan.enabled=false 表示取消已有计划。
 */
@Composable
fun ScheduledAcScreen(
    plan: ScheduledAcPlan,
    onPlanChange: (ScheduledAcPlan) -> Unit,
    loading: Boolean = false,
    loadingFailed: Boolean = false,
    onReload: () -> Unit = {},
    saving: Boolean = false,
    result: String? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val selectedDays = remember(plan.days) { ChargePlanCyclesHelper.parseToDaySet(plan.days) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        ScreenTopBar("定时空调", onBack)

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = C.accent)
                }
                return@Column
            }
            loadingFailed -> {
                InfoCard {
                    Text("未能读取车端计划", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "可先按下面内容手动设置并下发，下发成功即会覆盖车端旧计划",
                        color = C.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重试读取")
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // 总开关
        InfoCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("启用定时空调", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(
                    checked = plan.enabled,
                    onCheckedChange = { onPlanChange(plan.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.accent, uncheckedTrackColor = C.barTrack),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "设定时间到点后自动开启空调，无需手动操作；夏令钳制外的时段请自行确认车辆是否允许远程控制",
                color = C.textSecondary, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        InfoCard {
            TimePickerRow("开启时间", plan.startTime, minuteStep = 5) {
                onPlanChange(plan.copy(startTime = it))
            }
        }

        Spacer(Modifier.height(14.dp))
        InfoCard {
            Text("重复", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayLabels.forEachIndexed { index, label ->
                    val day = index + 1
                    val active = selectedDays.contains(day)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) C.chipBgActive else C.chipBg)
                            .clickable {
                                val next = if (active) selectedDays - day else selectedDays + day
                                onPlanChange(plan.copy(days = ChargePlanCyclesHelper.toVehicleMask(next)))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (active) C.accent else C.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        InfoCard {
            Spacer(Modifier.height(2.dp))
            StepperRow("目标温度", plan.temperatureC, 16..32, "℃") {
                onPlanChange(plan.copy(temperatureC = it))
            }
            Spacer(Modifier.height(12.dp))
            Text("风量", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ChoicePillRow(
                options = (1..7).map { it to it.toString() },
                selected = plan.windLevel,
            ) { onPlanChange(plan.copy(windLevel = it)) }
            Spacer(Modifier.height(14.dp))
            Text("模式", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ChoicePillRow(
                options = ScheduledAcMode.entries.map { it to it.label },
                selected = plan.mode,
            ) { onPlanChange(plan.copy(mode = it)) }
        }

        Spacer(Modifier.height(14.dp))
        InfoCard {
            Text("空气循环", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ChoicePillRow(
                options = com.leapcontrol.app.AirCircle.entries.map { it to it.displayLabel },
                selected = plan.circle,
            ) { onPlanChange(plan.copy(circle = it)) }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("前挡除雾", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(
                    checked = plan.windshieldDefogging,
                    onCheckedChange = { onPlanChange(plan.copy(windshieldDefogging = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.accent, uncheckedTrackColor = C.barTrack),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("下发中…", color = Color.White, fontSize = 15.sp)
            } else {
                Text("保存并下发设置", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCancel,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("取消定时", color = C.textSecondary, fontSize = 14.sp)
        }
        if (result != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = result,
                color = C.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ---------------------------------------------------------------------------
// 车辆体检：四条系统 + 一键修复
// ---------------------------------------------------------------------------

/**
 * 车辆体检二级页：顶部评分与摘要，下方四大系统可折叠展开，异常项提供一键修复。
 * [busy] 非空表示正在下发某条修复指令（值为指令名），用于禁用该按钮。
 */
@Composable
fun VehicleHealthScreen(
    status: CarStatus?,
    busy: String? = null,
    onFix: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val report = remember(status) { VehicleHealthDiagnostics.evaluate(status) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        ScreenTopBar("车辆体检", onBack)

        // 评分与摘要
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HealthScore(score = report.score, level = report.level)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.summary,
                    color = C.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (status == null) "暂无车况数据" else "共检查 ${report.systems.size} 个系统、${report.systems.sumOf { it.items.size }} 项指标",
                    color = C.textSecondary,
                    fontSize = 12.5.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("刷新车况后重新体检", color = C.textSecondary, fontSize = 14.sp)
        }

        // 系统清单
        report.systems.forEach { system ->
            Spacer(Modifier.height(14.dp))
            HealthSystemCard(system = system, busy = busy, onFix = onFix)
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun HealthScore(score: Int, level: HealthLevel) {
    val C = LocalAppColors.current
    val color = healthColor(level)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
        CircularProgressIndicator(
            progress = (score / 100f).coerceIn(0f, 1f),
            modifier = Modifier.size(84.dp),
            strokeWidth = 6.dp,
            color = color,
            trackColor = C.barTrack,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = score.toString(), color = color, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "分", color = C.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HealthSystemCard(
    system: HealthSystemReport,
    busy: String?,
    onFix: (String) -> Unit,
) {
    val C = LocalAppColors.current
    var expanded by remember { mutableStateOf(system.defaultExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.panelBg, RoundedCornerShape(18.dp))
            .border(1.dp, C.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = system.name,
                color = C.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = system.statusText,
                color = if (system.level == HealthLevel.GOOD) C.textSecondary else healthColor(system.level),
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = C.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            system.items.forEach { item ->
                HealthItemRow(item = item, busy = busy, onFix = onFix)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HealthItemRow(item: HealthCheckItem, busy: String?, onFix: (String) -> Unit) {
    val C = LocalAppColors.current
    val color = healthColor(item.level)
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = when (item.level) {
                HealthLevel.CRITICAL -> Icons.Filled.ErrorOutline
                HealthLevel.WARNING -> Icons.Filled.WarningAmber
                HealthLevel.GOOD -> Icons.Filled.CheckCircle
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = if (item.level == HealthLevel.GOOD) C.textPrimary else color,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.fixCommand != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.iconBg)
                            .clickable(enabled = busy == null) { onFix(item.fixCommand!!) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = if (busy == item.fixCommand) "执行中…" else item.fixLabel ?: "一键处理",
                            color = if (busy == null) C.accent else C.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(text = item.detail, color = C.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun healthColor(level: HealthLevel): Color = when (level) {
    HealthLevel.GOOD -> Color(0xFF2FCC71)
    HealthLevel.WARNING -> Color(0xFFFF9F43)
    HealthLevel.CRITICAL -> Color(0xFFFF5B5B)
}

// ---------------------------------------------------------------------------
// OTA 升级（cmdid 390 / 391 / 392）
// ---------------------------------------------------------------------------

/**
 * OTA 升级二级页：展示当前版本与新版本信息，按状态提供下载 / 安装 / 预约安装。
 * [onAction] 的第一个参数是动作名：download / install / schedule，第二个是附加参数（预约时间）。
 */
@Composable
fun OtaScreen(
    info: VehicleOtaInfo?,
    loading: Boolean = false,
    busy: String? = null,
    result: String? = null,
    onAction: (String, String) -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var scheduleTime by remember { mutableStateOf("23:00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        ScreenTopBar("系统升级", onBack)

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = C.accent)
                }
            }
            info == null -> {
                Spacer(Modifier.height(30.dp))
                InfoCard {
                    Icon(Icons.Filled.SystemUpdate, null, tint = C.textSecondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("暂未获取到升级信息", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "可能是车辆尚未上报固件版本，或当前网络无法访问升级服务",
                        color = C.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth()) { Text("重新查询") }
                }
            }
            else -> {
                Spacer(Modifier.height(10.dp))
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SystemUpdate, null, tint = C.accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("当前版本", color = C.textSecondary, fontSize = 12.sp)
                            Text(info.currentVersion, color = C.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalLine(Modifier.padding(vertical = 14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OtaField("升级状态", otaStatusText(info), Modifier.weight(1f))
                        if (info.newVersion.isNotBlank()) {
                            OtaField("最新版本", info.newVersion, Modifier.weight(1f))
                        }
                    }
                    if (info.packageSize.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        OtaField("固件大小", info.packageSize, Modifier.fillMaxWidth())
                    }
                    info.progressPercent?.let {
                        Spacer(Modifier.height(12.dp))
                        Text("进度 $it%", color = C.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(C.barTrack),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((it / 100f).coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .background(C.accent),
                            )
                        }
                    }
                }

                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    InfoCard {
                        Text("更新说明", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(info.releaseNotes, color = C.textSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                }

                // 操作区
                Spacer(Modifier.height(18.dp))
                when {
                    info.isUpToDate -> {
                        OtaHintCard("已是最新版本", "车辆当前运行的固件为官方最新版本，无需升级")
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("重新检查", color = C.textSecondary, fontSize = 14.sp)
                        }
                    }
                    info.isDownloading || info.isInstalling -> {
                        OtaHintCard("升级进行中", otaStatusText(info) + "，请勿熄火断电")
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("刷新进度", color = C.textSecondary, fontSize = 14.sp)
                        }
                    }
                    info.isDownloaded -> {
                        InfoCard {
                            Text("预约安装时间", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            TimePickerRow("安装时间", scheduleTime) { scheduleTime = it }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "安装过程中车辆将无法使用，建议选择无人用车的时段",
                                color = C.textSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        OtaButton("安装固件", "install", busy, onAction)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onAction("schedule", scheduleTime) },
                            enabled = busy == null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("预约安装", color = C.textSecondary, fontSize = 14.sp) }
                    }
                    info.status == com.leapcontrol.app.OtaStatus.FAILED -> {
                        OtaHintCard("上次升级失败", "可重新下载固件包后再安装；若反复失败请联系售后")
                        Spacer(Modifier.height(12.dp))
                        OtaButton("重新下载固件", "download", busy, onAction)
                    }
                    info.status == com.leapcontrol.app.OtaStatus.SCHEDULED -> {
                        OtaHintCard("已预约安装", info.scheduledTime?.let { "将在 $it 开始安装" } ?: "车辆将按预约时间安装固件")
                        Spacer(Modifier.height(12.dp))
                        InfoCard {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("调整安装时间", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                TimeDropdown(
                                    value = scheduleTime.split(":").getOrNull(0)?.toIntOrNull() ?: 23,
                                    options = (0..23).toList(),
                                ) { scheduleTime = String.format(Locale.CHINA, "%02d:00", it) }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "修改后会覆盖原预约计划",
                                color = C.textSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        OtaButton("更新预约时间", "schedule", busy, onAction)
                    }
                    else -> {
                        if (info.newVersion.isNotBlank()) {
                            OtaHintCard("发现新版本", "建议连接 WiFi 或在家中充电桩附近下载固件包")
                            Spacer(Modifier.height(12.dp))
                        }
                        OtaButton("下载固件", "download", busy, onAction)
                    }
                }

                if (result != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = result,
                        color = C.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun OtaButton(label: String, action: String, busy: String?, onAction: (String, String) -> Unit) {
    Button(
        onClick = { onAction(action, "") },
        enabled = busy == null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.accent),
    ) {
        if (busy == action) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("执行中…", color = Color.White, fontSize = 15.sp)
        } else {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun OtaHintCard(title: String, detail: String) {
    val C = LocalAppColors.current
    InfoCard {
        Text(title, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = C.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun OtaField(label: String, value: String, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Column(modifier = modifier) {
        Text(label, color = C.textSecondary, fontSize = 11.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HorizontalLine(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(LocalAppColors.current.border))
}

private fun otaStatusText(info: VehicleOtaInfo): String = when (info.status) {
    com.leapcontrol.app.OtaStatus.UP_TO_DATE -> "已是最新"
    com.leapcontrol.app.OtaStatus.UPDATE_AVAILABLE -> "待下载"
    com.leapcontrol.app.OtaStatus.DOWNLOADING -> "下载中 ${info.progressPercent?.let { "$it%" } ?: ""}".trim()
    com.leapcontrol.app.OtaStatus.DOWNLOADED -> "待安装"
    com.leapcontrol.app.OtaStatus.INSTALLING -> "安装中"
    com.leapcontrol.app.OtaStatus.SCHEDULED -> "已预约"
    com.leapcontrol.app.OtaStatus.FAILED -> "升级失败"
    com.leapcontrol.app.OtaStatus.UNKNOWN -> "状态未知"
}
