package com.leapcontrol.app.journal

import java.time.LocalDate

/**
 * 一句话要做的事。
 *
 * 目前只有两件：记一笔花费、点亮一座城市。一句话里可能同时出现
 * （「今天去了杭州，充电 50」），所以这里用列表而不是单个动作。
 */
sealed interface QuickAction {
    data class Cost(val parsed: ParsedCost) : QuickAction
    data class Checkin(val city: City, val date: Long) : QuickAction
}

/**
 * 一句话解析出来的计划。
 *
 * 只做解析、不碰任何数据：解析是纯函数，能不能记账/打卡由 UI 调
 * [JournalRuntime] 的现有方法完成，两边都不特殊化。
 */
data class QuickPlan(val actions: List<QuickAction>) {

    val cost: ParsedCost? get() = actions.filterIsInstance<QuickAction.Cost>().firstOrNull()?.parsed

    val checkin: QuickAction.Checkin? get() = actions.filterIsInstance<QuickAction.Checkin>().firstOrNull()

    val isEmpty: Boolean get() = actions.isEmpty()

    /** 预览与回执的文案：「杭州打卡 · 充电 ¥50」。 */
    fun describe(): String = actions.joinToString(" · ") { action ->
        when (action) {
            is QuickAction.Cost -> "${action.parsed.category.label} ¥${CostStats.money(action.parsed.amount)}"
            is QuickAction.Checkin -> "${action.city.name}打卡"
        }
    }
}

/**
 * 灵感球的一句话识别：**说/打一句，记一件事**。
 *
 * 这里做的事情很少 —— 认出城市、认出是不是在说「去过了」、把金额交给
 * [CostParser]。分成两个解析器而不是一个，是因为它们的容错方向不同：
 * 记账宁可认不出金额也不要瞎猜，打卡则只要句子里有城市名就该记下来。
 *
 * 纯函数、不碰 Android API，可单测。
 */
object QuickLog {

    /** 灵感球上方轮播的操作提示。 */
    val HINTS: List<String> = listOf(
        "说一句「今天充电花了 50」",
        "或者「今天点亮了杭州」",
        "长按说话，点一下打字",
        "说错了没关系，记完还能删",
    )

    /**
     * 打卡意图词。
     *
     * 必须明确表达「去过/到了」，否则「在杭州停车 20」会被当成打卡 ——
     * 那种句子里城市只是一个地点状语，用户想记的是停车费。
     */
    private val CHECKIN_WORDS = listOf(
        "打卡", "点亮", "插旗", "到了", "抵达", "去过", "去了", "来到",
        "开到", "走到", "玩过", "逛过", "路过", "出差到", "旅行到",
    )

    /**
     * 解析一句话。
     *
     * 判定顺序：
     *  1. 命中「城市 + 打卡意图词」→ 打卡；
     *  2. 能解析出金额 → 记账；
     *  3. 只有城市名、既没说去也没说钱（用户就说了「杭州」两个字）→ 打卡；
     *  4. 都没有 → null，让 UI 提示「再说一次」。
     *
     * @param today 用来解释「昨天」「10月26日」的基准日
     */
    fun parse(raw: String, today: LocalDate = JournalDates.today()): QuickPlan? {
        val text = CostParser.normalize(raw)
        if (text.isBlank()) return null

        val city = CityCatalog.findInText(text)
        val intent = city != null && CHECKIN_WORDS.any { text.contains(it) }
        val cost = CostParser.parse(text, today)
        // 打卡日期单独认一次：「今天去了杭州」没有金额，CostParser.parse 会直接 null，
        // 但「今天」这两个字仍然是有意义的。
        val date = CostParser.detectDate(text, today) ?: JournalDates.startOfDay(today)

        val actions = buildList {
            if (city != null && (intent || cost == null)) add(QuickAction.Checkin(city, date))
            if (cost != null) add(QuickAction.Cost(cost))
        }
        return actions.takeIf { it.isNotEmpty() }?.let { QuickPlan(it) }
    }
}
