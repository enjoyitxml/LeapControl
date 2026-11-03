package com.leapcontrol.app.journal

import kotlin.math.abs
import org.json.JSONObject

/**
 * 「自上次充电后」的起点：**充上电那一刻的表显总里程**。
 *
 * 车端没有充电历史接口（信号表里只有 `chargeState` 这一个实时状态，没有行程 / 分段里程），
 * 所以这条起点只能靠 App 自己「看见」充电时记下来 —— 详见 [ChargeCycle.observe]。
 *
 * [soc] 与 [socPeak] 是一对：**从多少充到多少**，卡上要一起写出来。
 *  - [soc] = 第一次看见这次充电时的电量（「从 X% 充起」）；
 *  - [socPeak] = 这次充电**充到多少**（「充到 Y%」）。
 *
 * ## socPeak 为什么是「往上抬」而不是创建时就填
 * 创建锚点的那一刻充电还没结束，那时的电量只是个下限 —— 照着它写「充到 55%」，
 * 充到 89% 就成了谎报。所以它从第一格开始每观测一次就往高处抬，只涨不跌，
 * 直到拔枪定格（见 [ChargeCycle.observe]）。
 * 代价是：整段充电期间一次都没看过车况、充完又先开走了 —— 这个值就永远缺着。
 * 缺着就缺着，宁可只写「余 74%」，也不拼一个假区间出来。采样由 [ChargeWatch]
 * 在充电期间自动完成，用户不必记得打开 App。
 *
 * ## [full] 与 [socPeak] 是两件事
 * 车端 `chargeState=2`（已完成）只说明「这次充电结束了、车端认定充到了头」，
 * **不告诉具体百分比** —— 健康充电上限 90% 时它也报 2。所以充满只能记成
 * 「充到满电」这一句话，不能拿 100% 冒认。
 */
data class ChargeAnchor(
    /** 充电起点的表显总里程（km）。 */
    val km: Double,
    /** 记下这个起点的时刻（毫秒）。 */
    val at: Long,
    /** 「从 X% 充起」：第一次看见这次充电时的电量（%），可能没有。 */
    val soc: Int? = null,
    /** 「充到 Y%」：这次充电的满电电量（%），没观测到就 null。 */
    val socPeak: Int? = null,
    /**
     * 车端报告这次充电「已完成」（`chargeState=2`，且车还在原地）。
     *
     * 它只说明充到了车端认定的终点，**不含具体百分比**：健康充电上限设成 90% 时
     * 车端同样报 2。所以卡上写「充到满电」，不写 100%。
     */
    val full: Boolean = false,
    /** true = 用户手动「从现在起算」；false = 观测到充电自动记的。 */
    val manual: Boolean = false,
    /**
     * 这一次充电已经收尾：之后观测到过「没在充电」。
     *
     * 这是切分「两次充电」最硬的一条证据 —— 只要车开出去过、App 又刷新过一次车况，
     * 下一次进入充电状态必然是新的一次，不必再靠里程差去猜。
     */
    val ended: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("km", km)
        put("at", at)
        soc?.let { put("soc", it) }
        socPeak?.let { put("socPeak", it) }
        if (full) put("full", true)
        if (manual) put("manual", true)
        if (ended) put("ended", true)
    }

    companion object {
        fun fromJson(raw: JSONObject?): ChargeAnchor? {
            if (raw == null) return null
            val km = raw.opt("km")?.toString()?.toDoubleOrNull() ?: return null
            if (!ChargeCycle.isUsableKm(km)) return null
            val at = raw.opt("at")?.toString()?.toLongOrNull() ?: 0L
            if (at <= 0L) return null
            return ChargeAnchor(
                km = km,
                at = at,
                soc = raw.opt("soc")?.toString()?.toIntOrNull()?.takeIf { it in 0..100 },
                socPeak = raw.opt("socPeak")?.toString()?.toIntOrNull()?.takeIf { it in 0..100 },
                full = raw.optBoolean("full", false),
                manual = raw.optBoolean("manual", false),
                ended = raw.optBoolean("ended", false),
            )
        }
    }
}

/**
 * 卡片要展示的状态。
 *
 * 「没有数字」有两种原因，要分开说（和位置卡那套同一个原则）：
 * 一种是还没记录过，另一种是拿不到当前里程 —— 对用户来说该做的事完全不同。
 */
sealed interface ChargeCycleUi {
    /** 从没记录过起点：提示「充电时打开一次零控即可自动记录」。 */
    data object NoAnchor : ChargeCycleUi

    /** 有起点，但这会儿读不到当前表显里程。 */
    data object NoKm : ChargeCycleUi

    data class Ready(
        /** 自上次充电后跑了多少公里（非负）。 */
        val km: Double,
        /** 起点时刻（毫秒）。 */
        val at: Long,
        /** 「从 X% 充起」：第一次看见这次充电时的电量。 */
        val soc: Int?,
        /** 「充到 Y%」：这次充电的满电电量；没观测到就 null。 */
        val socPeak: Int?,
        /** 车端确认这次充电已充到终点（具体百分比未知）。 */
        val full: Boolean,
        /** true = 这个起点是用户手动指定的。 */
        val manual: Boolean,
    ) : ChargeCycleUi
}

/**
 * 「自上次充电后行驶里程」的观测与换算（纯逻辑，无 Android 依赖，可单测）。
 *
 * ## 为什么是「观测式」而不是「查询式」
 * 车端只给两样东西：**实时充电状态**（`chargeState`）和**累计总里程**。没有
 * 「上次充电后里程」这种行程信号，也没有充电记录接口。所以只能在 App 拿到车况时自己看：
 *
 *  - 充电时车是停着的，**总里程不变** —— 所以不需要精确抓住「充电结束那一刻」，
 *    只要在充电期间任何时刻看到过一次，记下的里程就是准确起点；
 *  - 之后 `当前总里程 − 起点` 就是「自上次充电后行驶里程」，精度等于表显里程的分辨率。
 *
 * ## 怎么判断「这是另一次充电」
 * 观测式最难的不是记起点，而是**认出第二次充电** —— 记错的表现就是「充完电又跑了一段，
 * 下次充电后数字还在往上累」。三条判据，任一成立就算新的一次：
 *
 *  1. **中间看见过「没在充电」**（[ChargeAnchor.ended]）：车一开走就会被看到
 *     （进首页 / 手动刷新 / 桌面小组件），这是最硬的一条；
 *  2. **里程变了**：充电时车不可能移动，所以两次观测的总里程差超过
 *     [MOVE_TOLERANCE_KM] 只可能是「车真的开过」，那必然是新的一次。
 *     * 这一条原来写成 2 km（怕里程抖动），结果「充完开两公里又充」正好被吞掉 ——
 *     起点不重置，卡片接着上一次累计。表显分辨率是 0.1 km，容差收到 0.5 就够，
 *     既容得下显示抖动，也认得出短途挪车；
 *  3. **隔得太久**（[SAME_SESSION_MS]）：里程没变、又没看到「未充电」，
 *     但那已经是六个小时前的事了 —— 重置一下没有代价（车没动，起点里程一样），
 *     却能让「上午充一次、下午原地再插枪」也翻篇。
 *
 * ## 漏检
 * 充电期间一次都没拿到过车况（插枪前没打开过零控、充完直接开走）—— 这次充电就看不到了，
 * 起点会停在更早那次，数字偏大。
 *
 * 中间那一段已经不需要人管了：充电时 [ChargeWatch] 每 10 分钟自动采一次，拔枪即停。
 * 但**第一次**仍然得有人碰一下（打开 App / 点小组件）—— 没有常驻服务时，车端不可能
 * 主动告诉手机「我刚插上枪」；要做成完全免操作就只剩常驻低频轮询，那是另一个决定。
 *
 * 剩下的漏检没法从数据里补救（没有充电记录可查），所以：
 * 卡片上永远写着「上次充电 X 月 X 日」让用户自己判断新鲜度，并提供一个
 * 「从现在起算」（[ChargeCycle] 之外由 UI 调 `restartChargeCycle`）把起点拉回当下。
 */
object ChargeCycle {

    /**
     * 同一次充电的判定窗口：这段时间内、里程没变，就还认为在充同一次。
     *
     * 超窗只是「重置一次起点」，不是错误：充电时车停着，起点里程本来就一样，
     * 变的只有日期和起充电量。所以这个值宁可小一点，让「上午一次、下午一次」也能分开。
     */
    const val SAME_SESSION_MS = 6 * 60 * 60 * 1000L

    /** 表显里程的合理上限，超过就是脏数据（与 OwnershipStats 同一口径）。 */
    private const val MAX_KM = 2_000_000.0

    /**
     * 「车没动」的容差（km）。表显分辨率是 0.1，充电时车必然静止，
     * 所以差 0.5 以上只可能是真开过车；0.3~0.4 之间的显示抖动仍能容下。
     */
    private const val MOVE_TOLERANCE_KM = 0.5

    /**
     * 正在往电池里灌电的状态：`1` 交流 / 常规充电中、`5` 直流快充中。
     *
     * `5` 是上游与 [com.leapcontrol.app.ChargeStatus.isGunConnected] 都认的「直流充电中」。
     * 以前这里只认 `1`，结果**直流快充全程记不到起点**：车端报 5 的车型永远停在上一次
     * 交流慢充，卡片上的里程越攒越大。这是 2026-11-03 全面检查时补上的。
     */
    private val CHARGING_STATES = setOf(1, 5)

    /** 状态 2 = 充电已完成（见 ChargeStatus）。 */
    private const val STATE_DONE = 2

    fun isUsableKm(km: Double?): Boolean =
        km != null && km.isFinite() && km > 0.0 && km < MAX_KM

    /**
     * 观测一次车况，返回**新的起点**；返回 null 表示这次观测不改变任何东西。
     *
     * 每次拿到车况都该调一次（不限于正在充电时）—— 只有「不在充电」的那些观测
     * 才能给上一次充电盖上 [ChargeAnchor.ended] 的戳，那是下次能正确重置的前提。
     */
    fun observe(
        anchor: ChargeAnchor?,
        chargeState: Int?,
        soc: Int?,
        totalKm: Double?,
        now: Long,
    ): ChargeAnchor? {
        // 读不到充电状态：判断不了这次观测属于哪一次充电，什么都别动
        if (chargeState == null) return null

        // 「车还停在充电那一格」：充电时车不动，所以里程差在容差内就等于还在充电位上。
        // 读不到里程就当它动过 —— 宁可缺着，也不能把行驶后的电量写成充到的电量。
        val km = totalKm?.takeIf { isUsableKm(it) }
        val parked = km != null && anchor != null && abs(km - anchor.km) <= MOVE_TOLERANCE_KM

        // ① 没在充电（0 未插枪 / 3 故障 / 4 预约等待 / 6 暂停）：盖「已结束」的戳。
        //    4 也归这一类 —— 预约等待结束、真正开始充电，那就是新的一次。
        //    这一格同时是「充到多少」的定格时机：充电停了、车没动，此刻电量就是充完那一格。
        if (chargeState !in CHARGING_STATES && chargeState != STATE_DONE) {
            if (anchor == null || anchor.ended) return null
            val ended = anchor.copy(ended = true)
            return raiseChargeLevel(ended, soc, reachedFull = false, parked = parked) ?: ended
        }

        // ② 读不到总里程：宁可留着旧起点，也不写一个瞎猜的
        if (km == null) return null

        // ③ 这次观测还算不算「同一次充电」
        val sameSession = anchor != null && !anchor.ended &&
            abs(anchor.km - km) <= MOVE_TOLERANCE_KM &&
            now - anchor.at < SAME_SESSION_MS

        if (chargeState in CHARGING_STATES) {
            // 充电中车一定停着：里程变了就是另一次充电，时间隔久了也翻篇。
            // 新的一次先不填「充到多少」（还没充完，照着此刻写只会低估），
            // 同一次则把观测到的最高电量抬上去 —— 采样由 ChargeWatch 在充电期间自动完成。
            return if (sameSession) {
                raiseChargeLevel(anchor!!, soc, reachedFull = false, parked = true)
            } else {
                ChargeAnchor(km = km, at = now, soc = soc)
            }
        }

        // ④ state=2 会残留到拔枪之后，甚至车已经开走。
        //    只看见「已充满」时从多少充起的无从得知 —— 只填「充到多少」，别拿它冒充起点。
        if (anchor == null) {
            return ChargeAnchor(km = km, at = now, socPeak = soc, full = true)
        }
        // 车明确开过一段 → 这条「已充满」是残影，此刻的里程不是充电时那一格
        if (km > anchor.km + MOVE_TOLERANCE_KM) return null
        if (!sameSession) return ChargeAnchor(km = km, at = now, socPeak = soc, full = true)
        // 同一次充电的「已充满」：车没动过，此刻电量就是充到的那格，同时记下「到顶了」
        return raiseChargeLevel(anchor, soc, reachedFull = true, parked = true)
    }

    /**
     * 把「这次充到多少」往上报到观测到的最高电量，并记下「车端说到顶了」。
     *
     * 只在**车还停在充电那一格**时才写：充电时车不动，那里的电量只可能是充上去的；
     * 一旦开走过，之后的电量已经是行驶后的了，写进来就把两件事串了。
     *
     * @return 有变化时返回新锚点；没变化返回 null（调用方据此省掉一次写盘）。
     */
    private fun raiseChargeLevel(
        anchor: ChargeAnchor,
        soc: Int?,
        reachedFull: Boolean,
        parked: Boolean,
    ): ChargeAnchor? {
        if (!parked) return null
        var next = anchor
        val peak = anchor.socPeak
        if (soc != null && soc in 0..100 && (peak == null || soc > peak)) {
            next = next.copy(socPeak = soc)
        }
        if (reachedFull && !anchor.full) next = next.copy(full = true)
        return if (next == anchor) null else next
    }

    /**
     * 换成卡片要显示的东西。
     *
     * 里程回退（当前里程比起点还小）一律当作「起点不可信」处理 —— 换仪表、换车、
     * 数据异常都可能出现，显示一个负数是更糟的错误。
     */
    fun summary(anchor: ChargeAnchor?, currentKm: Double?): ChargeCycleUi {
        if (anchor == null) return ChargeCycleUi.NoAnchor
        if (!isUsableKm(currentKm)) return ChargeCycleUi.NoKm
        val km = (currentKm ?: return ChargeCycleUi.NoKm) - anchor.km
        if (km < 0.0) return ChargeCycleUi.NoAnchor
        return ChargeCycleUi.Ready(
            km = km,
            at = anchor.at,
            soc = anchor.soc,
            socPeak = anchor.socPeak,
            full = anchor.full,
            manual = anchor.manual,
        )
    }
}
