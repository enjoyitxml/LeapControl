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
    OWNERSHIP("相伴", "提车天数、累计公里与累计用电"),

    // 车辆位置 + 目的地：**一张卡承载两个动作**。
    // 上半是车况报文里的经纬度（信号 2/3）画成的地图，下半并排「导航到车辆」与「发送目的地」。
    // 原来这是两张卡（「将目的地发送到车机」横条 + 「车辆位置」图卡），
    // 同一把高德 Key、同一趟上车前的操作，合成一张后不用来回翻。
    // 熄火后车机休眠，位置冻结在熄火点 —— 这恰好就是「车停在哪」的答案。
    LOCATION("车辆位置与目的地", "看车停在哪，也能发目的地、拍车位"),

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
     *
     * 版本号 4 是「将目的地发送到车机」、5 是「车辆位置」；6 把这**两张合并成一张**
     * （[HomeCardId.LOCATION]）。合并时不能简单删掉旧 id：老用户存下的
     * `...DESTINATION...` 会解析失败、卡片凭空消失 —— 所以 [LEGACY_IDS] 把旧 id
     * 映射到合并后的卡，版本 6 再登记一次 [HomeCardId.LOCATION]，
     * 让停在 4/5 的设备也能补上。
     *
     * 版本 7 **没有新增卡片**，只是调整了默认顺序（空调、位置卡提到最前）。
     * 升版本号是为了让从没排过卡的老设备跟着新默认走（见 [PREVIOUS_DEFAULT] 的判定）。
     */
    const val VERSION = 7

    /**
     * 默认顺序，按「上车前 → 上车时 → 开车中 → 停车后」的使用节奏排：
     *
     *  1. **空调**：出门前先开的那个，一次点击就生效；
     *  2. **车辆位置与目的地**：找车 / 发目的地，和空调一样是「到车前」的动作，紧随其后；
     *  3. **胎压 · 行程**：上车扫一眼，安全类里最该看的一项；
     *  4. **车辆健康**：电池温度 / 电压 / 电流，与胎压同属「看一眼就放心」的监测卡，连在一起；
     *  5. **周能耗分布**：停车后才看的分析信息，占比一眼就懂；
     *  6. **近 6 周能耗**：同一组能耗数据的时间趋势，跟上一张连着读；
     *  7. **相伴**：提车天数 / 累计里程，长期回顾，放最后不碍事。
     */
    val DEFAULT: List<HomeCardId> = listOf(
        HomeCardId.AC,
        HomeCardId.LOCATION,
        HomeCardId.TIRE_TRIP,
        HomeCardId.HEALTH,
        HomeCardId.COMPOSITION,
        HomeCardId.RANK,
        HomeCardId.OWNERSHIP,
    )

    /**
     * 上一版（v6）的默认顺序，**只用来判断「这个用户到底有没有自己排过卡」**。
     *
     * 为什么要它：`AppRuntime` 启动时会把 `parse` 的结果连同版本号一起写回 prefs，
     * 所以装过一次旧版的人，prefs 里都躺着一份「当时的默认顺序」——它和「用户亲手拖出来的顺序」
     * 在存储上长得一模一样。改默认顺序时如果无脑覆盖，会把别人精心排过的版式抹掉；
     * 如果什么都不做，只有新装的用户能看到新顺序，老用户永远停在旧版式。
     *
     * 折中：**存的正好等于上一版默认 = 从没动过 → 跟着新默认走**；
     * 只要差一项（删过卡、拖过位置）就视为自定义，原样保留。
     */
    private val PREVIOUS_DEFAULT: List<HomeCardId> = listOf(
        HomeCardId.AC,
        HomeCardId.TIRE_TRIP,
        HomeCardId.COMPOSITION,
        HomeCardId.RANK,
        HomeCardId.HEALTH,
        HomeCardId.LOCATION,
        HomeCardId.OWNERSHIP,
    )

    /**
     * 已经删掉的旧卡片 id → 现在承载它的卡片。
     *
     * 枚举常量名就是存储协议，删一个 id 等于让所有存过它的老用户少一张卡。
     * 合并卡片时必须留这张映射表。
     */
    private val LEGACY_IDS: Map<String, HomeCardId> = mapOf(
        // 「将目的地发送到车机」并进了「车辆位置」
        "DESTINATION" to HomeCardId.LOCATION,
    )

    /** 每个版本新增的卡片（键是引入它的版本号）。 */
    private val ADDED_IN: Map<Int, List<HomeCardId>> = mapOf(
        2 to listOf(HomeCardId.HEALTH),
        6 to listOf(HomeCardId.LOCATION),
    )

    /**
     * 解析已保存的顺序。
     *
     * - `null`（从没保存过）→ [DEFAULT]，保证老用户装上新版就能看到新卡；
     * - 空串（用户把卡片全删了）→ 空列表，不要偷偷把卡片变回来；
     * - 非法 id 忽略；**已合并的旧 id 走 [LEGACY_IDS] 映射到新卡**；
     *   重复 id 只保留第一次出现；**未知的新枚举值**（降级安装）忽略；
     * - [savedVersion] 小于 [VERSION] 时，把这段时间新增、而用户列表里没有的卡补进去，
     *   插在「默认顺序里它前面那张卡」的后面。传 [VERSION] 表示不做补入（默认值）。
     */
    fun parse(raw: String?, savedVersion: Int = VERSION): List<HomeCardId> {
        if (raw == null) return DEFAULT
        val saved = if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(',')
                .mapNotNull { token ->
                    val name = token.trim()
                    HomeCardId.entries.firstOrNull { it.name == name } ?: LEGACY_IDS[name]
                }
                // 合并卡：老列表里「发送到车机」和「车辆位置」并存时只留第一次出现的位置
                .distinct()
        }
        if (savedVersion >= VERSION) return saved
        // v7 只调了默认顺序：从没动过的列表换成新默认，自定义过的原样带下去。
        // 注意这里不能直接 return DEFAULT —— 用户删掉的卡会被"顺便"补回来。
        val base = if (saved == PREVIOUS_DEFAULT) DEFAULT else saved
        return mergeNewCards(base, savedVersion)
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
