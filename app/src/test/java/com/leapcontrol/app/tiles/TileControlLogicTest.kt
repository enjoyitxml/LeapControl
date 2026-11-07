package com.leapcontrol.app.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 控制中心磁贴「点击逻辑照上游、8 块磁贴与图标仍用零控自己」的回归测试。
 *
 * 上游只有 3 块磁贴（车锁合一 / 寻车 / 空调），用户 2026-11-06 明确要求
 * **保留 8 块**、只换按钮逻辑。这里钉住数量、命令解析和上游的复位时长分档，
 * 防止以后同步上游时又把它们合并掉。
 */
class TileControlLogicTest {

    private fun spec(id: String) = requireNotNull(QuickTiles.byId(id)) { "磁贴定义缺失：$id" }

    @Test
    fun `仍保留 8 块磁贴且各自独立`() {
        assertEquals(8, QuickTiles.ALL.size)
        assertEquals(8, QuickTiles.ALL.map { it.id }.toSet().size)
        assertEquals(8, QuickTiles.ALL.map { it.service }.toSet().size)
        assertEquals(8, QuickTiles.ALL.map { it.iconRes }.toSet().size)
        // 上游把上锁/解锁合成一块「车锁」，零控仍要分成两块、各自固定一条指令
        assertTrue(QuickTiles.ALL.any { it.id == "lock" })
        assertTrue(QuickTiles.ALL.any { it.id == "unlock" })
    }

    @Test
    fun `只有上锁与鸣笛是瞬时动作，车窗是档位型，其余五块是开关`() {
        // 用户 2026-11-07：解锁 / 预热也有对应的「关」，亮着点就该关，
        // 所以它们从瞬时动作改成开关型，"亮灭"永远等于车上的功能开没开。
        val momentary = QuickTiles.ALL.filter { it.kind == TileKind.MOMENTARY }.map { it.id }
        assertEquals(listOf("lock", "horn"), momentary)
        val toggles = QuickTiles.ALL.filter { it.kind == TileKind.TOGGLE }.map { it.id }
        assertEquals(
            listOf("unlock", "ac", "trunk", "batteryPreheat", "sunshade"),
            toggles,
        )
        // 车窗单独一类：车况只报「有没有窗开着」，微开 / 全开靠本地档位
        val cycles = QuickTiles.ALL.filter { it.kind == TileKind.CYCLE }.map { it.id }
        assertEquals(listOf("window"), cycles)
    }

    @Test
    fun `动作型固定一条指令，开关型按开合方向取反`() {
        listOf("lock", "horn").forEach { id ->
            val s = spec(id)
            assertEquals(TileKind.MOMENTARY, s.kind)
            assertEquals("$id 点两次不该换指令", s.openCommand, s.commandFor(turningOn = false))
            assertEquals(s.openCommand, s.commandFor(turningOn = true))
        }
        listOf("unlock", "ac", "trunk", "batteryPreheat", "sunshade").forEach { id ->
            val s = spec(id)
            assertEquals(TileKind.TOGGLE, s.kind)
            assertTrue("$id 缺关闭指令", s.closeCommand.isNotBlank())
            assertEquals(s.openCommand, s.commandFor(turningOn = true))
            assertEquals(s.closeCommand, s.commandFor(turningOn = false))
        }
    }

    /**
     * 高亮**不许**再靠计时器复位。
     *
     * 2026-11-07 之前是「点下去先亮，2.2 / 2.5 / 10 秒后按本地状态复位」，高亮时长与
     * 真实结果无关：点关闭要亮 2.5 秒，上锁要亮 10 秒。现在必须由**结果回调**摆正 ——
     * 这条守住接线，防止哪天又退回计时复位（用户对「点了看不出成没成」反馈过两次）。
     */
    @Test
    fun `高亮由结果决定，不再按固定时长复位`() {
        val projectDir = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.first { File(it, "app").isDirectory }
        val service = File(
            projectDir,
            "app/src/main/java/com/leapcontrol/app/tiles/VehicleTileService.kt",
        ).readText()
        val runner = File(
            projectDir,
            "app/src/main/java/com/leapcontrol/app/tiles/TileCommandRunner.kt",
        ).readText()

        assertTrue("点击当帧没走高亮规则 clickedActive", service.contains("clickedActive("))
        assertTrue("结果回来后没有重画磁贴", service.contains("onFinished(ok)"))
        assertTrue("复位计时又回来了", !service.contains("scheduleReset"))
        assertTrue("磁贴定义里不该再有 resetMs", !service.contains("resetMs"))

        // 执行层必须把结果回调出去，而且要先撤在途标记再回调
        assertTrue("执行层没接结果回调", runner.contains("onFinished: (Boolean) -> Unit"))
        val clear = runner.indexOf("inFlight.set(null)")
        val callback = runner.indexOf("onFinished(result.isSuccess)")
        assertTrue("执行层没有回调结果", callback > 0)
        assertTrue("回调早于在途标记撤销，瞬时动作会被判成还在执行", clear in 0 until callback)
    }

    @Test
    fun `执行中文案按方向给出，且不依赖车端结果`() {
        val ac = spec("ac")
        assertEquals("空调开启中…", ac.doingText(turningOn = true))
        assertEquals("空调关闭中…", ac.doingText(turningOn = false))
        assertEquals("车辆上锁中…", spec("lock").doingText(turningOn = true))
    }

    @Test
    fun `Manifest 仍声明全部 8 块磁贴并只用零控自己的图标`() {
        val projectDir = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.first { File(it, "app").isDirectory }
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml").readText()
        listOf(
            "LockTileService", "UnlockTileService", "HornTileService", "ClimateTileService",
            "TrunkTileService", "WindowTileService", "BatteryHeatTileService", "SunshadeTileService",
        ).forEach { name ->
            assertTrue("Manifest 少了 $name", manifest.contains(".tiles.$name"))
        }
        // 「只保留我们自己项目的图标设计」：不许混入上游的 phosphor / location_horn 图标
        assertTrue(manifest.contains("@drawable/ic_tile_lock"))
        assertTrue("混进了上游图标", !manifest.contains("ic_phosphor_"))
        assertTrue("混进了上游图标", !manifest.contains("ic_location_horn"))
    }
}
