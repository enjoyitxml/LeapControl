package com.leapcontrol.app

import com.leapcontrol.app.journal.ChargeAnchor
import com.leapcontrol.app.journal.ChargeCycle
import com.leapcontrol.app.journal.ChargeCycleUi
import com.leapcontrol.app.journal.JournalCardId
import com.leapcontrol.app.journal.JournalCardLayout
import com.leapcontrol.app.ui.chargeLevelLine
import com.leapcontrol.app.ui.chargeLines
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
        // 里程没变、还在 6 小时内 → 是同一场充电，起点里程和时刻都不能被推着走
        assertNotNull(again)
        assertEquals(12_000.0, again!!.km, 0.001)
        assertEquals(now, again.at)
        assertEquals(20, again.soc)
        // 变的只有「充到多少」：充电中看到的电量是充上去的
        assertEquals(55, again.socPeak)
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
        assertNotNull(again)
        assertEquals(12_000.0, again!!.km, 0.001)
        assertEquals(now, again.at)
        // 「充到多少」抬到 55 就到位了：同一个电量再看一眼不该再写一次盘
        assertNull(ChargeCycle.observe(again, 1, 55, 12_000.2, now + 2 * hour))
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

    // ---------------- 「充到多少」的观测 ----------------

    /**
     * 用户 2026-11-03 报的那单：从 19% 充到 89%，卡上却写「19% → 74%」。
     *
     * 74 是「现在」，19 是「第一次看见充电」—— 两端隔了一整段充电，箭头读起来
     * 像「充到 74%」。根因是「充到多少」从来没被记过，显示时只好拿起点冒充。
     */
    @Test
    fun `充电中看到电量上涨就把充到多少抬上去`() {
        val start = ChargeAnchor(km = 12_000.0, at = now, soc = 19)
        val climbed = ChargeCycle.observe(start, 1, 89, 12_000.0, now + hour)
        assertNotNull(climbed)
        assertEquals(19, climbed!!.soc)
        assertEquals(89, climbed.socPeak)
    }

    @Test
    fun `拔枪时车还在原地，那一刻的电量就是充到多少`() {
        val start = ChargeAnchor(km = 12_000.0, at = now, soc = 19)
        val ended = ChargeCycle.observe(start, 0, 89, 12_000.0, now + 3 * hour)
        assertNotNull(ended)
        assertTrue(ended!!.ended)
        assertEquals(89, ended.socPeak)
    }

    @Test
    fun `开走了才看到未充电，不能拿行驶后的电量当充到多少`() {
        val start = ChargeAnchor(km = 12_000.0, at = now, soc = 19)
        // 充到 89%，但期间一次都没打开过零控；现在开出来 6 公里，电量掉到 74%
        val ended = ChargeCycle.observe(start, 0, 74, 12_006.0, now + 3 * hour)
        assertNotNull(ended)
        assertTrue(ended!!.ended)
        // 宁可缺着，也不能写成「充到 74%」
        assertNull(ended.socPeak)
    }

    @Test
    fun `充到多少只涨不跌，掉回去的电量不改写`() {
        val start = ChargeAnchor(km = 12_000.0, at = now, soc = 19, socPeak = 89)
        val drained = ChargeCycle.observe(start, 0, 74, 12_000.0, now + 5 * hour)
        assertNotNull(drained)
        assertTrue(drained!!.ended)
        assertEquals(89, drained.socPeak)
    }

    @Test
    fun `已完成状态当起点时只知道充到多少`() {
        val anchor = ChargeCycle.observe(null, 2, 89, 12_500.0, now)
        assertNotNull(anchor)
        // 只看见「已充满」，从多少充起的没观测到 —— 缺着，别拿 89 冒充
        assertNull(anchor!!.soc)
        assertEquals(89, anchor.socPeak)
        // 车端说充到终点了：卡上可以写「充至满电」，但绝不写 100%
        assertTrue(anchor.full)
    }

    /**
     * 直流快充（state=5）以前被当成「没在充电」，起点一路不更新 —— 快充车型上
     * 卡片会一直累计到上一次交流慢充。这是 2026-11-03 全面检查时发现的。
     */
    @Test
    fun `直流快充也要记起点和充到多少`() {
        val anchor = ChargeCycle.observe(null, 5, 30, 12_000.0, now)
        assertNotNull(anchor)
        assertEquals(12_000.0, anchor!!.km, 0.001)
        val climbed = ChargeCycle.observe(anchor, 5, 80, 12_000.0, now + hour)
        assertEquals(80, climbed!!.socPeak)
        // 也不该被当成「已结束」去盖戳
        assertTrue(!climbed.ended)
    }

    @Test
    fun `车停在原地看到已充满时记下到顶`() {
        val start = ChargeAnchor(km = 12_000.0, at = now, soc = 19, socPeak = 88)
        val done = ChargeCycle.observe(start, 2, 90, 12_000.0, now + 3 * hour)
        assertNotNull(done)
        assertEquals(90, done!!.socPeak)
        assertTrue(done.full)
        assertTrue(!done.ended)
    }

    @Test
    fun `车已开走时那条已充满是残影，不认`() {
        val start = ChargeAnchor(km = 12_000.0, at = now, soc = 19, socPeak = 88)
        val stale = ChargeCycle.observe(start, 2, 60, 12_040.0, now + 5 * hour)
        assertNull(stale)
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
        // 「充到多少」也要存得住；旧版存的记录没这个字段，读出来是 null 而不是崩
        val withPeak = ChargeAnchor(km = 12_345.6, at = now, soc = 19, socPeak = 89)
        assertEquals(withPeak, ChargeAnchor.fromJson(JSONObject(withPeak.toJson().toString())))
        val legacy = JSONObject().put("km", 12_345.6).put("at", now).put("soc", 19)
        assertEquals(null, ChargeAnchor.fromJson(legacy)!!.socPeak)
        // 「车端说充满」也要存得住
        val full = ChargeAnchor(km = 12_345.6, at = now, soc = 19, full = true)
        assertEquals(full, ChargeAnchor.fromJson(JSONObject(full.toJson().toString())))
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

    // ---------------- 卡片上的电量行 ----------------

    @Test
    fun `卡上要同时说清充到多少和目前多少`() {
        assertEquals(listOf("充 19% → 89%", "余 74%"), chargeLines(from = 19, to = 89, now = 74))
    }

    @Test
    fun `没观测到充到多少时只写目前，不拿起点冒充`() {
        // 这正是用户报的那单：19% 充到 89%，卡上却写 19% → 74%
        assertEquals(listOf("余 74%"), chargeLines(from = 19, to = null, now = 74))
    }

    @Test
    fun `只看得见充到多少时写充至`() {
        assertEquals(listOf("充至 89%", "余 74%"), chargeLines(from = null, to = 89, now = 74))
        assertEquals(listOf("充至 89%", "余 74%"), chargeLines(from = 89, to = 89, now = 74))
    }

    @Test
    fun `三个数都缺时整块留空`() {
        assertEquals(emptyList<String>(), chargeLines(from = null, to = null, now = null))
    }

    @Test
    fun `只知道车端说充满时写满电，不写 100%`() {
        assertEquals(listOf("充至满电", "余 74%"), chargeLines(from = 19, to = null, now = 74, full = true))
    }

    @Test
    fun `有具体电量时以数字为准，不叠加满电二字`() {
        assertEquals(
            listOf("充 19% → 89%", "余 74%"),
            chargeLines(from = 19, to = 89, now = 74, full = true),
        )
    }

    @Test
    fun `弹层把三个数说全`() {
        assertEquals(
            "上次充电从 19% 充到 89%，目前 74%。",
            chargeLevelLine(ChargeAnchor(km = 12_000.0, at = now, soc = 19, socPeak = 89), 74),
        )
    }

    @Test
    fun `弹层在充到多少缺失时说清为什么`() {
        assertEquals(
            "目前 74%。这次充到多少没记上：充电期间零控每 10 分钟自动记一次，" +
                "连不上车（没网、或后台被系统清理）就会缺这一格。",
            chargeLevelLine(ChargeAnchor(km = 12_000.0, at = now, soc = 19), 74),
        )
    }

    @Test
    fun `弹层在只知道充满时说充满`() {
        assertEquals(
            "上次充电车端报充满，目前 74%。",
            chargeLevelLine(ChargeAnchor(km = 12_000.0, at = now, soc = 19, full = true), 74),
        )
    }
}
