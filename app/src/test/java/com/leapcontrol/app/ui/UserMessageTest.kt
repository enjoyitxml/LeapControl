package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「技术原文不许上屏」这条线。
 *
 * 背景：2026-11-05 首页弹出过
 * `网关登录响应缺少 accessToken/signParam: {"code":302010202,...}` ——
 * 用户看不懂，只会以为 App 坏了。这里用真实出现过的几句话做回归。
 */
class UserMessageTest {

    /** 真机上出现过的原文，一个字都不能再回到界面上。 */
    @Test
    fun `网关登录原文不会上屏`() {
        val raw = "网关登录响应缺少 accessToken/signParam: " +
            """{"code":302010202,"message":"第三方TOKEN失效"}"""
        val text = UserMessage.ofMessage(raw)
        assertFalse("不该出现 accessToken", text.contains("accessToken"))
        assertFalse("不该出现花括号", text.contains("{"))
        assertTrue("应该说清是登录问题", text.contains("登录"))
    }

    @Test
    fun `网络异常翻成检查网络`() {
        val text = UserMessage.ofMessage("Unable to resolve host \"api.example.com\": No address associated with hostname")
        assertTrue(text.contains("网络"))
    }

    @Test
    fun `超时说成稍后重试`() {
        val text = UserMessage.ofMessage("connect timed out")
        assertTrue(text.contains("超时") || text.contains("稍后"))
    }

    @Test
    fun `车端给的原因原样保留`() {
        assertEquals("车辆不在线", UserMessage.ofMessage("控车失败(3021): 车辆不在线"))
    }

    @Test
    fun `哨兵无权限不能再只显示四个字`() {
        // 2026-11-06：首页原来直接把 serverReason 抠出的「无此权限」上屏
        val text = UserMessage.ofMessage("控车失败(40): 无此权限")
        assertFalse(text.contains("控车失败"))
        assertFalse(text.contains("40"))
        assertTrue(text.contains("车主账号"))
        assertTrue(text.contains("零跑 App") || text.contains("车机"))
    }

    @Test
    fun `前置拦截的提示原样给用户`() {
        assertEquals(
            ControlFailure.HINT_SENTRY_PERMISSION.message,
            UserMessage.ofMessage(ControlFailure.HINT_SENTRY_PERMISSION.message),
        )
    }

    @Test
    fun `车辆不在线仍然只保留原因`() {
        assertEquals("车辆不在线", UserMessage.ofMessage("控车失败(3021): 车辆不在线"))
    }

    @Test
    fun `本来就是人话就别改`() {
        val plain = "还没设置控车密码，请先到「我的」设置"
        assertEquals(plain, UserMessage.ofMessage(plain))
    }

    @Test
    fun `纯英文异常一律兜底`() {
        val text = UserMessage.ofMessage("java.net.SocketException: Socket closed")
        assertEquals(UserMessage.FALLBACK, text)
    }

    @Test
    fun `json 原文不上屏`() {
        val text = UserMessage.ofMessage("""{"code":500,"message":"internal error"}""")
        assertEquals(UserMessage.FALLBACK, text)
    }

    @Test
    fun `空消息用兜底`() {
        assertEquals(UserMessage.FALLBACK, UserMessage.ofMessage(null))
        assertEquals(UserMessage.FALLBACK, UserMessage.ofMessage("   "))
    }

    @Test
    fun `人话判定认得中文与英文技术词`() {
        assertTrue(UserMessage.isPlainChinese("车辆不在线，请稍后再试"))
        assertFalse(UserMessage.isPlainChinese("missing accessToken"))
        assertFalse(UserMessage.isPlainChinese("HTTP 500: {\"error\":1}"))
    }
}
