package com.leapcontrol.app

import com.leapcontrol.app.tiles.QuickTiles
import com.leapcontrol.app.tiles.TileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 磁贴文案口径的回归测试。
 *
 * 这套字被反馈过两次（「上锁已开启」既不是动作也不是状态），改文案时很容易顺手改出新的病句，
 * 所以把它钉在测试里：
 * - 执行中说的是**车正在发生什么**（车辆上锁中…）；
 * - 成功后说的是**哪件事成了 + 执行成功**（车辆已上锁，执行成功）；
 * - 开关型按方向分两句，不会出现「关空调时副标题写开启中」。
 */
class TileCopyTest {

    private fun spec(id: String) = requireNotNull(QuickTiles.byId(id)) { "磁贴定义缺失：$id" }

    @Test
    fun `一次性动作按方向无关的一句走`() {
        val lock = spec("lock")
        assertEquals(TileKind.MOMENTARY, lock.kind)
        assertEquals("车辆上锁中…", lock.doingText(turningOn = true))
        // 一次性动作没有「关」这一路，方向参数不该影响文案
        assertEquals("车辆上锁中…", lock.doingText(turningOn = false))
        assertEquals("车辆已上锁", lock.doneText(turningOn = true))
    }

    @Test
    fun `解锁与上锁是两句不同的话`() {
        assertEquals("车辆解锁中…", spec("unlock").doingText(turningOn = true))
        assertEquals("车辆已解锁", spec("unlock").doneText(turningOn = true))
    }

    @Test
    fun `开关型按方向分文案`() {
        val ac = spec("ac")
        assertEquals(TileKind.TOGGLE, ac.kind)
        assertEquals("空调开启中…", ac.doingText(turningOn = true))
        assertEquals("空调关闭中…", ac.doingText(turningOn = false))
        assertEquals("空调已开启", ac.doneText(turningOn = true))
        assertEquals("空调已关闭", ac.doneText(turningOn = false))
    }

    @Test
    fun `每块磁贴的执行中文案都说清了是哪件事`() {
        // 「执行中…」这种什么都不说的兜底不该出现在真实定义里
        QuickTiles.ALL.forEach { spec ->
            val text = spec.doingText(turningOn = true)
            assertTrue("${spec.id} 的执行中文案太笼统：$text", text.length > 3)
            assertTrue("${spec.id} 的执行中文案缺省略号：$text", text.endsWith("…"))
        }
    }

    @Test
    fun `成功后都有完成态可拼`() {
        QuickTiles.ALL.forEach { spec ->
            assertTrue("${spec.id} 缺完成态文案", spec.doneText(turningOn = true).isNotBlank())
            if (spec.kind == TileKind.TOGGLE) {
                assertTrue("${spec.id} 缺关闭那一路的完成态", spec.doneText(turningOn = false).isNotBlank())
            }
        }
    }
}
