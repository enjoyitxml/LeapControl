package com.leapcontrol.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「从 App 外面点进来，要直达哪个页面」。
 *
 * 桌面小组件和下拉磁贴在发现「还没设置控车密码」时会把用户领进 App，但把人丢在首页
 * 没有用 —— 他还得自己去翻「我的 → 设置 → 控车密码」。MainActivity 从启动 Intent
 * 里读目标写在这里，MainScreen 组合时消费一次并清空。
 *
 * 用 StateFlow 而不是 SharedFlow：冷启动时 Intent 在 setContent 之前就解析完了，
 * 没有 replay 的 SharedFlow 会把这唯一一条请求直接丢掉。
 */
object AppNavRequest {

    /** 直达「设置 → 控车密码」：打开设置页并自动弹出密码录入框。 */
    const val OP_PASSWORD = "op_password"

    /**
     * 登录已失效，进来只为了重新登录。
     *
     * 由桌面小组件 / 控制中心磁贴带进来：它们在外面发现 token 挂了，除了弹 toast
     * 没法把用户送回登录流程（后台 toast 在不少 ROM 上还会被直接吞掉）。
     */
    const val RELOGIN = "relogin"

    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target.asStateFlow()

    fun request(target: String) {
        _target.value = target
    }

    fun consume() {
        _target.value = null
    }
}
