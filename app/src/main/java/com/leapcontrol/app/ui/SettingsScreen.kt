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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppearanceMode
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.R
import com.leapcontrol.app.tiles.QuickTiles
import kotlinx.coroutines.launch

/**
 * 「设置」页（从「我的」进入）：功能与账号相关项。
 *  - 自定义车名（仅本地保存，清空恢复官方昵称）
 *  - 控车密码（4 位，官方 App 里设置的操控密码）
 *  - 高德 Web Key（目的地搜索）
 *  - 刷新车模图片
 *  - 系统控制中心快捷磁贴
 *
 * 本文件同时提供设置类页面共用的卡片 / 开关行 / 选项行组件，
 * 供「个性化」「关于」两页复用。
 */
@Composable
fun SettingsScreen(
    runtime: AppRuntime,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var showRename by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showAmapKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmGate by remember { mutableStateOf(runtime.biometricConfirmEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "设置", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        SettingsCard {
            ClickRow(
                title = "自定义车名",
                desc = if (runtime.customName().isBlank()) {
                    "当前：${runtime.displayName()}（官方昵称）"
                } else {
                    "当前：${runtime.customName()}（仅保存在本机）"
                },
                iconRes = R.drawable.ic_edit,
            ) { showRename = true }
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "控车密码",
                desc = if (runtime.hasPin) "已保存 4 位操控密码，点击修改" else "未设置，点击设置",
                iconRes = R.drawable.ic_qc_lock,
            ) { showPin = true }
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "高德 Web Key",
                desc = if (runtime.hasAmapKey()) "已配置，可搜索目的地" else "未配置，无法搜索目的地",
                iconRes = R.drawable.ic_key,
            ) { showAmapKey = true }
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "刷新车模图片",
                desc = "清除缓存并从官方接口重新拉取",
                iconRes = R.drawable.ic_nav_car,
            ) {
                scope.launch {
                    runtime.clearCarImageCache()
                    val ok = runCatching { runtime.fetchCarImage(force = true) }.getOrNull() != null
                    message = if (ok) "车模已刷新" else "未取到官方车图，首页暂不显示车模"
                }
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                label = "指纹/密码二次确认",
                desc = "主界面下发控车指令前需验证，防误触（控制中心磁贴不受限）",
                checked = confirmGate,
                onCheckedChange = {
                    confirmGate = it
                    runtime.setBiometricConfirmEnabled(it)
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- 系统控制中心快捷磁贴 ----
        val context = androidx.compose.ui.platform.LocalContext.current
        var tileTick by remember { mutableStateOf(0) }
        val addedIds = remember(tileTick) {
            QuickTiles.ALL.filter { QuickTiles.isAdded(context, it) }.map { it.id }.toSet()
        }
        SettingsCard("系统控制中心") {
            Text(
                "点击图标即可添加到下拉控制中心，锁屏状态下也能直接控车",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))
            val rows = QuickTiles.ALL.chunked(4)
            rows.forEachIndexed { index, rowSpecs ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowSpecs.forEach { spec ->
                        val added = spec.id in addedIds
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { QuickTiles.requestAdd(context, spec) { tileTick++ } }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            if (added) C.iconBgActive else C.chipBg,
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(spec.iconRes),
                                        contentDescription = spec.title,
                                        tint = if (added) C.accent else C.textSecondary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                if (added) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = C.accent,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(Alignment.TopEnd),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                spec.title,
                                color = if (added) C.accent else C.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(4 - rowSpecs.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = C.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    // ---- 自定义车名 ----
    if (showRename) {
        var nameInput by remember { mutableStateOf(runtime.customName()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = C.panelBg,
            title = { Text("自定义车名", color = C.textPrimary) },
            text = {
                Column {
                    Text(
                        "车名只保存在本机；留空则恢复为零跑官方 App 的昵称。",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(12) },
                        placeholder = { Text("如：小7", color = C.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.textPrimary,
                            unfocusedTextColor = C.textPrimary,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runtime.setCustomName(nameInput)
                    showRename = false
                }) { Text("保存", color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消", color = C.textSecondary) }
            },
        )
    }

    // ---- 高德 Web Key ----
    if (showAmapKey) {
        var keyInput by remember { mutableStateOf(runtime.amapKey()) }
        AlertDialog(
            onDismissRequest = { showAmapKey = false },
            containerColor = C.panelBg,
            title = { Text("高德 Web Key", color = C.textPrimary) },
            text = {
                Column {
                    Text(
                        "用于搜索目的地并发送到车机。请在高德开放平台申请「Web服务」类型的 Key。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.textPrimary,
                            unfocusedTextColor = C.textPrimary,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runtime.setAmapKey(keyInput)
                    showAmapKey = false
                }) { Text("保存", color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showAmapKey = false }) { Text("取消", color = C.textSecondary) }
            },
        )
    }

    // ---- 控车密码 ----
    if (showPin) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPin = false },
            containerColor = C.panelBg,
            title = { Text("控车密码", color = C.textPrimary) },
            text = {
                Column {
                    Text(
                        "输入零跑官方 App 中设置的 4 位操控密码",
                        color = C.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.textPrimary,
                            unfocusedTextColor = C.textPrimary,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length == 4,
                    onClick = {
                        scope.launch {
                            val ok = runCatching { runtime.verifyAndSavePin(pin) }.getOrDefault(false)
                            message = if (ok) "控车密码已保存" else "密码校验失败"
                            showPin = false
                        }
                    },
                ) {
                    Text(
                        "确认",
                        color = if (pin.length == 4) C.accent else C.textSecondary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPin = false }) { Text("取消", color = C.textSecondary) }
            },
        )
    }
}

// ============================================================================
// 设置类页面共用组件（设置 / 个性化 / 关于）
// ============================================================================

/** 设置页顶栏：返回按钮 + 标题（标题与图标统一主色）。 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = C.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(title, color = C.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 带开关的设置行。 */
@Composable
fun SwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = C.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = C.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = C.barTrack,
            ),
        )
    }
}

/** 可点击的设置行：左图标（可选）+ 标题 + 说明，右箭头。 */
@Composable
fun ClickRow(
    title: String,
    desc: String,
    iconRes: Int? = null,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = C.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = C.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = C.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 设置卡片：带 title 时在顶部显示小标题，不带则直接放内容。实心面板 + 细描边，干净不透光。 */
@Composable
fun SettingsCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.panelBg, shape)
            .border(1.dp, C.border, shape)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** 分组标题：把设置项按类隔开。 */
@Composable
fun SettingsSection(
    title: String,
    desc: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** 单选选项行（外观模式等）。 */
@Composable
fun OptionRow(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.5.dp, if (selected) C.accent else C.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) C.accent else C.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = C.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/**
 * 季节主题选择网格（春 / 夏 / 秋 / 冬 / 星空）。
 *
 * @param seasonId 用户自己选的主题（夜间不生效时显示的就是它）
 * @param activeSeasonId 当前真实生效的主题：夜间自动切换顶上时，勾选落在星空上
 * @param autoNightActive 夜间自动主题是否正在生效，用于给出「明早恢复」提示
 */
@Composable
fun SeasonThemeGrid(
    runtime: AppRuntime,
    seasonId: String,
    activeSeasonId: String,
    autoNightActive: Boolean,
    onSeasonChange: (String) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeasonThemes.ALL.forEach { theme ->
            val selected = theme.id == activeSeasonId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        runtime.setSeasonThemeId(theme.id)
                        onSeasonChange(theme.id)
                    }
                    .padding(2.dp)
                    .weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(theme.gradient(C.isDark)))
                        .border(
                            if (selected) 2.dp else 0.5.dp,
                            if (selected) C.accent else C.border,
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, null, tint = C.accent, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    theme.label,
                    color = if (selected) C.accent else C.textPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
    if (autoNightActive) {
        Spacer(Modifier.height(10.dp))
        Text(
            "夜间主题「${SeasonThemes.NIGHT.label}」正在生效，明早 "
                + "${SeasonThemes.NIGHT_END_HOUR}:00 后自动恢复「${
                    SeasonThemes.byId(seasonId).label
                }」。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

/** 两列信息行：左标题、右内容（关于页用）。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.textPrimary, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = C.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

/** 一组编号条款（免责声明 / 开源协议 / 隐私条款共用）。 */
@Composable
fun ClauseList(items: List<String>) {
    val C = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, text ->
            if (index > 0) Spacer(Modifier.height(7.dp))
            ClauseRow(index = index + 1, text = text)
        }
    }
}

/** 当前版本号（取自构建配置，避免与 build.gradle.kts 脱节）。 */
fun appVersionText(): String = "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"

/** 外观模式选项（跟随系统 / 浅色 / 深色）。 */
@Composable
fun AppearanceModeOptions(
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
) {
    val C = LocalAppColors.current
    Text(
        "默认跟随手机系统；开启系统深色模式后自动切换。",
        color = C.textSecondary,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(12.dp))
    AppearanceMode.entries.forEach { item ->
        if (item.ordinal > 0) Spacer(Modifier.height(8.dp))
        OptionRow(
            label = when (item) {
                AppearanceMode.SYSTEM -> "跟随系统"
                AppearanceMode.LIGHT -> "始终浅色"
                AppearanceMode.DARK -> "始终深色"
            },
            desc = when (item) {
                AppearanceMode.SYSTEM -> "随手机深色模式自动切换（默认）"
                AppearanceMode.LIGHT -> "不受系统影响，固定使用浅色界面"
                AppearanceMode.DARK -> "不受系统影响，固定使用深色界面"
            },
            selected = mode == item,
        ) { onModeChange(item) }
    }
}
