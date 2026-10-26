package com.leapcontrol.app

import android.content.Context
import android.content.Intent
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * 崩溃兜底：把未捕获异常的堆栈写到文件，并拉起 [CrashActivity] 直接显示出来，
 * 方便在没有 adb 的情况下定位问题。
 */
object CrashGuard {

    const val EXTRA_TRACE = "trace"

    fun install(context: Context) {
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { _: Thread, e: Throwable ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val device = "设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                "系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                "版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n"
            val trace = device + sw.toString()

            // 1) 落盘：Android/data/com.leapcontrol.app/files/crash.txt
            runCatching {
                app.getExternalFilesDir(null)?.let { dir ->
                    File(dir, "crash.txt").writeText(trace)
                }
            }
            runCatching {
                File(app.filesDir, "crash.txt").writeText(trace)
            }

            // 2) 显示到屏幕
            runCatching {
                val intent = Intent(app, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(EXTRA_TRACE, trace)
                }
                app.startActivity(intent)
            }

            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
