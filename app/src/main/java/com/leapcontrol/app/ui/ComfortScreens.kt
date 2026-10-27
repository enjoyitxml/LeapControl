package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import com.leapcontrol.app.FridgeControlCommand
import com.leapcontrol.app.FridgeMode
import com.leapcontrol.app.FridgeStyle
import com.leapcontrol.app.VehicleCapabilities

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
            .background(C.panelBg, shape)
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
    /** 车辆实际具备的座椅/方向盘/后视镜配置；未识别到时整页按「不支持」处理，避免给出空面板。 */
    caps: VehicleCapabilities = VehicleCapabilities.EMPTY,
) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
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

        // 车顶视图 + 悬浮座椅按钮：点一下循环档位，直观操作；下方再给精确档位列表
        CabinSeatPanel(status = status, busy = busy, caps = caps, onCommand = onCommand)
        Spacer(Modifier.height(12.dp))

        val heat = status?.seatHeating
        val heatLevels = listOf("关", "1档", "2档", "3档")
        // 只渲染车上真有的座位：二排没加热就只给前排两行
        if (caps.seatHeating.hasFront) {
            SectionCard("座椅加热") {
                if (caps.seatHeating.driver) {
                    LevelRow("主驾", heat?.driver, heatLevels, busy, "driverSeatHeating_", onCommand)
                }
                if (caps.seatHeating.passenger) {
                    LevelRow("副驾", heat?.passenger, heatLevels, busy, "passengerSeatHeating_", onCommand)
                }
                if (caps.seatHeating.leftRear) {
                    LevelRow("左后", heat?.leftRear, heatLevels, busy, "leftRearSeatHeating_", onCommand)
                }
                if (caps.seatHeating.rightRear) {
                    LevelRow("右后", heat?.rightRear, heatLevels, busy, "rightRearSeatHeating_", onCommand)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val vent = status?.seatVentilation
        if (caps.seatVentilation.hasFront) {
            SectionCard("座椅通风") {
                if (caps.seatVentilation.driver) {
                    LevelRow("主驾", vent?.driver, heatLevels, busy, "driverSeatVentilation_", onCommand)
                }
                if (caps.seatVentilation.passenger) {
                    LevelRow("副驾", vent?.passenger, heatLevels, busy, "passengerSeatVentilation_", onCommand)
                }
                if (caps.seatVentilation.leftRear) {
                    LevelRow("左后", vent?.leftRear, heatLevels, busy, "leftRearSeatVentilation_", onCommand)
                }
                if (caps.seatVentilation.rightRear) {
                    LevelRow("右后", vent?.rightRear, heatLevels, busy, "rightRearSeatVentilation_", onCommand)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (caps.steeringWheelHeating) {
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
        }

        if (caps.mirrorHeating) {
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
            .statusBarsPadding()
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
// 空调二级页（车内温控）：车顶视图 + 座椅加热/通风悬浮按钮、调温卡、快捷宫格、定时空调入口
// ---------------------------------------------------------------------------

@Composable
fun ClimateScreen(
    onBack: () -> Unit,
    status: CarStatus?,
    busy: Boolean,
    onCommand: (String) -> Unit,
    onTempChange: (Int) -> Unit,
    /** 「开启空调」指令在途。 */
    busyOn: Boolean = false,
    /** 「关闭空调」指令在途。 */
    busyOff: Boolean = false,
    onOpenScheduledAc: () -> Unit = {},
) {
    val C = LocalAppColors.current
    val acOn = status?.acOn
    val target = status?.acTargetTemp?.toIntOrNull() ?: 24

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 开了 enableEdgeToEdge 且 Scaffold inset 被清零，各页需自己避让状态栏，
            // 否则顶栏会顶到通知栏下面
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 顶栏：返回 + 居中标题
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = C.textPrimary)
            }
            Text(
                text = "车内温控",
                color = C.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 车内温度条（实时车况）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(C.chipBg)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "车内温度 ${status?.interiorTemp ?: "--"}°C",
                color = C.textPrimary,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        ClimateTempCard(target = target, busy = busy, onTempChange = onTempChange)

        Spacer(Modifier.height(12.dp))
        // 空调电源：显式「开启 / 关闭」两个按钮，绝不按推断状态取反。
        // 与修复「只能开不能关」的原因一致：部分车型 acOn 读不到（null），
        // 任何 `if (acOn == true) 关 else 开` 的写法都会把每次点击判成“当前关闭”而重复下发开启指令。
        ClimatePowerRow(
            acOn = acOn,
            busyOn = busyOn,
            busyOff = busyOff,
            onAcOn = { onCommand("acOn") },
            onAcOff = { onCommand("acOff") },
        )

        Spacer(Modifier.height(12.dp))
        ClimateActionGrid(busy = busy, onCommand = onCommand)

        Spacer(Modifier.height(14.dp))
        // 定时空调入口（居中文字链）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenScheduledAc)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "定时空调 ›",
                color = C.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(8.dp))
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
                                onCommand(if (value) "circleIn" else "circleOut")
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

/** 座椅控制按钮在车顶视图中的锚点：x / y 均为面板宽高的比例。 */
private val STEERING_ANCHOR = 0.34f to 0.17f
private val FRONT_LEFT_ANCHOR = 0.29f to 0.36f
private val FRONT_RIGHT_ANCHOR = 0.71f to 0.36f
private val REAR_LEFT_ANCHOR = 0.30f to 0.72f
private val REAR_RIGHT_ANCHOR = 0.70f to 0.72f

/** 车顶视图 + 座椅加热/通风/方向盘加热悬浮按钮（只渲染本车实际支持的座位）。 */
@Composable
private fun CabinSeatPanel(
    status: CarStatus?,
    busy: Boolean,
    caps: VehicleCapabilities,
    onCommand: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val heat = status?.seatHeating
    val vent = status?.seatVentilation
    val steering = status?.steeringWheelHeating

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(24.dp))
            .background(C.cardBg.copy(alpha = 0.45f)),
    ) {
        CabinInteriorCanvas(fill = C.cardBg, stroke = C.textSecondary)

        // 方向盘加热（0 关 / 1 弱 / 2 强，点击循环）
        if (caps.steeringWheelHeating) {
            SeatChip(
                active = (steering ?: 0) > 0,
                level = steering,
                enabled = !busy,
                modifier = Modifier.offset(
                    x = maxWidth * STEERING_ANCHOR.first - 23.dp,
                    y = maxHeight * STEERING_ANCHOR.second - 23.dp,
                ),
                icon = { tint -> SteeringWheelIcon(tint, Modifier.size(24.dp)) },
            ) { onCommand("steeringWheelHeating_${((steering ?: 0) + 1) % 3}") }
        }

        // 主驾 / 副驾：加热 + 通风两个按钮并排
        if (caps.seatHeating.driver || caps.seatVentilation.driver) {
            SeatChipPair(
                heatLevel = heat?.driver,
                ventLevel = vent?.driver,
                heatSupported = caps.seatHeating.driver,
                ventSupported = caps.seatVentilation.driver,
                busy = busy,
                onHeat = { onCommand("driverSeatHeating_$it") },
                onVent = { onCommand("driverSeatVentilation_$it") },
                modifier = Modifier.offset(
                    x = maxWidth * FRONT_LEFT_ANCHOR.first - 50.dp,
                    y = maxHeight * FRONT_LEFT_ANCHOR.second - 23.dp,
                ),
            )
        }
        if (caps.seatHeating.passenger || caps.seatVentilation.passenger) {
            SeatChipPair(
                heatLevel = heat?.passenger,
                ventLevel = vent?.passenger,
                heatSupported = caps.seatHeating.passenger,
                ventSupported = caps.seatVentilation.passenger,
                busy = busy,
                onHeat = { onCommand("passengerSeatHeating_$it") },
                onVent = { onCommand("passengerSeatVentilation_$it") },
                modifier = Modifier.offset(
                    x = maxWidth * FRONT_RIGHT_ANCHOR.first - 50.dp,
                    y = maxHeight * FRONT_RIGHT_ANCHOR.second - 23.dp,
                ),
            )
        }

        // 左后 / 右后加热
        if (caps.seatHeating.leftRear) {
            SeatChip(
                active = (heat?.leftRear ?: 0) > 0,
                level = heat?.leftRear,
                enabled = !busy,
                modifier = Modifier.offset(
                    x = maxWidth * REAR_LEFT_ANCHOR.first - 23.dp,
                    y = maxHeight * REAR_LEFT_ANCHOR.second - 23.dp,
                ),
                icon = { tint -> Icon(Icons.Filled.Waves, "左后座椅加热", tint = tint, modifier = Modifier.size(22.dp)) },
            ) { onCommand("leftRearSeatHeating_${((heat?.leftRear ?: 0) + 1) % 4}") }
        }
        if (caps.seatHeating.rightRear) {
            SeatChip(
                active = (heat?.rightRear ?: 0) > 0,
                level = heat?.rightRear,
                enabled = !busy,
                modifier = Modifier.offset(
                    x = maxWidth * REAR_RIGHT_ANCHOR.first - 23.dp,
                    y = maxHeight * REAR_RIGHT_ANCHOR.second - 23.dp,
                ),
                icon = { tint -> Icon(Icons.Filled.Waves, "右后座椅加热", tint = tint, modifier = Modifier.size(22.dp)) },
            ) { onCommand("rightRearSeatHeating_${((heat?.rightRear ?: 0) + 1) % 4}") }
        }
    }
}

/**
 * 车内俯视示意图（车头朝上）：方向盘、前后排座椅、中央通道。
 * 浅色描边 + 卡片色填充，跟随深浅色主题。
 */
@Composable
private fun CabinInteriorCanvas(fill: Color, stroke: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun rx(v: Float) = v / 100f * w
        fun ry(v: Float) = v / 140f * h
        fun rw(v: Float) = v / 100f * w
        val soft = stroke.copy(alpha = 0.32f)
        val softFill = stroke.copy(alpha = 0.10f)
        val lineW = rw(1.2f)

        // 车身轮廓
        drawRoundRect(
            color = soft,
            topLeft = Offset(rx(6f), ry(2f)),
            size = Size(rw(88f), ry(138f) - ry(2f)),
            cornerRadius = CornerRadius(rw(24f), rw(24f)),
            style = Stroke(lineW),
        )
        // 仪表台
        drawRoundRect(
            color = softFill,
            topLeft = Offset(rx(14f), ry(20f)),
            size = Size(rw(72f), ry(27f) - ry(20f)),
            cornerRadius = CornerRadius(rw(4f), rw(4f)),
        )
        // 方向盘
        drawCircle(
            color = soft,
            radius = rw(5f),
            center = Offset(rx(34f), ry(23f)),
            style = Stroke(rw(1.6f)),
        )
        // 前排座椅（靠背 + 坐垫）
        listOf(15f, 58f).forEach { x ->
            drawRoundRect(
                color = fill,
                topLeft = Offset(rx(x), ry(38f)),
                size = Size(rw(27f), ry(74f) - ry(38f)),
                cornerRadius = CornerRadius(rw(9f), rw(9f)),
            )
            drawRoundRect(
                color = soft,
                topLeft = Offset(rx(x), ry(38f)),
                size = Size(rw(27f), ry(74f) - ry(38f)),
                cornerRadius = CornerRadius(rw(9f), rw(9f)),
                style = Stroke(lineW * 0.8f),
            )
            // 头枕
            drawRoundRect(
                color = fill,
                topLeft = Offset(rx(x + 6f), ry(31f)),
                size = Size(rw(15f), ry(37f) - ry(31f)),
                cornerRadius = CornerRadius(rw(4f), rw(4f)),
            )
        }
        // 中央通道
        drawRoundRect(
            color = softFill,
            topLeft = Offset(rx(46f), ry(42f)),
            size = Size(rw(8f), ry(70f) - ry(42f)),
            cornerRadius = CornerRadius(rw(3f), rw(3f)),
        )
        // 后排头枕
        listOf(23f, 62f).forEach { x ->
            drawRoundRect(
                color = fill,
                topLeft = Offset(rx(x), ry(85f)),
                size = Size(rw(15f), ry(92f) - ry(85f)),
                cornerRadius = CornerRadius(rw(4f), rw(4f)),
            )
        }
        // 后排座椅（联排 + 中间缝线）
        drawRoundRect(
            color = fill,
            topLeft = Offset(rx(14f), ry(93f)),
            size = Size(rw(72f), ry(122f) - ry(93f)),
            cornerRadius = CornerRadius(rw(10f), rw(10f)),
        )
        drawRoundRect(
            color = soft,
            topLeft = Offset(rx(14f), ry(93f)),
            size = Size(rw(72f), ry(122f) - ry(93f)),
            cornerRadius = CornerRadius(rw(10f), rw(10f)),
            style = Stroke(lineW * 0.8f),
        )
        drawLine(
            color = soft,
            start = Offset(rx(50f), ry(95f)),
            end = Offset(rx(50f), ry(120f)),
            strokeWidth = lineW * 0.8f,
        )
    }
}

/** 座椅上的悬浮控制钮：激活高亮 + 当前档位角标。 */
@Composable
private fun SeatChip(
    active: Boolean,
    level: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .size(46.dp)
            .shadow(3.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) C.chipBgActive else C.chipBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(if (active) C.accent else C.textPrimary)
        if (level != null && level > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(15.dp)
                    .background(C.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$level",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 前排座椅的一对按钮：加热（波浪）+ 通风（风扇）。 */
@Composable
private fun SeatChipPair(
    heatLevel: Int?,
    ventLevel: Int?,
    heatSupported: Boolean,
    ventSupported: Boolean,
    busy: Boolean,
    onHeat: (Int) -> Unit,
    onVent: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (heatSupported) {
            SeatChip(
                active = (heatLevel ?: 0) > 0,
                level = heatLevel,
                enabled = !busy,
                icon = { tint -> Icon(Icons.Filled.Waves, "座椅加热", tint = tint, modifier = Modifier.size(22.dp)) },
            ) { onHeat(((heatLevel ?: 0) + 1) % 4) }
        }
        if (ventSupported) {
            SeatChip(
                active = (ventLevel ?: 0) > 0,
                level = ventLevel,
                enabled = !busy,
                icon = { tint -> Icon(Icons.Filled.Toys, "座椅通风", tint = tint, modifier = Modifier.size(22.dp)) },
            ) { onVent(((ventLevel ?: 0) + 1) % 4) }
        }
    }
}

/** 方向盘图标（material 图标集中没有现成的，用 Canvas 画）。 */
@Composable
private fun SteeringWheelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val sw = size.minDimension * 0.09f
        drawCircle(color = tint, radius = r * 0.9f, center = c, style = Stroke(sw))
        listOf(90f, 210f, 330f).forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            drawLine(
                color = tint,
                start = c,
                end = Offset(
                    c.x + (r * 0.9f * cos(rad)).toFloat(),
                    c.y + (r * 0.9f * sin(rad)).toFloat(),
                ),
                strokeWidth = sw,
            )
        }
        drawCircle(color = tint, radius = r * 0.2f, center = c)
    }
}

/** 风挡除霜图标：风挡轮廓 + 三条热浪线。 */
@Composable
private fun WindshieldDefrostIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.07f
        val outline = Path().apply {
            moveTo(w * 0.20f, h * 0.86f)
            lineTo(w * 0.08f, h * 0.32f)
            quadraticBezierTo(w * 0.50f, h * 0.12f, w * 0.92f, h * 0.32f)
            lineTo(w * 0.80f, h * 0.86f)
            quadraticBezierTo(w * 0.50f, h * 0.72f, w * 0.20f, h * 0.86f)
            close()
        }
        drawPath(outline, color = tint, style = Stroke(sw))
        (0..2).forEach { i ->
            val x = w * (0.36f + i * 0.14f)
            drawLine(
                color = tint,
                start = Offset(x, h * 0.40f),
                end = Offset(x - w * 0.09f, h * 0.66f),
                strokeWidth = sw * 0.85f,
            )
        }
    }
}

/** 调温卡：− / 大号温度 / + */
@Composable
private fun ClimateTempCard(target: Int, busy: Boolean, onTempChange: (Int) -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.panelBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TempPill("−", busy) { onTempChange((target - 1).coerceIn(16, 32)) }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$target",
                color = C.textPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "°C",
                color = C.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        TempPill("+", busy) { onTempChange((target + 1).coerceIn(16, 32)) }
    }
}

@Composable
private fun TempPill(symbol: String, busy: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(C.chipBg)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, color = C.textPrimary, fontSize = 22.sp)
    }
}

/**
 * 空调电源：两个**显式**按钮——点「开启空调」固定发 acOn，点「关闭空调」固定发 acOff。
 * 上方一行显示本次刷新实际拉取到的车况（运行中 / 已关闭 / 未知）。
 */
@Composable
private fun ClimatePowerRow(
    acOn: Boolean?,
    busyOn: Boolean,
    busyOff: Boolean,
    onAcOn: () -> Unit,
    onAcOff: () -> Unit,
) {
    val C = LocalAppColors.current
    SectionCard("空调电源") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (acOn) {
                    true -> "空调运行中"
                    false -> "空调已关闭"
                    null -> "空调状态未知"
                },
                color = if (acOn == true) C.accent else C.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (busyOn || busyOff) {
                Text(
                    text = if (busyOn) "正在开启…" else "正在关闭…",
                    color = C.accent,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClimatePowerButton(
                text = if (busyOn) "开启中…" else "开启空调",
                highlight = acOn == true,
                enabled = !busyOn && !busyOff,
                modifier = Modifier.weight(1f),
                onClick = onAcOn,
            )
            ClimatePowerButton(
                text = if (busyOff) "关闭中…" else "关闭空调",
                highlight = acOn == false,
                enabled = !busyOn && !busyOff,
                modifier = Modifier.weight(1f),
                onClick = onAcOff,
            )
        }
    }
}

@Composable
private fun ClimatePowerButton(
    text: String,
    highlight: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) C.chipBgActive else C.chipBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (highlight) C.accent else C.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 快捷模式宫格（2 列）：极速降温 / 极速升温 / 快速除味 / 风挡除霜。 */
private data class ClimateGridItem(
    val label: String,
    val active: Boolean,
    val icon: @Composable (Color) -> Unit,
    val onClick: () -> Unit,
)

@Composable
private fun ClimateActionGrid(busy: Boolean, onCommand: (String) -> Unit) {
    val items = listOf(
        ClimateGridItem(
            label = "极速降温",
            active = false,
            icon = { tint -> Icon(Icons.Filled.AcUnit, null, tint = tint, modifier = Modifier.size(26.dp)) },
        ) { onCommand("quickCool") },
        ClimateGridItem(
            label = "极速升温",
            active = false,
            icon = { tint -> Icon(Icons.Filled.WbSunny, null, tint = tint, modifier = Modifier.size(26.dp)) },
        ) { onCommand("quickHeat") },
        ClimateGridItem(
            label = "快速除味",
            active = false,
            icon = { tint -> Icon(Icons.Filled.Air, null, tint = tint, modifier = Modifier.size(26.dp)) },
        ) { onCommand("deodorize") },
        ClimateGridItem(
            label = "风挡除霜",
            active = false,
            icon = { tint -> WindshieldDefrostIcon(tint, Modifier.size(26.dp)) },
        ) { onCommand("defrost") },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { item -> ClimateGridCell(item, busy, Modifier.weight(1f)) }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ClimateGridCell(item: ClimateGridItem, busy: Boolean, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(C.panelBg)
            .clickable(enabled = !busy, onClick = item.onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item.icon(if (item.active) C.accent else C.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            color = if (item.active) C.accent else C.textPrimary,
            fontSize = 12.sp,
        )
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

/** 座椅卡片副标题：把主驾的加热 / 通风档位一起带出来（没有通风能力的车只显示加热）。 */
private fun seatSummary(status: CarStatus?): String {
    val heat = levelText(status?.seatHeating?.driver)
    val vent = status?.seatVentilation?.driver
    return if (vent == null) "主驾 加热$heat" else "主驾 加热$heat · 通风${levelText(vent)}"
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
            // 与首页其它模块卡一致：带一层轻投影，和旁边的空调卡同处一行时观感统一
            .shadow(4.dp, shape)
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
