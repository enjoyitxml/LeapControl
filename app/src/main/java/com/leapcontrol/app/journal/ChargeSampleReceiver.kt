package com.leapcontrol.app.journal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.leapcontrol.app.ui.AppRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 充电期间的采样广播（见 [ChargeWatch]）。
 *
 * 做的只有一件事：拉一次车况，让 [ChargeCycle] 把「充到多少」抬到当前电量，
 * 然后按新的充电状态续下一次闹钟。不弹窗、不发声、不碰控车指令 —— 用户在睡觉，
 * 这是后台行为该有的分寸。
 *
 * ⚠️ 走 [goAsync] 的广播大约 10 秒就会被系统判超时，所以整条链路有 8 秒上限；
 * 超时（断网 / 网关慢）时按 [ChargeWatch.retryLater] 排下一次，不放弃整次充电。
 */
class ChargeSampleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChargeWatch.ACTION) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val runtime = runCatching { AppRuntime(appContext) }.getOrNull()
                val session = runtime?.session
                if (runtime == null || session == null ||
                    session.selectedVin.isBlank() ||
                    (session.oldAuth == null && session.newAuth == null)
                ) {
                    // 没登录 / 没选车：这条链路没有意义，撤掉闹钟等下次进 App 再说
                    ChargeWatch.cancel(appContext)
                    return@launch
                }
                val status = runCatching {
                    withTimeoutOrNull(SAMPLE_TIMEOUT_MS) { runtime.refreshStatus() }
                }.getOrNull()
                // 成功时 refreshStatus 内部已经按新状态续过闹钟；失败才由这里兜底重试
                if (status == null) ChargeWatch.retryLater(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** 广播 10 秒预算，留 2 秒给进程启动与收尾。 */
        const val SAMPLE_TIMEOUT_MS = 8_000L
    }
}
