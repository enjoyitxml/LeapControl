package com.leapcontrol.app.ui

import android.content.Context

/**
 * 车窗档位：**关 → 微开 → 半开 → 全开 → 关**。
 *
 * 用户记得「以前有微开 / 全开 / 关闭三种」，但代码和真车复核的结论是**四档**：
 * cmd 230 的刻度是 0~10，能真正让车窗动起来的只有 `0 / 2 / 5 / 10`
 * （0=关，2=微开≈20%，5=半开≈50%，10=全开；`100` / `1` 云端回「请求成功」但车不动）。
 * 半开也是真车验过的档位，所以不做成三档、把它省掉 —— 四档循环才和车端能力对齐。
 *
 * ## 为什么档位必须本地记
 * 车端只回报「有没有窗开着」一位（`1693~1696`：0=关，非 0=开），**量不出开度** ——
 * 微开 / 半开 / 全开在车况里长得一模一样。所以档位只能由 App 自己记：
 * 下发哪一档就记哪一档，车况报「全关」时归零。
 */
enum class WindowStage {
    CLOSED,
    VENT,
    HALF,
    FULL,

    /**
     * 车况说「有窗开着」但本地没有记录（车里手动开的、或换了手机）。
     *
     * 不知道开多大，就不能假装是某一档；下一次点击给「关」——
     * 用户看到窗开着，直觉就是点一下关掉，而不是再开一档。
     */
    OPEN_UNKNOWN,
}

object WindowControl {

    const val CMD_VENT = "windowVent"
    const val CMD_FULL = "windowFull"
    const val CMD_CLOSE = "windowClose"

    /** 半开（230 / value 5）：四档里的第三档，老版本一直用的「开车窗」就是它。 */
    const val CMD_HALF = "windowOpen"

    private const val PREFS = "window_stage"

    /**
     * 刚下发完的宽限期。
     *
     * 车端 T-Box 唤醒 + 执行要 5~15 秒，这段时间拉回来的还是上一条报文：
     * 若拿它校准，刚点完「全开」会被旧报文里的「车窗关着」打回「已关闭」，
     * 下一个点击又变成微开。8 秒与磁贴执行层的 `ACTION_GRACE_MS` 一致。
     */
    private const val GRACE_MS = 8_000L

    /**
     * 当前档位。
     *
     * @param saved 本地记下的档位（null = 没记过）
     * @param savedAt 记下的时刻
     * @param statusOpen 车况里「是否有窗开着」；null = 读不到
     */
    fun resolve(
        saved: WindowStage?,
        savedAt: Long,
        statusOpen: Boolean?,
        now: Long = System.currentTimeMillis(),
    ): WindowStage {
        // 刚下发完：以本地为准，别让旧报文把刚做的事打回去
        if (saved != null && now - savedAt in 0..GRACE_MS) return saved
        return when {
            // 车况说全关，本地记录就作废（在车里手动关了窗也走这条）
            statusOpen == false -> WindowStage.CLOSED
            statusOpen == null -> saved ?: WindowStage.CLOSED
            // 车况说开着：本地记着「开着」就沿用；本地记的是关（或没记）说明是别人开的，
            // 不知道开度，交给 OPEN_UNKNOWN
            else -> when (saved) {
                WindowStage.VENT, WindowStage.HALF, WindowStage.FULL -> saved
                else -> WindowStage.OPEN_UNKNOWN
            }
        }
    }

    /** 这个档位下点一下，下发哪条指令。 */
    fun nextCommand(stage: WindowStage): String = when (stage) {
        WindowStage.CLOSED -> CMD_VENT
        WindowStage.VENT -> CMD_HALF
        WindowStage.HALF -> CMD_FULL
        WindowStage.FULL, WindowStage.OPEN_UNKNOWN -> CMD_CLOSE
    }

    /** 这条指令执行成功后，档位变成什么。 */
    fun nextStage(stage: WindowStage): WindowStage = when (stage) {
        WindowStage.CLOSED -> WindowStage.VENT
        WindowStage.VENT -> WindowStage.HALF
        WindowStage.HALF -> WindowStage.FULL
        WindowStage.FULL, WindowStage.OPEN_UNKNOWN -> WindowStage.CLOSED
    }

    /** 指令名 → 执行后的档位；不认识的指令返回 null。 */
    fun stageOf(command: String): WindowStage? = when (command) {
        CMD_VENT -> WindowStage.VENT
        CMD_HALF -> WindowStage.HALF
        CMD_FULL -> WindowStage.FULL
        CMD_CLOSE -> WindowStage.CLOSED
        else -> null
    }

    fun isOpen(stage: WindowStage): Boolean = stage != WindowStage.CLOSED

    /** 磁贴副标题：当前档位 + 点一下会做什么。 */
    fun hint(stage: WindowStage): String = when (stage) {
        WindowStage.CLOSED -> "已关闭 · 点击微开"
        WindowStage.VENT -> "已微开 · 点击半开"
        WindowStage.HALF -> "已半开 · 点击全开"
        WindowStage.FULL -> "已全开 · 点击关闭"
        WindowStage.OPEN_UNKNOWN -> "已开启 · 点击关闭"
    }

    /** 执行中的副标题：说清正在开到哪一档。 */
    fun runningText(command: String): String = when (command) {
        CMD_VENT -> "车窗微开中…"
        CMD_HALF -> "车窗半开中…"
        CMD_FULL -> "车窗全开中…"
        CMD_CLOSE -> "车窗关闭中…"
        else -> "车窗调节中…"
    }

    /** 完成态短句（磁贴 / 提示条共用）。 */
    fun doneLabel(stage: WindowStage): String = when (stage) {
        WindowStage.CLOSED -> "车窗已关闭"
        WindowStage.VENT -> "车窗已微开"
        WindowStage.HALF -> "车窗已半开"
        WindowStage.FULL -> "车窗已全开"
        WindowStage.OPEN_UNKNOWN -> "车窗已开启"
    }

    /**
     * 首页快捷按钮上的文字。
     *
     * 关着就说「车窗」，开着就把档位带出来 —— 用户不用点开任何东西，
     * 一眼能看到现在是微开还是全开；点完文字跟着变，也等于给了一次结果反馈。
     */
    fun buttonLabel(stage: WindowStage): String = when (stage) {
        WindowStage.CLOSED -> "车窗"
        WindowStage.VENT -> "车窗·微开"
        WindowStage.HALF -> "车窗·半开"
        WindowStage.FULL -> "车窗·全开"
        WindowStage.OPEN_UNKNOWN -> "车窗·开启"
    }

    // ---------------- 存储（按 VIN 隔离）----------------

    private fun prefsOf(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyOf(vin: String) = vin.ifBlank { "default" }

    fun load(context: Context, vin: String): WindowStage? {
        val raw = prefsOf(context).getString(keyOf(vin), null) ?: return null
        return WindowStage.entries.firstOrNull { it.name == raw }
    }

    fun savedAt(context: Context, vin: String): Long =
        prefsOf(context).getLong("${keyOf(vin)}_at", 0L)

    fun save(context: Context, vin: String, stage: WindowStage, now: Long = System.currentTimeMillis()) {
        prefsOf(context).edit()
            .putString(keyOf(vin), stage.name)
            .putLong("${keyOf(vin)}_at", now)
            .apply()
    }

    /**
     * 发完一条指令就记档位。
     *
     * 只认得出档位的指令（微开 / 半开 / 全开 / 关）才写，其它一律不动 ——
     * 宁可下次多点一下，也不能凭空调档。
     */
    fun remember(context: Context, vin: String, command: String, now: Long = System.currentTimeMillis()) {
        val stage = stageOf(command) ?: return
        save(context, vin, stage, now)
    }
}
