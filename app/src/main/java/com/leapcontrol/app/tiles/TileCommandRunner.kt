package com.leapcontrol.app.tiles

import android.content.Context
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.ControlFailure
import com.leapcontrol.app.ui.StatusSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

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
 * ## 与上游一致的行为
 * - [isCommandInFlight] 全局去重，磁贴点击时据此拦第二次；
 * - [execute] 发完就返回，**不等车端结果**，磁贴按自己的计时器复位；
 * - 指令结束后重新问一次磁贴，按最新车况把亮灭摆正（上游 `requestTilesUpdate`）。
 */
internal object TileCommandRunner {

    private val inFlight = AtomicBoolean(false)

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

    fun isCommandInFlight(): Boolean = inFlight.get()

    /**
     * 下发一条指令并立刻返回。
     *
     * @return false 表示已有指令在途（调用方负责提示用户稍候），没有重复下发。
     */
    fun execute(context: Context, spec: TileSpec, command: String, turningOn: Boolean): Boolean {
        if (!inFlight.compareAndSet(false, true)) return false
        val app = context.applicationContext
        scope.launch {
            val result = runCatching { AppRuntime(app).controlForWidget(command) }
            inFlight.set(false)
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
            if (spec.kind != TileKind.TOGGLE) return@forEach
            if (System.currentTimeMillis() - QuickTiles.lastActionAt(context, spec) < ACTION_GRACE_MS) {
                return@forEach
            }
            val open = when (spec.id) {
                "ac" -> fresh.acOn
                "trunk" -> fresh.trunkOpen
                "window" -> fresh.windowOpen
                "sunshade" -> fresh.sunshadeOpen
                else -> null
            } ?: return@forEach
            QuickTiles.setOn(context, spec, open, fromUser = false)
        }
    }
}
