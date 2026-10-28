package com.leapcontrol.app

import com.leapcontrol.app.ui.CarCutout
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 车模出厂「落位」的几何约束。
 *
 * 背景：自定义车模导入时要把紧裁的用户图缩进官方图的车模外框里。这一步一旦把图摆到
 * 画布边界外，`Canvas.drawBitmap` 就会静默地把车身切掉一截 —— 表现就是首页上「车底、
 * 车尾被一刀切平」，用户只能重新导入一张。这类问题不会抛异常、不会崩，只能靠断言钉住。
 */
class CarModelFitTest {

    private fun box(l: Int, t: Int, r: Int, b: Int) = CarCutout.Box(l, t, r, b)

    /** 落位结果必须整体落在画布内（允许 1px 的取整误差）。 */
    private fun assertInside(
        fit: CarCutout.FitRect,
        canvasW: Int,
        canvasH: Int,
        label: String,
    ) {
        assertTrue("$label: left=${fit.left} 越界", fit.left >= -1f)
        assertTrue("$label: top=${fit.top} 越界", fit.top >= -1f)
        assertTrue(
            "$label: 右边缘 ${fit.right} 超出画布 $canvasW",
            fit.right <= canvasW + 1f,
        )
        assertTrue(
            "$label: 下边缘 ${fit.bottom} 超出画布 $canvasH",
            fit.bottom >= 0f && fit.bottom <= canvasH + 1f,
        )
    }

    /** 落位不能把车拉变形：宽高比要与内容框一致。 */
    private fun assertAspectKept(content: CarCutout.Box, fit: CarCutout.FitRect, label: String) {
        val src = content.width.toFloat() / content.height
        val dst = fit.width / fit.height
        assertTrue(
            "$label: 宽高比 $src → $dst 被拉伸了",
            abs(src - dst) / src < 0.01f,
        )
    }

    /** 最常见的情况：官方图 1600×1000，车模占中间一大块，用户图紧裁。 */
    @Test
    fun `紧裁图摆进官方车模框后不越界`() {
        val canvas = 1600 to 1000
        val target = box(192, 150, 1407, 860)
        listOf(
            box(0, 0, 1399, 699),      // 很扁
            box(0, 0, 699, 999),       // 很方
            box(0, 0, 1599, 999),      // 与画布同比例
            box(0, 0, 299, 999),       // 竖长
        ).forEach { content ->
            val fit = CarCutout.placeRect(content, target, canvas.first, canvas.second)
            assertInside(fit, canvas.first, canvas.second, "content ${content.width}x${content.height}")
            assertAspectKept(content, fit, "content ${content.width}x${content.height}")
        }
    }

    /** 目标框就是整个画布（官方图自己也是紧裁图时会出现）：仍然不能顶出去。 */
    @Test
    fun `目标框贴满画布也不越界`() {
        val canvas = 800 to 600
        val target = box(0, 0, 799, 599)
        val fit = CarCutout.placeRect(box(0, 0, 1599, 1199), target, canvas.first, canvas.second)
        assertInside(fit, canvas.first, canvas.second, "贴满画布")
    }

    /** 内容比画布还大（极端宽高比）：先等比再缩一次，不能硬画到画布外。 */
    @Test
    fun `内容大于画布时先缩小再落位`() {
        val canvas = 400 to 300
        val target = box(20, 20, 379, 279)
        val fit = CarCutout.placeRect(box(0, 0, 9999, 99), target, canvas.first, canvas.second)
        assertInside(fit, canvas.first, canvas.second, "超宽内容")
        assertTrue("宽度 ${fit.width} 应该被缩到画布内", fit.width <= canvas.first + 1f)
        assertAspectKept(box(0, 0, 9999, 99), fit, "超宽内容")
    }

    /** 退化输入不能抛异常，也不能返回 NaN（NaN 传进 Canvas 会整张图不画）。 */
    @Test
    fun `退化输入返回空矩形`() {
        // width = right - left + 1 = 0 的空内容框
        val empty = CarCutout.Box(5, 5, 4, 4)
        val fit = CarCutout.placeRect(empty, box(10, 10, 100, 100), 200, 200)
        assertTrue("宽度应该是 0 而不是 ${fit.width}", fit.width == 0f)
        assertTrue("left 不能是 NaN", !fit.left.isNaN())
        val zeroCanvas = CarCutout.placeRect(box(0, 0, 99, 99), box(0, 0, 99, 99), 0, 0)
        assertTrue("画布为 0 时不能是 NaN", !zeroCanvas.left.isNaN() && !zeroCanvas.top.isNaN())
    }
}
