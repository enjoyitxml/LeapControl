package com.leapcontrol.app

import com.leapcontrol.app.ui.CustomBackdropKind
import com.leapcontrol.app.ui.WallpaperCropMath
import com.leapcontrol.app.ui.WallpaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义背景的两块纯逻辑：[WallpaperPalette]（从壁纸取主题色）与
 * [WallpaperCropMath]（裁切框尺寸）。
 *
 * 这两块都是「错了也不会崩、但用户一眼能看出来不对」的地方：
 * 主色取歪了整页配色就脏，框算错了预览和结果差半屏。所以钉在这里。
 */
class WallpaperTest {

    private fun solid(color: Int, size: Int = 32): IntArray = IntArray(size * size) { color }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    // ---------------- 主色提取 ----------------

    @Test
    fun `纯色壁纸取出同色相的主题色`() {
        // 深一点的蓝：明度不能顶到 1.0（纯色会被当作「过曝」滤掉）
        val pixels = solid(argb(0, 0, 200))
        val accent = WallpaperPalette.accentFrom(pixels, 32, 32, dark = false)
        assertTrue("蓝色壁纸应该能取到颜色", accent != null)
        val (h, _, _) = WallpaperPalette.rgbToHsv(
            (accent!! shr 16) and 0xFF,
            (accent shr 8) and 0xFF,
            accent and 0xFF,
        )
        // 色相要落在蓝色附近（允许几度误差）
        assertTrue("色相应接近 240°，实际 $h", kotlin.math.abs(h - 240f) < 8f)
    }

    @Test
    fun `灰色壁纸取不到主题色`() {
        // 纯灰饱和度 0：没有「主色」可言，交给内置主题色兜底
        assertNull(WallpaperPalette.accentFrom(solid(argb(128, 128, 128)), 32, 32, dark = false))
    }

    @Test
    fun `纯白与纯黑都取不到主题色`() {
        assertNull(WallpaperPalette.accentFrom(solid(argb(255, 255, 255)), 32, 32, dark = false))
        assertNull(WallpaperPalette.accentFrom(solid(argb(0, 0, 0)), 32, 32, dark = false))
    }

    @Test
    fun `透明像素被忽略`() {
        // 全透明：等于没有可用像素
        val pixels = IntArray(32 * 32) { 0x00000000 }
        assertNull(WallpaperPalette.accentFrom(pixels, 32, 32, dark = false))
    }

    @Test
    fun `深色模式的强调色更亮`() {
        val pixels = solid(argb(0, 0, 200))
        val light = WallpaperPalette.accentFrom(pixels, 32, 32, dark = false)!!
        val dark = WallpaperPalette.accentFrom(pixels, 32, 32, dark = true)!!
        fun brightness(c: Int) = maxOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        assertTrue(
            "深色模式的主题色应该更亮（浅 $light / 深 $dark）",
            brightness(dark) > brightness(light),
        )
    }

    @Test
    fun `大面积淡色不抢主色`() {
        // 4/5 是接近白的天空（饱和度低），1/5 是饱和的绿色：主色应该还是绿
        val size = 40
        val pixels = IntArray(size * size) { i ->
            if (i % 5 == 0) argb(40, 160, 60) else argb(232, 240, 236)
        }
        val accent = WallpaperPalette.accentFrom(pixels, size, size, dark = false)!!
        val (h, _, _) = WallpaperPalette.rgbToHsv(
            (accent shr 16) and 0xFF,
            (accent shr 8) and 0xFF,
            accent and 0xFF,
        )
        assertTrue("主色应接近绿色系，实际色相 $h", h in 80f..160f)
    }

    @Test
    fun `空输入不崩`() {
        assertNull(WallpaperPalette.accentFrom(IntArray(0), 0, 0, dark = false))
        assertNull(WallpaperPalette.accentFrom(IntArray(10), 10, 1, dark = false))
    }

    // ---------------- 裁切框尺寸 ----------------

    @Test
    fun `竖长比例受高度约束`() {
        // 手机 400×800dp、壁纸比例约 0.46：高度先顶到 78% 上限
        val (w, h) = WallpaperCropMath.frameSizeDp(400f, 800f, 0.46f)
        assertEquals(800f * 0.78f, h, 0.5f)
        assertEquals(h * 0.46f, w, 0.5f)
    }

    @Test
    fun `扁长比例受宽度约束`() {
        // 相伴卡的 3:1：宽度先顶到 88% 上限，高度自然很扁（约 117dp，和卡片 116dp 一致）
        val (w, h) = WallpaperCropMath.frameSizeDp(400f, 800f, 3f)
        assertEquals(400f * 0.88f, w, 0.5f)
        assertTrue("3:1 的高度应接近卡片高度，实际 $h", h in 110f..125f)
    }

    @Test
    fun `非法比例返回零`() {
        assertEquals(0f to 0f, WallpaperCropMath.frameSizeDp(400f, 800f, 0f))
        assertEquals(0f to 0f, WallpaperCropMath.frameSizeDp(400f, 800f, -1f))
    }

    // ---------------- 文件种类 ----------------

    @Test
    fun `只有两张壁纸算壁纸`() {
        assertTrue(CustomBackdropKind.WALLPAPER_DAY.isWallpaper)
        assertTrue(CustomBackdropKind.WALLPAPER_NIGHT.isWallpaper)
        // 相伴卡是 3:1 的横条，不是整页壁纸：主色提取只对壁纸有意义
        assertTrue(!CustomBackdropKind.OWNERSHIP.isWallpaper)
    }

    @Test
    fun `三个文件互不重名`() {
        val names = CustomBackdropKind.entries.map { it.fileName }
        assertEquals(names.size, names.toSet().size)
    }

}
