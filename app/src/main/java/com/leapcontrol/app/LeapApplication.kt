package com.leapcontrol.app

import android.app.Application

/** 尽早安装崩溃兜底，覆盖 MainActivity 之前（ContentProvider / Application 阶段）的异常。 */
class LeapApplication : Application() {

    override fun onCreate() {
        CrashGuard.install(this)
        super.onCreate()
        // 性能日志采集器的落盘目录（filesDir），越早注入越好
        com.leapcontrol.app.ui.PerfLogger.inject(this)
        // 桌面图标：升级安装会把 activity-alias 的 enabled 状态重置回清单默认值，
        // 所以每次启动按用户上次的选择校准一次，保证图标不会自己跳回默认。
        runCatching {
            LauncherIcon.ensureApplied(this, SessionStore(this).loadLauncherIconStyle())
        }
        // 车模图预热：把磁盘上那张解码进内存。首页第一帧因此能直接画出来，
        // 不用先空一块车模区等异步解码（冷启动"闪一下"的来源）。
        runCatching { com.leapcontrol.app.ui.CarImagePreload.warm(this) }
    }
}
