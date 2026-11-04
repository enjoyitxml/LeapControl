package com.leapcontrol.app

import android.content.Context

/**
 * 快捷控车里**可能**被要求「再点一次」的动作。
 *
 * [command] 就是控车指令名（`Commands.build` 的入参）。桌面小组件与控制中心磁贴拿到的
 * 正是这个名字，所以两边共用这一份开关，不用各自再维护一套动作表。
 *
 * 默认**全部关闭**（用户 2026-11-04 定的口径）：小组件与控制中心存在的意义就是一步到位，
 * 想防误触的人自己去「设置 → 快捷控制再点一次」里勾。
 * 注意这与 App 内的「控车二次确认」（弹窗，见 `AppRuntime.biometricConfirmEnabled`）是两回事：
 * 那个管首页下发前弹窗，这个管桌面 / 控制中心里的「再点一次」。
 */
enum class ConfirmAction(
    val command: String,
    val title: String,
    val group: String,
) {
    UNLOCK("unlock", "解锁", "车门"),
    LOCK("lock", "上锁", "车门"),
    TRUNK_OPEN("trunkOpen", "打开后备箱", "后备箱"),
    TRUNK_CLOSE("trunkClose", "关闭后备箱", "后备箱"),
    WINDOW_OPEN("windowOpen", "打开车窗", "车窗"),
    WINDOW_CLOSE("windowClose", "关闭车窗", "车窗"),
    AC_ON("acOn", "开启空调", "空调"),
    AC_OFF("acOff", "关闭空调", "空调"),
    SUNSHADE_OPEN("sunshadeOpen", "打开遮阳帘", "遮阳帘"),
    SUNSHADE_CLOSE("sunshadeClose", "关闭遮阳帘", "遮阳帘"),
    HORN("horn", "鸣笛寻车", "其他"),
    BATTERY_PREHEAT("batteryPreheat", "电池预热", "其他"),
    ;

    companion object {
        /** 指令名 → 动作；不认识的指令返回 null（调用方据此不做确认）。 */
        fun of(command: String?): ConfirmAction? = entries.firstOrNull { it.command == command }

        /**
         * 按「车门 → 后备箱 → 车窗 → 空调 → 遮阳帘 → 其他」分组。
         *
         * `groupBy` 用的是 LinkedHashMap，顺序就是枚举声明顺序，
         * 所以设置页照着列表渲染即可，不需要另写一份分组表（两处迟早会不同步）。
         */
        fun groups(): List<Pair<String, List<ConfirmAction>>> =
            entries.groupBy { it.group }.map { (group, actions) -> group to actions }
    }
}

/**
 * 哪些动作需要「再点一次」。存 prefs，key 用指令名，默认 false。
 */
object ConfirmActions {
    private const val PREFS = "confirm_actions"
    private const val KEY_PREFIX = "need_"

    fun isEnabled(context: Context, command: String?): Boolean {
        if (command.isNullOrBlank()) return false
        return prefs(context).getBoolean(KEY_PREFIX + command, false)
    }

    fun setEnabled(context: Context, command: String, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREFIX + command, on).apply()
    }

    /** 当前开启的指令名集合（设置页用它渲染勾选态、也用于一键全开 / 全关）。 */
    fun enabled(context: Context): Set<String> =
        ConfirmAction.entries.map { it.command }.filter { isEnabled(context, it) }.toSet()

    fun enabledCount(context: Context): Int = ConfirmAction.entries.count { isEnabled(context, it.command) }

    fun setAll(context: Context, on: Boolean) {
        val editor = prefs(context).edit()
        ConfirmAction.entries.forEach { editor.putBoolean(KEY_PREFIX + it.command, on) }
        editor.apply()
    }

    /** 设置页那一行的摘要：没开就说默认行为，开了就把前几项点出来。 */
    fun summary(context: Context): String {
        val on = ConfirmAction.entries.filter { isEnabled(context, it.command) }
        if (on.isEmpty()) return "默认点一次就下发，点此勾选要二次确认的指令"
        val names = on.take(3).joinToString("、") { it.title }
        val suffix = if (on.size > 3) "等 ${on.size} 项" else ""
        return "已开启：$names$suffix"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * 「再点一次」的判定：**无状态**，方便单测。
 *
 * 规则只有一条：上一次点的指令与本条相同、且间隔落在 [WINDOW_MS] 内，就算确认。
 * 只有 2~3 秒的窗口 —— 用户得盯着屏幕才有效；过了窗口再点，当成一次新的点击重新问。
 */
object DoubleConfirmRules {
    /**
     * 确认窗口。
     *
     * 3 秒：够用户看清提示再点一下，又不至于让磁贴长时间停在「再点一次确认」上；
     * 组件的复位协程按这个值 +300ms 排，两边共用一个常量，改一处就同步。
     */
    const val WINDOW_MS = 3_000L

    /**
     * @param pendingCommand 上一次点击记下的指令；null 表示没有待确认的动作
     * @param pendingAt 上一次点击的时刻
     * @return true = 这次点击落在窗口内且是同一个动作，可以下发
     */
    fun isConfirm(pendingCommand: String?, pendingAt: Long, command: String, now: Long): Boolean {
        if (pendingCommand == null || pendingCommand != command) return false
        val elapsed = now - pendingAt
        // 时钟回拨（elapsed < 0）当成没点过 —— 宁可多问一次，也不要凭空放行一条指令
        return elapsed in 0..WINDOW_MS
    }
}

/**
 * 「待确认」这个状态存在哪儿。
 *
 * 不存内存：小组件的广播、磁贴的点击、App 进程被回收后重开，都可能是不同的实例，
 * 只有 prefs 能跨这些边界记住「刚刚点过一下」。
 *
 * 每个入口一条独立记录（[scope]）：
 * - 桌面小组件统一用 `widget`；
 * - 控制中心每块磁贴用 `tile_<id>` —— 点了解锁再点后备箱，不该被当成「确认开后备箱」。
 */
object ConfirmGate {
    private const val PREFS = "control_confirm_state"

    fun step(
        context: Context,
        scope: String,
        command: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val prefs = prefs(context)
        val last = prefs.getString(keyAt(scope), null)
        val lastAt = prefs.getLong(timeKey(scope), 0L)
        val confirmed = DoubleConfirmRules.isConfirm(last, lastAt, command, now)
        // 确认过一次就清掉：连点三下只应该下发一次，不能变成两次
        prefs.edit()
            .putString(keyAt(scope), if (confirmed) null else command)
            .putLong(timeKey(scope), if (confirmed) 0L else now)
            .apply()
        return confirmed
    }

    /** 窗口内还在等确认的指令；过期或没有都返回 null。 */
    fun pending(
        context: Context,
        scope: String,
        now: Long = System.currentTimeMillis(),
    ): String? {
        val prefs = prefs(context)
        val last = prefs.getString(keyAt(scope), null) ?: return null
        val lastAt = prefs.getLong(timeKey(scope), 0L)
        val elapsed = now - lastAt
        return if (elapsed in 0..DoubleConfirmRules.WINDOW_MS) last else null
    }

    fun clear(context: Context, scope: String) {
        prefs(context).edit().remove(keyAt(scope)).remove(timeKey(scope)).apply()
    }

    private fun keyAt(scope: String) = "${scope}_command"

    private fun timeKey(scope: String) = "${scope}_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
