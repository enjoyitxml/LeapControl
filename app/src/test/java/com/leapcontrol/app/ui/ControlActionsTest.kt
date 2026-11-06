package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页快捷控车按钮的高亮与点击方向（用户 2026-11-05 定稿）：
 *
 * - 解锁 / 后备箱 / 车窗 / 电池预热 / 遮阳帘 / 哨兵：功能「开着」就高亮；
 * - 上锁 / 鸣笛：单次动作，永远不高亮；
 * - 高亮时点击 = 关闭，熄灭时点击 = 开启。
 */
class ControlActionsTest {

    @Test
    fun `解锁在车辆已解锁时高亮`() {
        assertEquals(true, ControlActions.isActive("unlock", status(locked = false)))
        assertEquals(false, ControlActions.isActive("unlock", status(locked = true)))
        // 读不到闭锁信号：不高亮，也不猜成「已解锁」
        assertNull(ControlActions.isActive("unlock", status(locked = null)))
    }

    @Test
    fun `上锁和鸣笛永远不高亮`() {
        for (locked in listOf(true, false, null)) {
            assertNull(ControlActions.isActive("lock", status(locked = locked)))
            assertNull(ControlActions.isActive("horn", status(locked = locked)))
        }
    }

    @Test
    fun `开关类按钮跟着车况亮灭`() {
        assertEquals(true, ControlActions.isActive("trunk", status(trunkOpen = true)))
        assertEquals(false, ControlActions.isActive("trunk", status(trunkOpen = false)))
        assertEquals(true, ControlActions.isActive("window", status(windowOpen = true)))
        assertEquals(true, ControlActions.isActive("sunshade", status(sunshadeOpen = true)))
        assertEquals(true, ControlActions.isActive("sentry", status(sentryOn = true)))
        assertEquals(false, ControlActions.isActive("sentry", status(sentryOn = false)))
    }

    @Test
    fun `电池预热跟着加热信号亮灭`() {
        assertEquals(true, ControlActions.isActive("batteryPreheat", status(preheat = true)))
        assertEquals(false, ControlActions.isActive("batteryPreheat", status(preheat = false)))
        assertNull(ControlActions.isActive("batteryPreheat", status(preheat = null)))
    }

    @Test
    fun `预热高亮时点击下发关闭指令`() {
        assertEquals("batteryPreheatOff", ControlActions.resolveCommand("batteryPreheat", status(preheat = true)))
        assertEquals("batteryPreheat", ControlActions.resolveCommand("batteryPreheat", status(preheat = false)))
        // 读不到状态时保守地当「开」，与其余复合按钮一致
        assertEquals("batteryPreheat", ControlActions.resolveCommand("batteryPreheat", status(preheat = null)))
    }

    // ---- 二次确认弹窗：必须写明「打开 / 关闭 X」，而不是按钮上的功能名 ----

    @Test
    fun `遮阳帘的确认文案跟着车况说清开还是关`() {
        assertEquals("打开遮阳帘", ControlActions.confirmLabel("sunshade", status(sunshadeOpen = false)))
        assertEquals("关闭遮阳帘", ControlActions.confirmLabel("sunshade", status(sunshadeOpen = true)))
        // 车况读不到时保守地按「开」下发，文案也要跟着说「打开」
        assertEquals("打开遮阳帘", ControlActions.confirmLabel("sunshade", status()))
    }

    @Test
    fun `其它按钮的确认文案也是具体指令`() {
        assertEquals("打开后备箱", ControlActions.confirmLabel("trunk", status()))
        assertEquals("关闭后备箱", ControlActions.confirmLabel("trunk", status(trunkOpen = true)))
        assertEquals("关闭车窗", ControlActions.confirmLabel("window", status(windowOpen = true)))
        assertEquals("开启电池预热", ControlActions.confirmLabel("batteryPreheat", status()))
        assertEquals("关闭电池预热", ControlActions.confirmLabel("batteryPreheat", status(preheat = true)))
        assertEquals("开启哨兵", ControlActions.confirmLabel("sentry", status()))
        assertEquals("关闭哨兵", ControlActions.confirmLabel("sentry", status(sentryOn = true)))
        assertEquals("打开天窗", ControlActions.confirmLabel("sunroof", status()))
        assertEquals("关闭天窗", ControlActions.confirmLabel("sunroof", status(sunroofOpen = true)))
        assertEquals("打开前备箱", ControlActions.confirmLabel("frunk", status()))
        assertEquals("关闭前备箱", ControlActions.confirmLabel("frunk", status(frunkOpen = true)))
        assertEquals("上锁车辆", ControlActions.confirmLabel("lock", status()))
        assertEquals("解锁车辆", ControlActions.confirmLabel("unlock", status()))
        assertEquals("鸣笛寻车", ControlActions.confirmLabel("horn", status()))
    }

    @Test
    fun `弹窗说的与实际下发的永远是同一条指令`() {
        val allOn = status(
            locked = false,
            trunkOpen = true,
            windowOpen = true,
            sunshadeOpen = true,
            sunroofOpen = true,
            frunkOpen = true,
            sentryOn = true,
            preheat = true,
        )
        ControlActions.all.forEach { action ->
            listOf(null, status(), allOn).forEach { st ->
                val command = ControlActions.resolveCommand(action.id, st)
                // HomeScreen 点击时解析一次，文案与下发共用这个 command
                assertEquals(ControlActions.commandLabel(command), ControlActions.confirmLabel(action.id, st))
                // 开/关一体的按钮若只报功能名，用户根本不知道这一下是开还是关
                if (action.toggle || action.id == "batteryPreheat") {
                    assertNotEquals(
                        "${action.id} 的确认文案退回成了功能名",
                        action.label,
                        ControlActions.commandLabel(command),
                    )
                }
            }
        }
    }

    @Test
    fun `所有可解析出的指令都有中文动作文案且不露内部指令名`() {
        val commands = mutableSetOf<String>()
        val allOn = status(
            locked = false,
            trunkOpen = true,
            windowOpen = true,
            sunshadeOpen = true,
            sunroofOpen = true,
            frunkOpen = true,
            sentryOn = true,
            preheat = true,
        )
        ControlActions.all.forEach { action ->
            listOf(null, status(), allOn).forEach { st ->
                commands += ControlActions.resolveCommand(action.id, st)
            }
        }
        assertTrue(commands.size >= 17)
        commands.forEach { cmd ->
            val label = ControlActions.commandLabel(cmd)
            assertNotEquals("指令 $cmd 落到了兜底文案", "控车", label)
            assertFalse("指令 $cmd 的文案露出了内部名：$label", label.any { it in 'A'..'Z' || it in 'a'..'z' })
        }
    }

    private fun status(
        locked: Boolean? = null,
        trunkOpen: Boolean? = null,
        windowOpen: Boolean? = null,
        sunshadeOpen: Boolean? = null,
        sunroofOpen: Boolean? = null,
        frunkOpen: Boolean? = null,
        sentryOn: Boolean? = null,
        preheat: Boolean? = null,
    ) = CarStatus(
        soc = 80,
        range = null,
        electricRange = null,
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = null,
        interiorTemp = null,
        locked = locked,
        acOn = null,
        trunkOpen = trunkOpen,
        windowOpen = windowOpen,
        sunshadeOpen = sunshadeOpen,
        sunroofOpen = sunroofOpen,
        frunkOpen = frunkOpen,
        sentryOn = sentryOn,
        batteryPreheatOn = preheat,
    )
}
