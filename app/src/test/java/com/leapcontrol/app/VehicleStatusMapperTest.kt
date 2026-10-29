package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 续航取值与动力类型判定。
 *
 * 回归背景：纯电 C01 用户反馈首页续航与电/油两行全是「--」。根因有两条 ——
 * ① 车端对纯电车同样下发 3235 fuelSoc = 0，旧判据「fuelSoc 存在即增程」把纯电车
 * 认成增程，主续航去取综合续航（纯电没有这一路）；
 * ② 纯电 C01 只下发 2188（仪表盘主显示续航），而续航取值链里只有 3257/3260，
 * 且 2188 此前只给 C11 开了口子。
 */
class VehicleStatusMapperTest {

    private fun status(json: String): JSONObject = SignalTable.decode(JSONObject(json))

    @Test
    fun `纯电 C01 只给 2188 时主续航与电续航都能取到`() {
        val st = status("""{"1204":81,"100003":81.2,"3235":0,"2188":412,"1318":12845}""")
        assertEquals("412", VehicleStatusMapper.electricRange(st))
        assertEquals("412", VehicleStatusMapper.remainingRange(st, "C01"))
        assertNull(VehicleStatusMapper.fuelRange(st))
    }

    @Test
    fun `纯电车 fuelSoc 为 0 也算纯电，不进增程分支`() {
        val st = status("""{"1204":81,"3235":0,"2188":412}""")
        assertFalse(
            VehiclePowerTypeResolver.isRangeExtender(
                status = st,
                configuredPowerType = null,
                carType = "C01",
            )
        )
    }

    @Test
    fun `有燃油比例才算增程`() {
        val st = status("""{"1204":55,"3235":42,"2188":612}""")
        assertTrue(
            VehiclePowerTypeResolver.isRangeExtender(
                status = st,
                configuredPowerType = null,
                carType = "C11",
            )
        )
    }

    @Test
    fun `增程车有完整信号时主续航取综合续航`() {
        val st = status(
            """{"3262":1,"3260":291,"3259":896,"3261":1187,"3235":100,"1204":60}"""
        )
        assertEquals("1187", VehicleStatusMapper.combinedRange(st))
        assertEquals("896", VehicleStatusMapper.fuelRange(st))
        assertEquals(
            "1187",
            VehicleStatusMapper.remainingRange(
                st,
                "C11",
                VehicleStatusMapper.PowerType.RANGE_EXTENDER,
            ),
        )
    }

    /**
     * 关键回归：增程车的 2188 往往等于综合续航。若燃油推导用带 2188 兜底的
     * electricRange，combined - electric 会算出 0，燃油里程凭空消失。
     */
    @Test
    fun `2188 不参与燃油里程推导`() {
        val st = status(
            """{"3262":1,"2188":1187,"3260":291,"3261":1187,"3235":50,"1204":60}"""
        )
        assertEquals("896", VehicleStatusMapper.fuelRange(st))
    }

    @Test
    fun `工况模式缺失时退回报文中实际存在的续航信号`() {
        val st = status("""{"1204":66,"3257":530}""")
        assertEquals("530", VehicleStatusMapper.remainingRange(st))
        assertEquals("530", VehicleStatusMapper.electricRange(st))
    }

    @Test
    fun `手动指定纯电时覆盖燃油信号`() {
        val st = status("""{"1204":55,"3235":42,"2188":612}""")
        assertFalse(
            VehiclePowerTypeResolver.isRangeExtender(
                status = st,
                configuredPowerType = SessionStore.VehiclePowerType.PURE_ELECTRIC,
                carType = "C11",
            )
        )
    }

    @Test
    fun `手动指定增程时即便没有燃油信号也按增程显示`() {
        val st = status("""{"1204":81,"3235":0,"2188":412}""")
        assertTrue(
            VehiclePowerTypeResolver.isRangeExtender(
                status = st,
                configuredPowerType = SessionStore.VehiclePowerType.RANGE_EXTENDER,
                carType = "C01",
            )
        )
    }

    @Test
    fun `车型名带增程关键字时按增程判定`() {
        val st = status("""{"1204":81}""")
        assertTrue(
            VehiclePowerTypeResolver.isRangeExtender(
                status = st,
                configuredPowerType = null,
                carType = "C11 增程 285 智享版",
            )
        )
    }

    @Test
    fun `纯电车型名带 EV 时按纯电判定`() {
        val st = status("""{"1204":81,"2188":412}""")
        assertFalse(
            VehiclePowerTypeResolver.isRangeExtender(
                status = st,
                configuredPowerType = null,
                carType = "T03 EV",
            )
        )
    }
}
