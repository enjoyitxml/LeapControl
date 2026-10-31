package com.leapcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.leapcontrol.app.QuickSwitchLock
import com.leapcontrol.app.QuickSwitchMode
import com.leapcontrol.app.SessionStore
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
    // 发送到车机弹层（首页卡片与设置里的「搜一个地方」共用）
    var showDestination by remember { mutableStateOf(false) }
    var showAsrKey by remember { mutableStateOf(false) }
    var showPowerType by remember { mutableStateOf(false) }
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
    // 自定义图正在生效时，对应部件的快捷切换会被收起（见 QuickSwitchLock.effectiveTarget）。
    // 在设置页要能自圆其说：开关明明开着、首页却没有白点，用户第一个来这儿找。
    val customVersions by runtime.customBackdrop
    val darkNow = ThemeResolver.isDark(runtime.appearanceMode(), isSystemInDarkTheme())
    val themeLocked = (if (darkNow) runtime.darkThemeId() else runtime.lightThemeId()) ==
        SeasonThemes.customFor(darkNow).id &&
        (if (darkNow) customVersions.night else customVersions.day) > 0L
    val cardLocked = runtime.ownershipBackdrop.value == OwnershipBackdrops.CUSTOM_ID &&
        customVersions.ownership > 0L
    // 零记总开关：关掉后语音 Key 这一项就没有服务对象了（语音只服务于灵感球），一起收起
    val journalOn by runtime.journalEnabled
    // 动力类型：null = 自动推断（按车型名 + 燃油信号），否则为用户手动指定
    val powerOverride by runtime.powerTypeOverride
    // 排查「滑动卡不卡」用的：能区分「掉帧」和「屏幕本来就只有 60Hz」两件事
    val frameStatsOn by runtime.frameStatsVisible
    // 官方图标那套带商标风险，默认不暴露，解锁开关在「高级设置」里
    val iconSwitcherOn by runtime.iconSwitcherVisible

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


        // ---- 车辆：本机显示用的信息，只影响零控里怎么称呼、怎么画这台车 ----
        SettingsCard("车辆") {
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
            // 动力类型：车端续航信号不全时（纯电 C01 只下发 2188、fuelSoc 恒为 0），
            // 自动推断可能失手，这里给个能自己拍板的开关。按 VIN 保存。
            ClickRow(
                title = "动力类型",
                desc = when (powerOverride) {
                    SessionStore.VehiclePowerType.PURE_ELECTRIC -> "已手动设为纯电（只显示电量）"
                    SessionStore.VehiclePowerType.RANGE_EXTENDER -> "已手动设为增程（电量 + 燃油）"
                    null -> "自动识别：" + when (runtime.statusFlow.value?.isRangeExtender) {
                        true -> "增程"
                        false -> "纯电"
                        null -> "待读取车况"
                    }
                },
                iconRes = R.drawable.ic_nav_car,
            ) { showPowerType = true }
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
        }

        Spacer(Modifier.height(14.dp))

        // ---- 控车与安全：下发指令前的两道闸，以及靠近解锁 ----
        SettingsCard("控车与安全") {
            ClickRow(
                title = "控车密码",
                desc = if (runtime.hasPin) "已保存 4 位操控密码，点击修改" else "未设置，点击设置",
                iconRes = R.drawable.ic_qc_lock,
            ) { showPin = true }
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
        }
        // 间距放在 if 外面：没配对车辆时这张卡整块不出现，靠卡内的 Spacer 会少一截留白
        Spacer(Modifier.height(14.dp))

        // ---- 首页外观：壁纸切换怎么触发 ----
        // 单独成卡而不是塞进车辆卡：这几项纯属观感偏好，
        // 混在车辆信息里会让用户以为关掉它会影响控车。
        SettingsCard("首页外观") {
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

            // 有自定义图在用 → 说明哪几块的入口被收起了。只在这里说一句，
            // 首页不加任何提示：那是用户自己的图，不该为了解释功能而在图上压字。
            if (themeLocked || cardLocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    QuickSwitchLock.settings(
                        buildList {
                            if (themeLocked) add(QuickSwitchLock.PART_CAR)
                            if (cardLocked) add(QuickSwitchLock.PART_CARD)
                        },
                    ),
                    color = C.accent.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }

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

        // ---- 零记：功能总开关 ----
        // 单独成卡：关掉它会让底部导航少一格、首页少两张卡，影响面比普通开关大得多
        SettingsCard("零记") {
            SwitchRow(
                label = "启用零记",
                // 一行放得下：详细后果写在下面那段，这里只列功能名，别把开关右侧挤没
                desc = "用车目标、记账、打卡、保养与保险提醒",
                checked = journalOn,
                onCheckedChange = {
                    runtime.setJournalEnabled(it)
                    message = if (it) "零记已重新开启" else "零记已关闭，数据仍保留在这台手机上"
                },
            )
            Spacer(Modifier.height(10.dp))
            // 手工断行：中文在 Compose 里可以直接断在「，」前面，自动折行会得到一行
            // 以标点开头的文字，很难看。这里按语义断成两句，窄屏上各自再折也没关系。
            Text(
                if (journalOn) {
                    "关掉后，底部「零记」和首页的保险、保养卡都会收起。\n" +
                        "想用时回到这里打开即可，记过的内容不会丢。"
                } else {
                    "已关闭。底部只剩「零控」和「我的」。\n" +
                        "账单、打卡和目标都还在本机，打开开关就能继续。"
                },
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            // 语音识别只服务于零记的灵感球；零记关掉之后这一项只剩个空配置页
            if (journalOn) {
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
            }
            // 备份入口不跟总开关走：零记关掉之后数据仍然在本机，这时候更需要能导出去。
            // （关掉零记的说明里写着「数据仍保留在这台手机上」，用户第一反应就是来这儿找出口）
            Spacer(Modifier.height(12.dp))
            JournalBackupRows(runtime = runtime, onMessage = { message = it })
            // 数据安全：自动备份 + 更新后数据不见了时的找回入口
            JournalStorageWarningRow(runtime = runtime)
            JournalSafetyRows(runtime = runtime, onMessage = { message = it })
        }

        Spacer(Modifier.height(14.dp))

        // ---- 车辆位置与目的地：首页那张合并卡，以及它依赖的高德 Web Key ----
        // 这一对原来锁在「高级设置」里，可那张卡已经回到首页了，配置就不该再藏一层：
        // 卡片点开要 Key，而「高级设置」默认折叠 —— 等于让人先去猜东西在哪。
        SettingsCard("车辆位置与目的地") {
            Text(
                "首页那张卡上的地图、地址与「发送目的地」都要高德的关键字搜索与静态地图，\n" +
                    "用的是你自己的 Key（免费额度足够日常使用）。\n" +
                    "卡片右上角拍停车位照片不需要 Key，也不联网。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "高德 Web Key",
                desc = if (runtime.hasAmapKey()) "已配置，可直接搜索目的地" else "未配置，点开看申请步骤",
                iconRes = R.drawable.ic_key,
            ) { showAmapKey = true }
            Spacer(Modifier.height(10.dp))
            ClickRow(
                title = "搜一个地方",
                desc = "不用回首页，直接在这里搜地点并发送到车机",
                iconRes = R.drawable.ic_nav_map,
            ) { showDestination = true }
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

        Spacer(Modifier.height(14.dp))

        // ---- 高级设置：默认折叠 ----
        // 这里放「大部分人用不上、又确实有用」的东西：带商标风险的图标切换、
        // 以及排查卡顿用的帧率浮层。
        // 折叠状态刻意只用 remember、不落盘 —— 每次进设置都回到收起状态。
        CollapsibleSettingsCard(
            title = "高级设置",
            desc = "进阶功能与排查选项，点开查看",
        ) {
            SwitchRow(
                label = "解锁应用图标切换",
                desc = "开启后「个性化」会出现「应用图标」，可在方向盘与官方 Logo 之间切换（切换后需重启 App）",
                checked = iconSwitcherOn,
                onCheckedChange = { runtime.setIconSwitcherVisible(it) },
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                label = "显示实时帧率",
                desc = "右上角小药丸显示当前帧率与屏幕刷新率，用来确认是否跑满屏幕的高刷",
                checked = frameStatsOn,
                onCheckedChange = { runtime.setFrameStatsVisible(it) },
            )
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

    // ---- 发送到车机（设置里也能直接搜，不必回首页翻卡片） ----
    if (showDestination) {
        DestinationSearchSheet(
            runtime = runtime,
            onDismiss = { showDestination = false },
            onMessage = { message = it },
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
                // 教程比输入框长得多，不给滚动时矮屏上「保存」会被顶出弹窗
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "首页「车辆位置与目的地」卡要用高德的关键字搜索与静态地图，\n" +
                            "都是它自己的接口，所以要你填一个自己的 Key。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    AmapKeyGuide(onOpenConsole = { runtime.openExternal(AmapConsoleUrl) })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        singleLine = true,
                        placeholder = {
                            Text("粘贴 32 位 Web 服务 Key", color = C.textSecondary, fontSize = 13.sp)
                        },
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

    // ---- 动力类型（增程 / 纯电） ----
    // 只在「自动」和两种手动之间切换，不做别的花样：
    // 纯电车若被认成增程，首屏会多出一行「油 0%」，主续航还会去取综合续航 → 显示「--」。
    if (showPowerType) {
        AlertDialog(
            onDismissRequest = { showPowerType = false },
            containerColor = C.panelBg,
            title = { Text("动力类型", color = C.textPrimary) },
            text = {
                Column {
                    Text(
                        "决定首屏显示「电量」还是「电量 + 燃油」两行，" +
                            "车端信号不全时自动判断可能失手，判断错了在这里手动指定。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingOptionRow(
                        label = "自动识别",
                        desc = "有燃油信号才算增程，其余按纯电",
                        selected = powerOverride == null,
                    ) {
                        runtime.setPowerTypeOverride(null)
                        showPowerType = false
                        message = "已改回自动识别动力类型"
                    }
                    SettingOptionRow(
                        label = "纯电",
                        desc = "只显示电量一行，续航取纯电",
                        selected = powerOverride == SessionStore.VehiclePowerType.PURE_ELECTRIC,
                    ) {
                        runtime.setPowerTypeOverride(SessionStore.VehiclePowerType.PURE_ELECTRIC)
                        showPowerType = false
                        message = "已设为纯电"
                    }
                    SettingOptionRow(
                        label = "增程",
                        desc = "显示电量与燃油两行，取综合续航",
                        selected = powerOverride == SessionStore.VehiclePowerType.RANGE_EXTENDER,
                    ) {
                        runtime.setPowerTypeOverride(SessionStore.VehiclePowerType.RANGE_EXTENDER)
                        showPowerType = false
                        message = "已设为增程"
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPowerType = false }) { Text("关闭", color = C.textSecondary) }
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        // 每行控制在 22 个汉字以内（弹层正文宽约 272dp，12sp 放得下 22 个），
                        // 否则会被系统折成四行、把上面的教程挤下去
                        "开箱可用：已内置百度语音凭据，不填也能说话。\n" +
                            "想用自己的额度，就按下面 4 步申请后填进来。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    BaiduKeyGuide(onOpenConsole = { runtime.openExternal(BaiduAsrConsoleUrl) })
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

/** 设置弹层里的单选项（动力类型、更新频率共用）：选中项铺一层 chip 底色 + 右侧对勾。 */
@Composable
internal fun SettingOptionRow(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.chipBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) C.accent else C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

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
 * 用于「高级设置」这类不该默认摊在明面上的分组。折叠状态刻意只用 `remember`、
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

/** 百度智能云「语音技术」控制台（短语音识别就在这里创建应用、拿 Key）。 */
const val BaiduAsrConsoleUrl = "https://console.bce.baidu.com/ai/#/ai/speech/overview/index"

/**
 * 给用户照着敲的网址：在域名后**手工断行**。
 * 弹层里正文只有 ~248dp 宽，整条 305dp 的 URL 会被系统随意折断（常常断在 "inde/x" 这种地方），
 * 主动分成「域名 + 路径」两行更像是有意排版，也不会多占高度。
 */
private const val BaiduAsrConsoleUrlText =
    "console.bce.baidu.com\n/ai/#/ai/speech/overview/index"

/** 申请教程的四步文案：写给不熟悉百度智能云的人看，照着走一遍就能拿到 Key。 */
private val BaiduKeySteps = listOf(
    "打开控制台，用百度账号登录并完成实名认证 —— 免费额度要认证之后才发放。",
    "左侧「应用列表」→「创建应用」，接口勾「短语音识别」，应用名随便写。",
    "创建后回到应用列表，复制这个应用的 API Key 与 Secret Key。",
    "粘到下面两个输入框，点「保存并校验」；通过后就长期用你的额度。",
)

/**
 * 申请教程通用卡片：标题 + 「打开控制台 ›」+ 编号步骤 + 可选「申请之后能干嘛」+ 可点的网址。
 *
 * 弹层里的文字不能复制，所以网址**原样印出来**（能照着敲），
 * 上面再放一个「打开控制台」直接跳浏览器，省得在手机上手动输入长链接。
 */
@Composable
private fun KeyGuideCard(
    title: String,
    steps: List<String>,
    urlText: String,
    onOpenConsole: () -> Unit,
    benefitTitle: String = "",
    benefits: List<String> = emptyList(),
) {
    val C = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.chipBg)
            .border(1.dp, C.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = C.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "打开控制台 ›",
                color = C.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenConsole)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    "${index + 1}",
                    color = C.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    step,
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        if (benefits.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                benefitTitle,
                color = C.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            benefits.forEach { item ->
                Row(modifier = Modifier.padding(bottom = 3.dp)) {
                    Text(
                        "·",
                        color = C.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(14.dp),
                    )
                    Text(
                        item,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        // 网址做成一颗可点的浅色胶囊，而不是一行灰字 ——
        // 灰字看不出能点，用户还是得照着敲；这里长相必须像按钮。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = urlText,
                color = C.accent,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(C.accent.copy(alpha = 0.10f))
                    .clickable(onClick = onOpenConsole)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "点击打开", color = C.textSecondary, fontSize = 10.sp)
        }
    }
}

/** 百度语音 Key 的申请教程。 */
@Composable
fun BaiduKeyGuide(onOpenConsole: () -> Unit) {
    KeyGuideCard(
        title = "申请教程（约 2 分钟）",
        steps = BaiduKeySteps,
        urlText = BaiduAsrConsoleUrlText,
        onOpenConsole = onOpenConsole,
    )
}

/** 高德开放平台「应用管理 → 我的应用」：Key 就在这里创建与查看。 */
const val AmapConsoleUrl = "https://console.amap.com/dev/key/app"

/** 照着敲的网址：同样手工断成「域名 + 路径」两行。 */
private const val AmapConsoleUrlText = "console.amap.com\n/dev/key/app"

/**
 * 申请高德 Web Key 的四步。
 *
 * 第 3 步必须点明「Web 服务」：平台默认给的选项里 Android 平台最常见，
 * 但那个要绑包名和 SHA1，填进零控只会报 `INVALID_USER_DOMAIN` / 无权限，是最容易踩的坑。
 */
private val AmapKeySteps = listOf(
    "打开控制台用手机号或支付宝登录，顺手把个人实名认证做完 —— 免费额度要认证后才发放。",
    "「应用管理 → 我的应用 → 创建新应用」，名字随意，类型选「出行」即可。",
    "进入该应用点「添加 Key」，服务平台必须选「Web 服务」（选 Android 要绑包名和 SHA1，填了用不了）。",
    "提交后得到一串 32 位 Key，复制它，粘到下面的输入框保存。",
)

/** 「申请之后能干嘛」：用户最关心的其实是这一步，顺手把账号同步的前提讲清楚。 */
private val AmapKeyBenefits = listOf(
    "首页「车辆位置与目的地」卡可以按关键字搜全国的地点、停车场、充电站，输入几个字就有结果",
    "选中后一键发到车机 —— 手机与车机登录同一个高德账号，上车自动提示继续导航",
    "同一张卡上还能看到车停在哪、一键导航过去，位置来自车机最后一次上报",
    "也可以直接在手机高德里开始导航，比上车再逐字输地址省事得多",
    "Web 服务每天有免费调用量，按日常搜索次数用不到上限，不需要付费",
)

/** 高德 Web Key 的申请教程（设置里与首页卡片弹层共用）。 */
@Composable
fun AmapKeyGuide(onOpenConsole: () -> Unit) {
    KeyGuideCard(
        title = "申请教程（约 3 分钟）",
        steps = AmapKeySteps,
        urlText = AmapConsoleUrlText,
        onOpenConsole = onOpenConsole,
        benefitTitle = "申请之后可以",
        benefits = AmapKeyBenefits,
    )
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

