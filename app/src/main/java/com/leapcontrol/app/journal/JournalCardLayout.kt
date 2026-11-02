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
 * 补卡机制与首页 [com.leapcontrol.app.HomeCardLayout] 同一套：
 * **存的正好等于上一版默认 = 从没动过 → 跟着新默认走；差一项就视为自定义，只把新卡插进去。**
 * 不这么做的话，排过卡的老用户永远看不到新卡（他保存的列表里没有这张）。
 */
object JournalCardLayout {

    /**
     * 存储协议版本。**新增卡片时 +1 并把新卡登记到 [ADDED_IN]**，
     * 否则已经保存过顺序的老用户永远看不到新卡。
     *
     * 1 → 2：没有新增卡片，只是把「用车成本」提到默认第一位（用户要求）。
     * 同样要升版本，否则老用户存着的那份「旧默认顺序」会被当成他亲手排的，改动永远不生效。
     */
    const val VERSION = 2

    /** 默认顺序：本月花费最常看，放最上；两张提醒其次（不记就会忘），再是目标、足迹。 */
    val DEFAULT: List<JournalCardId> = listOf(
        JournalCardId.COST,
        JournalCardId.INSURANCE,
        JournalCardId.MAINTENANCE,
        JournalCardId.GOAL,
        JournalCardId.FOOTPRINT,
    )

    /**
     * 上一版的默认顺序，**只用来判断「这个用户到底有没有自己排过卡」**。
     *
     * 零记启动时会把 parse 的结果写回 prefs，于是老用户手里躺着一份「当时的默认顺序」——
     * 它和「用户亲手拖出来的顺序」在存储上长得一模一样，只能靠内容比对区分。
     *
     * 所以**每次改 [DEFAULT] 都必须把「改之前的默认」搬到这里**，
     * 否则存的正好等于旧默认的人会被判成自定义，看不到新默认。
     */
    private val PREVIOUS_DEFAULT: List<JournalCardId> = listOf(
        JournalCardId.INSURANCE,
        JournalCardId.MAINTENANCE,
        JournalCardId.GOAL,
        JournalCardId.COST,
        JournalCardId.FOOTPRINT,
    )

    /** 每个版本新增的卡片（键是引入它的版本号）。加卡时在这里登记。 */
    private val ADDED_IN: Map<Int, List<JournalCardId>> = emptyMap()

    /**
     * 解析已保存的顺序。
     *
     * - `null`（从没保存过）→ [DEFAULT]；
     * - 空串（用户把卡片全删了）→ 空列表，不要偷偷把卡片变回来；
     * - 非法 / 重复 id 过滤掉，重复只保留第一次出现；降级安装遇到的未知 id 直接忽略；
     * - [savedVersion] 小于 [VERSION] 时，把这段时间新增、而用户列表里没有的卡补进去，
     *   插在「默认顺序里它前面那张卡」的后面。传 [VERSION] 表示不做补入（默认值）。
     */
    fun parse(raw: String?, savedVersion: Int = VERSION): List<JournalCardId> {
        if (raw == null) return DEFAULT
        val saved = if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(',')
                .mapNotNull { token -> JournalCardId.entries.firstOrNull { it.name == token.trim() } }
                .distinct()
        }
        if (savedVersion >= VERSION) return saved
        // 空串是「用户亲手把卡片全删了」，不是「还没见过新卡」：不补。
        if (raw.isBlank()) return saved
        // 从没动过的列表直接换成新默认；动过的原样保留，只把新卡插进去。
        // 不能无条件 return DEFAULT —— 用户删掉的卡会被"顺便"补回来。
        val base = if (saved == PREVIOUS_DEFAULT) DEFAULT else saved
        return mergeNewCards(base, savedVersion)
    }

    fun serialize(cards: List<JournalCardId>): String =
        cards.distinct().joinToString(",") { it.name }

    /** 还没显示出来的卡片（编辑态里的「添加卡片」用它）。 */
    fun hidden(cards: List<JournalCardId>): List<JournalCardId> =
        JournalCardId.entries.filter { it !in cards }

    /** 把 (savedVersion, VERSION] 之间新增的卡片补进用户列表。 */
    private fun mergeNewCards(saved: List<JournalCardId>, savedVersion: Int): List<JournalCardId> {
        var list = saved
        for (version in (savedVersion + 1)..VERSION) {
            ADDED_IN[version].orEmpty().forEach { card ->
                if (card !in list) list = insertByDefaultOrder(list, card)
            }
        }
        return list
    }

    /**
     * 按 [DEFAULT] 里的相对位置插入：找默认顺序中排在它前面、且用户列表里也有的卡，
     * 插到那张卡后面；一张都没有就放最前（用户把前面的卡全删了的情况）。
     */
    private fun insertByDefaultOrder(list: List<JournalCardId>, card: JournalCardId): List<JournalCardId> {
        val index = DEFAULT.indexOf(card)
        if (index < 0) return list
        for (i in index - 1 downTo 0) {
            val at = list.indexOf(DEFAULT[i])
            if (at >= 0) {
                val out = list.toMutableList()
                out.add(at + 1, card)
                return out
            }
        }
        return listOf(card) + list
    }
}
