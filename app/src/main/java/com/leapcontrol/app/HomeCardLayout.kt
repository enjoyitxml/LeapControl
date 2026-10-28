package com.leapcontrol.app

/**
 * 首页可自定义的卡片。
 *
 * 「胎压 · 行程」是一张卡：胎压卡和近 7 天行程卡本来就是并排的半屏卡，
 * 拆成两个可拖动项时，拖动过程里会出现「两个半屏卡各自占一行」的怪异中间态，
 * 所以按它们实际呈现的样子合成一个条目（删除时一起删除）。
 *
 * 顺序字符串用 id 逗号分隔存 SharedPreferences，因此**枚举常量名即存储协议**，
 * 改名等于让老用户的自定义顺序失效（会退回默认），不要随便改。
 */
enum class HomeCardId(val title: String, val subtitle: String) {
    AC("空调", "设定温度与开关"),
    TIRE_TRIP("胎压 · 行程", "四轮胎压与近 7 天里程"),
    COMPOSITION("周能耗分布", "上周行车 / 空调 / 其他占比"),
    RANK("近 6 周能耗", "百公里能耗趋势与全国排行"),
    OWNERSHIP("相伴", "提车天数与累计公里"),
}

/** 首页卡片顺序 / 显隐的纯逻辑（读写都走这里，便于单测）。 */
object HomeCardLayout {

    /** 默认顺序：新增的能耗卡插在行程卡之后、相伴卡之前。 */
    val DEFAULT: List<HomeCardId> = listOf(
        HomeCardId.AC,
        HomeCardId.TIRE_TRIP,
        HomeCardId.COMPOSITION,
        HomeCardId.RANK,
        HomeCardId.OWNERSHIP,
    )

    /**
     * 解析已保存的顺序。
     *
     * - `null`（从没保存过）→ [DEFAULT]，保证老用户装上新版就能看到新卡；
     * - 空串（用户把卡片全删了）→ 空列表，不要偷偷把卡片变回来；
     * - 非法 id 忽略；重复 id 只保留第一次出现；**未知的新枚举值**（降级安装）忽略。
     */
    fun parse(raw: String?): List<HomeCardId> {
        if (raw == null) return DEFAULT
        if (raw.isBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { token -> HomeCardId.entries.firstOrNull { it.name == token.trim() } }
            .distinct()
    }

    fun serialize(cards: List<HomeCardId>): String =
        cards.distinct().joinToString(",") { it.name }

    /** 还没显示出来的卡片（编辑态里的「添加卡片」用它）。 */
    fun hidden(cards: List<HomeCardId>): List<HomeCardId> =
        HomeCardId.entries.filter { it !in cards }

    /**
     * 把 [from] 位置的卡片移动到 [to]（编辑态拖拽松手时调用）。
     * 下标越界时原样返回，绝不吞卡片。
     */
    fun move(cards: List<HomeCardId>, from: Int, to: Int): List<HomeCardId> {
        if (from !in cards.indices) return cards
        val target = to.coerceIn(0, cards.size - 1)
        if (from == target) return cards
        val list = cards.toMutableList()
        val item = list.removeAt(from)
        list.add(target, item)
        return list
    }
}
