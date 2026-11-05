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
 * 这块磁贴平时该不该亮（纯函数，可单测；「执行中」的临时高亮由调用方另加）。
 *
 * 规则（用户 2026-11-05 定稿）：
 * - 开关型（空调 / 后备箱 / 车窗 / 遮阳帘）：**功能开着就一直亮**，
 *   亮着时点击是「关」，灭着时点击是「开」—— 所以亮灭必须跟着开合态走；
 * - 解锁：车**未闭锁**时亮着。看到解锁键亮，就知道车现在没锁；
 * - 上锁 / 鸣笛 / 预热：不常亮 —— 车绝大多数时间都锁着，常亮没有信息量；
 *   鸣笛和预热更是瞬时动作，做完就结束。
 *
 * @param localOn 开关型本地记住的开合态（点击意图 + 车况校准共同维护）
 * @param unlockedHint 刚控过车时的即时结论（null = 没控过，退回车况判断）
 * @param locked 车况里的闭锁信号（null = 这台车读不到）
 */
internal fun tileActive(
    kind: TileKind,
    id: String,
    localOn: Boolean,
    unlockedHint: Boolean?,
    locked: Boolean?,
): Boolean = when {
    kind == TileKind.TOGGLE -> localOn
    id == "unlock" -> unlockedHint ?: (locked == false)
    else -> false
}

/**
 * 一个系统控制中心磁贴的定义。
 *
 * [openCommand] / [closeCommand] 对应 Commands.build(name) 的指令名；
 * MOMENTARY 只用 [openCommand]。
 *
 * ## 文案口径
 * 副标题说的是**车正在发生什么**，不是「指令是怎么说的」：
 * 上锁 → 「车辆上锁中…」→「执行成功」→「点击执行」。
 * 早先写的「上锁已开启」既不是动作也不是状态，读的人得先猜一遍才明白。
 *
 * - [doing]：一次性动作执行中的副标题；
 * - [opening] / [closing]：开关型「正在打开 / 正在关闭」的副标题，留空就退回 [doing]；
 * - [done] / [doneOff]：成功提示里的完成态（「车辆已上锁」「空调已关闭」），
 *   和「执行成功」拼成一句：车辆已上锁，执行成功。
 */
data class TileSpec(
    val id: String,
    val title: String,
    val iconRes: Int,
    val service: Class<out VehicleTileService>,
    val kind: TileKind,
    val openCommand: String,
    val closeCommand: String = "",
    /** 一次性动作执行中 / 开关型兜底的副标题 */
    val doing: String = "执行中…",
    val opening: String = "",
    val closing: String = "",
    /** 成功后提示里的完成态（开关型是「打开」那一路） */
    val done: String = "",
    /** 开关型「关闭」那一路的完成态，留空则用 [done] */
    val doneOff: String = "",
) {
    /** 执行中的副标题：开关型分「开启 / 关闭」，一次性动作只有一句。 */
    fun doingText(turningOn: Boolean): String = when {
        kind == TileKind.MOMENTARY -> doing
        turningOn -> opening.ifBlank { doing }
        else -> closing.ifBlank { doing }
    }

    /** 成功提示里的完成态。 */
    fun doneText(turningOn: Boolean): String = when {
        kind == TileKind.MOMENTARY -> done
        turningOn -> done.ifBlank { title }
        else -> doneOff.ifBlank { done.ifBlank { title } }
    }
}

object QuickTiles {
    val ALL: List<TileSpec> = listOf(
        TileSpec(
            "lock", "上锁", R.drawable.ic_tile_lock, LockTileService::class.java,
            TileKind.MOMENTARY, "lock",
            doing = "车辆上锁中…", done = "车辆已上锁",
        ),
        TileSpec(
            "unlock", "解锁", R.drawable.ic_tile_unlock, UnlockTileService::class.java,
            TileKind.MOMENTARY, "unlock",
            doing = "车辆解锁中…", done = "车辆已解锁",
        ),
        TileSpec(
            "horn", "鸣笛寻车", R.drawable.ic_tile_horn, HornTileService::class.java,
            TileKind.MOMENTARY, "horn",
            doing = "鸣笛寻车中…", done = "鸣笛寻车",
        ),
        TileSpec(
            "ac", "空调", R.drawable.ic_tile_ac, ClimateTileService::class.java,
            TileKind.TOGGLE, "acOn", "acOff",
            doing = "空调调节中…", opening = "空调开启中…", closing = "空调关闭中…",
            done = "空调已开启", doneOff = "空调已关闭",
        ),
        TileSpec(
            "trunk", "后备箱", R.drawable.ic_tile_trunk, TrunkTileService::class.java,
            TileKind.TOGGLE, "trunkOpen", "trunkClose",
            doing = "后备箱调节中…", opening = "后备箱开启中…", closing = "后备箱关闭中…",
            done = "后备箱已开启", doneOff = "后备箱已关闭",
        ),
        TileSpec(
            "window", "车窗", R.drawable.ic_tile_window, WindowTileService::class.java,
            TileKind.TOGGLE, "windowOpen", "windowClose",
            doing = "车窗调节中…", opening = "车窗调整中…", closing = "车窗关闭中…",
            done = "车窗已半开", doneOff = "车窗已全关",
        ),
        TileSpec(
            "batteryPreheat", "电池预热", R.drawable.ic_tile_battery_heat, BatteryHeatTileService::class.java,
            TileKind.MOMENTARY, "batteryPreheat",
            doing = "电池预热中…", done = "电池预热",
        ),
        TileSpec(
            "sunshade", "遮阳帘", R.drawable.ic_tile_sunshade, SunshadeTileService::class.java,
            TileKind.TOGGLE, "sunshadeOpen", "sunshadeClose",
            doing = "遮阳帘调节中…", opening = "遮阳帘打开中…", closing = "遮阳帘关闭中…",
            done = "遮阳帘已打开", doneOff = "遮阳帘已关闭",
        ),
    )

    private const val PREFS = "quick_tiles_state"

    private fun prefsOf(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAdded(context: Context, spec: TileSpec): Boolean =
        prefsOf(context).getBoolean("added_${spec.id}", false)

    /** 按 id 取定义（桌面小组件要复用它记的开合态，见 widget/VehicleWidgetProvider）。 */
    fun byId(id: String): TileSpec? = ALL.firstOrNull { it.id == id }

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
