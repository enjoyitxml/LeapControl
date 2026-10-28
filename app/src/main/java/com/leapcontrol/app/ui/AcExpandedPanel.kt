package com.leapcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.Commands
import com.leapcontrol.app.VehicleCapabilities
import kotlin.math.roundToInt

/**
 * 空调卡片向下展开的「详细设置」面板。
 *
 * 版面自上而下：状态头（开关）→ 设定温度 → 四个快捷场景 → 除味 / 定时空调 →
 * 风量滑杆 → 「展开更多设置」→（点开）出风方向 + 座椅与座舱舒适。
 *
 * ## 与卡片的分工
 * 温度只在卡片上加减（草稿状态由 HomeScreen 持有），本面板只**显示**同一个值，
 * 再加一个加减号入口，避免两处各维护一份草稿。
 *
 * ## 为什么参数调整要发「整组」acSetting
 * 车端没有「只调风量」这种单字段指令，每次调空调都得把 operate / temperature /
 * windlevel / mode / circle / wshld / position 一起下发 —— 漏掉的字段车端会按默认值
 * 重置（比如调风量把温度带回 24℃）。所以这里把所有调整都交给
 * [Commands.acSettingName]，把「当前值」原样带上，只改要改的那一项。
 *
 * ## 硬件门禁
 * 座椅加热 / 通风按座位判断，方向盘与后视镜加热单独判断，全部来自
 * [VehicleCapabilities]；一项都没有时整块「座椅与座舱舒适」直接不渲染。
 */
@Composable
fun AcExpandedPanel(
    status: CarStatus?,
    caps: VehicleCapabilities,
    busy: Boolean,
    targetTemp: Int,
    /** 点设定温度加减号（±1℃）。 */
    onTempStep: (Int) -> Unit,
    /** 点右上开关：true = 开空调。 */
    onPower: (Boolean) -> Unit,
    /** 其它一切指令（场景 / 风量 / 循环 / 出风方向 / 座椅…）都走这里，参数是命令名。 */
    onCommand: (String) -> Unit,
    onOpenScheduledAc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current

    val acOn = status?.acOn == true
    val indoor = normalizeTemp(status?.interiorTemp)

    // 车端当前值：面板上所有「调一项、其余保持不变」的指令都拿它做底
    val remoteWind = status?.acWindLevel?.trim()?.toIntOrNull()
        ?.coerceIn(Commands.MIN_AC_WIND_LEVEL, Commands.MAX_AC_WIND_LEVEL)
        ?: COMMANDS_DEFAULT_WIND
    val remotePosition = status?.acPosition ?: "all"
    val mode = status?.acMode ?: "nohotcold"
    val circle = if (status?.acCircle?.contains("外") == true) "out" else "in"

    // 风量与出风方向的本地值：拖动的瞬间要跟手，指令回来再由车端真值覆盖。
    // remember(key) 让车端值一变就同步过来，拖动期间车端值不变所以不会被打断。
    var wind by remember(remoteWind) { mutableStateOf(remoteWind) }
    var position by remember(remotePosition) { mutableStateOf(remotePosition) }

    var showMore by remember { mutableStateOf(false) }

    /** 生成一条「只改一项」的空调指令名，其余字段沿用面板上的当前值。 */
    fun adjustName(
        op: String,
        windLevel: Int = wind,
        targetPosition: String = position,
        targetCircle: String = circle,
    ): String = Commands.acSettingName(
        op = op,
        temperature = targetTemp,
        windLevel = windLevel,
        mode = mode,
        circle = targetCircle,
        windshield = targetPosition == "wshld",
        position = targetPosition,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            // 与空调概览条同一套卡片底，展开后看起来是同一张卡续下来的一块
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // ---- 状态头：空调开关 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (acOn) "空调已开启" else "空调已关闭",
                    color = C.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "车内 $indoor℃",
                    color = C.textSecondary,
                    fontSize = 11.5.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(C.accent.copy(alpha = if (acOn) 0.16f else 0.12f))
                    .clickable(enabled = !busy) { onPower(!acOn) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (acOn) "关闭" else "开启",
                    color = if (busy) C.accent.copy(alpha = 0.5f) else C.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ---- 设定温度 ----
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(C.chipBg, RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(10.dp))
            TempStepButton(
                plus = false,
                enabled = !busy && AcTempDraft.canStep(targetTemp, -1),
                onClick = { onTempStep(-1) },
                touch = 36.dp,
                arm = 14.dp,
                label = "降低设定温度",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$targetTemp",
                        color = C.textPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        text = "°C",
                        color = C.textPrimary,
                        fontSize = 14.sp,
                        // 贴着数字顶端放（上标手感）：数字行高约 48dp，°C 行高约 17dp，
                        // 底部留 14dp 之后它的基线与大字的上三分之一齐平
                        modifier = Modifier.padding(bottom = 14.dp, start = 1.dp),
                    )
                }
                Text(
                    text = "设定温度",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }
            TempStepButton(
                plus = true,
                enabled = !busy && AcTempDraft.canStep(targetTemp, 1),
                onClick = { onTempStep(1) },
                touch = 36.dp,
                arm = 14.dp,
                label = "升高设定温度",
            )
            Spacer(Modifier.width(10.dp))
        }

        // ---- 快捷场景：两行、等宽 ----
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcTile(
                label = "极速降温",
                icon = Icons.Filled.AcUnit,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("quickCool") },
            )
            AcTile(
                label = "一键制热",
                icon = Icons.Filled.WbSunny,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("quickHeat") },
            )
            AcTile(
                label = "前挡除霜",
                icon = Icons.Filled.Air,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("defrost") },
            )
            AcTile(
                label = if (circle == "out") "内循环" else "外循环",
                icon = Icons.Filled.Refresh,
                modifier = Modifier.weight(1f),
                active = circle == "out",
                enabled = !busy,
                onClick = { onCommand(adjustName("circle", targetCircle = if (circle == "out") "in" else "out")) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcTile(
                label = "快速除味",
                icon = Icons.Filled.Waves,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = { onCommand("deodorize") },
            )
            AcTile(
                label = "定时空调",
                icon = Icons.Filled.Schedule,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = onOpenScheduledAc,
            )
        }

        // ---- 风量 ----
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "风量",
                color = C.textSecondary,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$wind 挡",
                color = C.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = wind.toFloat(),
            onValueChange = { v ->
                val next = v.roundToInt()
                    .coerceIn(Commands.MIN_AC_WIND_LEVEL, Commands.MAX_AC_WIND_LEVEL)
                if (next != wind) wind = next
            },
            valueRange = Commands.MIN_AC_WIND_LEVEL.toFloat()..Commands.MAX_AC_WIND_LEVEL.toFloat(),
            // 1..7 共 7 个值，中间 5 个刻度
            steps = Commands.MAX_AC_WIND_LEVEL - Commands.MIN_AC_WIND_LEVEL - 1,
            enabled = !busy,
            // 拖动过程中不发指令：手指还没离开就发，一次拖动会连发六七条。
            // 松手时才发一条，而且只在真的变了才发
            onValueChangeFinished = {
                if (wind != remoteWind) onCommand(adjustName("wind", windLevel = wind))
            },
            colors = SliderDefaults.colors(
                thumbColor = C.accent,
                activeTrackColor = C.accent,
                inactiveTrackColor = C.barTrack,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 更多设置开关 ----
        Spacer(Modifier.height(4.dp))
        val arrow by animateFloatAsState(if (showMore) 180f else 0f, label = "ac-more-arrow")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(C.chipBg, RoundedCornerShape(8.dp))
                .clickable { showMore = !showMore }
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showMore) "收起更多设置" else "展开更多设置",
                color = C.textSecondary,
                fontSize = 12.5.sp,
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = C.textSecondary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp)
                    .rotate(arrow),
            )
        }

        // ---- 更多设置 ----
        AnimatedVisibility(
            visible = showMore,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                // 出风方向
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "出风方向",
                        color = C.textSecondary,
                        fontSize = 12.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (position == "wshld") "前挡出风" else "全车出风",
                        color = C.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.chipBg, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf("all" to "全车出风", "wshld" to "前挡出风").forEach { (value, label) ->
                        SegmentCell(
                            label = label,
                            selected = position == value,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (position != value) {
                                position = value
                                onCommand(adjustName("position", targetPosition = value))
                            }
                        }
                    }
                }

                // 座椅与座舱舒适
                if (caps.hasSeatComfort) {
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Weekend,
                            contentDescription = null,
                            tint = C.accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "座椅与座舱舒适",
                            color = C.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    val driverHeating = status?.seatHeating?.driver
                    val driverVent = status?.seatVentilation?.driver
                    val passengerHeating = status?.seatHeating?.passenger
                    val passengerVent = status?.seatVentilation?.passenger
                    val hasDriver = caps.seatHeating.driver || caps.seatVentilation.driver
                    val hasPassenger = caps.seatHeating.passenger || caps.seatVentilation.passenger

                    if (hasDriver || hasPassenger) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasDriver) {
                                SeatColumn(
                                    title = "主驾驶位",
                                    heatingLevel = if (caps.seatHeating.driver) driverHeating else null,
                                    ventilationLevel = if (caps.seatVentilation.driver) driverVent else null,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                    onHeating = { lvl -> onCommand("driverSeatHeating_$lvl") },
                                    onVentilation = { lvl -> onCommand("driverSeatVentilation_$lvl") },
                                )
                            }
                            if (hasPassenger) {
                                SeatColumn(
                                    title = "副驾驶位",
                                    heatingLevel = if (caps.seatHeating.passenger) passengerHeating else null,
                                    ventilationLevel = if (caps.seatVentilation.passenger) passengerVent else null,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                    onHeating = { lvl -> onCommand("passengerSeatHeating_$lvl") },
                                    onVentilation = { lvl -> onCommand("passengerSeatVentilation_$lvl") },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (caps.steeringWheelHeating || caps.mirrorHeating) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (caps.steeringWheelHeating) {
                                val level = status?.steeringWheelHeating ?: 0
                                AcTile(
                                    label = "方向盘加热",
                                    subLabel = if (level > 0) "$level 档" else "关闭",
                                    icon = Icons.Filled.WbSunny,
                                    modifier = Modifier.weight(1f),
                                    active = level > 0,
                                    enabled = !busy,
                                    onClick = {
                                        // 0 关 → 1 弱 → 2 强 → 0 关
                                        onCommand("steeringWheelHeating_${(level + 1) % 3}")
                                    },
                                )
                            }
                            if (caps.mirrorHeating) {
                                val on = status?.rearviewMirrorHeating == true
                                AcTile(
                                    label = "后视镜加热",
                                    subLabel = if (on) "开启" else "关闭",
                                    icon = Icons.Filled.WbSunny,
                                    modifier = Modifier.weight(1f),
                                    active = on,
                                    enabled = !busy,
                                    onClick = {
                                        onCommand(if (on) "rearviewMirrorHeating_off" else "rearviewMirrorHeating_on")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 没读到车端风量时的默认挡位（与 [Commands.AC_ON_STATE] 的 2 挡一致）。 */
private const val COMMANDS_DEFAULT_WIND = 2

/**
 * 空调面板里的方形按钮：图标在上、文字在下，选中态用主题色。
 *
 * 不用 Material 的 Button：这里的按钮要能「一行平铺四个、文字不换行」，
 * 自带内边距的 Button 在 70dp 宽度里会把文字挤掉。
 */
@Composable
private fun AcTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /** 第二行小字（如「2 档」「关闭」）。null 表示不显示。 */
    subLabel: String? = null,
    /**
     * 未选中时的底色。默认 [AppColors.chipBg]，与面板上的其它淡底一致；
     * 嵌在「座位卡」里的按钮要显式传 [AppColors.panelBg]，否则与外层同色会糊成一片。
     */
    surface: Color? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val contentColor = when {
        !enabled -> C.textSecondary.copy(alpha = 0.45f)
        active -> C.accent
        else -> C.textPrimary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = when {
                    active -> C.chipBgActive
                    surface != null -> surface
                    else -> C.chipBg
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.5.sp,
            maxLines = 1,
        )
        if (subLabel != null) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = subLabel,
                color = if (active) C.accent else C.textSecondary,
                fontSize = 9.5.sp,
                maxLines = 1,
            )
        }
    }
}

/** 一个座位的两个按钮（加热 / 通风），档位 0-3 循环，0 为关闭。 */
@Composable
private fun SeatColumn(
    title: String,
    heatingLevel: Int?,
    ventilationLevel: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onHeating: (Int) -> Unit,
    onVentilation: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.chipBg, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        Text(
            text = title,
            color = C.textSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (heatingLevel != null) {
            AcTile(
                label = "座椅加热",
                subLabel = if (heatingLevel > 0) "$heatingLevel 档" else "关闭",
                icon = Icons.Filled.WbSunny,
                modifier = Modifier.fillMaxWidth(),
                surface = C.panelBg,
                active = heatingLevel > 0,
                enabled = enabled,
                // 0 → 1 → 2 → 3 → 0
                onClick = { onHeating((heatingLevel + 1) % 4) },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (ventilationLevel != null) {
            AcTile(
                label = "座椅通风",
                subLabel = if (ventilationLevel > 0) "$ventilationLevel 档" else "关闭",
                icon = Icons.Filled.AcUnit,
                modifier = Modifier.fillMaxWidth(),
                surface = C.panelBg,
                active = ventilationLevel > 0,
                enabled = enabled,
                onClick = { onVentilation((ventilationLevel + 1) % 4) },
            )
        }
    }
}

/** 分段控件的一格（出风方向：全车 / 前挡）。 */
@Composable
private fun SegmentCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            // 选中格用主题色淡底，在灰色轨道上一眼能看出当前是「全车」还是「前挡」
            .background(if (selected) C.chipBgActive else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> C.textSecondary.copy(alpha = 0.45f)
                selected -> C.accent
                else -> C.textSecondary
            },
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
