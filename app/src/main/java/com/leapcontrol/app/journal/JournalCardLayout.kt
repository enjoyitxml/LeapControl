package com.leapcontrol.app.journal

/**
 * 零记首页可自定义的卡片。
 *
 * 顺序按逗号拼串存进 SharedPreferences，因此**枚举常量名即存储协议**，
 * 改名等于让老用户自定义的顺序失效（会退回默认），不要随便改。
 *
 * 所有卡片都是整行横排（和零控首页的卡片同一个观感）。保险 / 保养早先是
 * 并排的两张半屏卡，靠固定高度对齐底边，副信息一长就被卡片下沿切掉，
 * 现在改成整行铺开 —— 左右顺序依然就是列表顺序。
 */
enum class JournalCardId(
    val title: String,
    val subtitle: String,
) {
    GOAL("用车目标", "陪伴进度与勋章"),
    INSURANCE("保险续费", "到期倒计时与续保提醒"),
    MAINTENANCE("保养提醒", "还剩多少天 / 多少公里该保养"),
    COST("用车成本", "每月花费与分类占比"),
    FOOTPRINT("足迹", "打卡城市与心愿单"),
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

    /** 默认顺序 = 之前写死的呈现顺序：两张提醒在上，其次目标、成本、足迹。 */
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
}
