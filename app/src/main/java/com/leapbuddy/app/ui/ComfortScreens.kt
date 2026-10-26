package com.leapbuddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapbuddy.app.FridgeControlCommand
import com.leapbuddy.app.FridgeMode
import com.leapbuddy.app.FridgeStyle

/** 二级页面顶栏：返回 + 标题。 */
@Composable
private fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = C.textPrimary)
        }
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 二级页面里的分区卡片。 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.cardBg, shape)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** 一行档位按钮：高亮当前档，点击下发对应指令。 */
@Composable
private fun LevelRow(
    label: String,
    current: Int?,
    levelLabels: List<String>,
    busy: Boolean,
    commandPrefix: String,
    onCommand: (String) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = C.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.width(76.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            levelLabels.forEachIndexed { index, text ->
                val selected = current == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) C.chipBgActive else C.chipBg)
                        .clickable(enabled = !busy) { onCommand("$commandPrefix$index") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        color = if (selected) C.accent else C.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 座椅舒适：座椅加热 / 通风、方向盘加热、后视镜加热
// ---------------------------------------------------------------------------

@Composable
fun SeatComfortScreen(
    onBack: () -> Unit,
    status: CarStatus?,
    busy: Boolean,
    onCommand: (String) -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SubScreenTopBar("座椅舒适", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "档位取自车况上报，信号缺失时显示未选；点一下即刻下发。",
            color = C.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        SectionCard("座椅加热") {
            val heat = status?.seatHeating
            LevelRow("主驾", heat?.driver, listOf("关", "1档", "2档", "3档"), busy, "driverSeatHeating_", onCommand)
            LevelRow("副驾", heat?.passenger, listOf("关", "1档", "2档", "3档"), busy, "passengerSeatHeating_", onCommand)
            LevelRow("左后", heat?.leftRear, listOf("关", "1档", "2档", "3档"), busy, "leftRearSeatHeating_", onCommand)
            LevelRow("右后", heat?.rightRear, listOf("关", "1档", "2档", "3档"), busy, "rightRearSeatHeating_", onCommand)
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("座椅通风") {
            val vent = status?.seatVentilation
            LevelRow("主驾", vent?.driver, listOf("关", "1档", "2档", "3档"), busy, "driverSeatVentilation_", onCommand)
            LevelRow("副驾", vent?.passenger, listOf("关", "1档", "2档", "3档"), busy, "passengerSeatVentilation_", onCommand)
            LevelRow("左后", vent?.leftRear, listOf("关", "1档", "2档", "3档"), busy, "leftRearSeatVentilation_", onCommand)
            LevelRow("右后", vent?.rightRear, listOf("关", "1档", "2档", "3档"), busy, "rightRearSeatVentilation_", onCommand)
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("方向盘加热") {
            LevelRow(
                "方向盘",
                status?.steeringWheelHeating,
                listOf("关", "弱", "强"),
                busy,
                "steeringWheelHeating_",
                onCommand,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("后视镜加热") {
            val on = status?.rearviewMirrorHeating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(true to "开启", false to "关闭").forEach { (value, text) ->
                    val selected = on == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) C.chipBgActive else C.chipBg)
                            .clickable(enabled = !busy) {
                                onCommand(if (value) "rearviewMirrorHeating_on" else "rearviewMirrorHeating_off")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            color = if (selected) C.accent else C.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// 车载冰箱
// ---------------------------------------------------------------------------

@Composable
fun FridgeScreen(
    onBack: () -> Unit,
    status: CarStatus?,
    busy: Boolean,
    onControlFridge: (FridgeControlCommand) -> Unit,
) {
    val C = LocalAppColors.current
    val snapshot = status?.fridge
    var enabled by remember(snapshot?.enabled) { mutableStateOf(snapshot?.enabled ?: false) }
    var mode by remember(snapshot?.mode) { mutableStateOf(snapshot?.mode ?: FridgeMode.COLD) }
    var style by remember(snapshot?.style) { mutableStateOf(snapshot?.style ?: FridgeStyle.NORMAL) }
    var temp by remember(snapshot?.temp) { mutableIntStateOf(snapshot?.temp ?: 4) }
    var parkEnable by remember { mutableStateOf(false) }
    var durationHours by remember { mutableIntStateOf(2) }
    var cycles by remember { mutableStateOf("1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SubScreenTopBar("车载冰箱", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "部分车型无此硬件，下发后请以车端反馈为准。",
            color = C.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        SectionCard("开关") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(true to "开启", false to "关闭").forEach { (value, text) ->
                    val selected = enabled == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) C.chipBgActive else C.chipBg)
                            .clickable { enabled = value },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            color = if (selected) C.accent else C.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        if (enabled) {
            Spacer(Modifier.height(12.dp))
            SectionCard("模式") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(FridgeMode.COLD to "制冷", FridgeMode.HOT to "恒温暖饮 50°C").forEach { (m, text) ->
                        val selected = mode == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) C.chipBgActive else C.chipBg)
                                .clickable { mode = m },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = text,
                                color = if (selected) C.accent else C.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (mode == FridgeMode.COLD) {
                Spacer(Modifier.height(12.dp))
                SectionCard("制冷温度") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0, 4, 8, 12).forEach { t ->
                            val selected = temp == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) C.chipBgActive else C.chipBg)
                                    .clickable { temp = t },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$t°",
                                    color = if (selected) C.accent else C.textSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("工作强度") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(FridgeStyle.NORMAL to "标准", FridgeStyle.TURBO to "急速").forEach { (s, text) ->
                        val selected = style == s
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) C.chipBgActive else C.chipBg)
                                .clickable { style = s },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = text,
                                color = if (selected) C.accent else C.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("离车继续工作") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("离车保持", color = C.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    listOf(true to "开", false to "关").forEach { (value, text) ->
                        val selected = parkEnable == value
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .width(56.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) C.chipBgActive else C.chipBg)
                                .clickable { parkEnable = value },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = text,
                                color = if (selected) C.accent else C.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                if (parkEnable) {
                    Spacer(Modifier.height(12.dp))
                    Text("保持时长", color = C.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(1, 2, 4, 8).forEach { h ->
                            val selected = durationHours == h
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) C.chipBgActive else C.chipBg)
                                    .clickable { durationHours = h },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${h}小时",
                                    color = if (selected) C.accent else C.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("重复", color = C.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("1" to "单次", "2" to "每天").forEach { (value, text) ->
                            val selected = cycles == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) C.chipBgActive else C.chipBg)
                                    .clickable { cycles = value },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = text,
                                    color = if (selected) C.accent else C.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.accent)
                    .clickable(enabled = !busy) {
                        onControlFridge(
                            FridgeControlCommand(
                                enable = true,
                                mode = mode,
                                temp = if (mode == FridgeMode.HOT) 50 else temp,
                                style = style,
                                parkEnable = parkEnable,
                                durationSeconds = durationHours * 3600,
                                cycles = cycles,
                                value = "false",
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (busy) "下发中…" else "保存并下发",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.accent)
                    .clickable(enabled = !busy) {
                        onControlFridge(
                            FridgeControlCommand(
                                enable = false,
                                mode = mode,
                                temp = temp,
                                style = style,
                                value = "false",
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (busy) "下发中…" else "关闭冰箱",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// 空调完整面板：开关、调温、除霜 / 极速 / 除味、风量、内外循环
// ---------------------------------------------------------------------------

@Composable
fun ClimateScreen(
    onBack: () -> Unit,
    status: CarStatus?,
    busy: Boolean,
    onCommand: (String) -> Unit,
    onTempChange: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    val acOn = status?.acOn == true
    val target = status?.acTargetTemp?.toIntOrNull() ?: 24

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SubScreenTopBar("空调", onBack)
        Spacer(Modifier.height(8.dp))

        SectionCard("开关与温度") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = C.iconBg,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy) {
                            onCommand(if (acOn) "acOff" else "acOn")
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AcUnit,
                            contentDescription = if (acOn) "关闭空调" else "开启空调",
                            tint = if (acOn) C.accent else C.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (acOn) "空调已开启" else "空调已关闭",
                    color = C.textPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "−",
                    color = C.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !busy) { onTempChange((target - 1).coerceIn(16, 32)) }
                        .padding(horizontal = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${target}°C",
                    color = C.textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "+",
                    color = C.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !busy) { onTempChange((target + 1).coerceIn(16, 32)) }
                        .padding(horizontal = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "车内温度 ${status?.interiorTemp ?: "--"}°C",
                color = C.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("快捷模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "defrost" to "除雾",
                    "quickCool" to "极速降温",
                    "quickHeat" to "极速升温",
                    "deodorize" to "快速除味",
                ).forEach { (cmd, text) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.chipBg)
                            .clickable(enabled = !busy) { onCommand(cmd) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = text, color = C.textPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("风量") {
            val current = status?.acWindLevel?.toIntOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { level ->
                    val selected = current == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) C.chipBgActive else C.chipBg)
                            .clickable(enabled = !busy) { onTempChange(target) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$level",
                            color = if (selected) C.accent else C.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Text(
                text = "当前风量 ${status?.acWindLevel ?: "--"}",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("内外循环") {
            val circleIn = status?.acCircle?.contains("内") == true
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(true to "内循环", false to "外循环").forEach { (value, text) ->
                    val selected = if (value) circleIn else status?.acCircle?.contains("外") == true
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) C.chipBgActive else C.chipBg)
                            .clickable(enabled = !busy) {
                                onCommand(if (value) "acOn" else "acOn")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            color = if (selected) C.accent else C.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 首页半宽卡片：座椅舒适入口。 */
@Composable
fun SeatComfortTile(
    modifier: Modifier = Modifier,
    status: CarStatus?,
    onClick: () -> Unit,
) {
    ComfortEntryTile(
        modifier = modifier,
        title = "座椅舒适",
        subtitle = seatSummary(status),
        icon = { Icon(Icons.Filled.Weekend, null, tint = it) },
        onClick = onClick,
    )
}

/** 首页半宽卡片：车载冰箱入口。 */
@Composable
fun FridgeTile(
    modifier: Modifier = Modifier,
    status: CarStatus?,
    onClick: () -> Unit,
) {
    ComfortEntryTile(
        modifier = modifier,
        title = "车载冰箱",
        subtitle = fridgeSummary(status),
        icon = { Icon(Icons.Filled.Kitchen, null, tint = it) },
        onClick = onClick,
    )
}

/** 首页半宽卡片：通风入口（与座椅菜单分开，直接给通风入口）。 */
@Composable
fun VentilationTile(
    modifier: Modifier = Modifier,
    status: CarStatus?,
    onClick: () -> Unit,
) {
    ComfortEntryTile(
        modifier = modifier,
        title = "座椅通风",
        subtitle = "主驾 ${levelText(status?.seatVentilation?.driver)}",
        icon = { Icon(Icons.Filled.Air, null, tint = it) },
        onClick = onClick,
    )
}

private fun levelText(level: Int?): String = when (level) {
    null -> "--"
    0 -> "关"
    else -> "${level}档"
}

private fun seatSummary(status: CarStatus?): String {
    val heat = status?.seatHeating
    return "加热 主驾${levelText(heat?.driver)}"
}

private fun fridgeSummary(status: CarStatus?): String {
    val f = status?.fridge
    if (f == null) return "状态未知"
    val state = when (f.enabled) {
        true -> "工作中"
        false -> "已关闭"
        null -> "状态未知"
    }
    val temp = f.temp?.let { " ${it}°C" } ?: ""
    return "$state$temp"
}

/** 入口卡片：标题 + 副标题 + 右侧圆形图标。 */
@Composable
private fun ComfortEntryTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .background(C.cardBg, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, color = C.textSecondary, fontSize = 12.sp)
        }
        Surface(shape = CircleShape, color = C.iconBg, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                icon(C.accent)
            }
        }
    }
}

/** 供外部复用的通用内边距。 */
val ComfortContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
