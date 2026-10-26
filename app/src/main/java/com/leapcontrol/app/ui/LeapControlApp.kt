package com.leapcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** 应用根组件：根据登录态路由到登录页或首页。 */
@Composable
fun LeapControlApp() {
    val context = LocalContext.current
    var runtimeKey by remember { mutableIntStateOf(0) }
    val runtime = remember(runtimeKey) { AppRuntime(context) }
    var loggedIn by remember(runtimeKey) { mutableStateOf(runtime.isLoggedIn) }

    if (loggedIn) {
        MainScreen(
            runtime = runtime,
            onLogout = { runtimeKey += 1 },
        )
    } else {
        LoginScreen(
            runtime = runtime,
            onLoginSuccess = { loggedIn = true },
        )
    }
}
