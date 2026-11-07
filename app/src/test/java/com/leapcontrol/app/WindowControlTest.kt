package com.leapcontrol.app

import com.leapcontrol.app.ui.ControlActions
import com.leapcontrol.app.ui.WindowControl
import com.leapcontrol.app.ui.WindowStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 车窗四档：**关 → 微开 → 半开 → 全开 → 关**。
 *
 * 用户反馈「记得以前是微开 / 全开 / 关闭三种状态切换，现在控车界面点起来不一致」，自己也拿不准
 * 是三档还是四档。代码与协议的结论是四档：指令表里早就有微开（230 / value 2）和半开（value 5），
 * 但界面从头到尾只发半开和关，全开（value 10）根本没有入口；
 * 提示、磁贴副标题、体检页又各说各的（「已开启」「已半开」「开窗通风」）。
 *
 * 这组用例钉住三件事：
 * 1. 四档的顺序与指令值（真车复核过的只有 0 / 2 / 5 / 10，`100` / `1` 车不动）；
 * 2. 档位怎么定（车况只能证明「全关」，分不出开了多大，所以靠本地记 + 车况校准）；
 * 3. 所有入口共用这一份实现（首页、磁贴、体检页），不允许再各写一套开/关。
 */
class WindowControlTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `四档按 微开 半开 全开 关 的顺序循环`() {
        // cmd 230 的刻度是 0~10，真车复核过能动的只有 0 / 2 / 5 / 10 —— 正好四档
        assertEquals(WindowControl.CMD_VENT, WindowControl.nextCommand(WindowStage.CLOSED))
        assertEquals(WindowStage.VENT, WindowControl.nextStage(WindowStage.CLOSED))

        assertEquals(WindowControl.CMD_HALF, WindowControl.nextCommand(WindowStage.VENT))
        assertEquals(WindowStage.HALF, WindowControl.nextStage(WindowStage.VENT))

        assertEquals(WindowControl.CMD_FULL, WindowControl.nextCommand(WindowStage.HALF))
        assertEquals(WindowStage.FULL, WindowControl.nextStage(WindowStage.HALF))

        assertEquals(WindowControl.CMD_CLOSE, WindowControl.nextCommand(WindowStage.FULL))
        assertEquals(WindowStage.CLOSED, WindowControl.nextStage(WindowStage.FULL))
    }

    @Test
    fun `车况说开着但没记录时，下一次点击是关`() {
        // 车里手动开的窗 / 换手机之后：不知道开多大，就不要瞎猜档位，
        // 用户看到窗开着，点一下的直觉是关掉。
        val stage = WindowControl.resolve(
            saved = null, savedAt = 0L, statusOpen = true, now = t0,
        )
        assertEquals(WindowStage.OPEN_UNKNOWN, stage)
        assertEquals(WindowControl.CMD_CLOSE, WindowControl.nextCommand(stage))
        assertEquals(WindowStage.CLOSED, WindowControl.nextStage(stage))
        // 说「已开启」，不假装知道是微开还是全开
        assertEquals("已开启 · 点击关闭", WindowControl.hint(stage))
    }

    @Test
    fun `车况报全关时档位归零`() {
        // 在车里手动关了窗：本地还记着「全开」，车况说全关，就该归零
        val stage = WindowControl.resolve(
            saved = WindowStage.FULL, savedAt = t0 - 60_000L, statusOpen = false, now = t0,
        )
        assertEquals(WindowStage.CLOSED, stage)
    }

    @Test
    fun `刚下发完的几秒内不让旧报文把档位打回去`() {
        // 车端执行要 5~15 秒，这期间拉回来的还是上一条报文（车窗关着）。
        // 若被它校准，刚点完「全开」会立刻变回「已关闭」，下一击又变成微开 —— 这就是"点起来不一致"。
        val stage = WindowControl.resolve(
            saved = WindowStage.FULL, savedAt = t0 - 3_000L, statusOpen = false, now = t0,
        )
        assertEquals(WindowStage.FULL, stage)
        // 过了宽限期就听车况的
        val later = WindowControl.resolve(
            saved = WindowStage.FULL, savedAt = t0 - 60_000L, statusOpen = false, now = t0,
        )
        assertEquals(WindowStage.CLOSED, later)
    }

    @Test
    fun `车况说开着时保留本地已经记住的档位`() {
        // 本地记着全开、车况说「有窗开着」：两票一致，继续全开
        assertEquals(
            WindowStage.FULL,
            WindowControl.resolve(WindowStage.FULL, t0 - 60_000L, statusOpen = true, now = t0),
        )
        // 本地记着关、车况说开着：别人开的，不知道开度
        assertEquals(
            WindowStage.OPEN_UNKNOWN,
            WindowControl.resolve(WindowStage.CLOSED, t0 - 60_000L, statusOpen = true, now = t0),
        )
    }

    @Test
    fun `读不到车窗信号时退回本地记录，不会假装全关`() {
        assertEquals(
            WindowStage.VENT,
            WindowControl.resolve(WindowStage.VENT, t0 - 60_000L, statusOpen = null, now = t0),
        )
        // 本地也没记过：从「关」起步，点一下是微开，不会误发关闭
        assertEquals(
            WindowStage.CLOSED,
            WindowControl.resolve(null, 0L, statusOpen = null, now = t0),
        )
    }

    @Test
    fun `半开是一条独立档位，不是微开的别名`() {
        // 老版本 / 上游小组件下发过 windowOpen（230 / value 5）：它就是「半开」这一档，
        // 下一步是全开，不是关 —— 不然车窗会在四档之间跳着走。
        assertEquals(WindowStage.HALF, WindowControl.stageOf("windowOpen"))
        assertEquals(WindowControl.CMD_FULL, WindowControl.nextCommand(WindowStage.HALF))
        assertEquals("已半开 · 点击全开", WindowControl.hint(WindowStage.HALF))
    }

    /**
     * 指令值必须是真车复核过的那几个。
     *
     * 刻度是 **0~10**，不是百分比：`100` / `1` 云端回「请求成功」但车不动
     * （C10 实测）。写错一个数字，用户看到的就是「点了没反应」。
     */
    @Test
    fun `车窗指令值是真车复核过的 0 2 5 10`() {
        assertEquals("""{"value":"2"}""", Commands.build("windowVent").stateJson)
        assertEquals("""{"value":"10"}""", Commands.build("windowFull").stateJson)
        assertEquals("""{"value":"0"}""", Commands.build("windowClose").stateJson)
        assertEquals("230", Commands.build("windowFull").cmdid)
        // 三条都必须是 cmd 230
        listOf("windowVent", "windowFull", "windowClose", "windowOpen").forEach {
            assertEquals("$it 的 cmdid 不对", "230", Commands.build(it).cmdid)
        }
    }

    @Test
    fun `按钮亮灭跟档位走，不跟车况的单一开合位走`() {
        assertFalse(WindowControl.isOpen(WindowStage.CLOSED))
        assertTrue(WindowControl.isOpen(WindowStage.VENT))
        assertTrue(WindowControl.isOpen(WindowStage.FULL))
        assertTrue(WindowControl.isOpen(WindowStage.OPEN_UNKNOWN))
    }

    @Test
    fun `首页按钮文字带出当前档位`() {
        // 关着只说「车窗」；开着把档位写在按钮上，点完立刻能看到变化
        assertEquals("车窗", WindowControl.buttonLabel(WindowStage.CLOSED))
        assertEquals("车窗·微开", WindowControl.buttonLabel(WindowStage.VENT))
        assertEquals("车窗·半开", WindowControl.buttonLabel(WindowStage.HALF))
        assertEquals("车窗·全开", WindowControl.buttonLabel(WindowStage.FULL))
        assertEquals("车窗·开启", WindowControl.buttonLabel(WindowStage.OPEN_UNKNOWN))
    }

    @Test
    fun `确认弹窗说的是具体档位，不是笼统的打开车窗`() {
        assertEquals("微开车窗", ControlActions.commandLabel("windowVent"))
        assertEquals("半开车窗", ControlActions.commandLabel("windowOpen"))
        assertEquals("全开车窗", ControlActions.commandLabel("windowFull"))
        assertEquals("关闭车窗", ControlActions.commandLabel("windowClose"))
    }

    /**
     * 四条车窗指令在提示层都要被认成「车窗」。
     *
     * 漏一条的后果很实在：右上角不显示「全开中…」，车况跟上后也不会自动收掉
     * 上一句提示（`reached` 靠 topicOf 找信号）。2026-11-07 加 windowFull 时就漏过一次。
     */
    @Test
    fun `四档指令在提示层都算车窗主题`() {
        listOf("windowVent", "windowOpen", "windowFull", "windowClose").forEach { command ->
            assertEquals(
                "$command 没被当成车窗，右上角提示会失效",
                com.leapcontrol.app.ui.ControlFeedback.TOPIC_WINDOW,
                com.leapcontrol.app.ui.ControlFeedback.topicOf(command),
            )
            assertTrue(
                "$command 缺执行中文案",
                !com.leapcontrol.app.ui.ControlFeedback.runningText(command).isNullOrBlank(),
            )
        }
    }

    /**
     * 首页与磁贴必须共用 [WindowControl]，不许再各写一套两档取反。
     *
     * 2026-11-07 之前首页是 `if (status.windowOpen) "windowClose" else "windowOpen"`，
     * 磁贴是本地布尔取反，体检页又自己挑了「半开」当通风 —— 三处口径不同，
     * 用户点同一个按钮，看到的提示和车实际动作对不上。
     */
    @Test
    fun `首页与磁贴都走同一份车窗档位`() {
        val projectDir = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.first { File(it, "app").isDirectory }
        val base = "$projectDir/app/src/main/java/com/leapcontrol/app"

        val home = File(base, "ui/HomeScreen.kt").readText()
        assertTrue("首页没走车窗档位", home.contains("WindowControl.resolve("))
        assertTrue("首页解析指令时没把档位带上", home.contains("windowStageNow()"))
        assertTrue("首页没记下发后的档位", home.contains("WindowControl.remember("))

        // 档位 → 指令的唯一出口
        val actions = File(base, "ui/ControlActions.kt").readText()
        assertTrue("ControlActions 没按档位给指令", actions.contains("WindowControl.nextCommand("))

        val tile = File(base, "tiles/VehicleTileService.kt").readText()
        assertTrue("磁贴没走车窗档位", tile.contains("WindowControl.nextCommand("))
        assertTrue("磁贴没记下发后的档位", tile.contains("WindowControl.save("))

        val health = File(base, "ui/VehicleHealth.kt").readText()
        assertTrue("体检页的「开窗通风」没发微开", health.contains("WindowControl.CMD_VENT"))

        // 老的「半开 / 关」两档取反不能再出现在首页解析里
        assertFalse(
            "首页又退回两档取反了",
            home.contains("""if (status?.windowOpen == true) "windowClose" else "windowOpen""""),
        )
    }
}
