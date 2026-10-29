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
        assertEquals(HomeCardId.COMPOSITION, default[3])
        assertEquals(HomeCardId.RANK, default[4])
        assertEquals(HomeCardId.OWNERSHIP, default.last())
    }

    @Test
    fun `发送到车机卡默认紧跟空调条`() {
        // 出发前的高频动作，不该埋到需要下滑两三屏的位置
        assertEquals(HomeCardId.AC, HomeCardLayout.DEFAULT[0])
        assertEquals(HomeCardId.DESTINATION, HomeCardLayout.DEFAULT[1])
    }

    @Test
    fun `拖动排序向下`() {
        val cards = HomeCardLayout.DEFAULT
        // 第 0 张（空调）拖到第 3 位
        val moved = HomeCardLayout.move(cards, 0, 3)
        assertEquals(
            listOf(
                HomeCardId.DESTINATION,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.AC,
                HomeCardId.RANK,
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
                HomeCardId.RANK,
                HomeCardId.DESTINATION,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.HEALTH,
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
        // 顺序跟着枚举声明走（「添加卡片」里的排列），不是跟着 DEFAULT 走
        assertEquals(
            listOf(
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.HEALTH,
                HomeCardId.OWNERSHIP,
                HomeCardId.DESTINATION,
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
        // v1 时期保存的列表里没有 HEALTH（v2 新增）与 DESTINATION（v4 新增）
        val parsed = HomeCardLayout.parse(
            "AC,TIRE_TRIP,COMPOSITION,RANK,OWNERSHIP",
            savedVersion = 1,
        )
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.DESTINATION,
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
        // 用户把相伴卡挪到了最前面，新卡仍应跟在自己的前一张（HEALTH 跟 RANK）后面
        val parsed = HomeCardLayout.parse("OWNERSHIP,RANK,AC", savedVersion = 1)
        assertEquals(
            listOf(
                HomeCardId.OWNERSHIP,
                HomeCardId.RANK,
                HomeCardId.HEALTH,
                HomeCardId.AC,
                HomeCardId.DESTINATION,
            ),
            parsed,
        )
    }

    @Test
    fun `前面的卡全被删掉时新卡放最前`() {
        val parsed = HomeCardLayout.parse("OWNERSHIP", savedVersion = 1)
        assertEquals(
            listOf(HomeCardId.DESTINATION, HomeCardId.HEALTH, HomeCardId.OWNERSHIP),
            parsed,
        )
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
        assertEquals(
            listOf(HomeCardId.DESTINATION, HomeCardId.HEALTH),
            HomeCardLayout.parse("", savedVersion = 1),
        )
    }

    @Test
    fun `停在 v3 的设备也能补回发送到车机卡`() {
        // v3 加过这张卡又撤掉过一版，存过 v3 的设备不会走更早的补入逻辑，
        // 所以它在 v4 重新登记：只补这一张，别把用户删掉的其它卡带回来。
        val parsed = HomeCardLayout.parse(
            "AC,TIRE_TRIP,COMPOSITION,RANK,HEALTH,OWNERSHIP",
            savedVersion = 3,
        )
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.DESTINATION,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.HEALTH,
                HomeCardId.OWNERSHIP,
            ),
            parsed,
        )
    }

    // ---- 零记总开关：保险 / 保养这两张卡的数据来自零记 ----

    @Test
    fun `零记关闭时首页不显示保险与保养卡`() {
        val all = listOf(
            HomeCardId.AC,
            HomeCardId.INSURANCE,
            HomeCardId.RANK,
            HomeCardId.MAINTENANCE,
            HomeCardId.OWNERSHIP,
        )
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.RANK, HomeCardId.OWNERSHIP),
            HomeCardLayout.visible(all, journalEnabled = false),
        )
        // 开着的时候一张都不许少
        assertEquals(all, HomeCardLayout.visible(all, journalEnabled = true))
    }

    @Test
    fun `零记关闭时添加卡片里不出现那两张卡`() {
        val shown = listOf(HomeCardId.AC)
        val off = HomeCardLayout.hidden(shown, journalEnabled = false)
        assertTrue(HomeCardId.INSURANCE !in off)
        assertTrue(HomeCardId.MAINTENANCE !in off)
        // 其它卡照旧可以加
        assertTrue(HomeCardId.RANK in off)

        val on = HomeCardLayout.hidden(shown, journalEnabled = true)
        assertTrue(HomeCardId.INSURANCE in on)
    }

    @Test
    fun `零记关闭期间编辑首页，保存时把两张卡插回原序号`() {
        val stored = listOf(
            HomeCardId.AC,
            HomeCardId.INSURANCE,
            HomeCardId.RANK,
            HomeCardId.MAINTENANCE,
            HomeCardId.OWNERSHIP,
        )
        // 关着零记时用户把「近 6 周能耗」拖到了最前
        val edited = listOf(
            HomeCardId.RANK,
            HomeCardId.AC,
            HomeCardId.OWNERSHIP,
        )
        assertEquals(
            listOf(
                HomeCardId.RANK,
                HomeCardId.INSURANCE,
                HomeCardId.AC,
                HomeCardId.MAINTENANCE,
                HomeCardId.OWNERSHIP,
            ),
            HomeCardLayout.restore(edited, stored),
        )
        // 什么都没动时，插回来必须和原样一模一样
        val untouched = listOf(HomeCardId.AC, HomeCardId.RANK, HomeCardId.OWNERSHIP)
        assertEquals(stored, HomeCardLayout.restore(untouched, stored))
    }

    @Test
    fun `关零记之前就删掉的卡不会因为重新开启而跑回来`() {
        // 用户早先把保养卡删了，只留了保险卡
        val stored = listOf(HomeCardId.AC, HomeCardId.INSURANCE)
        val edited = listOf(HomeCardId.AC)
        val restored = HomeCardLayout.restore(edited, stored)
        assertEquals(listOf(HomeCardId.AC, HomeCardId.INSURANCE), restored)
        assertTrue(HomeCardId.MAINTENANCE !in restored)
    }

    @Test
    fun `零记卡全都不在原位时插到末尾，不吞掉用户的其它卡`() {
        val stored = listOf(HomeCardId.INSURANCE, HomeCardId.AC)
        // 用户把 AC 也删了，只剩 RANK
        val restored = HomeCardLayout.restore(listOf(HomeCardId.RANK), stored)
        assertEquals(listOf(HomeCardId.INSURANCE, HomeCardId.RANK), restored)
    }
}
