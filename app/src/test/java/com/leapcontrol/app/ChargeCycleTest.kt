package com.leapcontrol.app

import com.leapcontrol.app.journal.ChargeAnchor
import com.leapcontrol.app.journal.ChargeCycle
import com.leapcontrol.app.journal.ChargeCycleUi
import com.leapcontrol.app.journal.JournalCardId
import com.leapcontrol.app.journal.JournalCardLayout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「自上次充电后行驶里程」的观测规则。
 *
 * 这块逻辑全是纯函数，最该被钉住的是两个「别记错」的分支：
 * 同一次充电不要反复改写起点；state=2 的残留状态不能当成起点。
 */
class ChargeCycleTest {

    private val now = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    // ---------------- 观测 ----------------

    @Test
    fun `充电中且没有起点时记下当前总里程`() {
        val anchor = ChargeCycle.observe(
            anchor = null,
            chargeState = 1,
            soc = 35,
            totalKm = 12_345.6,
            now = now,
        )
        assertNotNull(anchor)
        assertEquals(12_345.6, anchor!!.km, 0.001)
        assertEquals(now, anchor.at)
        assertEquals(35, anchor.soc)
        assertTrue(!anchor.manual)
    }

    @Test
    fun `同一次充电刷新多次只记第一次`() {
        val first = ChargeAnchor(km = 12_000.0, at = now, soc = 20)
        val again = ChargeCycle.observe(
            anchor = first,
            chargeState = 1,
            soc = 55,
            totalKm = 12_000.4,
            now = now + hour,
        )
        // 里程没变、还在 6 小时内 → 是同一场充电，不该把起点时间越推越晚
        assertNull(again)
    }

    @Test
    fun `隔了很久又看到充电中，算作新的一次`() {
        val old = ChargeAnchor(km = 12_000.0, at = now - ChargeCycle.SAME_SESSION_MS - hour, soc = 20)
        val next = ChargeCycle.observe(
            anchor = old,
            chargeState = 1,
            soc = 18,
            totalKm = 12_000.2,
            now = now,
        )
        assertNotNull(next)
        assertEquals(now, next!!.at)
    }

    @Test
    fun `未充电时什么都不做`() {
        assertNull(
            ChargeCycle.observe(
                anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 80),
                chargeState = 0,
                soc = 60,
                totalKm = 12_100.0,
                now = now + hour,
            ),
        )
    }

    @Test
    fun `已完成状态在没有起点时可以当起点`() {
        val anchor = ChargeCycle.observe(
            anchor = null,
            chargeState = 2,
            soc = 100,
            totalKm = 12_500.0,
            now = now,
        )
        assertNotNull(anchor)
        assertEquals(12_500.0, anchor!!.km, 0.001)
    }

    @Test
    fun `已完成状态且车已开走时不能当成起点`() {
        val existing = ChargeAnchor(km = 12_000.0, at = now - 3 * hour, soc = 100)
        val next = ChargeCycle.observe(
            anchor = existing,
            chargeState = 2,
            soc = 100,
            // 状态残留：已经开出去 40 公里，此刻的里程不是充电时那一格
            totalKm = 12_040.0,
            now = now,
        )
        assertNull(next)
    }

    @Test
    fun `里程缺失或非法时不记录`() {
        assertNull(ChargeCycle.observe(null, 1, 50, null, now))
        assertNull(ChargeCycle.observe(null, 1, 50, 0.0, now))
        assertNull(ChargeCycle.observe(null, 1, 50, Double.NaN, now))
    }

    // ---------------- 展示 ----------------

    @Test
    fun `没有起点时返回引导态`() {
        assertEquals(ChargeCycleUi.NoAnchor, ChargeCycle.summary(null, 12_345.0))
    }

    @Test
    fun `有起点但读不到当前里程`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 80)
        assertEquals(ChargeCycleUi.NoKm, ChargeCycle.summary(anchor, null))
    }

    @Test
    fun `里程差就是自上次充电后行驶里程`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 80)
        val ui = ChargeCycle.summary(anchor, 12_180.5) as ChargeCycleUi.Ready
        assertEquals(180.5, ui.km, 0.001)
        assertEquals(now, ui.at)
        assertEquals(80, ui.soc)
    }

    @Test
    fun `里程回退时判为起点不可信`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 80)
        // 换仪表 / 数据异常：当前里程比起点还小，绝不能显示负数
        assertEquals(ChargeCycleUi.NoAnchor, ChargeCycle.summary(anchor, 11_800.0))
    }

    // ---------------- 存储 ----------------

    @Test
    fun `起点能完整存进 JSON 再读回来`() {
        val anchor = ChargeAnchor(km = 12_345.6, at = now, soc = 42, manual = true)
        val back = ChargeAnchor.fromJson(JSONObject(anchor.toJson().toString()))
        assertEquals(anchor, back)
    }

    @Test
    fun `脏数据读出来是空`() {
        assertNull(ChargeAnchor.fromJson(null))
        assertNull(ChargeAnchor.fromJson(JSONObject().put("km", 0).put("at", now)))
        assertNull(ChargeAnchor.fromJson(JSONObject().put("km", 1234.0)))
    }

    // ---------------- 卡片 ----------------

    @Test
    fun `充电里程只出现在零控首页，不再是零记卡片`() {
        // 这张卡按用户要求放在零控首页（HomeCardId.CHARGE_CYCLE），
        // 零记概览不该再出现第二份同样的数字
        assertTrue(JournalCardId.entries.none { it.name == "CHARGE_CYCLE" })
        assertTrue(HomeCardId.entries.any { it == HomeCardId.CHARGE_CYCLE })
    }
}
