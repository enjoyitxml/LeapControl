package com.leapcontrol.app.journal

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
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("km", km)
        put("at", at)
        soc?.let { put("soc", it) }
        if (manual) put("manual", true)
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
 * ## 漏检
 * 整夜慢充、充完直接开走、期间一次都没打开过 App —— 这次充电就看不到了，起点会停在
 * 更早那次，数字偏大。这个没法从数据里补救（没有充电记录可查），所以：
 * 卡片上永远写着「上次充电 X 月 X 日」让用户自己判断新鲜度，并提供一个
 * 「从现在起算」（[ChargeCycle] 之外由 UI 调 `restartChargeCycle`）把起点拉回当下。
 */
object ChargeCycle {

    /**
     * 同一次充电的判定窗口：6 小时内、里程几乎没变，就认为还是这一次，
     * 不重复改写起点（否则每刷新一次车况就写一次盘，还会把起点时间越推越晚）。
     */
    const val SAME_SESSION_MS = 6 * 60 * 60 * 1000L

    /** 表显里程的合理上限，超过就是脏数据（与 OwnershipStats 同一口径）。 */
    private const val MAX_KM = 2_000_000.0

    /** 「车没动」的里程容差（km）：表显分辨率为 0.1，2 km 足够容下偶尔的跳变。 */
    private const val STOP_TOLERANCE_KM = 2.0

    /** 状态 1 = 充电中，2 = 充电已完成（见 ChargeStatus）。 */
    private const val STATE_CHARGING = 1
    private const val STATE_DONE = 2

    fun isUsableKm(km: Double?): Boolean =
        km != null && km.isFinite() && km > 0.0 && km < MAX_KM

    /**
     * 观测一次车况，返回**新的起点**；返回 null 表示这次观测不改变任何东西。
     *
     * 规则只有两条，都是为了「不记错」：
     *  1. **充电中（state=1）**：车必然静止，看到就记。同一次充电（6 小时内、里程没变）
     *     只记第一次，避免反复写盘。
     *  2. **已完成（state=2）**：这个状态可能一直保持到下一次上电，甚至车已经开走。
     *     所以只在两种情况下认它 —— 还没有起点，或者里程和已有起点几乎一样（说明车确实没动）。
     *     里程已经涨了一截的，宁可放弃这次观测，也不能把「开了一段之后的里程」当成充电起点，
     *     那会让卡片上的数字凭空少一截。
     *
     * state=0 / 3 / 其他一律不动：那不是充电，跟起点无关。
     */
    fun observe(
        anchor: ChargeAnchor?,
        chargeState: Int?,
        soc: Int?,
        totalKm: Double?,
        now: Long,
    ): ChargeAnchor? {
        if (!isUsableKm(totalKm)) return null
        val km = totalKm ?: return null
        return when (chargeState) {
            STATE_CHARGING -> {
                if (anchor != null && isSameSession(anchor, km, now)) return null
                ChargeAnchor(km = km, at = now, soc = soc)
            }

            STATE_DONE -> {
                if (anchor == null) return ChargeAnchor(km = km, at = now, soc = soc)
                // 车已经挪过 → 这条 state=2 是残留，不能当起点
                if (km > anchor.km + STOP_TOLERANCE_KM) return null
                if (isSameSession(anchor, km, now)) return null
                ChargeAnchor(km = km, at = now, soc = soc)
            }

            else -> null
        }
    }

    private fun isSameSession(anchor: ChargeAnchor, km: Double, now: Long): Boolean =
        kotlin.math.abs(anchor.km - km) <= STOP_TOLERANCE_KM && now - anchor.at < SAME_SESSION_MS

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
