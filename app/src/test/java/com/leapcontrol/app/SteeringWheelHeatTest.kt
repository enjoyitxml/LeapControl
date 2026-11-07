package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 方向盘加热（cmdid=320）：档位语义、指令体、状态读取。
 *
 * 这一组守的是 2026-11-07 的两次用户反馈：
 *  1. 「按了没有用，打不开」—— 以前点「开」发的是 `1`（弱档），部分车型不上热；
 *  2. 「一直显示是打开的，怎么都关不掉」—— v1.1.5 把 `1` 当成关，回读还开着就补发 `1`，
 *     结果把刚关掉的加热重新打开（弱档）。
 *
 * 结论：`0` 是唯一的关，`1` 只是弱档，`2` 是开。关的路径上不许出现 `1`。
 */
class SteeringWheelHeatTest {

    private fun state(cmd: ControlCommand): JSONObject = JSONObject(cmd.stateJson)

    /** 从测试运行目录往上找到仓库根（含 app/ 的那一层）。 */
    private fun projectDir(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app").isDirectory }

    // ---------------- 指令体 ----------------

    @Test
    fun `开是 level 2，不是 1`() {
        val on = SteeringWheelHeat.build(on = true)
        assertEquals("320", on.cmdid)
        assertEquals("2", state(on).getString("level"))
        assertEquals("开启方向盘加热", on.label)
    }

    @Test
    fun `关只发 0，没有第二个候选值`() {
        val off = SteeringWheelHeat.build(on = false)
        assertEquals("320", off.cmdid)
        assertEquals("0", state(off).getString("level"))
        assertEquals("关闭方向盘加热", off.label)
        // 守门：v1.1.5 那个「补发 1」的出口必须不存在，别再长回来
        assertTrue(
            "SteeringWheelHeat 不该再有 buildOffAlt（拿弱档当关 = 把加热打开）",
            SteeringWheelHeat::class.java.declaredMethods.none { it.name == "buildOffAlt" },
        )
    }

    // ---------------- 命令名 ----------------

    @Test
    fun `开关命令名可解析`() {
        assertEquals(true, SteeringWheelHeat.targetOf(SteeringWheelHeat.ON))
        assertEquals(false, SteeringWheelHeat.targetOf(SteeringWheelHeat.OFF))
    }

    /** 老命令名必须还能用：`_0` 关，`_1`（弱档）/ `_2`（强档）都是开。 */
    @Test
    fun `老命令名 _1 与 _2 都按开处理`() {
        assertEquals(false, SteeringWheelHeat.targetOf("steeringWheelHeating_0"))
        assertEquals(true, SteeringWheelHeat.targetOf("steeringWheelHeating_1"))
        assertEquals(true, SteeringWheelHeat.targetOf("steeringWheelHeating_2"))
        assertNull(SteeringWheelHeat.targetOf("steeringWheelHeating_fast"))
        assertNull(SteeringWheelHeat.targetOf("driverSeatHeating_1"))
        assertNull(SteeringWheelHeat.targetOf(null))
    }

    @Test
    fun `Commands 走同一套语义`() {
        assertEquals("2", state(Commands.build(SteeringWheelHeat.ON)).getString("level"))
        assertEquals("2", state(Commands.build("steeringWheelHeating_1")).getString("level"))
        assertEquals("0", state(Commands.build(SteeringWheelHeat.OFF)).getString("level"))
        assertEquals("0", state(Commands.build("steeringWheelHeating_0")).getString("level"))
        assertEquals("2", state(Commands.buildSteeringWheelHeating(2)).getString("level"))
        assertEquals("0", state(Commands.buildSteeringWheelHeating(0)).getString("level"))
    }

    /**
     * 关闭路径的源码守门。
     *
     * `runSteeringWheelHeat` 与「一键关闭耗电」都会在回读仍开着时再发一条 ——
     * 那一条只能是 `0`。2026-11-07 v1.1.5 发成了 `1`，用户的车就从「关不掉」变成「关不掉
     * 还一直热着」。
     */
    @Test
    fun `关闭的补发只能是 0，不许把弱档当关`() {
        val source = File(
            projectDir(),
            "app/src/main/java/com/leapcontrol/app/ui/AppRuntime.kt",
        ).readText()
        assertFalse("AppRuntime 里不该再出现 buildOffAlt", source.contains("buildOffAlt"))
        // 两处关闭路径都必须用 build(on = false)
        assertEquals(
            "关闭路径应有两处重发 0（单条控制 + 一键关闭）",
            2,
            Regex("""SteeringWheelHeat\.build\(on = false\)""").findAll(source).count(),
        )
    }

    // ---------------- 车况读取 ----------------

    @Test
    fun `1816 有值就以它为准，1624 只在缺失时兜底`() {
        assertEquals(0, SteeringWheelHeat.levelOf(level = 0, minutes = 0))
        assertEquals(1, SteeringWheelHeat.levelOf(level = 1, minutes = 0))
        assertEquals(2, SteeringWheelHeat.levelOf(level = 2, minutes = 0))
        // 只报剩余分钟数的车型：>0 就是开着
        assertEquals(2, SteeringWheelHeat.levelOf(level = null, minutes = 15))
        assertNull(SteeringWheelHeat.levelOf(level = null, minutes = 0))
        assertNull(SteeringWheelHeat.levelOf(level = null, minutes = null))
        // 1816 说关就是关：再拿 1624 的残留值翻回「开」，界面会永远关不掉
        assertEquals(0, SteeringWheelHeat.levelOf(level = 0, minutes = 8))
    }

    @Test
    fun `开关判定只看是否大于零`() {
        assertFalse(SteeringWheelHeat.isOn(0))
        assertTrue(SteeringWheelHeat.isOn(1))
        assertTrue(SteeringWheelHeat.isOn(2))
        assertFalse(SteeringWheelHeat.isOn(null))
    }
}
