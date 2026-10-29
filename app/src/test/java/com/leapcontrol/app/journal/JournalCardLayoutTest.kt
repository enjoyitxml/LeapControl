package com.leapcontrol.app.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 零记概览页的卡片顺序 / 显隐 / 读写协议。 */
class JournalCardLayoutTest {

    @Test
    fun `从未保存过时用默认顺序`() {
        assertEquals(JournalCardLayout.DEFAULT, JournalCardLayout.parse(null))
    }

    @Test
    fun `默认顺序里保险在保养前面`() {
        val order = JournalCardLayout.DEFAULT
        assertTrue(order.indexOf(JournalCardId.INSURANCE) < order.indexOf(JournalCardId.MAINTENANCE))
    }

    @Test
    fun `默认顺序包含全部卡片`() {
        assertEquals(JournalCardId.entries.toList(), JournalCardLayout.DEFAULT.sortedBy { it.ordinal })
    }

    @Test
    fun `空串表示用户把卡片全删了，不回退默认`() {
        assertEquals(emptyList<JournalCardId>(), JournalCardLayout.parse(""))
        assertEquals(emptyList<JournalCardId>(), JournalCardLayout.parse("   "))
    }

    @Test
    fun `非法与重复的 id 会被过滤`() {
        val parsed = JournalCardLayout.parse("GOAL,WHAT,GOAL,COST")
        assertEquals(listOf(JournalCardId.GOAL, JournalCardId.COST), parsed)
    }

    @Test
    fun `序列化与解析可以往返`() {
        val order = listOf(
            JournalCardId.FOOTPRINT,
            JournalCardId.INSURANCE,
            JournalCardId.MAINTENANCE,
        )
        assertEquals(order, JournalCardLayout.parse(JournalCardLayout.serialize(order)))
    }

    /** 老版本存的是「半屏并排」的顺序串，升级后必须还能原样读出来。 */
    @Test
    fun `旧版半屏顺序串仍可解析`() {
        val legacy = "INSURANCE,MAINTENANCE,GOAL,COST,FOOTPRINT"
        assertEquals(JournalCardLayout.DEFAULT, JournalCardLayout.parse(legacy))
    }

    @Test
    fun `隐藏卡片按枚举顺序列出`() {
        val shown = listOf(JournalCardId.GOAL)
        val hidden = JournalCardLayout.hidden(shown)
        assertEquals(JournalCardId.entries.size - 1, hidden.size)
        assertTrue(JournalCardId.GOAL !in hidden)
        assertTrue(JournalCardId.INSURANCE in hidden)
    }

    @Test
    fun `全部显示时没有可添加的卡片`() {
        assertTrue(JournalCardLayout.hidden(JournalCardLayout.DEFAULT).isEmpty())
    }
}
