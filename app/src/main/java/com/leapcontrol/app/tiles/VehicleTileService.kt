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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统控制中心磁贴基类。
 *
 * 点击后直接在本进程内下发控车指令（不需要常驻前台服务）：
 * 指令通常在 1~3 秒内完成，即使进程随后被回收，指令也已到达车端。
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
        if (!busy) render()
    }

    override fun onClick() {
        super.onClick()
        if (busy) return

        val ctx = applicationContext
        val store = SessionStore(ctx)
        val session = store.load()

        if (session.oldAuth == null && session.newAuth == null) {
            render("请先登录")
            toast("请先在零控 App 登录")
            openApp()
            return
        }
        if (session.selectedVin.isBlank()) {
            render("未选择车辆")
            toast("未选择车辆，请先打开 App")
            return
        }
        if (store.loadOpPassword().isNullOrBlank()) {
            render("未设置密码")
            toast("请先在「我的」里设置控车密码")
            openApp()
            return
        }

        busy = true
        render(spec.doing, active = true)
        scope.launch {
            val message = runCatching { execute(ctx) }
                .getOrElse { "执行失败：${it.message?.take(30) ?: "网络异常"}" }
            toast(message)
            busy = false
            render(message.take(18), active = false)
            delay(2500)
            render()
        }
    }

    private suspend fun execute(ctx: Context): String = withContext(Dispatchers.IO) {
        val runtime = AppRuntime(ctx)
        if (spec.kind == TileKind.MOMENTARY) {
            return@withContext runtime.control(spec.openCommand)
        }
        // 复合磁贴：先拉一次车况决定方向；拉不到就用磁贴当前激活态推断
        val status = runCatching { runtime.refreshStatus() }.getOrNull()
        val openNow = isOpen(status)
        val shouldOpen = openNow?.not() ?: (qsTile?.state != Tile.STATE_ACTIVE)
        val message = runtime.control(if (shouldOpen) spec.openCommand else spec.closeCommand)
        prefs.edit().putBoolean("state_${spec.id}", shouldOpen).apply()
        message
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
            spec.kind == TileKind.TOGGLE && prefs.getBoolean("state_${spec.id}", false) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun defaultSubtitle(): String = when {
        spec.kind != TileKind.TOGGLE -> "点击执行"
        prefs.getBoolean("state_${spec.id}", false) -> "已开启 · 点击关闭"
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
}
