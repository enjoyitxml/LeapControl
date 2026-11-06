package com.leapcontrol.app.trip

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 行程轨迹（B 方案：本地稀疏采样，不新增任何车况请求）。
 *
 * 数据从哪来：车况接口只有**一帧快照**，没有历史轨迹。所以每次 `refreshStatus()` 成功时
 * 顺手记一个点（经纬度 / 速度 / 表显总里程 / 是否行驶），不额外唤醒车机、不申请定位权限、
 * 不常驻后台。唯一的周期刷新是 HomeScreen 里「行驶中 && 停在首页前台」的 60 秒静默回拉，
 * 切页 / 退后台就停 —— 所以「记到点」不要求留在页面，「记全」才需要；界面上必须如实说，
 * 不能画成连续轨迹，也不能写成「打开 App 放后台就能记全」。
 *
 * 分段规则参考了 LeapMotor Mate 的 `poller/state_machine.py`：
 *  - 行驶判据**不只是**「挡位 D/R/N 或速度 > 1km/h」：还会拿上一帧的表显里程 / 坐标
 *    比一比，两帧之间动过就算。真机反馈过「家 → 充电站插枪」整趟空白：行驶中一帧
 *    都没刷到，出发前和插枪后两帧都是 P 挡静止快照，只看挡位就一个点都记不到。
 *    所以留一份 [TripOverview.lastSample]，用位移反推「中间开过车」；
 *  - 停车不立刻收尾，给一段宽限期（它是连续 6 次 P 挡 ≈ 1 分钟，我们是 5 分钟，
 *    因为零控 60 秒才刷一次，6 次就是 6 分钟，太迟了）；
 *  - 超过 30 分钟没有新读数时，用**最后一次真实读数**收尾，不把「现在」当结束时间。
 */
data class TripSample(
    /** 本机采样时刻。车端报文里的经纬度没有自己的时间戳，只有这个时间是可靠的。 */
    val at: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedKmh: Double? = null,
    /** 信号 1318：表显总里程，行程里程优先用它相减，比 GPS 点累加准。 */
    val odometerKm: Double? = null,
    val driving: Boolean = false,
    /**
     * 电量百分比（0–100，信号 1204 / 100003）。
     *
     * 行程能耗只能靠**首末两帧的电量差**：车况是快照，没有累计用电的分段数据，
     * 电池电压 / 电流更是稀疏几帧，积不出度数。所以这里只存百分比原始值，
     * 换算成 kWh 交给 [TripEnergy]，且必须有电池容量。
     */
    val socPercent: Double? = null,
    /** 表显剩余续航（km）。纯电车型用来给「掉电续航」这个辅助口径，不参与 kWh 计算。 */
    val rangeKm: Double? = null,
    /**
     * 车锁状态（信号 `driverDoorLockStatus`）：true = 已锁车，false = 未锁，null = 没读到。
     *
     * 只用来做一件事：**锁车且没在行驶的那一帧，当场结束当前行程**。
     * 用户 2026-11-06 的要求是「我锁车了就该立马结束，别等五分钟」—— 五分钟宽限期原本
     * 是为了红灯 / 临停，可车主下车锁门是个明确的「这趟完了」的信号，比等宽限期准得多。
     *
     * 注意必须 `&& !driving`：不少车开起来会**自动落锁**，行驶中被锁是常态，只看 locked
     * 会把整趟行程在第 5 公里砍断。见 [TripRules.ingest]。
     */
    val locked: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("at", at)
        latitude?.let { put("lat", it) }
        longitude?.let { put("lng", it) }
        speedKmh?.let { put("spd", it) }
        odometerKm?.let { put("odo", it) }
        put("driving", driving)
        socPercent?.let { put("soc", it) }
        rangeKm?.let { put("rng", it) }
        locked?.let { put("lk", it) }
    }

    companion object {
        fun fromJson(raw: JSONObject): TripSample? {
            val at = raw.optLong("at", 0L).takeIf { it > 0L } ?: return null
            fun number(key: String): Double? = raw.optDouble(key, Double.NaN)
                .takeIf { it.isFinite() }
            return TripSample(
                at = at,
                latitude = number("lat"),
                longitude = number("lng"),
                speedKmh = number("spd"),
                odometerKm = number("odo"),
                driving = raw.optBoolean("driving", false),
                socPercent = number("soc"),
                rangeKm = number("rng"),
                // 老数据没有 lk 键：optBoolean 会给 false，「已解锁」是个假事实。
                // 必须用 has() 区分「当时没读到」和「确实未锁」。
                locked = if (raw.has("lk")) raw.optBoolean("lk", false) else null,
            )
        }
    }
}

/**
 * 一段行程的能耗。
 *
 * ### 为什么读的是「电量差」而不是「累计用电」
 * 零跑车况接口只给一帧快照：没有分段累计用电，也没有充电量。电压 × 电流只有稀疏几帧，
 * 积分出来的度数会比真实值差出好几倍。唯一可信的是**首末两帧的 SOC 差**。
 *
 * 于是口径分三级，逐级退化、每一级都在界面上说清楚：
 *  1. 有首末 SOC → 给「耗电 X%」（实测，最可信）；
 *  2. 再有电池容量 → 换成 kWh，并按里程算百公里电耗（[capacityKwh] 是换算用的容量，
 *     用户没填就没有，绝不拿车型猜一个数字冒充实测）；
 *  3. 什么都读不到 → null，界面写「能耗未读到」。
 *
 * 充电会让 SOC 上升（end > start），此时 [usedPercent] 为 null —— 那段确实没「消耗」，
 * 硬取绝对值就成了假数据。
 */
data class TripEnergy(
    val startSoc: Double? = null,
    val endSoc: Double? = null,
    /** 掉了几个百分点；充电 / 没掉电 / 差值小到不可信时为 null。 */
    val usedPercent: Double? = null,
    /** 换算 kWh 用的电池容量（用户填的，不是车型猜的）。 */
    val capacityKwh: Double? = null,
    /** 耗电度数（kWh）。没有容量就是 null。 */
    val kwh: Double? = null,
    /** 百公里电耗（kWh/100km）。里程不足 1 km 时不给。 */
    val per100Km: Double? = null,
    val startRangeKm: Double? = null,
    val endRangeKm: Double? = null,
    /** 表显续航掉了多少 km（纯电辅助口径）。 */
    val rangeUsedKm: Double? = null,
) {
    val hasAny: Boolean
        get() = startSoc != null || endSoc != null || startRangeKm != null

    fun toJson(): JSONObject = JSONObject().apply {
        startSoc?.let { put("ssoc", it) }
        endSoc?.let { put("esoc", it) }
        usedPercent?.let { put("used", it) }
        capacityKwh?.let { put("cap", it) }
        kwh?.let { put("kwh", it) }
        per100Km?.let { put("p100", it) }
        startRangeKm?.let { put("srng", it) }
        endRangeKm?.let { put("erng", it) }
        rangeUsedKm?.let { put("rused", it) }
    }

    companion object {
        fun fromJson(raw: JSONObject?): TripEnergy? {
            if (raw == null) return null
            fun number(key: String): Double? = raw.optDouble(key, Double.NaN)
                .takeIf { it.isFinite() }
            val energy = TripEnergy(
                startSoc = number("ssoc"),
                endSoc = number("esoc"),
                usedPercent = number("used"),
                capacityKwh = number("cap"),
                kwh = number("kwh"),
                per100Km = number("p100"),
                startRangeKm = number("srng"),
                endRangeKm = number("erng"),
                rangeUsedKm = number("rused"),
            )
            return energy.takeIf { it.hasAny }
        }
    }
}

/** 一段已经收尾的行程。 */
data class TripRecord(
    val id: Long,
    val startAt: Long,
    val endAt: Long,
    val points: List<TripSample>,
    val distanceKm: Double,
    /** true = 表显里程没读到，只能按采样点直线距离估算。 */
    val distanceEstimated: Boolean,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    /** 逆地理补到的中文短地址；没填高德 Key 或解析失败时为 null。 */
    val startLabel: String? = null,
    val endLabel: String? = null,
    /**
     * 车辆真正开始行进的时刻。
     *
     * [startAt] 是采样窗口起点，可能是**出发前那一帧静止参照数据**（为了算表显里程差
     * 补进来的）。真机反馈过：卡片写「12:34 出发」，可用户 12:34 还在家里 —— 因为
     * 12:34 只是打开 App 时刷到的一帧 P 挡快照，不是车开始动的时间。
     * 所以显示出发时间、算时长与平均速度一律用 [effectiveStartAt]。
     */
    val departureAt: Long? = null,
    /** true = 这一趟没刷到行驶帧，只能按两帧位移的中点估计出发时刻，UI 要写「约」。 */
    val departureApprox: Boolean = false,
    /**
     * 这一趟的能耗。null = 全程没读到电量（老数据或信号缺失），不是「0 度」。
     * 换算 kWh 用的电池容量存在 [TripEnergy.capacityKwh] 里：用户以后改容量，
     * 历史行程的数字不会跟着变 —— 记录的是当时算出来的事实。
     */
    val energy: TripEnergy? = null,
) {
    /** 显示与计时统一用它；读不到出发时刻才退回采样窗口起点。 */
    val effectiveStartAt: Long get() = departureAt ?: startAt
    val durationMs: Long get() = (endAt - effectiveStartAt).coerceAtLeast(0L)
    val sampleCount: Int get() = points.size

    /** 里程 ÷ 时长。时长不足 1 分钟时不给，否则稀疏采样会把速度放大成离谱的数。 */
    val averageKmh: Double?
        get() {
            val hours = durationMs / 3_600_000.0
            return if (hours >= 1.0 / 60.0) distanceKm / hours else null
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("start", startAt)
        put("end", endAt)
        put("km", distanceKm)
        put("est", distanceEstimated)
        put("points", JSONArray().apply { points.forEach { put(it.toJson()) } })
        startLat?.let { put("slat", it) }
        startLng?.let { put("slng", it) }
        endLat?.let { put("elat", it) }
        endLng?.let { put("elng", it) }
        startLabel?.let { put("slabel", it) }
        endLabel?.let { put("elabel", it) }
        // 固定写这个键（0 = 这一趟判断不了起步时刻）。旧版本的数据没有 dep，
        // 读的时候靠 has("dep") 区分「新数据明确没有」和「老数据需要反推」，
        // 否则一次 JSON 往返就会凭空多出一个出发时间。
        put("dep", departureAt ?: 0L)
        put("dapprox", departureApprox)
        energy?.let { put("en", it.toJson()) }
    }

    companion object {
        fun fromJson(raw: JSONObject): TripRecord? {
            val start = raw.optLong("start", 0L).takeIf { it > 0L } ?: return null
            val end = raw.optLong("end", 0L).takeIf { it >= start } ?: return null
            val km = raw.optDouble("km", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
                ?: return null
            val points = mutableListOf<TripSample>()
            raw.optJSONArray("points")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { TripSample.fromJson(it) }?.let { points += it }
                }
            }
            fun number(key: String): Double? = raw.optDouble(key, Double.NaN)
                .takeIf { it.isFinite() }
            fun label(key: String): String? = raw.optString(key, "").takeIf { it.isNotEmpty() }
            // 老版本存的行程没有 dep 字段：从采样点反推一次出发时刻，不用等用户重跑一趟
            val inferred = TripRules.departure(points)
            return TripRecord(
                id = raw.optLong("id", start),
                startAt = start,
                endAt = end,
                points = points,
                distanceKm = km,
                distanceEstimated = raw.optBoolean("est", false),
                startLat = number("slat"),
                startLng = number("slng"),
                endLat = number("elat"),
                endLng = number("elng"),
                startLabel = label("slabel"),
                endLabel = label("elabel"),
                departureAt = if (raw.has("dep")) raw.optLong("dep", 0L).takeIf { it > 0L }
                else inferred?.at,
                departureApprox = if (raw.has("dep")) raw.optBoolean("dapprox", false)
                else inferred?.approx ?: false,
                energy = TripEnergy.fromJson(raw.optJSONObject("en")),
            )
        }
    }
}

/** 某辆车当前的全部行程数据。按 VIN 分开存，换车不会串。 */
data class TripOverview(
    val vin: String = "",
    /** 正在进行中的行程（含宽限期里的停车点）。 */
    val open: List<TripSample> = emptyList(),
    /** 第一个停车样本的时刻：宽限期起点，也是行程真正的结束时间。 */
    val pausedSince: Long? = null,
    /** 已收尾的行程，时间升序。 */
    val trips: List<TripRecord> = emptyList(),
    /**
     * 最近一帧车况快照，**不管当时是不是在行驶**。
     *
     * 必须有它：用户从家开到充电站，行驶中一帧都没刷到（后台 / 锁屏），只有出发前
     * 和插枪后两帧静止快照。只靠「挡位 D 或速度 >1」判行驶会一个点都记不到。
     * 留着上一帧才能拿位置 / 表显里程的变化反推「这两帧之间车动过」。
     */
    val lastSample: TripSample? = null,
    /**
     * 用户填的电池容量（kWh），按车存。
     *
     * 能耗只能靠电量差算，要换成 kWh 就必须知道容量；车型电池版本太多（同一款车
     * 好几个度数），按车型猜一个数字会把「估算」写成「实测」，所以让用户填一次。
     * 留空时界面只显示耗电百分比，不编度数。
     */
    val batteryKwh: Double? = null,
) {
    val latest: TripRecord? get() = trips.lastOrNull()

    /** 本月 / 该月已记录行程的耗电合计（kWh）；一段都算不出就是 null。 */
    fun monthKwh(monthKey: String): Double? {
        val values = trips.filter { TripFormat.monthKey(it.endAt) == monthKey }
            .mapNotNull { it.energy?.kwh }
        return if (values.isEmpty()) null else values.sum()
    }

    fun tripsOfMonth(monthKey: String): List<TripRecord> =
        trips.filter { TripFormat.monthKey(it.endAt) == monthKey }
            .sortedByDescending { it.endAt }

    fun monthKeys(): List<String> =
        trips.map { TripFormat.monthKey(it.endAt) }.distinct().sortedDescending()

    fun recentKm(now: Long, days: Int = 7): Double =
        trips.filter { it.endAt >= now - days * 86_400_000L }.sumOf { it.distanceKm }

    fun toJson(): JSONObject = JSONObject().apply {
        pausedSince?.let { put("paused", it) }
        lastSample?.let { put("last", it.toJson()) }
        batteryKwh?.let { put("bkwh", it) }
        put("open", JSONArray().apply { open.forEach { put(it.toJson()) } })
        put("trips", JSONArray().apply { trips.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(vin: String, raw: JSONObject?): TripOverview {
            if (raw == null) return TripOverview(vin)
            val open = mutableListOf<TripSample>()
            raw.optJSONArray("open")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { TripSample.fromJson(it) }?.let { open += it }
                }
            }
            val trips = mutableListOf<TripRecord>()
            raw.optJSONArray("trips")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { TripRecord.fromJson(it) }?.let { trips += it }
                }
            }
            return TripOverview(
                vin = vin,
                open = open,
                pausedSince = raw.optLong("paused", 0L).takeIf { it > 0L },
                trips = trips,
                lastSample = raw.optJSONObject("last")?.let { TripSample.fromJson(it) },
                batteryKwh = raw.optDouble("bkwh", Double.NaN)
                    .takeIf { it.isFinite() && it in TripRules.MIN_BATTERY_KWH..TripRules.MAX_BATTERY_KWH },
            )
        }
    }
}

/** 一组行程的合计（月度汇总 / 每天小计）。 */
data class TripTotals(val count: Int, val km: Double, val durationMs: Long, val kwh: Double? = null) {
    companion object {
        fun of(list: List<TripRecord>): TripTotals {
            val energies = list.mapNotNull { it.energy?.kwh }
            return TripTotals(
                count = list.size,
                km = list.sumOf { it.distanceKm },
                durationMs = list.sumOf { it.durationMs },
                // 一段都算不出来时是 null（界面写「--」），不是 0 —— 0 度是「真的没耗电」
                kwh = if (energies.isEmpty()) null else energies.sum(),
            )
        }
    }
}

/**
 * 车辆开始行进的时刻。[approx] = 没读到行驶帧，只能按两帧位移的中点估算。
 * 见 [TripRecord.departureAt]：不能把「打开 App 时刷到的静止帧」当出发时间。
 */
data class TripDeparture(val at: Long, val approx: Boolean)

/**
 * 正在记录的那一段的实时读数。
 *
 * 跟 [TripRecord] 的区别：它**没有结束时间、没有 id、也不会进历史清单**，
 * 每次刷新车况都可能变。界面拿它显示「这段还在记」，但不能把它画成已完成的行程。
 * 不含「现在」这个时刻 —— 时长/新鲜度由 UI 用自己的本地时钟算，纯逻辑保持可测。
 */
data class TripLive(
    /** 采样窗口起点，只用于数据；显示出发时间一律用 [departureAt]。 */
    val startAt: Long,
    val lastAt: Long,
    val points: List<TripSample>,
    /** 车辆开始行进的时刻；null = 这一趟还没读到能判断起步的帧。 */
    val departureAt: Long? = null,
    val departureApprox: Boolean = false,
    /** 表显里程差优先；缺失时按采样点直线累加。两者都读不到才是 null。 */
    val distanceKm: Double?,
    val distanceEstimated: Boolean,
    /** 最后一次读到的车速（不是「当前」车速，车况是稀疏快照）。 */
    val lastSpeedKmh: Double?,
    /** 最近一帧是停车帧，正在 5 分钟宽限期内。 */
    val paused: Boolean,
    val hasLocation: Boolean,
    /** 进行中这一段的实时能耗（同 [TripRecord.energy] 口径，随采样点增长）。 */
    val energy: TripEnergy? = null,
) {
    val sampleCount: Int get() = points.size
    /** 计时基准：优先用起步时刻，读不到才退回采样窗口起点。 */
    val effectiveStartAt: Long get() = departureAt ?: startAt
}

/** 手动「结束行程」的结果。UI 必须给一句明确反馈，不能点了没反应。 */
enum class TripCloseResult {
    /** 已收尾并写入历史清单。 */
    SAVED,
    NO_OPEN,
    /** 不足 2 个采样点：保留 open，让用户先刷新一次车况再结束。 */
    TOO_FEW_POINTS,
    /** 不足 30 秒：保留 open。 */
    TOO_SHORT,
    /** 表显里程没涨、坐标也没动，算不出里程：保留 open。 */
    NO_DISTANCE,

    /**
     * 用户确认「这趟不要了」：open 已清空，**没有**写进历史。
     *
     * 不到 30 秒 / 没读到位移的那一段不能硬存（会得到一段 0 km / 0 分的假行程），
     * 但也不能一直卡在「结束了却还在记录」—— 所以给一条明确的丢弃出口。
     */
    DISCARDED,
}

/**
 * 手动「开始行程」的结果。
 *
 * 行程不再自动开段（自动判据被信号噪声反复坑），但只要点了就必须有反馈：
 * [STARTED] 后卡片立刻切到「正在记录」，[ALREADY_OPEN] 说明已经在记了，不用重复点。
 */
enum class TripStartResult {
    STARTED,
    ALREADY_OPEN,
    /** 还没读到车况：开段需要一个起点快照，先刷新一次再点。 */
    NO_STATUS,
}

/** 多段合并的结果。同样是「必须有反馈」，不能点了没变化。 */
enum class TripMergeResult {
    MERGED,
    /** 勾选的段数不足 2 段（只有 1 段谈不合并）。 */
    TOO_FEW,
    /** 勾的部分在磁盘里找不到了（刚被 90 天裁剪 / 换车），原样不动。 */
    NOT_FOUND,
}

/** 纯逻辑：分段、算里程、裁剪。全部可在 JVM 单测里跑。 */
object TripRules {
    /** 20 秒内的重复刷新（小组件 + 首页同时刷）只留一个点。 */
    const val MIN_SAMPLE_INTERVAL_MS = 20_000L

    /** 停车宽限期：5 分钟内又动起来，仍算同一段（红灯 / 临停 / 买杯咖啡）。 */
    const val PAUSE_GRACE_MS = 5 * 60_000L

    /**
     * 锁车帧比最后一帧行驶数据最多能晚多久还算「这趟的结束时刻」。
     *
     * 车停稳后 60 秒的自动刷新就停了，锁车那一帧往往要等用户下次打开 App 才刷到；
     * 把这个间隔当行程时长会把 15 分钟的路记成 45 分钟。超过 2 分钟就按
     * 「最后一次真实读数」收尾，和手动结束 / 冻结收尾同一套口径。
     */
    const val MAX_LOCK_TAIL_MS = 2 * 60_000L

    /** 30 分钟没有新读数：按最后一次真实读数强制收尾（App 被切走了）。 */
    const val STALE_TRIP_MS = 30 * 60_000L

    const val MIN_TRIP_MS = 30_000L
    const val MIN_TRIP_KM = 0.1
    /**
     * 3 km/h 以上才算在开。
     *
     * 原来是 1 km/h，可地库挪车、GPS 噪声都能摸到 1~2 km/h，静止时反复点刷新就被当成行驶，
     * 行程自己开起来了（用户 2026-11-06 反馈）。挡位判据（D/R/N）仍然保留：
     * 等红灯时速度 0 但挂着 D，那确实属于同一趟。
     */
    const val MIN_DRIVING_KMH = 3.0
    /**
     * 「确实开过车」的位移门槛：**表显总里程涨 0.5 km** 或**坐标位移 1 km**。
     *
     * 这条只用于「行驶中一帧车况都没刷到」的补记（家 → 充电站那种）。门槛必须比「两帧
     * 有点位移」大一个量级：室内 GPS 漂几十上百米太常见，静止时点几下刷新就能凑出
     * 50 米位移，旧逻辑于是每刷一次就开一段 0.0 km 的假行程。
     */
    const val STRONG_MOVE_KM = 1.0
    const val STRONG_ODO_KM = 0.5
    /** 上一帧最多能当多久之前的起点：超过 6 小时就不补，避免跨天粘连。 */
    const val MAX_SEED_GAP_MS = 6 * 60 * 60_000L
    /** 两帧间隔超过这么久就不猜出发时刻了，隔了半天没法说是几点起步。 */
    const val MAX_DEPARTURE_GUESS_MS = 2 * 60 * 60_000L
    /** 行程条最多画几个采样刻度。99 个点全画出来就是用户说的「马赛克」。 */
    const val MAX_STRIP_MARKS = 6
    const val MAX_OPEN_POINTS = 240
    const val MAX_TRIPS = 300
    const val RETENTION_MS = 90L * 24 * 3600_000

    /** 电量差小于 0.5 个百分点就不给能耗：SOC 是整数，量化误差本身就有 1%。 */
    const val MIN_SOC_DROP = 0.5

    /** 百公里电耗至少要有 1 km 才给，否则一点点里程会把误差放大成离谱的数字。 */
    const val MIN_ENERGY_KM = 1.0

    /**
     * 自动开始模式下，相邻两帧行驶数据最多隔这么久才算「连续在开」。
     * 首页前台 60 秒一帧、手动刷新也就几分钟，10 分钟足够宽；再久就该当成两段。
     */
    const val MAX_AUTO_GAP_MS = 10 * 60_000L

    /** 电池容量的合理区间（kWh）：小于 5 或大于 200 的乘用车电池都不存在。 */
    const val MIN_BATTERY_KWH = 5.0
    const val MAX_BATTERY_KWH = 200.0

    /**
     * 挡位 D/R/N 或速度达到 [MIN_DRIVING_KMH] 才算在开。速度 >0 会把信号噪声当成在开车。
     *
     * **必须精确相等，不能用 `contains`**：车端在熄火 / 信号缺失时会下发 `UNKNOWN` / `NONE`
     * 这类值，`contains("N")` 会把它判成空挡，于是一打开 App 就「正在记录行程」
     * （用户 2026-11-06 反馈：刚打开 App 就显示行程中，车速还是 0 km/h）。
     */
    fun isDriving(speedKmh: Double?, gear: String?): Boolean {
        if ((speedKmh ?: 0.0) >= MIN_DRIVING_KMH) return true
        // 去掉内部空格：「D 挡」「P 挡」这种带空格的写法车端也会下发。
        val g = gear?.trim()?.uppercase(Locale.ROOT)?.replace(" ", "") ?: return false
        return g == "D" || g == "R" || g == "N" ||
            g == "D挡" || g == "R挡" || g == "N挡" ||
            g == "DRIVE" || g == "REVERSE" || g == "NEUTRAL" ||
            g == "前进" || g == "倒车" || g == "空挡"
    }

    fun parseSpeedKmh(raw: String?): Double? =
        raw?.let { Regex("""\d+(?:\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

    /**
     * 两帧之间**确实开过车**的强证据；只用来在「一帧行驶数据都没刷到」时补记一段。
     *
     * 门槛：表显总里程涨 0.5 km、或坐标位移 1 km 才算。
     * 用户反馈「每次点刷新行程就开始计算」，就是旧逻辑拿 50 米位移当行驶证据 ——
     * 停在家里 GPS 漂几十米很常见，点几次刷新就能凑够，于是开出一段 0.0 km 的假行程。
     * 宁可在地库那种极端场景少记一趟，也不能让每次刷新都变成一个行程。
     */
    fun strongMove(prev: TripSample?, next: TripSample): Boolean {
        if (prev == null) return false
        val a = prev.odometerKm
        val b = next.odometerKm
        if (a != null && b != null && b > a && b - a < 1_000.0) {
            return b - a >= STRONG_ODO_KM
        }
        if (prev.latitude != null && prev.longitude != null &&
            next.latitude != null && next.longitude != null
        ) {
            return haversineKm(
                prev.latitude, prev.longitude, next.latitude, next.longitude,
            ) >= STRONG_MOVE_KM
        }
        return false
    }

    /** 这一段的采样窗口里是否出现过强位移（表显里程涨 0.5 km / 坐标移动 1 km）。 */
    private fun movedInWindow(points: List<TripSample>): Boolean {
        for (i in 1 until points.size) {
            if (strongMove(points[i - 1], points[i])) return true
        }
        return false
    }

    /**
     * 车辆**开始行进**的时刻，不是采样窗口起点。
     *
     * 真机反馈：卡片右上写「12:34 出发」，用户说 12:34 还在家里。原因是 12:34 只是
     * 打开 App 时刷到的一帧 P 挡静止快照，被当成起点补进了 open（为了拿它算表显里程差）。
     * 出发时间必须从车真正动起来那一刻算：
     *  1. 有明确的行驶帧（速度 ≥3 或 D/R/N 挡）→ 取第一帧，这是最可靠的；
     *  2. 一帧行驶数据都没刷到（家 → 充电站两帧静止）→ 车是在「最后一帧静止」和
     *     「第一帧已明显位移」之间开的，取中点做「约」估计，界面必须写成「约 HH:mm」。
     *
     * 返回 null 表示实在判断不了（间隔过久 / 只有 GPS 漂移），UI 应显示「出发时间未读到」
     * 而不是编一个。
     */
    fun departure(points: List<TripSample>): TripDeparture? {
        if (points.isEmpty()) return null
        val first = points.first().at
        // 有明确的行驶帧就直接用它。别拿「静止帧之间的位移中点」去抢：
        // 车停在家门口时 GPS 漂个几十米很常见，那个中点会把出发时间又算回用户还没走的时候。
        points.firstOrNull { it.driving }?.let { return TripDeparture(it.at, false) }
        // 一帧行驶数据都没刷到（家 → 充电站两帧静止快照）：车是在「最后一帧静止」
        // 与「第一帧已明显位移」之间开的，取中点做「约」估计。这里必须用 strongMove，
        // 否则室内几十米的漂移就能凭空造出一个出发时间。
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (!strongMove(a, b)) continue
            val gap = b.at - a.at
            // 间隔过长就只给最早的可能时刻，别拿一个凭空的中点冒充出发时间
            val estimate = if (gap in 1..MAX_DEPARTURE_GUESS_MS) a.at + gap / 2 else a.at
            return TripDeparture(estimate.coerceIn(first, b.at), true)
        }
        return null
    }

    /**
     * 行程条抽稀：按时间分成最多 [maxMarks] 个桶，返回有采样点的桶中心位置（0..1）。
     *
     * 原来的做法是把每个采样点都画成圆点、下面再垫一条虚线 —— 99 个点挤在 18dp 高的
     * 条上就成了马赛克。改成固定桶数后，不管一趟记了 2 个点还是 200 个点，视觉密度都一样，
     * 而且空桶自然留出「这段没采到」的缺口，正好表达稀疏采样。纯函数，可单测。
     */
    fun stripMarks(points: List<TripSample>, maxMarks: Int = MAX_STRIP_MARKS): List<Float> {
        if (points.isEmpty()) return emptyList()
        val first = points.first().at
        val span = (points.last().at - first).coerceAtLeast(1L).toFloat()
        val buckets = maxMarks.coerceIn(1, 12)
        val hit = BooleanArray(buckets)
        for (p in points) {
            val f = ((p.at - first).toFloat() / span).coerceIn(0f, 1f)
            hit[(f * buckets).toInt().coerceIn(0, buckets - 1)] = true
        }
        return (0 until buckets).filter { hit[it] }.map { (it + 0.5f) / buckets }
    }

    /**
     * 吃进一个新样本，返回新状态。纯函数，不做 IO。
     *
     * @param autoStart 用户自己在行程页开了「自动开始记录」才为 true：
     *   连续两帧都是行驶状态就自己开一段（见下面「自动开始」那段注释）。
     *   默认 false = 只能手动点开始，打开 App、刷新车况都不会凭空冒出一段。
     */
    fun ingest(
        state: TripOverview,
        next: TripSample,
        autoStart: Boolean = false,
    ): TripOverview {
        val lastOpen = state.open.lastOrNull()
        // 20 秒内的重复刷新丢掉。用 lastSample 判，静止帧期间也不会把起点帧挤掉。
        // 但**锁车状态变化**不能被丢掉：那正好是「这趟结束」的唯一信号。
        val recent = state.lastSample ?: lastOpen
        if (recent != null &&
            next.at - recent.at < MIN_SAMPLE_INTERVAL_MS &&
            next.driving == recent.driving &&
            next.locked == recent.locked
        ) return state

        // 冻结：用最后一次真实读数收尾，绝不能用「现在」当结束时间。
        // 已经进过停车宽限期的，结束时间取「刚停下」那一帧，别把 App 被切走的半小时算进行程。
        // lastSample 一并清掉：冻结后是全新一段，不能让半小时前的旧帧当新段起点。
        if (lastOpen != null && next.at - lastOpen.at > STALE_TRIP_MS) {
            val endAt = state.pausedSince?.takeIf { it <= lastOpen.at } ?: lastOpen.at
            val closed = finish(state.open, endAt = endAt, batteryKwh = state.batteryKwh)
            val base = state.copy(
                open = emptyList(),
                pausedSince = null,
                lastSample = null,
                trips = state.trips + listOfNotNull(closed),
            )
            return ingest(base, next, autoStart)
        }

        val withLast = state.copy(lastSample = next)

        // 起止规则（2026-11-06 定稿）：**开始默认由用户手动点**，结束才自动兜底。
        //
        // 默认不开自动的原因：车端把熄火状态的挡位下发成 UNKNOWN / NONE 时会被判成空挡，
        // 用户一打开 App 就看见「正在记录 · 0 km/h · 1 个点」。与其继续猜，不如把「开始」
        // 交回用户。autoStart（行程页那个开关）开着的用户走下面这条路，但证据要求更硬。
        if (withLast.open.isEmpty()) {
            // 自动开始：必须**连续两帧**都是行驶状态才算真开起来了。
            // 单帧不算 —— 信号抖一下、GPS 速度跳一下都能让一帧看起来在动，那正是
            // 「刚打开 App 就显示行程中」的来源。两帧间隔超过 MAX_AUTO_GAP_MS 也不算连续：
            // 昨天的行驶帧不能给今天当证据。
            val prev = state.lastSample
            // 不查 locked：车开起来会自动落锁，行驶帧带 locked=true 是常态，
            // 拿它当否定条件会让自动模式永远开不了段。
            val autoOpen = autoStart && next.driving && prev != null && prev.driving &&
                next.at - prev.at in 1..MAX_AUTO_GAP_MS
            if (!autoOpen) return withLast
            // 起点补上前一帧：出发时间是车真正动起来的那一刻，不是第二帧刷到的时刻。
            return withLast.copy(open = listOf(prev!!, next), pausedSince = null)
        }

        // 锁车且没在行驶 = 车主下车走了，这一趟当场结束，不采点、也不等五分钟宽限期。
        // 必须同时要求 `!driving`：很多车开起来会自动落锁，行驶中出现 locked=true 是常态，
        // 只看 locked 会把一趟行程在第 5 公里砍断。
        if (next.locked == true && !next.driving) {
            // 结束时间：有停车帧就用「车刚停下」那一帧（锁车往往比停车晚几分钟，人走到
            // 电梯口才锁）；最后一帧还是行驶帧时，只在「离锁车很近」的情况下才用锁车这一刻
            // —— 车停稳后 60 秒自动刷新就停了，锁车那一帧往往要等用户下次打开 App 才刷到，
            // 拿它当结束时间会把一趟 15 分钟的路记成 45 分钟。
            val lastPoint = withLast.open.last()
            val tail = (next.at - lastPoint.at).coerceAtLeast(0L)
            // 全程只有首末两帧静止（点了开始、路上没刷到车况、锁车时才刷到一帧）：
            // 这一趟确实开过了（表显里程 / 坐标有强位移），结束时刻就是锁车这一刻。
            val movedOnly = withLast.open.none { it.driving } &&
                movedInWindow(withLast.open + next)
            val endAt = when {
                withLast.pausedSince != null && withLast.pausedSince!! <= next.at ->
                    withLast.pausedSince!!
                movedOnly -> next.at
                lastPoint.driving && tail <= MAX_LOCK_TAIL_MS -> next.at
                else -> lastPoint.at
            }
            // 锁车这一帧也要算进这一段（但不留在 open 里）：它是这趟最后一个真实读数，
            // 表显里程与终点坐标都以它为准。少了它，最后一小段里程会凭空丢掉。
            // endAt 早于它时，finish 内部按时间裁剪会把它自然排除。
            val closed = finish(
                withLast.open + next,
                endAt = endAt,
                batteryKwh = withLast.batteryKwh,
            )
            return withLast.copy(
                open = emptyList(),
                pausedSince = null,
                trips = withLast.trips + listOfNotNull(closed),
            )
        }

        if (next.driving) {
            return withLast.copy(
                open = (withLast.open + next).takeLast(MAX_OPEN_POINTS),
                pausedSince = null,
            )
        }

        // 点了开始但车还没动过（既没有行驶帧，也没有强位移）：**不做停车宽限收尾**。
        // 否则用户在地库点了开始、上车调座椅的这几分钟会被当成「停车」，
        // 到点自动清掉，界面上看着就是「点了开始又自己没了」。等他开起来或手动结束。
        // 一旦窗口里出现过强位移（家 → 充电站那种一帧行驶数据都没采到的情况），
        // 就照常走宽限期，否则插枪充电后这一段永远收不了尾。
        if (withLast.open.none { it.driving } && !movedInWindow(withLast.open)) {
            return withLast.copy(open = (withLast.open + next).takeLast(MAX_OPEN_POINTS))
        }

        val paused = withLast.pausedSince
        return when {
            paused == null -> withLast.copy(
                open = (withLast.open + next).takeLast(MAX_OPEN_POINTS),
                pausedSince = next.at,
            )
            next.at - paused >= PAUSE_GRACE_MS -> {
                // 结束时间取「车刚停下」那一帧，不把 5 分钟确认时间算进行程
                val closed = finish(withLast.open, endAt = paused, batteryKwh = withLast.batteryKwh)
                withLast.copy(
                    open = emptyList(),
                    pausedSince = null,
                    trips = withLast.trips + listOfNotNull(closed),
                )
            }
            else -> withLast.copy(
                open = (withLast.open + next).takeLast(MAX_OPEN_POINTS),
            )
        }
    }

    /**
     * 从采样点算这一趟的能耗。纯函数，不做 IO、不猜容量。
     *
     * 首末 SOC 各自取「第一个 / 最后一个读到的点」：中途某一帧没上报电量不该把整趟作废。
     * 充电会让电量上升，此时 [TripEnergy.usedPercent] 留 null —— 那段确实没消耗，
     * 取绝对值就成了假数据。里程不足 1 km 不折算百公里，误差会被放大到没意义。
     */
    fun energy(
        points: List<TripSample>,
        distanceKm: Double?,
        capacityKwh: Double?,
    ): TripEnergy? {
        if (points.isEmpty()) return null
        val startSoc = points.firstOrNull { it.socPercent != null }?.socPercent
        val endSoc = points.lastOrNull { it.socPercent != null }?.socPercent
        val startRange = points.firstOrNull { it.rangeKm != null }?.rangeKm
        val endRange = points.lastOrNull { it.rangeKm != null }?.rangeKm
        val used = if (startSoc != null && endSoc != null) {
            (startSoc - endSoc).takeIf { it >= MIN_SOC_DROP && it <= 100.0 }
        } else null
        return buildEnergy(startSoc, endSoc, startRange, endRange, used, distanceKm, capacityKwh)
    }

    /** 由「用电百分点」+ 里程 + 容量组装能耗。合并多段时也走这里，避免两套换算。 */
    private fun buildEnergy(
        startSoc: Double?,
        endSoc: Double?,
        startRange: Double?,
        endRange: Double?,
        used: Double?,
        distanceKm: Double?,
        capacityKwh: Double?,
    ): TripEnergy? {
        if (startSoc == null && startRange == null) return null
        val capacity = capacityKwh?.takeIf { it.isFinite() && it in MIN_BATTERY_KWH..MAX_BATTERY_KWH }
        val kwh = if (used != null && capacity != null) used / 100.0 * capacity else null
        val km = distanceKm?.takeIf { it.isFinite() && it >= MIN_ENERGY_KM }
        val per100 = if (kwh != null && km != null) kwh / km * 100.0 else null
        val rangeUsed = if (startRange != null && endRange != null) {
            (startRange - endRange).takeIf { it > 0.0 }
        } else null
        return TripEnergy(
            startSoc = startSoc,
            endSoc = endSoc,
            usedPercent = used,
            capacityKwh = capacity,
            kwh = kwh,
            per100Km = per100,
            startRangeKm = startRange,
            endRangeKm = endRange,
            rangeUsedKm = rangeUsed,
        )
    }

    /** 表显总里程差。优先用它算里程，比 GPS 点累加准得多；读不到 / 回绕时为 null。 */
    private fun odoDistance(points: List<TripSample>): Double? {
        val a = points.firstOrNull { it.odometerKm != null }?.odometerKm
        val b = points.lastOrNull { it.odometerKm != null }?.odometerKm
        return if (a != null && b != null && b > a && b - a < 1_000.0) b - a else null
    }

    /**
     * 按当前电池容量重算每段行程的度数。
     *
     * 为什么要重算：容量是车的常量，用户填它只是「补上一个事实」，所以填完 / 改正后
     * 历史里那些**已经记到电量差、但当时没容量**的段应该立刻有度数，不用重跑一趟。
     * 只动 [TripEnergy.kwh] / [TripEnergy.per100Km]，电量差始终是当时实测的。
     * 容量为空时原样返回 —— 清空容量不清历史数字（那些数字确实是照当时容量算的）。
     */
    fun reprice(state: TripOverview): TripOverview {
        val capacity = state.batteryKwh ?: return state
        var changed = false
        val trips = state.trips.map { trip ->
            val energy = trip.energy ?: return@map trip
            val used = energy.usedPercent ?: return@map trip
            val kwh = used / 100.0 * capacity
            val per100 = if (trip.distanceKm >= MIN_ENERGY_KM) {
                kwh / trip.distanceKm * 100.0
            } else null
            if (energy.capacityKwh == capacity && energy.kwh == kwh && energy.per100Km == per100) {
                return@map trip
            }
            changed = true
            trip.copy(
                energy = energy.copy(capacityKwh = capacity, kwh = kwh, per100Km = per100),
            )
        }
        return if (changed) state.copy(trips = trips) else state
    }

    /** 生成一段行程。里程优先用表显总里程差，读不到才按采样点直线距离估算。 */
    fun finish(points: List<TripSample>, endAt: Long, batteryKwh: Double? = null): TripRecord? {
        val window = points.filter { it.at <= endAt }
        if (window.size < 2) return null
        val first = window.first()
        if (endAt - first.at < MIN_TRIP_MS) return null

        val byOdo = odoDistance(window)
        val gps = gpsDistanceValue(window)
        // 车机没上报新的里程 / 坐标时，宁可存一段 0 km 并标注，也不能整段丢掉：
        // 用户跑完一趟发现列表空白，比看到「里程未读到」难受得多。
        val km = byOdo ?: gps?.takeIf { it >= MIN_TRIP_KM } ?: 0.0
        // 但一串纯静止帧不能算行程 —— 充电时采样闹钟会一直刷 P 挡静止快照。
        if (km == 0.0 && window.none { it.driving }) return null

        val firstPoint = window.firstOrNull { it.latitude != null && it.longitude != null }
        val lastPoint = window.lastOrNull { it.latitude != null && it.longitude != null }
        // 出发时刻 = 用户点「开始行程」那一刻 = 采样窗口的第一帧。
        // 手动开始之后不需要再从点里猜起步时刻：起点本身就是用户给的。
        // （`departure()` 仍保留，只服务老数据 —— 没有 dep 字段的历史行程读盘时反推一次。）
        return TripRecord(
            id = first.at,
            startAt = first.at,
            endAt = endAt,
            points = window,
            distanceKm = km,
            distanceEstimated = byOdo == null,
            startLat = firstPoint?.latitude,
            startLng = firstPoint?.longitude,
            endLat = lastPoint?.latitude,
            endLng = lastPoint?.longitude,
            departureAt = first.at,
            departureApprox = false,
            energy = energy(window, km, batteryKwh),
        )
    }

    /**
     * 实时读数：把还在 open 里的点算成一组给人看的数字。
     * 不做任何收尾判定，也不写盘 —— 显示「正在记录」绝不等于结束行程。
     */
    fun live(state: TripOverview): TripLive? {
        val points = state.open
        if (points.isEmpty()) return null
        val byOdo = odoDistance(points)
        val km = byOdo ?: gpsDistanceValue(points)
        return TripLive(
            startAt = points.first().at,
            lastAt = points.last().at,
            points = points,
            // 手动开始后，出发时刻就是点「开始行程」那一刻（= 第一帧），不用再猜。
            departureAt = points.first().at,
            departureApprox = false,
            distanceKm = km,
            distanceEstimated = byOdo == null,
            lastSpeedKmh = points.lastOrNull { it.speedKmh != null }?.speedKmh,
            paused = state.pausedSince != null,
            hasLocation = points.any { it.latitude != null && it.longitude != null },
            energy = energy(points, km, state.batteryKwh),
        )
    }

    /**
     * 用户手动点「开始行程」：用当前这一帧车况开一段。
     *
     * 起点就是点下去那一刻的车况快照 —— 有坐标、表显里程、电量，后面算里程 / 能耗 /
     * 起终点地址都靠它。已经有进行中的段时不动数据，返回 [TripStartResult.ALREADY_OPEN]。
     */
    fun start(state: TripOverview, sample: TripSample): Pair<TripOverview, TripStartResult> {
        if (state.open.isNotEmpty()) return state to TripStartResult.ALREADY_OPEN
        val next = state.copy(
            open = listOf(sample),
            pausedSince = null,
            lastSample = sample,
        )
        return next to TripStartResult.STARTED
    }

    /**
     * 用户手动点「结束行程」：把 open 收成一段写进 trips。
     *
     * 结束时间取**最后一次真实读数**（已进停车宽限期就取刚停下那一帧），不取「现在」——
     * 否则用户停好车聊了十分钟再点，行程会被凭空拉长十分钟。
     * 收尾失败时**保留 open 不删数据**，返回具体原因让 UI 告诉用户下一步怎么做。
     */
    fun closeOpen(state: TripOverview, now: Long): Pair<TripOverview, TripCloseResult> {
        val points = state.open
        if (points.isEmpty()) return state to TripCloseResult.NO_OPEN
        val last = points.last()
        val endAt = state.pausedSince?.takeIf { it <= last.at && it >= points.first().at } ?: last.at
        val record = finish(points, endAt = endAt, batteryKwh = state.batteryKwh)
        if (record == null) {
            val reason = when {
                points.size < 2 -> TripCloseResult.TOO_FEW_POINTS
                endAt - points.first().at < MIN_TRIP_MS -> TripCloseResult.TOO_SHORT
                else -> TripCloseResult.NO_DISTANCE
            }
            return state to reason
        }
        // 用户明确「结束」了这一段，下一帧就该是全新起点，别把这段的尾点继承过去。
        val next = state.copy(
            open = emptyList(),
            pausedSince = null,
            lastSample = null,
            trips = state.trips + record,
        )
        return prune(next, now) to TripCloseResult.SAVED
    }

    /**
     * 用户确认「这趟不要了」：清掉 open，不写历史。
     *
     * 与 [closeOpen] 的区别是**不建任何档**。不到 30 秒、车机没上报位移的那种段硬存进去
     * 只会得到一段 0 km / 0 分的假行程，所以直接丢掉；而用户点了结束却一直卡在记录中，
     * 又是另一种糟糕体验 —— 两条路都要有，让用户自己选。
     */
    fun discardOpen(state: TripOverview): TripOverview =
        state.copy(open = emptyList(), pausedSince = null, lastSample = null)

    /**
     * 预判：现在点「结束行程」会不会真的建档（只读，不改数据）。
     *
     * 判据与 [closeOpen] 同源，只是拿 [TripLive] 算 —— 确认框要在用户点下去**之前**
     * 就说清「这趟不会记录」，而不是点了之后才弹一张失败说明。
     * 返回 null 表示会正常存进历史。
     */
    fun closeGuard(live: TripLive): TripCloseResult? {
        val points = live.points
        if (points.isEmpty()) return TripCloseResult.NO_OPEN
        if (points.size < 2) return TripCloseResult.TOO_FEW_POINTS
        // closeOpen 的结束时刻可能取「刚停下那一帧」，比 lastAt 还早，所以这里只可能
        // 判得偏宽松（真结束时报 TOO_SHORT），不会误判成「不会记录」。
        if (live.lastAt - points.first().at < MIN_TRIP_MS) return TripCloseResult.TOO_SHORT
        val km = live.distanceKm ?: 0.0
        if (km == 0.0 && points.none { it.driving }) return TripCloseResult.NO_DISTANCE
        return null
    }

    /**
     * 删除一段历史行程。
     *
     * 只动 [TripOverview.trips]，不碰进行中的那段（那段还没进清单，删也删不到）。
     * 找不到就原样返回，由 UI 决定要不要提示。
     */
    fun delete(state: TripOverview, id: Long): TripOverview {
        if (state.trips.none { it.id == id }) return state
        return state.copy(trips = state.trips.filterNot { it.id == id })
    }

    /**
     * 把用户勾选的多段拼成一段（中途停车办事、买菜被拆成几段的场景）。
     *
     * 规则：
     *  - 按开始时间排序后首尾相接，**采样点合并去重**（两段交界处可能有一帧重复）；
     *  - 里程优先用合并后首末的表显里程差 —— 它把「两段之间没采到的那段路」也算进去，
     *    比把各段里程相加更接近真实走过的距离；读不到表显里程才退回相加；
     *  - 出发时刻沿用第一段，终点地址沿用最后一段；
     *  - 能耗按各段用电百分点相加。中间充过电时，直接拿首末电量差会算出负数，
     *    相加才是「这几段一共耗了多少」。只要有一段没测到电量就不给总耗电，
     *    免得把「部分段之和」写成整趟的耗电。
     *
     * 不做「必须相邻」的校验：用户勾了哪几段就合哪几段，中间的段不勾就是不想并进去。
     */
    fun merge(state: TripOverview, ids: Collection<Long>): Pair<TripOverview, TripMergeResult> {
        val wanted = ids.toSet()
        if (wanted.size < 2) return state to TripMergeResult.TOO_FEW
        val parts = state.trips.filter { it.id in wanted }.sortedBy { it.startAt }
        if (parts.size != wanted.size) return state to TripMergeResult.NOT_FOUND

        val first = parts.first()
        val last = parts.last()
        val points = parts.flatMap { it.points }.sortedBy { it.at }.distinctBy { it.at }
        val byOdo = odoDistance(points)
        val km = byOdo ?: parts.sumOf { it.distanceKm }

        val startSoc = points.firstOrNull { it.socPercent != null }?.socPercent
        val endSoc = points.lastOrNull { it.socPercent != null }?.socPercent
        val startRange = points.firstOrNull { it.rangeKm != null }?.rangeKm
        val endRange = points.lastOrNull { it.rangeKm != null }?.rangeKm
        val usedParts = parts.map { it.energy?.usedPercent }
        val used = if (usedParts.isNotEmpty() && usedParts.none { it == null }) {
            usedParts.filterNotNull().sum().takeIf { it >= MIN_SOC_DROP }
        } else null

        val merged = TripRecord(
            id = first.id,
            startAt = first.startAt,
            endAt = last.endAt,
            points = points,
            distanceKm = km,
            distanceEstimated = byOdo == null,
            startLat = first.startLat,
            startLng = first.startLng,
            endLat = last.endLat,
            endLng = last.endLng,
            startLabel = first.startLabel,
            endLabel = last.endLabel,
            departureAt = first.departureAt,
            departureApprox = first.departureApprox,
            energy = buildEnergy(
                startSoc, endSoc, startRange, endRange, used, km, state.batteryKwh,
            ),
        )
        val rest = state.trips.filterNot { it.id in wanted }
        return state.copy(trips = (rest + merged).sortedBy { it.endAt }) to
            TripMergeResult.MERGED
    }

    /** 两点间大圆距离（km）。只用于表显里程缺失时的兜底估算。 */
    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(a.coerceAtMost(1.0)))
    }

    /** 实时显示允许是 0.0（车机还没上报新的坐标），只有收尾才要套最小里程阈值。 */
    private fun gpsDistanceValue(points: List<TripSample>): Double? {
        var sum = 0.0
        var hasPair = false
        var prev: TripSample? = null
        for (p in points) {
            val a = prev
            if (a != null && a.latitude != null && a.longitude != null &&
                p.latitude != null && p.longitude != null
            ) {
                sum += haversineKm(a.latitude, a.longitude, p.latitude, p.longitude)
                hasPair = true
            }
            if (p.latitude != null && p.longitude != null) prev = p
        }
        return if (hasPair) sum else null
    }

    /** 落盘前裁剪：只保留最近 90 天、最多 300 段，避免文件无限长。 */
    fun prune(state: TripOverview, now: Long): TripOverview = state.copy(
        open = state.open.takeLast(MAX_OPEN_POINTS),
        trips = state.trips
            .filter { it.endAt >= now - RETENTION_MS }
            .takeLast(MAX_TRIPS),
    )
}

/** 日期与数字格式。统一用 java.text，避开 minSdk 26 上缺失的 java.time 接口。 */
object TripFormat {
    private val DAY = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val MONTH = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    private val TIME = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val MONTH_LABEL = SimpleDateFormat("yyyy 年 M 月", Locale.CHINA)

    fun dayKey(at: Long): String = synchronized(DAY) { DAY.format(Date(at)) }
    fun monthKey(at: Long): String = synchronized(MONTH) { MONTH.format(Date(at)) }
    fun time(at: Long): String = synchronized(TIME) { TIME.format(Date(at)) }

    /** 历史清单里显示的起点时间：用车辆起步时刻，估算的加「约」。 */
    fun startTime(record: TripRecord): String =
        (if (record.departureApprox) "约 " else "") + time(record.effectiveStartAt)

    /**
     * 首页卡上的时间段起点：`今天 17:39` / `昨天 09:12` / `11 月 4 日 17:39`。
     *
     * 卡片只放最近一趟，光给 HH:mm 看不出是哪天；带上「今天/昨天」再远一点给月日，
     * 一行也能放下。
     */
    fun dayTime(at: Long, now: Long): String = "${dayTitle(dayKey(at), now)} ${time(at)}"

    /** 实时面板右上角：手动开始后就是点下去那一刻，直接写「开始」，不再有估算。 */
    fun departureLabel(live: TripLive): String {
        val at = live.departureAt ?: return "开始时间未读到"
        return (if (live.departureApprox) "约 " else "") + time(at) + " 开始"
    }
    fun monthLabel(key: String, now: Long): String {
        val parts = key.split("-")
        if (parts.size != 2) return key
        return runCatching {
            val cal = java.util.Calendar.getInstance(Locale.CHINA)
            cal.timeInMillis = now
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.YEAR, parts[0].toInt())
            cal.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
            synchronized(MONTH_LABEL) { MONTH_LABEL.format(cal.time) }
        }.getOrDefault(key)
    }

    fun dayTitle(key: String, now: Long): String {
        if (key == dayKey(now)) return "今天"
        if (key == dayKey(now - 86_400_000L)) return "昨天"
        val parts = key.split("-")
        return if (parts.size == 3) "${parts[1].toInt()} 月 ${parts[2].toInt()} 日" else key
    }

    /**
     * 月份选择器把「年 + 月」拼成与 [monthKey] 完全一致的 key。
     * 月份必须补零到两位，写成 "2026-1" 会跟磁盘里的 "2026-01" 对不上、选了等于没选。
     */
    fun monthKeyOf(year: Int, month: Int): String =
        String.format(Locale.CHINA, "%04d-%02d", year, month.coerceIn(1, 12))

    fun duration(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0L)
        return if (minutes < 60) "$minutes 分" else "${minutes / 60} 小时 ${minutes % 60} 分"
    }

    /** 进行中行程的计时器：mm:ss（满一小时变 h:mm:ss），每十几秒就能看出在走。 */
    fun clock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.CHINA, "%02d:%02d", m, s)
    }

    fun km(value: Double): String = String.format(Locale.CHINA, "%.1f", value)
    fun speed(value: Double): String = String.format(Locale.CHINA, "%.0f", value)

    /** 电量：整数不带小数点（`78%`），有小数才保留一位（`78.5%`）。 */
    fun percent(value: Double?): String {
        if (value == null || !value.isFinite() || value < 0.0) return "--"
        return if (value % 1.0 == 0.0) "${value.toInt()}%"
        else String.format(Locale.CHINA, "%.1f%%", value)
    }

    /** 度数：固定一位小数，`5.2`；没有值给 `--`（不是 `0.0`）。 */
    fun kwh(value: Double?): String {
        if (value == null || !value.isFinite() || value < 0.0) return "--"
        return String.format(Locale.CHINA, "%.1f", value)
    }
    fun monthNow(now: Long): String = monthKey(now)

    /** 逆地理返回的长地址裁成卡片里放得下的一行。 */
    fun shortPlace(formatted: String): String {
        val text = formatted.trim()
        return if (text.length > 14) text.take(14) + "…" else text
    }
}
