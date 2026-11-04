package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleConfirmRulesTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `没点过时第一次点击不算确认`() {
        assertFalse(DoubleConfirmRules.isConfirm(null, 0L, "unlock", t0))
    }

    @Test
    fun `窗口内点同一个动作算确认`() {
        assertTrue(DoubleConfirmRules.isConfirm("unlock", t0, "unlock", t0 + 1_000L))
        // 边界：整 3 秒仍算确认
        assertTrue(
            DoubleConfirmRules.isConfirm("unlock", t0, "unlock", t0 + DoubleConfirmRules.WINDOW_MS),
        )
    }

    @Test
    fun `超过窗口就得重新点两下`() {
        assertFalse(
            DoubleConfirmRules.isConfirm("unlock", t0, "unlock", t0 + DoubleConfirmRules.WINDOW_MS + 1),
        )
    }

    @Test
    fun `窗口内点了另一个动作不算确认`() {
        // 上一次点的是解锁，这一次点开后备箱：不能被当成「确认开后备箱」
        assertFalse(DoubleConfirmRules.isConfirm("unlock", t0, "trunkOpen", t0 + 500L))
    }

    @Test
    fun `时钟回拨时不放行`() {
        // now 比上一次点击还早：宁可多问一次，也不能凭空放行一条指令
        assertFalse(DoubleConfirmRules.isConfirm("unlock", t0, "unlock", t0 - 1_000L))
    }

    @Test
    fun `每个动作的指令名唯一且有中文标题`() {
        val commands = ConfirmAction.entries.map { it.command }
        assertEquals(commands.size, commands.toSet().size)
        ConfirmAction.entries.forEach { action ->
            assertTrue("${action.name} 缺少指令名", action.command.isNotBlank())
            assertTrue("${action.name} 缺少标题", action.title.isNotBlank())
            assertTrue("${action.name} 缺少分组", action.group.isNotBlank())
        }
    }

    @Test
    fun `分组表覆盖全部动作且顺序稳定`() {
        val groups = ConfirmAction.groups()
        // 全部动作都在某个分组里，一个都不能漏（加动作时忘了分组会被这条挡住）
        assertEquals(ConfirmAction.entries.size, groups.sumOf { it.second.size })
        // 分组由枚举声明顺序决定，设置页直接照着渲染
        assertEquals(listOf("车门", "后备箱", "车窗", "空调", "遮阳帘", "其他"), groups.map { it.first })
    }

    @Test
    fun `指令名能反查回动作`() {
        ConfirmAction.entries.forEach { action ->
            assertEquals(action, ConfirmAction.of(action.command))
        }
        assertEquals(null, ConfirmAction.of("不存在的指令"))
        assertEquals(null, ConfirmAction.of(null))
    }

    @Test
    fun `小组件与磁贴用到的指令都已登记`() {
        // 这两处点击链路上真会下发的指令名，漏一个就会「勾了也不生效」
        val usedByEntries = listOf(
            "unlock", "lock", "horn",
            "acOn", "acOff",
            "trunkOpen", "trunkClose",
            "windowOpen", "windowClose",
            "sunshadeOpen", "sunshadeClose",
            "batteryPreheat",
        )
        usedByEntries.forEach { command ->
            assertNotNull("指令 $command 没有对应的确认动作", ConfirmAction.of(command))
        }
    }
}
