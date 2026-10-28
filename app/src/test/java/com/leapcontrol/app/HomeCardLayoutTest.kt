package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页卡片顺序 / 显隐（编辑态拖动、删除、添加的落盘逻辑）。 */
class HomeCardLayoutTest {

    @Test
    fun `从未保存过时用默认顺序`() {
        assertEquals(HomeCardLayout.DEFAULT, HomeCardLayout.parse(null))
    }

    @Test
    fun `空串表示用户把卡片全删了`() {
        assertTrue(HomeCardLayout.parse("").isEmpty())
        assertTrue(HomeCardLayout.parse("   ").isEmpty())
    }

    @Test
    fun `忽略非法与重复项并保序`() {
        val parsed = HomeCardLayout.parse("RANK,GHOST,AC,RANK,COMPOSITION")
        assertEquals(
            listOf(HomeCardId.RANK, HomeCardId.AC, HomeCardId.COMPOSITION),
            parsed,
        )
    }

    @Test
    fun `序列化往返一致`() {
        val cards = listOf(HomeCardId.OWNERSHIP, HomeCardId.TIRE_TRIP, HomeCardId.RANK)
        assertEquals(cards, HomeCardLayout.parse(HomeCardLayout.serialize(cards)))
    }

    @Test
    fun `默认顺序包含两张新能耗卡`() {
        val default = HomeCardLayout.DEFAULT
        assertEquals(HomeCardId.COMPOSITION, default[2])
        assertEquals(HomeCardId.RANK, default[3])
        assertEquals(HomeCardId.OWNERSHIP, default.last())
    }

    @Test
    fun `拖动排序向下`() {
        val cards = HomeCardLayout.DEFAULT
        // 第 0 张拖到第 3 位
        val moved = HomeCardLayout.move(cards, 0, 3)
        assertEquals(
            listOf(
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.AC,
                HomeCardId.OWNERSHIP,
            ),
            moved,
        )
        assertEquals(cards.size, moved.size)
    }

    @Test
    fun `拖动排序向上`() {
        val cards = HomeCardLayout.DEFAULT
        val moved = HomeCardLayout.move(cards, 4, 1)
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.OWNERSHIP,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
            ),
            moved,
        )
    }

    @Test
    fun `越界下标不会吞卡片`() {
        val cards = HomeCardLayout.DEFAULT
        assertEquals(cards, HomeCardLayout.move(cards, 9, 1))
        assertEquals(cards, HomeCardLayout.move(cards, 1, 1))
        // to 越界时夹到末尾，元素仍保留
        assertEquals(cards.size, HomeCardLayout.move(cards, 0, 99).size)
    }

    @Test
    fun `隐藏列表是当前没显示的那些`() {
        val shown = listOf(HomeCardId.AC, HomeCardId.RANK)
        val hidden = HomeCardLayout.hidden(shown)
        assertEquals(
            listOf(HomeCardId.TIRE_TRIP, HomeCardId.COMPOSITION, HomeCardId.OWNERSHIP),
            hidden,
        )
    }
}
