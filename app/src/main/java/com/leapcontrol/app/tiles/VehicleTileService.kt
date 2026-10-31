package com.leapcontrol.app.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
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
        // 不用等下面那个异步校准（它可能要发一次网络请求）
        if (!busy) applyKnownStatus()
        if (!busy) render()
        // 异步再校一次：顺手覆盖「App 完全没跑、进程内没有快照」的情况
        if (spec.kind == TileKind.TOGGLE) scope.launch { syncFromVehicle() }
    }

    override fun onClick() {
        super.onClick()
        if (busy) return

        val ctx = applicationContext
        val store = SessionStore(ctx)
        val session = store.load()

        if (session.oldAuth == null && session.newAuth == null) {
            render("请先登录", active = false)
            toast("请先在零控 App 登录")
            openApp()
            return
        }
        if (session.selectedVin.isBlank()) {
            render("未选择车辆", active = false)
            toast("未选择车辆，请先打开 App")
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            render("未设置密码", active = false)
            toast("请先在「我的」里设置控车密码")
            openApp()
            return
        }

        // 本次要下发的方向：一次性动作固定「执行」，开关磁贴取反本地记住的开合态。
        val turningOn = spec.kind == TileKind.MOMENTARY || !QuickTiles.isOn(ctx, spec)

        busy = true
        render(spec.doing, active = turningOn)
        scope.launch {
            val result = runCatching { execute(ctx, turningOn) }
            busy = false
            result.fold(
                onSuccess = { message ->
                    // 指令下发成功才落状态：失败时磁贴不能显示成「已开启」
                    if (spec.kind == TileKind.TOGGLE) QuickTiles.setOn(ctx, spec, turningOn)
                    toast(message)
                    render(message.take(18), active = spec.kind == TileKind.TOGGLE && turningOn)
                },
                onFailure = { e ->
                    toast("执行失败：${e.message?.take(30) ?: "网络异常"}")
                    render("执行失败", active = false)
                },
            )
            delay(2500)
            render()
        }
    }

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
     * 用车况校准开合态（只在后台悄悄改状态，不弹任何提示、不打断用户）。
     *
     * 这是「在车里手动关了空调 / 后备箱，控制中心那格还亮着」的修复路径，
     * 因此校准必须**跟着控制中心的展开走**，而不是隔一分钟才放行一次。
     *
     * 四条安全线：
     * 1. 状态只按车况改显示，**点击方向仍看本地**（见 [QuickTiles] 上的注释），
     *    读不到信号（null）就原地不动，绝不拿「读不到」当「关闭」；
     * 2. 快照比用户最后一次点击旧 → 不用它；刚点过又没有更新快照 → 等宽限期过去再说，
     *    否则旧报文会把用户刚下发的意图改回去；
     * 3. 同一块磁贴两次校准之间留最小间隔，真正挡住重复请求的是 [TileStatusSync] 的 15 秒缓存
     *    （控制中心一次展开会唤醒七八块磁贴，各自发请求就是七八个）；
     * 4. 校准过程中用户又点了一次，直接放弃这次结果。
     */
    private suspend fun syncFromVehicle() {
        if (spec.kind != TileKind.TOGGLE) return
        val ctx = applicationContext
        val now = System.currentTimeMillis()
        val lastAction = QuickTiles.lastActionAt(ctx, spec)

        var status = StatusSnapshot.get(SNAPSHOT_TTL_MS, since = lastAction)
        if (status == null) {
            // 没有比点击更新的进程内快照。刚点过就先别校：
            // 此刻车端大概率还没执行完，读回来的还是上一条报文。
            if (now - lastAction < ACTION_GRACE_MS) return
            if (now - QuickTiles.syncedAt(ctx, spec) < SYNC_INTERVAL_MS) return
            status = TileStatusSync.status(ctx, minAt = lastAction) ?: return
        }

        val open = isOpen(status) ?: return
        if (busy) return
        // 校准期间用户点过磁贴：以那次下发的方向为准
        if (QuickTiles.lastActionAt(ctx, spec) != lastAction) return
        // 读到有效信号才记账；信号为 null 的车型下次展开还会继续尝试
        QuickTiles.markSynced(ctx, spec)
        if (QuickTiles.isOn(ctx, spec) == open) return
        QuickTiles.setOn(ctx, spec, open, fromUser = false)
        if (!busy) render()
    }

    private fun isOpen(status: CarStatus?): Boolean? = when (spec.id) {
        "ac" -> status?.acOn
        "trunk" -> status?.trunkOpen
        "window" -> status?.windowOpen
        "sunshade" -> status?.sunshadeOpen
        else -> null
    }

    private fun render(subtitle: String? = null, active: Boolean = false) {
        val tile = qsTile ?: return
        tile.label = spec.title
        tile.icon = Icon.createWithResource(this, spec.iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: defaultSubtitle()
        }
        tile.state = when {
            active -> Tile.STATE_ACTIVE
            spec.kind == TileKind.TOGGLE && QuickTiles.isOn(this, spec) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun defaultSubtitle(): String = when {
        spec.kind != TileKind.TOGGLE -> "点击执行"
        QuickTiles.isOn(this, spec) -> "已开启 · 点击关闭"
        else -> "已关闭 · 点击开启"
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
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
         * 同一块磁贴两次车况校准之间的最小间隔。
         *
         * 原来是一分钟，结果用户在车里关掉空调/后备箱后马上拉开控制中心，磁贴还亮着 ——
         * 那 60 秒里校准被直接跳过，用户看到的就是「状态永远不回来」。
         * 真正的请求节流由 [TileStatusSync]（15 秒快照缓存）负责，这里只防同一瞬间的重复调用。
         */
        const val SYNC_INTERVAL_MS = 5_000L

        /**
         * 刚点完磁贴的宽限期：这段时间里车况可能还是上一条报文，
         * 用它去校准会把用户刚下发的意图改回去。
         */
        const val ACTION_GRACE_MS = 8_000L

        /** 进程内车况快照的最长可用年龄。再老的话宁可联网拉一次。 */
        const val SNAPSHOT_TTL_MS = 30_000L
    }
}

/**
 * 磁贴共用的车况快照。
 *
 * 控制中心一拉开，七八块磁贴会同时 onStartListening，各自 `refreshStatus()` 就是七八个请求；
 * 这里用一把锁 + 15 秒缓存把它们合并成一次。
 */
private object TileStatusSync {
    private const val TTL_MS = 15_000L
    private val mutex = Mutex()
    private var at = 0L
    private var cached: CarStatus? = null

    /**
     * @param minAt 只接受**晚于**这个时刻拉到的报文。传「用户最后一次点磁贴的时间」，
     *   缓存里如果是更早的报文就重新拉，避免拿旧状态盖掉用户刚下发的指令。
     */
    suspend fun status(context: Context, minAt: Long = 0L): CarStatus? = mutex.withLock {
        val now = System.currentTimeMillis()
        val hit = cached
        if (hit != null && now - at < TTL_MS && at >= minAt) return@withLock hit
        val fresh = runCatching { AppRuntime(context).refreshStatus() }.getOrNull()
            ?: return@withLock null
        cached = fresh
        at = now
        fresh
    }
}
