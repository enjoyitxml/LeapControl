package com.leapcontrol.app

import com.leapcontrol.app.ui.AvatarChoice
import com.leapcontrol.app.ui.AvatarStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 头像选择与自定义照片解码的纯逻辑测试。
 *
 * 重点不在「功能对不对」，而在「坏数据不炸」：偏好文件可能被旧版本写坏、
 * 被用户手改成乱码、或者将来头像数量精简导致下标越界，
 * 这些情况都必须安静地回落到默认头像，不能在「我的」页抛异常。
 */
class AvatarChoiceTest {

    @Test
    fun `没有存过时用第一个内置头像`() {
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode(null, count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("", count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("   ", count = 20))
    }

    @Test
    fun `正常的内置下标能还原`() {
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("preset:0", count = 20))
        assertEquals(AvatarChoice.Preset(7), AvatarChoice.decode("preset:7", count = 20))
        assertEquals(AvatarChoice.Preset(19), AvatarChoice.decode("preset:19", count = 20))
    }

    @Test
    fun `custom 解析为自定义`() {
        assertEquals(AvatarChoice.Custom, AvatarChoice.decode("custom", count = 20))
        // 前后空白也认
        assertEquals(AvatarChoice.Custom, AvatarChoice.decode("  custom  ", count = 20))
    }

    @Test
    fun `越界下标回落到默认而不是崩在取图的时候`() {
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("preset:99", count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("preset:-1", count = 20))
        // 将来头像精简到 8 个，旧的 preset:15 也得能活下来
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("preset:15", count = 8))
    }

    @Test
    fun `乱码回落到默认`() {
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("preset:abc", count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("preset:", count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("PRESET:3", count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("3", count = 20))
        assertEquals(AvatarChoice.Preset(0), AvatarChoice.decode("null", count = 20))
    }

    @Test
    fun `编码与解码可往返`() {
        assertEquals("preset:12", AvatarChoice.encode(AvatarChoice.Preset(12)))
        assertEquals("custom", AvatarChoice.encode(AvatarChoice.Custom))
        assertEquals(AvatarChoice.Preset(12), AvatarChoice.decode("preset:12", count = 20))
        assertEquals(AvatarChoice.Custom, AvatarChoice.decode("custom", count = 20))
    }

    @Test
    fun `默认值与内置数量自洽`() {
        assertEquals("preset:0", AvatarChoice.DEFAULT_RAW)
        assertTrue(AvatarStore.MAX_SIDE > 0)
    }

    // ---------------- 相册大图的解码采样 ----------------

    @Test
    fun `大图按二次幂采样到目标边长附近`() {
        // 1024 缩到 512：采样 2 正好 512
        assertEquals(2, AvatarStore.sampleSizeFor(1024, 1024, 512))
        // 4000 长边：4 次采样后 1000，再除一次只剩 500 < 512，所以停在 4
        assertEquals(4, AvatarStore.sampleSizeFor(4000, 3000, 512))
        // 8000 的相机原图：8 次采样后 1000
        assertEquals(8, AvatarStore.sampleSizeFor(8000, 6000, 512))
    }

    @Test
    fun `小于目标的图不采样`() {
        assertEquals(1, AvatarStore.sampleSizeFor(800, 600, 512))
        assertEquals(1, AvatarStore.sampleSizeFor(512, 512, 512))
        assertEquals(1, AvatarStore.sampleSizeFor(64, 64, 512))
    }

    @Test
    fun `非法尺寸返回 1 而不是死循环`() {
        assertEquals(1, AvatarStore.sampleSizeFor(0, 0, 512))
        assertEquals(1, AvatarStore.sampleSizeFor(-10, 800, 512))
        assertEquals(1, AvatarStore.sampleSizeFor(1024, 1024, 0))
    }
}
