package com.leapcontrol.app.ui

import com.leapcontrol.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App 内控车时，首页右上角车况区的那句「正在做什么 / 做成了什么」。
 *
 * ## 为什么 App 里也需要这一层
 * 车端 T-Box 唤醒 + 执行要 5~15 秒，`control()` 最长还要等 20 秒回执；
 * 这段时间里车况信号（闭锁 / 空调 / 后备箱）仍是**上一条报文**，
 * 于是用户点了「解锁」，右上角还写着「车辆已闭锁」，看着就像没点着。
 * 和桌面小组件的 [com.leapcontrol.app.widget.WidgetFlash] 是同一个问题、同一套办法：
 * 结果先上屏（「车辆已解锁」），真实车况跟上后自然交回去。
 *
 * ## 与小组件那条的区别
 * - 小组件存 prefs（进程随时可能被回收）；这里是进程内的 StateFlow，
 *   只服务前台界面，进程没了界面也没了，不需要落盘。
 * - 车况一旦**真的**变成意图状态，界面侧就会 [clear]（见 [reached]），
 *   所以那句提示不会盖住真实状态；只有车端迟迟不上报时才靠 TTL 兜底。
 *
 * ## 文案口径（与磁贴、小组件一致）
 * - 执行中：`车辆解锁中…`（说清车正在发生什么，不是「解锁已开启」这种混句）；
 * - 车端回执确认：`车辆已解锁` / `空调已开启`（完成式）；
 * - 只下发没等到回执：`解锁已下发`（不把「发出去了」说成「已经成了」）；
 * - 失败：`解锁失败`（具体原因由页面底部的提示条说）。
 */
object ControlFeedback {

    /** 这条提示处在哪个阶段，决定颜色。 */
    enum class Phase {
        /** 指令在途，车正在执行 */
        RUNNING,

        /** 车端回执确认已执行 */
        DONE,

        /** 指令已发出，但没等到车端确认 */
        PENDING,

        /** 没发出去 / 车端报错 */
        FAILED,
    }

    /**
     * @param text 右上角直接显示的短句
     * @param command 这次动作的指令名（`unlock` / `acOn` / `trunkClose`…），
     *   用来选图标、找它该顶替哪一行、以及判断车况是否已经跟上
     */
    data class Flash(
        val text: String,
        val phase: Phase,
        val command: String? = null,
        val at: Long = System.currentTimeMillis(),
    )

    /**
     * 执行中的兜底上限。
     *
     * 正常由结果覆盖，不会真等这么久；设 60 秒是防「协程被取消、结果永远不回来」
     * 时右上角一直挂着「车辆解锁中…」。`control()` 最长 20 秒，60 秒足够宽。
     */
    private const val RUNNING_TTL_MS = 60_000L

    /** 结果停留时间：车端报文一般 2~5 秒跟上，跟上就提前 [clear]，这只是兜底。 */
    private const val DONE_TTL_MS = 6_000L

    /** 没等到回执的那句更该让人注意，但也不该久留。 */
    private const val PENDING_TTL_MS = 5_000L

    private val _flash = MutableStateFlow<Flash?>(null)
    val flash: StateFlow<Flash?> = _flash.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 用来作废上一轮的过期任务：连点两次时，第一轮的「到期清空」不能把第二轮抹掉。 */
    private var seq = 0
    private var expiry: Job? = null

    /** 指令下发中。命令不在车况映射表里（座椅加热、空调参数组…）时静默返回，不改右上角。 */
    fun running(command: String) {
        val text = runningText(command) ?: return
        show(Flash(text, Phase.RUNNING, command), RUNNING_TTL_MS)
    }

    /**
     * 指令有了结果。
     *
     * [message] 是 `control()` 的返回文案，用来判断车端到底确认了没有 ——
     * 只有「执行成功」结尾或空调回读自证的那几条算确认，其余一律「已下发」。
     */
    fun done(command: String, message: String) {
        // 不在车况映射表里的指令（座椅加热、空调参数组…）不该在右上角冒一句泛泛的
        // 「指令已下发」—— 那里说的是车况，不是日志
        if (runningText(command) == null) return
        val confirmed = isConfirmed(command, message)
        show(
            Flash(doneText(command, confirmed), if (confirmed) Phase.DONE else Phase.PENDING, command),
            if (confirmed) DONE_TTL_MS else PENDING_TTL_MS,
        )
    }

    /** 没发出去 / 抛异常。具体原因由页面的提示条负责，这里只说哪件事没成。 */
    fun failed(command: String) {
        val name = actionName(command) ?: return
        show(Flash("${name}失败", Phase.FAILED, command), PENDING_TTL_MS)
    }

    /** 交回真实车况（车况已跟上、用户主动刷新、登录失效等）。 */
    fun clear() {
        expiry?.cancel()
        expiry = null
        _flash.value = null
    }

    private fun show(flash: Flash?, ttlMs: Long) {
        val id = ++seq
        _flash.value = flash
        expiry?.cancel()
        if (flash == null) return
        expiry = scope.launch {
            delay(ttlMs)
            if (seq == id) _flash.value = null
        }
    }

    /**
     * 车况是否已经达到这次控车的意图。
     *
     * @return true = 已经达到（提示可以撤了）、false = 能读到信号但还没到、
     *   null = 读不到这个信号或这次动作没有可回读的状态（鸣笛、充电枪）
     */
    fun reached(command: String?, status: CarStatus?): Boolean? {
        if (command == null || status == null) return null
        val wantOn = expectedOn(command) ?: return null
        val actual = when (topicOf(command)) {
            TOPIC_LOCK -> status.locked
            TOPIC_AC -> status.acOn
            TOPIC_TRUNK -> status.trunkOpen
            TOPIC_WINDOW -> status.windowOpen
            TOPIC_SUNSHADE -> status.sunshadeOpen
            TOPIC_SUNROOF -> status.sunroofOpen
            TOPIC_FRUNK -> status.frunkOpen
            TOPIC_SENTRY -> status.sentryOn
            else -> null
        } ?: return null
        return actual == wantOn
    }

    /** 这条提示该顶替右上角哪一行（见 [vehicleStatusRows] 的 topic）。 */
    fun topicOf(command: String?): String? = when (command) {
        "lock", "unlock" -> TOPIC_LOCK
        "acOn", "acOff" -> TOPIC_AC
        "trunkOpen", "trunkClose" -> TOPIC_TRUNK
        "windowOpen", "windowVent", "windowClose" -> TOPIC_WINDOW
        "sunshadeOpen", "sunshadeClose" -> TOPIC_SUNSHADE
        "sunroofOpen", "sunroofClose" -> TOPIC_SUNROOF
        "frunkOpen", "frunkClose" -> TOPIC_FRUNK
        "sentryOn", "sentryOff" -> TOPIC_SENTRY
        "horn" -> TOPIC_HORN
        else -> null
    }

    /** 右上角那一行的图标：沿用控车按键的同一套图标，用户不用二次辨认。 */
    fun iconOf(command: String?): Int = when (topicOf(command)) {
        TOPIC_LOCK -> if (command == "unlock") R.drawable.ic_qc_unlock else R.drawable.ic_qc_lock
        TOPIC_AC -> R.drawable.ic_qc_ac
        TOPIC_TRUNK -> R.drawable.ic_qc_trunk
        TOPIC_WINDOW -> R.drawable.ic_qc_window
        TOPIC_SUNSHADE -> R.drawable.ic_qc_sunshade
        TOPIC_SUNROOF -> R.drawable.ic_qc_sunroof
        TOPIC_FRUNK -> R.drawable.ic_qc_frunk
        TOPIC_SENTRY -> R.drawable.ic_qc_sentry
        TOPIC_HORN -> R.drawable.ic_qc_horn
        else -> R.drawable.ic_qc_lock
    }

    // ---------------- 文案（纯函数，单测钉住口径） ----------------

    /** 执行中：主语 + 动作 + 「中…」，不要「解锁已开启」这种既非动作也非状态的混句。 */
    fun runningText(command: String): String? = when (command) {
        "lock" -> "车辆上锁中…"
        "unlock" -> "车辆解锁中…"
        "acOn" -> "空调开启中…"
        "acOff" -> "空调关闭中…"
        "trunkOpen" -> "后备箱开启中…"
        "trunkClose" -> "后备箱关闭中…"
        "windowOpen", "windowVent" -> "车窗开启中…"
        "windowClose" -> "车窗关闭中…"
        "sunshadeOpen" -> "遮阳帘开启中…"
        "sunshadeClose" -> "遮阳帘关闭中…"
        "sunroofOpen" -> "天窗开启中…"
        "sunroofClose" -> "天窗关闭中…"
        "frunkOpen" -> "前备箱开启中…"
        "frunkClose" -> "前备箱关闭中…"
        "sentryOn" -> "哨兵开启中…"
        "sentryOff" -> "哨兵关闭中…"
        "batteryPreheat" -> "电池预热中…"
        "batteryPreheatOff" -> "关闭预热中…"
        "horn" -> "鸣笛寻车中…"
        "startCharging" -> "正在开始充电…"
        "stopCharging" -> "正在停止充电…"
        "unlockCharger" -> "正在解锁充电枪…"
        else -> null
    }

    /**
     * 完成后那句话：**完成式**（车辆已解锁），未确认时只能说「已下发」。
     *
     * 把「发出去了」说成「已经成了」是骗人 —— 车主看到状态变了就不会再去车边确认，
     * 车窗被防夹弹回、后备箱被挡住这类情况反而会耽误事。
     */
    fun doneText(command: String, confirmed: Boolean): String = when (command) {
        "unlock" -> if (confirmed) "车辆已解锁" else "解锁已下发"
        "lock" -> if (confirmed) "车辆已上锁" else "上锁已下发"
        "acOn" -> if (confirmed) "空调已开启" else "开空调已下发"
        "acOff" -> if (confirmed) "空调已关闭" else "关空调已下发"
        "trunkOpen" -> if (confirmed) "后备箱已开启" else "开后备箱已下发"
        "trunkClose" -> if (confirmed) "后备箱已关闭" else "关后备箱已下发"
        "windowOpen" -> if (confirmed) "车窗已开启" else "开车窗已下发"
        "windowVent" -> if (confirmed) "车窗已通风" else "开窗通风已下发"
        "windowClose" -> if (confirmed) "车窗已关闭" else "关车窗已下发"
        "sunshadeOpen" -> if (confirmed) "遮阳帘已开启" else "开遮阳帘已下发"
        "sunshadeClose" -> if (confirmed) "遮阳帘已关闭" else "关遮阳帘已下发"
        "sunroofOpen" -> if (confirmed) "天窗已开启" else "开天窗已下发"
        "sunroofClose" -> if (confirmed) "天窗已关闭" else "关天窗已下发"
        "frunkOpen" -> if (confirmed) "前备箱已开启" else "开前备箱已下发"
        "frunkClose" -> if (confirmed) "前备箱已关闭" else "关前备箱已下发"
        "sentryOn" -> if (confirmed) "哨兵已开启" else "开哨兵已下发"
        "sentryOff" -> if (confirmed) "哨兵已关闭" else "关哨兵已下发"
        "batteryPreheat" -> if (confirmed) "电池预热已开启" else "预热已下发"
        "batteryPreheatOff" -> if (confirmed) "电池预热已关闭" else "关预热已下发"
        "horn" -> if (confirmed) "已鸣笛寻车" else "鸣笛已下发"
        "startCharging" -> if (confirmed) "已开始充电" else "开始充电已下发"
        "stopCharging" -> if (confirmed) "已停止充电" else "停止充电已下发"
        "unlockCharger" -> if (confirmed) "充电枪已解锁" else "解锁充电枪已下发"
        else -> if (confirmed) "执行成功" else "指令已下发"
    }

    /**
     * 车端到底确认了没有。
     *
     * `control()` 只有三种「确认」写法：网关结果码 0/200（`XX执行成功`）、
     * 空调超时后回读车况自证（`空调已开启` / `空调已关闭`）。
     * 其余都是「已下发」，不能算成功。
     */
    fun isConfirmed(command: String, message: String): Boolean =
        message.endsWith("执行成功") || message == doneText(command, true)

    private fun actionName(command: String): String? = when (topicOf(command)) {
        TOPIC_LOCK -> if (command == "unlock") "解锁" else "上锁"
        TOPIC_AC -> if (command == "acOn") "开空调" else "关空调"
        TOPIC_TRUNK -> if (command == "trunkOpen") "开后备箱" else "关后备箱"
        TOPIC_WINDOW -> if (command == "windowClose") "关车窗" else "开车窗"
        TOPIC_SUNSHADE -> if (command == "sunshadeOpen") "开遮阳帘" else "关遮阳帘"
        TOPIC_SUNROOF -> if (command == "sunroofOpen") "开天窗" else "关天窗"
        TOPIC_FRUNK -> if (command == "frunkOpen") "开前备箱" else "关前备箱"
        TOPIC_SENTRY -> if (command == "sentryOn") "开哨兵" else "关哨兵"
        TOPIC_HORN -> "鸣笛寻车"
        else -> null
    }

    /** 这次动作期望车况变成「开」还是「关」；null = 没有可回读的开合态。 */
    private fun expectedOn(command: String): Boolean? = when (topicOf(command)) {
        TOPIC_LOCK -> command == "lock"
        TOPIC_AC -> command == "acOn"
        TOPIC_TRUNK -> command == "trunkOpen"
        TOPIC_WINDOW -> command != "windowClose"
        TOPIC_SUNSHADE -> command == "sunshadeOpen"
        TOPIC_SUNROOF -> command == "sunroofOpen"
        TOPIC_FRUNK -> command == "frunkOpen"
        TOPIC_SENTRY -> command == "sentryOn"
        else -> null
    }

    const val TOPIC_LOCK = "lock"
    const val TOPIC_AC = "ac"
    const val TOPIC_TRUNK = "trunk"
    const val TOPIC_WINDOW = "window"
    const val TOPIC_SUNSHADE = "sunshade"
    const val TOPIC_SUNROOF = "sunroof"
    const val TOPIC_FRUNK = "frunk"
    const val TOPIC_SENTRY = "sentry"
    const val TOPIC_HORN = "horn"
}
