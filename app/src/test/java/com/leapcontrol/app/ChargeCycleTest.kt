package com.leapcontrol.app

import com.leapcontrol.app.journal.ChargeAnchor
import com.leapcontrol.app.journal.ChargeCycle
import com.leapcontrol.app.journal.ChargeCycleState
import com.leapcontrol.app.journal.ChargeCycleUi
import com.leapcontrol.app.journal.ChargeEvidence
import com.leapcontrol.app.journal.ChargeSample
import com.leapcontrol.app.journal.JournalCardId
import com.leapcontrol.app.journal.JournalCardLayout
import com.leapcontrol.app.journal.JournalData
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
 * 这块逻辑全是纯函数，最该被钉住的是两件事：
 *  1. **误清**（把起点改到当下 = 卡片数字归零）的每一条路径；
 *  2. **误记**（同一次充电反复改写起点 / 把残影当充电）。
 *
 * 2026-11-06 用户反馈「总是自动清零」，根因是「看到一格 chargeState=1 就换起点」——
 * 而这一格在拔枪后、车休眠时都可能残留。现在换起点要么「车没动」（无害），
 * 要么必须有硬件旁证（[ChargeEvidence.CONFIRMED]），否则先挂候选。
 */
class ChargeCycleTest {

    private val now = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun sample(
        state: Int?,
        soc: Int? = null,
        km: Double? = null,
        moving: Boolean = false,
        evidence: ChargeEvidence = ChargeEvidence.UNKNOWN,
    ) = ChargeSample(chargeState = state, soc = soc, totalKm = km, moving = moving, evidence = evidence)

    private fun state(anchor: ChargeAnchor?, pending: ChargeAnchor? = null) =
        ChargeCycleState(anchor = anchor, pending = pending)

    // ---------------- 记起点 ----------------

    @Test
    fun `充电中且没有起点时记下当前总里程`() {
        val next = ChargeCycle.observe(null, sample(1, 35, 12_345.6), now)
        assertNotNull(next)
        assertEquals(12_345.6, next!!.anchor!!.km, 0.001)
        assertEquals(now, next.anchor!!.at)
        assertEquals(35, next.anchor!!.soc)
        assertTrue(!next.anchor!!.manual)
    }

    @Test
    fun `同一次充电刷新多次只记第一次`() {
        val first = state(ChargeAnchor(km = 12_000.0, at = now, soc = 20))
        val next = ChargeCycle.observe(first, sample(1, 55, 12_000.4, evidence = ChargeEvidence.CONFIRMED), now + hour)
        // 里程没变 → 是同一场充电，起点里程和时刻都不能被推着走
        assertEquals(12_000.0, next!!.anchor!!.km, 0.001)
        assertEquals(now, next.anchor!!.at)
        assertEquals(20, next.anchor!!.soc)
        // 变的只有「充到多少」：充电中看到的电量是充上去的
        assertEquals(55, next.anchor!!.socPeak)
    }

    @Test
    fun `充完电开了两公里再充，必须算新的一次`() {
        val morning = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19, ended = true))
        val afternoon = ChargeCycle.observe(
            morning,
            sample(1, 21, 12_002.0, evidence = ChargeEvidence.CONFIRMED),
            now + 5 * hour,
        )
        assertEquals(12_002.0, afternoon!!.anchor!!.km, 0.001)
        assertEquals(now + 5 * hour, afternoon.anchor!!.at)
        assertNull(afternoon.pending)
        // 重置之后卡片上的数字从 0 开始，不是接着上午的 2 公里
        val ui = ChargeCycle.summary(afternoon.anchor, 12_002.0) as ChargeCycleUi.Ready
        assertEquals(0.0, ui.km, 0.001)
    }

    @Test
    fun `哪怕只挪了半公里，也算新的一次`() {
        val first = state(ChargeAnchor(km = 12_000.0, at = now, soc = 40))
        val next = ChargeCycle.observe(
            first,
            sample(1, 40, 12_000.6, evidence = ChargeEvidence.CONFIRMED),
            now + 2 * hour,
        )
        assertEquals(12_000.6, next!!.anchor!!.km, 0.001)
    }

    /**
     * 一夜慢充 10 小时：以前「6 小时就算新的一次」会把「从 19% 充起」切成
     * 「从 83% 充起」。现在同一次充电只认「看见过未充电」和「里程变了」。
     */
    @Test
    fun `整夜慢充十小时仍算同一次，电量区间不被切段`() {
        var current = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19))
        listOf(2, 5, 8, 10).forEach { h ->
            val next = ChargeCycle.observe(
                current,
                sample(1, 19 + h * 8, 12_000.0, evidence = ChargeEvidence.CONFIRMED),
                now + h * hour,
            )
            if (next != null) current = next
        }
        assertEquals(now, current.anchor!!.at)
        assertEquals(19, current.anchor!!.soc)
        assertEquals(99, current.anchor!!.socPeak)
        assertEquals(12_000.0, current.anchor!!.km, 0.001)
    }

    // ---------------- 不能清零（本次修复的核心） ----------------

    /**
     * 用户 2026-11-06 报的那单：卡片上的数字「总是自动清零」。
     *
     * 残影路径：拔枪开出去几十公里，网关还回放上一次充电的 `chargeState=1`。
     * 以前看到这一格就把起点改到当下，数字立刻归零。
     */
    @Test
    fun `车开走后又报充电中，没有旁证时绝不清零`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        val next = ChargeCycle.observe(state(anchor), sample(1, 74, 12_046.0), now + 20 * hour)

        // 起点一动不动：卡片上仍然是「46 km」
        assertEquals(12_000.0, next!!.anchor!!.km, 0.001)
        assertEquals(now, next.anchor!!.at)
        assertEquals(46.0, (ChargeCycle.summary(next.anchor, 12_046.0) as ChargeCycleUi.Ready).km, 0.001)
        // 但也没丢掉线索：这次读数挂在候选上，等下一条证据
        assertEquals(12_046.0, next.pending!!.km, 0.001)
    }

    @Test
    fun `候选等到电量真的涨上来就转正，起点用当初那一刻的里程`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        val pending = ChargeAnchor(km = 12_046.0, at = now + 20 * hour, soc = 40)
        val next = ChargeCycle.observe(
            state(anchor, pending),
            sample(1, 52, 12_046.0),
            now + 21 * hour,
        )
        // 电量 40 → 52：这次充电是真的，起点落到看到它的那一刻（12_046）
        assertEquals(12_046.0, next!!.anchor!!.km, 0.001)
        assertEquals(now + 20 * hour, next.anchor!!.at)
        assertNull(next.pending)
        assertEquals(52, next.anchor!!.socPeak)
    }

    @Test
    fun `数量没涨、车却开走了，候选直接作废`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        val pending = ChargeAnchor(km = 12_046.0, at = now, soc = 40)
        val next = ChargeCycle.observe(state(anchor, pending), sample(0, 38, 12_120.0), now + 2 * hour)
        assertNull(next!!.pending)
        // 未充电：只给上一次充电盖「已结束」的戳，起点不动
        assertEquals(12_000.0, next.anchor!!.km, 0.001)
        assertTrue(next.anchor!!.ended)
    }

    @Test
    fun `候选放过六个小时就作废`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        val pending = ChargeAnchor(km = 12_046.0, at = now, soc = 40)
        val next = ChargeCycle.observe(
            state(anchor, pending),
            sample(0, 40, 12_046.0),
            now + ChargeCycle.PENDING_TTL_MS + hour,
        )
        assertNull(next!!.pending)
        assertEquals(12_000.0, next.anchor!!.km, 0.001)
    }

    @Test
    fun `有枪信号旁证时当场就能换起点`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        val next = ChargeCycle.observe(
            state(anchor),
            sample(1, 40, 12_046.0, evidence = ChargeEvidence.CONFIRMED),
            now + 20 * hour,
        )
        assertEquals(12_046.0, next!!.anchor!!.km, 0.001)
        assertNull(next.pending)
    }

    @Test
    fun `车在动时任何充电状态都不改变起点`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        assertNull(
            ChargeCycle.observe(
                state(anchor),
                sample(1, 40, 12_046.0, moving = true, evidence = ChargeEvidence.CONFIRMED),
                now + hour,
            ),
        )
    }

    @Test
    fun `明确没插枪时连候选都不挂`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true)
        assertNull(
            ChargeCycle.observe(
                state(anchor),
                sample(1, 40, 12_046.0, evidence = ChargeEvidence.REFUTED),
                now + hour,
            ),
        )
        // 车没动也一样：不能凭一格残影把卡上的「上次充电」改成今天
        assertNull(
            ChargeCycle.observe(
                state(anchor),
                sample(1, 40, 12_000.0, evidence = ChargeEvidence.REFUTED),
                now + hour,
            ),
        )
    }

    @Test
    fun `车没动时换起点是无害的，不需要旁证`() {
        // 车一直停在充电位上：改起点等于没改，数字不会变小 → 放行
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 40, ended = true)
        val next = ChargeCycle.observe(state(anchor), sample(1, 55, 12_000.2), now + 30 * hour)
        assertEquals(55, next!!.anchor!!.soc)
        assertEquals(now + 30 * hour, next.anchor!!.at)
        assertEquals(12_000.2, next.anchor!!.km, 0.001)
    }

    // ---------------- 旁证本身 ----------------

    @Test
    fun `交直流输入有电就是旁证，两路都断开就是反证`() {
        assertEquals(
            ChargeEvidence.CONFIRMED,
            ChargeCycle.evidence(inputOn = true, gunSignal = null, currentAmp = null),
        )
        assertEquals(
            ChargeEvidence.REFUTED,
            ChargeCycle.evidence(inputOn = false, gunSignal = null, currentAmp = 0.2),
        )
        // 枪信号说有、输入没读到：仍然算有电
        assertEquals(
            ChargeEvidence.CONFIRMED,
            ChargeCycle.evidence(inputOn = null, gunSignal = true, currentAmp = null),
        )
        // 三路都没读到：不知道，别猜
        assertEquals(
            ChargeEvidence.UNKNOWN,
            ChargeCycle.evidence(inputOn = null, gunSignal = null, currentAmp = null),
        )
    }

    @Test
    fun `电池有电流也算旁证，静置的小电流不算`() {
        // 直流快充几十上百安
        assertEquals(
            ChargeEvidence.CONFIRMED,
            ChargeCycle.evidence(inputOn = null, gunSignal = null, currentAmp = -18.6),
        )
        // 停着不充电时的噪声电流
        assertEquals(
            ChargeEvidence.UNKNOWN,
            ChargeCycle.evidence(inputOn = null, gunSignal = null, currentAmp = 0.3),
        )
    }

    // ---------------- 结束戳与「充到多少」 ----------------

    @Test
    fun `未充电时给这次充电盖结束戳`() {
        val running = ChargeAnchor(km = 12_000.0, at = now, soc = 80)
        val ended = ChargeCycle.observe(state(running), sample(0, 60, 12_100.0), now + hour)
        // 不产生新起点，只标记「这次已经结束」—— 起点里程不能跟着车走
        assertEquals(12_000.0, ended!!.anchor!!.km, 0.001)
        assertTrue(ended.anchor!!.ended)
        // 已经盖过戳了就不再动，别每次刷新都写一次盘
        assertNull(ChargeCycle.observe(ended, sample(0, 60, 12_200.0), now + 2 * hour))
        // 没有起点时无从盖戳
        assertNull(ChargeCycle.observe(null, sample(0, 60, 12_000.0), now))
        // 读不到充电状态（车型不报这个信号）时更不能瞎判
        assertNull(ChargeCycle.observe(state(running), sample(null, 60, 12_000.0), now))
    }

    @Test
    fun `充电中的里程抖动不该反复改写起点`() {
        val first = ChargeAnchor(km = 12_000.0, at = now, soc = 20)
        // 表显 0.1 的分辨率，充电时车停着，差 0.2 属于显示抖动
        val again = ChargeCycle.observe(state(first), sample(1, 55, 12_000.2), now + hour)
        assertEquals(12_000.0, again!!.anchor!!.km, 0.001)
        assertEquals(now, again.anchor!!.at)
        // 「充到多少」抬到 55 就到位了：同一个电量再看一眼不该再写一次盘
        assertNull(ChargeCycle.observe(again, sample(1, 55, 12_000.2), now + 2 * hour))
    }

    @Test
    fun `充电中看到电量上涨就把充到多少抬上去`() {
        val start = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19))
        val climbed = ChargeCycle.observe(start, sample(1, 89, 12_000.0), now + hour)
        assertEquals(19, climbed!!.anchor!!.soc)
        assertEquals(89, climbed.anchor!!.socPeak)
    }

    @Test
    fun `拔枪时车还在原地，那一刻的电量就是充到多少`() {
        val start = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19))
        val ended = ChargeCycle.observe(start, sample(0, 89, 12_000.0), now + 3 * hour)
        assertTrue(ended!!.anchor!!.ended)
        assertEquals(89, ended.anchor!!.socPeak)
    }

    @Test
    fun `开走了才看到未充电，不能拿行驶后的电量当充到多少`() {
        val start = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19))
        // 充到 89%，但期间一次都没打开过零控；现在开出来 6 公里，电量掉到 74%
        val ended = ChargeCycle.observe(start, sample(0, 74, 12_006.0), now + 3 * hour)
        assertTrue(ended!!.anchor!!.ended)
        // 宁可缺着，也不能写成「充到 74%」
        assertNull(ended.anchor!!.socPeak)
    }

    @Test
    fun `充到多少只涨不跌，掉回去的电量不改写`() {
        val start = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19, socPeak = 89))
        val drained = ChargeCycle.observe(start, sample(0, 74, 12_000.0), now + 5 * hour)
        assertTrue(drained!!.anchor!!.ended)
        assertEquals(89, drained.anchor!!.socPeak)
    }

    @Test
    fun `直流快充也要记起点和充到多少`() {
        val anchor = ChargeCycle.observe(null, sample(5, 30, 12_000.0), now)
        assertEquals(12_000.0, anchor!!.anchor!!.km, 0.001)
        val climbed = ChargeCycle.observe(anchor, sample(5, 80, 12_000.0), now + hour)
        assertEquals(80, climbed!!.anchor!!.socPeak)
        // 也不该被当成「已结束」去盖戳
        assertTrue(!climbed.anchor!!.ended)
    }

    @Test
    fun `已完成状态在没有起点时可以当起点`() {
        val anchor = ChargeCycle.observe(null, sample(2, 89, 12_500.0), now)
        // 只看见「已充满」，从多少充起的没观测到 —— 缺着，别拿 89 冒充
        assertNull(anchor!!.anchor!!.soc)
        assertEquals(89, anchor.anchor!!.socPeak)
        // 车端说充到终点了：卡上可以写「充至满电」，但绝不写 100%
        assertTrue(anchor.anchor!!.full)
    }

    @Test
    fun `已完成状态且车已开走时不能当成起点`() {
        val existing = state(ChargeAnchor(km = 12_000.0, at = now - 3 * hour, soc = 100))
        // 状态残留：已经开出去 40 公里，此刻的里程不是充电时那一格
        assertNull(ChargeCycle.observe(existing, sample(2, 100, 12_040.0), now))
    }

    @Test
    fun `车停在原地看到已充满时记下到顶，且不丢原来的起充电量`() {
        val start = state(ChargeAnchor(km = 12_000.0, at = now, soc = 19, socPeak = 88))
        val done = ChargeCycle.observe(start, sample(2, 90, 12_000.0), now + 3 * hour)
        assertEquals(90, done!!.anchor!!.socPeak)
        assertTrue(done.anchor!!.full)
        assertTrue(!done.anchor!!.ended)
        assertEquals(19, done.anchor!!.soc)
        assertEquals(now, done.anchor!!.at)
    }

    @Test
    fun `里程缺失或非法时不记录`() {
        assertNull(ChargeCycle.observe(null, sample(1, 50, null), now))
        assertNull(ChargeCycle.observe(null, sample(1, 50, 0.0), now))
        assertNull(ChargeCycle.observe(null, sample(1, 50, Double.NaN), now))
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

    /**
     * 里程回退时以前返回 [ChargeCycleUi.NoAnchor]，卡片上的数字直接消失 ——
     * 用户看到的和「清零」一模一样。现在单独给一个状态，卡片能说「点开重设」。
     */
    @Test
    fun `里程明显回退时判为起点不可信，而不是显示 0`() {
        val anchor = ChargeAnchor(km = 12_000.0, at = now, soc = 80)
        assertEquals(ChargeCycleUi.Mismatch, ChargeCycle.summary(anchor, 11_800.0))
        // 表显抖动（差 0.6 km）不算异常，按 0 显示
        val jitter = ChargeCycle.summary(anchor, 11_999.4) as ChargeCycleUi.Ready
        assertEquals(0.0, jitter.km, 0.001)
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
    fun `候选也要跟着账本一起存住`() {
        // 候选垮在重启里 = 下一次看到的电量上涨就没人认了，白等一个周期
        val data = JournalData(
            chargeAnchor = ChargeAnchor(km = 12_000.0, at = now, soc = 20, ended = true),
            chargePending = ChargeAnchor(km = 12_046.0, at = now + hour, soc = 40),
        )
        val back = JournalData.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(data.chargeAnchor, back.chargeAnchor)
        assertEquals(data.chargePending, back.chargePending)
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
