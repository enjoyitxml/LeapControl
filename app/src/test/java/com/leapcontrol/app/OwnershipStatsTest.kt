package com.leapcontrol.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「与爱车相伴」口径换算。
 *
 * 这组用例锁三件事：
 *  1. 天数只认接口给的合理值 —— 0 天、负数、明显是时间戳的脏数据都要变成 null，
 *     否则卡片会显示「走过 19723 天」这种一眼假的数字；
 *  2. 里程文本的多种写法都要能抠出数字（带千分位、带单位、带小数）；
 *  3. 解析器能在嵌套对象里找到提车天数 —— 服务端把它塞进子对象时不能丢。
 */
class OwnershipStatsTest {

    @Test
    fun `天数正常值保留`() {
        assertEquals(1, OwnershipStats.days(1))
        assertEquals(128, OwnershipStats.days(128))
        assertEquals(3650, OwnershipStats.days(3650))
    }

    @Test
    fun `天数缺失或异常变 null`() {
        assertNull(OwnershipStats.days(null))
        assertNull("0 天多半是缺省值", OwnershipStats.days(0))
        assertNull("负数不合法", OwnershipStats.days(-5))
        assertNull("秒级时间戳被当成天数", OwnershipStats.days(1_760_000_000))
    }

    @Test
    fun `里程文本抠数字`() {
        assertEquals(12345.0, OwnershipStats.kmFromText("12345")!!, 0.001)
        assertEquals(12345.0, OwnershipStats.kmFromText("12,345")!!, 0.001)
        assertEquals(12345.0, OwnershipStats.kmFromText("12,345 km")!!, 0.001)
        assertEquals(12345.6, OwnershipStats.kmFromText("12345.6公里")!!, 0.001)
        assertEquals(12345.0, OwnershipStats.kmFromText(" 12 345 ")!!, 0.001)
    }

    @Test
    fun `里程文本为空或没有数字变 null`() {
        assertNull(OwnershipStats.kmFromText(null))
        assertNull(OwnershipStats.kmFromText(""))
        assertNull(OwnershipStats.kmFromText("--"))
        assertNull("离谱的大数不认", OwnershipStats.kmFromText("9999999999"))
    }

    @Test
    fun `公里格式化带千分位且取整`() {
        assertEquals("12,345", OwnershipStats.formatKm(12345.0))
        assertEquals("12,346", OwnershipStats.formatKm(12345.6))
        assertEquals("0", OwnershipStats.formatKm(0.0))
        assertEquals("1,000,000", OwnershipStats.formatKm(1_000_000.0))
        assertNull(OwnershipStats.formatKm(null))
    }

    @Test
    fun `提车天数在顶层能被解析`() {
        val resp = JSONObject(
            """
            {
              "code": 0,
              "data": {
                "totalmileage": 12345.0,
                "deliveryDays": 128
              }
            }
            """.trimIndent(),
        )
        assertEquals(128, MileageEnergyParser.parseRecent(resp)?.ownershipDays)
    }

    @Test
    fun `提车天数在嵌套对象里也能被解析`() {
        val resp = JSONObject(
            """
            {
              "code": 0,
              "data": {
                "totalmileage": 12345.0,
                "vehicleInfo": { "pickupDays": 96 }
              }
            }
            """.trimIndent(),
        )
        assertEquals(96, MileageEnergyParser.parseRecent(resp)?.ownershipDays)
    }

    @Test
    fun `当前总里程表显优先且都不认零值`() {
        // 表显里程优先：里程接口的累计值在服务端按行驶记录汇总，实测滞后一天以上
        assertEquals(
            12345.6,
            OwnershipStats.currentKm("12345.6km", 12000.0)!!,
            0.001,
        )
        // 表显取不到才退回接口值
        assertEquals(12000.0, OwnershipStats.currentKm(null, 12000.0)!!, 0.001)
        assertEquals(12000.0, OwnershipStats.currentKm("暂无", 12000.0)!!, 0.001)
        // 0 km 是「没上报」，不能当有效里程 —— 否则剩余保养里程会按 0 算，直接报成已超期
        assertEquals(12000.0, OwnershipStats.currentKm("0 km", 12000.0)!!, 0.001)
        // 两边都是 0 / 都取不到时给 null，让卡片走「暂无数据」而不是显示 0
        assertNull(OwnershipStats.currentKm("0km", 0.0))
        assertNull(OwnershipStats.currentKm("—", null))
        assertNull(OwnershipStats.currentKm(null, null))
    }
}
