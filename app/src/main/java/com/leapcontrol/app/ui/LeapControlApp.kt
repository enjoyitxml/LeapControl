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
                    // 车模预热图跟着会话一起作废，别让下一位车主在首帧看到上一个人的车
                    CarImagePreload.clear()
                    runtimeKey += 1
                },
            )
        }
    } else {
        LoginScreen(
            runtime = runtime,
            onLoginSuccess = {
                // 登录成功：从桌面带进来的 RELOGIN / OP_PASSWORD 请求已经过期了。
                // 不清的话，下一次进主界面会突然弹一句「登录已失效」，而人刚刚才登录过。
                AppNavRequest.consume()
                // 首次登录时 Application 那次预热读不到 VIN（还没登录），这里补一次；
                // 冷启动本来就有登录态的话，上面那次已经解好了，warm 内部会直接返回
                CarImagePreload.warm(context)
                loggedIn = true
            },
        )
    }
}
