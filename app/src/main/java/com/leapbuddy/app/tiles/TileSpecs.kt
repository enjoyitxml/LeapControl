package com.leapbuddy.app.tiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.leapbuddy.app.R

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

    fun isAdded(context: Context, spec: TileSpec): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("added_${spec.id}", false)

    fun markAdded(context: Context, spec: TileSpec, added: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("added_${spec.id}", added).apply()
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
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "【${spec.title}】已在控制中心"
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
