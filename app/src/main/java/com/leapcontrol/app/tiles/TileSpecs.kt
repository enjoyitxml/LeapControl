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
import com.leapcontrol.app.ui.CarStatus

/**
 * 磁贴「亮着」到底表达什么 —— 用户 2026-11-07 定稿。
 *
 * 两类，规则各一句：
 * - **瞬时动作**（上锁、鸣笛寻车）：亮 = 这条指令正在发。点下去当帧就亮，
 *   网关受理后**立刻灭**；亮着的这段时间本身就在告诉用户「还在发 / 发完了」。
 * - **开关型**（解锁、空调、后备箱、车窗、电池预热、遮阳帘）：亮 = 功能（目标）是开着的。
 *   灭着点 → 当帧变亮（开启成功后继续亮着）；亮着点 → 当帧变暗并下发关闭指令。
 *   于是「亮灭」始终等于「车上的功能开没开」，不会出现「点了关闭还亮 2.5 秒」。
 *
 * 这套规则取代了 2026-11-05 那版「点下去先亮满 N 秒再复位」：复位计时与真实结果没
 * 任何关系，用户看到的是一次无缘无故的闪烁。
 */
enum class TileKind { MOMENTARY, TOGGLE }

/**
 * 点下去当帧该不该亮（纯函数，可单测）。
 *
 * 瞬时动作一律先亮 —— 这一下就是「正在发」的唯一反馈，不等任何网络；
 * 开关型直接亮成**目标状态**：开 → 立刻亮，关 → 立刻灭。
 */
internal fun clickedActive(kind: TileKind, turningOn: Boolean): Boolean = when (kind) {
    TileKind.MOMENTARY -> true
    TileKind.TOGGLE -> turningOn
}

/**
 * 不在点击当帧时该不该亮（纯函数，可单测）。
 *
 * - 瞬时动作：**不常亮** —— 车绝大多数时间都锁着，常亮没有信息量；鸣笛更是做完就结束；
 * - 开关型：跟着开合态走。
 */
internal fun tileActive(kind: TileKind, localOn: Boolean): Boolean =
    kind == TileKind.TOGGLE && localOn

/**
 * 开关型磁贴「现在算开着吗」：优先本地记住的开合态，本地没记过才用车况兜底。
 *
 * 为什么不能只看本地：`state_<id>` 默认 false，冷启动第一次点「解锁」时车可能已经解着，
 * 只按本地判就会再发一次 unlock，用户看到的是「点了没反应」。
 * 为什么不能只看车况：部分车型根本读不到这些信号（null），那样就永远只能开不能关 ——
 * 见 [QuickTiles] 里那段注释踩过的坑。
 *
 * @param localState 本地记下的开合态；null = 从没记过（不是「关着」）
 * @param status 车况里对应的信号；null = 这台车读不到
 */
internal fun effectiveOn(kind: TileKind, localState: Boolean?, status: Boolean?): Boolean =
    when (kind) {
        TileKind.MOMENTARY -> false
        TileKind.TOGGLE -> localState ?: status ?: false
    }

/** 车况里能对应到这块磁贴的信号；读不到（或这块磁贴本来就没有对应信号）返回 null。 */
internal fun statusOn(id: String, status: CarStatus?): Boolean? = when (id) {
    // 解锁磁贴的「开」= 车没锁，所以闭锁信号要取反
    "unlock" -> status?.locked?.let { !it }
    "ac" -> status?.acOn
    "trunk" -> status?.trunkOpen
    "window" -> status?.windowOpen
    "sunshade" -> status?.sunshadeOpen
    "batteryPreheat" -> status?.batteryPreheatOn
    else -> null
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
    /**
     * 开关型常态副标题：亮着 / 灭着各一句。
     *
     * 留空就用「已开启 · 点击关闭」「已关闭 · 点击开启」。
     * 只有语义不能直接套「开 / 关」的那几块才值得自定义（解锁、车窗这类）。
     */
    val onHint: String = "",
    val offHint: String = "",
) {
    /** 执行中的副标题：开关型分「开启 / 关闭」，一次性动作只有一句。 */
    fun doingText(turningOn: Boolean): String = when {
        kind == TileKind.MOMENTARY -> doing
        turningOn -> opening.ifBlank { doing }
        else -> closing.ifBlank { doing }
    }

    /** 这次点击真正下发的指令名（纯函数，便于单测）。动作型固定，开关型按意图取反。 */
    fun commandFor(turningOn: Boolean): String =
        if (kind == TileKind.MOMENTARY || turningOn) openCommand else closeCommand

    /** 成功提示里的完成态。 */
    fun doneText(turningOn: Boolean): String = when {
        kind == TileKind.MOMENTARY -> done
        turningOn -> done.ifBlank { title }
        else -> doneOff.ifBlank { done.ifBlank { title } }
    }
}

object QuickTiles {
    /**
     * 八块磁贴。
     *
     * 只有**上锁**与**鸣笛寻车**是瞬时动作（亮 = 正在发，受理后立刻灭）；
     * 其余六块都是开关型 —— 包括解锁与电池预热：它们都有对应的「关」指令，
     * 亮着点就是关，这样「亮灭」永远等于车上的功能开没开。
     */
    val ALL: List<TileSpec> = listOf(
        TileSpec(
            "lock", "上锁", R.drawable.ic_tile_lock, LockTileService::class.java,
            TileKind.MOMENTARY, "lock",
            doing = "车辆上锁中…", done = "车辆已上锁",
        ),
        TileSpec(
            "unlock", "解锁", R.drawable.ic_tile_unlock, UnlockTileService::class.java,
            TileKind.TOGGLE, "unlock", "lock",
            doing = "车辆解锁中…", opening = "车辆解锁中…", closing = "车辆上锁中…",
            done = "车辆已解锁", doneOff = "车辆已上锁",
            onHint = "未上锁 · 点击上锁", offHint = "已上锁 · 点击解锁",
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
            onHint = "已半开 · 点击全关", offHint = "已全关 · 点击半开",
        ),
        TileSpec(
            "batteryPreheat", "电池预热", R.drawable.ic_tile_battery_heat, BatteryHeatTileService::class.java,
            TileKind.TOGGLE, "batteryPreheat", "batteryPreheatOff",
            doing = "电池预热中…", opening = "电池预热开启中…", closing = "电池预热关闭中…",
            done = "电池预热已开启", doneOff = "电池预热已关闭",
            onHint = "预热中 · 点击关闭", offHint = "未预热 · 点击开启",
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
    // 磁贴点击的**方向只由这里 + 车况兜底决定**（见 `effectiveOn`）：本地记过就按本地取反，
    // 没记过才看车况；下发失败会把本地值回滚。
    //
    // 曾经只用 `refreshStatus()` 回来的车况当判据（status.acOn / trunkOpen / windowOpen / sunshadeOpen），
    // 但这些信号在部分车型上根本读不到（null），`if (车况开着) 关 else 开` 就永远判成「当前是关的」，
    // 于是每次点磁贴都只发开启指令 —— 用户看到的就是「点开了之后没办法取消」。
    // 空调卡片当初也是同一个坑（见 ComfortScreens 里那段注释）。
    //
    // 车况只用来**校准显示**（用户可能在车里手动开了后备箱，或者手动关了空调）：
    // 磁贴展开时读车况，非 null 就按车况修正，null 一律不动本地状态。

    fun isOn(context: Context, spec: TileSpec): Boolean =
        prefsOf(context).getBoolean("state_${spec.id}", false)

    /**
     * 本地到底记过没有。
     *
     * 用来区分「记的是 false」与「从来没记过」：后者不能当成「关着」，
     * 否则冷启动第一次点「解锁」会把已经解锁的车再解一次（见 `effectiveOn`）。
     */
    fun hasState(context: Context, spec: TileSpec): Boolean =
        prefsOf(context).contains("state_${spec.id}")

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

    /** 让某一块磁贴重新走一次 onStartListening（按最新状态重画）。 */
    fun refresh(context: Context, spec: TileSpec) {
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, spec.service))
        }
    }

    /** 让所有磁贴重新走一次 onStartListening，刷新文案与激活态。 */
    fun refreshAll(context: Context) {
        ALL.forEach { refresh(context, it) }
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
