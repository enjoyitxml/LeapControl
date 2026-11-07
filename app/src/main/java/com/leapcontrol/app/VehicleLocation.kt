package com.leapcontrol.app

import kotlin.math.cos
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Source of a vehicle coordinate pair in a decoded vehicle signal map.
 * The numeric signal IDs are kept here so callers do not need to guess them.
 */
enum class VehicleLocationSource {
    PRIMARY,
    VERIFIED_SIGNAL_MAP
}

/**
 * Coordinate systems are deliberately explicit because the vehicle protocol
 * source has not yet been confirmed as WGS-84, GCJ-02, or another datum.
 */
enum class VehicleCoordinateSystem {
    UNCONFIRMED,
    WGS84,
    GCJ02
}

enum class VehicleLocationFreshness {
    FRESH,
    STALE,
    EXPIRED,
    INVALID
}

enum class VehicleLocationAvailability {
    AVAILABLE,
    UNAVAILABLE
}

data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val source: VehicleLocationSource,
    val coordinateSystem: VehicleCoordinateSystem = VehicleCoordinateSystem.UNCONFIRMED
)

data class VehicleLocationSnapshot(
    val location: VehicleLocation,
    val queriedAtEpochMs: Long,
    /** The successful vehicle-response time is kept for freshness diagnostics only. */
    val receivedAtEpochMs: Long = queriedAtEpochMs
)

/** Safe, non-precise location state for the vehicle status UI. */
data class VehicleLocationSummary(
    val availability: VehicleLocationAvailability,
    val freshness: VehicleLocationFreshness,
    val coordinateSystem: VehicleCoordinateSystem,
    val queriedAtEpochMs: Long,
    val invalidReason: VehicleLocationValidation.InvalidReason? = null
) {
    companion object {
        fun fromSnapshot(
            snapshot: VehicleLocationSnapshot,
            nowEpochMs: Long,
            freshnessPolicy: VehicleLocationFreshnessPolicy = VehicleLocationFreshnessPolicy()
        ): VehicleLocationSummary = VehicleLocationSummary(
            availability = VehicleLocationAvailability.AVAILABLE,
            freshness = VehicleLocationDomain.freshness(snapshot, nowEpochMs, freshnessPolicy),
            coordinateSystem = snapshot.location.coordinateSystem,
            queriedAtEpochMs = snapshot.queriedAtEpochMs
        )

        fun fromValidation(
            validation: VehicleLocationValidation,
            queriedAtEpochMs: Long,
            nowEpochMs: Long = queriedAtEpochMs,
            freshnessPolicy: VehicleLocationFreshnessPolicy = VehicleLocationFreshnessPolicy()
        ): VehicleLocationSummary = when (validation) {
            is VehicleLocationValidation.Valid -> {
                val snapshot = VehicleLocationSnapshot(validation.location, queriedAtEpochMs)
                fromSnapshot(snapshot, nowEpochMs, freshnessPolicy)
            }
            is VehicleLocationValidation.Invalid -> VehicleLocationSummary(
                availability = VehicleLocationAvailability.UNAVAILABLE,
                freshness = VehicleLocationFreshness.INVALID,
                coordinateSystem = VehicleCoordinateSystem.UNCONFIRMED,
                queriedAtEpochMs = queriedAtEpochMs,
                invalidReason = validation.reason
            )
        }
    }
}

/** Text-only presentation rules shared by Compose and JVM tests. */
object VehicleLocationSummaryPresentation {
    fun refreshTimeLabel(summary: VehicleLocationSummary?, timeZoneId: String? = null): String {
        val time = summary?.let {
            SimpleDateFormat("HH:mm", Locale.CHINA).apply {
                timeZoneId?.let { id -> timeZone = java.util.TimeZone.getTimeZone(id) }
            }.format(Date(it.queriedAtEpochMs))
        } ?: "--"
        return "位置更新时间：$time"
    }
}

data class VehicleLocationFreshnessPolicy(
    val freshForMs: Long = DEFAULT_FRESH_FOR_MS,
    val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    val expiresAfterMs: Long = DEFAULT_EXPIRES_AFTER_MS
) {
    init {
        require(freshForMs >= 0L) { "freshForMs must be non-negative" }
        require(staleAfterMs >= freshForMs) { "staleAfterMs must be >= freshForMs" }
        require(expiresAfterMs >= staleAfterMs) { "expiresAfterMs must be >= staleAfterMs" }
    }

    companion object {
        // Product TTLs remain configurable until the vehicle timestamp contract is confirmed.
        const val DEFAULT_FRESH_FOR_MS = 5 * 60 * 1000L
        const val DEFAULT_STALE_AFTER_MS = 30 * 60 * 1000L
        const val DEFAULT_EXPIRES_AFTER_MS = 60 * 60 * 1000L
    }
}

data class VehicleLocationValidationPolicy(
    val maxJumpKm: Double = DEFAULT_MAX_JUMP_KM,
    val maxJumpIntervalMs: Long = DEFAULT_MAX_JUMP_INTERVAL_MS
) {
    init {
        require(maxJumpKm > 0.0) { "maxJumpKm must be positive" }
        require(maxJumpIntervalMs > 0L) { "maxJumpIntervalMs must be positive" }
    }

    companion object {
        // This is a fail-closed sanity guard, not a claim about vehicle dynamics.
        const val DEFAULT_MAX_JUMP_KM = 500.0
        const val DEFAULT_MAX_JUMP_INTERVAL_MS = 5 * 60 * 1000L
    }
}

sealed interface VehicleLocationValidation {
    data class Valid(val location: VehicleLocation) : VehicleLocationValidation

    enum class InvalidReason {
        MISSING_LATITUDE,
        MISSING_LONGITUDE,
        NON_NUMERIC,
        OUT_OF_RANGE,
        ZERO_COORDINATE,
        OBVIOUS_JUMP
    }

    data class Invalid(val reason: InvalidReason) : VehicleLocationValidation
}

data class CoordinatePair(
    val latitude: Any?,
    val longitude: Any?
) {
    fun hasAnyValue(): Boolean = latitude != null || longitude != null
}

object VehicleLocationDomain {
    const val PRIMARY_LATITUDE_KEY = "latitude"
    const val PRIMARY_LONGITUDE_KEY = "longitude"
    const val PRIMARY_LATITUDE_SIGNAL = "3725"
    const val PRIMARY_LONGITUDE_SIGNAL = "3724"
    const val VERIFIED_LATITUDE_SIGNAL = "3"
    const val VERIFIED_LONGITUDE_SIGNAL = "2"
    const val LOCATION_TIMESTAMP_SIGNAL = "sts"

    /** Selects only the decoded, verified primary coordinate fields. */
    fun selectCoordinatePair(values: Map<String, Any?>): SelectedCoordinatePair {
        val primary = CoordinatePair(
            latitude = values[PRIMARY_LATITUDE_KEY],
            longitude = values[PRIMARY_LONGITUDE_KEY]
        )
        return SelectedCoordinatePair(primary, VehicleLocationSource.PRIMARY)
    }

    /**
     * 依次尝试三种键：命名字段 → `3724`/`3725` → `2`/`3`。
     *
     * API 文档里「车辆位置」这一行写的是 **`2` / `3724` = longitude、`3` / `3725` = latitude**
     * —— 同一对信号在不同车型/网关下用两套键名下发。
     *
     * 先前这里只认命名与 3724/3725，而 [SignalTable.decode] 会把 3724/3725 折成
     * `latitude`/`longitude`、**只把 `2`/`3` 原样留在解码结果里**，
     * 于是只报 `2`/`3` 的车型永远取不到坐标 —— 首页卡和找车页一直停在
     * 「车机还没上报过位置」，而车其实一直在正常上报。
     */
    fun selectFromDecodedOrRaw(values: Map<String, Any?>): SelectedCoordinatePair {
        val candidates = candidatePairs(values)
        return candidates.firstOrNull { it.pair.hasAnyValue() } ?: candidates.last()
    }

    /**
     * 业务取数用的版本：**跳过这一对解不出合法坐标的**，而不是「键存在就认」。
     *
     * 与 [selectFromDecodedOrRaw] 的差别只在「值坏掉」这一种情况：
     *  - 报文里同时有 `3724`/`3725` 与 `2`/`3`，而前者是 `0` / 空串（车机刚唤醒、
     *    或是老网关残留的占位值）时，旧逻辑看到「键非空」就直接认了，
     *    于是一对 0 把它们按在地上 —— 明明 `2`/`3` 有真坐标，界面却说没有位置；
     *  - 反过来，如果两对都是真坐标但差得较远（一台车不可能同时停两个地方），
     *    优先取位置更新的那一对是猜不出来的，所以维持「命名键优先」的顺序不变，
     *    只把「选了也是废的」那一对让过去。
     *
     * 全部候选都不可用时退回 [selectFromDecodedOrRaw] 的结果 —— 让诊断那句
     * 「上报的坐标是 0,0」还能照常说出口，不要把原因也一起吞掉。
     */
    fun selectUsable(values: Map<String, Any?>): SelectedCoordinatePair {
        val candidates = candidatePairs(values)
        return candidates.getOrNull(usablePairIndex(values))
            ?: candidates.firstOrNull { it.pair.hasAnyValue() }
            ?: candidates.last()
    }

    /**
     * 三档候选里**第一个可用**的序号（0 命名 / 1 3725+3724 / 2 3+2），没有可用的时候是 -1。
     *
     * 单独暴露出来是给 debug 诊断用的：报告里要能说清「这一帧到底用了哪一对键」，
     * 否则「位置不准」这类问题只能靠猜。
     */
    fun usablePairIndex(values: Map<String, Any?>): Int {
        val candidates = candidatePairs(values)
        return candidates.indexOfFirst { isUsablePair(it.pair) }
    }

    /** 三档候选，顺序即优先级：命名键 → `3724`/`3725` → `2`/`3`。 */
    private fun candidatePairs(values: Map<String, Any?>): List<SelectedCoordinatePair> = listOf(
        selectCoordinatePair(values),
        SelectedCoordinatePair(
            CoordinatePair(
                latitude = values[PRIMARY_LATITUDE_SIGNAL],
                longitude = values[PRIMARY_LONGITUDE_SIGNAL]
            ),
            VehicleLocationSource.PRIMARY
        ),
        SelectedCoordinatePair(
            CoordinatePair(
                latitude = values[VERIFIED_LATITUDE_SIGNAL],
                longitude = values[VERIFIED_LONGITUDE_SIGNAL]
            ),
            VehicleLocationSource.VERIFIED_SIGNAL_MAP
        )
    )

    /** 这一对坐标能不能直接拿去用（两个值都要解得出、不是 0,0、不越界）。 */
    private fun isUsablePair(pair: CoordinatePair): Boolean {
        val latitude = parseCoordinate(pair.latitude) ?: return false
        val longitude = parseCoordinate(pair.longitude) ?: return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    fun validate(
        values: Map<String, Any?>,
        previous: VehicleLocationSnapshot? = null,
        queriedAtEpochMs: Long? = null,
        policy: VehicleLocationValidationPolicy = VehicleLocationValidationPolicy()
    ): VehicleLocationValidation {
        val selected = selectFromDecodedOrRaw(values)
        val latitude = parseCoordinate(selected.pair.latitude)
            ?: return if (isMissingCoordinateValue(selected.pair.latitude)) {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LATITUDE)
            } else {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.NON_NUMERIC)
            }
        val longitude = parseCoordinate(selected.pair.longitude)
            ?: return if (isMissingCoordinateValue(selected.pair.longitude)) {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.MISSING_LONGITUDE)
            } else {
                VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.NON_NUMERIC)
            }

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.OUT_OF_RANGE)
        }
        if (latitude == 0.0 && longitude == 0.0) {
            return VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.ZERO_COORDINATE)
        }

        if (previous != null && queriedAtEpochMs != null) {
            val elapsedMs = queriedAtEpochMs - previous.queriedAtEpochMs
            if (elapsedMs in 0..policy.maxJumpIntervalMs &&
                distanceKm(previous.location.latitude, previous.location.longitude, latitude, longitude) > policy.maxJumpKm
            ) {
                return VehicleLocationValidation.Invalid(VehicleLocationValidation.InvalidReason.OBVIOUS_JUMP)
            }
        }

        return VehicleLocationValidation.Valid(
            VehicleLocation(
                latitude = latitude,
                longitude = longitude,
                source = selected.source,
                coordinateSystem = VehicleCoordinateSystem.UNCONFIRMED
            )
        )
    }

    /**
     * P2 contract: read only the verified raw signalMap IDs. This deliberately
     * does not fall back to the older 3725/3724 fields or named coordinates.
     */
    fun validateVerifiedSignalMap(
        values: Map<String, Any?>,
        previous: VehicleLocationSnapshot? = null,
        queriedAtEpochMs: Long? = null,
        policy: VehicleLocationValidationPolicy = VehicleLocationValidationPolicy()
    ): VehicleLocationValidation {
        val validation = validate(
            values = mapOf(
                PRIMARY_LATITUDE_KEY to values[VERIFIED_LATITUDE_SIGNAL],
                PRIMARY_LONGITUDE_KEY to values[VERIFIED_LONGITUDE_SIGNAL]
            ),
            previous = previous,
            queriedAtEpochMs = queriedAtEpochMs,
            policy = policy
        )
        return when (validation) {
            is VehicleLocationValidation.Valid ->
                VehicleLocationValidation.Valid(
                    validation.location.copy(
                        source = VehicleLocationSource.VERIFIED_SIGNAL_MAP,
                        coordinateSystem = VehicleCoordinateSystem.GCJ02
                    )
                )
            is VehicleLocationValidation.Invalid -> validation
        }
    }

    fun freshness(
        snapshot: VehicleLocationSnapshot?,
        nowEpochMs: Long,
        policy: VehicleLocationFreshnessPolicy = VehicleLocationFreshnessPolicy()
    ): VehicleLocationFreshness {
        if (snapshot == null) return VehicleLocationFreshness.INVALID
        val ageMs = nowEpochMs - snapshot.queriedAtEpochMs
        if (ageMs < 0L) return VehicleLocationFreshness.INVALID
        return when {
            ageMs <= policy.freshForMs -> VehicleLocationFreshness.FRESH
            ageMs <= policy.staleAfterMs -> VehicleLocationFreshness.STALE
            else -> VehicleLocationFreshness.EXPIRED
        }
    }

    fun snapshot(
        validation: VehicleLocationValidation,
        queriedAtEpochMs: Long,
        receivedAtEpochMs: Long = queriedAtEpochMs
    ): VehicleLocationSnapshot? = when (validation) {
        is VehicleLocationValidation.Valid ->
            VehicleLocationSnapshot(validation.location, queriedAtEpochMs, receivedAtEpochMs)
        is VehicleLocationValidation.Invalid -> null
    }

    /** A missing or rejected refresh never discards the last valid position. */
    fun retainLastTrustedSnapshot(
        previous: VehicleLocationSnapshot?,
        candidate: VehicleLocationSnapshot?
    ): VehicleLocationSnapshot? = candidate ?: previous

    fun isFutureSnapshot(snapshot: VehicleLocationSnapshot, nowEpochMs: Long): Boolean =
        snapshot.queriedAtEpochMs > nowEpochMs

    private fun parseCoordinate(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble().takeIf { it.isFinite() }
        else -> value.toString().trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    }

    private fun isMissingCoordinateValue(value: Any?): Boolean =
        value == null || (value is String && value.isBlank())

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0088
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val haversine =
            kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                kotlin.math.sin(deltaLon / 2) * kotlin.math.sin(deltaLon / 2)
        return 2 * earthRadiusKm * kotlin.math.atan2(sqrt(haversine), sqrt(1 - haversine))
    }

}

data class SelectedCoordinatePair(
    val pair: CoordinatePair,
    val source: VehicleLocationSource
)

/**
 * 「车端这两对坐标，哪一对是 WGS-84、哪一对是 GCJ-02」—— 用互证代替猜。
 *
 * ## 为什么值得单独做一层
 * 用户反馈里 C11 原样就准、B10 / C01 要勾换算、还有一批勾了仍偏。三种结果说明
 * **车型之间上报的坐标系不一样**，而我们手上没有一份可信的车型对照表
 * （信号表那一栏本来就没抓包证据）。
 *
 * 但报文自身会露馅：不少车型**同时**下发两对坐标
 * （命名键 / `3724`+`3725` / `2`+`3`，见 [VehicleLocationDomain.candidatePairs]）。
 * 如果两对相距二三百米以上，拿 WGS-84 → GCJ-02 的公式去套其中一对，
 * 结果正好落在另一对上（误差小于 25 米），那这一对必然是 WGS-84、另一对是 GCJ-02。
 * 这是**数学上的硬证据**，比任何车型名单都可靠，而且不需要用户做任何选择。
 *
 * ## 什么时候不说话
 *  - 只有一对坐标：没法自证，返回 null，交回用户的选择（[CoordinateDatum.AUTO] 退化为原样）；
 *  - 两对相距很近（< 80 米）：同一坐标系的副本，谁都能用，不必插手；
 *  - 两对相差很远（> 1200 米）：更像「一对是上次熄火的位置」，换算公式套不上，
 *    此时沉默比选错强（错的那一对会把车标画到几十公里外）。
 */
object VehicleDatumDetector {
    /** 低于这个距离的两对更像同一份数据的副本，不当作「两种坐标系」。 */
    private const val MIN_GAP_METERS = 80.0

    /** 超过这个距离，换算公式无论如何对不上（GCJ 偏移最大约 800 米）。 */
    private const val MAX_GAP_METERS = 1200.0

    /** 换算结果与另一对的允许误差：GCJ 算法的近似、车端取整都会带来几米误差。 */
    private const val MATCH_TOLERANCE_METERS = 25.0

    /**
     * 已经确认是 GCJ-02 的那一对；没有硬证据时返回 null。
     *
     * 返回的是**坐标本身**而不是「该做哪种换算」：既然另一对就是现成的 GCJ-02，
     * 直接用它比换算过去更准（少一次算法误差）。
     */
    data class Detection(
        val latitude: Double,
        val longitude: Double,
        val source: VehicleLocationSource,
        /** 一句话说明判据（debug 展示 / 日志用）。 */
        val note: String,
    )

    fun detect(values: Map<String, Any?>): Detection? {
        val pairs = usablePairs(values)
        if (pairs.size < 2) return null
        for (i in pairs.indices) {
            for (j in pairs.indices) {
                if (i == j) continue
                val a = pairs[i]
                val b = pairs[j]
                val gap = ChinaCoordinate.offsetMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                if (gap !in MIN_GAP_METERS..MAX_GAP_METERS) continue

                // a 是 WGS-84、b 是 GCJ-02
                val (convertedLat, convertedLng) = ChinaCoordinate.wgs84ToGcj02(a.latitude, a.longitude)
                if (ChinaCoordinate.offsetMeters(convertedLat, convertedLng, b.latitude, b.longitude) <= MATCH_TOLERANCE_METERS) {
                    return Detection(
                        latitude = b.latitude,
                        longitude = b.longitude,
                        source = b.source,
                        note = "${a.key} 换算后与 ${b.key} 重合，判定后者为 GCJ-02",
                    )
                }
                // b 是 WGS-84、a 是 GCJ-02
                val (reversedLat, reversedLng) = ChinaCoordinate.wgs84ToGcj02(b.latitude, b.longitude)
                if (ChinaCoordinate.offsetMeters(reversedLat, reversedLng, a.latitude, a.longitude) <= MATCH_TOLERANCE_METERS) {
                    return Detection(
                        latitude = a.latitude,
                        longitude = a.longitude,
                        source = a.source,
                        note = "${b.key} 换算后与 ${a.key} 重合，判定后者为 GCJ-02",
                    )
                }
            }
        }
        return null
    }

    /** 报文里到底有几对可用坐标（0~3）。判定失败时用它区分「只有一对」和「两对但不一样」。 */
    fun usablePairCount(values: Map<String, Any?>): Int = usablePairs(values).size

    /** 报文里所有解得出数字、且不是 0,0 的候选对（同一坐标只留一条）。 */
    private fun usablePairs(values: Map<String, Any?>): List<Candidate> {
        val raw = listOf(
            Triple("命名键", values[VehicleLocationDomain.PRIMARY_LATITUDE_KEY], values[VehicleLocationDomain.PRIMARY_LONGITUDE_KEY]),
            Triple(
                "3725/3724",
                values[VehicleLocationDomain.PRIMARY_LATITUDE_SIGNAL],
                values[VehicleLocationDomain.PRIMARY_LONGITUDE_SIGNAL],
            ),
            Triple(
                "3/2",
                values[VehicleLocationDomain.VERIFIED_LATITUDE_SIGNAL],
                values[VehicleLocationDomain.VERIFIED_LONGITUDE_SIGNAL],
            ),
        )
        val out = ArrayList<Candidate>(3)
        for ((key, latRaw, lngRaw) in raw) {
            val latitude = latRaw?.toString()?.trim()?.toDoubleOrNull()
            val longitude = lngRaw?.toString()?.trim()?.toDoubleOrNull()
            if (latitude == null || longitude == null) continue
            if (!latitude.isFinite() || !longitude.isFinite()) continue
            if (latitude == 0.0 && longitude == 0.0) continue
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
            val duplicate = out.any {
                ChinaCoordinate.offsetMeters(it.latitude, it.longitude, latitude, longitude) < 1.0
            }
            if (duplicate) continue
            val source = when (key) {
                "3/2" -> VehicleLocationSource.VERIFIED_SIGNAL_MAP
                else -> VehicleLocationSource.PRIMARY
            }
            out.add(Candidate(key, latitude, longitude, source))
        }
        return out
    }

    private data class Candidate(
        val key: String,
        val latitude: Double,
        val longitude: Double,
        val source: VehicleLocationSource,
    )
}

/**
 * 「位置取不到」的一句话原因 —— **只用于诊断，不参与任何业务判断**。
 *
 * 同一个「没有位置」背后至少有四种情况：报文里根本没有位置信号、键名是没收录的那一对、
 * 值不是数字、或者车机自己报了个 0,0。用户在手机上无从分辨，
 * 而我们看不到他的车况报文 —— 所以 debug 构建把这句话显示在找车页底部，
 * 一张截图就能定位该往哪修。release 构建不显示（见 `FindCarScreen`）。
 */
object VehicleLocationDiagnostics {
    /** 目前认识的坐标键（含两套数字信号 ID）。 */
    private val CANDIDATE_KEYS = listOf(
        VehicleLocationDomain.PRIMARY_LATITUDE_KEY,
        VehicleLocationDomain.PRIMARY_LONGITUDE_KEY,
        VehicleLocationDomain.PRIMARY_LATITUDE_SIGNAL,
        VehicleLocationDomain.PRIMARY_LONGITUDE_SIGNAL,
        VehicleLocationDomain.VERIFIED_LATITUDE_SIGNAL,
        VehicleLocationDomain.VERIFIED_LONGITUDE_SIGNAL,
    )

    fun describe(values: Map<String, Any?>): String? {
        val seen = CANDIDATE_KEYS.filter { values[it] != null }
        if (seen.isEmpty()) return "报文里没有位置信号（2/3、3724/3725 都没出现）"
        val pair = VehicleLocationDomain.selectUsable(values).pair
        val latitude = parse(pair.latitude)
        val longitude = parse(pair.longitude)
        if (latitude == null || longitude == null) {
            val raw = listOfNotNull(pair.latitude, pair.longitude).joinToString(" / ")
            return "位置键 ${seen.joinToString("、")} 的值不是数字：$raw"
        }
        if (latitude == 0.0 && longitude == 0.0) return "上报的坐标是 0,0（车机还没定位或已休眠）"
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return "坐标越界：$latitude, $longitude"
        }
        return null
    }

    /**
     * 这次实际用的是哪一对键 —— **只给 debug 构建看**。
     *
     * 有两套键这件事本身就是「位置怎么不对」最可疑的地方：如果某台车同时报
     * `3725`/`3724` 和 `3`/`2` 而两个值差得很远，说明其中一对是旧的，
     * 一眼看出用的是哪一对，才知道该不该调整优先级。
     */
    fun describeSource(values: Map<String, Any?>): String? {
        val index = VehicleLocationDomain.usablePairIndex(values)
        if (index < 0) return null
        return when (index) {
            0 -> "命名键（3724/3725 解码）"
            1 -> "信号 3725/3724"
            else -> "信号 3/2"
        }
    }

    private fun parse(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble().takeIf { it.isFinite() }
        else -> value.toString().trim().toDoubleOrNull()
    }
}

/** Converts the protocol's status-time value into the epoch used by freshness rules. */
object VehicleLocationTimestamp {
    fun resolveEpochMs(value: Any?, fallbackEpochMs: Long): Long {
        val text = when (value) {
            null -> null
            org.json.JSONObject.NULL -> null
            else -> value.toString().trim().takeIf { it.isNotEmpty() }
        }
        val numeric = text?.toLongOrNull()
        if (numeric != null) {
            return when {
                numeric in 946_684_800_000L..4_102_444_800_000L -> numeric
                numeric in 946_684_800L..4_102_444_800L -> numeric * 1_000L
                else -> fallbackEpochMs
            }
        }

        val parsed = text?.let { valueText ->
            listOf("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
                .asSequence()
                .mapNotNull { pattern ->
                    runCatching {
                        SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                            .parse(valueText)?.time
                    }.getOrNull()
                }
                .firstOrNull { it > 0L }
        }
        return parsed ?: fallbackEpochMs
    }
}

enum class VehicleLocationMapState {
    NOT_REQUESTED,
    NO_DATA,
    INVALID,
    STALE,
    EXPIRED,
    COORDINATE_SYSTEM_UNCONFIRMED,
    READY,
    INITIALIZATION_FAILED
}

data class VehicleLocationMapModel(
    val state: VehicleLocationMapState,
    val showMarker: Boolean
)

/** Pure state gate for the future map SDK adapter. */
object VehicleLocationMapDomain {
    fun model(
        snapshot: VehicleLocationSnapshot?,
        summary: VehicleLocationSummary?,
        mapRequested: Boolean,
        initializationFailed: Boolean = false
    ): VehicleLocationMapModel {
        if (!mapRequested) return VehicleLocationMapModel(VehicleLocationMapState.NOT_REQUESTED, false)
        if (initializationFailed) return VehicleLocationMapModel(VehicleLocationMapState.INITIALIZATION_FAILED, false)
        if (snapshot == null || summary == null) {
            return VehicleLocationMapModel(VehicleLocationMapState.NO_DATA, false)
        }
        if (summary.availability == VehicleLocationAvailability.UNAVAILABLE ||
            summary.freshness == VehicleLocationFreshness.INVALID
        ) {
            return VehicleLocationMapModel(VehicleLocationMapState.INVALID, false)
        }
        if (summary.coordinateSystem == VehicleCoordinateSystem.UNCONFIRMED ||
            snapshot.location.coordinateSystem == VehicleCoordinateSystem.UNCONFIRMED
        ) {
            return VehicleLocationMapModel(VehicleLocationMapState.COORDINATE_SYSTEM_UNCONFIRMED, false)
        }
        if (summary.freshness == VehicleLocationFreshness.STALE) {
            return VehicleLocationMapModel(VehicleLocationMapState.STALE, true)
        }
        if (summary.freshness == VehicleLocationFreshness.EXPIRED) {
            return VehicleLocationMapModel(VehicleLocationMapState.EXPIRED, true)
        }
        return VehicleLocationMapModel(VehicleLocationMapState.READY, true)
    }
}
