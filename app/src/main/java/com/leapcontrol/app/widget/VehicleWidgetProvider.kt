package com.leapcontrol.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.leapcontrol.app.WidgetControlSecurity
import com.leapcontrol.app.ui.AppRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 桌面小组件（4x2）的广播接收器。
 *
 * 点击按键 → 本进程内直接下发控车指令 → 回读车况 → 重画卡片，
 * 不需要常驻服务，也不需要打开 App。
 *
 * ⚠️ 这里所有网络动作都必须在 ~9 秒内结束：广播接收器走 [goAsync]，
 * 超过 10 秒就会被系统判定超时。所以控车用的是
 * [AppRuntime.controlForWidget]（短轮询），而不是 App 内那套最长 20 秒的 `control()`。
 */
class VehicleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 先用磁盘缓存的车况画一帧（冷启动也立刻有内容），再后台联网刷新
        runCatching { VehicleWidget.render(context) }
        val appContext = context.applicationContext
        work.launch { refresh(appContext) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 改尺寸后重画一次，避免残留旧宽高下的位图
        runCatching { VehicleWidget.render(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != VehicleWidget.ACTION_UNLOCK &&
            action != VehicleWidget.ACTION_LOCK &&
            action != VehicleWidget.ACTION_TOGGLE_AC &&
            action != VehicleWidget.ACTION_TOGGLE_TRUNK &&
            action != VehicleWidget.ACTION_HORN
        ) {
            return
        }
        runCatching { handleAction(context, action) }
    }

    // ---------------- 内部实现 ----------------

    private fun handleAction(context: Context, action: String) {
        val appContext = context.applicationContext
        val pending = goAsync()
        val runtime = runCatching { AppRuntime(appContext) }.getOrNull()
        if (runtime == null) {
            pending.finish()
            toast(appContext, "零控初始化失败，请打开 App 后重试")
            return
        }

        // 登录态 / 密码的检查放在广播线程里同步做完，失败了就没必要起后台任务
        val session = runtime.session
        if (session.selectedVin.isBlank() || (session.oldAuth == null && session.newAuth == null)) {
            pending.finish()
            toast(appContext, "请先登录零控（点卡片打开 App）")
            return
        }
        if (runtime.sessionStore.loadOpPassword().isNullOrBlank()) {
            pending.finish()
            toast(appContext, "请先在「我的」里设置 4 位控车密码")
            return
        }

        work.launch {
            try {
                execute(appContext, runtime, action)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun execute(context: Context, runtime: AppRuntime, action: String) {
        // 开关式按键（空调 / 后备箱）需要先知道「现在是什么状态」才能决定往哪个方向切。
        // 单次指令（解锁 / 上锁）不看状态，直接发。
        // 先吃磁盘缓存的上一份车况（瞬时，且通常就是几分钟内的），缓存太旧或没有才联网拉。
        runtime.loadCachedStatus()
        var status = runtime.statusFlow.value
        val cacheAge = System.currentTimeMillis() - runtime.lastUpdatedFlow.value
        val needsState = action == VehicleWidget.ACTION_TOGGLE_AC ||
            action == VehicleWidget.ACTION_TOGGLE_TRUNK
        if (needsState && (status == null || cacheAge > CACHE_FRESH_MS)) {
            status = runCatching { withTimeoutOrNull(4_000) { runtime.refreshStatus() } }.getOrNull() ?: status
        }

        val command = when (action) {
            VehicleWidget.ACTION_UNLOCK -> "unlock"
            VehicleWidget.ACTION_LOCK -> "lock"
            VehicleWidget.ACTION_TOGGLE_AC -> if (status?.acOn == true) "acOff" else "acOn"
            VehicleWidget.ACTION_TOGGLE_TRUNK -> if (status?.trunkOpen == true) "trunkClose" else "trunkOpen"
            VehicleWidget.ACTION_HORN -> "horn"
            else -> return
        }

        // 开后备箱属于不可撤销动作：沿用 App 内的「3 秒内再点一次确认」策略
        if (command == "trunkOpen" && !confirmTrunkOpen(context)) {
            toast(context, "再点一次后备箱确认打开")
            return
        }

        VehicleWidget.render(context, runtime, busy = labelOf(command))

        val message = runCatching {
            withTimeoutOrNull(7_500) { runtime.controlForWidget(command) }
        }.getOrNull() ?: "指令已下发，请在车上确认"

        // 回读车况：锁车 / 空调 / 后备箱信号变了才算真的生效
        runCatching { withTimeoutOrNull(6_000) { runtime.refreshStatus() } }
        runCatching { VehicleWidget.ensureCar(context, runtime) }
        VehicleWidget.render(context, runtime)
        toast(context, message)
    }

    private fun labelOf(command: String): String = when (command) {
        "lock" -> "正在上锁…"
        "unlock" -> "正在解锁…"
        "acOn" -> "正在开启空调…"
        "acOff" -> "正在关闭空调…"
        "trunkOpen" -> "正在打开后备箱…"
        "trunkClose" -> "正在关闭后备箱…"
        "horn" -> "正在鸣笛寻车…"
        else -> "正在下发…"
    }

    private fun confirmTrunkOpen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val confirmed = WidgetControlSecurity.isDoubleClickConfirmed(
            lastCommand = prefs.getString(KEY_LAST_TRUNK, null),
            lastEpochMs = prefs.getLong(KEY_LAST_TRUNK_AT, 0L),
            currentCommand = "trunkOpen",
            currentEpochMs = now,
        )
        // 确认过一次就清掉，避免连点三下变成两次开箱
        prefs.edit()
            .putString(KEY_LAST_TRUNK, if (confirmed) null else "trunkOpen")
            .putLong(KEY_LAST_TRUNK_AT, if (confirmed) 0L else now)
            .apply()
        return confirmed
    }

    private suspend fun refresh(context: Context) {
        val runtime = runCatching { AppRuntime(context) }.getOrNull() ?: return
        runCatching { withTimeoutOrNull(9_000) { runtime.refreshStatus() } }
        runCatching { withTimeoutOrNull(6_000) { VehicleWidget.ensureCar(context, runtime) } }
        runCatching { VehicleWidget.render(context, runtime) }
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        /** 小组件的后台任务作用域：进程在，任务就在；进程被回收时指令早已发出。 */
        val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 缓存的锁车 / 空调 / 后备箱状态超过这个时间就重新拉一次，避免按旧状态反着切。 */
        const val CACHE_FRESH_MS = 10 * 60 * 1000L

        const val PREFS = "widget_action_state"
        const val KEY_LAST_TRUNK = "last_trunk_command"
        const val KEY_LAST_TRUNK_AT = "last_trunk_at"
    }
}
