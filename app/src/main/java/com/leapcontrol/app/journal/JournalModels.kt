package com.leapcontrol.app.journal

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「零记」的数据模型。
 *
 * 三块内容各自独立，但都跟着 VIN 走（换车就是另一份账）：
 *  - [CarGoal] 用车目标：陪伴时长 + 落地价 + 提车日期；
 *  - [CostEntry] 用车花费：一条一条的流水；
 *  - [CityCheckin] 城市打卡：去过哪儿。
 *
 * 全部本地存储（`filesDir/journal/journal_<vin>.json`），不上传任何服务器 ——
 * 花费与行程属于个人隐私，没必要出门。
 */
object JournalDates {

    private val ZONE: ZoneId = ZoneId.systemDefault()

    /** 当天 0 点的时间戳（毫秒），所有「按天」的判定都以它为基准。 */
    fun startOfDay(millis: Long): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZONE)
            .atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun todayStart(): Long = today().atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun toLocalDate(millis: Long): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZONE)

    /** `2026-10-28`，与里程接口的 day 字段同一口径，便于直接对齐。 */
    fun dayKey(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun dayKey(millis: Long): String = dayKey(LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZONE))

    /** 宽容地解析服务端返回的 day：`2026-10-28` / `2026/10/28` / `20261028`。 */
    fun parseDayKey(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val parts = text.split("-", "/")
        if (parts.size >= 3) {
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        }
        if (text.length == 8) {
            val y = text.substring(0, 4).toIntOrNull() ?: return null
            val m = text.substring(4, 6).toIntOrNull() ?: return null
            val d = text.substring(6, 8).toIntOrNull() ?: return null
            return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        }
        return null
    }

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** 在某个时间戳上加减月份，回到当天 0 点（保养/保险的「N 个月后」都靠它）。 */
    fun addMonths(millis: Long, months: Int): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZONE)
            .plusMonths(months.toLong())
            .atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** 到目标日期还剩多少天（负数 = 已经过了）。 */
    fun daysUntil(targetMillis: Long, today: LocalDate = today()): Int =
        java.time.temporal.ChronoUnit.DAYS
            .between(today, LocalDate.ofInstant(Instant.ofEpochMilli(targetMillis), ZONE))
            .toInt()

    /** `10月28日`；跨年时带年份。 */
    fun shortLabel(millis: Long, today: LocalDate = today()): String {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZONE)
        return if (date.year == today.year) {
            String.format(Locale.CHINA, "%d月%d日", date.monthValue, date.dayOfMonth)
        } else {
            String.format(Locale.CHINA, "%d年%d月%d日", date.year, date.monthValue, date.dayOfMonth)
        }
    }

    fun yearMonthKey(millis: Long): String {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZONE)
        return String.format(Locale.CHINA, "%04d-%02d", date.year, date.monthValue)
    }

    fun monthLabel(yearMonth: String): String {
        val parts = yearMonth.split("-")
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return yearMonth
        return "${month}月"
    }
}

/**
 * 用车目标。
 *
 * [targetYears] 是「希望爱车陪自己多久」，[landingPrice] 是购车落地价，
 * [deliveryDate] 是提车日期（当天 0 点）。进度全部由这三个数算出来，
 * 不额外存「已达成多少天」—— 存了就会跟系统时间打架。
 */
data class CarGoal(
    val targetYears: Int,
    val landingPrice: Double,
    val deliveryDate: Long,
) {
    /** 目标总天数：按 365 天一年折算，文案里统一说「约」。 */
    val targetDays: Int get() = targetYears * 365

    val valid: Boolean
        get() = targetYears in 1..40 && landingPrice >= 0.0 && deliveryDate > 0L

    fun toJson(): JSONObject = JSONObject().apply {
        put("targetYears", targetYears)
        put("landingPrice", landingPrice)
        put("deliveryDate", deliveryDate)
    }

    companion object {
        fun fromJson(raw: JSONObject?): CarGoal? {
            if (raw == null) return null
            val years = raw.opt("targetYears")?.toString()?.toIntOrNull() ?: return null
            val price = raw.opt("landingPrice")?.toString()?.toDoubleOrNull() ?: 0.0
            val delivery = raw.opt("deliveryDate")?.toString()?.toLongOrNull() ?: return null
            return CarGoal(years, price, delivery).takeIf { it.valid }
        }

        /** 常见档位：3 / 5 / 8 / 10 / 15 年，输入框里也可以自己填。 */
        val SUGGESTED_YEARS = listOf(3, 5, 8, 10, 15)
    }
}

/**
 * 用车花费分类。
 *
 * [short] 是圆形色块里的单字 —— 分类图标全部用「一个字」而不是自绘小图，
 * 十几类图标画下来风格很难统一，一个字反而认得最快。
 *
 * [glyph] 是后来补的小图标：分类排行、流水行、图表图例里用得上，
 * 复用勋章那一套自绘线稿（同包直接引用），风格天生一致。
 */
enum class CostCategory(
    val id: String,
    val label: String,
    val short: String,
    /** 归一化色相（0~1），画分类条与圆标用。 */
    val hue: Float,
    val glyph: MedalGlyph,
) {
    CHARGE("charge", "充电", "充", 0.36f, MedalGlyph.BOLT),
    FUEL("fuel", "加油", "油", 0.07f, MedalGlyph.FUEL),
    INSURANCE("insurance", "保险", "保", 0.58f, MedalGlyph.SHIELD),
    WASH("wash", "洗车", "洗", 0.50f, MedalGlyph.DROPLET),
    SERVICE("service", "维修保养", "修", 0.02f, MedalGlyph.WRENCH),
    PARKING("parking", "停车", "停", 0.72f, MedalGlyph.PARKING),
    TOLL("toll", "过路", "路", 0.13f, MedalGlyph.TOLL),
    OTHER("other", "其他", "其", 0.0f, MedalGlyph.GRID);

    companion object {
        fun byId(id: String?): CostCategory =
            entries.firstOrNull { it.id == id } ?: OTHER

        /** 记账页展示顺序：高频在前。 */
        val ordered: List<CostCategory> = listOf(
            CHARGE, FUEL, PARKING, TOLL, WASH, SERVICE, INSURANCE, OTHER,
        )
    }
}

/** 一条用车花费。 */
data class CostEntry(
    val id: String,
    /** 花费发生日期（当天 0 点）。 */
    val date: Long,
    val category: CostCategory,
    val amount: Double,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date)
        put("cat", category.id)
        put("amount", amount)
        if (note.isNotEmpty()) put("note", note)
    }

    companion object {
        fun fromJson(raw: JSONObject?): CostEntry? {
            if (raw == null) return null
            val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return null
            val date = raw.opt("date")?.toString()?.toLongOrNull() ?: return null
            val amount = raw.opt("amount")?.toString()?.toDoubleOrNull() ?: return null
            if (!amount.isFinite() || amount <= 0.0) return null
            return CostEntry(
                id = id,
                date = date,
                category = CostCategory.byId(raw.optString("cat")),
                amount = amount,
                note = raw.optString("note").orEmpty(),
            )
        }
    }
}

/**
 * 保养计划。
 *
 * 三个数就能把「下次什么时候该去保养」算清楚：上次保养的日期与当时的表显里程、
 * 以及周期（几个月 / 多少公里）。[mode] 决定看哪一个 —— 电车常见「一年或一万公里」，
 * 两个都设就该两个都提醒，哪个先到算哪个。
 *
 * 里程用**当前总里程**去比，不在本地累加：总里程来自车况接口，比本地差分更准，
 * 也不会因为几天没开就凭空少掉几百公里。
 */
data class MaintenancePlan(
    val mode: Mode = Mode.BOTH,
    /** 上次保养日期（当天 0 点）。 */
    val lastDate: Long,
    /** 上次保养时的表显总里程。 */
    val lastKm: Double,
    /** 间隔月数。 */
    val months: Int,
    /** 间隔公里数。 */
    val km: Double,
) {
    enum class Mode(val id: String, val label: String) {
        TIME("time", "按时间"),
        KM("km", "按里程"),
        BOTH("both", "都提醒"),
        ;

        companion object {
            fun byId(id: String?): Mode = entries.firstOrNull { it.id == id } ?: BOTH
        }
    }

    val valid: Boolean get() = lastDate > 0L && months in 1..120 && km >= 0.0

    /** 下次保养日期；只看里程时为 null。 */
    fun nextDate(): Long? =
        if (mode == Mode.KM) null else JournalDates.addMonths(lastDate, months)

    /** 下次保养里程；只看时间时为 null。 */
    fun nextKm(): Double? = if (mode == Mode.TIME) null else lastKm + km

    /** 距下次保养还剩几天（负数 = 已过期）。 */
    fun remainingDays(): Int? = nextDate()?.let { JournalDates.daysUntil(it) }

    /** 距下次保养还剩多少公里（负数 = 已超）。 */
    fun remainingKm(currentKm: Double?): Double? {
        val target = nextKm() ?: return null
        val current = currentKm ?: return null
        return target - current
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.id)
        put("lastDate", lastDate)
        put("lastKm", lastKm)
        put("months", months)
        put("km", km)
    }

    companion object {
        fun fromJson(raw: JSONObject?): MaintenancePlan? {
            if (raw == null) return null
            val lastDate = raw.opt("lastDate")?.toString()?.toLongOrNull() ?: return null
            val months = raw.opt("months")?.toString()?.toIntOrNull() ?: 12
            val km = raw.opt("km")?.toString()?.toDoubleOrNull() ?: 10_000.0
            val plan = MaintenancePlan(
                mode = Mode.byId(raw.optString("mode")),
                lastDate = lastDate,
                lastKm = raw.opt("lastKm")?.toString()?.toDoubleOrNull() ?: 0.0,
                months = months,
                km = km,
            )
            return plan.takeIf { it.valid }
        }
    }
}

/**
 * 保险续费计划。
 *
 * 只记「起保日期 + 保期」，到期日现场算 —— 存一个到期日，续一年之后
 * 就容易忘了改，变成两个日期互相打架。
 */
data class InsurancePlan(
    val company: String = "",
    /** 起保（购买）日期，当天 0 点。 */
    val startDate: Long,
    /** 保期月数，家用车一般 12。 */
    val months: Int = 12,
    /** 保费，0 表示没填。 */
    val premium: Double = 0.0,
) {
    val valid: Boolean get() = startDate > 0L && months in 1..120

    fun expireDate(): Long = JournalDates.addMonths(startDate, months)

    /** 距到期还剩几天（负数 = 已过期）。 */
    fun remainingDays(): Int = JournalDates.daysUntil(expireDate())

    fun toJson(): JSONObject = JSONObject().apply {
        if (company.isNotBlank()) put("company", company)
        put("startDate", startDate)
        put("months", months)
        put("premium", premium)
    }

    companion object {
        fun fromJson(raw: JSONObject?): InsurancePlan? {
            if (raw == null) return null
            val start = raw.opt("startDate")?.toString()?.toLongOrNull() ?: return null
            val plan = InsurancePlan(
                company = raw.optString("company").orEmpty(),
                startDate = start,
                months = raw.opt("months")?.toString()?.toIntOrNull() ?: 12,
                premium = raw.opt("premium")?.toString()?.toDoubleOrNull() ?: 0.0,
            )
            return plan.takeIf { it.valid }
        }
    }
}

/** 一次城市打卡。同一个城市只保留一条（[id] 就是「省/市」）。 */
data class CityCheckin(
    val id: String,
    val city: String,
    val province: String,
    val date: Long,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("city", city)
        put("province", province)
        put("date", date)
        if (note.isNotEmpty()) put("note", note)
    }

    companion object {
        fun fromJson(raw: JSONObject?): CityCheckin? {
            if (raw == null) return null
            val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return null
            val city = raw.optString("city").takeIf { it.isNotBlank() } ?: return null
            val province = raw.optString("province").orEmpty()
            val date = raw.opt("date")?.toString()?.toLongOrNull() ?: 0L
            return CityCheckin(id, city, province, date, raw.optString("note").orEmpty())
        }
    }
}

/**
 * 心愿单里的一座城市 —— 「想去，但还没去」。
 *
 * 与 [CityCheckin] 分开存：一个是愿望、一个是事实，混在一张表里会让
 * 「点亮城市数」把想去的地方也算进去。打卡成功时对应心愿自动消掉。
 */
data class CityWish(
    val id: String,
    val city: String,
    val province: String,
    /** 加入时间（毫秒），只用来排序与展示。 */
    val added: Long,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("city", city)
        put("province", province)
        put("added", added)
        if (note.isNotEmpty()) put("note", note)
    }

    companion object {
        fun fromJson(raw: JSONObject?): CityWish? {
            if (raw == null) return null
            val id = raw.optString("id").takeIf { it.isNotBlank() } ?: return null
            val city = raw.optString("city").takeIf { it.isNotBlank() } ?: return null
            val province = raw.optString("province").orEmpty()
            val added = raw.opt("added")?.toString()?.toLongOrNull() ?: 0L
            return CityWish(id, city, province, added, raw.optString("note").orEmpty())
        }
    }
}

/**
 * 一份完整的零记数据（跟着一个 VIN 走）。
 *
 * [dailyKm] 是「日期 → 当日行驶里程」，来源两条：
 *  - 车况总里程的日差分（本地算，最准）；
 *  - 服务端近 7 日明细（一次补齐过去 7 天）。
 * 两条都只往上写更大的值：同一天先记了 12km、后来服务端说 13km，取 13。
 */
data class JournalData(
    val goal: CarGoal? = null,
    /** 服务端返回的提车天数（`ownershipDays`），没有就是 null。 */
    val ownershipDays: Int? = null,
    val costs: List<CostEntry> = emptyList(),
    val checkins: List<CityCheckin> = emptyList(),
    val dailyKm: Map<String, Double> = emptyMap(),
    /** 最近一次用总里程差分推算出的「当天里程」，用来判断是否需要补写。 */
    val odometerKm: Double? = null,
    val odometerDay: String? = null,
    /** 心愿单：想去还没去的城市。打卡成功会自动从这里移除。 */
    val wishes: List<CityWish> = emptyList(),
    /** 保养计划：填了才提醒，null = 没设置。 */
    val maintenance: MaintenancePlan? = null,
    /** 保险续费计划：同上。 */
    val insurance: InsurancePlan? = null,
) {
    val isEmpty: Boolean
        get() = goal == null && costs.isEmpty() && checkins.isEmpty() &&
            dailyKm.isEmpty() && wishes.isEmpty() &&
            maintenance == null && insurance == null

    /** 已点亮城市数。 */
    val cityCount: Int get() = checkins.size

    val wishCount: Int get() = wishes.size

    val provinceCount: Int get() = checkins.map { it.province }.filter { it.isNotEmpty() }.toSet().size

    /** 累计记账金额。 */
    val costTotal: Double get() = costs.fold(0.0) { acc, e -> acc + e.amount }

    /** 按分类汇总，金额从大到小，0 元的不出现。 */
    fun costByCategory(): List<Pair<CostCategory, Double>> =
        CostCategory.entries
            .map { cat -> cat to costs.filter { it.category == cat }.fold(0.0) { a, e -> a + e.amount } }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }

    /** 某个月份（`2026-10`）的支出。 */
    fun costOfMonth(yearMonth: String): Double =
        costs.filter { JournalDates.yearMonthKey(it.date) == yearMonth }
            .fold(0.0) { acc, e -> acc + e.amount }

    fun costOfYear(year: Int): Double =
        costs.filter {
            LocalDate.ofInstant(Instant.ofEpochMilli(it.date), ZoneId.systemDefault()).year == year
        }.fold(0.0) { acc, e -> acc + e.amount }

    /** 区间统计：`[fromInclusive, toExclusive)`，日 / 周 / 月 / 年都基于它。 */
    fun costBetween(fromInclusive: Long, toExclusive: Long): Double =
        costs.filter { it.date >= fromInclusive && it.date < toExclusive }
            .fold(0.0) { acc, e -> acc + e.amount }

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", VERSION)
        goal?.let { put("goal", it.toJson()) }
        ownershipDays?.let { put("ownershipDays", it) }
        if (costs.isNotEmpty()) {
            put("costs", JSONArray().apply { costs.forEach { put(it.toJson()) } })
        }
        if (checkins.isNotEmpty()) {
            put("checkins", JSONArray().apply { checkins.forEach { put(it.toJson()) } })
        }
        if (dailyKm.isNotEmpty()) {
            put("daily", JSONObject().apply { dailyKm.forEach { (k, v) -> put(k, v) } })
        }
        if (wishes.isNotEmpty()) {
            put("wishes", JSONArray().apply { wishes.forEach { put(it.toJson()) } })
        }
        maintenance?.let { put("maintenance", it.toJson()) }
        insurance?.let { put("insurance", it.toJson()) }
        odometerKm?.let { put("odometerKm", it) }
        odometerDay?.let { put("odometerDay", it) }
    }

    companion object {
        const val VERSION = 1

        val EMPTY = JournalData()

        fun fromJson(raw: JSONObject?): JournalData {
            if (raw == null) return EMPTY
            val costs = mutableListOf<CostEntry>()
            raw.optJSONArray("costs")?.let { array ->
                for (i in 0 until array.length()) {
                    CostEntry.fromJson(array.optJSONObject(i))?.let { costs += it }
                }
            }
            val checkins = mutableListOf<CityCheckin>()
            raw.optJSONArray("checkins")?.let { array ->
                for (i in 0 until array.length()) {
                    CityCheckin.fromJson(array.optJSONObject(i))?.let { checkins += it }
                }
            }
            val daily = mutableMapOf<String, Double>()
            raw.optJSONObject("daily")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val km = obj.opt(key)?.toString()?.toDoubleOrNull() ?: continue
                    if (km.isFinite() && km >= 0.0) daily[key] = km
                }
            }
            val wishes = mutableListOf<CityWish>()
            raw.optJSONArray("wishes")?.let { array ->
                for (i in 0 until array.length()) {
                    CityWish.fromJson(array.optJSONObject(i))?.let { wishes += it }
                }
            }
            return JournalData(
                goal = CarGoal.fromJson(raw.optJSONObject("goal")),
                ownershipDays = raw.opt("ownershipDays")?.toString()?.toIntOrNull(),
                costs = costs.sortedByDescending { it.date },
                checkins = checkins.sortedByDescending { it.date },
                dailyKm = daily,
                odometerKm = raw.opt("odometerKm")?.toString()?.toDoubleOrNull(),
                odometerDay = raw.optString("odometerDay").takeIf { it.isNotBlank() },
                // 心愿单按加入时间正序：先想去的排前面，像一张「要去的清单」
                wishes = wishes.sortedBy { it.added },
                maintenance = MaintenancePlan.fromJson(raw.optJSONObject("maintenance")),
                insurance = InsurancePlan.fromJson(raw.optJSONObject("insurance")),
            )
        }
    }
}
