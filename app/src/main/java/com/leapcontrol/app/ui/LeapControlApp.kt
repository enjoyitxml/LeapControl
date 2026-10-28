package com.leapcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
        // 蓝牙钥匙宿主只存在于登录态：它是进程级单例，登出时要把会话与后台服务一起收掉
        val bleHost = rememberBleKeyHost(runtime)
        CompositionLocalProvider(LocalBleKeyHost provides bleHost) {
            MainScreen(
                runtime = runtime,
                onLogout = {
                    // 登出必须显式收掉蓝牙钥匙会话：把已绑定的钥匙挂起、断开后台服务，
                    // 否则换账号后仍会带着上一个人的绑定去连车
                    runCatching { bleHost.ble.endSession() }
                    runtimeKey += 1
                },
            )
        }
    } else {
        LoginScreen(
            runtime = runtime,
            onLoginSuccess = { loggedIn = true },
        )
    }
}
