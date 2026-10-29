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
    HEALTH("车辆健康", "电池温度电压电流与耗电监测"),
    OWNERSHIP("相伴", "提车天数与累计公里"),

    // 下面两张是「可选」卡片（见 [HomeCardLayout] 里关于 ADDED_IN 的说明）：
    // 没设置计划时它们只是一张「去设置」的引导，不该默认塞进每个人的首页。
    INSURANCE("保险续费", "到期倒计时与续保提醒"),
    MAINTENANCE("保养提醒", "还剩多少天 / 多少公里该保养"),
}

/** 首页卡片顺序 / 显隐的纯逻辑（读写都走这里，便于单测）。 */
object HomeCardLayout {

    /**
     * 存储协议版本。**新增卡片时 +1，并把新卡登记到 [ADDED_IN]**，
     * 否则已经保存过顺序的老用户永远看不到新卡（他保存的列表里没有这张）。
     *
     * 例外：`INSURANCE` / `MAINTENANCE` 这两张**故意不登记**、也不进 [DEFAULT]。
     * 它们是「有保养 / 保险计划才有意义」的提醒卡，没设置时只是一张引导卡；
     * 直接补进所有人的首页，等于替用户做了一次他未必想要的排版改动。
     * 要看它们，编辑态里的「添加卡片」里就有。
     */
    const val VERSION = 2

    /** 默认顺序：新卡的默认位置也参照这里（升级补入时按相对位置插）。 */
    val DEFAULT: List<HomeCardId> = listOf(
        HomeCardId.AC,
        HomeCardId.TIRE_TRIP,
        HomeCardId.COMPOSITION,
        HomeCardId.RANK,
        HomeCardId.HEALTH,
        HomeCardId.OWNERSHIP,
    )

    /** 每个版本新增的卡片（键是引入它的版本号）。 */
    private val ADDED_IN: Map<Int, List<HomeCardId>> = mapOf(
        2 to listOf(HomeCardId.HEALTH),
    )

    /**
     * 解析已保存的顺序。
     *
     * - `null`（从没保存过）→ [DEFAULT]，保证老用户装上新版就能看到新卡；
     * - 空串（用户把卡片全删了）→ 空列表，不要偷偷把卡片变回来；
     * - 非法 id 忽略；重复 id 只保留第一次出现；**未知的新枚举值**（降级安装）忽略；
     * - [savedVersion] 小于 [VERSION] 时，把这段时间新增、而用户列表里没有的卡补进去，
     *   插在「默认顺序里它前面那张卡」的后面。传 [VERSION] 表示不做补入（默认值）。
     */
    fun parse(raw: String?, savedVersion: Int = VERSION): List<HomeCardId> {
        if (raw == null) return DEFAULT
        val saved = if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(',')
                .mapNotNull { token -> HomeCardId.entries.firstOrNull { it.name == token.trim() } }
                .distinct()
        }
        if (savedVersion >= VERSION) return saved
        return mergeNewCards(saved, savedVersion)
    }

    fun serialize(cards: List<HomeCardId>): String =
        cards.distinct().joinToString(",") { it.name }

    /** 还没显示出来的卡片（编辑态里的「添加卡片」用它）。 */
    fun hidden(cards: List<HomeCardId>): List<HomeCardId> =
        HomeCardId.entries.filter { it !in cards }

    /**
     * 这张卡的数据来自「零记」吗？
     *
     * 保险 / 保养的金额、日期、里程全存在零记的 JSON 里，零记关掉之后它们只剩一张
     * 「去设置」的空壳，点开还得进零记的设置弹层 —— 所以跟着一起收起。
     */
    fun usesJournal(card: HomeCardId): Boolean =
        card == HomeCardId.INSURANCE || card == HomeCardId.MAINTENANCE

    /**
     * 零记关闭时要隐藏的卡片。
     *
     * 只影响「显示」，不动用户存下的顺序：重新打开零记，卡片会回到原来的位置。
     */
    fun visible(cards: List<HomeCardId>, journalEnabled: Boolean): List<HomeCardId> =
        if (journalEnabled) cards else cards.filterNot { usesJournal(it) }

    /** 编辑态「添加卡片」里可选的项（零记关闭时不提供那两张卡）。 */
    fun hidden(cards: List<HomeCardId>, journalEnabled: Boolean): List<HomeCardId> =
        hidden(cards).filter { journalEnabled || !usesJournal(it) }

    /**
     * 保存编辑结果时把「暂时隐藏」的零记卡插回去。
     *
     * 零记关着的时候卡片区只渲染 [visible] 的结果，拖动 / 删除提交上来的列表里
     * 天然没有那两张卡；直接落盘等于把用户排好的位置抹掉了。
     *
     * 插回规则：**留在它原来的序号上**（列表比原来短就贴到末尾）。选序号而不是
     * 「跟着某张邻居卡走」，是因为用户可能同时挪了别的卡 —— 跟着邻居会把它一路带到
     * 屏幕最上面，序号则最多偏一格，看起来还是原来那个位置。
     *
     * 只处理 [stored] 里确实存在的卡：用户在关零记之前就删掉的，不能凭空变回来。
     */
    fun restore(visible: List<HomeCardId>, stored: List<HomeCardId>): List<HomeCardId> {
        var out = visible
        stored.forEachIndexed { index, card ->
            if (!usesJournal(card) || card in out) return@forEachIndexed
            val at = index.coerceIn(0, out.size)
            out = out.toMutableList().apply { add(at, card) }
        }
        return out
    }

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

    /** 把 (savedVersion, VERSION] 之间新增的卡片补进用户列表。 */
    private fun mergeNewCards(saved: List<HomeCardId>, savedVersion: Int): List<HomeCardId> {
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
    private fun insertByDefaultOrder(list: List<HomeCardId>, card: HomeCardId): List<HomeCardId> {
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
