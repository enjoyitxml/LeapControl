package com.leapcontrol.app

import androidx.compose.ui.graphics.Color
import com.leapcontrol.app.ui.DIALOG_SCRIM
import com.leapcontrol.app.ui.appColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * 弹窗可读性约束。
 *
 * 起因：弹窗曾经沿用首页卡片的半透明底色（浅色 85% 白），压在车模 / 四季渐变 / 视频背景上时
 * 底下的深色画面透上来，白底发灰、正文和背景亮度接近，读起来非常吃力。
 * 这里把「弹窗必须不透明 + 文字对比度必须达标 + 遮罩必须够深」写成断言，
 * 防止以后又被改回半透明底色。
 */
class DialogSurfaceTest {

    private fun linear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

    /** WCAG 相对对比度，1:1 ~ 21:1。 */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun dialogSurfaceMustBeOpaque() {
        assertEquals(
            "浅色弹窗底一旦透光，底下的车模/渐变会透上来",
            1f,
            appColors(dark = false).dialogBg.alpha,
        )
        assertEquals(
            "深色弹窗底一旦透光，底下的车模/渐变会透上来",
            1f,
            appColors(dark = true).dialogBg.alpha,
        )
    }

    @Test
    fun dialogSurfaceMoreOpaqueThanCard() {
        // cardBg 是刻意半透明的（让四季渐变透出），弹窗不允许跟它混用
        val light = appColors(dark = false)
        assertTrue("首页卡片应当保持半透明", light.cardBg.alpha < 1f)
        assertTrue("弹窗底必须比首页卡片更实", light.dialogBg.alpha > light.cardBg.alpha)
    }

    @Test
    fun textContrastOnDialogSurface() {
        listOf(false, true).forEach { dark ->
            val c = appColors(dark)
            val title = contrast(c.textPrimary, c.dialogBg)
            val body = contrast(c.textSecondary, c.dialogBg)
            assertTrue("dark=$dark 标题对比度 $title 低于 4.5:1", title >= 4.5)
            assertTrue("dark=$dark 正文对比度 $body 低于 3:1", body >= 3.0)
        }
    }

    @Test
    fun scrimWithinRange() {
        assertTrue("遮罩 $DIALOG_SCRIM 太薄，背景会和弹窗抢焦点", DIALOG_SCRIM >= 0.50f)
        assertTrue("遮罩 $DIALOG_SCRIM 太重，弹窗像悬在黑屏上", DIALOG_SCRIM <= 0.70f)
    }

    @Test
    fun dialogSurfaceLighterThanDimmedPage() {
        // 弹窗真正的对手不是页面原色，而是「被遮罩压暗后的页面」。
        // 只要弹窗始终比它亮，方向就是对的（深色主题下两者都很暗，靠阴影分层，不能硬卡对比度）。
        listOf(false, true).forEach { dark ->
            val c = appColors(dark)
            val dimmedBg = Color(
                red = c.bgTop.red * (1f - DIALOG_SCRIM),
                green = c.bgTop.green * (1f - DIALOG_SCRIM),
                blue = c.bgTop.blue * (1f - DIALOG_SCRIM),
            )
            assertTrue(
                "dark=$dark 弹窗没有比压暗后的背景更亮（弹窗 ${luminance(c.dialogBg)} / 背景 ${luminance(dimmedBg)}）",
                luminance(c.dialogBg) > luminance(dimmedBg),
            )
        }
    }
}
