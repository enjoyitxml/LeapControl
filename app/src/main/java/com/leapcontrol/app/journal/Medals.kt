package com.leapcontrol.app.journal

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 勋章图案。全部用 Canvas 画（见 `ui/JournalCommon.kt` 的 `drawGlyph`），不引图片资源。
 *
 * 一共 30 个图形，够 108 枚勋章分行当用 —— 真正的区分靠「名字 + 金属档位 + 分组」，
 * 不是每个都硬造一张图：108 张风格统一的自绘图标是做不到的，硬做只会看起来像杂货铺。
 */
enum class MedalGlyph {
    SPROUT,
    LEAF,
    CALENDAR,
    SUN,
    MOON,
    STAR,
    HEART,
    CROWN,
    FLAG,
    ROAD,
    CAR,
    COMPASS,
    MOUNTAIN,
    GEM,
    TROPHY,
    MAP_PIN,
    CITY,
    GRID,
    COIN,
    RECEIPT,
    BOLT,
    FUEL,
    WRENCH,
    DROPLET,
    SHIELD,
    TOLL,
    PARKING,
    TARGET,
    GAUGE,
    HOURGLASS,
}

/**
 * 金属档位。
 *
 * 同一组里越靠后越「贵」：青铜 → 白银 → 黄金 → 铂金。
 * 档位只影响颜色，不影响解锁规则 —— 规则永远只有一条：数值到了就亮。
 */
enum class MedalTier { BRONZE, SILVER, GOLD, PLATINUM }

/** 勋章分组：勋章墙按这个分页，也是「这个 App 一共能玩几件事」的自我说明。 */
enum class MedalGroup(val label: String) {
    DAYS("陪伴"),
    DISTANCE("里程"),
    PROGRESS("目标"),
    CITY("城市"),
    PROVINCE("省份"),
    COST("记账"),
    DAILY("单日"),
    CATEGORY("分类"),
}

/** 解锁口径。每一种都对应一个真实存在的数，不做「凭感觉」的勋章。 */
enum class MedalMetric {
    DAYS,
    DISTANCE,
    PROGRESS,
    CITY,
    PROVINCE,
    COST_COUNT,
    DAILY_KM,
    CATEGORY,
}

/**
 * 算勋章时用到的全部原料。
 *
 * 单独抽一个类，而不是给 `unlocked()` 塞八个参数：以后再加一组勋章（比如「季节」），
 * 只需要在这里多一个字段，所有勋章与界面的调用点都不用动。
 */
data class MedalStats(
    val days: Int = 0,
    val fraction: Float = 0f,
    /** 车辆总里程；拿不到就是 null（此时里程勋章一律不解锁，也不显示「差多少」）。 */
    val km: Double? = null,
    val cities: Int = 0,
    val provinces: Int = 0,
    val costCount: Int = 0,
    val maxDailyKm: Double = 0.0,
    val categories: Set<CostCategory> = emptySet(),
) {
    fun value(metric: MedalMetric): Double? = when (metric) {
        MedalMetric.DAYS -> days.toDouble()
        MedalMetric.DISTANCE -> km
        MedalMetric.PROGRESS -> fraction.toDouble()
        MedalMetric.CITY -> cities.toDouble()
        MedalMetric.PROVINCE -> provinces.toDouble()
        MedalMetric.COST_COUNT -> costCount.toDouble()
        MedalMetric.DAILY_KM -> maxDailyKm
        MedalMetric.CATEGORY -> null
    }

    companion object {
        val EMPTY = MedalStats()

        /** 从账本 + 进度算原料；[data] 为 null 时只有天数 / 进度 / 里程可用。 */
        fun of(data: JournalData?, days: Int, fraction: Float, km: Double?): MedalStats {
            if (data == null) return MedalStats(days = days, fraction = fraction, km = km)
            return MedalStats(
                days = days,
                fraction = fraction,
                km = km,
                cities = data.cityCount,
                provinces = data.provinceCount,
                costCount = data.costs.size,
                maxDailyKm = data.dailyKm.values.maxOrNull() ?: 0.0,
                categories = data.costs.map { it.category }.toSet(),
            )
        }
    }
}

/**
 * 一枚勋章。
 *
 * 解锁条件只有一条：对应口径的数值 ≥ [threshold]（分类勋章是「这类花过钱」）。
 */
data class Medal(
    val id: String,
    val group: MedalGroup,
    val metric: MedalMetric,
    val threshold: Double,
    val title: String,
    val desc: String,
    val glyph: MedalGlyph,
    val tier: MedalTier = MedalTier.BRONZE,
    /** 仅 [MedalMetric.CATEGORY] 用：对应哪一类花费。 */
    val category: CostCategory? = null,
) {
    fun unlocked(stats: MedalStats): Boolean {
        if (metric == MedalMetric.CATEGORY) {
            return category != null && stats.categories.contains(category)
        }
        val value = stats.value(metric) ?: return false
        return value + 1e-4 >= threshold
    }

    /** 完成度 0~1，用来挑「下一枚最接近拿到的」；无法度量时为 null。 */
    fun ratio(stats: MedalStats): Float? {
        if (metric == MedalMetric.CATEGORY) return if (unlocked(stats)) 1f else null
        val value = stats.value(metric) ?: return null
        if (threshold <= 0.0) return 1f
        return (value / threshold).toFloat().coerceIn(0f, 1f)
    }

    /** 还差多少（「差 12 天」「差 3 座城市」）；已解锁或无法度量时返回 null。 */
    fun gapText(stats: MedalStats): String? {
        if (unlocked(stats)) return null
        return when (metric) {
            MedalMetric.DAYS -> "差 ${Math.max(1, threshold.roundToInt() - stats.days)} 天"
            MedalMetric.DISTANCE -> {
                val km = stats.km ?: return "还没有里程数据"
                "差 ${kmText(threshold - km)} 公里"
            }

            MedalMetric.PROGRESS -> {
                val gap = ((threshold - stats.fraction) * 100f).roundToInt()
                "差 ${Math.max(1, gap)}% 进度"
            }

            MedalMetric.CITY -> "差 ${Math.max(1, threshold.roundToInt() - stats.cities)} 座城市"
            MedalMetric.PROVINCE -> "差 ${Math.max(1, threshold.roundToInt() - stats.provinces)} 个省份"
            MedalMetric.COST_COUNT -> "差 ${Math.max(1, threshold.roundToInt() - stats.costCount)} 笔记账"
            MedalMetric.DAILY_KM -> "单日差 ${kmText(threshold - stats.maxDailyKm)} 公里"
            MedalMetric.CATEGORY -> "还没有${category?.label.orEmpty()}花费"
        }
    }
}

/** 公里数文案：过万带千分位，不足一万保留整数。 */
private fun kmText(value: Double): String {
    val v = Math.max(0.0, value)
    return String.format(Locale.CHINA, "%,d", v.roundToInt())
}

// ---------------------------------------------------------------------------
// 勋章表
// ---------------------------------------------------------------------------

private fun day(days: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "day_$days",
    group = MedalGroup.DAYS,
    metric = MedalMetric.DAYS,
    threshold = days.toDouble(),
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun km(k: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "km_$k",
    group = MedalGroup.DISTANCE,
    metric = MedalMetric.DISTANCE,
    threshold = k.toDouble(),
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun progress(percent: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "p_$percent",
    group = MedalGroup.PROGRESS,
    metric = MedalMetric.PROGRESS,
    threshold = percent / 100.0,
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun city(n: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "city_$n",
    group = MedalGroup.CITY,
    metric = MedalMetric.CITY,
    threshold = n.toDouble(),
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun province(n: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "prov_$n",
    group = MedalGroup.PROVINCE,
    metric = MedalMetric.PROVINCE,
    threshold = n.toDouble(),
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun cost(n: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "cost_$n",
    group = MedalGroup.COST,
    metric = MedalMetric.COST_COUNT,
    threshold = n.toDouble(),
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun daily(k: Int, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "daily_$k",
    group = MedalGroup.DAILY,
    metric = MedalMetric.DAILY_KM,
    threshold = k.toDouble(),
    title = title,
    desc = desc,
    glyph = glyph,
)

private fun cat(c: CostCategory, title: String, desc: String, glyph: MedalGlyph) = Medal(
    id = "cat_${c.id}",
    group = MedalGroup.CATEGORY,
    metric = MedalMetric.CATEGORY,
    threshold = 1.0,
    title = title,
    desc = desc,
    glyph = glyph,
    category = c,
)

/**
 * 勋章表：**正好 108 枚**，分 8 组。
 *
 * 为什么是 108：用户要的。为什么分 8 组而不是一条长列表：一百多枚平铺出来，
 * 用户看不到"我还差什么"，只会觉得一堆灰点；分组之后每组都是一条清晰的爬坡路线。
 *
 * 组内顺序 = 解锁顺序；金属档位按组内位置自动分四档（见 [withTiers]）。
 */
object Medals {

    /** 陪伴天数：16 枚（1 天 → 10 年）。 */
    private val DAYS = listOf(
        day(1, "初见", "提车的第一天", MedalGlyph.SPROUT),
        day(7, "一周", "第一个星期，还在磨合", MedalGlyph.LEAF),
        day(30, "满月", "三十天，习惯成自然", MedalGlyph.CALENDAR),
        day(60, "双月", "两个月，开始有默契", MedalGlyph.CALENDAR),
        day(100, "百日", "一百天，从陌生到熟悉", MedalGlyph.SUN),
        day(180, "半年", "半年同行，风雨都见过", MedalGlyph.SUN),
        day(250, "八月", "一起走过大半年", MedalGlyph.LEAF),
        day(365, "一年", "完整走过四季", MedalGlyph.ROAD),
        day(500, "五百日", "五百天，日子有了刻度", MedalGlyph.CALENDAR),
        day(730, "两年", "两年，车也是家人", MedalGlyph.MOON),
        day(1000, "千日", "第一个一千天", MedalGlyph.STAR),
        day(1095, "三年", "三年，一起长大", MedalGlyph.MOON),
        day(1460, "四年", "四年，仍然心动", MedalGlyph.HEART),
        day(1825, "五年", "五年，值得纪念", MedalGlyph.STAR),
        day(2555, "七年", "七年了，还在开", MedalGlyph.HOURGLASS),
        day(3650, "十年", "十年长情，长期主义的奖杯", MedalGlyph.CROWN),
    )

    /** 行驶里程：16 枚（500 → 50 万公里）。 */
    private val DISTANCE = listOf(
        km(500, "五百公里", "第一次跑出城", MedalGlyph.FLAG),
        km(1_000, "千公里", "第一段长路", MedalGlyph.ROAD),
        km(2_000, "两千公里", "城市之间来去自如", MedalGlyph.ROAD),
        km(5_000, "五千公里", "半个万里", MedalGlyph.FLAG),
        km(8_000, "八千公里", "路已经熟得不用导航", MedalGlyph.COMPASS),
        km(10_000, "万里同行", "一万公里，足够跨越山海", MedalGlyph.MOUNTAIN),
        km(20_000, "两万公里", "车轮丈量生活", MedalGlyph.MOUNTAIN),
        km(30_000, "三万公里", "绕了大半圈地球", MedalGlyph.CAR),
        km(50_000, "五万公里", "绕地球一圈还多", MedalGlyph.GEM),
        km(80_000, "八万公里", "老伙计了", MedalGlyph.CAR),
        km(100_000, "十万公里", "这辆车已经写进生活", MedalGlyph.TROPHY),
        km(150_000, "十五万", "值得敬重的里程", MedalGlyph.GEM),
        km(200_000, "二十万", "半个地球的陪伴", MedalGlyph.GEM),
        km(250_000, "二十五万", "里程表上的勋章", MedalGlyph.TROPHY),
        km(300_000, "三十万", "传奇级车况", MedalGlyph.CROWN),
        km(500_000, "五十万", "这是要留给孩子开的意思", MedalGlyph.CROWN),
    )

    /** 目标进度：18 枚（1% → 100%）。 */
    private val PROGRESS = listOf(
        progress(1, "起点", "进度条第一次动了", MedalGlyph.SPROUT),
        progress(3, "上路", "3%，先跑起来", MedalGlyph.LEAF),
        progress(5, "二十分一", "二十分之一，习惯在形成", MedalGlyph.LEAF),
        progress(8, "渐入", "8%，开始有节奏", MedalGlyph.CALENDAR),
        progress(10, "出发", "走过目标的十分之一", MedalGlyph.FLAG),
        progress(15, "六分之一", "六分之一，方向对了", MedalGlyph.ROAD),
        progress(20, "五分之一", "五分之一，稳住了", MedalGlyph.ROAD),
        progress(25, "同行", "四分之一，已经有了默契", MedalGlyph.FLAG),
        progress(30, "三成", "30%，越走越顺", MedalGlyph.COMPASS),
        progress(35, "三分之一", "三分之一，过了心理关口", MedalGlyph.COMPASS),
        progress(40, "四成", "40%，过半在望", MedalGlyph.MOUNTAIN),
        progress(50, "半程", "一半的路，一起走完了", MedalGlyph.MOUNTAIN),
        progress(60, "六成", "60%，剩下的更珍惜", MedalGlyph.HEART),
        progress(70, "七成", "70%，越来越近", MedalGlyph.HEART),
        progress(75, "笃行", "四分之三，越走越稳", MedalGlyph.TARGET),
        progress(80, "八成", "80%，只差一步", MedalGlyph.TARGET),
        progress(90, "九成", "90%，终点看得见了", MedalGlyph.STAR),
        progress(100, "长情", "目标抵达，继续相伴", MedalGlyph.CROWN),
    )

    /** 城市打卡：16 枚（1 → 100 座）。 */
    private val CITY = listOf(
        city(1, "首旗", "第一面小红旗", MedalGlyph.FLAG),
        city(2, "两城", "开始有路线了", MedalGlyph.MAP_PIN),
        city(3, "三城", "周末有了去处", MedalGlyph.MAP_PIN),
        city(5, "五城", "五座城市走下来", MedalGlyph.MAP_PIN),
        city(8, "八城", "半个省跑遍了", MedalGlyph.ROAD),
        city(10, "十城", "足迹连成一条线", MedalGlyph.ROAD),
        city(12, "十二城", "十二座城，有故事了", MedalGlyph.COMPASS),
        city(15, "十五城", "周末不再问「去哪」", MedalGlyph.COMPASS),
        city(20, "二十城", "版图开始成形", MedalGlyph.GRID),
        city(25, "二十五城", "四分之一个省的密度", MedalGlyph.GRID),
        city(30, "三十城", "三十城，够写一本游记", MedalGlyph.CITY),
        city(35, "三十五城", "越来越像旅行家", MedalGlyph.CITY),
        city(40, "四十城", "四十座城的方向感", MedalGlyph.CITY),
        city(50, "五十城", "走过半个中国", MedalGlyph.TROPHY),
        city(70, "七十城", "地图上都是红的", MedalGlyph.TROPHY),
        city(100, "百城", "百城之约，说到做到", MedalGlyph.CROWN),
    )

    /** 到过的省份：12 枚（1 → 25 个）。 */
    private val PROVINCE = listOf(
        province(1, "一省", "第一块版图", MedalGlyph.GRID),
        province(2, "两省", "跨省了", MedalGlyph.GRID),
        province(3, "三省", "三省之地", MedalGlyph.GRID),
        province(4, "四省", "四个省的味道都尝过", MedalGlyph.COMPASS),
        province(5, "五省", "半个中国的跨度", MedalGlyph.COMPASS),
        province(6, "六省", "六省联动", MedalGlyph.MAP_PIN),
        province(8, "八省", "八省通吃", MedalGlyph.MAP_PIN),
        province(10, "十省", "十省足迹", MedalGlyph.CITY),
        province(12, "十二省", "十二省，长途常客", MedalGlyph.CITY),
        province(15, "十五省", "十五省，半个版图", MedalGlyph.TROPHY),
        province(20, "二十省", "二十省，真的在跑", MedalGlyph.TROPHY),
        province(25, "二十五省", "二十五省，走遍山河", MedalGlyph.CROWN),
    )

    /** 记账笔数：14 枚（1 → 500 笔）。 */
    private val COST = listOf(
        cost(1, "第一笔", "账本开张", MedalGlyph.RECEIPT),
        cost(3, "三笔", "三天两头记一记", MedalGlyph.RECEIPT),
        cost(5, "五笔", "记账开始顺手", MedalGlyph.COIN),
        cost(10, "十笔", "十笔之后成了习惯", MedalGlyph.COIN),
        cost(20, "二十笔", "二十笔，账目有样子了", MedalGlyph.COIN),
        cost(30, "三十笔", "一个月一笔不落", MedalGlyph.RECEIPT),
        cost(50, "五十笔", "五十笔，成本看得见", MedalGlyph.WRENCH),
        cost(80, "八十笔", "八十笔，一本细账", MedalGlyph.WRENCH),
        cost(100, "百笔", "一百笔，精算车主", MedalGlyph.TROPHY),
        cost(150, "一百五", "一百五十笔，滴水不漏", MedalGlyph.TROPHY),
        cost(200, "两百笔", "两百笔，成本专家", MedalGlyph.TROPHY),
        cost(300, "三百笔", "三百笔，账比车还老", MedalGlyph.GEM),
        cost(365, "一年不断", "365 笔，一年没断过", MedalGlyph.CALENDAR),
        cost(500, "五百笔", "五百笔，账本传家", MedalGlyph.CROWN),
    )

    /** 单日最多里程：8 枚（100 → 1000 公里）。 */
    private val DAILY = listOf(
        daily(100, "一日百里", "一天跑了一百公里", MedalGlyph.GAUGE),
        daily(200, "两百公里", "一天两百，出城了", MedalGlyph.GAUGE),
        daily(300, "三百公里", "三百公里的周末", MedalGlyph.ROAD),
        daily(400, "四百公里", "一天跨了两个省", MedalGlyph.ROAD),
        daily(500, "五百公里", "五百公里，纯纯长途", MedalGlyph.CAR),
        daily(600, "六百公里", "六百公里不停车", MedalGlyph.CAR),
        daily(800, "八百公里", "八百公里，一天的城市", MedalGlyph.BOLT),
        daily(1_000, "千公里一日", "一天跑完一千公里", MedalGlyph.CROWN),
    )

    /** 分类开张：8 枚（每一类花过钱就亮）。 */
    private val CATEGORY = listOf(
        cat(CostCategory.CHARGE, "充电", "第一笔电费记上了", MedalGlyph.BOLT),
        cat(CostCategory.FUEL, "加油", "第一笔油费记上了", MedalGlyph.FUEL),
        cat(CostCategory.PARKING, "停车", "停车费也是成本", MedalGlyph.PARKING),
        cat(CostCategory.TOLL, "过路", "跑高速的过路费", MedalGlyph.TOLL),
        cat(CostCategory.WASH, "洗车", "洗车的钱也算清楚", MedalGlyph.DROPLET),
        cat(CostCategory.SERVICE, "保养", "保养记一笔，心里有数", MedalGlyph.WRENCH),
        cat(CostCategory.INSURANCE, "保险", "保险是大头，别忘了", MedalGlyph.SHIELD),
        cat(CostCategory.OTHER, "杂项", "不分类的花费也记账", MedalGlyph.RECEIPT),
    )

    /** 档位按组内位置自动分四段：前 1/4 青铜、1/2 白银、3/4 黄金、其余铂金。 */
    private fun withTiers(list: List<Medal>): List<Medal> = list.mapIndexed { index, medal ->
        val n = list.size
        val tier = when {
            index >= n * 3 / 4 -> MedalTier.PLATINUM
            index >= n / 2 -> MedalTier.GOLD
            index >= n / 4 -> MedalTier.SILVER
            else -> MedalTier.BRONZE
        }
        medal.copy(tier = tier)
    }

    /** 按组取（组内已排好序）。 */
    private val GROUPS: Map<MedalGroup, List<Medal>> = mapOf(
        MedalGroup.DAYS to withTiers(DAYS),
        MedalGroup.DISTANCE to withTiers(DISTANCE),
        MedalGroup.PROGRESS to withTiers(PROGRESS),
        MedalGroup.CITY to withTiers(CITY),
        MedalGroup.PROVINCE to withTiers(PROVINCE),
        MedalGroup.COST to withTiers(COST),
        MedalGroup.DAILY to withTiers(DAILY),
        MedalGroup.CATEGORY to withTiers(CATEGORY),
    )

    /** 展示顺序 = 分组顺序（陪伴 → 里程 → 目标 → 城市 → 省份 → 记账 → 单日 → 分类）。 */
    val ALL: List<Medal> = MedalGroup.entries.flatMap { GROUPS[it].orEmpty() }

    init {
        // 定了 108 就不让它悄悄变：以后加勋章必须同时改这里和单测
        check(ALL.size == 108) { "勋章表应为 108 枚，实际 ${ALL.size}" }
        check(ALL.map { it.id }.toSet().size == ALL.size) { "勋章 id 有重复" }
    }

    fun of(group: MedalGroup): List<Medal> = GROUPS[group].orEmpty()

    fun byId(id: String): Medal? = ALL.firstOrNull { it.id == id }

    fun unlockedIds(stats: MedalStats): Set<String> =
        ALL.filter { it.unlocked(stats) }.map { it.id }.toSet()

    /** 某组里已解锁的数量。 */
    fun unlockedCount(group: MedalGroup, stats: MedalStats): Int =
        of(group).count { it.unlocked(stats) }

    /**
     * 「下一枚」：挑**完成度最高**的未解锁勋章。
     *
     * 不用表里的第一枚 —— 108 枚排下来，第一枚可能是「五十万公里」，
     * 提示就会变成一句毫无意义的远期目标。挑最接近的，才有「再走两步就能拿」的感觉。
     */
    fun next(stats: MedalStats): Medal? = ALL
        .filterNot { it.unlocked(stats) }
        .mapNotNull { medal -> medal.ratio(stats)?.let { medal to it } }
        .maxByOrNull { it.second }
        ?.first

    /** 组内最接近解锁的那枚（勋章墙每组下面点题用）。 */
    fun nextIn(group: MedalGroup, stats: MedalStats): Medal? = of(group)
        .filterNot { it.unlocked(stats) }
        .mapNotNull { medal -> medal.ratio(stats)?.let { medal to it } }
        .maxByOrNull { it.second }
        ?.first
}
