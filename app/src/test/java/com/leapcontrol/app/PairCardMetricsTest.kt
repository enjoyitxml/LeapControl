package com.leapcontrol.app

import com.leapcontrol.app.ui.pairCardCarWidth
import com.leapcontrol.app.ui.pairCardHeight
import com.leapcontrol.app.ui.pairCardScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「胎压 · 行程」两张卡的高度换算。
 *
 * 这两张卡最容易出错的地方就是高度写死：字号一放大，胎压卡最下面那行 `kPa` 被
 * 卡片底边的圆角 clip 切掉（用户 2026-11-04 反馈）。所以把「高度随字号放大、
 * 且放大有上界」这件事钉成用例，避免以后又被改回常量。
 */
class PairCardMetricsTest {

    @Test
    fun `字号 1 倍时保持原尺寸`() {
        assertEquals(1f, pairCardScale(1f), 0.0001f)
        assertEquals(172f, pairCardHeight(1f).value, 0.01f)
        assertEquals(60f, pairCardCarWidth(1f).value, 0.01f)
    }

    @Test
    fun `字号放大时卡片跟随放大，车模只做部分补偿`() {
        val scale = pairCardScale(1.2f)
        assertEquals(1.2f, scale, 0.0001f)
        assertEquals(206.4f, pairCardHeight(1.2f).value, 0.01f)
        // 车模宽度按六成补偿：卡片必须变高（否则字被裁），但车模完全等比会把左右文字挤窄
        assertEquals(67.2f, pairCardCarWidth(1.2f).value, 0.01f)
        // 补偿量必须小于高度增幅，否则就是等比放大、文字列会被压窄
        assertTrue(pairCardCarWidth(1.2f).value / 60f < pairCardHeight(1.2f).value / 172f)
    }

    @Test
    fun `放大到全局上限 1_3 倍封顶`() {
        assertEquals(1.3f, pairCardScale(1.3f), 0.0001f)
        // 再大的系统字号也不会真的渲染出来（Theme 里已钳在 1.3），卡片不再跟着长
        assertEquals(1.3f, pairCardScale(1.6f), 0.0001f)
        assertEquals(1.3f, pairCardScale(2.0f), 0.0001f)
        assertEquals(223.6f, pairCardHeight(2.0f).value, 0.01f)
        // 字号封顶后车模宽度也封顶，不会继续吃文字列的宽度
        assertEquals(70.8f, pairCardCarWidth(2.0f).value, 0.01f)
    }

    @Test
    fun `字号小于 1 时不缩小卡片`() {
        // 用户把字号调小只是想让更多内容装进一屏，卡片跟着缩只会更挤
        assertEquals(1f, pairCardScale(0.85f), 0.0001f)
        assertEquals(1f, pairCardScale(0f), 0.0001f)
        assertEquals(172f, pairCardHeight(0.85f).value, 0.01f)
    }

    @Test
    fun `异常字号不会算出非法尺寸`() {
        // fontScale 理论上不会为负或为 NaN，但布局值来自系统，兜一下更安全
        assertEquals(1f, pairCardScale(-1f), 0.0001f)
        assertEquals(1f, pairCardScale(Float.NaN), 0.0001f)
    }
}
