package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App 内控车提示的文案口径与「该顶替哪一行」。
 *
 * 背景：2026-11-05 用户反馈「App 里控车之后右上角还写着车辆已上锁」——
 * 车端执行要 5~15 秒，那段窗口必须由这次控车的结果顶着，否则看着像没点上。
 * 这里钉住三件事：说清在做什么、成功才说成功、提示顶掉对应那一行。
 */
class ControlFeedbackTest {

    @Test
    fun `执行中说清是哪件事，不用已开启这种混句`() {
        assertEquals("车辆上锁中…", ControlFeedback.runningText("lock"))
        assertEquals("车辆解锁中…", ControlFeedback.runningText("unlock"))
        assertEquals("后备箱开启中…", ControlFeedback.runningText("trunkOpen"))
        assertEquals("空调关闭中…", ControlFeedback.runningText("acOff"))
        val all = listOf("lock", "unlock", "acOn", "acOff", "trunkOpen", "trunkClose", "horn")
        all.forEach { command ->
            val text = requireNotNull(ControlFeedback.runningText(command)) { command }
            // 「上锁已开启」那类混句的根源就是没有省略号、也没有动作词
            assertTrue("$command 缺少省略号: $text", text.endsWith("…"))
            assertFalse("$command 混进了状态词: $text", text.contains("已开启"))
        }
    }

    @Test
    fun `不在映射表里的指令不冒充车况提示`() {
        assertNull(ControlFeedback.runningText("acSetting:temperature:24"))
        assertNull(ControlFeedback.runningText("driverSeatHeating_2"))
    }

    @Test
    fun `只有车端确认才说完成式`() {
        assertEquals("车辆已上锁", ControlFeedback.doneText("lock", confirmed = true))
        assertEquals("上锁已下发", ControlFeedback.doneText("lock", confirmed = false))
        assertEquals("空调已开启", ControlFeedback.doneText("acOn", confirmed = true))
        assertEquals("开空调已下发", ControlFeedback.doneText("acOn", confirmed = false))
    }

    @Test
    fun `没等到回执的文案不能说成已经成了`() {
        assertTrue(ControlFeedback.isConfirmed("lock", "上锁执行成功"))
        assertTrue(ControlFeedback.isConfirmed("acOn", "空调已开启"))
        assertFalse(ControlFeedback.isConfirmed("lock", "指令已经发出，车辆还没回话，请稍后在车上确认"))
        assertFalse(ControlFeedback.isConfirmed("unlock", "解锁已下发"))
    }

    @Test
    fun `车况翻成意图状态才算跟上`() {
        val locked = status(locked = true)
        val unlocked = status(locked = false)
        assertEquals(true, ControlFeedback.reached("unlock", unlocked))
        assertEquals(false, ControlFeedback.reached("unlock", locked))
        assertEquals(true, ControlFeedback.reached("lock", locked))
        // 读不到这个信号 → null，不能当「没生效」
        assertNull(ControlFeedback.reached("trunkOpen", status(trunkOpen = null)))
        // 鸣笛没有可回读的状态
        assertNull(ControlFeedback.reached("horn", locked))
    }

    @Test
    fun `控车提示顶掉同一项而不是并排两行`() {
        val rows = vehicleStatusRows(
            locked = true,
            acOn = true,
            windowOpen = false,
            trunkOpen = false,
            sunshadeOpen = false,
        )
        assertEquals(listOf("车辆已上锁", "空调已开启"), rows.map { it.label })

        val unlocked = withControlFlash(
            rows,
            ControlFeedback.Flash("车辆已解锁", ControlFeedback.Phase.DONE, "unlock"),
        )
        // 第一行换成结果，空调那行不动，不会同时出现「已上锁」和「已解锁」
        assertEquals(listOf("车辆已解锁", "空调已开启"), unlocked.map { it.label })
        assertEquals(ControlFeedback.TOPIC_LOCK, unlocked.first().topic)
    }

    @Test
    fun `车况里还没有这一项时追加到末尾`() {
        val rows = vehicleStatusRows(
            locked = true,
            acOn = false,
            windowOpen = false,
            trunkOpen = false,
            sunshadeOpen = false,
        )
        val flashing = withControlFlash(
            rows,
            ControlFeedback.Flash("空调开启中…", ControlFeedback.Phase.RUNNING, "acOn"),
        )
        assertEquals(listOf("车辆已上锁", "空调开启中…"), flashing.map { it.label })
        assertTrue(flashing.last().accent)
    }

    @Test
    fun `失败的提示用警示色且不冒充车况`() {
        val rows = vehicleStatusRows(true, null, null, null, null)
        val failed = withControlFlash(
            rows,
            ControlFeedback.Flash("解锁失败", ControlFeedback.Phase.FAILED, "unlock"),
        )
        assertTrue(failed.first().alert)
        assertEquals("执行中/完成才提亮", false, failed.first().accent)
    }

    @Test
    fun `没有提示时原样返回`() {
        val rows = vehicleStatusRows(true, null, null, null, null)
        assertEquals(rows, withControlFlash(rows, null))
    }

    private fun status(
        locked: Boolean? = null,
        acOn: Boolean? = null,
        trunkOpen: Boolean? = null,
    ) = CarStatus(
        soc = 60,
        range = null,
        electricRange = null,
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = null,
        interiorTemp = null,
        locked = locked,
        acOn = acOn,
        trunkOpen = trunkOpen,
    )
}
