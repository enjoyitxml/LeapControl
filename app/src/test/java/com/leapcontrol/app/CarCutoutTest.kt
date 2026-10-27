package com.leapcontrol.app

import com.leapcontrol.app.ui.CarCutout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 车模导入的两步纯算法：抠背景 + 量外框 + 按官方外框摆位。
 *
 * 这层出错的典型表现（都是线上真实踩过的坑）：
 *  - 白底图抠不干净 → 首页车模后面挂一块白方块；
 *  - 抠过头 → 车身被啃出一个洞；
 *  - 外框量歪 → 导入的图比官方大一圈（1.33 倍）或者整辆车偏到一边。
 * 所以每条都用合成图钉死具体像素。
 */
class CarCutoutTest {

    private val transparent = 0x00000000
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF202020.toInt()
    private val carBody = 0xFF6E7480.toInt()

    /** 造一张图：纯色底 + 中间一块实心矩形（当作车）。 */
    private fun canvasWithCar(
        w: Int,
        h: Int,
        bg: Int,
        carLeft: Int,
        carTop: Int,
        carRight: Int,
        carBottom: Int,
        carColor: Int = carBody,
    ): IntArray {
        val px = IntArray(w * h) { bg }
        for (y in carTop..carBottom) {
            for (x in carLeft..carRight) {
                px[y * w + x] = carColor
            }
        }
        return px
    }

    private fun alphaOf(p: Int) = (p ushr 24) and 0xFF

    // ---------------- 抠背景 ----------------

    @Test
    fun `白底图会把背景抠成透明而保留车身`() {
        val w = 100
        val h = 60
        val px = canvasWithCar(w, h, white, 20, 20, 79, 45)
        val cut = CarCutout.removeBackground(px, w, h)

        assertTrue("白底应该被判定为有背景可抠", cut.removed)
        assertEquals("角落应变成全透明", 0, alphaOf(cut.pixels[0]))
        assertEquals("车身中心必须保留", 255, alphaOf(cut.pixels[30 * w + 50]))
    }

    @Test
    fun `抠掉的面积就是背景面积`() {
        val w = 100
        val h = 50
        val px = canvasWithCar(w, h, white, 25, 13, 74, 36)
        val cut = CarCutout.removeBackground(px, w, h)
        val carArea = 50 * 24
        val expected = 1f - carArea.toFloat() / (w * h)
        val actual = CarCutout.transparentRatio(cut.pixels)
        // 允许软边把车身外一圈过渡像素也削掉，误差给 3%
        assertTrue("背景占比应约 $expected，实际 $actual", abs(actual - expected) < 0.03f)
    }

    @Test
    fun `本来就是透明底的图不会再抠一次`() {
        val w = 40
        val h = 40
        val px = IntArray(w * h) { transparent }
        for (y in 10..29) for (x in 10..29) px[y * w + x] = carBody
        val cut = CarCutout.removeBackground(px, w, h)

        assertFalse("已经有透明底就不该再动手", cut.removed)
        assertEquals("车身像素不应被改动", carBody, cut.pixels[20 * w + 20])
    }

    @Test
    fun `边界颜色太杂的实景照片不会硬抠`() {
        val w = 64
        val h = 64
        // 边界上每 8px 换一个颜色：主色桶占比远低于 45%，判定为「不是纯色背景」
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val edge = x < 2 || y < 2 || x >= w - 2 || y >= h - 2
            if (edge) 0xFF000000.toInt() or ((x * 4 + y * 7) * 0x00010101) else carBody
        }
        val cut = CarCutout.removeBackground(px, w, h)
        assertFalse("实景背景不该硬抠（否则会啃出一堆洞）", cut.removed)
    }

    @Test
    fun `整张同色（用户选错图）时不会把图吃光`() {
        val w = 30
        val h = 30
        val px = IntArray(w * h) { white }
        val cut = CarCutout.removeBackground(px, w, h)
        assertFalse("吃光整张图说明判错了，应当放弃", cut.removed)
        assertEquals("原图应原样返回", 255, alphaOf(cut.pixels[0]))
    }

    // ---------------- 外框 ----------------

    @Test
    fun `外框会跳过四周留白`() {
        val w = 100
        val h = 60
        val px = canvasWithCar(w, h, transparent, 20, 10, 79, 49)
        val box = CarCutout.contentBox(px, w, h)!!
        assertEquals(20, box.left)
        assertEquals(10, box.top)
        assertEquals(79, box.right)
        assertEquals(49, box.bottom)
        assertEquals(60, box.width)
        assertEquals(40, box.height)
    }

    @Test
    fun `全透明的图没有外框`() {
        val px = IntArray(100) { transparent }
        assertNull(CarCutout.contentBox(px, 10, 10))
    }

    @Test
    fun `个别噪点不会把外框撑大`() {
        val w = 50
        val h = 50
        val px = IntArray(w * h) { transparent }
        for (y in 20..29) for (x in 20..29) px[y * w + x] = carBody
        // 左上角一个孤立噪点（同一行只有 1 个有效像素）
        px[0] = carBody
        val box = CarCutout.contentBox(px, w, h)!!
        assertEquals("噪点行不参与取景", 20, box.left)
        assertEquals(20, box.top)
    }

    // ---------------- 按官方外框摆位 ----------------

    @Test
    fun `比例与官方一致的图会严丝合缝落进官方外框`() {
        val target = CarCutout.Box(50, 30, 249, 159) // 200 x 130
        val content = CarCutout.Box(0, 0, 399, 259) // 400 x 260，比例相同
        val fit = CarCutout.containRect(content, target)
        assertEquals(50f, fit.left, 0.01f)
        assertEquals(30f, fit.top, 0.01f)
        assertEquals(200f, fit.width, 0.01f)
        assertEquals(130f, fit.height, 0.01f)
    }

    @Test
    fun `比官方更宽的紧裁图会缩到官方宽度并居中`() {
        val target = CarCutout.Box(50, 30, 249, 159) // 200 x 130，比例 1.54
        val content = CarCutout.Box(0, 0, 599, 199) // 600 x 200，比例 3.0
        val fit = CarCutout.containRect(content, target)
        // 受宽度限制：600 宽缩到 200，高只剩 66.7，垂直居中
        assertEquals(200f, fit.width, 0.01f)
        assertEquals(200f / 3f, fit.height, 0.01f)
        assertEquals(50f, fit.left, 0.01f)
        assertTrue("垂直居中：${fit.top}", abs(fit.top - (30f + (130f - 200f / 3f) / 2f)) < 0.01f)
    }

    @Test
    fun `比官方更方的图会缩到官方高度并居中`() {
        val target = CarCutout.Box(50, 30, 249, 159) // 200 x 130
        val content = CarCutout.Box(0, 0, 199, 199) // 200 x 200，正方形
        val fit = CarCutout.containRect(content, target)
        assertEquals(130f, fit.height, 0.01f)
        assertEquals(130f, fit.width, 0.01f)
        assertTrue("水平居中：${fit.left}", abs(fit.left - (50f + (200f - 130f) / 2f)) < 0.01f)
    }

    @Test
    fun `摆位永远不超出官方外框`() {
        val target = CarCutout.Box(10, 10, 109, 69)
        val cases = listOf(
            CarCutout.Box(0, 0, 999, 10), // 极扁
            CarCutout.Box(0, 0, 10, 999), // 极窄
            CarCutout.Box(0, 0, 300, 300), // 正方形
        )
        cases.forEach { content ->
            val fit = CarCutout.containRect(content, target)
            assertTrue("左边界溢出 $content -> $fit", fit.left >= target.left - 0.01f)
            assertTrue("上边界溢出 $content -> $fit", fit.top >= target.top - 0.01f)
            assertTrue("右边界溢出 $content -> $fit", fit.right <= target.right + 1f)
            assertTrue("下边界溢出 $content -> $fit", fit.bottom <= target.bottom + 1f)
        }
    }
}
