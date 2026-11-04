package com.leapcontrol.app

import com.leapcontrol.app.ui.TireValueMinFontSp
import com.leapcontrol.app.ui.fittedValueFontSp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 胎压数字按可用宽度缩放。
 *
 * 起因：字号放大后「258」比列宽还宽，`Text` 默认 `TextOverflow.Clip`，
 * 于是最右边的数字被切掉一块（用户 2026-11-04 第二次反馈，第一次只修了高度）。
 * 这里守住「量出来多宽就按比例缩多少」这条换算，别让它缩过头或干脆不缩。
 */
class TireValueFontTest {

    private val base = 17f

    @Test
    fun `放得下就用基准字号`() {
        assertEquals(base.toDouble(), fittedValueFontSp(90, 120).toDouble(), 0.001)
        // 正好等宽也算放得下
        assertEquals(base.toDouble(), fittedValueFontSp(120, 120).toDouble(), 0.001)
    }

    @Test
    fun `放不下时按宽度等比缩小`() {
        // 需要 120px 只给 90px → 缩到 17 × 0.75 = 12.75sp
        assertEquals(12.75.toDouble(), fittedValueFontSp(120, 90).toDouble(), 0.001)
        // 需要 136px 只给 108px → 缩到 17 × 0.794… ≈ 13.5sp
        assertEquals(13.5.toDouble(), fittedValueFontSp(136, 108).toDouble(), 0.01)
    }

    @Test
    fun `极窄时不低于下限`() {
        assertEquals(
            TireValueMinFontSp.toDouble(),
            fittedValueFontSp(200, 40).toDouble(),
            0.001,
        )
    }

    /** 测量失败（0）或约束未定（0 / 负数）时不能把字号算成 0 或负数。 */
    @Test
    fun `测量或约束无效时退回基准字号`() {
        assertEquals(base.toDouble(), fittedValueFontSp(0, 100).toDouble(), 0.001)
        assertEquals(base.toDouble(), fittedValueFontSp(-1, 100).toDouble(), 0.001)
        assertEquals(base.toDouble(), fittedValueFontSp(100, 0).toDouble(), 0.001)
        assertEquals(base.toDouble(), fittedValueFontSp(100, -5).toDouble(), 0.001)
    }
}
