package com.leapcontrol.app

import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostEntry
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.JournalVault
import com.leapcontrol.app.journal.VaultCar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 零记存储格式（schema 2）的编解码。
 *
 * 这组用例锁的是**数据安全的下限**：
 *  1. 写进去再读出来必须一模一样（多车、单值字段、里程浮点都不能走样）；
 *  2. 解析失败必须返回 null，**绝对不能返回一个空账本** ——
 *     空账本会被上层当成「用户本来就没有数据」，接着把空数据写回磁盘，
 *     那一步才是真正把账本弄丢的地方；
 *  3. 旧格式（裸 JournalData）和无关 JSON 要能区分开，「找回历史数据」才不会乱认。
 */
class JournalVaultTest {

    private fun sample(costId: String = "c1"): JournalData = JournalData(
        costs = listOf(
            CostEntry(id = costId, date = 1_761_753_600_000L, category = CostCategory.CHARGE, amount = 42.5),
        ),
        dailyKm = mapOf("2026-10-30" to 12.5, "2026-10-31" to 30.25),
        odometerKm = 12345.6,
        odometerDay = "2026-10-31",
    )

    @Test
    fun `多车往返编解码不丢字段`() {
        val cars = mapOf(
            "LVIN_A" to VaultCar("LVIN_A", sample("c1"), 111L),
            "LVIN_B" to VaultCar("LVIN_B", sample("c2"), 222L),
        )
        val text = JournalVault.encode(cars, lastKey = "LVIN_B", savedAt = 333L)
        val decoded = JournalVault.decode(text)!!

        assertEquals(setOf("LVIN_A", "LVIN_B"), decoded.cars.keys)
        assertEquals("LVIN_B", decoded.lastKey)
        assertEquals(333L, decoded.savedAt)
        assertEquals(222L, decoded.cars["LVIN_B"]!!.updatedAt)
        val b = decoded.cars["LVIN_B"]!!.data
        assertEquals(42.5, b.costs.single().amount, 0.0001)
        assertEquals("c2", b.costs.single().id)
        assertEquals(12.5, b.dailyKm["2026-10-30"]!!, 0.0001)
        assertEquals(12345.6, b.odometerKm!!, 0.0001)
        assertEquals("2026-10-31", b.odometerDay)
    }

    @Test
    fun `解析失败返回 null 而不是空账本`() {
        assertNull(JournalVault.decode(null))
        assertNull(JournalVault.decode(""))
        assertNull(JournalVault.decode("   "))
        assertNull(JournalVault.decode("不是 json"))
        // 合法 JSON 但没有 cars：这是别人的文件，不能当成「零记没有数据」
        assertNull(JournalVault.decode("""{"schema":2,"hello":1}"""))
        assertNull(JournalVault.decode("""{"kind":"leapcontrol-journal","vehicles":[]}"""))
    }

    @Test
    fun `没有任何车辆的文件仍可解析`() {
        val text = JournalVault.encode(emptyMap(), lastKey = null, savedAt = 1L)
        val decoded = JournalVault.decode(text)
        assertNotNull(decoded)
        assertTrue(decoded!!.cars.isEmpty())
        assertNull(decoded.lastKey)
    }

    @Test
    fun `单一非空数据可以被自动接续识别`() {
        val empty = JournalData()
        val vault = JournalVault.decode(
            JournalVault.encode(
                mapOf(
                    "OLD" to VaultCar("OLD", sample(), 1L),
                    "NEW" to VaultCar("NEW", empty, 2L),
                ),
                lastKey = "NEW",
                savedAt = 3L,
            ),
        )!!
        // 当前车 NEW 是空的，本机只有 OLD 一份有内容 → 应该能拿到 OLD
        assertEquals("OLD", vault.soleNonEmpty(excludeKey = "NEW")?.key)
        // NEW 自己是空的，不排除它时也只有 OLD 是候选
        assertEquals("OLD", vault.soleNonEmpty()?.key)
    }

    @Test
    fun `两份都有内容时不做自动接续`() {
        val vault = JournalVault.decode(
            JournalVault.encode(
                mapOf(
                    "A" to VaultCar("A", sample("a"), 1L),
                    "B" to VaultCar("B", sample("b"), 2L),
                    "C" to VaultCar("C", JournalData(), 3L),
                ),
                lastKey = "C",
                savedAt = 3L,
            ),
        )!!
        // 两份非空 → 不知道该接哪一份，必须交给用户手动认领
        assertNull(vault.soleNonEmpty(excludeKey = "C"))
    }

    @Test
    fun `旧版单文件也能被宽松解析`() {
        val legacy = sample().toJson().toString()
        val cars = JournalVault.decodeLoose(legacy, fallbackKey = "LVIN_OLD")!!
        assertEquals(setOf("LVIN_OLD"), cars.keys)
        assertEquals(1, cars["LVIN_OLD"]!!.costs.size)
    }

    @Test
    fun `备份文件不会被当成本地数据`() {
        val backup = """{"kind":"leapcontrol-journal","format":1,"vehicles":[{"vin":"X","data":{}}]}"""
        assertNull(JournalVault.decodeLoose(backup, fallbackKey = "X"))
        assertNull(JournalVault.decodeLoose("", fallbackKey = "X"))
        assertNull(JournalVault.decodeLoose("随便一段文字", fallbackKey = "X"))
    }
}
