package com.leapcontrol.app.tiles

import android.content.Context
import com.leapcontrol.app.SessionStore
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.ControlFailure
import com.leapcontrol.app.ui.StatusSnapshot
import com.leapcontrol.app.ui.WindowControl
import com.leapcontrol.app.ui.WindowStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 磁贴控车的执行层，对应上游 `ControlService` 的「全局在途标记 + 后台跑指令」。
 *
 * ## 为什么不是 `startForegroundService`
 * 上游三块磁贴点击后 `startForegroundService(ControlService)`，由前台服务在网络线程里执行。
 * 但 Android 12 起**后台启动前台服务会被系统直接拒绝**，而控制中心展开时 TileService
 * 恰恰是后台进程 —— 字面照搬会让它 catch 掉异常后什么都没发出去（磁贴显示「正在…」却永远不动）。
 * 所以执行载体换成**进程级** CoroutineScope：一样是「点完立刻返回、指令在后台跑、
 * 全局只允许一条在途」，而且不会随控制中心收起被取消（项目里的既有纪律也是如此）。
 *
 * ## 高亮怎么收
 * 点下去当帧的那一下由 [VehicleTileService] 自己负责（见 `clickedActive`）。
 * 这里只负责**在指令有了结果之后回调一次** `onFinished`：
 * 瞬时动作据此立刻熄灭（用户 2026-11-07：上锁 / 鸣笛受理后不该继续亮 10 秒），
 * 开关型据此按目标状态重画（成功保持亮、失败回滚灭）。
 *
 * 回调**不阻塞**指令链路，也不在这个线程里碰 UI：内部切主线程再调，磁贴随叫随到。
 */
internal object TileCommandRunner {

    /** 在途记录：哪块磁贴、往哪个方向。副标题靠它说「空调开启中…」。 */
    data class InFlight(val id: String, val turningOn: Boolean)

    private val inFlight = AtomicReference<InFlight?>(null)

    /** 进程级：不挂在 TileService 实例上，控制中心收起 / 面板销毁都带不走它。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val syncMutex = Mutex()
    private var lastSyncAt = 0L

    /** 控制中心一次展开会同时唤醒八块磁贴，合并成一个车况请求。 */
    private const val SYNC_MERGE_MS = 2_000L

    /**
     * 刚点完磁贴的宽限期：车端 T-Box 唤醒 + 执行要 5~15 秒，这段时间拉回来的
     * 往往还是上一条报文，拿它校准会把用户刚下发的意图改回去。
     */
    private const val ACTION_GRACE_MS = 8_000L

    fun inFlight(): InFlight? = inFlight.get()

    fun isCommandInFlight(): Boolean = inFlight.get() != null

    /**
     * 下发一条指令并立刻返回。
     *
     * @param onFinished 指令有了结果之后回调（主线程）。true = 网关受理了；false = 没发出去。
     * @return false 表示已有指令在途（调用方负责提示用户稍候），没有重复下发。
     */
    fun execute(
        context: Context,
        spec: TileSpec,
        command: String,
        turningOn: Boolean,
        onFinished: (Boolean) -> Unit,
    ): Boolean {
        if (!inFlight.compareAndSet(null, InFlight(spec.id, turningOn))) return false
        val app = context.applicationContext
        scope.launch {
            val result = runCatching { AppRuntime(app).controlForWidget(command) }
            result.fold(
                onSuccess = {
                    // 这次通了，撤掉「上次因登录失效失败」的标记
                    ControlFailure.clearSessionExpired(app)
                    // 上游 ControlService 结束时也会给一条结果提示。这里只说「已下发」：
                    // controlForWidget 拿的是网关回执，不等于车端执行，不能写「执行成功」。
                    // 固定 id 会覆盖掉刚才那条「正在…」，不会在通知栏堆两条。
                    TileNotice.pending(app, "${spec.title}指令已发出，车况稍后更新")
                },
                onFailure = { e ->
                    val hint = ControlFailure.of(e)
                    // 下发失败就把乐观写下的开合态回滚：别亮着一个车上根本没开的功能
                    if (spec.kind == TileKind.TOGGLE) QuickTiles.setOn(app, spec, !turningOn)
                    TileNotice.failed(app, hint.message)
                    if (hint.action == com.leapcontrol.app.ui.ControlFix.RELOGIN) {
                        ControlFailure.markSessionExpired(app)
                    }
                },
            )
            // 先撤在途标记再回调：磁贴那边据此决定「还要不要亮」，
            // 顺序反了会让瞬时动作在回调之后又被判成「还在执行」。
            inFlight.set(null)
            runCatching {
                withContext(Dispatchers.Main) { onFinished(result.isSuccess) }
            }
            // 结果回来后再拉一次车况 + 让系统重新问磁贴（上游 requestTilesUpdate 的位置）
            refreshStatus(app)
            QuickTiles.refreshAll(app)
        }
        return true
    }

    /** 控制中心每次展开都刷一次车况；**不阻塞**磁贴渲染，也不拿它当点击的前提。 */
    fun appear(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshStatus(app) }
    }

    /**
     * 拉一次车况并按真实信号校准开关型磁贴的显示状态。
     * 读不到信号（null）就原地不动，绝不拿「读不到」当「已关闭」。
     */
    private suspend fun refreshStatus(context: Context) = syncMutex.withLock {
        val now = System.currentTimeMillis()
        if (now - lastSyncAt < SYNC_MERGE_MS) return@withLock
        lastSyncAt = now
        val fresh = runCatching { AppRuntime(context).refreshStatus() }.getOrNull() ?: return@withLock
        StatusSnapshot.put(fresh)
        QuickTiles.ALL.forEach { spec ->
            if (spec.kind == TileKind.MOMENTARY) return@forEach
            // 档位型（车窗）：车况只能证明「全关」，就用它把档位归零；
            // 报「有窗开着」时分不出微开 / 全开，**不能**拿它改档位，保留本地记录。
            if (spec.kind == TileKind.CYCLE) {
                val vin = SessionStore(context).load().selectedVin
                val now = System.currentTimeMillis()
                if (now - WindowControl.savedAt(context, vin) < ACTION_GRACE_MS) return@forEach
                if (fresh.windowOpen == false) {
                    WindowControl.save(context, vin, WindowStage.CLOSED)
                }
                return@forEach
            }
            if (System.currentTimeMillis() - QuickTiles.lastActionAt(context, spec) < ACTION_GRACE_MS) {
                return@forEach
            }
            val open = statusOn(spec.id, fresh) ?: return@forEach
            QuickTiles.setOn(context, spec, open, fromUser = false)
        }
    }
}
