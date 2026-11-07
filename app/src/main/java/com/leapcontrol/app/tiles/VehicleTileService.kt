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
 * 系统控制中心磁贴基类。
 *
 * ## 高亮 = 操作结果（用户 2026-11-07 定稿）
 *
 * 以前是照上游那套「点一下先亮，2.2 / 2.5 / 10 秒后按本地状态复位」：高亮的时长与
 * 真实结果毫无关系 —— 点了关闭还要亮 2.5 秒，上锁要亮 10 秒，用户看不出到底成没成。
 * 现在按磁贴类型分开：
 *
 * - **瞬时动作**（上锁、鸣笛寻车）：点下去当帧亮（= 正在发），网关一受理就灭。
 *   亮→灭这一下就是「发出去了」的反馈；失败另有提示，磁贴同样立刻灭。
 * - **开关型**（解锁、空调、后备箱、车窗、电池预热、遮阳帘）：亮 = 功能是开着的。
 *   灭着点 → 当帧变亮，开启成功后继续亮着；亮着点 → 当帧变暗并下发关闭指令。
 *   在途期间重新拉开控制中心也不会又亮起来（见 [initialActive]）。
 *
 * 点击链路其余部分仍沿用上游手感：校验凭据 → 全局在途拦截 → 立刻反馈 →
 * 指令丢给后台就返回，**不在磁贴里等车端结果**（等结果就是「点一下卡 10 秒」），
 * 结果回来由 [TileCommandRunner] 回调一次，把高亮摆正。
 */
abstract class VehicleTileService : TileService() {

    protected abstract val spec: TileSpec

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 兜底：万一回调因为实例被销毁而丢失，到点按真实状态重画一次。 */
    private var watchdog: Runnable? = null

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
        val running = TileCommandRunner.inFlight()?.takeIf { it.id == spec.id }
        val subtitle = when {
            waitingConfirm -> ConfirmActions.HINT
            // 在途：说清正在做什么（开关型分开启 / 关闭），但**不改亮灭** ——
            // 亮灭永远等于目标状态，否则关空调后的几秒里重新拉开面板，会看到它又亮起来。
            running != null -> spec.doingText(running.turningOn)
            else -> null
        }
        updateTileState(initialActive(), subtitle)
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
            updateTileState(initialActive(), "请先登录")
            TileNotice.failed(ctx, ControlFailure.HINT_RELOGIN.message)
            openApp(AppNavRequest.RELOGIN)
            return
        }
        if (session.selectedVin.isBlank()) {
            updateTileState(initialActive(), "未选择车辆")
            TileNotice.failed(ctx, ControlFailure.HINT_NO_VEHICLE.message)
            openApp(null)
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            updateTileState(initialActive(), "未设置密码")
            TileNotice.failed(ctx, ControlFailure.HINT_PIN.message)
            openApp(AppNavRequest.OP_PASSWORD)
            return
        }

        // ---- 全局在途去重（上游 `ControlService.isCommandInFlight()`）----
        // 注意**不能**把这块磁贴点亮：在跑的是另一条指令，点亮它等于告诉用户
        // 「你点的这条正在执行」，等他看到车没反应只会更迷惑。
        if (TileCommandRunner.isCommandInFlight()) {
            updateTileState(initialActive(), "另一条指令执行中…")
            TileNotice.running(ctx, "已有控车指令执行中，请稍候")
            return
        }

        // 瞬时动作固定一条指令；开关型按当前开合态取反
        val turningOn = spec.kind == TileKind.MOMENTARY || !isOnNow()
        val command = spec.commandFor(turningOn)

        // ---- 二次确认（可选项，默认全关，见「设置 → 快捷控制再点一次」）----
        // 第一次点只把磁贴改成「再点一次确认」，窗口内再点同一块才真正下发。
        // 位置必须在下面 `QuickTiles.setOn` 之前：先按意图翻状态再拦，
        // 就会「第一次点没下发、磁贴却已经亮了」。
        // 这里也**不能**进在途状态：待确认时磁贴必须还能被点第二下。
        if (ConfirmActions.isEnabled(ctx, command) && !ConfirmGate.step(ctx, confirmScope, command)) {
            updateTileState(initialActive(), ConfirmActions.HINT)
            TileNotice.confirm(ctx, "再点一次确认${spec.title}")
            // 窗口过了就把副标题收回默认；期间用户点了第二下（进入执行中）不覆盖
            scheduleWatchdog(DoubleConfirmRules.WINDOW_MS + 300)
            return
        }

        // 开关型「点下去先写目标状态」：不等车端，磁贴当帧就按目标亮/灭
        if (spec.kind == TileKind.TOGGLE) QuickTiles.setOn(ctx, spec, turningOn)

        // 当帧反馈：瞬时动作先亮（正在发），开关型直接亮成目标态
        updateTileState(clickedActive(spec.kind, turningOn), spec.doingText(turningOn))
        TileNotice.running(ctx, spec.doingText(turningOn))

        // 发完就走，不等结果；结果回来后由 onFinished 把高亮摆正
        val accepted = TileCommandRunner.execute(ctx, spec, command, turningOn) { ok ->
            onFinished(ok)
        }
        if (!accepted) {
            // 没发出去：别留在「点亮」那一帧
            updateTileState(initialActive(), "另一条指令执行中…")
            TileNotice.running(ctx, "已有控车指令执行中，请稍候")
            return
        }
        // 兜底重画：正常情况下 onFinished 已经先到，这一步只处理「回调丢了」
        scheduleWatchdog()
    }

    /**
     * 指令有了结果：把高亮摆回「真实状态」。
     *
     * 成功失败都重画同一件事，因为「该不该亮」只由状态决定、不由结果决定：
     * - 瞬时动作：在途标记已撤，重画就是熄灭 —— 用户要的「受理后立刻不亮」；
     * - 开关型：成功 → 本地开合态已是目标值，保持亮/灭；
     *   失败 → 执行层已把开合态回滚，重画一次就跟着灭掉。
     */
    private fun onFinished(ok: Boolean) {
        mainHandler.post {
            watchdog?.let { mainHandler.removeCallbacks(it) }
            watchdog = null
            updateTileState(initialActive())
            // 上锁成功顺手把「解锁」那块也灭掉：两块磁贴看的是同一个车门状态，
            // 不同步的话锁完车之后「解锁」还亮着「未上锁」，得等车况回来才对齐。
            if (ok && spec.id == "lock") {
                QuickTiles.byId("unlock")?.let {
                    QuickTiles.setOn(this, it, false)
                    QuickTiles.refresh(this, it)
                }
            }
        }
    }

    /** 兜底重画；[delayMs] 传 0 走默认时长。 */
    private fun scheduleWatchdog(delayMs: Long = WATCHDOG_MS) {
        watchdog?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { updateTileState(initialActive()) }
        watchdog = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * 画磁贴（图标固定用 [TileSpec.iconRes]，不随状态换图）。
     *
     * @param active 亮不亮。**由调用方算好**：点击当帧用 `clickedActive`，
     *   其余时候用 [initialActive]。这里不再掺任何「执行中就一定亮」的判断 ——
     *   那正是「关空调后还亮一会儿」的来源。
     */
    private fun updateTileState(active: Boolean, customSubtitle: String? = null) {
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
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    /** 没有点击、没有在途时的亮灭。 */
    private fun initialActive(): Boolean = when (spec.kind) {
        // 瞬时动作只有在「这条指令还在发」的时候才亮，发完就灭
        TileKind.MOMENTARY -> TileCommandRunner.inFlight()?.id == spec.id
        TileKind.TOGGLE -> isOnNow()
    }

    /**
     * 开关型现在算不算「开着」：本地记过就按本地，没记过才看车况。
     *
     * 车况快照给 2 分钟窗口 —— 只是冷启动第一次点击的兜底，长时间不用的旧报文
     * 不能拿来决定下发方向（`StatusSnapshot` 自己也会再过期一次）。
     */
    private fun isOnNow(): Boolean = effectiveOn(
        kind = spec.kind,
        localState = if (QuickTiles.hasState(this, spec)) QuickTiles.isOn(this, spec) else null,
        status = statusOn(spec.id, StatusSnapshot.get(maxAgeMs = STATUS_SEED_MS)),
    )

    private fun defaultSubtitle(): String = when (spec.kind) {
        TileKind.MOMENTARY -> "点击执行"
        else -> if (isOnNow()) {
            spec.onHint.ifBlank { "已开启 · 点击关闭" }
        } else {
            spec.offHint.ifBlank { "已关闭 · 点击开启" }
        }
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
        watchdog?.let { mainHandler.removeCallbacks(it) }
        watchdog = null
        super.onDestroy()
    }

    private companion object {
        /**
         * 兜底重画时长。
         *
         * 只有当 `onFinished` 因为磁贴实例被销毁而没跑起来时才会用到；
         * 这个值不参与正常的高亮时长（那个由结果决定，不由计时决定）。
         */
        const val WATCHDOG_MS = 20_000L

        /** 冷启动点击时允许用车况判断方向的快照年龄。 */
        const val STATUS_SEED_MS = 120_000L
    }
}
