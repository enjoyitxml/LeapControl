package com.leapcontrol.app

import com.leapcontrol.app.ui.CarTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 车模换色的数学部分。
 *
 * 这一层的输出直接决定首页那辆车看起来「像不像自己的车」，出错的典型表现是：
 * 车漆没变、轮胎被染成彩色、反光被抹平、或者整辆车糊成一块。所以每一条都钉具体数值。
 */
class CarTintTest {

    private val white = 0xFFF4F5F7.toInt()
    private val black = 0xFF17181C.toInt()
    private val deepBlue = 0xFF1F3C88.toInt()
    private val lightGray = 0xFFC3C8CF.toInt()

    // ---------------- 单像素换色 ----------------

    @Test
    fun `换成白色会把蓝色车漆的饱和度压到目标色的水平`() {
        val out = CarTint.tintPixel(deepBlue, white, 1f)
        val s = CarTint.toHsl(out).second
        val targetS = CarTint.toHsl(white).second
        // 「珍珠白」本身带一点点冷调（s≈0.16），所以判据是「收敛到目标色」而不是「归零」
        assertTrue("目标色本身就得很白：$targetS", targetS < 0.2f)
        assertTrue("输出饱和度应贴近目标色 $targetS，实际 $s", abs(s - targetS) < 0.04f)
    }

    @Test
    fun `换成白色会提亮深色车漆`() {
        val before = CarTint.toHsl(deepBlue).third
        val after = CarTint.toHsl(CarTint.tintPixel(deepBlue, white, 1f)).third
        assertTrue("深蓝换白应变亮：$before → $after", after > before + 0.1f)
    }

    @Test
    fun `换成黑色会压暗浅色车漆`() {
        val before = CarTint.toHsl(lightGray).third
        val after = CarTint.toHsl(CarTint.tintPixel(lightGray, black, 1f)).third
        assertTrue("浅灰换黑应变暗：$before → $after", after < before - 0.3f)
    }

    @Test
    fun `强度为零时原样返回`() {
        assertEquals(deepBlue, CarTint.tintPixel(deepBlue, white, 0f))
    }

    @Test
    fun `全透明像素不参与换色`() {
        val transparent = 0x001F3C88
        assertEquals(transparent, CarTint.tintPixel(transparent, white, 1f))
    }

    @Test
    fun `半透明像素保留原 alpha`() {
        val half = 0x801F3C88.toInt()
        val out = CarTint.tintPixel(half, white, 1f)
        assertEquals(0x80, CarTint.alphaOf(out))
    }

    @Test
    fun `近黑的轮胎与投影完全不动`() {
        val tire = 0xFF0A0A0A.toInt()
        assertEquals(tire, CarTint.tintPixel(tire, white, 1f))
        assertEquals(tire, CarTint.tintPixel(tire, 0xFFFF0000.toInt(), 1f))
    }

    @Test
    fun `车身权重按明度分层`() {
        assertEquals(0f, CarTint.bodyWeight(0xFF000000.toInt()), 0.001f)
        // 深色（玻璃 / 暗部）：压一半，避免玻璃也被染成彩色
        assertEquals(0.55f, CarTint.bodyWeight(0xFF202020.toInt()), 0.02f)
        // 正常车漆：完全参与
        assertEquals(1f, CarTint.bodyWeight(lightGray), 0.02f)
        // 近白高光：留一点原色，反光才不会被抹平
        assertEquals(0.35f, CarTint.bodyWeight(0xFFFEFEFE.toInt()), 0.02f)
    }

    // ---------------- 明度处理 ----------------

    @Test
    fun `换色后仍然保留明暗层次`() {
        // 两个只差明度的车漆像素，换同一目标色后，亮的那个还得更亮
        val dark = 0xFF5A6068.toInt()
        val light = 0xFF9AA1AA.toInt()
        val outDark = CarTint.toHsl(CarTint.tintPixel(dark, white, 1f)).third
        val outLight = CarTint.toHsl(CarTint.tintPixel(light, white, 1f)).third
        assertTrue("换白后层次应保留：$outDark < $outLight", outDark < outLight)
    }

    @Test
    fun `强度影响改变幅度且有单调性`() {
        val weak = CarTint.toHsl(CarTint.tintPixel(deepBlue, white, 0.3f)).second
        val strong = CarTint.toHsl(CarTint.tintPixel(deepBlue, white, 1f)).second
        val original = CarTint.toHsl(deepBlue).second
        assertTrue("强度越大越接近目标色：$original → $weak → $strong", weak < original && strong < weak)
    }

    // ---------------- HSL 基础运算 ----------------

    @Test
    fun `HSL 往返不丢颜色`() {
        val samples = listOf(
            0xFF1F3C88.toInt(),
            0xFFAF2B2B.toInt(),
            0xFFF4F5F7.toInt(),
            0xFF17181C.toInt(),
            0xFF808080.toInt(),
            0xFF1F6F5C.toInt(),
        )
        samples.forEach { c ->
            val (h, s, l) = CarTint.toHsl(c)
            val back = CarTint.toArgb(h, s, l, 255)
            assertChannelNear(c, back, 2)
        }
    }

    @Test
    fun `统计值给出合理的明度区间`() {
        // 一半深灰、一半浅灰，分位应该落在两者之间
        val pixels = IntArray(64) { i ->
            if (i < 32) 0xFF333333.toInt() else 0xFFCCCCCC.toInt()
        }
        val stats = CarTint.stats(pixels)
        assertTrue("6% 分位应接近深灰：${stats.loL}", stats.loL in 0.15f..0.30f)
        assertTrue("94% 分位应接近浅灰：${stats.hiL}", stats.hiL in 0.70f..0.85f)
        assertTrue("区间不能反向", stats.hiL > stats.loL)
    }

    @Test
    fun `全透明的图走兜底区间而不是崩掉`() {
        val stats = CarTint.stats(IntArray(16))
        assertEquals(CarTint.Stats.EMPTY, stats)
    }

    @Test
    fun `批量换色不碰纯黑像素`() {
        val tire = 0xFF000000.toInt()
        val pixels = intArrayOf(tire, deepBlue, tire, lightGray)
        CarTint.tintPixels(pixels, white, 1f)
        assertEquals(tire, pixels[0])
        assertEquals(tire, pixels[2])
        assertTrue("车身像素应该变了", pixels[1] != deepBlue)
        assertTrue("车身像素应该变了", pixels[3] != lightGray)
    }

    // ---------------- 色板 ----------------

    @Test
    fun `色板前四个是去饱和方向`() {
        CarTint.PRESETS.take(4).forEach { preset ->
            val s = CarTint.toHsl(preset.argb).second
            assertTrue("${preset.label} 应该接近无彩：s=$s", s <= 0.2f)
        }
    }

    @Test
    fun `色板不重复且都在有效范围`() {
        assertEquals(CarTint.PRESETS.size, CarTint.PRESETS.map { it.argb }.toSet().size)
        CarTint.PRESETS.forEach { p ->
            assertEquals(255, CarTint.alphaOf(p.argb))
            assertTrue("${p.label} 应该有不透明的颜色", p.label.isNotBlank())
        }
    }

    private fun assertChannelNear(expected: Int, actual: Int, tolerance: Int) {
        val channels = listOf(16, 8, 0)
        channels.forEach { shift ->
            val e = (expected ushr shift) and 0xFF
            val a = (actual ushr shift) and 0xFF
            assertTrue(
                "通道偏移 $shift 不一致：expected=$e actual=$a",
                abs(e - a) <= tolerance,
            )
        }
    }
}
