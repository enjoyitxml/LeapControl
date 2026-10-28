package com.leapcontrol.app.journal

import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * 一句话记账的解析结果。
 *
 * [date] 是「花费发生的那一天」的 0 点时间戳；没说日期就是今天。
 */
data class ParsedCost(
    val category: CostCategory,
    val amount: Double,
    val date: Long,
    val note: String,
    /** 是否从原文里认出了日期（UI 用它决定要不要高亮「昨天」这类提示）。 */
    val hasExplicitDate: Boolean,
)

/**
 * 「一句话记账」解析器：把语音识别 / 手打的自然语言变成一条花费。
 *
 * 支持的写法（顺序无关）：
 *  - `充电 50`、`加油花了 300 块`、`保险 3800 元`、`洗车 30`、`保养 1200`
 *  - `昨天停车 15`、`10月26日 洗车 40`、`2026-10-20 轮胎 800`
 *  - `充了 1.2 万元保险`（万 = ×10000）、`¥68 洗车`
 *
 * 认不出金额就返回 null —— 宁可让用户补一次，也不要猜一个数字记进账本。
 * 全程纯函数，不碰 Android API，可单测。
 */
object CostParser {

    /** 单笔上限：一千万，超过必然是识别错误（把日期 / 里程当金额了）。 */
    private const val MAX_AMOUNT = 10_000_000.0

    /** 分类关键词：顺序有意义 —— 长词 / 更具体的词排在前面（「机油」不能被「油」抢走）。 */
    private val KEYWORDS: List<Pair<CostCategory, List<String>>> = listOf(
        CostCategory.INSURANCE to listOf(
            "交强险", "商业险", "三者险", "车损险", "保险", "车险", "续保", "保费",
        ),
        CostCategory.SERVICE to listOf(
            "维修保养", "保养", "维修", "修车", "机油", "换胎", "补胎", "轮胎", "刹车片",
            "刹车", "电瓶", "空调滤芯", "雨刮", "钣金", "喷漆", "年检", "大修", "工时费",
        ),
        CostCategory.WASH to listOf(
            "洗车", "精洗", "打蜡", "洗内饰", "内饰清洗", "洗车卡", "抛光", "镀晶",
        ),
        CostCategory.CHARGE to listOf(
            "超充", "快充", "慢充", "家充", "充电桩", "充电", "电费", "加电", "充了",
        ),
        CostCategory.FUEL to listOf(
            "加油", "汽油", "柴油", "油费", "92号", "95号", "98号", "92", "95", "98",
        ),
        CostCategory.PARKING to listOf(
            "停车费", "停车", "泊车", "车位", "地库", "月租车位",
        ),
        CostCategory.TOLL to listOf(
            "过路费", "高速费", "通行费", "收费站", "ETC", "etc", "过路",
        ),
    )

    private val UNIT_PATTERN = Regex("""(\d+(?:[.,]\d{1,2})?)\s*(万)?\s*(元|块钱|块|人民币|rmb|RMB)""")
    private val PREFIX_PATTERN = Regex("""[¥￥]\s*(\d+(?:[.,]\d{1,2})?)""")
    private val NUMBER_PATTERN = Regex("""\d+(?:[.,]\d{1,2})?""")

    private val ISO_DATE = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val CN_DATE = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""")
    private val SLASH_DATE = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?!\d)""")

    /**
     * 解析一句话。
     *
     * @param raw 语音识别或手输的原文
     * @param today 用来解释「昨天」「10月26日」的基准日
     */
    fun parse(raw: String, today: LocalDate = JournalDates.today()): ParsedCost? {
        val text = normalize(raw)
        if (text.isBlank()) return null

        val dateHit = parseDate(text, today)
        // 日期片段必须先摘掉：「10月26日」里的 10、26 不能被当成金额
        var rest = text
        dateHit?.matched?.forEach { rest = rest.replace(it, " ") }

        val amount = parseAmount(rest) ?: return null
        val category = detectCategory(text)

        return ParsedCost(
            category = category,
            amount = amount,
            date = JournalDates.startOfDay(dateHit?.date ?: today),
            note = buildNote(rest, amount),
            hasExplicitDate = dateHit != null,
        )
    }

    /** 全角数字 / 全角空格 / 常见别名统一，避免语音输入的法子各种变体漏识别。 */
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when (ch) {
                    in '０'..'９' -> ('0' + (ch - '０'))
                    '．', '。' -> '.'
                    '，' -> ','
                    '　' -> ' '
                    '：' -> ' '
                    else -> ch
                },
            )
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }

    fun detectCategory(text: String): CostCategory {
        // 「机油」同时含有「油」，所以按表顺序找，命中即返回 —— 表里保养排在加油前面
        for ((category, words) in KEYWORDS) {
            if (words.any { text.contains(it, ignoreCase = true) }) return category
        }
        return CostCategory.OTHER
    }

    private data class DateHit(val date: LocalDate, val matched: List<String>)

    private fun parseDate(text: String, today: LocalDate): DateHit? {
        // 相对日期优先：「昨天」里没有数字，不会被下面的正则抢走
        val relatives = listOf(
            "大前天" to 3L,
            "前天" to 2L,
            "昨天" to 1L,
            "昨日" to 1L,
            "今天" to 0L,
            "今日" to 0L,
            "刚才" to 0L,
        )
        relatives.firstOrNull { text.contains(it.first) }?.let { (word, back) ->
            return DateHit(today.minusDays(back), listOf(word))
        }

        ISO_DATE.find(text)?.let { m ->
            val date = runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()
            if (date != null) return DateHit(date, listOf(m.value))
        }

        CN_DATE.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            // 说「12月」而现在是 2 月，指的是去年的 12 月
            var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
                ?: return@let
            if (date.isAfter(today)) {
                date = runCatching { LocalDate.of(today.year - 1, month, day) }.getOrNull() ?: date
            }
            return DateHit(date, listOf(m.value))
        }

        SLASH_DATE.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
                ?: return@let
            if (date.isAfter(today)) {
                date = runCatching { LocalDate.of(today.year - 1, month, day) }.getOrNull() ?: date
            }
            return DateHit(date, listOf(m.value))
        }
        return null
    }

    private fun parseAmount(rest: String): Double? {
        // 1) 带「元 / 块 / 万」单位的数字最可信
        UNIT_PATTERN.find(rest)?.let { m ->
            val base = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return@let
            val scale = if (m.groupValues[2] == "万") 10_000.0 else 1.0
            return sane(base * scale)
        }
        // 2) 前缀 ￥
        PREFIX_PATTERN.find(rest)?.let { m ->
            return sane(m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return@let)
        }
        // 3) 没有单位：取最后一个「不像油号 / 度数 / 升数」的数字
        val candidates = NUMBER_PATTERN.findAll(rest).toList()
        for (m in candidates.reversed()) {
            val value = m.value.replace(",", ".").toDoubleOrNull() ?: continue
            val tail = rest.substring(m.range.last + 1).trimStart()
            // 「92 号」「50 度」「40 升」「600 公里」都是数量，不是钱
            val looksLikeQuantity = tail.startsWith("号") || tail.startsWith("度") ||
                tail.startsWith("升") || tail.startsWith("公里") || tail.startsWith("km") ||
                tail.startsWith("千米") || tail.startsWith("次") || tail.startsWith("L")
            if (looksLikeQuantity) continue
            // 油号本身（92 / 95 / 98）单独出现时不认
            if (m.value in setOf("92", "95", "98") && rest.contains("${m.value}号")) continue
            sane(value)?.let { return it }
        }
        return null
    }

    private fun sane(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it > 0.0 && it <= MAX_AMOUNT }?.let {
            (it * 100).roundToLong() / 100.0
        }

    /** 备注：原文去掉金额与多余空格；只剩数字就留空，不把「3800」当备注。 */
    private fun buildNote(rest: String, amount: Double): String {
        var text = rest
        UNIT_PATTERN.findAll(rest).forEach { text = text.replace(it.value, " ") }
        PREFIX_PATTERN.findAll(rest).forEach { text = text.replace(it.value, " ") }
        val cleaned = text.replace(NUMBER_PATTERN, " ").replace(Regex("""\s+"""), " ").trim()
        val trimmed = cleaned.trim('，', ',', '。', '.', '、', ' ')
        return if (trimmed.matches(Regex("""[^\u4e00-\u9fa5A-Za-z]*"""))) "" else trimmed
    }
}
