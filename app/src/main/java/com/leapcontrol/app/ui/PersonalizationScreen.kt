package com.leapcontrol.app.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.AppearanceMode
import com.leapcontrol.app.R
import com.leapcontrol.app.UpdateCheckResult
import kotlinx.coroutines.launch

/** 本项目开源仓库地址（关于页可直接点击跳转）。 */
private const val PROJECT_GITHUB_URL = "https://github.com/enjoyitxml/LeapControl"

/**
 * 主题中心的三个分类，对应页面上横向的三个 tab。
 *
 * 「日间 / 夜间」是实时绘制的 20 套，「视频」是旧版四季 + 星空实拍视频，
 * 三类平铺在同一个主题中心里，不再分散成三块 —— 之前一页里连着三个大网格，
 * 找主题要一直往下滚，分类也不直观。
 */
private enum class ThemeTab(val label: String, val themes: List<SeasonTheme>) {
    DAY("日间", SeasonThemes.DAY),
    NIGHT("夜间", SeasonThemes.NIGHT_ALL),
    VIDEO("视频", SeasonThemes.VIDEO),
}

/**
 * 「个性化」页（从「我的」进入，也可由首页顶部「皮肤」按钮直达）：
 *  - 外观模式：跟随系统 / 始终浅色 / 始终深色 / 自由模式（四选一，决定明暗与可选主题）
 *  - 主题中心：日间 / 夜间 / 视频 三个 tab，共 25 套
 *  - 夜间时段强制深色（仅自由模式）
 *  - 相伴卡背景：6 套内置图
 *
 * 缩略图直接用 [ThemeSwatch] 画真实场景，和首页看到的是同一套绘制代码。
 */
@Composable
fun PersonalizationScreen(
    runtime: AppRuntime,
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
    lightThemeId: String,
    darkThemeId: String,
    activeThemeId: String,
    systemDark: Boolean,
    darkNow: Boolean,
    onPickTheme: (String) -> Unit,
    nightAuto: Boolean,
    onNightAutoChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val activeTheme = SeasonThemes.byId(activeThemeId)
    // 进页面直接落在「当前生效主题」所在的分类上，不用先自己找
    var tab by remember { mutableStateOf(ThemeTab.entries.first { activeTheme in it.themes }) }
    // 跟随系统下点了明暗相反的主题：先记住它，等用户确认「是否退出跟随」
    var pendingFollowExit by remember { mutableStateOf<SeasonTheme?>(null) }
    // 切到「始终浅色 / 始终深色」后，如果当前 tab 整组都不可选，自动挪到能用的那一组
    LaunchedEffect(mode) {
        val usable = tab.themes.any { ThemeResolver.canPick(mode, it) }
        if (!usable) {
            tab = when (mode) {
                AppearanceMode.DARK -> ThemeTab.NIGHT
                else -> ThemeTab.DAY
            }
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
        SettingsTopBar(title = "个性化", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 外观模式 ----
        SettingsSection(title = "外观模式", desc = "决定界面明暗与可选的类型") {
            SettingsCard {
                AppearanceModeCards(mode = mode, onModeChange = onModeChange)
                Spacer(Modifier.height(10.dp))
                Text(
                    appearanceHint(mode),
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 主题中心：日间 / 夜间 / 视频 三个 tab ----
        SettingsSection(
            title = "主题中心",
            desc = "${SeasonThemes.ALL.size} 套 · 三类",
        ) {
            SettingsCard {
                ThemeTabBar(selected = tab, onSelect = { tab = it })
                Spacer(Modifier.height(14.dp))
                ThemePickGrid(
                    themes = tab.themes,
                    lightThemeId = lightThemeId,
                    darkThemeId = darkThemeId,
                    activeThemeId = activeThemeId,
                    mode = mode,
                    onPick = { theme ->
                        when {
                            !ThemeResolver.canPick(mode, theme) -> Toast.makeText(
                                context,
                                if (theme.night) "「始终浅色」模式下只能选浅色主题"
                                else "「始终深色」模式下只能选深色主题",
                                Toast.LENGTH_SHORT,
                            ).show()

                            // 跟随系统 + 明暗相反的主题：必须先问，应用它就意味着退出跟随
                            ThemeResolver.needsFollowExitPrompt(mode, systemDark, theme) ->
                                pendingFollowExit = theme

                            else -> {
                                onPickTheme(theme.id)
                                // 自由模式下选的是「另一侧」的主题：不会立刻换皮肤，说一句免得以为没反应
                                if (theme.night != darkNow) {
                                    Toast.makeText(
                                        context,
                                        "已设为${if (theme.night) "深色" else "浅色"}模式的主题，" +
                                            "切到${if (theme.night) "深色" else "浅色"}界面时生效",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                ThemeSlotSummary(
                    mode = mode,
                    activeThemeId = activeThemeId,
                    lightThemeId = lightThemeId,
                    darkThemeId = darkThemeId,
                )
            }
        }

        // ---- 夜间时段强制深色：只对自由模式有意义 ----
        if (ThemeResolver.supportsNightOverride(mode)) {
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                SwitchRow(
                    label = "夜间时段强制深色",
                    desc = "每天 ${SeasonThemes.NIGHT_START_HOUR}:00 – 次日 " +
                        "${SeasonThemes.NIGHT_END_HOUR}:00，即使手机是浅色模式也切到深色主题，" +
                        "用的是上面深色那一套（选择不会被覆盖）",
                    checked = nightAuto,
                    onCheckedChange = onNightAutoChange,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 相伴卡背景 ----
        SettingsSection(title = "相伴卡背景", desc = "首页「我与我的爱车走过」") {
            SettingsCard {
                OwnershipBackdropGrid(runtime = runtime, selectedId = runtime.ownershipBackdrop.value)
            }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    // ---- 退出跟随确认 ----
    pendingFollowExit?.let { theme ->
        val target = if (theme.night) "深色" else "浅色"
        val systemNow = if (systemDark) "深色" else "浅色"
        AlertDialog(
            onDismissRequest = { pendingFollowExit = null },
            containerColor = C.panelBg,
            title = { Text("退出「跟随系统」？", color = C.textPrimary) },
            text = {
                Text(
                    "手机当前是$systemNow 模式，而「${theme.label}」是$target 主题。\n\n" +
                        "应用后会关闭「跟随系统」，外观固定为$target 模式，" +
                        "之后不再随手机切换。想保留自动切换，可以改用「自由模式」，" +
                        "那样浅色和深色能各指定一套。",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPickTheme(theme.id)
                    onModeChange(ThemeResolver.modeAfterFollowExit(theme))
                    pendingFollowExit = null
                }) { Text("应用并切换", color = C.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFollowExit = null }) {
                    Text("取消", color = C.textSecondary)
                }
            },
        )
    }
}

/** 外观模式的四种模式：2×2 卡片，一眼看全，不用横向挤四个小段。 */
@Composable
private fun AppearanceModeCards(
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
) {
    val C = LocalAppColors.current
    val items = listOf(
        Triple(AppearanceMode.SYSTEM, "跟随系统", Icons.Filled.BrightnessAuto),
        Triple(AppearanceMode.LIGHT, "始终浅色", Icons.Filled.LightMode),
        Triple(AppearanceMode.DARK, "始终深色", Icons.Filled.DarkMode),
        Triple(AppearanceMode.FREE, "自由模式", Icons.Filled.Tune),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (m, label, icon: ImageVector) ->
                    val selected = m == mode
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
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
                            .clickable { onModeChange(m) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) C.accent else C.textSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            color = if (selected) C.accent else C.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (selected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = C.accent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 当前模式的行为说明，避免用户靠猜。 */
private fun appearanceHint(mode: AppearanceMode): String = when (mode) {
    AppearanceMode.SYSTEM ->
        "明暗跟着手机的深色模式走，白天用浅色那一套、夜里用深色那一套，都随手机自动切。" +
            "在浅色下选了深色主题会先问你 —— 应用它就要关掉跟随。"
    AppearanceMode.LIGHT ->
        "界面固定浅色，只能选浅色主题（日间 10 套 + 视频里的春日/夏日/秋日/冬日）。"
    AppearanceMode.DARK ->
        "界面固定深色，只能选深色主题（夜间 10 套 + 星空视频）。"
    AppearanceMode.FREE ->
        "明暗仍跟手机走，但浅色和深色各用哪套由你定，两边互不影响，" +
            "选主题不会退出跟随。"
}

/** 主题中心顶部的三分类 tab（胶囊分段，选中白底浮起）。 */
@Composable
private fun ThemeTabBar(
    selected: ThemeTab,
    onSelect: (ThemeTab) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(C.iconBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeTab.entries.forEach { t ->
            val on = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(3.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) C.cardBg else Color.Transparent)
                    .clickable { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    color = if (on) C.textPrimary else C.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 两个槽位现在各选了哪套、哪套正在生效。
 *
 * 始终浅色只看浅色槽、始终深色只看深色槽，就在对应模式下少显示一行，
 * 免得多出一个「反正用不到」的信息。
 */
@Composable
private fun ThemeSlotSummary(
    mode: AppearanceMode,
    activeThemeId: String,
    lightThemeId: String,
    darkThemeId: String,
) {
    val C = LocalAppColors.current
    val active = SeasonThemes.byId(activeThemeId).label
    val light = SeasonThemes.byId(lightThemeId).label
    val dark = SeasonThemes.byId(darkThemeId).label
    val detail = when (mode) {
        AppearanceMode.LIGHT -> "浅色 → $light"
        AppearanceMode.DARK -> "深色 → $dark"
        AppearanceMode.SYSTEM, AppearanceMode.FREE -> "浅色 → $light　·　深色 → $dark"
    }
    Column {
        Text(
            "当前生效：$active",
            color = C.accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(detail, color = C.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

/**
 * 「关于」页：版本信息 + 检查更新 + 内嵌「免责声明」「隐私条款」两个子入口。
 */
@Composable
fun AboutScreen(
    runtime: AppRuntime,
    onBack: () -> Unit,
    onOpenDisclaimer: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val C = LocalAppColors.current
    val updateInfo by runtime.updateInfo.collectAsState()
    var checkingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    // 上一次手动检查是否「失败」（网络 / 接口异常），与「已是最新」分开反馈
    var checkFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "关于", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        SettingsCard {
            InfoRow(label = "客户端", value = "零控 LeapControl")
            Spacer(Modifier.height(10.dp))
            InfoRow(label = "版本", value = appVersionText())
            Spacer(Modifier.height(10.dp))
            // 检查更新：红点 + 手动检查 + 弹窗
            ClickRow(
                title = "检查更新",
                desc = when {
                    checkingUpdate -> "检查中…"
                    checkFailed -> "检查失败，请检查网络后重试"
                    updateInfo != null -> "发现新版本 v${updateInfo?.version}，点击前往下载"
                    else -> "当前已是最新版本（v${runtime.currentVersion()}）"
                },
                iconRes = R.drawable.ic_system_update,
                showDot = updateInfo != null,
            ) {
                if (!checkingUpdate) {
                    scope.launch {
                        checkingUpdate = true
                        checkFailed = false
                        val result = runCatching { runtime.checkForUpdate() }.getOrNull()
                        checkingUpdate = false
                        when (result) {
                            is UpdateCheckResult.Newer -> {
                                pendingUpdate = result.info
                                showUpdateDialog = true
                            }
                            UpdateCheckResult.UpToDate -> Unit
                            else -> checkFailed = true
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Text(
                "零控是车主自发开发的非官方自用客户端，与零跑汽车官方无关。"
                    + "所有车况读取与控车指令均通过零跑官方云端接口下发，"
                    + "使用前请确保车辆处于安全状态，并遵守车辆使用规范。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        // 本项目开源地址：完全免费开源，点击直接用浏览器打开仓库
        ClickRow(
            title = "项目开源地址",
            desc = PROJECT_GITHUB_URL,
            iconRes = R.drawable.ic_github,
            onClick = { runtime.openExternal(PROJECT_GITHUB_URL) },
        )
        Spacer(Modifier.height(12.dp))
        ClickRow(
            title = "免责声明",
            desc = "软件性质、使用风险与责任免除条款",
            iconRes = com.leapcontrol.app.R.drawable.ic_disclaimer,
            onClick = onOpenDisclaimer,
        )
        Spacer(Modifier.height(12.dp))
        ClickRow(
            title = "隐私条款",
            desc = "数据全部存于本地，不收集任何隐私",
            iconRes = com.leapcontrol.app.R.drawable.ic_privacy,
            onClick = onOpenPrivacy,
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    // ---- 版本更新弹窗（手动「检查更新」触发）----
    if (showUpdateDialog && pendingUpdate != null) {
        UpdateDialog(
            info = pendingUpdate!!,
            currentVersion = runtime.currentVersion(),
            onOpenDownload = {
                // 默认直接下载安装包；无直链时退回发布页
                runtime.openExternal(pendingUpdate!!.downloadUrl ?: pendingUpdate!!.htmlUrl)
                showUpdateDialog = false
            },
            onOpenGithub = {
                runtime.openExternal(pendingUpdate!!.htmlUrl)
                showUpdateDialog = false
            },
            onDismiss = { showUpdateDialog = false },
            onNeverAgain = {
                runtime.setDismissedVersion(pendingUpdate!!.version)
                showUpdateDialog = false
            },
        )
    }
}

/**
 * 相伴卡背景选择网格：3 列 × 2 行，缩略图按 3:1 裁切（和实际卡片一致）。
 *
 * 缩略图直接用原图 Crop，所见即所得；选中的那张加主题色描边 + 半透明蒙层 + 勾。
 */
@Composable
fun OwnershipBackdropGrid(runtime: AppRuntime, selectedId: String) {
    val C = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OwnershipBackdrops.ALL.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { backdrop ->
                    val selected = backdrop.id == selectedId
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    if (selected) 2.dp else 0.dp,
                                    if (selected) C.accent else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { runtime.setOwnershipBackdropId(backdrop.id) },
                        ) {
                            Image(
                                painter = painterResource(backdrop.res),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.28f)),
                                )
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            backdrop.label,
                            color = if (selected) C.accent else C.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 主题选择网格：3 列，缩略图用 [ThemeSwatch] 画真实场景（和首页同一套绘制代码）。
 *
 * 三种状态要分得清：
 *  - 正在生效：[activeThemeId] → 主题色蒙层 + 白勾；
 *  - 已选中但不是当前明暗那一侧（自由 / 跟随系统下另一侧）：主题色描边；
 *  - 当前模式下不可选：整块降到 35% 透明度，点了弹提示。
 */
@Composable
fun ThemePickGrid(
    themes: List<SeasonTheme>,
    lightThemeId: String,
    darkThemeId: String,
    activeThemeId: String,
    mode: AppearanceMode,
    onPick: (SeasonTheme) -> Unit,
) {
    val C = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        themes.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { theme ->
                    val enabled = ThemeResolver.canPick(mode, theme)
                    val slotId = if (theme.night) darkThemeId else lightThemeId
                    val slotted = theme.id == slotId
                    val active = theme.id == activeThemeId
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    if (slotted) 2.dp else 0.dp,
                                    if (slotted) C.accent else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .alpha(if (enabled) 1f else 0.35f)
                                // 不可选也让点击进来，好弹一句「为什么不能选」
                                .clickable { onPick(theme) },
                        ) {
                            ThemeSwatch(
                                theme = theme,
                                dark = C.isDark,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (theme.animated) {
                                ThemeCornerBadge("动态", Modifier.align(Alignment.TopEnd))
                            }
                            if (theme.video) {
                                ThemeCornerBadge("视频", Modifier.align(Alignment.TopEnd))
                            }
                            if (active) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(C.accent.copy(alpha = 0.20f)),
                                )
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(18.dp),
                                )
                            }
                            // 另一侧槽位已选中的那套：右下角点一个小圆点，表示「切过去就是它」
                            if (slotted && !active) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(5.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(C.accent),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            theme.label,
                            color = when {
                                !enabled -> C.textSecondary.copy(alpha = 0.55f)
                                active -> C.accent
                                slotted -> C.textPrimary
                                else -> C.textPrimary
                            },
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 缩略图右上角的小角标（动态 / 视频）。 */
@Composable
private fun ThemeCornerBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 8.5.sp,
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
