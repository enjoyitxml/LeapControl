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
    fun `未充电时给这次充电盖结束戳`() {
        val running = ChargeAnchor(km = 12_000.0, at = now, soc = 80)
        val ended = ChargeCycle.observe(
            anchor = running,
            chargeState = 0,
            soc = 60,
            totalKm = 12_100.0,
            now = now + hour,
        )
        // 不产生新起点，只标记「这次已经结束」—— 起点里程不能跟着车走
        assertNotNull(ended)
        assertEquals(12_000.0, ended!!.km, 0.001)
        assertTrue(ended.ended)
        // 已经盖过戳了就不再动，别每次刷新都写一次盘
        assertNull(ChargeCycle.observe(ended, 0, 60, 12_200.0, now + 2 * hour))
        // 没有起点时无从盖戳
        assertNull(ChargeCycle.observe(null, 0, 60, 12_000.0, now))
        // 读不到充电状态（车型不报这个信号）时更不能瞎判
        assertNull(ChargeCycle.observe(running, null, 60, 12_000.0, now))
    }

    /**
     * 用户 2026-11-02 报的那个 bug：一天充两次电，第一次 19%→30%，然后开了 2 公里，
     * 下午再充 —— 卡片上的「自上次充电行驶里程」没归零，还接着上午那次累计。
     *
     * 原因是判定「同一次充电」的里程容差写成了 2 km，正好把「开了 2 公里」吞掉。
     */
    @Test
    fun `充完电开了两公里再充，必须算新的一次`() {
        val morning = ChargeAnchor(km = 12_000.0, at = now, soc = 19)
        val afternoon = ChargeCycle.observe(
            anchor = morning,
            chargeState = 1,
            soc = 21,
            totalKm = 12_002.0,
            now = now + 5 * hour,
        )
        assertNotNull("开了 2 公里就是另一次充电了", afternoon)
        assertEquals(12_002.0, afternoon!!.km, 0.001)
        assertEquals(now + 5 * hour, afternoon.at)
        // 重置之后卡片上的数字从 0 开始，不是接着上午的 2 公里
        val ui = ChargeCycle.summary(afternoon, 12_002.0) as ChargeCycleUi.Ready
        assertEquals(0.0, ui.km, 0.001)
    }

    @Test
    fun `哪怕只挪了半公里，也算新的一次`() {
        val first = ChargeAnchor(km = 12_000.0, at = now, soc = 40)
        val next = ChargeCycle.observe(first, 1, 40, 12_000.6, now + 2 * hour)
        assertNotNull(next)
        assertEquals(12_000.6, next!!.km, 0.001)
    }

    @Test
    fun `中间看见过未充电时，里程几乎没变也要重置`() {
        // 挪了 0.2 km（不足容差），但车开出去时 App 刷新过 → 已经盖过 ended 戳
        val ended = ChargeAnchor(km = 12_000.0, at = now, soc = 40, ended = true)
        val next = ChargeCycle.observe(ended, 1, 38, 12_000.2, now + hour)
        assertNotNull(next)
        assertEquals(now + hour, next!!.at)
        // 新起点是新的一次充电，ended 必须清掉，否则下一次又白重置
        assertTrue(!next.ended)
    }

    @Test
    fun `充电中的里程抖动不该反复改写起点`() {
        val first = ChargeAnchor(km = 12_000.0, at = now, soc = 20)
        // 表显 0.1 的分辨率，充电时车停着，差 0.2 属于显示抖动
        val again = ChargeCycle.observe(first, 1, 55, 12_000.2, now + hour)
        assertNull(again)
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
        // 「已结束」这个戳必须一起存下来，否则重启后第二次充电又会白算
        val ended = ChargeAnchor(km = 12_345.6, at = now, soc = 42, ended = true)
        assertEquals(ended, ChargeAnchor.fromJson(JSONObject(ended.toJson().toString())))
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
