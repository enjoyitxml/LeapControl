package com.leapcontrol.app.ui

import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.R
import com.leapcontrol.app.UpdateCheckResult
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// ============================================================================
// 雾化遮罩（以车模正中心为圆心的椭圆）—— 调参入口，改这里即可，不用动下面的布局
// ----------------------------------------------------------------------------
// 作用：场景主景（山脊 / 云团 / 星点 / 灯）落在上半屏 38% 以上，而首页文字（车名 /
//       续航大字 / 里程行）正好在它后面，不加这层小字会被画面细节吃掉。
// 形状：圆心在「车模正中心」，越往外越浓。车模所在的位置最通透，往四周呈圆弧发散
//       逐渐起雾 —— 过渡是一条圆弧而不是一条横直线，衔接更自然，车模也不会被压灰。
// ============================================================================

/** 车模上沿到内容顶部的距离（照 HomeScreen 的实际布局量出来）：
 *  车名 33 + 更新时间行 16 + 间距 16 + 续航区 78 + 充电中心 24 + 间距 4。
 *  v0.2.1 起顶栏固定在顶部、车模与上方模块间距被压缩（充电中心 14→8、车模前 8→4、快捷控车上 10→6），
 *  车模整体上移约 12dp，这里同步减 12。 */
private val HEADER_VEIL_CONTENT_HEIGHT = 176.dp

/** 车模自身高度（HomeScreen 里固定 200dp）。圆心 = 车模上沿 + 一半。 */
private val CAR_ARTWORK_HEIGHT = 200.dp

/** 椭圆的纵向半径：从车模中心到最外圈。屏幕顶端大致正好落在最外圈上。 */
private val HEADER_VEIL_RADIUS_Y = 320.dp

/**
 * 横向半径 = 纵向半径 × 该倍率。
 * 竖屏是「高远大于宽」，用正圆的话画面左右两侧会跟着一起糊死；把横向拉开（>1）之后，
 * 左右两侧只走到过渡曲线的前 40% 左右，发散更缓，更像环境雾而不是一个圆洞。
 * 调小 = 两侧更浓，调大 = 两侧更透。
 */
private val HEADER_VEIL_RADIUS_X_RATIO = 1.55f

/**
 * 雾化曲线：Pair(归一化椭圆半径 0~1, 该处不透明度 0~1)。
 *   · 想整体更糊 / 更透：同步增减每一行第二个数（保持曲线形状）
 *   · 想让雾扩得更大 / 收得更紧：改 HEADER_VEIL_RADIUS_Y
 *   · 想让左右两侧更浓 / 更淡：改 HEADER_VEIL_RADIUS_X_RATIO
 * 圆心处是 0 —— 车模正中最通透，从它往外才逐渐起雾。
 */
private val HEADER_VEIL_STOPS = arrayOf(
    0.00f to 0.00f,   // 车模正中：完全通透
    0.15f to 0.04f,   // 车模内部
    0.28f to 0.16f,   // 车模上 / 下沿附近
    0.40f to 0.36f,
    0.52f to 0.55f,   // 里程行 13sp
    0.66f to 0.68f,   // 续航大字 40sp
    0.80f to 0.75f,   // 车名 20sp
    0.92f to 0.78f,
    1.00f to 0.78f,   // 屏幕顶端：最浓，再往外保持不变
)

/**
 * 「支持开发者 / 开源协议与致谢 / QQ 交流群」这几个「面向社区」的模块是否显示。
 *
 * 当前已恢复对外发布，入口全部开放；若将来再转回纯自用，把这里改成 false 即可整体收起
 * （页面与路由都保留，不会编译不过）。
 */
internal const val SHOW_DEV_ENTRIES = true

/**
 * 「检查更新」是否启用：控制「我的」页入口与启动后的自动检测弹窗。
 *
 * 仓库已转回公开，检测与下载全部走匿名请求（APK 内不再携带任何令牌），因此恢复启用：
 * 启动时静默查一次最新 Release，确有新版本且未对该版本选择「不再提醒」才弹窗；
 * 「我的」页入口则随时可手动检查。临时想关掉的话改回 false 即可。
 */
internal const val SHOW_UPDATE_CHECK = true

/** 二级页路由：「我的」页 → 设置 / 个性化 / 玩法说明 / 关于（内嵌 免责声明、隐私条款）/ 车模外观 / 开源协议 / 支持开发者 */
enum class SettingsPage {
    SETTINGS,
    PERSONALIZATION,
    PLAYBOOK,
    CAR_APPEARANCE,
    ABOUT,
    DISCLAIMER,
    PRIVACY,
    OPEN_SOURCE,
    SUPPORT,
}

/** 底部 Dock：零控 / 零记 / 我的 */
@Composable
fun MainScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    // 零记总开关（设置 → 零记）。关掉后底部只剩「零控 / 我的」两格。
    val journalEnabled by runtime.journalEnabled
    // 选中项用「目标 tab 编号」存（0 零控 / 1 零记 / 2 我的），DockBar 要下标时再换算：
    // 直接把下标当状态的话，零记一关，下标 1 就从「零记」变成「我的」，页面会突然自己切走。
    val tabIds = remember(journalEnabled) {
        if (journalEnabled) listOf(0, 1, 2) else listOf(0, 2)
    }
    LaunchedEffect(journalEnabled) {
        // 正停在零记那一格时把它关掉 —— 回到首页，别留在一个已经不存在的 tab 上
        if (!journalEnabled && tab == 1) tab = 0
    }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    // 二级页返回栈：「关于 → 隐私条款 → 开源协议」这种链条要能逐级退回。
    // 之前没有栈，系统返回手势（或返回键）找不到可退的层级，会直接把 Activity 关掉。
    val settingsStack = remember { mutableStateListOf<SettingsPage>() }
    /**
     * 进入二级页：把当前页压栈，返回时就能原路回到上一级。
     *
     * 目标页若已经在栈里（「支持开发者 ⇄ 开源协议」这两个页面互相有入口），
     * 就当成「回退到它」处理并截断栈，否则来回点几次会叠出一长串重复项，
     * 返回键要按很多下才出得来。
     */
    fun openSettings(target: SettingsPage) {
        val current = settingsPage
        if (current == null) {
            settingsPage = target
            return
        }
        if (current == target) return
        val existing = settingsStack.indexOf(target)
        if (existing >= 0) {
            while (settingsStack.size > existing) settingsStack.removeAt(settingsStack.lastIndex)
            settingsPage = target
            return
        }
        settingsStack.add(current)
        settingsPage = target
    }
    /** 退一级：栈空则回「我的」页本体（settingsPage = null）。 */
    fun closeSettings() {
        settingsPage = if (settingsStack.isEmpty()) {
            null
        } else {
            settingsStack.removeAt(settingsStack.lastIndex)
        }
    }
    // 首页是否停在「主界面」（打开充电中心 / 空调 / 体检等二级页时为 false）。
    // 动态场景只在零控首页主界面跑动画，其余界面一律用主题色渐变。
    var homeOnMainPage by remember { mutableStateOf(true) }
    // 车模是否滑出视口：HomeScreen 通过 onCarModelGoneChange 上报。
    // 车模消失后场景层也要跟着隐去、换成纯色，避免画面细节干扰下方文字，提高可读性。
    // 刻意**不**用 by 解包：这两个值每次滑动都会变一次，在 MainScreen 的组合期读它们，
    // 会让整页（HomeScreen 两百多个节点）跟着重组 —— 而那一刻用户正好在滑动。
    // 以 State 形式传给下面专门的小 composable，让它们各自在自己的作用域里读。
    val carModelGone = remember { mutableStateOf(false) }
    // 首页是否正在滑动：滑动期间冻结动态场景。
    // 24s 一循环的慢动画在滑动时根本看不清，却要和滚动抢同一帧预算，冻结它最划算。
    val homeScrolling = remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(runtime.appearanceMode()) }
    // 两个主题槽位：浅色槽 / 深色槽。外观模式只决定「现在取哪一格」。
    // 跟随系统时两格都参与（白天一格、夜里一格），锁定模式只用得到对应那一格。
    var lightThemeId by remember { mutableStateOf(runtime.lightThemeId()) }
    var darkThemeId by remember { mutableStateOf(runtime.darkThemeId()) }
    // 快捷切换配置（小白点 / 点击切换 / 关闭）：读 State 才能「设置里一改、退回首页立刻生效」
    val quickSwitch by runtime.quickSwitch
    // 自定义背景的版本号（0 = 那张图不存在）。用来判断「车模这一块是不是正显示用户自己的壁纸」，
    // 攒在 MainScreen 里算一次，别让 HomeScreen 里那几个分支各算一遍。
    val customBackdropState by runtime.customBackdrop
    // 实时帧率浮层（设置 → 显示 → 显示帧率）
    val frameStatsVisible by runtime.frameStatsVisible
    // App 是否在前台：进后台就暂停解码，别在看不见的时候继续耗电
    var resumed by remember { mutableStateOf(true) }
    // 版本更新弹窗：检测到 GitHub 新版本且未忽略该版本时，启动后自动弹出
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    // 更新包下载状态：仓库已公开，下载全程匿名走 GitHub API
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }
    // 最小化：弹窗收起、下载照跑，底部留一条悬浮进度条
    var updateMinimized by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()

    val systemInDark = isSystemInDarkTheme()
    // 明暗来源只有一处：始终浅色/始终深色由模式锁死，跟随系统看手机
    val dark = ThemeResolver.isDark(appearance, systemInDark)
    // 生效主题：按明暗取对应槽位，槽里类型不对时自动回落到该类型默认主题
    val season = ThemeResolver.activeTheme(appearance, dark, lightThemeId, darkThemeId)
    val activeSeasonId = season.id
    // 用户自己的壁纸正在生效 → 车模侧的快捷换壁纸入口收起（原因见 QuickSwitchLock）。
    // 两个条件都必需：主题槽位选的是「我的壁纸」**且**当前明暗那一侧确实有图；
    // 图被移除后槽位还指着 custom，这时铺出来的是纯渐变，再锁住入口就没法换了。
    val themeCustomLocked = season.custom &&
        (if (dark) customBackdropState.night else customBackdropState.day) > 0L
    // ---- 快捷换壁纸（车模右上角的呼吸小白点）----
    // 在当前壁纸所属的池子里随机换一套：浅色只在日间池、深色只在夜间池、
    // 只在当前明暗那一侧的主题池里换，规则见 ThemeResolver.randomSwitch。
    // 只写「正在显示的那一侧槽位」，也不改外观模式 —— 用户点的是换张壁纸，不是换模式。
    // 注意：相伴卡上的那个点是换「卡片背景」，走 runtime.randomizeOwnershipBackdrop，别接到这里。
    val randomizeTheme = {
        val pick = ThemeResolver.randomSwitch(appearance, systemInDark, lightThemeId, darkThemeId)
        if (pick.lightThemeId != lightThemeId) {
            runtime.setLightThemeId(pick.lightThemeId)
            lightThemeId = pick.lightThemeId
        }
        if (pick.darkThemeId != darkThemeId) {
            runtime.setDarkThemeId(pick.darkThemeId)
            darkThemeId = pick.darkThemeId
        }
    }
    // 主题色跟着季节走：卡片描边、柱状图、强调元素都用它。
    // 自定义壁纸（season.custom）例外：颜色从壁纸里提取，取不到再退回主题自带的那一档 ——
    // 否则一张蓝色海景壁纸配默认的灰绿强调色，整页会显得脏。
    val wallpaperAccent = if (season.custom) {
        runtime.customWallpaperAccent(dark)?.let { Color(it) }
    } else {
        null
    }
    val themeAccent = wallpaperAccent ?: season.accent(dark)
    val colors = remember(dark, themeAccent) { appColors(dark, themeAccent) }
    // 页面底色渐变与 Material 配色只在「主题 / 明暗」变化时重建。
    // 每次重组都 new 一个 Brush 会让下层所有 draw 缓存失效，等于白做优化。
    val pageGradient = remember(season.id, dark) { Brush.verticalGradient(season.gradient(dark)) }
    val materialScheme = remember(dark, themeAccent, colors.bgTop) {
        if (dark) {
            darkColorScheme(primary = themeAccent, background = colors.bgTop)
        } else {
            lightColorScheme(primary = themeAccent, background = colors.bgTop)
        }
    }

    // 状态栏/导航栏透明，图标颜色随主题；关闭手势条对比度蒙层，让渐变一直透到底
    // 前后台事件：回前台恢复播放，进后台暂停
    LaunchedEffect(Unit) {
        ForegroundSignal.events.collect { resumed = true }
    }
    LaunchedEffect(Unit) {
        ForegroundSignal.pauseEvents.collect { resumed = false }
    }
    // 启动后检测一次 GitHub 更新；确有新版本且未对该版本选择「不再提醒」才弹窗。
    // 开关 SHOW_UPDATE_CHECK 关闭时整段不执行，App 不会主动联网查版本。
    // 频率由「我的 → 检查更新」右侧的「设置更新频率」决定（关闭 / 每次启动 / 7 / 14 / 30 天）：
    // 只有距上次成功检测期满才联网，OFF 档直接跳过 —— 这就是「仅手动检查更新」。
    LaunchedEffect(Unit) {
        if (!SHOW_UPDATE_CHECK) return@LaunchedEffect
        if (!runtime.shouldAutoCheckUpdate()) return@LaunchedEffect
        val result = runCatching { runtime.checkForUpdate() }.getOrNull()
        if (result is UpdateCheckResult.Newer && result.info.version != runtime.dismissedVersion()) {
            pendingUpdate = result.info
            updateMinimized = false
            showUpdateDialog = true
        }
    }

    val view = LocalView.current
    val activity = LocalContext.current as? ComponentActivity
    val density = LocalDensity.current
    // 状态栏高度（px）：圆弧遮罩的圆心要按它往下偏移，不同机型才对得齐。
    // 组合期拿不到 insets 时兜底 24dp（Android 状态栏的最小高度）
    val statusBarTopPx = remember(view, density) {
        val insetPx = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
        if (insetPx > 0) insetPx.toFloat() else with(density) { 24.dp.toPx() }
    }
    DisposableEffect(dark) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
        onDispose { }
    }

    // ---- 系统返回（返回键 / 左右滑返回手势）----
    // 层级：二级页 → 二级页的上一级 → 所属 Tab 回到零控首页 → 最后才交给系统退出。
    // 以前完全没有拦截，在任意二级页划一下手势就直接 finish 掉 Activity，
    // 看起来就像「一滑就退出 App」。内层页面（HomeScreen / JournalScreen）自己注册的
    // BackHandler 会覆盖这里，因为它在组合顺序上更靠后。
    BackHandler(enabled = settingsPage != null) { closeSettings() }
    BackHandler(enabled = settingsPage == null && tab != 0) { tab = 0 }

    ProvideAppColors(dark = dark, accent = themeAccent) {
        MaterialTheme(
            colorScheme = materialScheme,
        ) {
            // 二级页（设置 / 个性化 / 车模外观 / 关于 / 开源协议与致谢 / 支持开发者，
            // 关于内嵌 免责声明 与 隐私条款 两个子页）：
            // 非首页一律铺当前主题的主题色渐变（不跑场景动画）
            if (settingsPage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pageGradient),
                ) {
                    when (settingsPage) {
                        SettingsPage.SETTINGS -> SettingsScreen(
                            runtime = runtime,
                            onBack = { closeSettings() },
                        )

                        SettingsPage.PERSONALIZATION -> PersonalizationScreen(
                            runtime = runtime,
                            mode = appearance,
                            onModeChange = { mode ->
                                runtime.setAppearanceMode(mode)
                                appearance = mode
                            },
                            lightThemeId = lightThemeId,
                            darkThemeId = darkThemeId,
                            activeThemeId = activeSeasonId,
                            systemDark = systemInDark,
                            // 选完主题由 ThemeResolver 统一算出「模式 + 两槽」，
                            // 这里只负责落盘并同步内存状态，不做第二套判断
                            onApplyPick = { pick ->
                                if (pick.mode != appearance) {
                                    runtime.setAppearanceMode(pick.mode)
                                    appearance = pick.mode
                                }
                                runtime.setLightThemeId(pick.lightThemeId)
                                runtime.setDarkThemeId(pick.darkThemeId)
                                lightThemeId = pick.lightThemeId
                                darkThemeId = pick.darkThemeId
                            },
                            onOpenCarAppearance = { openSettings(SettingsPage.CAR_APPEARANCE) },
                            onBack = { closeSettings() },
                        )

                        // 玩法说明：「我的」页的常驻入口（首页右上角那个图标可以被关掉，
                        // 关掉之后这一页就没有别的入口了，所以这里必须留一个）
                        SettingsPage.PLAYBOOK -> PlaybookScreen(
                            onBack = { closeSettings() },
                            journalEnabled = journalEnabled,
                        )

                        // 车模外观从个性化进，返回也回个性化 —— 别让用户按一次返回直接掉回「我的」
                        SettingsPage.CAR_APPEARANCE -> CarAppearanceScreen(
                            runtime = runtime,
                            onBack = { closeSettings() },
                        )

                        SettingsPage.ABOUT -> AboutScreen(
                            runtime = runtime,
                            onBack = { closeSettings() },
                            onOpenDisclaimer = { openSettings(SettingsPage.DISCLAIMER) },
                            onOpenPrivacy = { openSettings(SettingsPage.PRIVACY) },
                        )

                        SettingsPage.DISCLAIMER -> DisclaimerScreen(onBack = { closeSettings() })

                        SettingsPage.PRIVACY -> PrivacyScreen(
                            onBack = { closeSettings() },
                            onOpenOpenSource = { openSettings(SettingsPage.OPEN_SOURCE) },
                        )

                        SettingsPage.OPEN_SOURCE -> OpenSourceScreen(
                            onBack = { closeSettings() },
                            onOpenSupport = { openSettings(SettingsPage.SUPPORT) },
                        )

                        SettingsPage.SUPPORT -> SupportScreen(
                            onBack = { closeSettings() },
                            onOpenOpenSource = { openSettings(SettingsPage.OPEN_SOURCE) },
                        )

                        null -> Unit
                    }
                }
                return@MaterialTheme
            }

            // 根布局铺四季渐变，系统栏区域与内容同源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageGradient),
            ) {
                // ---- 自定义壁纸：铺满整页（只有选中「我的壁纸」时才画）----
                // 画在最底层、渐变之上：图的四周由自带的一层薄纱压暗，
                // 保证车名与卡片在任意壁纸上都能读清。
                CustomWallpaperLayer(
                    runtime = runtime,
                    dark = dark,
                    enabled = season.custom,
                )

                // ---- 主题场景：只画在页面上半部分，往下淡出到看不见 ----
                // 场景覆盖「屏幕顶端 → 空调控件上沿」，车模与快捷控车压在已经淡化的画面上；
                // 不在首页主界面 / 进后台 / 正在滑动时都改用固定相位，完全静止、省电、也省帧。
                //
                // 「是否在滑动」「车模是否滑出」这两个值都会在滑动过程中变化，绝不能在本函数
                // 的组合期直接读：一读就让整个 MainScreen 重组（连带 HomeScreen 两百多个节点），
                // 而那一刻正好是用户手指在屏上的时候，表现出来就是「滑动一起手先卡一下」。
                // 所以把它们以 State 形式交给下面两个小 composable，让重组只发生在它们内部。
                val sceneAlpha: State<Float> = rememberSceneAlpha(carModelGone)
                // ---- 雾化遮罩：以车模正中心为圆心，向外圆弧发散 ----
                // 圆心处完全通透（车模不被压灰），越往四周越浓，屏幕顶端最浓。
                // 取主题渐变的最顶色，画面是「淡出进背景」而不是被压上一块别的颜色。
                val veilColor = season.gradient(dark).first()
                val veilRadiusY = with(density) { HEADER_VEIL_RADIUS_Y.toPx() }
                val veilCenterY = statusBarTopPx + with(density) {
                    (HEADER_VEIL_CONTENT_HEIGHT + CAR_ARTWORK_HEIGHT / 2).toPx()
                }
                val veilBrush = remember(veilColor, veilRadiusY, veilCenterY, season.veilAlphaScale) {
                    radialVeilBrush(veilColor, veilCenterY, veilRadiusY, season.veilAlphaScale)
                }
                // 场景层统一用 graphicsLayer 做透明度，动画期间不触发重组
                val sceneModifier = Modifier.graphicsLayer { alpha = sceneAlpha.value }

                SceneLayer(
                    theme = season,
                    dark = dark,
                    // 这里读的是低频状态（切 Tab、进出二级页、前后台），不会每帧变
                    active = tab == 0 && homeOnMainPage && resumed,
                    scrolling = homeScrolling,
                    veilBrush = veilBrush,
                    modifier = sceneModifier,
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    // 关掉 Scaffold 自带的状态栏/导航栏 inset，避免和页面内 statusBarsPadding 叠加
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        DockBar(
                            // 零记被关掉时 DockBar 只有两项，下标要跟着重算
                            selectedIndex = tabIds.indexOf(tab).coerceAtLeast(0),
                            onSelect = { tab = tabIds[it] },
                            items = buildList {
                                add(NavItem("零控", iconRes = R.drawable.ic_nav_car))
                                // 「零记」：用车目标 / 用车成本 / 城市打卡，数据全部只存本机。
                                // 设置里可以整个关掉，关掉后这一格不再出现。
                                if (journalEnabled) {
                                    add(NavItem("零记", iconRes = R.drawable.ic_nav_map))
                                }
                                add(NavItem("我的", iconRes = R.drawable.ic_nav_person))
                            },
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        when (tab) {
                            0 -> HomeScreen(
                                runtime = runtime,
                                onLogout = onLogout,
                                onOpenSkin = { openSettings(SettingsPage.PERSONALIZATION) },
                                // 两处快捷切换各管一摊：车模换主题壁纸，相伴卡换卡片背景
                                onRandomTheme = randomizeTheme,
                                onRandomBackdrop = { runtime.randomizeOwnershipBackdrop() },
                                quickSwitch = quickSwitch,
                                themeCustomLocked = themeCustomLocked,
                                onMainPageChange = { homeOnMainPage = it },
                                onCarModelGoneChange = { carModelGone.value = it },
                                onScrollingChange = { homeScrolling.value = it },
                            )

                            1 -> JournalScreen(runtime = runtime)

                            else -> ProfileScreen(
                                runtime = runtime,
                                onLogout = onLogout,
                                onOpenPage = { openSettings(it) },
                            )
                        }
                    }
                }
            }

            // ---- 实时帧率浮层（设置里可关，默认不显示）----
            // 放在 Scaffold 之后：要盖在内容与 Dock 之上，才看得到真实出帧情况
            val frameStats = rememberFrameStats(frameStatsVisible)
            if (frameStatsVisible) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FrameStatsBadge(
                        stats = frameStats,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 6.dp, end = 14.dp),
                    )
                }
            }

            // ---- 版本更新提醒弹窗（覆盖全局，最高层级）----
            // 最小化时不画弹窗，改由下面的悬浮条接管（下载协程不受影响，继续跑）
            if (showUpdateDialog && pendingUpdate != null && !updateMinimized) {
                UpdateDialog(
                    info = pendingUpdate!!,
                    currentVersion = runtime.currentVersion(),
                    downloading = updateDownloading,
                    progress = updateProgress,
                    downloadError = updateDownloadError,
                    onMinimize = { updateMinimized = true },
                    onOpenDownload = {
                        val info = pendingUpdate
                        if (info != null && !updateDownloading) {
                            updateDownloading = true
                            updateProgress = null
                            updateDownloadError = null
                            updateScope.launch {
                                val result = runtime.downloadUpdate(info) { p ->
                                    updateProgress = p.fraction
                                }
                                updateDownloading = false
                                result.fold(
                                    onSuccess = { file ->
                                        if (runtime.installUpdate(file)) {
                                            showUpdateDialog = false
                                            updateMinimized = false
                                        } else {
                                            updateDownloadError =
                                                "安装包已下载，但需要先在系统里允许「安装未知应用」，授权后回来再点一次"
                                        }
                                    },
                                    onFailure = { e ->
                                        updateDownloadError = "下载失败：${e.message ?: "网络异常"}"
                                    },
                                )
                            }
                        }
                    },
                    onOpenGithub = {
                        runtime.openExternal(pendingUpdate!!.htmlUrl)
                        showUpdateDialog = false
                    },
                    onDismiss = {
                        // 下载中收起弹窗＝转最小化：进度条还在，别让用户以为下载没了
                        if (updateDownloading) updateMinimized = true else showUpdateDialog = false
                    },
                    onNeverAgain = {
                        runtime.setDismissedVersion(pendingUpdate!!.version)
                        updateMinimized = false
                        showUpdateDialog = false
                    },
                )
            }

            // ---- 最小化后的更新悬浮条：贴在底部导航上方 ----
            val miniInfo = pendingUpdate
            if (updateMinimized && miniInfo != null) {
                // 外面套一层 Box 只为拿 BoxScope（父级是 Column，没有 align）
                Box(modifier = Modifier.fillMaxSize()) {
                    UpdateMiniBar(
                        version = miniInfo.version,
                        downloading = updateDownloading,
                        progress = updateProgress,
                        error = updateDownloadError,
                        onExpand = { updateMinimized = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 22.dp, end = 22.dp, bottom = 104.dp),
                    )
                }
            }
        }
    }
}

/**
 * 背景场景层（手绘静态 / 动态主题）。
 *
 * 单独抽成一个 composable 只为一件事：`scrolling` 这个 State 在[这里]读，
 * 而不是在 [MainScreen] 里读。开始/结束滑动各会改一次这个值，如果在 MainScreen 读，
 * 每次都要把整页（含 HomeScreen）重组一遍 —— 那一下正好发生在手指刚按下的时候，
 * 手感上就是「滑动起步顿一下」。收进这里之后，重组只影响这一小块背景。
 */
@Composable
private fun SceneLayer(
    theme: SeasonTheme,
    dark: Boolean,
    active: Boolean,
    scrolling: State<Boolean>,
    veilBrush: Brush?,
    modifier: Modifier = Modifier,
) {
    val scrollingNow by scrolling
    val animate = active && !scrollingNow
    if (theme.scene != SceneKind.NONE) {
        ThemeBackdrop(
            theme = theme,
            dark = dark,
            animate = animate,
            veilBrush = veilBrush,
            modifier = modifier,
        )
    }
}

/**
 * 场景层透明度：车模滑出视口后淡到 30%。
 *
 * 同样要单独成一个 composable：`gone` 在读的这一刻属于本函数的重组作用域，
 * 车模滑出时只有这里重组 + 420ms 的动画在 graphicsLayer 里跑，不会牵动整页。
 */
@Composable
private fun rememberSceneAlpha(gone: State<Boolean>): State<Float> =
    animateFloatAsState(
        targetValue = if (gone.value) 0.30f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sceneAlpha",
    )

/**
 * 以「车模正中心」为圆心的椭圆形雾化遮罩。
 *
 * Compose 的 `Brush.radialGradient` 只支持正圆，而竖屏高远大于宽 —— 用正圆会让画面
 * 左右两侧跟着顶部一起糊死。所以这里用原生 `RadialGradient`（正圆）+ 横向缩放矩阵，
 * 把它拉成椭圆：纵向半径保持 [radiusYPx]，横向半径 = [radiusYPx] ×
 * HEADER_VEIL_RADIUS_X_RATIO。
 *
 * 超出半径的部分用 CLAMP 停在最外圈颜色上，所以屏幕四角与最浓处一致，不会漏出硬边。
 */
private fun radialVeilBrush(
    color: Color,
    centerYPx: Float,
    radiusYPx: Float,
    alphaScale: Float = 1f,
): Brush {
    // 色表与位置只跟颜色有关，提到闭包外算一次，别在每次 createShader 时重复分配
    val argbColors = IntArray(HEADER_VEIL_STOPS.size) { i ->
        color.copy(alpha = HEADER_VEIL_STOPS[i].second * alphaScale).toArgb()
    }
    val positions = FloatArray(HEADER_VEIL_STOPS.size) { i -> HEADER_VEIL_STOPS[i].first }
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val cx = size.width / 2f
            val rx = radiusYPx * HEADER_VEIL_RADIUS_X_RATIO
            val gradient = RadialGradient(
                cx,
                centerYPx,
                radiusYPx,
                argbColors,
                positions,
                Shader.TileMode.CLAMP,
            )
            // 正圆 → 椭圆：只横向拉伸，纵向不动
            gradient.setLocalMatrix(Matrix().apply { setScale(rx / radiusYPx, 1f, cx, centerYPx) })
            return gradient
        }
    }
}
