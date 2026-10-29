package com.leapcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.QuickSwitchMode
import com.leapcontrol.app.R
import com.leapcontrol.app.tiles.QuickTiles
import com.leapcontrol.app.journal.BaiduAsr
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
    var showAsrKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmGate by remember { mutableStateOf(runtime.biometricConfirmEnabled()) }
    // 蓝牙钥匙详情页（从本页进入时以整页覆盖的形式出现，返回即回到设置）
    var showBleKey by remember { mutableStateOf(false) }
    LocalBleKeyHost.current?.let { host ->
        if (showBleKey) {
            BluetoothKeyScreen(host = host, onBack = { showBleKey = false })
            return
        }
    }
    // 快速切换壁纸/背景：三态模式 + 两块各自的小白点开关（都挂在 runtime 的 State 上，
    // 这里直接订阅，改完即时生效，不在页面里存第二份）
    val quick by runtime.quickSwitch

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
                title = "语音识别（百度）",
                desc = if (runtime.hasCustomBaiduAsr()) {
                    "已使用你填的 Key，长按灵感球可直接说话"
                } else {
                    "已内置可用，长按灵感球直接说话；也可以填自己的 Key"
                },
                iconRes = R.drawable.ic_mic,
            ) { showAsrKey = true }
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "刷新车模图片",
                desc = if (runtime.hasCustomCarModel()) {
                    "重新拉取官方图，当前显示的自定义车模不受影响"
                } else {
                    "清除缓存并从官方接口重新拉取"
                },
                iconRes = R.drawable.ic_nav_car,
            ) {
                scope.launch {
                    // 只清官方缓存：用户改过色 / 导入的自定义车模要留住，
                    // 否则点一下刷新就把他辛苦调的图弄没了
                    runtime.clearCarImageCache()
                    val ok = runCatching { runtime.fetchCarImage(force = true) }.getOrNull() != null
                    message = when {
                        ok && runtime.hasCustomCarModel() -> "官方车模已刷新（首页仍用你的自定义车模）"
                        ok -> "车模已刷新"
                        else -> "未取到官方车图，首页暂不显示车模"
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                label = "控车二次确认",
                desc = "主界面下发控车指令前弹窗确认，防误触（控制中心磁贴不受限）",
                checked = confirmGate,
                onCheckedChange = {
                    confirmGate = it
                    runtime.setBiometricConfirmEnabled(it)
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- 首页：快速切换壁纸 / 背景 ----
        // 单独成卡而不是塞进上面那张：上面全是"功能/安全"项，这个纯属观感偏好，
        // 混在一起会让用户以为关掉它会影响控车。
        SettingsCard("首页") {
            Text(
                "快速切换壁纸或背景",
                color = C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "车模换的是主题壁纸，相伴卡换的是卡片背景。这里只决定怎么触发。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))

            QuickSwitchCards(
                mode = quick.mode,
                onPick = { runtime.setQuickSwitchMode(it) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                quickSwitchHint(quick.mode),
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            // 「每个部件的小白点单独设置」只在小白点模式下出现：
            // 点击切换是整块可点、关闭是什么都没有，这两种模式里单个部件的开关没有意义
            if (quick.mode == QuickSwitchMode.DOT) {
                Spacer(Modifier.height(16.dp))
                // 只留标题：上面那行提示已经说过「可以只留一个」，这里再解释一遍是废话
                Text(
                    "每个部件单独设置",
                    color = C.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    label = "车模右上角",
                    // 一行放得下：这个说明太长会被折成「…那一类」+「）」两行，尾巴挂着一个括号很难看
                    desc = "点击换主题壁纸，只换当前明暗那一类",
                    checked = quick.carDot,
                    onCheckedChange = { runtime.setCarQuickDotEnabled(it) },
                )
                Spacer(Modifier.height(16.dp))
                SwitchRow(
                    label = "相伴卡右上角",
                    desc = "点击换相伴卡背景，不影响主题",
                    checked = quick.cardDot,
                    onCheckedChange = { runtime.setCardQuickDotEnabled(it) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- 显示：实时帧率浮层 ----
        // 排查「滑动卡不卡」用的：能区分「掉帧」和「屏幕本来就只有 60Hz」两件事。
        val frameStatsOn by runtime.frameStatsVisible
        val iconSwitcherOn by runtime.iconSwitcherVisible
        SettingsCard("显示") {
            SwitchRow(
                label = "显示实时帧率",
                desc = "右上角小药丸显示当前帧率与屏幕刷新率，用来确认是否跑满屏幕的高刷",
                checked = frameStatsOn,
                onCheckedChange = { runtime.setFrameStatsVisible(it) },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- 隐藏设置 ----
        // 官方 logo 那套图标带商标风险，不能摆在明面上，默认关着；
        // 打开之后「个性化」才会多出「应用图标」一项，可在两套之间切换。
        // 整块默认折叠：平时不展开，外人翻设置也看不到这些开关。
        CollapsibleSettingsCard(
            title = "隐藏设置",
            desc = "高级选项，点开查看",
        ) {
            SwitchRow(
                label = "解锁应用图标切换",
                desc = "开启后「个性化」会出现「应用图标」，可在方向盘与官方 Logo 之间切换（切换后需重启 App）",
                checked = iconSwitcherOn,
                onCheckedChange = { runtime.setIconSwitcherVisible(it) },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- 蓝牙钥匙 ----
        // 只放「总开关 + 一个进详情的入口」：具体感应距离、自动解锁这些在详情页里，
        // 免得设置页被七八个开关撑满，也避免同一项设置在两处出现两份状态。
        val bleHost = LocalBleKeyHost.current
        if (bleHost != null) {
            SettingsCard("蓝牙钥匙") {
                SwitchRow(
                    label = "智能无感钥匙",
                    desc = when {
                        !bleHost.bound -> "还没有配对车辆，先点右侧进入配对吧"
                        bleHost.pending -> "设置待车辆确认"
                        bleHost.enabled -> "已开启，带着手机靠近车辆即可解锁"
                        else -> "已关闭，配对记录保留"
                    },
                    checked = bleHost.enabled,
                    onCheckedChange = { on ->
                        if (!bleHost.bound) {
                            message = "请先进入蓝牙钥匙页完成车辆配对"
                        } else {
                            bleHost.toggleEnabled(on)
                            message = bleHost.notice
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                ClickRow(
                    title = "蓝牙钥匙设置",
                    desc = "配对车辆 · 靠近解锁 · 感应距离",
                    iconRes = R.drawable.ic_phosphor_key,
                ) { showBleKey = true }
            }
            Spacer(Modifier.height(14.dp))
        }

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

    // ---- 语音识别（百度短语音） ----
    if (showAsrKey) {
        val current = runtime.baiduAsrKeys()
        var apiInput by remember { mutableStateOf(current.first) }
        var secretInput by remember { mutableStateOf(current.second) }
        var checking by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!checking) showAsrKey = false },
            containerColor = C.panelBg,
            title = { Text("百度语音识别", color = C.textPrimary) },
            text = {
                Column {
                    Text(
                        "语音记账开箱可用：App 已内置一组百度短语音凭据，不需要任何配置。\n" +
                            "这里只是「换用自己的 Key」的入口 —— 想用自己的额度就在百度智能云" +
                            "「语音技术 → 短语音识别」创建应用，把 API Key 与 Secret Key 填进来即可；" +
                            "个人认证送 5 万次。\n" +
                            "保存时会先用这两个 Key 换一次 token，换得到才算配置成功。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiInput,
                        onValueChange = { apiInput = it.trim() },
                        singleLine = true,
                        placeholder = { Text("API Key", color = C.textSecondary, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.textPrimary,
                            unfocusedTextColor = C.textPrimary,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it.trim() },
                        singleLine = true,
                        placeholder = { Text("Secret Key", color = C.textSecondary, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.textPrimary,
                            unfocusedTextColor = C.textPrimary,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                    failure?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = Color(0xFFD9534F), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (checking) return@TextButton
                    checking = true
                    failure = null
                    scope.launch {
                        val problem = BaiduAsr.checkCredentials(apiInput, secretInput)
                        checking = false
                        if (problem == null) {
                            runtime.setBaiduAsrKeys(apiInput, secretInput)
                            BaiduAsr.forgetToken()
                            message = "百度语音已配置，长按灵感球可以直接说话"
                            showAsrKey = false
                        } else {
                            failure = problem
                        }
                    }
                }) { Text(if (checking) "校验中…" else "保存并校验", color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { if (!checking) showAsrKey = false }) {
                    Text("取消", color = C.textSecondary)
                }
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

/**
 * 「快速切换壁纸或背景」的三选一：一行三块卡片。
 *
 * 和「个性化 → 外观模式」用同一套视觉（选中 = 主题色淡底 + 1.5dp 描边 + 图标文字变色），
 * 因为两者都是"三选一、只影响观感"的设置，用户不用重新学一遍。
 * 说明文字不塞进卡片里（四五个字塞不下），统一放在下面一行，跟着选择变。
 */
@Composable
private fun QuickSwitchCards(
    mode: QuickSwitchMode,
    onPick: (QuickSwitchMode) -> Unit,
) {
    val C = LocalAppColors.current
    val items = listOf(
        QuickSwitchMode.DOT to Icons.Filled.FiberManualRecord,
        QuickSwitchMode.TAP to Icons.Filled.TouchApp,
        QuickSwitchMode.OFF to Icons.Filled.VisibilityOff,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (m, icon) ->
            val selected = m == mode
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(shape)
                    .background(
                        if (selected) C.accent.copy(alpha = 0.12f) else C.iconBg,
                        shape,
                    )
                    .border(
                        if (selected) 1.5.dp else 0.dp,
                        if (selected) C.accent else Color.Transparent,
                        shape,
                    )
                    .clickable { onPick(m) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) C.accent else C.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = m.label,
                    color = if (selected) C.accent else C.textPrimary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 当前模式的触发方式说明：点之前就讲清楚，别让人试了才知道点哪儿。 */
private fun quickSwitchHint(mode: QuickSwitchMode): String = mode.desc

/** 可点击的设置行：左图标（可选）+ 标题 + 说明，右箭头；[showDot] 为真时标题右侧显示红点。 */
@Composable
fun ClickRow(
    title: String,
    desc: String,
    iconRes: Int? = null,
    icon: ImageVector? = null,
    showDot: Boolean = false,
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
        // 两种图标来源：老页面传 drawable 资源，新页面直接用 Material 矢量图（不用再补 XML）
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = C.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = C.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (showDot) {
                // 红点紧跟标题，提示有更新待处理
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = C.textPrimary, fontSize = 14.sp)
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF3B30), CircleShape),
                    )
                }
                Spacer(Modifier.height(2.dp))
            } else {
                Text(title, color = C.textPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
            }
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
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/**
 * 可折叠设置卡片：标题行常驻，内容默认收起，点标题才展开。
 *
 * 用于「隐藏设置」这类不该默认摊在明面上的分组。折叠状态刻意只用 `remember`、
 * 不落盘 —— 每次重新进设置页都回到收起状态，避免上次忘了收回、下次一进设置
 * 就把隐藏项晾在屏幕上。
 */
@Composable
fun CollapsibleSettingsCard(
    title: String,
    desc: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.panelBg, shape)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (desc != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(desc, color = C.textSecondary, fontSize = 11.sp)
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = C.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
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
            .border(1.5.dp, if (selected) C.accent else Color.Transparent, shape)
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

