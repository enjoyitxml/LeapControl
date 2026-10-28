package com.leapcontrol.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** 两张能耗卡的解析与格式化（近 6 周百公里能耗 / 上周能耗构成）。 */
class WeeklyEnergyTest {

    private fun rankResponse(rank: Any, average: Any, weeks: JSONArray) = JSONObject()
        .put("result", "0")
        .put(
            "data",
            JSONObject()
                .put("rankResult", JSONObject().put("hundredKmEC", average).put("rank", rank))
                .put("weeklyEC", weeks),
        )

    private fun compositionResponse(driver: Any?, ac: Any?, other: Any?) = JSONObject()
        .put("code", 200)
        .put(
            "data",
            JSONObject().apply {
                driver?.let { put("driverEC", it) }
                ac?.let { put("acEC", it) }
                other?.let { put("otherEC", it) }
            },
        )

    @Test
    fun `近6周能耗读出平均值排行与每周点位`() {
        val weeks = JSONArray().apply {
            put(JSONObject().put("weekStart", "2026-09-21").put("weekEnd", "2026-09-27").put("hundredKmEC", 11.2))
            put(JSONObject().put("weekStart", "2026-10-19").put("weekEnd", "2026-10-25").put("hundredKmEC", 13.6))
        }
        val rank = WeeklyEnergyParser.parseRank(rankResponse("91%", 13.6, weeks))
        assertEquals(13.6, rank.averageEc!!, 0.001)
        assertEquals("91%", rank.rankPercent)
        assertEquals(2, rank.weeks.size)
        assertEquals("2026-09-21", rank.weeks.first().weekStart)
        assertEquals(11.2, rank.weeks.first().hundredKmEc, 0.001)
    }

    @Test
    fun `缺 weekEnd 的点也保留`() {
        val weeks = JSONArray().apply {
            put(JSONObject().put("weekStart", "2026-10-19").put("hundredKmEC", 13.6))
        }
        val rank = WeeklyEnergyParser.parseRank(rankResponse("91", 13.6, weeks))
        assertEquals(1, rank.weeks.size)
        assertNull(rank.weeks.first().weekEnd)
    }

    @Test
    fun `排行百分数归一化`() {
        assertEquals("91%", WeeklyEnergyParser.normalizeRank("91"))
        assertEquals("91%", WeeklyEnergyParser.normalizeRank(" 91% "))
        assertEquals("91%", WeeklyEnergyParser.normalizeRank("0.91"))
        assertEquals("91.5%", WeeklyEnergyParser.normalizeRank("91.5%"))
        assertNull(WeeklyEnergyParser.normalizeRank(""))
        assertNull(WeeklyEnergyParser.normalizeRank("暂无"))
    }

    @Test
    fun `业务码非0时抛异常`() {
        val response = JSONObject().put("result", "500").put("data", JSONObject())
        assertThrows(WeeklyEnergyException::class.java) {
            WeeklyEnergyParser.parseRank(response)
        }
    }

    @Test
    fun `排行接口字段全空时抛异常`() {
        val response = rankResponse("", "", JSONArray())
        assertThrows(WeeklyEnergyException::class.java) {
            WeeklyEnergyParser.parseRank(response)
        }
    }

    @Test
    fun `上周能耗构成读出三项`() {
        val composition = WeeklyEnergyParser.parseComposition(
            compositionResponse(9.8, 0.1, 0.8),
        )
        assertEquals(3, composition.slices.size)
        assertEquals("行车", composition.slices[0].label)
        assertEquals(9.8, composition.slices[0].kwh, 0.001)
        assertEquals("空调", composition.slices[1].label)
        assertEquals("其他", composition.slices[2].label)
        assertEquals(10.7, composition.totalKwh, 0.001)
    }

    @Test
    fun `缺一项时按剩余项求和`() {
        val composition = WeeklyEnergyParser.parseComposition(
            compositionResponse(9.8, null, 0.8),
        )
        assertEquals(2, composition.slices.size)
        assertEquals(10.6, composition.totalKwh, 0.001)
    }

    @Test
    fun `占比按最大余数法凑满100`() {
        // 官方截图里的真实数字：9.8 / 0.1 / 0.8 → 92% / 1% / 7%
        assertEquals(listOf(92, 1, 7), EnergyFormat.percentInts(listOf(9.8, 0.1, 0.8)))
        // 三项各占三分之一：33 + 33 + 34 才等于 100
        val thirds = EnergyFormat.percentInts(listOf(1.0, 1.0, 1.0))
        assertEquals(100, thirds.sum())
    }

    @Test
    fun `总量为0时占比全0`() {
        assertEquals(listOf(0, 0, 0), EnergyFormat.percentInts(listOf(0.0, 0.0, 0.0)))
    }

    @Test
    fun `周末标签取月日`() {
        assertEquals("9/21", EnergyFormat.weekLabel("2026-09-21"))
        assertEquals("10/19", EnergyFormat.weekLabel("2026/10/19"))
        assertEquals("10/19", EnergyFormat.weekLabel("10-19"))
        assertEquals("--", EnergyFormat.weekLabel(null))
    }

    @Test
    fun `车型名去掉品牌前缀`() {
        assertEquals("C11", EnergyFormat.carModelName("零跑C11"))
        assertEquals("C11", EnergyFormat.carModelName("C11"))
        assertEquals("零跑", EnergyFormat.carModelName(""))
        assertEquals("零跑", EnergyFormat.carModelName("零跑"))
    }

    @Test
    fun `内部代号折回宣传名`() {
        // Lafa5 的车辆接口下发的是项目代号 B05，不能直接显示给车主
        assertEquals("Lafa5", EnergyFormat.carModelName("B05"))
        assertEquals("Lafa5", EnergyFormat.carModelName("b05"))
        assertEquals("Lafa5", EnergyFormat.carModelName("零跑B05"))
        assertEquals("Lafa5", EnergyFormat.carModelName("LAFA5"))
        // 未知代号原样显示，不做猜测
        assertEquals("B10", EnergyFormat.carModelName("B10"))
    }

    @Test
    fun `能耗数值一位小数`() {
        assertEquals("9.8", EnergyFormat.kwh(9.8))
        assertEquals("13.6", EnergyFormat.kwh(13.64))
        assertEquals("--", EnergyFormat.kwh(null))
    }

    @Test
    fun `磁盘缓存往返不丢字段`() {
        val data = WeeklyEnergyData(
            rank = WeeklyEnergyRank(
                averageEc = 13.6,
                rankPercent = "91%",
                weeks = listOf(
                    WeeklyEnergyPoint("2026-10-19", "2026-10-25", 13.6),
                ),
            ),
            composition = EnergyComposition(
                listOf(EnergySlice("行车", 9.8), EnergySlice("空调", 0.1), EnergySlice("其他", 0.8)),
            ),
            capturedAt = 12345L,
        )
        val restored = WeeklyEnergyData.fromJson(JSONObject(data.toJson().toString()))
        assertNotNull(restored)
        assertEquals("91%", restored!!.rank!!.rankPercent)
        assertEquals(1, restored.rank!!.weeks.size)
        assertEquals(13.6, restored.rank!!.weeks.first().hundredKmEc, 0.001)
        assertEquals(3, restored.composition!!.slices.size)
        assertEquals(10.7, restored.composition!!.totalKwh, 0.001)
        assertEquals(12345L, restored.capturedAt)
    }
}
