package com.leapcontrol.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.leapcontrol.app.tiles.QuickTiles
import com.leapcontrol.app.tiles.TileSpec
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 桌面小组件的广播接收器。
 *
 * 点按键 → 本进程内直接下发控车指令 → 回读车况 → 重画卡片，不需要常驻服务，也不用打开 App。
 *
 * ⚠️ 这里所有网络动作都必须尽快结束：走 [goAsync] 的广播约 10 秒就会被系统判超时。
 * 所以整条链路都按「预算」分配：只发指令不等回执（≈2s）+ 回读车况 3s + 车模 1.5s，
 * 三段上限合计 9.5 秒，压住 10 秒这条线。
 * **不能在这里调不带 resultWaitMs 的 control()** —— 它内部用 Thread.sleep 轮询车端回执，
 * 最长 20 秒，外面套 withTimeoutOrNull 也掐不住（没有可中断的挂起点），
 * 结果就是卡片来不及重画、提示弹不出来，甚至被系统当成广播超时掐掉。
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
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 改尺寸后重画：字号是按卡片实际 dp 算的，不重画会停在旧尺寸的字上
        runCatching { VehicleWidget.render(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in VehicleWidget.ACTIONS) return
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
        // 登录态 / 控车密码同步检查：不满足就没必要起后台任务
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
        // 开关式按键（空调 / 后备箱）得先知道「现在是什么状态」才知道往哪个方向切。
        // 这里**只看本地记住的开合态**，不拿车况当判据 —— 与控制中心磁贴同一套规则：
        // 部分车型读不到 acOn / trunkOpen（null），`车况开着就发关` 会被永远判成「当前是关的」，
        // 于是按键只能开、关不掉。这条坑在 tiles/TileSpecs.kt 里已经踩过一次。
        // 车况只在「从来没点过」时喂一个初始值，见 [seedToggleState]。
        val toggle = when (action) {
            VehicleWidget.ACTION_TOGGLE_AC -> QuickTiles.byId("ac")
            VehicleWidget.ACTION_TOGGLE_TRUNK -> QuickTiles.byId("trunk")
            else -> null
        }
        var wantOn: Boolean? = null
        if (toggle != null) {
            seedToggleState(context, runtime, toggle)
            wantOn = !QuickTiles.isOn(context, toggle)
        }

        val command = when (action) {
            VehicleWidget.ACTION_UNLOCK -> "unlock"
            VehicleWidget.ACTION_LOCK -> "lock"
            VehicleWidget.ACTION_TOGGLE_AC -> if (wantOn == true) "acOn" else "acOff"
            VehicleWidget.ACTION_TOGGLE_TRUNK -> if (wantOn == true) "trunkOpen" else "trunkClose"
            VehicleWidget.ACTION_HORN -> "horn"
            else -> return
        }

        // 开后备箱不可撤销：沿用 App 内「3 秒内再点一次确认」的策略
        if (command == "trunkOpen" && !confirmTrunkOpen(context)) {
            toast(context, "再点一次后备箱确认打开")
            return
        }

        // partial：卡片结构没变，只换状态文案。全量重绘会让整张卡（含壁纸）重画一遍，
        // 一次点击要来回闪两三次。
        VehicleWidget.render(context, runtime, busy = labelOf(command), partial = true)

        // resultWaitMs = 0：整条广播链路只有 10 秒预算，而等车端回执最长要 20 秒
        // （control 里是 Thread.sleep 轮询，没有可中断的挂起点，外面套 withTimeout 也掐不住）。
        // 等下去的结果是：卡片来不及重画、提示弹不出来，甚至被系统当成广播超时掐掉进程。
        // 指令发出去就够了，结果靠下面的回读车况判断。
        val outcome = runCatching {
            withTimeoutOrNull(SEND_TIMEOUT_MS) { runtime.control(command, resultWaitMs = 0L) }
        }
        if (outcome.getOrNull() == null) {
            // 没发出去就别改本地开合态，也别让卡片停在「正在上锁…」
            VehicleWidget.render(context, runtime, partial = true)
            toast(context, failureText(outcome.exceptionOrNull()))
            return
        }

        if (toggle != null) {
            // 指令确实发出去了才落状态：发失败时不能让按键「假装」已经开/关了
            QuickTiles.setOn(context, toggle, wantOn == true)
            // 顺手把控制中心那几块磁贴也标脏，两处显示不会对不上
            runCatching { QuickTiles.refreshAll(context) }
        }

        // 回读车况：锁车 / 空调 / 后备箱信号真的变了才算生效
        runCatching { withTimeoutOrNull(STATUS_REFRESH_MS) { runtime.refreshStatus() } }
        runCatching { withTimeoutOrNull(CAR_IMAGE_MS) { VehicleWidget.ensureCar(context, runtime) } }
        VehicleWidget.render(context, runtime, partial = true)
        toast(context, resultMessage(action, runtime.statusFlow.value, wantOn))
    }

    /**
     * 从来没点过这块开关时，用磁盘缓存的车况喂一个初始值。
     *
     * 只喂一次（`state_at` 一旦有值就不再覆盖）：用户在车里手动开过空调 / 后备箱，
     * 靠回读车况校准显示没问题，但**绝不拿车况反推点击方向** —— 原因见 [execute] 开头。
     */
    private fun seedToggleState(context: Context, runtime: AppRuntime, spec: TileSpec) {
        if (QuickTiles.lastActionAt(context, spec) != 0L) return
        val status = runtime.statusFlow.value ?: return
        val open = when (spec.id) {
            "ac" -> status.acOn
            "trunk" -> status.trunkOpen
            else -> null
        } ?: return
        QuickTiles.setOn(context, spec, open, fromUser = false)
    }

    /**
     * 提示语按**回读到的车况**说人话，而不是笼统的「指令已下发」。
     * 车况读不到这个信号（部分车型没有）时退回「已下发」，不谎报成功。
     */
    private fun resultMessage(action: String, status: CarStatus?, wantOn: Boolean?): String = when (action) {
        VehicleWidget.ACTION_UNLOCK -> if (status?.locked == false) "已解锁" else "解锁指令已下发"
        VehicleWidget.ACTION_LOCK -> if (status?.locked == true) "已上锁" else "上锁指令已下发"
        VehicleWidget.ACTION_HORN -> "已鸣笛寻车"
        VehicleWidget.ACTION_TOGGLE_AC -> when (status?.acOn) {
            true -> "空调已开启"
            false -> "空调已关闭"
            null -> if (wantOn == true) "开启空调指令已下发" else "关闭空调指令已下发"
        }
        VehicleWidget.ACTION_TOGGLE_TRUNK -> when (status?.trunkOpen) {
            true -> "后备箱已开启"
            false -> "后备箱已关闭"
            null -> if (wantOn == true) "开启后备箱指令已下发" else "关闭后备箱指令已下发"
        }
        else -> "指令已下发"
    }

    private fun failureText(e: Throwable?): String = when {
        e == null -> "指令下发超时，请重试"
        e.message.isNullOrBlank() -> "下发失败，请检查网络"
        else -> "下发失败：${e.message!!.take(30)}"
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
        val last = prefs.getString(KEY_LAST_TRUNK, null)
        val lastAt = prefs.getLong(KEY_LAST_TRUNK_AT, 0L)
        val confirmed = last == "trunkOpen" && now - lastAt in 0..DOUBLE_CLICK_WINDOW_MS
        // 确认过一次就清掉，避免连点三下变成两次开箱
        prefs.edit()
            .putString(KEY_LAST_TRUNK, if (confirmed) null else "trunkOpen")
            .putLong(KEY_LAST_TRUNK_AT, if (confirmed) 0L else now)
            .apply()
        return confirmed
    }

    private suspend fun refresh(context: Context) {
        val runtime = runCatching { AppRuntime(context) }.getOrNull() ?: return
        runCatching { withTimeoutOrNull(6_000) { runtime.refreshStatus() } }
        runCatching { withTimeoutOrNull(3_000) { VehicleWidget.ensureCar(context, runtime) } }
        // partial：桌面定时更新只改数字/状态，别把整张卡连同壁纸重画一遍
        runCatching { VehicleWidget.render(context, runtime, partial = true) }
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        /** 小组件的后台任务作用域：进程在，任务就在；进程被回收时指令早已发出。 */
        val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 只发不等回执，正常 1~2 秒；给个上限防止网关卡死把广播拖住。 */
        const val SEND_TIMEOUT_MS = 5_000L

        /** 回读车况的窗口：这条才是「按键到底生效没有」的依据。 */
        const val STATUS_REFRESH_MS = 3_000L

        /** 车模图（有缓存时是 0 开销）。三段上限 5+3+1.5 = 9.5 秒，压住 10 秒广播预算。 */
        const val CAR_IMAGE_MS = 1_500L

        /** 与 App 内「再点一次」窗口保持一致（3 秒）。 */
        const val DOUBLE_CLICK_WINDOW_MS = 3_000L

        const val PREFS = "widget_action_state"
        const val KEY_LAST_TRUNK = "last_trunk_command"
        const val KEY_LAST_TRUNK_AT = "last_trunk_at"
    }
}
