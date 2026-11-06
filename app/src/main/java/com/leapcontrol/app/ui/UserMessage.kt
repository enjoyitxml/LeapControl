package com.leapcontrol.app.ui

/**
 * 把底层异常翻成**用户能看懂的一句话**。
 *
 * ## 为什么需要这一层
 * 2026-11-05 用户截图里，首页直接弹出一句：
 * `网关登录响应缺少 accessToken/signParam: {"code":302010202,"message":"第...`
 * —— 这句话是给写代码的人看的，用户看到只会以为 App 坏了。
 *
 * 底层异常里到处是 accessToken、signParam、JSON、HTTP 302、signalMap 这些词，
 * 与其逐条改（改完还会漏），不如在**上屏前统一过一遍**：
 * 认得的翻成人话，认不得的直接换成一句通用说明，技术细节留在异常和诊断页里。
 *
 * ## 判定顺序（不能乱）
 * 1. 登录失效 —— 这种不能拿「稍后重试」糊过去，用户得去重新登录；
 * 2. 网络不通 / 超时 —— 用户唯一能做的是检查网络或等一会儿；
 * 3. 车端给的原因 —— 「控车失败(3021): 车辆不在线」里那句「车辆不在线」有价值；
 * 4. 本来就是人话 —— 原样用；
 * 5. 剩下的 —— 通用说明，绝不把原文糊上去。
 */
object UserMessage {

    /** 兜底：宁可含糊，也不把技术原文丢给用户。 */
    const val FALLBACK = "操作没成功，请稍后重试"

    private const val MAX_LEN = 60

    fun of(e: Throwable?, fallback: String = FALLBACK): String =
        ofMessage(e?.message, fallback)

    fun ofMessage(raw: String?, fallback: String = FALLBACK): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return fallback
        val lower = text.lowercase()

        // 1) 登录态最优先：这类重试一百次也没用
        if (ControlFailure.isAuthFailure(lower)) return "登录已过期，请到「我的」重新登录"

        // 1.5) 云端不给权限（result=40 /「无此权限」）：必须在 serverReason 之前拦下，
        //      否则首页只会显示「无此权限」四个字，用户根本不知道该做什么
        if (ControlFailure.isPermissionDenied(lower)) {
            return ControlFailure.HINT_SENTRY_PERMISSION.message
        }

        // 2) 网络
        if (NETWORK_WORDS.any { lower.contains(it) }) return "连不上服务器，请检查手机网络后重试"
        if (lower.contains("timeout") || lower.contains("timed out") || text.contains("超时")) {
            return "请求超时了，稍后会自动重试"
        }

        // 3) 车端 / 网关明确给出的原因（去掉业务码和内部字段后剩下的那句）
        serverReason(text)?.let { return it }

        // 4) 本来就是一句人话
        if (isPlainChinese(text)) return text.lines().first().take(MAX_LEN)

        // 5) 认不出来：说一句能懂的，别把原文糊上去
        return fallback
    }

    /**
     * 这句话是不是「能直接给用户看」。
     *
     * 判据很保守：出现任何技术标记、花括号、纯英文，都不算人话。
     * 宁可多走一次兜底，也不要让一串 accessToken 出现在首页上。
     */
    fun isPlainChinese(text: String): Boolean {
        if (text.length > 80) return false
        if (text.any { it == '{' || it == '}' || it == '[' || it == ']' }) return false
        val lower = text.lowercase()
        if (TECH_WORDS.any { lower.contains(it) }) return false
        // 至少要有一个汉字：纯英文的一律不给人看
        return text.any { it.code in 0x4E00..0x9FFF }
    }

    /** 底层异常才会出现的词。命中任何一个，这句话就不该上屏。 */
    private val TECH_WORDS = listOf(
        "token", "signparam", "signkey", "signalmap", "accesstoken",
        "json", "http", "exception", "stacktrace", "traceback",
        "null", "undefined", "code=", "\"code\"", "errno",
        "ssl", "socket", "unable to", "failed", "unknownhost",
        "vin=", "oppwd=", "carvin=", "java.", "kotlin.",
        "gateway", "response",
    )

    private val NETWORK_WORDS = listOf(
        "unable to resolve host",
        "no address associated with hostname",
        "failed to connect",
        "connection reset",
        "unexpected end of stream",
        "network is unreachable",
        "connectexception",
        "sockettimeout",
        "unknownhostexception",
        "network is down",
        "broken pipe",
    )
}
