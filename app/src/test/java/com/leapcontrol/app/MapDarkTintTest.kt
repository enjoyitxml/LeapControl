package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 深色主题下把浅色静态图翻成深色底图。
 *
 * 高德静态地图没有样式参数，夜间配色只能本地算。这里锁住三条性质：
 * 背景要落到近黑、深色文字要变成浅色、有颜色的地物（绿地 / 水面）不能被翻成怪色。
 */
class MapDarkTintTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun transformOne(color: Int): Int {
        val pixels = intArrayOf(color)
        MapDarkTint.transform(pixels)
        return pixels[0]
    }

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun `米白底子翻成近黑`() {
        val out = transformOne(argb(247, 247, 245))
        assertTrue("r=${red(out)}", red(out) < 30)
        // 中性底色翻完仍是中性灰（带一点点色偏是反相保留色相的结果，不能跑偏成彩色）
        assertTrue("r=${red(out)} b=${blue(out)}", kotlin.math.abs(red(out) - blue(out)) <= 3)
    }

    @Test
    fun `深灰文字翻成浅色`() {
        val out = transformOne(argb(0x33, 0x33, 0x33))
        assertTrue("r=${red(out)}", red(out) > 200)
    }

    @Test
    fun `纯白路面翻成纯黑`() {
        val out = transformOne(argb(255, 255, 255))
        assertEquals(0, red(out))
        assertEquals(0, green(out))
        assertEquals(0, blue(out))
    }

    @Test
    fun `纯黑描边翻成纯白而不是除零`() {
        val out = transformOne(argb(0, 0, 0))
        assertEquals(255, red(out))
        assertEquals(255, green(out))
        assertEquals(255, blue(out))
    }

    @Test
    fun `绿地翻暗后还是绿的`() {
        val out = transformOne(argb(0xCF, 0xE8, 0xC8))
        assertTrue("g 应高于 r 和 b：${red(out)},${green(out)},${blue(out)}", green(out) > red(out) && green(out) > blue(out))
        assertTrue("不能还亮着：g=${green(out)}", green(out) < 90)
    }

    @Test
    fun `水面翻暗后还是偏蓝`() {
        val out = transformOne(argb(0xBF, 0xDC, 0xF2))
        assertTrue("b 应最高：${red(out)},${green(out)},${blue(out)}", blue(out) >= green(out) && blue(out) >= red(out))
    }

    @Test
    fun `透明度原样保留`() {
        val out = transformOne(argb(0xF7, 0xF7, 0xF5, a = 0x80))
        assertEquals(0x80, (out ushr 24) and 0xFF)
    }

    @Test
    fun `整体明度确实降下来了`() {
        // 一整块浅色地图的平均色：拿几种典型底色算，翻完必须明显更暗
        val pixels = intArrayOf(
            argb(247, 247, 245), argb(239, 236, 227), argb(221, 221, 221), argb(207, 232, 200),
        )
        val before = pixels.sumOf { red(it) + green(it) + blue(it) }
        MapDarkTint.transform(pixels)
        val after = pixels.sumOf { red(it) + green(it) + blue(it) }
        assertTrue("before=$before after=$after", after * 3 < before)
    }
}
