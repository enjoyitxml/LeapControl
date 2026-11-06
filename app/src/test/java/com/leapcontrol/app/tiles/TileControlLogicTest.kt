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
    fun `动作型固定一条指令，开关型按开合方向取反`() {
        listOf("lock", "unlock", "horn", "batteryPreheat").forEach { id ->
            val s = spec(id)
            assertEquals(TileKind.MOMENTARY, s.kind)
            assertEquals("$id 点两次不该换指令", s.openCommand, s.commandFor(turningOn = false))
            assertEquals(s.openCommand, s.commandFor(turningOn = true))
        }
        listOf("ac", "trunk", "window", "sunshade").forEach { id ->
            val s = spec(id)
            assertEquals(TileKind.TOGGLE, s.kind)
            assertTrue("$id 缺关闭指令", s.closeCommand.isNotBlank())
            assertEquals(s.openCommand, s.commandFor(turningOn = true))
            assertEquals(s.closeCommand, s.commandFor(turningOn = false))
        }
    }

    @Test
    fun `复位时长按上游分档且都不为 0`() {
        // 上游：车锁 10 秒（车端门锁信号慢）、寻车 2.2 秒、其余 2.5 秒
        assertEquals(10_000L, spec("lock").resetMs)
        assertEquals(10_000L, spec("unlock").resetMs)
        assertEquals(2_200L, spec("horn").resetMs)
        QuickTiles.ALL.forEach { s ->
            assertTrue("${s.id} 没有复位计时", s.resetMs > 0L)
            assertTrue("${s.id} 复位太慢，又是「点了卡住」", s.resetMs <= 10_000L)
        }
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
