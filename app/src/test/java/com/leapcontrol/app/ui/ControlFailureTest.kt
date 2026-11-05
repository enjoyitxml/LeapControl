package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 控车失败时的「提示语 + 下一步」。
 *
 * 背景（2026-11-05 用户反馈）：账号被退出登录后，桌面小组件和快捷中心全部点了没反应，
 * 提示只写「下发失败：未登录」「执行失败：信息校验失败」。用户看不懂，
 * 以为 App 坏了 —— 这里守住「每条提示都能告诉用户下一步做什么」。
 */
class ControlFailureTest {

    @Test
    fun `没有错误消息时算车端未响应`() {
        val hint = ControlFailure.ofMessage(null)
        assertEquals(ControlFix.NONE, hint.action)
        assertTrue(hint.message.contains("没有响应"))
    }

    @Test
    fun `旧 token 校验失败要走重新登录`() {
        // 上游网关在账号被别处顶掉时给的就是这个（result=39）
        val hint = ControlFailure.ofMessage("控车失败(39): 信息校验失败")
        assertEquals(ControlFix.RELOGIN, hint.action)
        assertTrue(hint.message.contains("重新登录"))
    }

    @Test
    fun `未登录不会被误判成没设控车密码`() {
        // 这句话里带「密码」，但它说的是「缺少加密用的 Token」，本质是登录没了
        val hint = ControlFailure.ofMessage("未登录（缺少用于加密操作密码的 Token）")
        assertEquals(ControlFix.RELOGIN, hint.action)
    }

    @Test
    fun `没设控车密码时把人送到密码设置`() {
        val hint = ControlFailure.of(NeedPinException())
        assertEquals(ControlFix.OPEN_PIN, hint.action)
        assertTrue(hint.message.contains("控车密码"))
    }

    @Test
    fun `没选车时把人送到选车`() {
        val hint = ControlFailure.ofMessage("还没有选择车辆 VIN")
        assertEquals(ControlFix.OPEN_APP, hint.action)
    }

    @Test
    fun `车辆不在线只提示等一会儿`() {
        val hint = ControlFailure.ofMessage("控车失败(3021): 车辆不在线")
        assertEquals(ControlFix.NONE, hint.action)
        assertEquals("车辆离线", hint.short)
        assertTrue(hint.message.contains("稍后再试"))
    }

    @Test
    fun `业务码里的 4012 不算鉴权失败`() {
        val hint = ControlFailure.ofMessage("控车失败(4012): 车辆未唤醒")
        assertNotEquals(ControlFix.RELOGIN, hint.action)
        assertTrue(hint.message.startsWith("未完成"))
    }

    @Test
    fun `网络不通给网络提示`() {
        val hint = ControlFailure.ofMessage("Failed to connect to /10.0.0.2:443")
        assertEquals(ControlFix.NONE, hint.action)
        assertEquals("网络异常", hint.short)
    }

    @Test
    fun `超时按未响应处理`() {
        val hint = ControlFailure.ofMessage("java.net.SocketTimeoutException: timeout")
        assertEquals("未响应", hint.short)
    }

    @Test
    fun `短句要能塞进磁贴副标题`() {
        // 磁贴副标题 / 卡片右上角只有几格宽，超过 5 个字就被省略号切掉
        val samples = listOf(
            null,
            "未登录",
            "控车失败(39): 信息校验失败",
            "还没有选择车辆 VIN",
            "控车失败(3021): 车辆不在线",
            "控车失败(500): 系统繁忙，请稍后再试",
            "Failed to connect to /10.0.0.2:443",
            "控车失败(9): 未知错误",
        )
        samples.forEach { raw ->
            val hint = ControlFailure.ofMessage(raw)
            assertTrue("short 为空：$raw", hint.short.isNotBlank())
            assertTrue("short 太长（${hint.short}）：$raw", hint.short.length <= 5)
        }
    }

    @Test
    fun `每条提示都有一句能读懂的下一步`() {
        val samples = listOf(
            null,
            "未登录",
            "还没有选择车辆 VIN",
            "控车失败(3021): 车辆不在线",
            "Failed to connect to /10.0.0.2:443",
            "控车失败(9): 未知错误",
        )
        samples.forEach { raw ->
            val hint = ControlFailure.ofMessage(raw)
            // 提示不能只是一句结论，必须带动作或明确的等待/重试指引
            val actionable = listOf("登录", "重试", "再试", "打开", "唤醒", "检查")
                .any { hint.message.contains(it) }
            assertTrue("没有下一步：${hint.message}", actionable)
        }
    }

    @Test
    fun `普通成功报文不会被误判`() {
        assertFalse(ControlFailure.isAuthFailure("解锁指令已下发"))
        assertFalse(ControlFailure.isAuthFailure("已上锁"))
    }
}
