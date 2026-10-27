package com.leapcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.UpdateCheckResult
import com.leapcontrol.app.AppearanceMode
import com.leapcontrol.app.R
import kotlinx.coroutines.launch

/** 本项目开源仓库地址（关于页可直接点击跳转）。 */
private const val PROJECT_GITHUB_URL = "https://github.com/enjoyitxml/LeapControl"

/**
 * 「个性化」页（从「我的」进入，也可由首页顶部「皮肤」按钮直达）：
 *  - 外观模式：跟随系统 / 始终浅色 / 始终深色
 *  - 日间主题：10 套（6 静态 + 4 动态）
 *  - 夜间主题：10 套（6 静态 + 4 动态）+ 夜间自动切换
 *  - 相伴卡背景：6 套内置图
 *
 * 缩略图直接用 [ThemeSwatch] 画真实场景，和首页看到的是同一套绘制代码。
 */
@Composable
fun PersonalizationScreen(
    runtime: AppRuntime,
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
    seasonId: String,
    onSeasonChange: (String) -> Unit,
    activeSeasonId: String,
    autoNightActive: Boolean,
    nightThemeId: String,
    onNightThemeChange: (String) -> Unit,
    nightAuto: Boolean,
    onNightAutoChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
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
        SettingsSection(title = "外观模式", desc = "浅色 / 深色") {
            SettingsCard {
                AppearanceModeOptions(mode = mode, onModeChange = onModeChange)
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 日间主题 ----
        SettingsSection(title = "日间主题", desc = "10 套 · 6 静态 + 4 动态") {
            SettingsCard {
                ThemePickGrid(
                    themes = SeasonThemes.DAY,
                    selectedId = activeSeasonId,
                    onPick = onSeasonChange,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 夜间主题 ----
        SettingsSection(title = "夜间主题", desc = "10 套 · 6 静态 + 4 动态") {
            SettingsCard {
                ThemePickGrid(
                    themes = SeasonThemes.NIGHT_ALL,
                    selectedId = activeSeasonId,
                    // 选夜间主题同时记成「夜间自动切换」的目标，晚上自动切到它
                    onPick = { id ->
                        onSeasonChange(id)
                        onNightThemeChange(id)
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (autoNightActive) {
                        "夜间主题「${SeasonThemes.byId(activeSeasonId).label}」正在生效，明早 " +
                            "${SeasonThemes.NIGHT_END_HOUR}:00 后自动恢复「" +
                            "${SeasonThemes.byId(seasonId).label}」。"
                    } else {
                        "夜间自动切换会切到「${SeasonThemes.byId(nightThemeId).label}」 —— " +
                            "在上面选任意一套夜间主题，就会记住它。"
                    },
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    label = "夜间自动切换",
                    desc = "每天 ${SeasonThemes.NIGHT_START_HOUR}:00 – 次日 " +
                        "${SeasonThemes.NIGHT_END_HOUR}:00 自动切到夜间主题，白天恢复你选的日间主题" +
                        "（选择不会被覆盖）。夜间主题是深色调，生效时界面文字会自动转浅色",
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

        Spacer(Modifier.height(18.dp))

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
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
 * 动态主题右上角标「动态」；选中加主题色描边 + 勾。
 * 每套主题的说明（[SeasonTheme.mood]）不在这里显示 —— 20 套都带一行小字会把页面撑得太长，
 * 想看效果直接点一下首页就换了。
 */
@Composable
fun ThemePickGrid(
    themes: List<SeasonTheme>,
    selectedId: String,
    onPick: (String) -> Unit,
) {
    val C = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        themes.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { theme ->
                    val selected = theme.id == selectedId
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
                                    if (selected) 2.dp else 0.dp,
                                    if (selected) C.accent else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onPick(theme.id) },
                        ) {
                            ThemeSwatch(
                                theme = theme,
                                dark = C.isDark,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (theme.animated) {
                                Text(
                                    "动态",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.32f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            if (selected) {
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
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            theme.label,
                            color = if (selected) C.accent else C.textPrimary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
