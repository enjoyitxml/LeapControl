package com.leapcontrol.app.ui

import com.leapcontrol.app.journal.MedalGlyph
import com.leapcontrol.app.journal.MedalTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 勋章图形表与色板的自检。
 *
 * 图形是一串手写的 SVG 路径，写错一个字母不会编译报错，只会在真机上「那枚章空了」——
 * 这种问题极难靠肉眼在一百多枚里发现，所以在这里把底线守住：
 * 每个枚举都有图、每层只有一个线宽前缀、坐标不出 24 网格。
 */
class MedalArtTest {

    private val source = MedalGlyphSource.ALL

    @Test
    fun `每个图形都有路径且不多不少`() {
        assertEquals(MedalGlyph.entries.size, source.size)
        MedalGlyph.entries.forEach { glyph ->
            val src = source[glyph]
            assertTrue("缺图形：$glyph", src != null && src.isNotBlank())
        }
    }

    @Test
    fun `每层都以 M 开头且只带一个线宽前缀`() {
        source.forEach { (glyph, src) ->
            src.split('|').forEach { raw ->
                val layer = raw.trim()
                assertTrue("$glyph 有空层", layer.isNotEmpty())
                val body = when (layer.first()) {
                    '~', 'o', '*' -> layer.substring(1)
                    else -> layer
                }
                assertTrue("$glyph 有多余前缀：$layer", body.first() != '~' && body.first() != 'o' && body.first() != '*')
                assertTrue("$glyph 不是绝对起点：$layer", body.startsWith("M"))
            }
        }
    }

    @Test
    fun `坐标不出 24 网格`() {
        // 有些路径用了相对指令（`l-3.77`），差值也是数字，不能按绝对坐标卡死，
        // 所以只挡「明显打错一位」的量级错误 —— 那种错在真机上就是那枚章整个画飞。
        val number = Regex("""-?\d+(\.\d+)?""")
        source.forEach { (glyph, src) ->
            number.findAll(src).forEach { m ->
                val v = m.value.toFloat()
                assertTrue("$glyph 坐标量级异常：$v", v in -8f..32f)
            }
        }
    }

    @Test
    fun `四档浅色盘面由亮到暗`() {
        MedalTier.entries.forEach { tier ->
            val pal = medalPalette(tier, dark = false)
            val plate = lum(pal.plate)
            val edge = lum(pal.plateEdge)
            val deep = lum(pal.plateDeep)
            assertTrue("$tier 盘心应最亮", plate >= edge)
            assertTrue("$tier 盘缘应最暗", edge >= deep)
            // 图形要比盘面深得多，否则 46dp 下「刻」不出来
            assertTrue("$tier 图形与盘面对比不足", plate - lum(pal.ink) > 0.35f)
        }
    }

    @Test
    fun `未解锁是同一套灰阶`() {
        val pal = lockedMedalPalette(dark = false)
        val max = maxOf(pal.plate.red, pal.plate.green, pal.plate.blue)
        val min = minOf(pal.plate.red, pal.plate.green, pal.plate.blue)
        assertTrue("未解锁不该带明显色相", max - min < 0.06f)
        assertTrue("未解锁图形要退到底", lum(pal.plate) - lum(pal.ink) in 0.05f..0.42f)
    }

    /** 粗略亮度：人眼对绿最敏感，够判断明暗顺序。 */
    private fun lum(c: androidx.compose.ui.graphics.Color): Float =
        0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
}
