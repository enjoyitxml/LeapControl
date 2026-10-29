package com.leapcontrol.app.journal

/**
 * 零记首页可自定义的卡片。
 *
 * 顺序按逗号拼串存进 SharedPreferences，因此**枚举常量名即存储协议**，
 * 改名等于让老用户自定义的顺序失效（会退回默认），不要随便改。
 *
 * [half] = 半屏卡：相邻两张半屏卡会并排显示（保险在左、保养在右）。
 * 只剩一张时会自动撑成整行，不会在旁边留一块空白。
 */
enum class JournalCardId(
    val title: String,
    val subtitle: String,
    val half: Boolean = false,
) {
    GOAL("用车目标", "陪伴进度与勋章"),
    INSURANCE("保险续费", "到期倒计时与续保提醒", half = true),
    MAINTENANCE("保养提醒", "还剩多少天 / 多少公里该保养", half = true),
    COST("用车成本", "每月花费与分类占比"),
    FOOTPRINT("足迹", "打卡城市与心愿单"),
}

/** 卡片区里的一行：通常一张卡，两张半屏卡相邻时合成一行。 */
data class JournalRow(val cards: List<JournalCardId>) {
    /** 这一行是不是「两张半屏卡并排」。 */
    val paired: Boolean get() = cards.size == 2
}

/**
 * 零记卡片顺序 / 显隐的纯逻辑（读写都走这里，便于单测）。
 *
 * 与首页不同的地方：这里没有「新增卡片自动补进老用户列表」的机制 ——
 * 零记一共就五张卡，[DEFAULT] 里全都有，不存在「新版本又多出一张」的情况。
 * 但**仍然保留版本号**：哪天真加了第六张卡，可以按首页那套 [HomeCardLayout]
 * 的补入逻辑扩展，不必再改存储格式。
 */
object JournalCardLayout {

    const val VERSION = 1

    /** 默认顺序 = 之前写死的呈现顺序：提醒并排在上，其次目标、成本、足迹。 */
    val DEFAULT: List<JournalCardId> = listOf(
        JournalCardId.INSURANCE,
        JournalCardId.MAINTENANCE,
        JournalCardId.GOAL,
        JournalCardId.COST,
        JournalCardId.FOOTPRINT,
    )

    /**
     * 解析已保存的顺序。
     *
     * - `null`（从没保存过）→ [DEFAULT]；
     * - 空串（用户把卡片全删了）→ 空列表，不要偷偷把卡片变回来；
     * - 非法 / 重复 id 过滤掉，重复只保留第一次出现；降级安装遇到的未知 id 直接忽略。
     */
    fun parse(raw: String?): List<JournalCardId> {
        if (raw == null) return DEFAULT
        if (raw.isBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { token -> JournalCardId.entries.firstOrNull { it.name == token.trim() } }
            .distinct()
    }

    fun serialize(cards: List<JournalCardId>): String =
        cards.distinct().joinToString(",") { it.name }

    /** 还没显示出来的卡片（编辑态里的「添加卡片」用它）。 */
    fun hidden(cards: List<JournalCardId>): List<JournalCardId> =
        JournalCardId.entries.filter { it !in cards }

    /**
     * 把一维顺序切成排版行：**相邻的两张半屏卡并成一行**，其余各自一行。
     *
     * 「相邻」是硬条件 —— 半屏卡被拖到两张整行卡中间时，它单独占一行并撑成整行，
     * 而不是跑到某张整行卡旁边挤成半屏（那会让页面右半边空掉一块）。
     */
    fun rows(cards: List<JournalCardId>): List<JournalRow> {
        val out = ArrayList<JournalRow>(cards.size)
        var i = 0
        while (i < cards.size) {
            val current = cards[i]
            val next = cards.getOrNull(i + 1)
            if (current.half && next != null && next.half) {
                out += JournalRow(listOf(current, next))
                i += 2
            } else {
                out += JournalRow(listOf(current))
                i += 1
            }
        }
        return out
    }

    /** 把排版行摊平回一维顺序（保存时用）。 */
    fun flatten(rows: List<JournalRow>): List<JournalCardId> = rows.flatMap { it.cards }

    /**
     * 在编辑态里添加一张卡。
     *
     * 半屏卡有讲究：页面上要是正好有一张**落单**的半屏卡（同伴被删了），
     * 新加的这张会插在它前面凑成一对，而不是孤零零排到末尾 ——
     * 否则「删了保险再添加回来」会变成保养在左、保险在右，和默认左右相反。
     */
    fun withAdded(order: List<JournalCardId>, card: JournalCardId): List<JournalCardId> {
        if (order.isEmpty()) return listOf(card)
        if (!card.half) return order + card
        val lone = rows(order).firstOrNull { !it.paired && it.cards.single().half } ?: return order + card
        val at = order.indexOf(lone.cards.single())
        val out = order.toMutableList()
        out.add(at, card)
        return out
    }
}
