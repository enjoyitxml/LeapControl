package com.leapcontrol.app

import android.app.Application

/** 尽早安装崩溃兜底，覆盖 MainActivity 之前（ContentProvider / Application 阶段）的异常。 */
class LeapApplication : Application() {
    override fun onCreate() {
        CrashGuard.install(this)
        super.onCreate()
    }
}
