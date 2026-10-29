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
    fun `保养与保险卡是可选卡，不自动塞进首页`() {
        // 这两张卡没设置计划时只是引导卡，默认顺序里不该有它们；
        // 老用户列表（savedVersion 偏低）走补入逻辑时也一样不会被塞进去。
        assertTrue(HomeCardId.INSURANCE !in HomeCardLayout.DEFAULT)
        assertTrue(HomeCardId.MAINTENANCE !in HomeCardLayout.DEFAULT)
        val old = listOf(HomeCardId.AC, HomeCardId.RANK)
        assertEquals(old, HomeCardLayout.parse("AC,RANK", savedVersion = HomeCardLayout.VERSION))
        // 低版本列表触发补入逻辑时，也只会补协议里登记过的卡，不会补这两张
        val upgraded = HomeCardLayout.parse("AC,RANK", savedVersion = 1)
        assertTrue(HomeCardId.INSURANCE !in upgraded)
        assertTrue(HomeCardId.MAINTENANCE !in upgraded)
        // 但它们必须能从「添加卡片」里被加回来
        assertTrue(HomeCardId.INSURANCE in HomeCardLayout.hidden(old))
        assertTrue(HomeCardId.MAINTENANCE in HomeCardLayout.hidden(old))
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
                HomeCardId.HEALTH,
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
                HomeCardId.HEALTH,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.OWNERSHIP,
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
            listOf(
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.HEALTH,
                HomeCardId.OWNERSHIP,
                // 保养 / 保险是「可选卡」：默认不进 DEFAULT，因此总在「添加卡片」里
                HomeCardId.INSURANCE,
                HomeCardId.MAINTENANCE,
            ),
            hidden,
        )
    }

    // ---- 新增卡片的升级补入 ----

    @Test
    fun `老用户保存过顺序时按默认位置补入新卡`() {
        // v1 时期保存的列表里没有 HEALTH
        val parsed = HomeCardLayout.parse(
            "AC,TIRE_TRIP,COMPOSITION,RANK,OWNERSHIP",
            savedVersion = 1,
        )
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.HEALTH,
                HomeCardId.OWNERSHIP,
            ),
            parsed,
        )
    }

    @Test
    fun `新卡插在默认顺序里前一张卡的后面`() {
        // 用户把相伴卡挪到了最前面，新卡仍应跟在自己的前一张（RANK）后面
        val parsed = HomeCardLayout.parse("OWNERSHIP,RANK,AC", savedVersion = 1)
        assertEquals(listOf(HomeCardId.OWNERSHIP, HomeCardId.RANK, HomeCardId.HEALTH, HomeCardId.AC), parsed)
    }

    @Test
    fun `前面的卡全被删掉时新卡放最前`() {
        val parsed = HomeCardLayout.parse("OWNERSHIP", savedVersion = 1)
        assertEquals(listOf(HomeCardId.HEALTH, HomeCardId.OWNERSHIP), parsed)
    }

    @Test
    fun `版本已是最新时不会把用户删掉的卡变回来`() {
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.RANK),
            HomeCardLayout.parse("AC,RANK", savedVersion = HomeCardLayout.VERSION),
        )
    }

    @Test
    fun `卡片全删但版本落后时补入新卡`() {
        assertEquals(listOf(HomeCardId.HEALTH), HomeCardLayout.parse("", savedVersion = 1))
    }
}
