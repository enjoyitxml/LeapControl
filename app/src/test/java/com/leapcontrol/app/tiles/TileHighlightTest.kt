package com.leapcontrol.app.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 磁贴高亮规则（用户 2026-11-07 定稿）。
 *
 * 一句话：**高亮要能说明操作结果**，不是一段与结果无关的定时动画。
 *
 * - 瞬时动作（上锁、鸣笛寻车）：点下去当帧亮（= 正在发），网关受理后立刻灭；
 * - 开关型（解锁、空调、后备箱、车窗、电池预热、遮阳帘）：亮 = 功能开着。
 *   灭着点 → 当帧亮并保持；亮着点 → 当帧灭并下发关闭指令。
 *
 * 2026-11-05 那版是「点下去先亮满 2.2 / 2.5 / 10 秒再复位」，点了关闭还要亮 2.5 秒、
 * 上锁亮 10 秒，用户完全看不出成没成 —— 这组用例就是防它回来。
 */
class TileHighlightTest {

    @Test
    fun `瞬时动作点下去先亮`() {
        assertTrue("上锁点下去要立刻亮", clickedActive(TileKind.MOMENTARY, turningOn = true))
        // 瞬时动作没有「反方向」，参数不该影响结果
        assertTrue(clickedActive(TileKind.MOMENTARY, turningOn = false))
    }

    @Test
    fun `瞬时动作不留常亮`() {
        assertFalse("上锁不该常亮", tileActive(TileKind.MOMENTARY, localOn = true))
        assertFalse("鸣笛不该常亮", tileActive(TileKind.MOMENTARY, localOn = false))
    }

    @Test
    fun `开关型点开启当帧亮、点关闭当帧灭`() {
        for (id in listOf("unlock", "ac", "trunk", "batteryPreheat", "sunshade")) {
            val spec = requireNotNull(QuickTiles.byId(id))
            assertEquals("$id 应该是开关型", TileKind.TOGGLE, spec.kind)
            assertTrue("$id 开启当帧要亮", clickedActive(spec.kind, turningOn = true))
            assertFalse("$id 关闭当帧要灭（不能等 2.5 秒）", clickedActive(spec.kind, turningOn = false))
        }
    }

    @Test
    fun `开关型常态跟着开合态`() {
        for (id in listOf("unlock", "ac", "trunk", "batteryPreheat", "sunshade")) {
            assertTrue("$id 开着应该亮", tileActive(TileKind.TOGGLE, localOn = true))
            assertFalse("$id 关着应该灭", tileActive(TileKind.TOGGLE, localOn = false))
        }
    }

    @Test
    fun `档位型车窗高亮等同于「有没有窗开着」`() {
        // 车窗是四档（关 → 微开 → 半开 → 全开），磁贴只表达开 / 关：
        // 只要不是全关就亮，点下去往下一档走。
        val window = requireNotNull(QuickTiles.byId("window"))
        assertEquals(TileKind.CYCLE, window.kind)
        assertTrue("开窗当帧要亮", clickedActive(window.kind, turningOn = true))
        assertFalse("关窗当帧要灭", clickedActive(window.kind, turningOn = false))
        assertTrue("窗开着就常亮", tileActive(TileKind.CYCLE, localOn = true))
        assertFalse("窗全关就不亮", tileActive(TileKind.CYCLE, localOn = false))
    }

    @Test
    fun `本地记过就按本地，没记过才用车况`() {
        // 本地记过：车况再说什么都不算（车况可能是几分钟前的）
        assertTrue(effectiveOn(TileKind.TOGGLE, localState = true, status = false))
        assertFalse(effectiveOn(TileKind.TOGGLE, localState = false, status = true))
        // 从没记过：用车况兜底，否则冷启动第一次点「解锁」会把已解锁的车再解一次
        assertTrue(effectiveOn(TileKind.TOGGLE, localState = null, status = true))
        assertFalse(effectiveOn(TileKind.TOGGLE, localState = null, status = null))
        // 瞬时动作没有开合态
        assertFalse(effectiveOn(TileKind.MOMENTARY, localState = true, status = true))
    }

    @Test
    fun `解锁与电池预热都能取反下发关闭指令`() {
        val unlock = requireNotNull(QuickTiles.byId("unlock"))
        assertEquals("unlock", unlock.commandFor(turningOn = true))
        assertEquals("lock", unlock.commandFor(turningOn = false))

        val heat = requireNotNull(QuickTiles.byId("batteryPreheat"))
        assertEquals("batteryPreheat", heat.commandFor(turningOn = true))
        assertEquals("batteryPreheatOff", heat.commandFor(turningOn = false))
    }

    @Test
    fun `车况映射把解锁取反`() {
        // 闭锁 = true 时，解锁磁贴应该是「关着」
        assertEquals(false, statusOn("unlock", com.leapcontrol.app.ui.CarStatus(
            soc = 50, range = null, electricRange = null, fuelRange = null, fuelSoc = null,
            isRangeExtender = false, totalMileage = null, interiorTemp = null,
            locked = true, acOn = null,
        )))
        // 读不到闭锁信号就是 null，不能当成「未锁」
        assertEquals(null, statusOn("unlock", null))
    }
}
