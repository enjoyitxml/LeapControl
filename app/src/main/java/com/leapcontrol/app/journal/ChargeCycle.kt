package com.leapcontrol.app.journal

import kotlin.math.abs
import org.json.JSONObject

/**
 * 「自上次充电后」的起点：**充上电那一刻的表显总里程**。
 *
 * 车端没有充电历史接口（信号表里只有 `chargeState` 这一个实时状态，没有行程 / 分段里程），
 * 所以这条起点只能靠 App 自己「看见」充电时记下来 —— 详见 [ChargeCycle.observe]。
 *
 * [soc] 只用来在卡上写「上次充电时 82%」，不参与任何判定：
 * 它记的是「第一次看见这次充电时」的电量，等于开始充的时候多少，不是充满多少。
 */
data class ChargeAnchor(
    /** 充电起点的表显总里程（km）。 */
    val km: Double,
    /** 记下这个起点的时刻（毫秒）。 */
    val at: Long,
    /** 当时电量（%），可能没有。 */
    val soc: Int? = null,
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
        val soc: Int?,
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
 * 整夜慢充、充完直接开走、期间一次都没打开过 App —— 这次充电就看不到了，起点会停在
 * 更早那次，数字偏大。这个没法从数据里补救（没有充电记录可查），所以：
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

    /** 状态 1 = 充电中，2 = 充电已完成（见 ChargeStatus）。 */
    private const val STATE_CHARGING = 1
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

        // ① 没在充电（0 未插枪 / 3 故障 / 4 预约等待 / 6 暂停）：只盖「已结束」的戳。
        //    4 也归这一类 —— 预约等待结束、真正开始充电，那就是新的一次。
        if (chargeState != STATE_CHARGING && chargeState != STATE_DONE) {
            if (anchor == null || anchor.ended) return null
            return anchor.copy(ended = true)
        }

        // ② 读不到总里程：宁可留着旧起点，也不写一个瞎猜的
        val km = totalKm?.takeIf { isUsableKm(it) } ?: return null

        // ③ 这次观测还算不算「同一次充电」
        val sameSession = anchor != null && !anchor.ended &&
            abs(anchor.km - km) <= MOVE_TOLERANCE_KM &&
            now - anchor.at < SAME_SESSION_MS

        if (chargeState == STATE_CHARGING) {
            // 充电中车一定停着：里程变了就是另一次充电，时间隔久了也翻篇
            return if (sameSession) null else ChargeAnchor(km = km, at = now, soc = soc)
        }

        // ④ state=2 会残留到拔枪之后，甚至车已经开走
        if (anchor == null) return ChargeAnchor(km = km, at = now, soc = soc)
        // 车明确开过一段 → 这条「已充满」是残影，此刻的里程不是充电时那一格
        if (km > anchor.km + MOVE_TOLERANCE_KM) return null
        return if (sameSession) null else ChargeAnchor(km = km, at = now, soc = soc)
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
        return ChargeCycleUi.Ready(km = km, at = anchor.at, soc = anchor.soc, manual = anchor.manual)
    }
}
