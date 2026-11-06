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
     * 下一次进入充电状态必然是新的一次。
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
 * 这一次车况报文里「充电是否真的在发生」的旁证。
 *
 * 存在的理由：`chargeState` 单独一句话不够。车端的充电状态会**残留** —— 拔了枪、
 * 开出去几十公里，网关仍可能回放上一次充电的 `1`；车休眠时更是照抄上一帧。
 * 以前拿这一格当唯一判据，于是「开着车刷一次车况 → 起点被改到当下 → 卡片清零」
 * 反复发生（用户 2026-11-06 反馈「总是自动清零」）。
 *
 * 所以起点「往大改」（= 卡片数字变小）时，必须再有一票硬件级旁证。
 */
enum class ChargeEvidence {
    /** 有旁证：交 / 直流输入有电、枪信号说连着、或电池真有充电电流。 */
    CONFIRMED,

    /** 明确没插枪 / 没电流进来：这个 `chargeState` 是上一次充电的残影。 */
    REFUTED,

    /** 车型不上报这几路信号（或数值不足以判断）：不知道，别拿它当依据。 */
    UNKNOWN,
}

/**
 * 一次车况观测里与充电有关的全部输入。
 *
 * 单独打包是为了让 [ChargeCycle.observe] 的判定读起来是一件事一件事的，
 * 而不是七个位置参数（以前那个签名调用方传错顺序都发现不了）。
 */
data class ChargeSample(
    /** 1149 `chargeState`：0 未插枪 / 1 充电中 / 2 已完成 / 3 故障 / 4 预约等待 / 5 直流快充 / 6 暂停。 */
    val chargeState: Int?,
    /** 电量（%）。 */
    val soc: Int?,
    /** 表显总里程（km）。 */
    val totalKm: Double?,
    /**
     * 车在动（车速 > 0 或挂在 D / R 挡）。
     *
     * 车在动就绝不可能在充电 —— 这一格为 true 时，本次观测对起点**一律不作数**。
     */
    val moving: Boolean = false,
    /** 充电的旁证，见 [ChargeEvidence]。 */
    val evidence: ChargeEvidence = ChargeEvidence.UNKNOWN,
)

/**
 * 观测累积出来的状态：已确认的起点 + 还在等第二票的候选。
 *
 * [pending] 是这次改版新加的：遇到「chargeState 说在充电、但没有任何旁证」的观测时，
 * 不再立刻改起点（那正是清零的来源），而是先记成候选；等下一次看到电量真的涨上去
 * （或车端报「已完成」）再转正。这样既不会误清，也不至于因为信号缺失而整段漏记。
 */
data class ChargeCycleState(
    val anchor: ChargeAnchor? = null,
    val pending: ChargeAnchor? = null,
) {
    companion object {
        val EMPTY = ChargeCycleState()
    }
}

/**
 * 卡片要展示的状态。
 *
 * 「没有数字」有几种原因，要分开说（和位置卡那套同一个原则）：
 * 还没记录过、拿不到当前里程、读数比起点还小 —— 对用户来说该做的事完全不同。
 */
sealed interface ChargeCycleUi {
    /** 从没记录过起点：提示「充电时打开一次零控即可自动记录」。 */
    data object NoAnchor : ChargeCycleUi

    /** 有起点，但这会儿读不到当前表显里程。 */
    data object NoKm : ChargeCycleUi

    /**
     * 当前里程比起点还小 —— 起点不可信（换仪表 / 脏数据 / 车端回了旧帧）。
     *
     * 单独一个状态而不是显示 0：显示 0 会被当成「记录被清零了」，
     * 而这其实只需要用户点一下「从现在起算」。
     */
    data object Mismatch : ChargeCycleUi

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
 * ## 唯一一条硬规矩：**会让数字变小的改动必须有两票**
 * 观测式最怕的不是漏记，而是**误清**：卡片上的数字每天在长，一旦某次误判「又充了一次」，
 * 起点被推到当下，数字立刻归零 —— 用户看到的不是「差一点」，是「记录没了」。
 *
 * 所以这里按代价分档：
 *
 *  1. **车没动**（本次里程与起点差 ≤ [MOVE_TOLERANCE_KM]）：改起点等于没改，数字不会变小。
 *     这种重置一律放行 —— 于是「整夜慢充」「原地又插一次」都不用任何旁证。
 *  2. **车动过了**：改起点就是清零。此时必须 [ChargeEvidence.CONFIRMED]（交 / 直流输入有电、
 *     枪信号说连着、或电池真有充电电流）。
 *  3. 车动过、又没有旁证：不猜也不丢，先存成 [ChargeCycleState.pending]。
 *     下一次观测电量真的涨了（或车端报「已完成」）就转正，否则过期作废。
 *
 * 另外两条防线：
 *  - **车在动**（[ChargeSample.moving]）的观测对起点一律不作数 —— 充电时车不可能在跑；
 *  - **明确没插枪**（[ChargeEvidence.REFUTED]）时，`chargeState=1` 当残影忽略：不改起点、
 *    也不挂候选，连「已结束」的戳都不盖（盖了会让下一次真充电失去判据）。
 *    只有「第一次记起点」不受这条约束 —— 那时没有任何数字会因此变小。
 *
 * ## 为什么不再按「6 小时」切段
 * 以前同一次充电超过 6 小时就当成新的一次。慢充从 20% 到满要 8~10 小时，
 * 于是一夜之间被切成两三段，「从 20% 充起」变成「从 88% 充起」。里程不受影响（车没动），
 * 但电量区间是假的。现在切分只认两件事：**看见过未充电**（[ChargeAnchor.ended]）
 * 与**里程变了**。
 *
 * ## 漏检
 * 充电期间一次都没拿到过车况（插枪前没打开过零控、充完直接开走）—— 这次充电就看不到了，
 * 起点会停在更早那次，数字偏大。中间那一段已经不需要人管：[ChargeWatch] 在充电期间
 * 每 10~20 分钟自动采一次，拔枪即停。但**第一次**仍然得有人碰一下（打开 App / 点小组件）。
 *
 * 剩下的漏检没法从数据里补救（没有充电记录可查），所以：
 * 卡片上永远写着「上次充电 X 月 X 日」让用户自己判断新鲜度，并提供一个
 * 「从现在起算」（[ChargeCycle] 之外由 UI 调 `restartChargeCycle`）把起点拉回当下。
 */
object ChargeCycle {

    /** 表显里程的合理上限，超过就是脏数据（与 OwnershipStats 同一口径）。 */
    private const val MAX_KM = 2_000_000.0

    /**
     * 「车没动」的容差（km）。表显分辨率是 0.1，充电时车必然静止，
     * 所以差 0.5 以上只可能是真开过车；0.3~0.4 之间的显示抖动仍能容下。
     */
    private const val MOVE_TOLERANCE_KM = 0.5

    /** 里程回退的容差（km）：小于它当显示抖动，大于它才判「起点不可信」。 */
    private const val ROLLBACK_TOLERANCE_KM = 1.0

    /**
     * 「电池真有电流」的门槛（A）。
     *
     * 交流 7 kW 在 350 V 上是 20 A 量级，直流快充上百安；而车停着不充电时高压侧基本是 0。
     * 取 1 A（≈350 W）足够把两者分开，又容得下采样抖动。
     */
    private const val MIN_CHARGE_AMP = 1.0

    /** 候选的有效期：超过就作废（拔枪开走之后电量不会再涨，留着也没用）。 */
    const val PENDING_TTL_MS = 6 * 60 * 60 * 1000L

    /** 候选与当前位置差多少就算「车开走了」——那次充电是残影。 */
    private const val PENDING_MOVE_KM = 1.0

    /** 候选转正要求电量至少涨这么多（%）：一次充电跨两次采样几乎必然涨得比这多。 */
    private const val MIN_SOC_RISE = 1

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
     * 把报文里几路信号折成一条「充电是否真的在发生」的判断。
     *
     * @param inputOn 交流 / 直流输入信号：任一为真有电 = true；两路都明确为 0 = false；其余 null
     * @param gunSignal 专用的枪状态信号（`chargeGunStatus` 之类），车型不上报时为 null
     * @param currentAmp 电池电流（A，带符号）
     *
     * 顺序是刻意的：**先认定「没插枪」再认定「在充电」**。两路输入都明确是 0 时，
     * 哪怕电流有点读数（自检、DC-DC 之类）也不该被当成充电 —— 宁可让卡片上的数字
     * 大一天，也不要把用户的记录抹掉。
     */
    fun evidence(
        inputOn: Boolean?,
        gunSignal: Boolean?,
        currentAmp: Double?,
    ): ChargeEvidence {
        if (inputOn == false && gunSignal != true) return ChargeEvidence.REFUTED
        if (gunSignal == false && inputOn != true) return ChargeEvidence.REFUTED
        if (inputOn == true || gunSignal == true) return ChargeEvidence.CONFIRMED
        val amp = currentAmp?.takeIf { it.isFinite() }
        if (amp != null && abs(amp) >= MIN_CHARGE_AMP) return ChargeEvidence.CONFIRMED
        return ChargeEvidence.UNKNOWN
    }

    /**
     * 观测一次车况，返回**新的观测状态**；返回 null 表示这次观测不改变任何东西。
     *
     * 每次拿到车况都该调一次（不限于正在充电时）—— 只有「不在充电」的那些观测
     * 才能给上一次充电盖上 [ChargeAnchor.ended] 的戳，那是下次能正确重置的前提。
     */
    fun observe(
        state: ChargeCycleState?,
        sample: ChargeSample,
        now: Long,
    ): ChargeCycleState? {
        val current = state ?: ChargeCycleState.EMPTY
        val chargeState = sample.chargeState ?: return null
        // 车在动：充电不可能发生。此刻报上来的「充电中」只可能是残留，
        // 改起点就等于把卡片上的数字抹掉 —— 这一格是最划算的一道防线。
        if (sample.moving) return null

        val km = sample.totalKm?.takeIf { isUsableKm(it) }
        val soc = sample.soc?.takeIf { it in 0..100 }

        val settled = settlePending(current, chargeState, soc, km, now)
        val next = when {
            chargeState in CHARGING_STATES -> observeCharging(settled, soc, km, sample.evidence, now)
            chargeState == STATE_DONE -> observeDone(settled, soc, km, now)
            else -> closeCurrent(settled, soc, km)
        }
        return next.takeIf { it != current }
    }

    /**
     * 先把候选结掉：够证据就转正，放太久或车开走了就丢掉。
     *
     * 转正时用的是**候选自己**记下的里程与时刻（那是真正看见充电的那一格），
     * 不是这一刻的读数 —— 中间要是挪过车，起点也该落在当初那个位置。
     */
    private fun settlePending(
        state: ChargeCycleState,
        chargeState: Int,
        soc: Int?,
        km: Double?,
        now: Long,
    ): ChargeCycleState {
        val pending = state.pending ?: return state
        if (km != null && abs(km - pending.km) > PENDING_MOVE_KM) return state.copy(pending = null)
        if (now - pending.at > PENDING_TTL_MS) return state.copy(pending = null)
        val rose = soc != null && pending.soc != null && soc >= pending.soc + MIN_SOC_RISE
        // 「车端说充完了」也算这一票：充满与否和 chargeState 的写法无关，它必然是一次真充电。
        if (!rose && chargeState != STATE_DONE) return state

        var promoted = pending.copy(ended = false)
        if (soc != null && (promoted.socPeak == null || soc > promoted.socPeak!!)) {
            promoted = promoted.copy(socPeak = soc)
        }
        if (chargeState == STATE_DONE) promoted = promoted.copy(full = true)
        return ChargeCycleState(anchor = promoted, pending = null)
    }

    /**
     * `chargeState ∈ {1, 5}`：车端说正在充电。
     *
     * 按代价分三档（见类注释）：同一次充电只抬电量；车没动 / 有旁证就换起点；
     * 都不满足就先挂候选，绝不直接清零。
     */
    private fun observeCharging(
        state: ChargeCycleState,
        soc: Int?,
        km: Double?,
        evidence: ChargeEvidence,
        now: Long,
    ): ChargeCycleState {
        // 读不到总里程：宁可留着旧起点，也不写一个瞎猜的
        if (km == null) return state

        val anchor = state.anchor
            ?: return state.copy(anchor = ChargeAnchor(km = km, at = now, soc = soc))

        val sameSpot = abs(anchor.km - km) <= MOVE_TOLERANCE_KM

        // 同一次充电（车没动、也还没收尾）：起点里程与时刻都不能被推着走，
        // 只把「充到多少」抬到看过的最高那格。
        if (sameSpot && !anchor.ended) {
            val raised = raiseChargeLevel(anchor, soc, reachedFull = false, parked = true)
                ?: return state
            return state.copy(anchor = raised)
        }

        // 车没动：换起点等于没换（里程一样），数字不会变小 —— 放行。
        // 车动过但有旁证：确实充了一次 —— 换起点。
        // 唯一例外是「明确没插枪」：那时连「今天充过」都不成立，别去改卡上的日期。
        val harmless = sameSpot && evidence != ChargeEvidence.REFUTED
        if (harmless || evidence == ChargeEvidence.CONFIRMED) {
            return ChargeCycleState(
                anchor = ChargeAnchor(km = km, at = now, soc = soc),
                pending = null,
            )
        }

        // 明确没插枪（REFUTED）：连候选都不挂 —— 它连"可能是充电"都不成立
        if (evidence == ChargeEvidence.REFUTED) return state

        // 车动过、又没有旁证：这次「充电」很可能是 chargeState 的残影。
        // 不猜、也不丢 —— 挂成候选，等电量真的涨上来再转正。
        return state.copy(pending = ChargeAnchor(km = km, at = now, soc = soc))
    }

    /**
     * `chargeState = 2`（充电已完成）：会残留到拔枪之后，甚至车已经开走。
     *
     * 只看见「已充满」时从多少充起的无从得知 —— 只填「充到多少」，别拿它冒充起点。
     */
    private fun observeDone(
        state: ChargeCycleState,
        soc: Int?,
        km: Double?,
        now: Long,
    ): ChargeCycleState {
        if (km == null) return state
        val anchor = state.anchor
            ?: return state.copy(anchor = ChargeAnchor(km = km, at = now, socPeak = soc, full = true))

        // 车明确开过一段 → 这条「已充满」是残影，此刻的里程不是充电时那一格
        if (km > anchor.km + MOVE_TOLERANCE_KM) return state

        // 车没动过：还是同一次充电，把电量抬到这一格并记下「到顶了」。
        // 以前这里会因为「已收尾」而重开一个起点，顺手把「从 X% 充起」丢掉。
        if (abs(km - anchor.km) <= MOVE_TOLERANCE_KM) {
            val raised = raiseChargeLevel(anchor, soc, reachedFull = true, parked = true)
                ?: return state
            return state.copy(anchor = raised)
        }

        // 里程比起点还小（换仪表 / 脏数据）：只记「车端说充满」，起点别动
        return if (anchor.full) state else state.copy(anchor = anchor.copy(full = true))
    }

    /**
     * 没在充电（0 未插枪 / 3 故障 / 4 预约等待 / 6 暂停）：盖「已结束」的戳。
     *
     * 4 也归这一类 —— 预约等待结束、真正开始充电，那就是新的一次。
     * 这一格同时是「充到多少」的定格时机：充电停了、车没动，此刻电量就是充完那一格。
     */
    private fun closeCurrent(
        state: ChargeCycleState,
        soc: Int?,
        km: Double?,
    ): ChargeCycleState {
        val anchor = state.anchor ?: return state
        if (anchor.ended) return state
        // 读不到里程就当它动过 —— 宁可缺着，也不能把行驶后的电量写成充到的电量。
        val parked = km != null && abs(km - anchor.km) <= MOVE_TOLERANCE_KM
        val ended = anchor.copy(ended = true)
        return state.copy(anchor = raiseChargeLevel(ended, soc, reachedFull = false, parked = parked) ?: ended)
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
     * 车端回了旧帧都可能出现，显示一个负数是更糟的错误；显示 0 又会被当成「记录清零」。
     * 所以单独给一个 [ChargeCycleUi.Mismatch]，让卡片能说清「点开重设一下就好」。
     */
    fun summary(anchor: ChargeAnchor?, currentKm: Double?): ChargeCycleUi {
        if (anchor == null) return ChargeCycleUi.NoAnchor
        if (!isUsableKm(currentKm)) return ChargeCycleUi.NoKm
        val km = (currentKm ?: return ChargeCycleUi.NoKm) - anchor.km
        if (km < -ROLLBACK_TOLERANCE_KM) return ChargeCycleUi.Mismatch
        return ChargeCycleUi.Ready(
            km = km.coerceAtLeast(0.0),
            at = anchor.at,
            soc = anchor.soc,
            socPeak = anchor.socPeak,
            full = anchor.full,
            manual = anchor.manual,
        )
    }
}
