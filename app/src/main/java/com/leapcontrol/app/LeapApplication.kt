package com.leapcontrol.app

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import com.leapcontrol.app.widget.VehicleWidget

/** 尽早安装崩溃兜底，覆盖 MainActivity 之前（ContentProvider / Application 阶段）的异常。 */
class LeapApplication : Application() {

    /**
     * 系统深浅模式一变，小组件要跟着换配色（浅底深字 / 深底浅字）。
     *
     * 只能用运行时注册：CONFIGURATION_CHANGED 是受保护广播，
     * 在清单里声明收不到。App 进程活着时（用 App 的人几乎都活着）这一下就够了，
     * 进程被杀期间切换，等下次 App 刷新状态时自然会带上新的明暗。
     */
    private val widgetThemeWatcher = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            runCatching { VehicleWidget.render(this@LeapApplication) }
        }

        override fun onLowMemory() = Unit
    }

    override fun onCreate() {
        CrashGuard.install(this)
        super.onCreate()
        registerComponentCallbacks(widgetThemeWatcher)
    }
}
