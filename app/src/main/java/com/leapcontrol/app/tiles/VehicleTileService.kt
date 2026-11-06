package com.leapcontrol.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.leapcontrol.app.ConfirmActions
import com.leapcontrol.app.ConfirmGate
import com.leapcontrol.app.DoubleConfirmRules
import com.leapcontrol.app.MainActivity
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ui.AppNavRequest
import com.leapcontrol.app.ui.ControlFailure
import com.leapcontrol.app.ui.StatusSnapshot

/**
 * 系统控制中心磁贴基类 —— **点击逻辑照上游仓库 `LockToggleTileService` / `HornTileService` /
 * `ClimateTileService` 那一套**，8 块磁贴与图标仍用零控自己的（见 [QuickTiles]）。
 *
 * 上游的手感是：校验凭据 → 全局在途拦截 → 磁贴立刻变成「正在…」并亮起 →
 * 指令丢给后台就返回，**不等车端结果** → 2.2 / 2.5 / 10 秒后按本地状态复位。
 * 零控之前那套「在磁贴里等 8~12 秒车况求证 + flash 文案 + 结果通知 + 逐块高亮编排」
 * 让点一下要等很久，用户 2026-11-06 反馈「快捷中心控车的感觉还是不对」，故整条点击链路换回上游。
 *
 * 两处刻意不照搬：
 * - 上游用 `startForegroundService` 发指令，Android 12 起从控制中心（后台）会被系统拒绝，
 *   执行载体换成 [TileCommandRunner] 的进程级协程（在途去重 / 后台执行 / 结果后重问磁贴一致）；
 * - `stateDescription` 给副标题同一句话，压住 MIUI 之类 ROM 按 ACTIVE 自己拼的「已开启」。
 *   这是零控修过的坑，不能因为换逻辑又放回去。
 */
abstract class VehicleTileService : TileService() {

    protected abstract val spec: TileSpec

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resetRunnable: Runnable? = null

    /**
     * 「待确认」状态的作用域：每块磁贴各一条记录（见 [ConfirmGate]）。
     *
     * 不能所有磁贴共用一个 —— 点完「解锁」马上点「后备箱」，共用一个 key 会被当成
     * 「确认开后备箱」，反而把用户没打算做的事做掉。
     */
    private val confirmScope: String get() = "tile_${spec.id}"

    override fun onTileAdded() {
        super.onTileAdded()
        QuickTiles.markAdded(applicationContext, spec, true)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        QuickTiles.markAdded(applicationContext, spec, false)
    }

    override fun onStartListening() {
        super.onStartListening()
        // 上游：先看在途标记，直接把磁贴摆正，**不等任何网络请求**
        // 上一次点击还在确认窗口里（收起再展开控制中心）：提示要接着显示，
        // 否则用户第二次展开会以为刚才那一下已经生效了。
        val waitingConfirm = ConfirmGate.pending(this, confirmScope) != null
        updateTileState(
            inProgress = TileCommandRunner.isCommandInFlight(),
            customSubtitle = if (waitingConfirm) ConfirmActions.HINT else null,
        )
        // 上游快照超过 60 秒才同步；零控按用户要求「每次出现都刷新」，
        // 但放在进程级后台跑，不阻塞渲染、也不拿它当点击前提。
        TileCommandRunner.appear(applicationContext)
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        val store = SessionStore(ctx)
        val session = store.load()

        // ---- 前置凭据校验（上游三块磁贴逐字同款）----
        // 保留零控的「登录失效标记 + 拉起 App 指路」：用户 2026-11-05 反馈过
        // 「账号被退出登录导致快捷中心全部失效」，只写「请先登录」用户不知道该去哪儿。
        val sessionExpired = ControlFailure.isSessionExpired(ctx)
        if (sessionExpired || (session.oldAuth == null && session.newAuth == null)) {
            updateTileState(inProgress = false, customSubtitle = "请先登录")
            TileNotice.failed(ctx, ControlFailure.HINT_RELOGIN.message)
            openApp(AppNavRequest.RELOGIN)
            return
        }
        if (session.selectedVin.isBlank()) {
            updateTileState(inProgress = false, customSubtitle = "未选择车辆")
            TileNotice.failed(ctx, ControlFailure.HINT_NO_VEHICLE.message)
            openApp(null)
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            updateTileState(inProgress = false, customSubtitle = "未设置密码")
            TileNotice.failed(ctx, ControlFailure.HINT_PIN.message)
            openApp(AppNavRequest.OP_PASSWORD)
            return
        }

        // ---- 全局在途去重（上游 `ControlService.isCommandInFlight()`）----
        if (TileCommandRunner.isCommandInFlight()) {
            updateTileState(inProgress = true, customSubtitle = "指令执行中…")
            TileNotice.running(ctx, "已有控车指令执行中，请稍候")
            return
        }

        // 动作型固定一条指令；开关型按本地记住的开合态取反（读不到车况信号的车型也不会只能开不能关）
        val turningOn = spec.kind == TileKind.MOMENTARY || !QuickTiles.isOn(ctx, spec)
        val command = spec.commandFor(turningOn)

        // ---- 二次确认（可选项，默认全关，见「设置 → 快捷控制再点一次」）----
        // 第一次点只把磁贴改成「再点一次确认」，窗口内再点同一块才真正下发。
        // 位置必须在下面 `QuickTiles.setOn` 之前：先按意图翻状态再拦，
        // 就会「第一次点没下发、磁贴却已经亮了」。
        // 这里也**不能**进在途状态：待确认时磁贴必须还能被点第二下。
        if (ConfirmActions.isEnabled(ctx, command) && !ConfirmGate.step(ctx, confirmScope, command)) {
            updateTileState(inProgress = false, customSubtitle = ConfirmActions.HINT)
            TileNotice.confirm(ctx, "再点一次确认${spec.title}")
            // 窗口过了就把副标题收回默认；期间用户点了第二下（进入执行中）不覆盖
            scheduleReset(DoubleConfirmRules.WINDOW_MS + 300)
            return
        }

        // 上游「点下去先写目标状态」：不等车端，磁贴当帧就按意图亮/灭
        if (spec.kind == TileKind.TOGGLE) QuickTiles.setOn(ctx, spec, turningOn)

        // 立刻给反馈：副标题 + 高亮 + 屏幕上一行字
        updateTileState(inProgress = true, customSubtitle = spec.doingText(turningOn))
        TileNotice.running(ctx, spec.doingText(turningOn))

        // 发完就走，不等结果；失败回滚 / 结果后重问磁贴都在执行层里
        if (!TileCommandRunner.execute(ctx, spec, command, turningOn)) {
            updateTileState(inProgress = true, customSubtitle = "指令执行中…")
            TileNotice.running(ctx, "已有控车指令执行中，请稍候")
            return
        }
        scheduleReset()
    }

    /** 上游的 `mainHandler.postDelayed { updateTileState(false) }`，时长按磁贴类型分档。 */
    private fun scheduleReset(delayMs: Long = spec.resetMs) {
        resetRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { updateTileState(inProgress = false) }
        resetRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * 画磁贴（上游 `updateTileState` 的零控版：图标固定用 [TileSpec.iconRes]，不随状态换图）。
     *
     * - `inProgress`：执行中一律 ACTIVE，副标题说「正在做什么」；
     * - 常态：ACTIVE / INACTIVE 由 [stickyActive] 决定 —— 开关型按开合态、解锁按车是否没锁，
     *   上锁 / 鸣笛 / 预热不常亮（用户 2026-11-05 定稿的规则，未随这次逻辑切换改动）。
     */
    private fun updateTileState(inProgress: Boolean, customSubtitle: String? = null) {
        val tile = qsTile ?: return
        val text = customSubtitle ?: defaultSubtitle()
        tile.label = spec.title
        tile.icon = Icon.createWithResource(this, spec.iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = text
        }
        tile.state = if (inProgress || stickyActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun stickyActive(): Boolean = tileActive(
        kind = spec.kind,
        id = spec.id,
        localOn = spec.kind == TileKind.TOGGLE && QuickTiles.isOn(this, spec),
        unlockedHint = null,
        locked = StatusSnapshot.get()?.locked,
    )

    private fun defaultSubtitle(): String = when {
        spec.kind != TileKind.TOGGLE -> "点击执行"
        QuickTiles.isOn(this, spec) -> "已开启 · 点击关闭"
        else -> "已关闭 · 点击开启"
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(nav: String? = null) {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
            if (nav != null) {
                // 直达某个二级页：复用已有实例，别叠第二个 Activity
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra(MainActivity.EXTRA_NAV, nav)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    override fun onDestroy() {
        resetRunnable?.let { mainHandler.removeCallbacks(it) }
        resetRunnable = null
        super.onDestroy()
    }
}
