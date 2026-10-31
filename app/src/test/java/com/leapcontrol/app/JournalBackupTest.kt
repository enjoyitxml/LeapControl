package com.leapcontrol.app

import com.leapcontrol.app.journal.CarGoal
import com.leapcontrol.app.journal.CityCheckin
import com.leapcontrol.app.journal.CityWish
import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.CostEntry
import com.leapcontrol.app.journal.InsurancePlan
import com.leapcontrol.app.journal.JournalBackup
import com.leapcontrol.app.journal.JournalData
import com.leapcontrol.app.journal.MaintenancePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 零记备份单测：文件格式、识别逻辑、合并规则。
 *
 * 合并是这里最要紧的部分 —— 它是「换手机恢复数据」唯一的写路径，
 * 弄丢或弄重一笔账都没法撤销，所以要逐条钉住。
 */
class JournalBackupTest {

    private val day = 1_790_000_000_000L

    private fun cost(id: String, amount: Double, offsetDays: Long = 0) =
        CostEntry(id = id, date = day + offsetDays * 86_400_000L, category = CostCategory.CHARGE, amount = amount)

    private fun checkin(id: String, city: String) =
        CityCheckin(id = id, city = city, province = "浙江", date = day)

    private fun sample(vararg costs: CostEntry) = JournalData(
        costs = costs.toList().sortedByDescending { it.date },
        checkins = listOf(checkin("浙江/杭州", "杭州")),
        dailyKm = mapOf("2026-10-28" to 12.0, "2026-10-29" to 30.0),
        wishes = listOf(CityWish(id = "四川/成都", city = "成都", province = "四川", added = day)),
        goal = CarGoal(targetYears = 8, landingPrice = 120_000.0, deliveryDate = day),
    )

    @Test
    fun roundTripKeepsEverything() {
        val payload = JournalBackup.Payload(
            exportedAt = day,
            appVersion = "0.2.52",
            vehicles = listOf(JournalBackup.Vehicle("LVIN0001", sample(cost("c1", 30.0), cost("c2", 50.0, 1)))),
            cardOrder = "goal,cost,city",
        )

        val decoded = JournalBackup.decode(JournalBackup.encode(payload)).getOrThrow()

        assertEquals(day, decoded.exportedAt)
        assertEquals("0.2.52", decoded.appVersion)
        assertEquals("goal,cost,city", decoded.cardOrder)
        assertEquals(1, decoded.vehicleCount)
        val vehicle = decoded.vehicles.single()
        assertEquals("LVIN0001", vehicle.vin)
        assertEquals(2, vehicle.data.costs.size)
        assertEquals(1, vehicle.data.checkins.size)
        assertEquals(2, vehicle.data.dailyKm.size)
        assertEquals(1, vehicle.data.wishes.size)
        assertEquals(120_000.0, vehicle.data.goal!!.landingPrice, 0.001)
    }

    @Test
    fun multipleVehiclesAreKeptSeparately() {
        val payload = JournalBackup.Payload(
            exportedAt = day,
            appVersion = "0.2.52",
            vehicles = listOf(
                JournalBackup.Vehicle("AAA", sample(cost("c1", 30.0))),
                JournalBackup.Vehicle("BBB", JournalData(dailyKm = mapOf("2026-10-01" to 5.0))),
            ),
        )

        val decoded = JournalBackup.decode(JournalBackup.encode(payload)).getOrThrow()

        assertEquals(2, decoded.vehicleCount)
        assertEquals(1, decoded.costCount)
        assertEquals(3, decoded.kmDayCount)
    }

    @Test
    fun foreignJsonIsRejected() {
        val result = JournalBackup.decode("""{"hello":"world"}""")

        assertTrue(result.isFailure)
        assertEquals("这不是零控的备份文件", result.exceptionOrNull()?.message)
    }

    @Test
    fun brokenTextIsRejected() {
        val result = JournalBackup.decode("not a json at all")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun newerFormatIsRejectedWithHint() {
        val text = """{"kind":"leapcontrol-journal","format":99,"vehicles":[{"vin":"A","data":{}}]}"""

        val result = JournalBackup.decode(text)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("更新"))
    }

    @Test
    fun emptyVehicleListIsRejected() {
        val text = """{"kind":"leapcontrol-journal","format":1,"vehicles":[]}"""

        assertTrue(JournalBackup.decode(text).isFailure)
    }

    @Test
    fun mergeIntoEmptyLocalRestoresEverything() {
        val backup = sample(cost("c1", 30.0), cost("c2", 50.0))

        val merged = JournalBackup.merge(JournalData.EMPTY, backup)

        assertEquals(backup, merged)
    }

    @Test
    fun mergeDoesNotDuplicateTheSameEntry() {
        val local = sample(cost("c1", 30.0))
        val backup = sample(cost("c1", 30.0), cost("c2", 50.0))

        val merged = JournalBackup.merge(local, backup)

        assertEquals(2, merged.costs.size)
        assertEquals(listOf("c1", "c2"), merged.costs.map { it.id }.sorted())
        // 打卡、心愿同一 id 也不该变成两条
        assertEquals(1, merged.checkins.size)
        assertEquals(1, merged.wishes.size)
    }

    @Test
    fun mergeKeepsLocalWhenBothSidesHaveSingleValue() {
        val local = sample(cost("c1", 30.0)).copy(
            goal = CarGoal(targetYears = 3, landingPrice = 90_000.0, deliveryDate = day),
            maintenance = MaintenancePlan(lastDate = day, lastKm = 12_000.0, months = 12, km = 10_000.0),
            insurance = InsurancePlan(startDate = day, months = 12, premium = 4_500.0),
        )
        val backup = sample(cost("c2", 50.0)).copy(
            goal = CarGoal(targetYears = 10, landingPrice = 200_000.0, deliveryDate = day),
            insurance = InsurancePlan(startDate = day, months = 24, premium = 8_000.0),
        )

        val merged = JournalBackup.merge(local, backup)

        assertEquals(3, merged.goal!!.targetYears)
        assertEquals(12, merged.maintenance!!.months)
        assertEquals(12, merged.insurance!!.months)
        // 本机没有的照样补上
        assertEquals(2, merged.costs.size)
    }

    @Test
    fun mergeTakesTheLargerDailyKm() {
        val local = JournalData(dailyKm = mapOf("2026-10-28" to 12.0, "2026-10-29" to 30.0))
        val backup = JournalData(dailyKm = mapOf("2026-10-28" to 13.0, "2026-10-27" to 8.0))

        val merged = JournalBackup.merge(local, backup)

        assertEquals(13.0, merged.dailyKm["2026-10-28"]!!, 0.001)
        assertEquals(30.0, merged.dailyKm["2026-10-29"]!!, 0.001)
        assertEquals(8.0, merged.dailyKm["2026-10-27"]!!, 0.001)
    }

    @Test
    fun fileNameCarriesTimestamp() {
        val name = JournalBackup.fileName(day)

        assertTrue(name.startsWith("零控零记-"))
        assertTrue(name.endsWith(".json"))
    }

    @Test
    fun payloadDescriptionListsWhatWillBeRestored() {
        val payload = JournalBackup.Payload(
            exportedAt = day,
            appVersion = "0.2.52",
            vehicles = listOf(JournalBackup.Vehicle("AAA", sample(cost("c1", 30.0), cost("c2", 50.0)))),
        )

        val text = JournalBackup.describe(payload)

        assertTrue(text.contains("车辆：1 辆"))
        assertTrue(text.contains("记账 2 笔"))
        assertTrue(text.contains("打卡 1 城"))
        assertTrue(text.contains("里程 2 天"))
    }

    @Test
    fun emptyImportSummarySaysNothingNew() {
        val text = JournalBackup.describe(JournalBackup.Summary(vehicles = 1))

        assertTrue(text.contains("都已经有了"))
    }

    @Test
    fun importSummaryOnlyReportsNewRecords() {
        val text = JournalBackup.describe(
            JournalBackup.Summary(vehicles = 1, newCosts = 3, newCheckins = 1, cardOrderRestored = true),
        )

        assertTrue(text.contains("记账 3 笔"))
        assertTrue(text.contains("打卡 1 城"))
        assertTrue(text.contains("卡片排版"))
        assertFalse(text.contains("里程"))
    }

    @Test
    fun cardOrderIsOptional() {
        val payload = JournalBackup.Payload(
            exportedAt = day,
            appVersion = "0.2.52",
            vehicles = listOf(JournalBackup.Vehicle("AAA", sample())),
        )

        val decoded = JournalBackup.decode(JournalBackup.encode(payload)).getOrThrow()

        assertNull(decoded.cardOrder)
    }
}
