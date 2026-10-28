package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.ChargeSchedule
import com.leapcontrol.app.ChargeCenterDraft
import com.leapcontrol.app.ui.theme.statusGood
import com.leapcontrol.app.ui.theme.statusGoodDark

/**
 * 充电中心（二级页，首页「充电中心」胶囊进入）。
 *
 * 版式对齐官方 App：大字 SOC → 电池温度/充电功率/剩余时间三列 → 健康充电（含充电上限）
 * → 预约充电 → 电池预热 → 底部「保存并下发设置」。
 *
 * ## 为什么是「草稿 + 一次下发」
 * 三个开关心跳式下发会连打三条车控指令，网关很容易返回「系统繁忙」；而且 190 的
 * `chargesoc` 与健康充电的 `targetSoc` 是同一个数字，分开下发必然互相覆盖。
 * 所以页面上的改动只落到 [ChargeCenterDraft]，点保存时由
 * [com.leapcontrol.app.ui.AppRuntime.applyChargingSettings] 一次性、按顺序下发。
 *
 * ## 数据来源
 * - 电量 / 电池温度 / 功率 / 剩余时间 / 健康充电开关 → 车况信号（[CarStatus]）
 * - 预约充电的时段与重复日 → 云端 commonConfig 的充电计划
 * - 电池预热的时段与重复日 → `cmdid=161` 的 getappointment
 * 三者都在进入页面时回读，不用本地缓存冒充车端真值。
 */
@Composable
fun ChargingScreen(
    status: CarStatus?,
    draft: ChargeCenterDraft,
    onDraftChange: (ChargeCenterDraft) -> Unit,
    loading: Boolean,
    loadFailed: Boolean,
    saving: Boolean,
    gunBusy: Boolean,
    onGunAction: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenPreheat: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val soc = status?.soc

    val chargeState = status?.chargeState
    val charging = chargeState == 1 || (chargeState == null && status?.charging == true)
    val charged = chargeState == 2
    // 插着枪才显示「解锁充电枪 / 开始充电」——没插枪时这两颗按钮点了也是空转
    val gunConnected = when {
        chargeState != null -> chargeState != 0
        else -> status?.chargeCableConnected == true
    }

    // 功率 / 剩余时间：充电中才有意义，其余状态明确写「未充电」「已充满」，
    // 而不是留一个「--」让人以为信号丢了
    val idleText = if (charged) "已充满" else "未充电"
    val powerText = if (charging) status?.chargingPower ?: "充电中" else idleText
    val remainText = if (charging) status?.chargeTimeLeft ?: "--" else idleText
    val tempText = status?.batteryTemp ?: "--"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // MainActivity 开了 enableEdgeToEdge 且 Scaffold inset 被清零，各页自己避让系统栏
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        ChargeTopBar(title = "充电中心", onBack = onBack)

        // ---- 大字 SOC ----
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = soc?.toString() ?: "--",
                color = C.textPrimary,
                fontSize = 62.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 66.sp,
            )
            Text(
                text = "%",
                color = C.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp, start = 2.dp),
            )
        }

        // ---- 电池温度 / 充电功率 / 剩余时间 ----
        Spacer(Modifier.height(18.dp))
        ChargeCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChargeReading("电池温度", tempText, Modifier.weight(1f))
                ReadingDivider()
                ChargeReading("充电功率", powerText, Modifier.weight(1f), highlight = charging)
                ReadingDivider()
                ChargeReading("剩余时间", remainText, Modifier.weight(1.2f), highlight = charging)
            }
        }

        // ---- 插枪时的即时操作（开始 / 结束充电、解锁枪）----
        if (gunConnected) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChargeOutlineButton(
                    label = "解锁充电枪",
                    enabled = !gunBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onGunAction("unlock") },
                )
                ChargeSolidButton(
                    label = if (charging) "结束充电" else "开始充电",
                    enabled = !gunBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onGunAction(if (charging) "stop" else "start") },
                )
            }
        }

        // 回读失败的提示：车端值与本地草稿可能不一致，必须告诉用户
        if (loadFailed) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.panelBg)
                    .clickable(onClick = onRetryLoad)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "未能读取车端设置，当前显示为上次保存的值",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("重试", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ---- 健康充电 + 充电上限 ----
        Spacer(Modifier.height(14.dp))
        ChargeCard {
            ChargeSwitchRow(
                title = "健康充电",
                subtitle = null,
                checked = draft.healthy,
                onCheckedChange = { onDraftChange(draft.copy(healthy = it)) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "充电上限${draft.limitSoc}%",
                color = C.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            LimitSocSlider(
                soc = draft.limitSoc,
                onChange = { onDraftChange(draft.copy(limitSoc = it)) },
            )
        }

        // ---- 预约充电 ----
        Spacer(Modifier.height(12.dp))
        ChargeCard {
            ChargeSwitchRow(
                title = "预约充电",
                subtitle = "插枪后会根据设定时间充电，仅支持慢充",
                checked = draft.scheduledEnabled,
                onCheckedChange = { onDraftChange(draft.copy(scheduledEnabled = it)) },
            )
            if (draft.scheduledEnabled) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
                Spacer(Modifier.height(14.dp))
                ScheduleRow(
                    title = "时段",
                    value = ChargeSchedule.summaryText(
                        draft.scheduledStart,
                        draft.scheduledEnd,
                        draft.scheduledDays,
                    ),
                    onClick = onOpenScheduled,
                )
            }
        }

        // ---- 电池预热 ----
        Spacer(Modifier.height(12.dp))
        ChargeCard {
            ChargeSwitchRow(
                title = "电池预热",
                // 官方这一张卡只有标题与开关，说明文字留给三级页，避免整页文案过密
                subtitle = null,
                checked = draft.preheatEnabled,
                onCheckedChange = { onDraftChange(draft.copy(preheatEnabled = it)) },
            )
            if (draft.preheatEnabled) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.border))
                Spacer(Modifier.height(14.dp))
                ScheduleRow(
                    title = "时段",
                    value = "${ChargeSchedule.daysLabel(draft.preheatDays)} ${draft.preheatStart} 开始",
                    onClick = onOpenPreheat,
                )
            }
        }

        // ---- 保存并下发 ----
        Spacer(Modifier.height(20.dp))
        ChargeSaveButton(saving = saving, loading = loading, onClick = onSave)
        Spacer(Modifier.height(24.dp))
    }
}

/** 顶栏：左侧返回圆钮 + 居中标题（与空调 / 舒适等二级页同一套）。 */
@Composable
private fun ChargeTopBar(title: String, onBack: () -> Unit) {
    val C = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
                .clip(CircleShape)
                .background(C.panelBg)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            AcIcon(AcGlyph.ChevronLeft, C.textPrimary, size = 17.dp, stroke = 1.9.dp)
        }
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** 实心卡片。整页不画阴影：首页本身是渐变背景，再叠阴影会显脏。 */
@Composable
private fun ChargeCard(
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.panelBg, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

/** 读数单元：上方小字标签 + 下方数值。 */
@Composable
private fun ChargeReading(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val C = LocalAppColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text = label, color = C.textSecondary, fontSize = 12.sp)
        Text(
            text = value,
            color = if (highlight) (if (C.isDark) statusGoodDark else statusGood) else C.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 三列读数之间的浅竖线。 */
@Composable
private fun ReadingDivider() {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .background(C.border),
    )
}

/**
 * 开关行：左标题（可带一行说明）+ 右开关。
 *
 * 开关只改本地草稿，真正下发在「保存并下发设置」。
 */
@Composable
private fun ChargeSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = C.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (C.isDark) Color(0xFF8E9099) else Color.White,
                uncheckedTrackColor = if (C.isDark) Color(0xFF3A3E46) else Color(0xFFD8DCE2),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** 时段行：左「时段 / 具体时间」，右箭头，整行可点进入三级页。 */
@Composable
private fun ScheduleRow(title: String, value: String, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(5.dp))
            Text(text = value, color = C.textSecondary, fontSize = 12.5.sp)
        }
        AcIcon(AcGlyph.ChevronRight, C.textSecondary, size = 16.dp, stroke = 1.7.dp)
    }
}

/**
 * 充电上限滑杆（50%~100%，5% 一档）。
 *
 * 为什么不用 Material Slider：官方这一条要「绿色渐变轨道 + 分段白点 + 白色圆钮 +
 * 底部 90% 最佳限值三角」，用 Slider 只能改颜色，白点与三角都得另画一层，
 * 位置还容易和滑块的实际内边距错开。这里整条自绘，拖动与点击都用
 * [detectHorizontalDragGestures] / [detectTapGestures]，滑动条自身的可点性不打折。
 */
@Composable
private fun LimitSocSlider(soc: Int, onChange: (Int) -> Unit) {
    val C = LocalAppColors.current
    val density = LocalDensity.current
    val thumbRadius = 11.dp
    val fraction = ChargeSchedule.socToFraction(soc)
    val trackColor = C.barTrack
    val activeStart = if (C.isDark) Color(0xFF2E8B57) else Color(0xFF56C271)
    val activeEnd = if (C.isDark) Color(0xFF3DD68C) else Color(0xFF8FE0A4)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("50%", color = C.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val radius = thumbRadius.toPx()
                            val usable = (size.width - radius * 2).coerceAtLeast(1f)
                            onChange(ChargeSchedule.fractionToSoc((offset.x - radius) / usable))
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            val radius = thumbRadius.toPx()
                            val usable = (size.width - radius * 2).coerceAtLeast(1f)
                            onChange(ChargeSchedule.fractionToSoc((change.position.x - radius) / usable))
                        }
                    },
            ) {
                val usable = (maxWidth - thumbRadius * 2).coerceAtLeast(0.dp)
                val thumbCenterX = usable * fraction

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = thumbRadius.toPx()
                    val trackHeight = 6.dp.toPx()
                    val top = (size.height - trackHeight) / 2f
                    val trackStart = r
                    val trackWidth = (size.width - r * 2).coerceAtLeast(1f)

                    // 底层灰轨
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(trackStart, top),
                        size = Size(trackWidth, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f),
                    )
                    // 已选段：绿色渐变
                    val activeWidth = trackWidth * fraction
                    if (activeWidth > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(activeStart, activeEnd),
                                startX = trackStart,
                                endX = trackStart + activeWidth.coerceAtLeast(1f),
                            ),
                            topLeft = Offset(trackStart, top),
                            size = Size(activeWidth, trackHeight),
                            cornerRadius = CornerRadius(trackHeight / 2f),
                        )
                    }
                    // 分段白点（60/70/80/90）
                    val dotRadius = 1.6.dp.toPx()
                    for (step in 1..4) {
                        val x = trackStart + trackWidth * (step / 5f)
                        drawCircle(
                            color = Color.White.copy(alpha = if (x <= trackStart + activeWidth) 0.75f else 0.35f),
                            radius = dotRadius,
                            center = Offset(x, size.height / 2f),
                        )
                    }
                }

                // 白色圆钮：带投影与细描边，压在淡底上也能看清
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = thumbCenterX)
                        .size(thumbRadius * 2)
                        .shadow(3.dp, CircleShape, clip = false)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0x14000000), CircleShape),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("100%", color = C.textSecondary, fontSize = 12.sp)
        }

        // 90% 最佳限值：三角尖对准 90% 位置，文字居中于三角下方。
        // 文字宽度先量一次再定位，避免首帧从左边滑过来的跳动。
        Spacer(Modifier.height(2.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val textStyle = TextStyle(fontSize = 11.sp)
            val label = "最佳限值${ChargeSchedule.BEST_LIMIT_SOC}%"
            val labelWidthPx = remember(measurer, label) {
                measurer.measure(label, textStyle).size.width
            }
            val labelWidth = with(density) { labelWidthPx.toDp() }
            // 滑杆左右各留了 thumbRadius，这里按同一比例定位
            val trackWidth = maxWidth - thumbRadius * 2
            val centerX = thumbRadius + trackWidth * ChargeSchedule.socToFraction(ChargeSchedule.BEST_LIMIT_SOC)
            val labelOffset = (centerX - labelWidth / 2)
                .coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))

            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = centerX - 5.dp, y = 3.dp)
                    .size(10.dp, 5.dp),
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = C.textSecondary.copy(alpha = 0.55f))
            }
            Text(
                text = label,
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.offset(x = labelOffset, y = 11.dp),
            )
        }
    }
}

/** 线框按钮（解锁充电枪）。 */
@Composable
private fun ChargeOutlineButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.panelBg)
            .border(1.dp, C.border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) C.textPrimary else C.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 实心按钮（开始 / 结束充电）。 */
@Composable
private fun ChargeSolidButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val bg = if (C.isDark) Color(0xFFF2F2F4) else Color(0xFF1C1C1E)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (C.isDark) Color(0xFF16181C) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 底部「保存并下发设置」。 */
@Composable
private fun ChargeSaveButton(saving: Boolean, loading: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    val bg = if (C.isDark) Color(0xFFF2F2F4) else Color(0xFF1C1C1E)
    val fg = if (C.isDark) Color(0xFF16181C) else Color.White
    val enabled = !saving && !loading
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                saving -> "正在下发…"
                loading -> "正在读取车端设置…"
                else -> "保存并下发设置"
            },
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
