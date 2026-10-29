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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppearanceMode
import com.leapcontrol.app.LauncherIcon
import com.leapcontrol.app.R

/**
 * 主题中心的两个分类，对应页面上横向的两个 tab。
 *
 * 「日间 / 夜间」各 20 套（10 静态 + 10 动态），全部是实时绘制的场景。
 */
private enum class ThemeTab(val label: String, val themes: List<SeasonTheme>) {
    DAY("日间", SeasonThemes.DAY),
    NIGHT("夜间", SeasonThemes.NIGHT_ALL),
}

/**
 * 「个性化」页（从「我的」进入，也可由首页顶部「皮肤」按钮直达）：
 *  - 外观模式：跟随系统 / 始终浅色 / 始终深色（三选一，决定明暗与可选主题）
 *  - 主题中心：日间 / 夜间 两个 tab，共 40 套
 *  - 相伴卡背景：6 套内置图
 *
 * 缩略图直接用 [ThemeSwatch] 画真实场景，和首页看到的是同一套绘制代码。
 *
 * 所有点击都交给 [ThemeResolver.pick] 算结果，本页只负责把结果画出来、
 * 把该说的话说一句 —— 规则不在这里分叉，避免改一处漏一处。
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
    onApplyPick: (ThemeResolver.Pick) -> Unit,
    onOpenCarAppearance: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val activeTheme = SeasonThemes.byId(activeThemeId)
    // 进页面直接落在「当前生效主题」所在的分类上，不用先自己找
    var tab by remember { mutableStateOf(ThemeTab.entries.first { activeTheme in it.themes }) }
    // 切到「始终浅色 / 始终深色」后，如果当前 tab 整组都不可选，自动挪到能用的那一组
    LaunchedEffect(mode) {
        val usable = tab.themes.any { ThemeResolver.canPick(mode, it) }
        if (!usable) tab = if (mode == AppearanceMode.DARK) ThemeTab.NIGHT else ThemeTab.DAY
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

        // ---- 主题中心：日间 / 夜间 两个 tab ----
        SettingsSection(
            title = "主题中心",
            desc = "${SeasonThemes.ALL.size} 套 · 日间夜间各 ${SeasonThemes.DAY.size}",
        ) {
            SettingsCard {
                ThemeTabBar(selected = tab, onSelect = { tab = it })
                Spacer(Modifier.height(12.dp))
                // 分类说明：锁定模式下有哪些主题点不了、以及「选了另一侧现在看不到效果」，
                // 都在点之前讲清楚，别让用户以为没反应
                tabHint(tab, mode, systemDark)?.let { hint ->
                    Text(
                        hint,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                ThemePickGrid(
                    themes = tab.themes,
                    lightThemeId = lightThemeId,
                    darkThemeId = darkThemeId,
                    activeThemeId = activeThemeId,
                    mode = mode,
                    onPick = { theme ->
                        val result = ThemeResolver.pick(
                            mode = mode,
                            systemDark = systemDark,
                            theme = theme,
                            lightThemeId = lightThemeId,
                            darkThemeId = darkThemeId,
                        )
                        if (result.hint != ThemeResolver.PickHint.BLOCKED) onApplyPick(result)
                        pickHintText(result.hint, theme)?.let {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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

        Spacer(Modifier.height(18.dp))

        // ---- 相伴卡背景 ----
        SettingsSection(title = "相伴卡背景", desc = "首页「我与我的爱车走过」") {
            SettingsCard {
                OwnershipBackdropGrid(runtime = runtime, selectedId = runtime.ownershipBackdrop.value)
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 车模外观 ----
        // 官方车模图是按车辆档案渲染的定妆照，接口没有「选颜色」的入参，
        // 所以颜色跟实车不一致时只能在这一页自己换（内置换色 / 导入自己改好的图）。
        SettingsSection(title = "车模外观", desc = "换色 · 导入自有图片") {
            SettingsCard {
                ClickRow(
                    title = "首页车模",
                    desc = "颜色跟实车不一样？在这里换车漆，或导入自己改好的图片",
                    icon = Icons.Filled.DirectionsCar,
                    onClick = onOpenCarAppearance,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 首页图标 ----
        // 只给「显示 / 不显示」，不给排序：顶栏就三个图标，藏起来是为了画面干净，
        // 不是为了让人再学一套拖拽。
        SettingsSection(title = "首页图标", desc = "右上角") {
            SettingsCard {
                SwitchRow(
                    label = "蓝牙钥匙",
                    desc = "在「个性化」图标左边显示蓝牙钥匙入口，关掉后只能从设置进入",
                    checked = runtime.bleIconVisible.value,
                    onCheckedChange = { runtime.setBleIconVisible(it) },
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    label = "玩法说明",
                    desc = "显示换车模、换背景、快捷控车等玩法的入口；关掉后蓝牙钥匙会直接贴到个性化左边",
                    checked = runtime.tipIconVisible.value,
                    onCheckedChange = { runtime.setTipIconVisible(it) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 应用图标 ----
        // 只有「设置 → 隐藏设置」里解锁了才出现：官方 logo 那套带商标风险，
        // 默认就用方向盘，不把它摆在明面上。切换靠 activity-alias 换组件，重启后生效。
        if (runtime.iconSwitcherVisible.value) {
            SettingsSection(title = "应用图标", desc = "切换后重启生效") {
                SettingsCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LauncherIcon.allStyles().forEachIndexed { index, style ->
                            if (index > 0) Spacer(Modifier.width(10.dp))
                            AppIconOption(
                                style = style,
                                selected = runtime.launcherIcon.value == style,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (runtime.launcherIcon.value != style) {
                                        val ok = runtime.setLauncherIcon(style)
                                        Toast.makeText(
                                            context,
                                            if (ok) {
                                                "已切换为「${LauncherIcon.displayName(style)}」，重启 App 后生效"
                                            } else {
                                                "切换失败，系统不允许修改组件状态"
                                            },
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "桌面图标由系统缓存，切换后要重新打开 App 才会刷新；" +
                            "个别桌面要再等几秒，或者滑动一下桌面才会变。",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * 一张图标选项卡：深色预览 + 名称 + 一句说明。
 *
 * 预览底固定用深色 —— 两套图标本身就是「深色底 + 白色图形」，这样看到的就是桌面上
 * 真实的样子，也不会因为浅色主题下换了底色而误判效果。
 */
@Composable
private fun AppIconOption(
    style: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    val official = style == LauncherIcon.STYLE_OFFICIAL
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) C.chipBgActive else C.chipBg)
            .border(1.5.dp, if (selected) C.accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF1B1B1D)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    if (official) {
                        R.drawable.ic_launcher_foreground
                    } else {
                        R.drawable.ic_launcher_fg_steering
                    },
                ),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // 官方 vector 的图形只占 viewport 的 ~41%，这里放大到与方向盘同样的观感；
                    // 多出来的部分是透明区域，被外层 clip 裁掉不会切到图形本体。
                    .then(
                        if (official) {
                            Modifier.graphicsLayer(scaleX = 1.64f, scaleY = 1.64f)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            LauncherIcon.displayName(style),
            color = C.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            LauncherIcon.desc(style),
            color = C.textSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (selected) {
            Spacer(Modifier.height(5.dp))
            Text(
                "当前使用",
                color = C.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 主题中心的分类说明：只在「会有意外感」的那几种情况下出现。 */
private fun tabHint(tab: ThemeTab, mode: AppearanceMode, systemDark: Boolean): String? = when {
    // 锁定模式下这一组里有选不了的主题，提醒一句免得白点
    tab.themes.any { !ThemeResolver.canPick(mode, it) } ->
        "当前是「${mode.label}」模式，这里有些主题不适用，点了会提示原因。"
    tab == ThemeTab.NIGHT && !systemDark ->
        "手机现在是浅色模式，这里选的主题会在切到深色时生效。"
    tab == ThemeTab.DAY && systemDark ->
        "手机现在是深色模式，这里选的主题会在切到浅色时生效。"
    else -> null
}

/** 选完主题之后那句话：只在「界面没按预期变化」或「模式被改了」时需要说。 */
private fun pickHintText(hint: ThemeResolver.PickHint, theme: SeasonTheme): String? = when (hint) {
    ThemeResolver.PickHint.NONE -> null
    ThemeResolver.PickHint.OTHER_SIDE ->
        "已设为${if (theme.night) "深色" else "浅色"}界面的主题，切到${if (theme.night) "深色" else "浅色"}时生效"
    ThemeResolver.PickHint.BLOCKED ->
        if (theme.night) "「始终浅色」模式下只能选浅色主题" else "「始终深色」模式下只能选深色主题"
}

/** 外观模式的三种模式：一行三块，宽高一致，一眼看全。 */
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
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (m, label, icon: ImageVector) ->
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
                    .clickable { onModeChange(m) },
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
                    label,
                    color = if (selected) C.accent else C.textPrimary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 当前模式的行为说明，避免用户靠猜。 */
private fun appearanceHint(mode: AppearanceMode): String = when (mode) {
    AppearanceMode.SYSTEM ->
        "明暗跟着手机的深色模式走：白天用浅色那一套、夜里用深色那一套，" +
            "两套各自选一次就好，之后随手机自动切换。"
    AppearanceMode.LIGHT ->
        "界面固定浅色，只能选日间主题（${SeasonThemes.DAY.size} 套）。"
    AppearanceMode.DARK ->
        "界面固定深色，只能选夜间主题（${SeasonThemes.NIGHT_ALL.size} 套）。"
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
        AppearanceMode.SYSTEM -> "浅色 → $light　·　深色 → $dark"
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

/** 本项目在 GitHub 上的公开仓库地址（关于页可直接点击跳转）。 */
private const val PROJECT_GITHUB_URL = "https://github.com/enjoyitxml/LeapControl"

/**
 * 「关于」页：版本信息 + 开源仓库地址 + 内嵌「免责声明」「隐私条款」两个子入口。
 *
 * 本项目已在 GitHub 公开：这里给出仓库地址，方便车友查阅源码、提 issue。
 * 使用范围仍然写明「仅供车友自用体验，请勿外传」—— 公开的是源码，不是分发授权。
 * 「检查更新」入口在「我的」页（由 SHOW_UPDATE_CHECK 控制，当前启用），这里只留版本号。
 */
@Composable
fun AboutScreen(
    runtime: AppRuntime,
    onBack: () -> Unit,
    onOpenDisclaimer: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
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
        SettingsTopBar(title = "关于", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        SettingsCard {
            InfoRow(label = "客户端", value = "零控 LeapControl")
            Spacer(Modifier.height(10.dp))
            InfoRow(label = "版本", value = appVersionText())
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Text(
                "零控是车主自发开发的非官方客户端，与零跑汽车官方无关。"
                    + "所有车况读取与控车指令均通过零跑官方云端接口下发，"
                    + "使用前请确保车辆处于安全状态，并遵守车辆使用规范。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            // 使用范围：公开的是源码，不是分发授权，两句话都要在
            androidx.compose.material3.Text(
                "本软件仅供车友自用体验，请勿外传、请勿二次分发。"
                    + "本项目源码已在 GitHub 公开，基于开源项目二次开发而来。",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(12.dp))
        // 开源仓库：点一下用系统浏览器打开
        ClickRow(
            title = "开源项目地址",
            desc = PROJECT_GITHUB_URL,
            iconRes = com.leapcontrol.app.R.drawable.ic_github,
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

/** 缩略图右上角的小角标（动态）。 */
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
