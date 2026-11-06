package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 哨兵模式的账号权限策略。
 *
 * 2026-11-06 用户反馈「控车失败(40): 无此权限」：零跑云端不允许授权 / 子账号
 * 远程开启哨兵，上游项目在点击前就拦下来了。这组用例钉住「只拦开启、不拦关闭」，
 * 免得以后把关闭也一起禁掉（那会让子账号失去关掉安防的能力）。
 */
class SentryPermissionTest {

    @Test
    fun `授权账号不能开启哨兵`() {
        assertFalse(SentryModeControlPolicy.canOperateSentry(isSharedAccount = true, targetOn = true))
        assertEquals(
            "当前子账号不支持开启哨兵操作",
            SentryModeControlPolicy.SUB_ACCOUNT_UNSUPPORTED_MESSAGE,
        )
    }

    @Test
    fun `授权账号仍可关闭哨兵`() {
        assertTrue(SentryModeControlPolicy.canOperateSentry(isSharedAccount = true, targetOn = false))
    }

    @Test
    fun `车主账号开关都允许`() {
        assertTrue(SentryModeControlPolicy.canOperateSentry(isSharedAccount = false, targetOn = true))
        assertTrue(SentryModeControlPolicy.canOperateSentry(isSharedAccount = false, targetOn = false))
    }

    @Test
    fun `指令名映射出开启或关闭的目标态`() {
        assertEquals(true, SentryModeControlPolicy.targetEnabled("sentryOn"))
        assertEquals(false, SentryModeControlPolicy.targetEnabled("sentryOff"))
        assertNull(SentryModeControlPolicy.targetEnabled("lock"))
        assertEquals("sentryOff", SentryModeControlPolicy.commandName(true))
        assertEquals("sentryOn", SentryModeControlPolicy.commandName(false))
        assertEquals("sentryOn", SentryModeControlPolicy.commandName(null))
    }

    @Test
    fun `车况求证必须信号等于目标态`() {
        assertTrue(SentryModeControlPolicy.isConfirmed(true, true))
        assertFalse(SentryModeControlPolicy.isConfirmed(true, false))
        assertFalse(SentryModeControlPolicy.isConfirmed(true, null))
        assertTrue(SentryModeControlPolicy.isConfirmed(false, false))
    }
}
