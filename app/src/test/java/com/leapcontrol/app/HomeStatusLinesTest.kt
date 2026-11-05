package com.leapcontrol.app

import com.leapcontrol.app.ui.HomeStatusLines
import com.leapcontrol.app.ui.StatusEmphasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页车辆状态区（方案 02 双行文本流）的文案规则。
 *
 * 这里守的是 2026-11-04 用户反馈过的那条口径：**空调开着是常态，不该是警示色**。
 * 之前四种「已开启」全走 `alert = true`，红图标一排，看着像车出了四个故障。
 */
class HomeStatusLinesTest {

    private fun view(
        badge: DrivingBadge? = DrivingBadge("已驻车", tone = DrivingTone.PARKED),
        locked: Boolean? = true,
        acOn: Boolean? = null,
        acTemp: String? = null,
        windowOpen: Boolean? = null,
        trunkOpen: Boolean? = null,
        sunshadeOpen: Boolean? = null,
    ) = HomeStatusLines.build(badge, locked, acOn, acTemp, windowOpen, trunkOpen, sunshadeOpen)

    @Test
    fun `有主状态时闭锁降为从属项`() {
        val v = view()
        assertEquals("已驻车", v.primary)
        assertEquals(listOf("已上锁"), v.tokens.map { it.text })
    }

    @Test
    fun `空调开着用普通色，不是警示色`() {
        val v = view(acOn = true, acTemp = "24")
        val ac = v.tokens.first { it.text.startsWith("空调") }
        assertEquals(StatusEmphasis.NORMAL, ac.emphasis)
        assertEquals("空调 24°", ac.text)
    }

    @Test
    fun `已解锁和车窗开着才算警示`() {
        val v = view(locked = false, windowOpen = true, trunkOpen = true)
        v.tokens.forEach { assertEquals(StatusEmphasis.ALERT, it.emphasis) }
    }

    @Test
    fun `遮阳帘开着也只是普通项`() {
        val v = view(sunshadeOpen = true)
        assertEquals(StatusEmphasis.NORMAL, v.tokens.first { it.text == "遮阳帘开" }.emphasis)
    }

    @Test
    fun `没有挡位车速时退回闭锁说法，不写状态未知`() {
        val v = view(badge = null, locked = true)
        assertEquals("已上锁", v.primary)
        assertTrue(v.tokens.isEmpty())
    }

    @Test
    fun `什么都判不出来也不留空`() {
        val v = view(badge = null, locked = null)
        assertEquals("锁车未知", v.primary)
        assertTrue(v.tokens.isEmpty())
    }

    @Test
    fun `没有主状态时开启项仍然照常显示`() {
        val v = view(badge = null, locked = true, acOn = true, acTemp = "24")
        assertEquals("已上锁", v.primary)
        assertEquals(listOf("空调 24°"), v.tokens.map { it.text })
    }

    @Test
    fun `温度文案各种形态`() {
        assertEquals("空调 24°", HomeStatusLines.acText("24"))
        assertEquals("空调 24°", HomeStatusLines.acText("24.0"))
        assertEquals("空调 23.5°", HomeStatusLines.acText("23.5"))
        assertEquals("空调 24°", HomeStatusLines.acText("24°"))
        assertEquals("空调 24°", HomeStatusLines.acText(" 24℃ "))
        assertEquals("空调开", HomeStatusLines.acText(null))
        assertEquals("空调开", HomeStatusLines.acText("  "))
    }

    @Test
    fun `行驶中带车速，闭锁仍在第二行`() {
        val v = view(
            badge = DrivingBadge("行驶中", detail = "42 km/h", tone = DrivingTone.MOVING),
            locked = true,
        )
        assertEquals("行驶中", v.primary)
        assertEquals("42 km/h", v.detail)
        assertEquals(listOf("已上锁"), v.tokens.map { it.text })
    }
}
