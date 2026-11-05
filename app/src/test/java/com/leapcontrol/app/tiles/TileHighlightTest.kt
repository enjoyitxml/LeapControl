package com.leapcontrol.app.tiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 磁贴高亮规则（2026-11-05 用户定稿）：
 *
 * - 上锁 / 解锁 / 鸣笛 / 预热：**发起时高亮**（由 TileService.render 的 `active` 传 true，
 *   不在这里管），执行成功后熄灭；
 * - 其中「解锁」在**车辆解锁状态下要一直亮**；
 * - 空调 / 后备箱 / 车窗 / 遮阳帘：功能开着就一直亮，亮着点=关，灭着点=开。
 */
class TileHighlightTest {

    @Test
    fun `开关型跟着开合态亮灭`() {
        for (id in listOf("ac", "trunk", "window", "sunshade")) {
            assertTrue("$id 开着应该亮", active(TileKind.TOGGLE, id, localOn = true))
            assertFalse("$id 关着应该灭", active(TileKind.TOGGLE, id, localOn = false))
        }
    }

    @Test
    fun `解锁在车辆已解锁时常亮`() {
        assertTrue(active(TileKind.MOMENTARY, "unlock", locked = false))
        assertFalse(active(TileKind.MOMENTARY, "unlock", locked = true))
    }

    @Test
    fun `刚控完就按结果亮，不等车端报文`() {
        // 解锁成功但报文还没跟上：仍然要亮
        assertTrue(active(TileKind.MOMENTARY, "unlock", unlockedHint = true, locked = true))
        // 上锁成功但报文还说已解锁：按结果熄灭
        assertFalse(active(TileKind.MOMENTARY, "unlock", unlockedHint = false, locked = false))
    }

    @Test
    fun `上锁 鸣笛 预热成功之后不常亮`() {
        for (id in listOf("lock", "horn", "batteryPreheat")) {
            assertFalse("$id 不该常亮", active(TileKind.MOMENTARY, id, locked = true))
            assertFalse("$id 不该常亮", active(TileKind.MOMENTARY, id, locked = false))
        }
    }

    @Test
    fun `读不到闭锁信号时不高亮，别拿读不到当未锁`() {
        assertFalse(active(TileKind.MOMENTARY, "unlock", locked = null))
    }

    private fun active(
        kind: TileKind,
        id: String,
        localOn: Boolean = false,
        unlockedHint: Boolean? = null,
        locked: Boolean? = null,
    ) = tileActive(kind, id, localOn, unlockedHint, locked)
}
