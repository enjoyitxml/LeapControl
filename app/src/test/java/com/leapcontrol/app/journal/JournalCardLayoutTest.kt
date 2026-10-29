package com.leapcontrol.app.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 零记概览页的卡片顺序 / 显隐 / 半屏卡排版分组。 */
class JournalCardLayoutTest {

    @Test
    fun `从未保存过时用默认顺序`() {
        assertEquals(JournalCardLayout.DEFAULT, JournalCardLayout.parse(null))
    }

    @Test
    fun `默认顺序里保险在保养前面`() {
        // 半屏并排时左边的就是列表里靠前的那张，所以顺序即左右
        val order = JournalCardLayout.DEFAULT
        assertTrue(order.indexOf(JournalCardId.INSURANCE) < order.indexOf(JournalCardId.MAINTENANCE))
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
        val order = listOf(JournalCardId.FOOTPRINT, JournalCardId.INSURANCE, JournalCardId.MAINTENANCE)
        assertEquals(order, JournalCardLayout.parse(JournalCardLayout.serialize(order)))
    }

    @Test
    fun `两张相邻的半屏卡并成一行`() {
        val rows = JournalCardLayout.rows(
            listOf(JournalCardId.GOAL, JournalCardId.INSURANCE, JournalCardId.MAINTENANCE, JournalCardId.COST),
        )
        assertEquals(3, rows.size)
        assertEquals(listOf(JournalCardId.GOAL), rows[0].cards)
        assertEquals(listOf(JournalCardId.INSURANCE, JournalCardId.MAINTENANCE), rows[1].cards)
        assertTrue(rows[1].paired)
        assertEquals(listOf(JournalCardId.COST), rows[2].cards)
    }

    @Test
    fun `半屏卡不相邻时各占一整行`() {
        // 中间被整行卡隔开：各自单独一行（渲染时撑成整行），不硬凑成一对
        val rows = JournalCardLayout.rows(
            listOf(JournalCardId.INSURANCE, JournalCardId.GOAL, JournalCardId.MAINTENANCE),
        )
        assertEquals(3, rows.size)
        assertTrue(rows.none { it.paired })
    }

    @Test
    fun `落单的半屏卡不会和后面的半屏卡跨行配对`() {
        val rows = JournalCardLayout.rows(listOf(JournalCardId.MAINTENANCE))
        assertEquals(1, rows.size)
        assertEquals(listOf(JournalCardId.MAINTENANCE), rows[0].cards)
    }

    @Test
    fun `摊平能还原一维顺序`() {
        val order = listOf(
            JournalCardId.INSURANCE,
            JournalCardId.MAINTENANCE,
            JournalCardId.GOAL,
            JournalCardId.COST,
            JournalCardId.FOOTPRINT,
        )
        assertEquals(order, JournalCardLayout.flatten(JournalCardLayout.rows(order)))
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
    fun `删掉保险再添加回来会插在保养左边`() {
        // 只删保险：保养落单占一整行；再添加保险应该回到它左边，而不是排到最后
        val afterDelete = listOf(JournalCardId.GOAL, JournalCardId.MAINTENANCE, JournalCardId.COST)
        val restored = JournalCardLayout.withAdded(afterDelete, JournalCardId.INSURANCE)
        assertEquals(
            listOf(JournalCardId.GOAL, JournalCardId.INSURANCE, JournalCardId.MAINTENANCE, JournalCardId.COST),
            restored,
        )
        // 且这两张确实并成了一行
        assertEquals(
            listOf(JournalCardId.INSURANCE, JournalCardId.MAINTENANCE),
            JournalCardLayout.rows(restored)[1].cards,
        )
    }

    @Test
    fun `没有落单半屏卡时新增卡片追加到末尾`() {
        val order = listOf(JournalCardId.GOAL, JournalCardId.COST)
        assertEquals(order + JournalCardId.FOOTPRINT, JournalCardLayout.withAdded(order, JournalCardId.FOOTPRINT))
        // 两个半屏卡都在时再加一张半屏卡（理论上不会发生）也只能追加
        val full = listOf(JournalCardId.INSURANCE, JournalCardId.MAINTENANCE)
        assertEquals(full, JournalCardLayout.withAdded(full, JournalCardId.INSURANCE).take(2))
    }

    @Test
    fun `空列表里添加卡片`() {
        assertEquals(
            listOf(JournalCardId.COST),
            JournalCardLayout.withAdded(emptyList(), JournalCardId.COST),
        )
    }
}
