package com.leapcontrol.app.tiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.leapcontrol.app.R

/** 磁贴行为：MOMENTARY 为一次性动作，TOGGLE 按车况在开/关之间取反。 */
enum class TileKind { MOMENTARY, TOGGLE }

/**
 * 一个系统控制中心磁贴的定义。
 *
 * [openCommand] / [closeCommand] 对应 Commands.build(name) 的指令名；
 * MOMENTARY 只用 [openCommand]。
 */
data class TileSpec(
    val id: String,
    val title: String,
    val iconRes: Int,
    val service: Class<out VehicleTileService>,
    val kind: TileKind,
    val openCommand: String,
    val closeCommand: String = "",
    /** 一次性动作执行中/完成时显示的文案 */
    val doing: String = "执行中…",
    val done: String = "已执行",
)

object QuickTiles {
    val ALL: List<TileSpec> = listOf(
        TileSpec(
            "lock", "上锁", R.drawable.ic_tile_lock, LockTileService::class.java,
            TileKind.MOMENTARY, "lock", doing = "正在上锁…", done = "已上锁",
        ),
        TileSpec(
            "unlock", "解锁", R.drawable.ic_tile_unlock, UnlockTileService::class.java,
            TileKind.MOMENTARY, "unlock", doing = "正在解锁…", done = "已解锁",
        ),
        TileSpec(
            "horn", "鸣笛寻车", R.drawable.ic_tile_horn, HornTileService::class.java,
            TileKind.MOMENTARY, "horn", doing = "正在寻车…", done = "已鸣笛",
        ),
        TileSpec(
            "ac", "空调", R.drawable.ic_tile_ac, ClimateTileService::class.java,
            TileKind.TOGGLE, "acOn", "acOff", doing = "空调执行中…", done = "空调已执行",
        ),
        TileSpec(
            "trunk", "后备箱", R.drawable.ic_tile_trunk, TrunkTileService::class.java,
            TileKind.TOGGLE, "trunkOpen", "trunkClose", doing = "后备箱执行中…", done = "后备箱已执行",
        ),
        TileSpec(
            "window", "车窗", R.drawable.ic_tile_window, WindowTileService::class.java,
            TileKind.TOGGLE, "windowOpen", "windowClose", doing = "车窗执行中…", done = "车窗已执行",
        ),
        TileSpec(
            "batteryPreheat", "电池预热", R.drawable.ic_tile_battery_heat, BatteryHeatTileService::class.java,
            TileKind.MOMENTARY, "batteryPreheat", doing = "正在预热…", done = "预热已开启",
        ),
        TileSpec(
            "sunshade", "遮阳帘", R.drawable.ic_tile_sunshade, SunshadeTileService::class.java,
            TileKind.TOGGLE, "sunshadeOpen", "sunshadeClose", doing = "遮阳帘执行中…", done = "遮阳帘已执行",
        ),
    )

    private const val PREFS = "quick_tiles_state"

    private fun prefsOf(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAdded(context: Context, spec: TileSpec): Boolean =
        prefsOf(context).getBoolean("added_${spec.id}", false)

    fun markAdded(context: Context, spec: TileSpec, added: Boolean) {
        prefsOf(context).edit().putBoolean("added_${spec.id}", added).apply()
    }

    // ---------------- 开关磁贴的开合态（本地记忆）----------------
    //
    // 磁贴点击的**方向只由这里决定**：先读本地开合态取反，再下发指令，下发成功才回写。
    //
    // 曾经用 `refreshStatus()` 回来的车况当判据（status.acOn / trunkOpen / windowOpen / sunshadeOpen），
    // 但这些信号在部分车型上根本读不到（null），`if (车况开着) 关 else 开` 就永远判成「当前是关的」，
    // 于是每次点磁贴都只发开启指令 —— 用户看到的就是「点开了之后没办法取消」。
    // 空调卡片当初也是同一个坑（见 ComfortScreens 里那段注释）。
    //
    // 车况只用来**校准显示**（用户可能在车里手动开了后备箱，或者手动关了空调）：
    // 磁贴展开时读车况，非 null 就按车况修正，null 一律不动本地状态。
    // 点击方向仍以本地为准，所以「读不到信号」的车型也不会退化成只能开不能关。

    fun isOn(context: Context, spec: TileSpec): Boolean =
        prefsOf(context).getBoolean("state_${spec.id}", false)

    /**
     * 写开合态。
     *
     * [fromUser] 决定要不要刷新「最后一次用户动作时间」`state_at`：
     * - 用户点磁贴（true）→ 两样都写，这个时间戳既是动作记录，也是「别拿更旧的车况覆盖我」的凭据；
     * - 车况校准（false）→ **只写状态**。校准不是用户动作，写进去会让之后 8 秒的宽限期
     *   误以为是刚点过，反而把真正的用户点击挡在外面。
     */
    fun setOn(context: Context, spec: TileSpec, on: Boolean, fromUser: Boolean = true) {
        val editor = prefsOf(context).edit().putBoolean("state_${spec.id}", on)
        if (fromUser) editor.putLong("state_at_${spec.id}", System.currentTimeMillis())
        editor.apply()
    }

    /** 上一次由用户（或指令回写）改动这块磁贴状态的时间。 */
    fun lastActionAt(context: Context, spec: TileSpec): Long =
        prefsOf(context).getLong("state_at_${spec.id}", 0L)

    /** 上一次用车况校准这块磁贴的时间。 */
    fun syncedAt(context: Context, spec: TileSpec): Long =
        prefsOf(context).getLong("sync_at_${spec.id}", 0L)

    fun markSynced(context: Context, spec: TileSpec) {
        prefsOf(context).edit().putLong("sync_at_${spec.id}", System.currentTimeMillis()).apply()
    }

    /** 让所有磁贴重新走一次 onStartListening，刷新文案与激活态。 */
    fun refreshAll(context: Context) {
        ALL.forEach { spec ->
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, spec.service))
            }
        }
    }

    /**
     * Android 13+ 直接弹系统底部弹窗一键添加；低版本只能提示用户手动去控制中心编辑。
     */
    fun requestAdd(context: Context, spec: TileSpec, onResult: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                context,
                "请下拉控制中心 → 点「编辑」→ 把【${spec.title}】拖到常用区域",
                Toast.LENGTH_LONG,
            ).show()
            onResult?.invoke(false)
            return
        }
        try {
            val sbm = context.getSystemService(StatusBarManager::class.java) ?: run {
                onResult?.invoke(false)
                return
            }
            sbm.requestAddTileService(
                ComponentName(context, spec.service),
                spec.title,
                Icon.createWithResource(context, spec.iconRes),
                ContextCompat.getMainExecutor(context),
            ) { code ->
                val ok = code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                if (ok) markAdded(context, spec, true)
                val text = when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加【${spec.title}】到控制中心"
                    // 系统没有「移除磁贴」的接口，只能告诉用户去哪儿拖走它
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "【${spec.title}】已在控制中心，要移除请下拉后点「编辑」拖走它"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "已取消添加"
                    else -> "添加失败（系统拒绝）"
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                onResult?.invoke(ok)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "添加失败：${e.message}", Toast.LENGTH_SHORT).show()
            onResult?.invoke(false)
        }
    }
}
