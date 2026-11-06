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
 * 所以整条链路都按「预算」分配：短轮询等车端回执 3.5s（[AppRuntime.controlForWidget]）
 * + 回读车况 2.5s + 车模 1s，合计约 7 秒（发送本身 1~2 秒），压住 10 秒这条线。
 *
 * 控车拿到结果后**立刻重画一帧**，右上角写完成式短句（「车辆已上锁」/「空调已开启」），
 * 记录留在 [WidgetFlash] 里 30 秒，等车端报文跟上再自然交回真实车况 ——
 * 车端执行要 5~15 秒，只靠回读会把「还没更新」显示成「点了没生效」。
 *
 * 右上角的刷新键（[VehicleWidget.ACTION_REFRESH]）不走控车链路：只拉一次车况重画。
 *
 * **不能在这里调不带 resultWaitMs 的 control()** —— 它内部用 Thread.sleep 轮询车端回执，
 * 最长 20 秒，外面套 withTimeoutOrNull 也掐不住（没有可中断的挂起点），
 * 结果就是卡片来不及重画、提示弹不出来，甚至被系统当成广播超时掐掉。
 *
 * ## 4×2 与 5×2 是**两个** receiver
 * 安卓的「添加小部件」列表里，一个 receiver + 一份 provider-info 只对应一个固定格数，
 * 想让用户能挑 5 列版，只能再注册一个 receiver（[VehicleWidgetProviderWide]）。
 * 两者共用这里的全部逻辑 —— 它只是个子类，连一个方法都没重写；
 * 差别只在 [VehicleWidget.render] 里「这个 id 属于哪个 component」决定用哪份布局。
 */
open class VehicleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 先用磁盘缓存的车况画一帧（冷启动也立刻有内容），再后台联网刷新
        runCatching { VehicleWidget.render(context) }
        refreshOnAppear(context)
    }

    /**
     * 卡片「出现」时刷一次车况（用户 2026-11-05 要求：每次出现都是新的）。
     *
     * 系统**没有**「小组件变得可见」的回调 —— `updatePeriodMillis` 故意设 0
     * （定时联网会反复唤醒车上 T-Box）。能感知到的出现时机只有三处：
     * 添加到桌面 / 改尺寸 / 桌面主动请求重画（onUpdate），它们都汇到这里。
     * 5 秒内只发一次：`onUpdate` 与 [onAppWidgetOptionsChanged] 常常前后脚一起到。
     */
    private fun refreshOnAppear(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_APPEAR_REFRESH_AT, 0L) < APPEAR_DEDUPE_MS) return
        prefs.edit().putLong(KEY_APPEAR_REFRESH_AT, now).apply()
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
        // 卡片刚被调整（多半是刚加到桌面）—— 顺手把车况拉新
        refreshOnAppear(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        // 刷新键只拉车况，不走控车那条（不需要登录校验、二次确认、结果轮询）
        if (action == VehicleWidget.ACTION_REFRESH) {
            runCatching { refreshFromTap(context) }
            return
        }
        if (action !in VehicleWidget.ACTIONS) return
        runCatching { handleAction(context, action) }
    }

    // ---------------- 内部实现 ----------------

    /**
     * 右上角刷新键：只拉一次车况重画。
     *
     * 车况接口正常 1~2 秒，6 秒是上限；加上车模 1 秒与重画，仍压在广播 10 秒预算内。
     * 一开始先把右上角写成「正在刷新…」—— 用户点完立刻有反应，
     * 不然要盯着旧时间等好几秒，看起来就像按钮没接上。
     */
    private fun refreshFromTap(context: Context) {
        val appContext = context.applicationContext
        val pending = goAsync()
        work.launch {
            try {
                val runtime = runCatching { AppRuntime(appContext) }.getOrNull()
                if (runtime == null) {
                    toast(appContext, "零控初始化失败，请打开 App 后重试")
                    return@launch
                }
                val session = runtime.session
                if (session.oldAuth == null && session.newAuth == null) {
                    // 拉车况同样要登录态。这里不主动拉起 App（用户只是点了个刷新，
                    // 被弹到前台会更烦），但把卡片切成「登录已失效」——
                    // 点卡片 / 点五颗按键都会带 RELOGIN 直接进登录流程。
                    ControlFailure.markSessionExpired(appContext)
                    toast(appContext, "登录已失效，点卡片重新登录")
                    VehicleWidget.render(appContext, partial = false)
                    return@launch
                }
                if (session.selectedVin.isBlank()) {
                    toast(appContext, "还没选择车辆，点卡片打开零控后选择")
                    return@launch
                }
                // 用户主动刷新 = 想看真实车况，刚控车的结果提示立刻让位
                WidgetFlash.clear(appContext)
                VehicleWidget.render(appContext, runtime, busy = "正在刷新…", partial = true)
                val fresh = runCatching {
                    withTimeoutOrNull(REFRESH_TIMEOUT_MS) { runtime.refreshStatus() }
                }.getOrNull()
                runCatching { withTimeoutOrNull(CAR_IMAGE_MS) { VehicleWidget.ensureCar(appContext, runtime) } }
                VehicleWidget.render(appContext, runtime, partial = true)
                if (fresh == null) toast(appContext, "刷新失败，请检查网络后重试")
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private fun handleAction(context: Context, action: String) {
        val appContext = context.applicationContext
        val pending = goAsync()
        val runtime = runCatching { AppRuntime(appContext) }.getOrNull()
        if (runtime == null) {
            pending.finish()
            toast(appContext, "零控初始化失败，请打开 App 后重试")
            return
        }
        // 登录态 / 控车密码同步检查：不满足就没必要起后台任务。
        // 这里的每一句都必须回答「为什么没成」+「下一步做什么」——
        // 2026-11-05 那波「小组件全部失效」实际是账号被退出登录，而当时只弹
        // 「下发失败：未登录」，用户以为是 App 坏了。
        val session = runtime.session
        val noAuth = session.oldAuth == null && session.newAuth == null
        if (noAuth || ControlFailure.isSessionExpired(appContext)) {
            pending.finish()
            // 光弹 toast 不够：桌面广播里弹的 toast 在不少 ROM 上会被直接吞掉，
            // 用户看到的就是「点了按钮毫无反应」。记下登录失效（卡片右上角随即改成
            // 「登录已失效」、五颗按键改成「点一下打开 App」），同时把 App 拉到前台。
            ControlFailure.markSessionExpired(appContext)
            toast(appContext, ControlFailure.HINT_RELOGIN.message)
            work.launch { runCatching { VehicleWidget.render(appContext, partial = false) } }
            openApp(appContext, AppNavRequest.RELOGIN)
            return
        }
        if (session.selectedVin.isBlank()) {
            pending.finish()
            toast(appContext, ControlFailure.HINT_NO_VEHICLE.message)
            openApp(appContext, null)
            return
        }
        if (runtime.sessionStore.loadOpPassword().isNullOrBlank()) {
            pending.finish()
            toast(appContext, ControlFailure.HINT_PIN.message)
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

        // 二次确认（可选项，默认全关，见「设置 → 快捷控制再点一次」）：
        // 第一次点只记下「待确认」并提示，窗口内再点同一个键才真正下发。
        if (ConfirmActions.isEnabled(context, command)) {
            if (!ConfirmGate.step(context, SCOPE, command)) {
                val label = ConfirmAction.of(command)?.title ?: "该操作"
                // 卡片右上角那行本来显示「12:30 更新」，临时借来说一句确认提示；
                // 只走增量，别为一个提示把整张卡（含壁纸）重画一遍
                VehicleWidget.render(context, runtime, busy = ConfirmActions.HINT, partial = true)
                toast(context, "再点一次「$label」确认")
                resetHintLater(context, runtime)
                return
            }
        } else if (command == "trunkOpen" && !confirmTrunkOpen(context)) {
            // 没勾也留着这条兜底：开后备箱不可撤销，误触赔不起。
            // 勾了的话走上面那道门禁，这里不再叠加，否则要点三下。
            toast(context, "再点一次后备箱确认打开")
            return
        }

        VehicleWidget.render(context, runtime, busy = labelOf(command), partial = true)

        // 短轮询：在广播预算里真的等一段车端回执（默认 3.5 秒）。
        // 拿到结果码 0 / 200 会返回「XX已执行」，这才是真实执行结果；
        // 等不到就返回「XX已下发（车端结果码 n）」，再由下面的回读兜底。
        // 不能退回 resultWaitMs = 0：那是发完立刻返回，一个字的结果都不等，
        // 只剩 3 秒回读去猜 —— 而车端执行要 5~15 秒，读回来还是上一条报文，
        // 用户看到的就是「点了之后状态不刷新」。
        val outcome = runCatching {
            withTimeoutOrNull(WATCHDOG_MS) { runtime.controlForWidget(command, RESULT_BUDGET_MS) }
        }
        val message = outcome.getOrNull()
        if (message == null) {
            // 没发出去就别改本地开合态，也别让卡片停在「正在上锁…」
            VehicleWidget.render(context, runtime)
            fail(context, outcome.exceptionOrNull())
            return
        }

        // 这次通了：撤掉「登录已失效」的标记，别让卡片一直写着「登录已失效」
        ControlFailure.clearSessionExpired(context)

        if (toggle != null) {
            // 指令确实发出去了才落状态：发失败时不能让按键「假装」已经开/关了
            QuickTiles.setOn(context, toggle, wantOn == true)
            // 顺手把控制中心那几块磁贴也标脏，两处显示不会对不上
            runCatching { QuickTiles.refreshAll(context) }
        }

        // 结果**立刻**上卡片：右上角换成完成式短句并马上重画一帧，
        // 不等下面的车况回读 —— 回读还要 2~3 秒，用户要的是点完就有答复。
        // 这条记录只活 2 秒（[WidgetFlash]），到期自动切回「HH:mm 更新」。
        val confirmed = message.endsWith("已执行")
        WidgetFlash.write(
            context,
            doneLabelOf(command, confirmed),
            ok = confirmed,
            hint = if (confirmed) command else null,
        )
        VehicleWidget.render(context, runtime, partial = true)
        toast(context, message)
        scheduleFlashExpiry(context)

        // 车端已经执行（或结果码已回），这时回读才有意义：
        // 报文通常已经跟着变了，读到的才不是上一条旧状态。
        runCatching { withTimeoutOrNull(STATUS_REFRESH_MS) { runtime.refreshStatus() } }
        runCatching { withTimeoutOrNull(CAR_IMAGE_MS) { VehicleWidget.ensureCar(context, runtime) } }
        VehicleWidget.render(context, runtime, partial = true)
    }

    /**
     * 结果提示到期后把右上角改回「HH:mm 更新」。
     *
     * 这里是整条链路里唯一的定时器，也是「一直显示上一轮控车结果」的修复点：
     * [WidgetFlash.read] 到点就返回 null，但**桌面不会自己重画** ——
     * 不补这一帧，那行字会一直挂着，直到下次刷新 / 控车。
     *
     * 走进程级 [work]：广播那 10 秒预算早就用完了，这条协程仍然能把卡片恢复；
     * 进程如果被回收，这点小事也不值得去 wake up，下次任何重画都会自然回到更新时间。
     * 不为了它再拉一次车况 —— 控车时已经回读过一次，多打一次只是在费车电。
     */
    private fun scheduleFlashExpiry(context: Context) {
        val appContext = context.applicationContext
        work.launch {
            delay(WidgetFlash.TTL_MS + 500)
            runCatching { VehicleWidget.render(appContext, partial = true) }
        }
    }

    /**
     * 把「再点一次确认」那行改回车况文案。
     *
     * 单独起协程、**不阻塞当前这条广播**：广播 10 秒就超时，
     * 在 [execute] 里 `delay(3 秒)` 会把整条链路一起拖长（v1.1.0 之前踩过）。
     */
    private fun resetHintLater(context: Context, runtime: AppRuntime) {
        val appContext = context.applicationContext
        work.launch {
            delay(DoubleConfirmRules.WINDOW_MS + 300)
            runCatching { VehicleWidget.render(appContext, runtime, partial = true) }
        }
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
     * 控车没成：说清原因 + 给下一步。
     *
     * 登录失效 / 没设密码 / 没选车这三类，**必须**把用户送到能解决问题的那一页 ——
     * 在桌面上弹一句 toast 是不够的，很多 ROM 会直接吞掉后台 toast
     * （用户看到的就是「点了没反应」）。
     */
    private fun fail(context: Context, e: Throwable?) {
        val hint = ControlFailure.of(e)
        toast(context, hint.message)
        when (hint.action) {
            ControlFix.RELOGIN -> {
                ControlFailure.markSessionExpired(context)
                // 全量重画一次：卡片右上角要改成「登录已失效」、五颗按键改成「点一下打开 App」，
                // 这些点击区属于静态部分，增量更新改不了。丢到进程级协程里做，别占广播预算。
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
     * 控车完成后右上角那句话。
     *
     * 用**完成式**（「车辆已上锁」）而不是指令名（「上锁」）：用户要的是结果，不是动作。
     * 没等到车端结果码时只能说「已下发」—— 把「发出去了」画成「已经成了」是骗人，
     * 车主看到卡片变了就不会再去车边确认，反而耽误事。
     */
    private fun doneLabelOf(command: String, confirmed: Boolean): String = when (command) {
        "unlock" -> if (confirmed) "车辆已解锁" else "解锁已下发"
        "lock" -> if (confirmed) "车辆已上锁" else "上锁已下发"
        "acOn" -> if (confirmed) "空调已开启" else "开空调已下发"
        "acOff" -> if (confirmed) "空调已关闭" else "关空调已下发"
        "trunkOpen" -> if (confirmed) "后备箱已开启" else "开后备箱已下发"
        "trunkClose" -> if (confirmed) "后备箱已关闭" else "关后备箱已下发"
        "horn" -> if (confirmed) "已鸣笛寻车" else "鸣笛已下发"
        else -> "指令已下发"
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
        runCatching { VehicleWidget.render(context, runtime) }
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    /**
     * 把 App 拉起来，可带一个直达目标（[AppNavRequest.OP_PASSWORD] / [AppNavRequest.RELOGIN]）。
     *
     * 广播接收器里 `startActivity` 在 Android 10+ 会被后台启动限制拦下，所以这只是兜底：
     * 卡片重画过之后，按键本身已经换成 Activity 型 PendingIntent（由桌面发起，不受这条限制），
     * 用户再点一下一定进得去。`nav == null` 表示只打开首页。
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

        /** 等车端回执的窗口：发送 1~2 秒 + 这段轮询，落在 10 秒广播预算内。 */
        const val RESULT_BUDGET_MS = 3_500L

        /** 兜底看门狗：controlForWidget 内部是 Thread.sleep 轮询，实际靠 [RESULT_BUDGET_MS] 收口。 */
        const val WATCHDOG_MS = 8_000L

        /** 回读车况的窗口：车端已执行，报文一般已经跟上，2.5 秒足够。 */
        const val STATUS_REFRESH_MS = 2_500L

        /** 手动刷新车况的窗口：正常 1~2 秒，给 6 秒兜住弱网。 */
        const val REFRESH_TIMEOUT_MS = 6_000L

        /** 车模图（有缓存时是 0 开销）。 */
        const val CAR_IMAGE_MS = 1_000L

        /** 与 App 内「再点一次」窗口保持一致（3 秒）。 */
        const val DOUBLE_CLICK_WINDOW_MS = 3_000L

        /**
         * 「待确认」状态在**桌面卡片**这个入口下的作用域（见 [ConfirmGate]）。
         *
         * 控制中心每块磁贴用自己的 `tile_<id>`，两边互不干扰：
         * 在磁贴上点过解锁，不该让桌面卡片把下一次开后备箱当成「确认」。
         */
        const val SCOPE = "widget"

        const val PREFS = "widget_action_state"
        const val KEY_LAST_TRUNK = "last_trunk_command"
        const val KEY_LAST_TRUNK_AT = "last_trunk_at"

        /** 卡片出现时的刷新去重窗口（onUpdate 与改尺寸回调常前后脚一起到）。 */
        const val APPEAR_DEDUPE_MS = 5_000L
        const val KEY_APPEAR_REFRESH_AT = "appear_refresh_at"
    }
}
