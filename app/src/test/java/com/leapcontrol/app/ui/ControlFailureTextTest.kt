package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 控车失败文案：只把车端 / 网关给的原因讲给用户，别把内部错误码和报文糊出去。
 *
 * 真机上看到的原样是「控车失败(3021): 车辆不在线」——那串数字对用户毫无意义，
 * 反而像是 App 出错了（上游 4.5.12 的修法）。
 */
class ControlFailureTextTest {

    @Test
    fun `取冒号后面那句原因`() {
        assertEquals("车辆不在线", serverReason("控车失败(3021): 车辆不在线"))
        assertEquals("操作密码错误", serverReason("控车失败(1001)：操作密码错误"))
        assertEquals("车辆已上锁", serverReason("控车失败: 车辆已上锁"))
        assertEquals("系统繁忙，请稍后再试", serverReason("控车失败(500): 系统繁忙，请稍后再试"))
    }

    @Test
    fun `只有错误码时不给原因`() {
        assertNull(serverReason("控车失败(3021)"))
        assertNull(serverReason("控车失败(3021):"))
        assertNull(serverReason("控车失败"))
    }

    @Test
    fun `夹带内部参数或堆栈时不显示`() {
        assertNull(serverReason("控车失败(3021): java.net.SocketTimeoutException"))
        assertNull(serverReason("控车失败(3021): VIN=LFZ63AZ55SH023503 不在线"))
        assertNull(serverReason("控车失败(3021): oppwd=**** 校验失败"))
        assertNull(serverReason("控车失败(3021): token=abc 过期"))
    }

    @Test
    fun `不是控车失败就不碰`() {
        assertNull(serverReason("Unable to resolve host"))
        assertNull(serverReason("登录状态已过期，请重新登录"))
        assertNull(serverReason(""))
    }

    @Test
    fun `超长原因截断`() {
        val long = "控车失败(500): " + "很长的原因".repeat(40)
        assertEquals(60, serverReason(long)?.length)
    }
}
