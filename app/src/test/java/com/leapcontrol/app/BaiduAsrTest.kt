package com.leapcontrol.app

import com.leapcontrol.app.journal.baiduAsrErrorText
import com.leapcontrol.app.journal.parseBaiduReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 百度短语音的返回解析。
 *
 * 只测纯函数（不碰 Base64 / 网络）：真机上出错时，第一件事就是确认
 * 「返回体到底被读成了什么」，这部分必须能单独跑。
 */
class BaiduAsrTest {

    @Test
    fun `成功返回取第一条结果并去掉换行`() {
        val raw = """{"err_no":0,"err_msg":"success.","result":["今天充电花了50元"]}"""
        val reply = parseBaiduReply(raw)
        assertEquals("今天充电花了50元", reply.text)
        assertNull(reply.error)
    }

    @Test
    fun `结果数组带换行会清掉`() {
        val raw = """{"err_no":0,"result":["今天去了杭州\n"]}"""
        assertEquals("今天去了杭州", parseBaiduReply(raw).text)
    }

    @Test
    fun `结果为空当作没听清`() {
        val reply = parseBaiduReply("""{"err_no":0,"result":[""]}""")
        assertNull(reply.text)
        assertEquals("没听清，再说一次吧", reply.error)
    }

    @Test
    fun `缺 result 字段不崩`() {
        val reply = parseBaiduReply("""{"err_no":0}""")
        assertNull(reply.text)
        assertEquals("没听清，再说一次吧", reply.error)
    }

    @Test
    fun `token 失效标记为可重取`() {
        val reply = parseBaiduReply("""{"err_no":3302,"err_msg":"invalid token"}""")
        assertTrue(reply.authFailed)
        assertEquals("识别凭据失效了，去设置里检查百度 Key", reply.error)
    }

    @Test
    fun `静音与超长有各自的提示`() {
        assertEquals("没听到声音，再按一次说话", parseBaiduReply("""{"err_no":3312}""").error)
        assertEquals("音频超过 60 秒了，分两次说吧", parseBaiduReply("""{"err_no":3307}""").error)
    }

    @Test
    fun `非 JSON 返回给出可读提示`() {
        val reply = parseBaiduReply("<html>502 Bad Gateway</html>")
        assertNull(reply.text)
        assertEquals("识别服务返回了看不懂的内容", reply.error)
    }

    @Test
    fun `未知错误码带上数字便于排查`() {
        assertEquals("识别失败（错误码 9999）", baiduAsrErrorText(9999))
    }
}
