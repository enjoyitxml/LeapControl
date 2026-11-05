package com.leapcontrol.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.leapcontrol.app.MainActivity
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ui.AppNavRequest
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
import com.leapcontrol.app.ui.ControlFailure
import com.leapcontrol.app.ui.ControlFix
import com.leapcontrol.app.ui.StatusSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 系统控制中心磁贴基类。
 *
 * 点击后直接在本进程内下发控车指令（不需要常驻前台服务）：
 * 指令通常在 1~3 秒内完成，即使进程随后被回收，指令也已到达车端。
 *
 * 开关型磁贴（空调 / 后备箱 / 车窗 / 遮阳帘）分两层：
 * - **点击方向**只看本地记住的开合态，不拿车况当判据 —— 原因见 [QuickTiles] 上的注释：
 *   部分车型读不到 acOn / trunkOpen 这类信号，会把每次点击都判成「当前是关的」，
 *   于是磁贴只能开、关不掉；
 * - **显示状态**则跟随车况：每次展开控制中心都用最新报文校准一次，
 *   用户在车里手动关了空调 / 后备箱，磁贴不能还亮着。
 */
abstract class VehicleTileService : TileService() {

    protected abstract val spec: TileSpec

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences("quick_tiles_state", Context.MODE_PRIVATE) }
    private var busy = false

    /** 刚执行完的那句话（「执行成功」/「已上锁」）与它的时间戳。 */
    private var flashText: String? = null
    private var flashLabel: String? = null
    private var flashAt = 0L

    /** 每次点击 +1：用来让上一轮的「把副标题收回默认」别盖掉新一轮的执行中文案。 */
    private var execSeq = 0

    /**
     * 「车是不是解锁着」的即时答案（只在解锁磁贴上用）。
     *
     * 控车成功后车端报文要 5~15 秒才跟上，这段窗口里不能还按旧报文画。
     * null = 还没判断过，退回读进程内车况快照。
     */
    private var unlockedHint: Boolean? = null

    override fun onTileAdded() {
        super.onTileAdded()
        prefs.edit().putBoolean("added_${spec.id}", true).apply()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        prefs.edit().putBoolean("added_${spec.id}", false).apply()
    }

    override fun onStartListening() {
        super.onStartListening()
        // 先用进程内已知的最新车况把状态摆正，再渲染：控制中心一展开就是对的，
        // 不用等下面那个异步校准（它要发一次网络请求）
        if (!busy) applyKnownStatus()
        if (!busy) render()
        // 控制中心**每展开一次**都刷一次车况（用户 2026-11-05 要求：一出现就是新的）。
        // 七八块磁贴会同时回调，[TileStatusSync.appear] 用 2 秒合并窗口把它们并成一个请求。
        // 动作型磁贴也调：它们自己不用这份结果，但刷新是整块控制中心共用的。
        scope.launch { syncFromVehicle() }
    }

    override fun onClick() {
        super.onClick()
        if (busy) return

        val ctx = applicationContext
        val store = SessionStore(ctx)
        val session = store.load()
        // 「上次控车因为登录失效失败了」也当成没登录：否则用户每点一次都要等一轮网络往返
        // 才看到同一句提示，还以为磁贴坏了。
        // 2026-11-05 那波「快捷中心全部失效」实际是账号被退出登录 —— 提示必须指路，
        // 不能只说「执行失败」。
        val sessionExpired = ControlFailure.isSessionExpired(ctx)

        if (sessionExpired || (session.oldAuth == null && session.newAuth == null)) {
            render(if (sessionExpired) "登录已失效" else "请先登录", active = false)
            // 磁贴副标题只有几格宽，说不全「去重新登录」这一步，所以必须把 App 拉起来 ——
            // 控制中心里的提示更容易被 ROM 吞掉，用户看到的就是「点了没反应」
            TileNotice.failed(ctx, ControlFailure.HINT_RELOGIN.message)
            openApp(AppNavRequest.RELOGIN)
            return
        }
        if (session.selectedVin.isBlank()) {
            render("未选择车辆", active = false)
            TileNotice.failed(ctx, ControlFailure.HINT_NO_VEHICLE.message)
            openApp(null)
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            render("未设置密码", active = false)
            TileNotice.failed(ctx, ControlFailure.HINT_PIN.message)
            openApp(AppNavRequest.OP_PASSWORD)
            return
        }

        // 本次要下发的方向：一次性动作固定「执行」，开关磁贴取反本地记住的开合态。
        val turningOn = spec.kind == TileKind.MOMENTARY || !QuickTiles.isOn(ctx, spec)

        busy = true
        // 上一轮的「执行成功」不能盖住这一轮的「上锁中」
        flashText = null
        flashLabel = null
        val seq = ++execSeq

        // 开关型：点击瞬间先按**意图**落本地状态 —— 要开就先亮、要关就先灭，
        // 「点完马上有反应」这一半不等车端（下发失败会在下面回滚）。
        // 显示方向仍以车况校准为准，只是把「等结果」挪到了失败回滚那一步。
        val previousOn = if (spec.kind == TileKind.TOGGLE) QuickTiles.isOn(ctx, spec) else null
        if (spec.kind == TileKind.TOGGLE) QuickTiles.setOn(ctx, spec, turningOn)

        // 执行中：动作型把磁贴名改成「上锁中」，**并且亮起** ——
        // 用户 2026-11-05 明确要求：上锁 / 鸣笛 / 预热发起时要高亮，执行成功后熄灭；
        // 开关型则按刚落下的本地状态亮灭。
        //
        // 解锁是唯一的例外（同一天的第二轮反馈）：闭锁时它必须是灭的，
        // 只有**车真的解锁了**才点亮 —— 所以它不跟「发起」亮，亮灭只由
        // [stickyActive] 里的车况（unlockedHint / locked）决定。
        render(
            subtitle = spec.doingText(turningOn),
            active = turningOn && spec.id != "unlock",
            label = if (spec.kind == TileKind.MOMENTARY) "${spec.title}中" else null,
        )
        // 点完先给一句「正在上锁…」。车端 T-Box 唤醒 + 执行要 5~15 秒，
        // 这段时间如果屏幕上什么都没有，用户只会觉得「点了没反应」。
        // 优先系统原生 toast；控制中心展开时磁贴是后台、toast 会被系统吞，
        // 这时 [TileNotice] 自动改用横幅通知兜底。
        TileNotice.running(ctx, spec.doingText(turningOn))
        scope.launch {
            val result = runCatching { execute(ctx, turningOn) }
            busy = false
            result.fold(
                onSuccess = { message ->
                    // 这次通了：撤掉「登录已失效」的标记，下次展开控制中心不会又写「登录已失效」
                    ControlFailure.clearSessionExpired(ctx)
                    // 车端确认执行了 →「执行成功」；只发出去了 →「已下发 · 待确认」
                    val done = isDone(message)
                    // 解锁磁贴常亮的依据：**车端确认解锁了** → 亮着；确认上锁 → 熄灭。
                    // 只有 [done]（车端信号已变成意图值）才写，否则「指令发出去了但车没动」
                    // 也会把解锁键点亮 —— 用户 2026-11-05 反馈的「假高亮」就是这一条。
                    // 不再依赖「控车后车端报文 5~15 秒才跟上」：control() 现在自己读车况求证，
                    // 回来时车况信号已经是新的。
                    if (done) {
                        when (spec.id) {
                            "unlock" -> unlockedHint = true
                            "lock" -> unlockedHint = false
                        }
                    }
                    flash(
                        text = if (done) "执行成功" else "已下发 · 待确认",
                        label = if (spec.kind == TileKind.MOMENTARY) {
                            if (done) "已${spec.title}" else "待确认"
                        } else {
                            null
                        },
                    )
                    // 先把磁贴摆正（系统可能因此自己弹一句状态），再弹我们的结论 ——
                    // 顺序反了的话，我们这句会被系统那句压在后头，用户先看到的就是错的。
                    // 动作型这里熄灭（解锁磁贴若车已解锁会由 [stickyActive] 继续保持亮）。
                    render()
                    if (done) {
                        TileNotice.done(ctx, "${spec.doneText(turningOn)}，执行成功")
                    } else {
                        // 消息本身就是「指令已发出，但车辆状态还没变化…」这类实话，原样给用户
                        TileNotice.pending(ctx, message)
                    }
                },
                onFailure = { e ->
                    // 没发出去：开关型把本地开合态回滚，别让磁贴亮着一个车上根本没开的功能
                    if (spec.kind == TileKind.TOGGLE && previousOn != null) {
                        QuickTiles.setOn(ctx, spec, previousOn)
                    }
                    fail(e)
                },
            )
            delay(FLASH_MS)
            // 期间又点了一次就别把新一轮的「正在…」盖掉
            if (seq == execSeq) render()
        }
    }

    /**
     * 这句回执算不算「车端真的执行了」。
     *
     * 只有两种算数：
     * - 网关返回结果码 0 / 200 → [AppRuntime.control] 的文案以「执行成功」结尾；
     * - 超时后回读车况自证 → 「空调已开启 / 空调已关闭（车端结果码 n）」。
     *
     * 其余全是「已下发（车端结果码 n），请在车辆上确认执行结果」。
     * 把「发出去了」说成「执行成功」，用户就不会再去车边确认，反而耽误事。
     */
    private fun isDone(message: String): Boolean =
        message.endsWith("执行成功") ||
            message.startsWith("空调已开启") ||
            message.startsWith("空调已关闭")

    private suspend fun execute(ctx: Context, turningOn: Boolean): String = withContext(Dispatchers.IO) {
        val runtime = AppRuntime(ctx)
        if (spec.kind == TileKind.MOMENTARY) {
            return@withContext runtime.control(spec.openCommand)
        }
        runtime.control(if (turningOn) spec.openCommand else spec.closeCommand)
    }

    /**
     * 用**进程内已知车况**校准（不联网，同步执行，供 onStartListening 直接调用）。
     *
     * 只接受「比用户最后一次点击更新」的快照：比点击更旧的报文代表的是上一状态，
     * 拿它来改会把用户刚下发的意图覆盖掉。
     */
    private fun applyKnownStatus() {
        if (spec.kind != TileKind.TOGGLE) return
        val lastAction = QuickTiles.lastActionAt(this, spec)
        val status = StatusSnapshot.get(SNAPSHOT_TTL_MS, since = lastAction) ?: return
        val open = isOpen(status) ?: return
        if (QuickTiles.isOn(this, spec) == open) return
        QuickTiles.setOn(this, spec, open, fromUser = false)
    }

    /**
     * 控制中心展开时：**刷一次车况**，顺手把显示状态校准回真实值。
     *
     * 这是两条反馈的共同修复点：
     * - 「控制中心每次出现都要自动刷新车况」→ 每次 onStartListening 都刷一次；
     *   七八块磁贴几乎同时回调，[TileStatusSync.appear] 用 2 秒合并窗口把它们并成**一个**请求；
     * - 「在车里手动关了空调 / 后备箱，磁贴还亮着」→ 展开时就用这份新报文校准显示。
     *
     * 三条安全线：
     * 1. 只按车况改**显示**，点击方向仍看本地（见 [QuickTiles] 上的注释）；
     *    读不到信号（null）就原地不动，绝不拿「读不到」当「关闭」；
     * 2. 刚点过 [ACTION_GRACE_MS] 内不校准：此刻车端大概率还没执行完，
     *    读回来的还是上一条报文，会把你刚下发的意图改回去；
     * 3. 校准过程中用户又点了一次，直接放弃这次结果。
     */
    private suspend fun syncFromVehicle() {
        val ctx = applicationContext
        // 每次展开都联网刷一次（合并窗口内多块磁贴只发一个请求）
        val fresh = TileStatusSync.appear(ctx) ?: return
        if (busy) return
        val lastAction = QuickTiles.lastActionAt(ctx, spec)
        if (System.currentTimeMillis() - lastAction < ACTION_GRACE_MS) return
        if (QuickTiles.lastActionAt(ctx, spec) != lastAction) return

        when {
            spec.kind == TileKind.TOGGLE -> {
                val open = isOpen(fresh) ?: return
                // 读到有效信号才记账；信号为 null 的车型下次展开还会继续尝试
                QuickTiles.markSynced(ctx, spec)
                if (QuickTiles.isOn(ctx, spec) == open) return
                QuickTiles.setOn(ctx, spec, open, fromUser = false)
            }
            // 解锁磁贴：刷新「车是不是解锁着」，它常亮的依据就是这个
            spec.id == "unlock" -> {
                // ⚠️ `CarStatus.locked` 是**已闭锁**，两者互为反相。
                // 早先这里把 locked 直接赋给 unlockedHint，结果「车锁着反而点亮解锁键」
                // （用户 2026-11-05 反馈：闭锁状态下解锁按钮是高亮的）。
                val lockedNow = fresh.locked ?: return
                if (unlockedHint == !lockedNow) return
                unlockedHint = !lockedNow
            }
            else -> return
        }
        if (!busy) render()
    }

    private fun isOpen(status: CarStatus?): Boolean? = when (spec.id) {
        "ac" -> status?.acOn
        "trunk" -> status?.trunkOpen
        "window" -> status?.windowOpen
        "sunshade" -> status?.sunshadeOpen
        else -> null
    }

    /**
     * 记一句「刚执行完」的文字，[FLASH_MS] 内优先显示它。
     *
     * 存成一段时间内有效的短句（而不是当场 render 一次就算完）：
     * 这段时间里控制中心收起又展开、或者车况校准触发重画，文字都不会被
     * 「点击执行」立刻顶掉 —— 用户回来还能看见刚才那一下成没成。
     *
     * @param label 磁贴名要临时改成什么（动作型磁贴用它报状态，见 [render]）
     */
    private fun flash(text: String, label: String? = null) {
        flashText = text
        flashLabel = label
        flashAt = System.currentTimeMillis()
    }

    private fun flashFresh(): Boolean = System.currentTimeMillis() - flashAt < FLASH_MS

    private fun flashSubtitle(): String? = flashText?.takeIf { flashFresh() }

    private fun flashLabelText(): String? = flashLabel?.takeIf { flashFresh() }

    /**
     * 画磁贴。
     *
     * 高亮语义（用户 2026-11-05 定稿）：
     * - **动作型**（上锁 / 鸣笛 / 预热）：执行中亮起，执行成功后熄灭；
     * - **解锁**：不跟「发起」亮。车闭锁时它是灭的，**车真的解锁之后才亮** ——
     *   所以它的亮灭只由 [stickyActive] 决定（见那里的注释）。
     * - **开关型**（空调 / 后备箱 / 车窗 / 遮阳帘）：跟随实际开合，开着就亮；
     *   亮着时点击是「关」，灭着时点击是「开」，点击瞬间先按意图亮/灭，失败再回滚。
     *
     * ⚠️ 动作型亮起时，MIUI 这类 ROM 可能在提示里补一句「已开启」（2026-11-05 的
     * 「上锁已开启」就是它编的，不是我们的文案，改 [Tile.subtitle] 也没用）。
     * 现在的处理：执行中把磁贴名改成「上锁中」，同时自己弹一句「车辆上锁中…」，
     * 让最后留在屏幕上的是我们的话；成功后立刻熄灭，那句「已开启」也就没了立足点。
     */
    private fun render(subtitle: String? = null, active: Boolean = false, label: String? = null) {
        val tile = qsTile ?: return
        val text = subtitle ?: defaultSubtitle()
        tile.label = label ?: flashLabelText() ?: spec.title
        tile.icon = Icon.createWithResource(this, spec.iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 部分 ROM 拿 stateDescription 当磁贴下的说明文字，给同一句，
            // 免得它退回「已开启 / 已关闭」这种按开关语义猜出来的词。
            tile.stateDescription = text
        }
        tile.state = if (active || stickyActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    /**
     * 不靠「这一次点击」也该一直亮着的状态。
     *
     * - 开关型：本地记住的开合态（由点击意图 + 车况校准共同维护）；
     * - 解锁：车未闭锁 → 亮（用户明确要求「车辆解锁状态下解锁按钮要高亮」）；
     * - 上锁 / 鸣笛 / 预热：不常亮 —— 车绝大多数时间都锁着，常亮没有信息量，
     *   鸣笛、预热更是瞬时动作。
     */
    private fun stickyActive(): Boolean = tileActive(
        kind = spec.kind,
        id = spec.id,
        localOn = spec.kind == TileKind.TOGGLE && QuickTiles.isOn(this, spec),
        unlockedHint = unlockedHint,
        locked = StatusSnapshot.get(SNAPSHOT_TTL_MS)?.locked,
    )

    private fun defaultSubtitle(): String {
        flashSubtitle()?.let { return it }
        return when {
            spec.kind != TileKind.TOGGLE -> "点击执行"
            QuickTiles.isOn(this, spec) -> "已开启 · 点击关闭"
            else -> "已关闭 · 点击开启"
        }
    }

    /**
     * 控车失败：说清原因 + 指路。
     *
     * 副标题只放得下 5 个字（[ControlHint.short]），通知横幅给完整一句，
     * 需要用户动手的（登录失效 / 没设密码 / 没选车）再把 App 拉起来并直达那一页。
     */
    private fun fail(e: Throwable?) {
        val hint = ControlFailure.of(e)
        flash(hint.short, label = if (spec.kind == TileKind.MOMENTARY) "未成功" else null)
        // 同样先摆磁贴再弹提示：系统若跟着弹一句自己的状态词，也不会压住真正的原因
        render()
        TileNotice.failed(applicationContext, hint.message)
        when (hint.action) {
            ControlFix.RELOGIN -> {
                ControlFailure.markSessionExpired(applicationContext)
                openApp(AppNavRequest.RELOGIN)
            }
            ControlFix.OPEN_PIN -> openApp(AppNavRequest.OP_PASSWORD)
            ControlFix.OPEN_APP -> openApp(null)
            ControlFix.NONE -> Unit
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
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        /**
         * 刚点完磁贴的宽限期：这段时间里车况可能还是上一条报文，
         * 用它去校准会把用户刚下发的意图改回去。
         */
        const val ACTION_GRACE_MS = 8_000L

        /** 进程内车况快照的最长可用年龄。再老的话宁可联网拉一次。 */
        const val SNAPSHOT_TTL_MS = 30_000L

        /**
         * 「执行成功 / 执行失败」在副标题上停留多久。
         *
         * 2.5 秒：够看清一行字，又不至于让磁贴长时间停在结果上 ——
         * 之后该回到「点击执行」或「已开启 · 点击关闭」这些可以再点的常态。
         */
        const val FLASH_MS = 2_500L
    }
}

/**
 * 磁贴共用的车况刷新。
 *
 * 控制中心一拉开，七八块磁贴会同时 `onStartListening`。如果每块都自己
 * `refreshStatus()`，一次展开就是七八个请求（每个都要唤醒一次车上 T-Box）。
 * 这里用一把锁 + 2 秒合并窗口：**同一瞬间只发一个请求**，结果共享；
 * 窗口一过，下次展开（用户重新拉开控制中心）照常再刷一次 —— 用户要的是
 * 「每次出现都是新的」，而不是「一天只准刷一次」。
 */
private object TileStatusSync {
    /**
     * 合并窗口。
     *
     * 只用来吃掉「同一次展开里七八块磁贴的重复调用」：它们几乎同时发生（几十毫秒内）。
     * 2 秒是宽裕值，也远小于「用户收起再拉开」的间隔 —— 手动重新展开仍会真的联网。
     */
    private const val MERGE_WINDOW_MS = 2_000L

    private val mutex = Mutex()
    private var at = 0L
    private var cached: CarStatus? = null

    /**
     * 展开控制中心时刷一次车况。
     *
     * 与之前那版 15 秒缓存不同：这里**不看缓存多久**，只要过了合并窗口就重新请求。
     * 失败时退回上次结果（拿旧数据也比空白好，调用方还会按 null 决定不动状态）。
     */
    suspend fun appear(context: Context): CarStatus? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - at < MERGE_WINDOW_MS) return@withLock cached
        val fresh = runCatching { AppRuntime(context).refreshStatus() }.getOrNull()
            ?: return@withLock cached
        cached = fresh
        at = now
        fresh
    }
}
