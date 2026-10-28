package com.leapcontrol.app.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R
import com.leapcontrol.app.bluetooth.BatteryOptimizationHelper
import com.leapcontrol.app.bluetooth.BleConnectionPhase
import com.leapcontrol.app.bluetooth.BleKeyService
import com.leapcontrol.app.bluetooth.BlePassiveConfiguration
import com.leapcontrol.app.bluetooth.BlePermissionPolicy
import com.leapcontrol.app.ui.theme.statusGood

/**
 * 「蓝牙钥匙」详情页。
 *
 * 结构与其它二级页一致（返回 + 分组卡片）。三块内容：
 *   1. 状态 —— 现在到底是「已认证在待命」「没连上」还是「没配对」，一句话说清；
 *   2. 无感解锁 —— 总开关 + 靠近解锁 / 离开落锁 / 车外按键，以及感应距离三档；
 *   3. 车辆配对 —— 没配对时在这里搜车、配对；已配对时给一个「重新配对」。
 *
 * 所有开关都是「整组下发」：车端只认完整的配置帧，所以每次改动都基于当前 key.desired
 * 复制一份再改一个字段（见 [BleKeyHost.applyConfiguration]）。
 */
@Composable
fun BluetoothKeyScreen(
    host: BleKeyHost,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val connection = host.connection
    val key = host.key

    // 权限：第一次点「搜索车辆」时才申请，跟着动作走，不在进页面时就弹窗
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val action = pendingAction
        pendingAction = null
        // 授权结果只在这里知道，宿主里的缓存要立刻刷新，否则紧接着的 startScan 仍会判「没权限」
        host.refreshEnvironment()
        if (grants.values.all { it }) {
            action?.invoke()
        } else {
            host.notice = "未授予附近设备权限，无法搜索车辆"
        }
    }
    val withPermission: (() -> Unit) -> Unit = { action ->
        if (host.hasPermissions) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(
                BlePermissionPolicy.requiredPermissions(Build.VERSION.SDK_INT).toTypedArray(),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "蓝牙钥匙", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 当前状态 ----
        StatusCard(host = host)
        Spacer(Modifier.height(14.dp))

        // ---- 无感解锁设置 ----
        SettingsSection(title = "无感解锁", desc = "开启后靠蓝牙感应，走到车边自动解锁") {
            SettingsCard {
                SwitchRow(
                    label = "蓝牙钥匙",
                    desc = when {
                        !host.bound -> "还没有配对车辆，先在下面完成配对"
                        key?.pending == true -> "设置已下发，等待车辆确认"
                        key?.desired?.enabled == true -> "已开启，后台保持连接中"
                        else -> "已关闭，仅保留配对记录"
                    },
                    checked = key?.desired?.enabled == true,
                    onCheckedChange = { on ->
                        if (!host.bound) {
                            host.notice = "请先配对车辆"
                        } else {
                            withPermission { host.toggleEnabled(on) }
                        }
                    },
                )

                if (host.bound) {
                    val desired = key?.desired ?: BlePassiveConfiguration()
                    val interactive = desired.enabled && !connection.isBusy
                    Spacer(Modifier.height(16.dp))
                    SwitchRow(
                        label = "靠近自动解锁",
                        desc = "带着手机走到车边，门把手自动弹出",
                        checked = desired.autoUnlock,
                        onCheckedChange = { on ->
                            if (!interactive) {
                                host.notice = if (desired.enabled) "请等待当前操作完成" else "先打开蓝牙钥匙总开关"
                            } else {
                                host.applyConfiguration(desired.copy(autoUnlock = on))
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    SwitchRow(
                        label = "离开自动落锁",
                        desc = "走远后自动上锁，不会忘了锁车",
                        checked = desired.autoLock,
                        onCheckedChange = { on ->
                            if (!interactive) {
                                host.notice = if (desired.enabled) "请等待当前操作完成" else "先打开蓝牙钥匙总开关"
                            } else {
                                host.applyConfiguration(desired.copy(autoLock = on))
                            }
                        },
                    )
                    if (supportsButton(key?.device?.protocolMinor, host.runtime.session.selectedCarType)) {
                        Spacer(Modifier.height(16.dp))
                        SwitchRow(
                            label = "车外按键解锁",
                            desc = "靠近时按门把手按键即可解锁",
                            checked = desired.buttonEnabled,
                            onCheckedChange = { on ->
                                if (!interactive) {
                                    host.notice = if (desired.enabled) "请等待当前操作完成" else "先打开蓝牙钥匙总开关"
                                } else {
                                    host.applyConfiguration(desired.copy(buttonEnabled = on))
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    CalibrationPicker(
                        current = desired.calibration,
                        isC16 = host.runtime.session.selectedCarType.orEmpty().uppercase()
                            .let { it.contains("C16") || it.contains("C10") },
                        enabled = interactive,
                        onPick = { calibration ->
                            if (!interactive) {
                                host.notice = if (desired.enabled) "请等待当前操作完成" else "先打开蓝牙钥匙总开关"
                            } else {
                                host.applyConfiguration(desired.copy(calibration = calibration))
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 车辆配对 ----
        SettingsSection(title = "车辆配对", desc = "首次使用需要与车辆配对一次") {
            SettingsCard {
                if (host.bound) {
                    val device = key?.device
                    Text(
                        text = device?.name?.takeIf { it.isNotBlank() } ?: "已配对车辆",
                        color = C.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "信号 ${
                            device?.rssi?.let { "$it dBm" } ?: "—"
                        } · ${connection.phaseLabel}",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                    val boundDetail = connection.detailMessage
                    if (boundDetail != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(boundDetail, color = C.textSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineAction(
                            text = if (connection.isBusy) "连接中…" else "重新连接",
                            enabled = !connection.isBusy && !host.backgroundRunning,
                            modifier = Modifier.weight(1f),
                        ) {
                            withPermission {
                                host.cancelScan()
                                host.reconnect()
                            }
                        }
                        OutlineAction(
                            text = "关闭后台钥匙",
                            enabled = host.backgroundRunning,
                            modifier = Modifier.weight(1f),
                        ) { host.stopBackground() }
                    }
                } else {
                    Text(
                        text = when {
                            host.syncing -> "正在同步钥匙材料…"
                            !host.certificateReady -> "钥匙材料未就绪"
                            connection.phase == BleConnectionPhase.SCANNING -> "正在搜索附近的车辆…"
                            else -> "把手机靠近车辆，然后开始搜索"
                        },
                        color = C.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "配对过程约 10 秒，需要在车旁、手机蓝牙已开启。",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledAction(
                            text = when {
                                host.syncing -> "同步中…"
                                connection.phase == BleConnectionPhase.SCANNING -> "搜索中…"
                                else -> "搜索附近车辆"
                            },
                            enabled = !host.syncing && !connection.isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            withPermission {
                                if (!host.certificateReady) host.syncCertificate() else host.startScan()
                            }
                        }
                        if (connection.phase == BleConnectionPhase.SCANNING) {
                            OutlineAction(text = "停止", modifier = Modifier.width(96.dp)) { host.cancelScan() }
                        }
                    }
                }

                // 扫描结果
                if (connection.devices.isNotEmpty() && !host.bound) {
                    Spacer(Modifier.height(14.dp))
                    connection.devices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !connection.isBusy) { host.connect(device) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phosphor_key),
                                contentDescription = null,
                                tint = C.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name.ifBlank { "零跑车辆" },
                                    color = C.textPrimary,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = "信号 ${device.rssi} dBm",
                                    color = C.textSecondary,
                                    fontSize = 10.5.sp,
                                )
                            }
                            Text("配对", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 保活说明 ----
        SettingsCard("后台保活") {
            Text(
                text = "开启后会在通知栏保留一条「蓝牙钥匙」常驻通知，用于维持与车辆的蓝牙连接。" +
                    "关掉这条通知不影响解锁，但系统可能更快回收连接。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "打开通知设置",
                color = C.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { BleKeyService.openNotificationSettings(host.context) },
            )
            Spacer(Modifier.height(12.dp))
            val batteryExempt = remember {
                BatteryOptimizationHelper.isIgnoringBatteryOptimizations(host.context)
            }
            Text(
                text = if (batteryExempt) {
                    "已加入电池优化白名单，后台连接不会被系统掐断。"
                } else {
                    "省电模式下系统会限制后台蓝牙，点这里把零控加入电池优化白名单。"
                },
                color = if (batteryExempt) C.textSecondary else C.accent,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.clickable(enabled = !batteryExempt) {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimization(host.context)
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 顶部状态卡：图标 + 一句状态 + 一句解释。 */
@Composable
private fun StatusCard(host: BleKeyHost) {
    val C = LocalAppColors.current
    val status = host.status
    val tint = when (status) {
        BleKeyStatus.ACTIVE -> statusGood
        BleKeyStatus.STANDBY -> C.accent
        else -> C.textSecondary
    }
    val title = when (status) {
        BleKeyStatus.ACTIVE -> "蓝牙钥匙已就绪"
        BleKeyStatus.STANDBY -> if (host.bound) "蓝牙钥匙未连接" else "尚未配对"
        BleKeyStatus.UNBOUND -> "尚未配对"
        BleKeyStatus.UNAVAILABLE -> "蓝牙不可用"
    }
    val detail = host.connection.detailMessage
    val desc = when {
        !host.hasPermissions -> "没有「附近设备」权限，点下面的按钮授权后即可搜索车辆"
        detail != null -> detail
        status == BleKeyStatus.ACTIVE -> "车端已认证，靠近即可解锁"
        host.bound -> "后台待命中，进入感应范围会自动连接"
        else -> "首次使用需要与车辆配对一次"
    }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (C.isDark) 0.20f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_key),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = C.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(desc, color = C.textSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
            if (host.syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.8.dp,
                    color = C.accent,
                )
            }
        }
        host.notice?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = C.textSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
    }
}

/** 感应距离三档：贴近 / 标准 / 较远。 */
@Composable
private fun CalibrationPicker(
    current: com.leapcontrol.app.bluetooth.BleCalibration,
    isC16: Boolean,
    enabled: Boolean,
    onPick: (com.leapcontrol.app.bluetooth.BleCalibration) -> Unit,
) {
    val C = LocalAppColors.current
    Text(
        text = "感应距离",
        color = C.textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(3.dp))
    Text(
        text = "决定离车多远开始解锁、走多远落锁。改了之后需要车端确认才生效。",
        color = C.textSecondary,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalibrationPreset.entries.forEach { preset ->
            val value = getPresetCalibration(preset, isC16)
            val selected = value == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) C.accent.copy(alpha = 0.12f) else C.iconBg)
                    .border(
                        width = if (selected) 1.5.dp else 0.dp,
                        color = if (selected) C.accent else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = enabled) { onPick(value) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = preset.label,
                    color = if (selected) C.accent else C.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 主按钮（实心主题色）。 */
@Composable
private fun FilledAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) C.accent else C.accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 次要按钮（描边）。 */
@Composable
private fun OutlineAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) C.textPrimary else C.textSecondary.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )
    }
}

/** 车外按键解锁只有新协议（minor ≥ 9）与 C10/C16 平台才有。 */
private fun supportsButton(protocolMinor: Int?, carType: String?): Boolean {
    if ((protocolMinor ?: 0) >= 9) return true
    val model = carType.orEmpty().uppercase()
    return model.contains("C16") || model.contains("C10")
}
