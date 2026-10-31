package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 车辆位置的取数链路：原始 signalMap → [SignalTable.decode] → [VehicleLocationDomain]。
 *
 * 回归的是「位置一直显示『车机还没上报过位置』」那一次：
 * 文档里位置有两套键（`2`/`3` 与 `3724`/`3725`），而解码只会把 3724/3725 折成
 * `latitude`/`longitude`、`2`/`3` 原样留下；取数时又只认命名键与 3724/3725，
 * 于是只报 `2`/`3` 的车型永远拿不到坐标。
 */
class VehicleLocationParseTest {

    private fun JSONObject.toValues(): Map<String, Any?> {
        val out = HashMap<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = opt(key)
        }
        return out
    }

    /** 走一遍真实链路：原始信号 → 解码 → 取坐标。 */
    private fun parse(raw: JSONObject): Pair<Double?, Double?> {
        val values = SignalTable.decode(raw).toValues()
        val pair = VehicleLocationDomain.selectFromDecodedOrRaw(values).pair
        return pair.latitude?.toString()?.toDoubleOrNull() to
            pair.longitude?.toString()?.toDoubleOrNull()
    }

    @Test
    fun `只上报 2 与 3 的车型也能取到坐标`() {
        val raw = JSONObject()
            .put("2", "120.123456")
            .put("3", "30.123456")
            .put("1204", "80")

        assertEquals(30.123456 to 120.123456, parse(raw))
    }

    @Test
    fun `只上报 3724 与 3725 的车型取到的是同一对坐标`() {
        val raw = JSONObject()
            .put("3724", "120.123456")
            .put("3725", "30.123456")

        assertEquals(30.123456 to 120.123456, parse(raw))
    }

    @Test
    fun `解码后 2 与 3 不会被当成普通数字键丢掉`() {
        val decoded = SignalTable.decode(
            JSONObject().put("2", "120.1").put("3", "30.1").put("999999", "1"),
        )

        assertTrue(decoded.has("2"))
        assertTrue(decoded.has("3"))
        // 表里没有的纯数字键仍然丢掉，避免把无关信号带进业务判断
        assertFalse(decoded.has("999999"))
    }

    @Test
    fun `两套键同时存在时以命名键为准`() {
        val raw = JSONObject()
            .put("2", "120.100000")
            .put("3", "30.100000")
            .put("3724", "120.900000")
            .put("3725", "30.900000")

        // 3724/3725 解码成 latitude/longitude 后优先命中；数值与 2/3 不同，用来确认顺序
        assertEquals(30.9 to 120.9, parse(raw))
    }

    @Test
    fun `T03 这类直接返回命名字段的老车型照常可用`() {
        val raw = JSONObject().put("latitude", 30.5).put("longitude", 120.5).put("soc", 60)

        assertEquals(30.5 to 120.5, parse(raw))
    }

    @Test
    fun `数字形式的坐标也能解析`() {
        val raw = JSONObject().put("2", 120.5).put("3", 30.5)

        assertEquals(30.5 to 120.5, parse(raw))
    }

    @Test
    fun `0 0 不算有效坐标`() {
        val raw = JSONObject().put("2", "0").put("3", "0")

        assertEquals(0.0 to 0.0, parse(raw))
        assertFalse(
            VehicleLocationRules.isValid(
                VehicleLocationDomain
                    .selectFromDecodedOrRaw(SignalTable.decode(raw).toValues())
                    .pair.latitude?.toString()?.toDoubleOrNull(),
                VehicleLocationDomain
                    .selectFromDecodedOrRaw(SignalTable.decode(raw).toValues())
                    .pair.longitude?.toString()?.toDoubleOrNull(),
            ),
        )
    }

    @Test
    fun `诊断区分没信号 非数字 零坐标三种情况`() {
        assertEquals(
            "报文里没有位置信号（2/3、3724/3725 都没出现）",
            VehicleLocationDiagnostics.describe(mapOf("soc" to "80")),
        )
        assertNotNull(
            VehicleLocationDiagnostics.describe(mapOf("3" to "北纬", "2" to "东经")),
        )
        assertEquals(
            "上报的坐标是 0,0（车机还没定位或已休眠）",
            VehicleLocationDiagnostics.describe(mapOf("3" to "0", "2" to "0")),
        )
        // 有可用坐标时不啰嗦
        assertNull(VehicleLocationDiagnostics.describe(mapOf("3" to "30.1", "2" to "120.1")))
    }
}
