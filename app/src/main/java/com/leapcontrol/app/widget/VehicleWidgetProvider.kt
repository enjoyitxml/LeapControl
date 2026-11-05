package com.leapcontrol.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.leapcontrol.app.ConfirmAction
import com.leapcontrol.app.ConfirmActions
import com.leapcontrol.app.ConfirmGate
import com.leapcontrol.app.DoubleConfirmRules
import com.leapcontrol.app.MainActivity
import com.leapcontrol.app.tiles.QuickTiles
import com.leapcontrol.app.tiles.TileSpec
import com.leapcontrol.app.ui.AppNavRequest
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
import com.leapcontrol.app.ui.ControlFix
import com.leapcontrol.app.ui.ControlFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
        // 覆盖安装之后：桌面上已有的卡片还是旧 APK 里的 RemoteViews，
        // 系统不会自己按新资源重画。这里立刻用本地缓存的车况重推一帧，
        // 否则这版改过的版式 / 图标要等用户把卡片删掉重加才出现（上游 4.4.11 同样修法）。
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { VehicleWidget.render(context) }
            return
        }
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
        val noAuth = session.oldAuth == null && session.newAuth == null
        if (noAuth || ControlFailure.isSessionExpired(appContext)) {
            pending.finish()
            // 光弹 toast 不够：桌面广播里弹的 toast 在不少 ROM 上会被直接吞掉，
            // 用户看到的就是「点了按钮毫无反应」—— 2026-11-05 那波「小组件全部失效」
            // 的反馈就是这么来的。直接把 App 拉到前台，让他看得见明确的下一步。
            toast(appContext, "登录已失效，请重新登录零控")
            openApp(appContext, AppNavRequest.RELOGIN)
            return
        }
        if (session.selectedVin.isBlank()) {
            pending.finish()
            // 登录着但没选车：指令层会抛「还没有选择车辆 VIN」，提前说清楚，别让用户等到超时
            toast(appContext, "还没选择车辆，正在打开零控")
            openApp(appContext, null)
            return
        }
        if (runtime.sessionStore.loadOpPassword().isNullOrBlank()) {
            pending.finish()
            // 光弹一句 toast 不够：桌面广播里弹的 toast 在不少 ROM 上会被直接吞掉，
            // 用户看到的就是「点了按钮毫无反应」。这里直接把 App 拉到前台并落到密码设置页，
            // 让他看见一个确定的结果。
            toast(appContext, "还没设置 4 位控车密码，正在打开零控")
            openApp(appContext, AppNavRequest.OP_PASSWORD)
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

        // 二次确认（可选项，默认全关）：第一次点只记下「待确认」并提示，
        // 窗口内再点同一个键才真正下发。开关在「设置 → 快捷控制再点一次」。
        if (ConfirmActions.isEnabled(context, command) && !ConfirmGate.step(context, SCOPE, command)) {
            val label = ConfirmAction.of(command)?.title ?: "该操作"
            // 卡片右上角那行本来显示「12:30 更新」，临时借来说一句确认提示；
            // 只走增量，别为一个提示把整张卡（含壁纸）重画一遍
            VehicleWidget.render(context, runtime, busy = "再点一次确认", partial = true)
            toast(context, "再点一次「$label」确认")
            // 3 秒后把提示改回车况文案。单独起协程、**不阻塞这一条广播**：
            // 广播 10 秒就超时，在 execute() 里 delay 会把整条链路一起拖长。
            resetHintLater(context, runtime)
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
            fail(context, outcome.exceptionOrNull())
            return
        }

        // 这次通了：之前记下的「登录已失效」可以撤了，别让卡片一直写着「点此登录」
        ControlFailure.clearSessionExpired(context)

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

    /**
     * 控车没成：说清原因 + 给下一步。
     *
     * 登录失效 / 没设密码 / 没选车这三类，**必须**把用户送到能解决问题的那一页 ——
     * 在桌面上弹一句 toast 是不够的，很多 ROM 会直接吞掉后台 toast（用户看到的就是「点了没反应」）。
     */
    private fun fail(context: Context, e: Throwable?) {
        val hint = ControlFailure.of(e)
        toast(context, hint.message)
        when (hint.action) {
            ControlFix.RELOGIN -> {
                ControlFailure.markSessionExpired(context)
                // 全量重画一次：卡片右上角要改成「点此登录」、五颗按键改成「点一下打开 App」。
                // 丢到进程级协程里做，别占着广播那 10 秒预算。
                work.launch { runCatching { VehicleWidget.render(context, partial = false) } }
                openApp(context, AppNavRequest.RELOGIN)
            }
            ControlFix.OPEN_PIN -> openApp(context, AppNavRequest.OP_PASSWORD)
            ControlFix.OPEN_APP -> openApp(context, null)
            ControlFix.NONE -> Unit
        }
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

    /**
     * 确认提示到期后把卡片右上角改回车况文案。
     *
     * 不挂在 [execute] 里 `delay`：那条链路走的是 [goAsync]，广播只有约 10 秒预算，
     * 多等 3 秒会把「回读车况 + 重画」一起顶出预算。这里用进程级 [work]，
     * 广播早就 finish 了，协程仍然能把卡片恢复；进程被回收时卡片也会在下一次更新时重置。
     */
    private fun resetHintLater(context: Context, runtime: AppRuntime) {
        val appContext = context.applicationContext
        work.launch {
            delay(DoubleConfirmRules.WINDOW_MS + 300)
            runCatching { VehicleWidget.render(appContext, runtime, partial = true) }
        }
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

    /**
     * 把 App 拉起来，可带一个直达目标（[AppNavRequest.OP_PASSWORD] / [AppNavRequest.RELOGIN]）。
     *
     * 广播接收器里 `startActivity` 在 Android 10+ 会被后台启动限制拦下，
     * 所以这只是兜底：卡片正常刷新过之后，本该下发的按键本身已换成 Activity 型
     * PendingIntent（由桌面发起，不受这条限制），不会再走到这里。
     * `nav == null` 表示只打开首页。
     */
    private fun openApp(context: Context, nav: String?) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        if (nav != null) launch.putExtra(MainActivity.EXTRA_NAV, nav)
        runCatching { context.startActivity(launch) }
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

        /** 「待确认」状态在这块入口下的作用域（见 [ConfirmGate]），与磁贴的互不干扰。 */
        const val SCOPE = "widget"
    }
}
