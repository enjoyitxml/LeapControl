package com.leapcontrol.app.ui

import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.leapcontrol.app.AppearanceMode
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.R
import com.leapcontrol.app.UpdateCheckResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

// ============================================================================
// 雾化遮罩（以车模正中心为圆心的椭圆）—— 调参入口，改这里即可，不用动下面的布局
// ----------------------------------------------------------------------------
// 作用：视频主景（花枝/树冠/落叶/飘雪）落在视频上部 38%，而首页文字（车名 /
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

/** 「我的」页的入口：设置 / 个性化 / 关于 / 免责声明 / 隐私条款 / 开源协议 / 支持开发者 */
enum class SettingsPage {
    SETTINGS,
    PERSONALIZATION,
    ABOUT,
    DISCLAIMER,
    PRIVACY,
    OPEN_SOURCE,
    SUPPORT,
}

/** 底部 Dock：首页 / 我的 */
@Composable
fun MainScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    // 首页是否停在「主界面」（打开充电中心 / 空调 / 体检等二级页时为 false）。
    // 动态背景视频只在零控首页主界面播放，其余界面一律用主题色渐变。
    var homeOnMainPage by remember { mutableStateOf(true) }
    // 车模是否滑出视口：HomeScreen 通过 onCarModelGoneChange 上报。
    // 车模消失后视频背景也要跟着隐去、换成纯色，避免画面细节干扰下方文字，提高可读性。
    var carModelGone by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(runtime.appearanceMode()) }
    // 动态背景：视频开关与声音开关。设置页改完后回到首页要立刻生效，
    // 所以状态提在这里持有，而不是让设置页自己去读一次就不变了
    var videoEnabled by remember { mutableStateOf(runtime.seasonVideoEnabled()) }
    var videoSound by remember { mutableStateOf(runtime.seasonVideoSoundEnabled()) }
    var nightAuto by remember { mutableStateOf(runtime.nightThemeAuto()) }
    // App 是否在前台：进后台就暂停解码，别在看不见的时候继续耗电
    var resumed by remember { mutableStateOf(true) }
    // 四季背景提到根布局：状态栏、Dock、导航条区域都透出同一套渐变
    var seasonId by remember { mutableStateOf(runtime.seasonThemeId()) }
    // 版本更新弹窗：检测到 GitHub 新版本且未忽略该版本时，启动后自动弹出
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    // ---- 夜间自动主题 ----
    // 每 30 秒复核一次当前时间，跨过 22:00 / 06:00 时立刻生效，不必等用户重启 App。
    // 用户自己选的季节原样保留，白天自动还原。
    var nightTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nightTick++
        }
    }
    val autoNightActive = remember(nightAuto, nightTick) {
        nightAuto && SeasonThemes.isNight()
    }
    val activeSeasonId = if (autoNightActive) SeasonThemes.NIGHT.id else seasonId
    val season = SeasonThemes.byId(activeSeasonId)

    val systemInDark = isSystemInDarkTheme()
    // 星空主题底色就是深空黑，必须按深色取内容色，否则会变成黑底黑字；
    // 其余主题仍照「跟随系统 / 始终浅色 / 始终深色」的用户设置走。
    val dark = season.forcesDarkContent || appearance.resolvesToDark(systemInDark)
    val colors = appColors(dark)

    // 状态栏/导航栏透明，图标颜色随主题；关闭手势条对比度蒙层，让渐变一直透到底
    // 前后台事件：回前台恢复播放，进后台暂停
    LaunchedEffect(Unit) {
        ForegroundSignal.events.collect { resumed = true }
    }
    LaunchedEffect(Unit) {
        ForegroundSignal.pauseEvents.collect { resumed = false }
    }
    // 启动后检测一次 GitHub 更新；确有新版本且未对该版本选择「不再提醒」才弹窗
    LaunchedEffect(Unit) {
        val result = runCatching { runtime.checkForUpdate() }.getOrNull()
        if (result is UpdateCheckResult.Newer && result.info.version != runtime.dismissedVersion()) {
            pendingUpdate = result.info
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

    ProvideAppColors(dark = dark) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(primary = colors.accent, background = colors.bgTop)
            } else {
                lightColorScheme(primary = colors.accent, background = colors.bgTop)
            },
        ) {
            // 二级页（设置 / 个性化 / 支持开发者 / 开源协议与致谢 / 关于；
            // 关于内嵌 免责声明 与 隐私条款 两个子页）：不播视频，统一铺当前季节的主题色渐变
            if (settingsPage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(season.gradient(dark))),
                ) {
                    when (settingsPage) {
                        SettingsPage.SETTINGS -> SettingsScreen(
                            runtime = runtime,
                            onBack = { settingsPage = null },
                        )

                        SettingsPage.PERSONALIZATION -> PersonalizationScreen(
                            runtime = runtime,
                            mode = appearance,
                            onModeChange = { mode ->
                                runtime.setAppearanceMode(mode)
                                appearance = mode
                            },
                            seasonId = seasonId,
                            onSeasonChange = { id ->
                                runtime.setSeasonThemeId(id)
                                seasonId = id
                            },
                            activeSeasonId = activeSeasonId,
                            autoNightActive = autoNightActive,
                            videoEnabled = videoEnabled,
                            onVideoEnabledChange = {
                                runtime.setSeasonVideoEnabled(it)
                                videoEnabled = it
                            },
                            videoSound = videoSound,
                            onVideoSoundChange = {
                                runtime.setSeasonVideoSoundEnabled(it)
                                videoSound = it
                            },
                            nightAuto = nightAuto,
                            onNightAutoChange = {
                                runtime.setNightThemeAuto(it)
                                nightAuto = it
                            },
                            onBack = { settingsPage = null },
                        )

                        SettingsPage.ABOUT -> AboutScreen(
                            runtime = runtime,
                            onBack = { settingsPage = null },
                            onOpenDisclaimer = { settingsPage = SettingsPage.DISCLAIMER },
                            onOpenPrivacy = { settingsPage = SettingsPage.PRIVACY },
                        )

                        SettingsPage.DISCLAIMER -> DisclaimerScreen(onBack = { settingsPage = null })

                        SettingsPage.PRIVACY -> PrivacyScreen(
                            onBack = { settingsPage = null },
                            onOpenOpenSource = { settingsPage = SettingsPage.OPEN_SOURCE },
                        )

                        SettingsPage.OPEN_SOURCE -> OpenSourceScreen(
                            onBack = { settingsPage = null },
                            onOpenSupport = { settingsPage = SettingsPage.SUPPORT },
                        )

                        SettingsPage.SUPPORT -> SupportScreen(
                            onBack = { settingsPage = null },
                            onOpenOpenSource = { settingsPage = SettingsPage.OPEN_SOURCE },
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
                    .background(Brush.verticalGradient(season.gradient(dark))),
            ) {
                // ---- 四季动态背景：视频在最底层，主景在上部，车辆与控件压在下方留空区 ----
                // 视频层「常驻」组合、不随切页 / 进二级页卸载：用 alpha 显隐 + paused 控制播放，
                // 切回首页时画面连续、不会从头起播闪烁一下（我的页、设置/个性化/关于、
                // 以及首页二级页不显示视频，纯主题色渐变，靠 alpha=0 隐藏、暂停解码省电）。
                //
                // 车模滑出视口时的背景切换做「交叉淡入淡出」：视频 alpha 渐隐到 0 的同时，
                // 纯色背景层 alpha 渐入到 1；雾化遮罩 / 下缘渐隐跟随视频一起渐隐，避免硬切突兀。
                val videoActive = videoEnabled && tab == 0 && homeOnMainPage && !carModelGone
                val videoAlpha by animateFloatAsState(
                    targetValue = if (videoActive) 1f else 0f,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                    label = "videoAlpha",
                )
                // 纯色背景层：车模滑出后渐入；滑回时渐出，与视频形成柔和交叉过渡
                val solidAlpha by animateFloatAsState(
                    targetValue = if (carModelGone) 1f else 0f,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                    label = "solidAlpha",
                )
                if (videoEnabled) {
                    // 视频层：始终挂载（carModelGone 时 alpha 渐隐到 0 而非直接卸载，保证淡出连续）
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(videoAlpha),
                    ) {
                        SeasonVideoBackground(
                            seasonId = activeSeasonId,
                            muted = !videoSound,
                            // 不在前台、不在首页主界面、或车模已滑出视口都暂停解码；
                            // 切回时从当前帧续播，不闪。
                            paused = !resumed || !videoActive,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    // 纯色背景层：车模滑出后交叉淡入，盖住根渐变，文字压在干净底色上更清晰
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(solidAlpha)
                            .background(season.gradient(dark).last()),
                    )
                    // 雾化遮罩 + 下缘渐隐：仅视频显示时才有意义，跟随 videoAlpha 一起渐隐
                    Box(Modifier.matchParentSize().alpha(videoAlpha)) {
                        // ---- 雾化遮罩：以车模正中心为圆心，向外圆弧发散 ----
                        // 圆心处完全通透（车模不被压灰），越往四周越浓，屏幕顶端最浓。
                        // 取主题渐变的最顶色，视频是「淡出进背景」而不是被压上一块别的颜色。
                        val veilColor = season.gradient(dark).first()
                        val veilRadiusY = with(density) { HEADER_VEIL_RADIUS_Y.toPx() }
                        val veilCenterY = statusBarTopPx + with(density) {
                            (HEADER_VEIL_CONTENT_HEIGHT + CAR_ARTWORK_HEIGHT / 2).toPx()
                        }
                        val veilBrush = remember(veilColor, veilRadiusY, veilCenterY, season.veilAlphaScale) {
                            radialVeilBrush(veilColor, veilCenterY, veilRadiusY, season.veilAlphaScale)
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(veilBrush),
                        )
                        // 下缘渐隐回主题色：主景留在画面上部，从快捷控件上沿（约 38%）开始压暗，
                        // 到控件区已接近纯色 —— 文字与组件压在干净底色上，不会被画面细节吃掉。
                        val scrim = season.gradient(dark).last()
                        val scrimScale = season.scrimAlphaScale
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Transparent,
                                        0.38f to Color.Transparent,
                                        0.50f to scrim.copy(alpha = 0.55f * scrimScale),
                                        0.62f to scrim.copy(alpha = 0.85f * scrimScale),
                                        0.78f to scrim.copy(alpha = 0.95f * scrimScale),
                                        1.00f to scrim.copy(alpha = 0.98f * scrimScale),
                                    )
                                )
                        )
                    }
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    // 关掉 Scaffold 自带的状态栏/导航栏 inset，避免和页面内 statusBarsPadding 叠加
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        DockBar(
                            selectedIndex = tab,
                            onSelect = { tab = it },
                            items = listOf(
                                NavItem("零控", iconRes = R.drawable.ic_nav_car),
                                // 「零记」页暂时下线：该板块待后续版本开发，当前版本优先把控车做稳。
                                // 恢复时把下面这行加回来，并把 when(tab) 的分支改回去即可。
                                // NavItem("零记", iconRes = R.drawable.ic_nav_map),
                                NavItem("我的", iconRes = R.drawable.ic_nav_person),
                            ),
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        // 「零记」已下线，tab=1 现在是「我的」
                        when (tab) {
                            0 -> HomeScreen(
                                runtime = runtime,
                                onLogout = onLogout,
                                onOpenSkin = { settingsPage = SettingsPage.PERSONALIZATION },
                                seasonId = activeSeasonId,
                                onSeasonChange = {
                                    runtime.setSeasonThemeId(it)
                                    seasonId = it
                                },
                                onMainPageChange = { homeOnMainPage = it },
                                onCarModelGoneChange = { carModelGone = it },
                            )

                            else -> ProfileScreen(
                                runtime = runtime,
                                onLogout = onLogout,
                                onOpenPage = { settingsPage = it },
                            )
                        }
                    }
                }
            }

            // ---- 版本更新提醒弹窗（覆盖全局，最高层级）----
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
    }
}

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
