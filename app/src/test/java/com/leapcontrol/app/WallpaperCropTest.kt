package com.leapcontrol.app

import com.leapcontrol.app.widget.WallpaperCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 自定义壁纸裁剪的几何。
 *
 * 这几条对应的是「预览框里看到的」和「真正存下来的」必须是同一块：
 * - 缩放不够 → 框里露底色，存下来的图会把底色也拍进去；
 * - 位移没夹住 → 拖动时露边，存下来缺一块；
 * - 反算矩形越界 → `Bitmap.createBitmap` 直接抛异常，而这时用户已经点过「完成」。
 * 出错的代价都是「点了确定才崩 / 才变形」，所以放在纯函数里用断言钉住。
 */
class WallpaperCropTest {

    private val aspect = WallpaperCrop.ASPECT

    /** 常见手机竖图：1080×2400。 */
    private val portrait = 1080f to 2400f

    /** 横图：4032×3024。 */
    private val landscape = 4032f to 3024f

    @Test
    fun `coverScale 一定盖住裁剪框`() {
        val boxW = 1000f
        val boxH = boxW / aspect
        listOf(portrait, landscape).forEach { (iw, ih) ->
            val s = WallpaperCrop.coverScale(iw, ih, boxW, boxH)
            assertTrue("宽 ${iw * s} 盖不住 $boxW", iw * s >= boxW - 0.01f)
            assertTrue("高 ${ih * s} 盖不住 $boxH", ih * s >= boxH - 0.01f)
        }
    }

    @Test
    fun `位移被夹进合法范围，图片任何一边都不进框`() {
        val boxW = 900f
        val boxH = boxW / aspect
        val (iw, ih) = landscape
        val s = WallpaperCrop.coverScale(iw, ih, boxW, boxH)
        // 故意给一个巨大的位移
        val (ox, oy) = WallpaperCrop.clampOffset(iw, ih, boxW, boxH, s, 5000f, -5000f)
        assertTrue("横向 ${iw * s} 宽，位移 $ox 过大", abs(ox) <= (iw * s - boxW) / 2f + 0.01f)
        assertTrue("纵向位移 $oy 过大", abs(oy) <= (ih * s - boxH) / 2f + 0.01f)
    }

    @Test
    fun `不拖动时裁的是正中那块，比例与框一致`() {
        val boxW = 880f
        val boxH = boxW / aspect
        val (iw, ih) = portrait
        val s = WallpaperCrop.coverScale(iw, ih, boxW, boxH)
        val rect = WallpaperCrop.sourceRect(iw, ih, boxW, boxH, s, 0f, 0f)
        // 宽度取整后最大差 1px；比例误差允许 1%
        val ratio = rect.width.toFloat() / rect.height
        assertTrue("比例 $ratio 与 $aspect 差太多", abs(ratio - aspect) / aspect < 0.02f)
        // 居中：左右留白应当相等（纵向是长边，会上下裁掉很多）
        val leftGap = rect.left
        val rightGap = iw.toInt() - (rect.left + rect.width)
        assertTrue("左右没居中：$leftGap / $rightGap", abs(leftGap - rightGap) <= 2)
    }

    @Test
    fun `放到最大并拖到边角，矩形仍落在图片内`() {
        val boxW = 900f
        val boxH = boxW / aspect
        listOf(portrait, landscape).forEach { (iw, ih) ->
            val base = WallpaperCrop.coverScale(iw, ih, boxW, boxH)
            val rect = WallpaperCrop.sourceRect(iw, ih, boxW, boxH, base * 4f, 9999f, -9999f)
            assertTrue("left=${rect.left}", rect.left >= 0)
            assertTrue("top=${rect.top}", rect.top >= 0)
            assertTrue("右边缘 ${rect.left + rect.width} > ${iw.toInt()}", rect.left + rect.width <= iw.toInt())
            assertTrue("下边缘 ${rect.top + rect.height} > ${ih.toInt()}", rect.top + rect.height <= ih.toInt())
            assertTrue("宽高必须 ≥1", rect.width >= 1 && rect.height >= 1)
        }
    }

    @Test
    fun `退化输入不抛异常也不给 0 尺寸`() {
        val rect = WallpaperCrop.sourceRect(0f, 0f, 100f, 100f / aspect, 1f, 0f, 0f)
        assertTrue(rect.width >= 1 && rect.height >= 1)
        val scale = WallpaperCrop.coverScale(0f, 0f, 100f, 50f)
        assertTrue("缩放不能是 NaN", scale.isFinite() && scale > 0f)
    }

    @Test
    fun `导出高度与框比例一致`() {
        val h = WallpaperCrop.outputHeight(WallpaperCrop.OUT_WIDTH)
        assertEquals(WallpaperCrop.OUT_WIDTH / aspect, h.toFloat(), 1f)
    }
}
