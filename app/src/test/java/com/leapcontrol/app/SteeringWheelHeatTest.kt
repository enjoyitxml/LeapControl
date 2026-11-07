package com.leapcontrol.app

import com.leapcontrol.app.ui.ComfortOptimistic
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 方向盘加热（cmdid=320）：档位语义、指令体、状态读取，以及**下发链路**的源码守门。
 *
 * 2026-11-07 三次反馈之后的定稿，全部对齐上游 leapauto v4.3.6：
 *  1. 「按了没有用，打不开」—— 以前点「开」发的是 `1`（弱档），部分车型不上热；
 *  2. 「一直显示打开，怎么都关不掉」—— v1.1.5 把 `1` 当关做补发，等于把加热重新打开；
 *  3. v1.1.6 补发改成 0 但**仍在补发**，而且「关」的分支提前 return，
 *     跳过了 `rememberComfortTarget`：界面没有乐观值，车况一刷新就把旧值盖回来。
 *
 * 上游的做法是：一次点击只发一条（`2` 开 / `0` 关），下发前写乐观值、网关受理即成功，
 * 之后阶梯静默刷新车况；**不回读 1816 判成败、不补发、不拿 1624 判开关**。
 */
class SteeringWheelHeatTest {

    private fun state(cmd: ControlCommand): JSONObject = JSONObject(cmd.stateJson)

    /** 从测试运行目录往上找到仓库根（含 app/ 的那一层）。 */
    private fun projectDir(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app").isDirectory }

    private fun source(path: String): String = File(projectDir(), path).readText()

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

    // ---------------- 下发链路（照上游）----------------

    /**
     * 方向盘不能再有单开的下发函数：它会提前 return，跳过下面的 rememberComfortTarget，
     * 界面从点下去那一刻就没有乐观值 —— 这才是「怎么都关不掉」看到的那一层。
     */
    @Test
    fun `方向盘走通用 control 路径，不提前 return`() {
        val runtime = source("app/src/main/java/com/leapcontrol/app/ui/AppRuntime.kt")
        listOf(
            "runSteeringWheelHeat",
            "pollSteeringHeat",
            "readSteeringOn",
            "buildOffAlt",
            "STEERING_RETRY",
            "SteeringWheelHeat.targetOf(commandName)",
        ).forEach { forbidden ->
            assertFalse(
                "AppRuntime 里不该再出现 $forbidden（照上游：一次点击只发一条，走通用路径）",
                runtime.contains(forbidden),
            )
        }
        // 通用路径必须接上：指令名经过 Commands.build 落到 SteeringWheelHeat
        val models = source("app/src/main/java/com/leapcontrol/app/Models.kt")
        assertTrue(
            "Commands.build 必须把方向盘指令交给 SteeringWheelHeat",
            models.contains("SteeringWheelHeat.build(SteeringWheelHeat.targetOf(name) == true)"),
        )
    }

    /** 上游在发指令前就写乐观值；少了这一行，车况刷新会把滞后的旧档位盖回来。 */
    @Test
    fun `单条控制与一键关闭都要写乐观值`() {
        val runtime = source("app/src/main/java/com/leapcontrol/app/ui/AppRuntime.kt")
        assertEquals(
            "control() 与 controlForWidget() 各要写一次乐观值",
            2,
            Regex("""rememberComfortTarget\(commandName\)""").findAll(runtime).count(),
        )
        assertTrue(
            "一键关闭耗电也要写乐观值，否则「已关闭 N 项」后方向盘行还写着开",
            runtime.contains("rememberComfortTarget(name)"),
        )
        // 界面把「关」翻成 level 0 是这条链路的最后一环
        assertEquals(
            0,
            ComfortOptimistic.targetOf(SteeringWheelHeat.OFF, 1_000L)?.level,
        )
        assertEquals(
            2,
            ComfortOptimistic.targetOf(SteeringWheelHeat.ON, 1_000L)?.level,
        )
    }

    // ---------------- 车况读取 ----------------

    /** 上游只读 1816；1624 是剩余加热分钟数，关掉后常有残值，绝不能拿来判开关。 */
    @Test
    fun `状态只读 1816，不再拿 1624 兜底`() {
        assertEquals(0, SteeringWheelHeat.levelOf(0))
        assertEquals(1, SteeringWheelHeat.levelOf(1))
        assertEquals(2, SteeringWheelHeat.levelOf(2))
        assertNull(SteeringWheelHeat.levelOf(null))
        // 越界读数收敛到合法档位，不把异常值显示成「开」
        assertEquals(2, SteeringWheelHeat.levelOf(9))
        assertEquals(0, SteeringWheelHeat.levelOf(-3))
        // 守门：levelOf 只能有一个入参（第二个入参就是 1624 兜底又回来了）
        val levelOf = SteeringWheelHeat::class.java.declaredMethods.first { it.name == "levelOf" }
        assertEquals("levelOf 不该再有 minutes 入参", 1, levelOf.parameterTypes.size)
        val runtime = source("app/src/main/java/com/leapcontrol/app/ui/AppRuntime.kt")
        assertFalse(
            "车况解析不该再读 1624 判方向盘开关",
            runtime.contains("steeringWheelHeaterMinutes"),
        )
    }

    @Test
    fun `开关判定只看是否大于零`() {
        assertFalse(SteeringWheelHeat.isOn(0))
        assertTrue(SteeringWheelHeat.isOn(1))
        assertTrue(SteeringWheelHeat.isOn(2))
        assertFalse(SteeringWheelHeat.isOn(null))
    }
}
