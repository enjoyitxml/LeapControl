package com.leapcontrol.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 里程 / 能耗响应解析：近 7 日固定契约 + 累计口径别名匹配。 */
class MileageEnergyParserTest {

    @Test
    fun parseRecent_readsDetailAndTotal() {
        val detail = JSONArray().apply {
            put(JSONObject().put("day", "2026-10-20").put("accumulatedMileage", 12.5))
            put(JSONObject().put("day", "2026-10-21").put("accumulatedMileage", 30))
            // 缺字段的行直接跳过
            put(JSONObject().put("day", "2026-10-22"))
        }
        val response = JSONObject()
            .put("result", "0")
            .put(
                "data", JSONObject()
                    .put("detail", detail)
                    .put("totalmileage", 12345.6)
                    .put("totalEnergy", 2100)
                    .put("deliveryDays", 300)
            )

        val summary = MileageEnergyParser.parseRecent(response)
        assertNotNull(summary)
        assertEquals(2, summary!!.recentDays.size)
        assertEquals(42.5, summary.recent7DaysKm!!, 0.001)
        assertEquals(12345.6, summary.totalMileageKm!!, 0.001)
        assertEquals(2100.0, summary.cumulativeEnergyKwh!!, 0.001)
        assertEquals(300, summary.ownershipDays)
    }

    @Test
    fun parseLifetime_matchesAliases() {
        val response = JSONObject().put(
            "data", JSONObject()
                .put("comprehensiveEnergyConsumption", 14.2)
                .put("totalMileage", 8800)
                .put("cumulativeEnergy", 1250.5)
                .put("ownershipDays", 210)
        )
        val summary = MileageEnergyParser.parseLifetime(response)
        assertNotNull(summary)
        assertEquals(14.2, summary!!.overallConsumption!!, 0.001)
        assertEquals(8800.0, summary.totalMileageKm!!, 0.001)
        assertEquals(1250.5, summary.cumulativeEnergyKwh!!, 0.001)
        assertEquals(210, summary.ownershipDays)
    }

    @Test
    fun businessFailure_isRejected() {
        val response = JSONObject().put("result", "39").put("data", JSONObject())
        assertNull(MileageEnergyParser.parseRecent(response))
        assertNull(MileageEnergyParser.parseLifetime(response))
    }

    @Test
    fun sensitiveFields_areIgnored() {
        val response = JSONObject().put(
            "data", JSONObject()
                .put("vin", "LFZ1234567890123")
                .put("longitude", 120.1)
                .put("totalMileage", 1000)
        )
        val summary = MileageEnergyParser.parseLifetime(response)
        assertNotNull(summary)
        // 只认里程，位置与车架号不进结果
        assertEquals(1000.0, summary!!.totalMileageKm!!, 0.001)
        assertNull(summary.overallConsumption)
    }

    @Test
    fun summary_roundTripThroughJson() {
        val summary = MileageEnergySummary(
            totalMileageKm = 5000.0,
            overallConsumption = 13.7,
            cumulativeEnergyKwh = 685.0,
            ownershipDays = 120,
            recentDays = listOf(DailyMileage("2026-10-26", 25.0)),
            capturedAt = 1_700_000_000_000L,
        )
        val restored = MileageEnergySummary.fromJson(summary.toJson())
        assertNotNull(restored)
        assertEquals(5000.0, restored!!.totalMileageKm!!, 0.001)
        assertEquals(13.7, restored.overallConsumption!!, 0.001)
        assertEquals(120, restored.ownershipDays)
        assertEquals(1, restored.recentDays.size)
        assertEquals(25.0, restored.recent7DaysKm!!, 0.001)
    }

    @Test
    fun merge_fillsMissingFieldsOnly() {
        val base = MileageEnergySummary(totalMileageKm = 100.0)
        val extra = MileageEnergySummary(totalMileageKm = 999.0, overallConsumption = 12.0)
        val merged = base.merge(extra)
        // 已有字段不被覆盖
        assertEquals(100.0, merged.totalMileageKm!!, 0.001)
        assertEquals(12.0, merged.overallConsumption!!, 0.001)
    }
}
