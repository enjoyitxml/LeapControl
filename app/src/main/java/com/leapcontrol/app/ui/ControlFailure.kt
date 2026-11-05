package com.leapcontrol.app.ui

import android.content.Context

/**
 * 控车失败之后，用户还该做什么。
 *
 * 加这层的原因是 2026-11-05 的用户反馈：**账号被退出登录**之后，桌面小组件和
 * 控制中心磁贴点了全都没反应。失败提示只写了「下发失败：未登录」或者「执行失败：信息校验失败」——
 * 用户看到的是「App 坏了」，而真正该做的是「去重新登录」。
 *
 * 所以这里的每一条文案都必须回答两件事：**为什么没成** + **你下一步做什么**。
 */
enum class ControlFix {
    /** 车端 / 网络的问题，用户只能重试，不需要去改什么。 */
    NONE,

    /** 还没选车，需要打开 App 选一辆。 */
    OPEN_APP,

    /** 登录态没了，需要重新登录。 */
    RELOGIN,

    /** 还没设 4 位控车密码，指令层根本不会下发。 */
    OPEN_PIN,
}

/**
 * @param message 完整一句，toast 用；必须带上「下一步做什么」。
 * @param short 卡片右上角 / 磁贴副标题这类只有几格宽的地方用的短句（≤5 字）。
 */
data class ControlHint(
    val message: String,
    val short: String,
    val action: ControlFix = ControlFix.NONE,
)

/**
 * 把控车异常翻译成「说人话 + 指路」的提示。
 *
 * 纯字符串判定，不碰网络也不碰 UI，方便单测（见 `ControlFailureTest`）。
 */
object ControlFailure {

    // ---------------- 文案 ----------------

    private val HINT_RELOGIN = ControlHint(
        message = "登录已失效，正在打开零控，请重新登录",
        short = "登录已失效",
        action = ControlFix.RELOGIN,
    )
    private val HINT_PIN = ControlHint(
        message = "还没设置控车密码，正在打开零控",
        short = "未设密码",
        action = ControlFix.OPEN_PIN,
    )
    private val HINT_NO_VEHICLE = ControlHint(
        message = "还没选择车辆，正在打开零控",
        short = "未选车辆",
        action = ControlFix.OPEN_APP,
    )
    internal val HINT_OFFLINE = ControlHint(
        // 车机休眠是「不在线」最常见的原因，说清楚比让用户干等有用
        message = "车辆不在线，请稍后再试；多次失败请上车唤醒车机",
        short = "车辆离线",
    )
    internal val HINT_NO_RESPONSE = ControlHint(
        message = "车辆没有响应，稍后重试；多次失败请检查车机信号",
        short = "未响应",
    )
    private val HINT_BUSY = ControlHint(
        message = "操作太频繁，等几秒再试",
        short = "太频繁",
    )
    internal val HINT_NETWORK = ControlHint(
        message = "网络不太好，请检查手机网络后重试",
        short = "网络异常",
    )
    internal val HINT_FALLBACK = ControlHint(
        message = "指令没发出去，请稍后重试",
        short = "执行失败",
    )

    // ---------------- 判定 ----------------

    fun of(e: Throwable?): ControlHint {
        if (e is NeedPinException) return HINT_PIN
        return ofMessage(e?.message)
    }

    fun ofMessage(raw: String?): ControlHint {
        val text = raw?.trim().orEmpty()
        // 没消息：桌面小组件那条链路是 `withTimeoutOrNull` 掐掉的，就是「车端没回话」。
        // 别写成「指令没发出去」—— 指令可能早就到了，只是没等到回执。
        if (text.isEmpty()) return HINT_NO_RESPONSE
        val lower = text.lowercase()

        // 登录态排在最前：它的文案里也带「密码」「token」，别被后面的规则抢走
        if (isAuthFailure(lower)) return HINT_RELOGIN
        if (text.contains("控车密码") || text.contains("4 位")) return HINT_PIN
        if (text.contains("选择车辆") || text.contains("车辆 VIN")) return HINT_NO_VEHICLE
        if (lower.contains("不在线") || lower.contains("离线") || lower.contains("offline")) {
            return HINT_OFFLINE
        }
        if (lower.contains("繁忙") || lower.contains("频繁") || lower.contains("busy")) {
            return HINT_BUSY
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时")) {
            return HINT_NO_RESPONSE
        }
        if (isNetworkFailure(lower)) return HINT_NETWORK

        // 车端给的原因（「车辆不在线」除外，上面已单独处理）直接告诉用户，
        // 但只说原因不指路仍然不够 —— 这类通常等一会儿自己就好了，所以补一句重试。
        serverReason(text)?.let { return ControlHint("未完成：$it，稍后重试", "未完成") }
        return HINT_FALLBACK
    }

    /**
     * 是不是「登录态失效」。
     *
     * 覆盖三类真实报文：
     * - 本地没会话：`未登录`、`未完成网关登录`；
     * - 旧 token 挂掉：`控车失败(39): 信息校验失败`、`第三方TOKEN失效`；
     * - 网关侧：HTTP 401 / 403。
     */
    internal fun isAuthFailure(lowered: String): Boolean {
        if (AUTH_WORDS.any { lowered.contains(it) }) return true
        // 单独的 401/403：别把 `控车失败(4012)` 这种业务码当鉴权错误
        if (AUTH_CODE.containsMatchIn(lowered)) return true
        // token 与「失效/过期」同时出现才算，避免 「token 已刷新」这种被误判
        return lowered.contains("token") && TOKEN_BAD.any { lowered.contains(it) }
    }

    private fun isNetworkFailure(lowered: String): Boolean =
        NETWORK_WORDS.any { lowered.contains(it) }

    private val AUTH_WORDS = listOf(
        "未登录",
        "未完成网关登录",
        "信息校验失败",
        "第三方token失效",
        "鉴权失败",
        "认证失败",
        "登录已失效",
        "登录失效",
        "登录过期",
        "登录超时",
        "重新登录",
        "登录状态",
        "unauthorized",
        "invalid_grant",
    )

    private val AUTH_CODE = Regex("""(?<![0-9])40[13](?![0-9])""")

    private val TOKEN_BAD = listOf("失效", "过期", "无效", "expired", "invalid")

    private val NETWORK_WORDS = listOf(
        "unable to resolve host",
        "no address associated with hostname",
        "failed to connect",
        "connection reset",
        "unexpected end of stream",
        "network is unreachable",
        "ssl",
        "连接被中断",
        "网络",
    )

    // ---------------- 「登录已失效」的本地标记 ----------------

    /**
     * 记住「这次失败是因为登录没了」。
     *
     * 桌面卡片靠它把右上角从「12:30 更新」改成「点此登录」、把五颗按键改成「点一下打开 App」——
     * 否则用户要反复点、反复等 5 秒超时，才知道是登录出了问题。
     */
    fun markSessionExpired(context: Context) {
        prefs(context).edit().putBoolean(KEY_EXPIRED, true).apply()
    }

    /** 重新登录成功后清掉（见 [AppRuntime.login] 与登出）。 */
    fun clearSessionExpired(context: Context) {
        prefs(context).edit().remove(KEY_EXPIRED).apply()
    }

    fun isSessionExpired(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EXPIRED, false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "control_health"
    private const val KEY_EXPIRED = "session_expired"
}
