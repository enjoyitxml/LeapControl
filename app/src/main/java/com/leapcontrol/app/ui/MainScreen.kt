package com.leapcontrol.app.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.leapcontrol.app.AppearanceMode
import com.leapcontrol.app.R

/** 底部 Dock：首页 / 我的 */
@Composable
fun MainScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(runtime.appearanceMode()) }
    // 四季背景提到根布局：状态栏、Dock、导航条区域都透出同一套渐变
    var seasonId by remember { mutableStateOf(runtime.seasonThemeId()) }

    val systemInDark = isSystemInDarkTheme()
    val dark = appearance.resolvesToDark(systemInDark)
    val colors = appColors(dark)
    val season = SeasonThemes.byId(seasonId)

    // 状态栏/导航栏透明，图标颜色随主题；关闭手势条对比度蒙层，让渐变一直透到底
    val view = LocalView.current
    val activity = LocalContext.current as? ComponentActivity
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
            if (showSettings) {
                SettingsScreen(
                    runtime = runtime,
                    mode = appearance,
                    onModeChange = { mode ->
                        runtime.setAppearanceMode(mode)
                        appearance = mode
                    },
                    onSeasonChange = { id ->
                        runtime.setSeasonThemeId(id)
                        seasonId = id
                    },
                    onBack = { showSettings = false },
                )
                return@MaterialTheme
            }
            // 根布局铺四季渐变，系统栏区域与内容同源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(season.gradient(dark))),
            ) {
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
                                onOpenSettings = { showSettings = true },
                                seasonId = seasonId,
                                onSeasonChange = {
                                    runtime.setSeasonThemeId(it)
                                    seasonId = it
                                },
                            )

                            else -> ProfileScreen(
                                runtime = runtime,
                                onLogout = onLogout,
                                onOpenSettings = { showSettings = true },
                            )
                        }
                    }
                }
            }
        }
    }
}
