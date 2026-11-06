package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页卡片顺序 / 显隐（编辑态拖动、删除、添加的落盘逻辑）。 */
class HomeCardLayoutTest {

    @Test
    fun `从未保存过时用默认顺序`() {
        assertEquals(HomeCardLayout.DEFAULT, HomeCardLayout.parse(null))
    }

    @Test
    fun `保养与保险卡是可选卡，不自动塞进首页`() {
        // 这两张卡没设置计划时只是引导卡，默认顺序里不该有它们；
        // 老用户列表（savedVersion 偏低）走补入逻辑时也一样不会被塞进去。
        assertTrue(HomeCardId.INSURANCE !in HomeCardLayout.DEFAULT)
        assertTrue(HomeCardId.MAINTENANCE !in HomeCardLayout.DEFAULT)
        val old = listOf(HomeCardId.AC, HomeCardId.RANK)
        assertEquals(old, HomeCardLayout.parse("AC,RANK", savedVersion = HomeCardLayout.VERSION))
        // 低版本列表触发补入逻辑时，也只会补协议里登记过的卡，不会补这两张
        val upgraded = HomeCardLayout.parse("AC,RANK", savedVersion = 1)
        assertTrue(HomeCardId.INSURANCE !in upgraded)
        assertTrue(HomeCardId.MAINTENANCE !in upgraded)
        // 但它们必须能从「添加卡片」里被加回来
        assertTrue(HomeCardId.INSURANCE in HomeCardLayout.hidden(old))
        assertTrue(HomeCardId.MAINTENANCE in HomeCardLayout.hidden(old))
    }

    @Test
    fun `空串表示用户把卡片全删了`() {
        assertTrue(HomeCardLayout.parse("").isEmpty())
        assertTrue(HomeCardLayout.parse("   ").isEmpty())
    }

    @Test
    fun `忽略非法与重复项并保序`() {
        val parsed = HomeCardLayout.parse("RANK,GHOST,AC,RANK,COMPOSITION")
        assertEquals(
            listOf(HomeCardId.RANK, HomeCardId.AC, HomeCardId.COMPOSITION),
            parsed,
        )
    }

    @Test
    fun `序列化往返一致`() {
        val cards = listOf(HomeCardId.OWNERSHIP, HomeCardId.TIRE_TRIP, HomeCardId.RANK)
        assertEquals(cards, HomeCardLayout.parse(HomeCardLayout.serialize(cards)))
    }

    @Test
    fun `默认顺序是空调与位置卡打头`() {
        // 用户定的基调：空调第一、车辆位置与目的地第二；后面按「上车前 → 开车中 → 停车后」排
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.LOCATION,
                HomeCardId.TIRE_TRIP,
                HomeCardId.HEALTH,
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.OWNERSHIP,
            ),
            HomeCardLayout.DEFAULT,
        )
    }

    @Test
    fun `充电里程默认排在周能耗分布上方`() {
        // 两张都是「回来复盘」的数据，一上一下顺着读
        val default = HomeCardLayout.DEFAULT
        assertEquals(
            default.indexOf(HomeCardId.COMPOSITION) - 1,
            default.indexOf(HomeCardId.CHARGE_CYCLE),
        )
    }

    @Test
    fun `位置与目的地合成为一张卡`() {
        // 「将目的地发送到车机」不再是独立卡片：枚举里只留合并后的 LOCATION，
        // 否则首页会同时出现两张内容重叠的卡
        assertEquals(
            listOf(
                "AC",
                "TIRE_TRIP",
                "CHARGE_CYCLE",
                "COMPOSITION",
                "RANK",
                "HEALTH",
                "OWNERSHIP",
                "LOCATION",
                "INSURANCE",
                "MAINTENANCE",
                "TRIP",
            ),
            HomeCardId.entries.map { it.name },
        )
        // 合并卡默认排在第二位：它和空调一样是「到车前」的动作（找车 / 发目的地）
        val default = HomeCardLayout.DEFAULT
        assertEquals(1, default.indexOf(HomeCardId.LOCATION))
        assertTrue(default.indexOf(HomeCardId.LOCATION) < default.indexOf(HomeCardId.RANK))
        assertTrue(default.indexOf(HomeCardId.LOCATION) > default.indexOf(HomeCardId.AC))
    }

    @Test
    fun `拖动排序向下`() {
        val cards = HomeCardLayout.DEFAULT
        // 第 0 张（空调）拖到第 3 位
        val moved = HomeCardLayout.move(cards, 0, 3)
        assertEquals(
            listOf(
                HomeCardId.LOCATION,
                HomeCardId.TIRE_TRIP,
                HomeCardId.HEALTH,
                HomeCardId.AC,
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.OWNERSHIP,
            ),
            moved,
        )
        assertEquals(cards.size, moved.size)
    }

    @Test
    fun `拖动排序向上`() {
        val cards = HomeCardLayout.DEFAULT
        // 第 4 张（行程轨迹）拖到第 1 位
        val moved = HomeCardLayout.move(cards, 4, 1)
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.TRIP,
                HomeCardId.LOCATION,
                HomeCardId.TIRE_TRIP,
                HomeCardId.HEALTH,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.OWNERSHIP,
            ),
            moved,
        )
    }

    @Test
    fun `越界下标不会吞卡片`() {
        val cards = HomeCardLayout.DEFAULT
        assertEquals(cards, HomeCardLayout.move(cards, 9, 1))
        assertEquals(cards, HomeCardLayout.move(cards, 1, 1))
        // to 越界时夹到末尾，元素仍保留
        assertEquals(cards.size, HomeCardLayout.move(cards, 0, 99).size)
    }

    @Test
    fun `隐藏列表是当前没显示的那些`() {
        val shown = listOf(HomeCardId.AC, HomeCardId.RANK)
        val hidden = HomeCardLayout.hidden(shown)
        // 顺序跟着枚举声明走（「添加卡片」里的排列），不是跟着 DEFAULT 走
        assertEquals(
            listOf(
                HomeCardId.TIRE_TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.COMPOSITION,
                HomeCardId.HEALTH,
                HomeCardId.OWNERSHIP,
                HomeCardId.LOCATION,
                // 保养 / 保险是「可选卡」：默认不进 DEFAULT，因此总在「添加卡片」里
                HomeCardId.INSURANCE,
                HomeCardId.MAINTENANCE,
                HomeCardId.TRIP,
            ),
            hidden,
        )
    }

    // ---- 新增卡片的升级补入 ----

    @Test
    fun `老用户保存过顺序时按默认位置补入新卡`() {
        // v1 时期保存的列表里没有 HEALTH（v2）、合并后的位置卡（v6）与充电里程（v8）
        val parsed = HomeCardLayout.parse(
            "AC,TIRE_TRIP,COMPOSITION,RANK,OWNERSHIP",
            savedVersion = 1,
        )
        assertEquals(
            listOf(
                HomeCardId.AC,
                // LOCATION 的默认前一张是空调，所以紧跟在空调后面
                HomeCardId.LOCATION,
                HomeCardId.TIRE_TRIP,
                // HEALTH 的默认前一张是胎压·行程，插在它后面
                HomeCardId.HEALTH,
                // v9 的行程轨迹排在充电里程上方，同样跟在车辆健康后面
                HomeCardId.TRIP,
                // CHARGE_CYCLE 是 v8 补的，也插在车辆健康后面
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.OWNERSHIP,
            ),
            parsed,
        )
    }

    @Test
    fun `新卡插在默认顺序里前一张卡的后面`() {
        // 用户把相伴卡挪到了最前面，新卡仍应跟在自己的前一张后面：
        // HEALTH 的前一张（胎压·行程）不在这份列表里，继续往前落到空调后
        val parsed = HomeCardLayout.parse("OWNERSHIP,RANK,AC", savedVersion = 1)
        assertEquals(
            listOf(
                HomeCardId.OWNERSHIP,
                HomeCardId.RANK,
                HomeCardId.AC,
                // LOCATION 的默认前一张也是空调，排在 HEALTH 之前
                HomeCardId.LOCATION,
                HomeCardId.HEALTH,
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
            ),
            parsed,
        )
    }

    @Test
    fun `前面的卡全被删掉时新卡放最前`() {
        val parsed = HomeCardLayout.parse("OWNERSHIP", savedVersion = 1)
        assertEquals(
            listOf(
                HomeCardId.LOCATION,
                HomeCardId.HEALTH,
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.OWNERSHIP,
            ),
            parsed,
        )
    }

    @Test
    fun `版本已是最新时不会把用户删掉的卡变回来`() {
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.RANK),
            HomeCardLayout.parse("AC,RANK", savedVersion = HomeCardLayout.VERSION),
        )
    }

    @Test
    fun `卡片全删但版本落后时补入新卡`() {
        assertEquals(
            listOf(HomeCardId.LOCATION, HomeCardId.HEALTH, HomeCardId.TRIP, HomeCardId.CHARGE_CYCLE),
            HomeCardLayout.parse("", savedVersion = 1),
        )
    }

    @Test
    fun `停在 v4 的设备能补回位置与目的地卡`() {
        // 只登记过的卡会被补进来，用户删掉的其它卡不会被带回来
        val parsed = HomeCardLayout.parse(
            "AC,RANK,OWNERSHIP",
            savedVersion = 4,
        )
        assertEquals(
            // 位置卡的默认前一张是空调，插到它后面
            listOf(HomeCardId.AC, HomeCardId.LOCATION, HomeCardId.TRIP, HomeCardId.CHARGE_CYCLE, HomeCardId.RANK, HomeCardId.OWNERSHIP),
            parsed,
        )
    }

    @Test
    fun `旧版独立的发送到车机卡升级后变成合并卡`() {
        // 0.2.54 存下的顺序里是 DESTINATION；这个 id 已经删了，
        // 必须落到合并卡上，不能静默丢掉（否则用户的卡片凭空少一张）
        val parsed = HomeCardLayout.parse(
            "AC,DESTINATION,RANK",
            savedVersion = 4,
        )
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.LOCATION, HomeCardId.TRIP, HomeCardId.CHARGE_CYCLE, HomeCardId.RANK),
            parsed,
        )
    }

    @Test
    fun `两张旧卡同时存在时只留靠前的那个位置`() {
        // 未发版的一版里两张卡并存过：合并后只能出现一张，位置取靠前的
        val parsed = HomeCardLayout.parse(
            "AC,DESTINATION,RANK,LOCATION,OWNERSHIP",
            savedVersion = 5,
        )
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.LOCATION, HomeCardId.TRIP, HomeCardId.CHARGE_CYCLE, HomeCardId.RANK, HomeCardId.OWNERSHIP),
            parsed,
        )
    }

    // ---- v8：新增充电里程卡，默认放在周能耗分布上方 ----

    /** v6 写进 prefs 的默认顺序（跳过 v7 直接升到 v8 的老设备，存的就是这一串）。 */
    private val v6Default = "AC,TIRE_TRIP,COMPOSITION,RANK,HEALTH,LOCATION,OWNERSHIP"

    /** v7 写进 prefs 的默认顺序。 */
    private val v7Default = "AC,LOCATION,TIRE_TRIP,HEALTH,COMPOSITION,RANK,OWNERSHIP"

    @Test
    fun `从没排过卡的设备升级后跟着新默认走`() {
        // AppRuntime 每次启动都会把 parse 结果写回 prefs，所以「从没动过」的人在存储上
        // 看起来就是「存着一份上一版的默认顺序」—— 升级后应该换成新的
        assertEquals(
            HomeCardLayout.DEFAULT,
            HomeCardLayout.parse(v7Default, savedVersion = 7),
        )
        assertEquals(HomeCardId.LOCATION, HomeCardLayout.parse(v7Default, savedVersion = 7)[1])
    }

    @Test
    fun `跳过 v7 的设备保留自己的顺序，只补新卡`() {
        // v6 默认和 v7 默认不一样（空调/位置卡顺序调过），无法判定「从没动过」，
        // 于是按自定义处理：顺序原样保留，只把充电里程插到车辆健康后面
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.HEALTH,
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.LOCATION,
                HomeCardId.OWNERSHIP,
            ),
            HomeCardLayout.parse(v6Default, savedVersion = 6),
        )
    }

    @Test
    fun `自己排过的顺序升级后只多一张新卡`() {
        val custom = "LOCATION,OWNERSHIP,AC,RANK"
        assertEquals(
            listOf(
                HomeCardId.LOCATION,
                // 两张新卡都跟在自己的默认前一张（位置卡）后面，用户原有的相对顺序不动
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.OWNERSHIP,
                HomeCardId.AC,
                HomeCardId.RANK,
            ),
            HomeCardLayout.parse(custom, savedVersion = 6),
        )
    }

    @Test
    fun `删过卡的列表不算从没动过，改默认时不补回来`() {
        // 用户在 v6 默认的基础上去掉了「相伴」：只要与旧默认差一项就视为自定义，
        // 既不会被强行套上新顺序，也不会把删掉的卡悄悄补回来
        val edited = "AC,TIRE_TRIP,COMPOSITION,RANK,HEALTH,LOCATION"
        val parsed = HomeCardLayout.parse(edited, savedVersion = 6)
        assertEquals(
            listOf(
                HomeCardId.AC,
                HomeCardId.TIRE_TRIP,
                HomeCardId.COMPOSITION,
                HomeCardId.RANK,
                HomeCardId.HEALTH,
                HomeCardId.TRIP,
                HomeCardId.CHARGE_CYCLE,
                HomeCardId.LOCATION,
            ),
            parsed,
        )
        assertTrue(HomeCardId.OWNERSHIP !in parsed)
    }

    @Test
    fun `新默认的顺序一经保存就不再变`() {
        // 保存过一次（版本已是最新）之后，即使用户拖乱了也原样返回
        val shuffled = HomeCardLayout.serialize(
            listOf(HomeCardId.OWNERSHIP, HomeCardId.RANK, HomeCardId.AC),
        )
        assertEquals(
            listOf(HomeCardId.OWNERSHIP, HomeCardId.RANK, HomeCardId.AC),
            HomeCardLayout.parse(shuffled, savedVersion = HomeCardLayout.VERSION),
        )
    }

    // ---- 零记总开关：保险 / 保养这两张卡的数据来自零记 ----

    @Test
    fun `零记关闭时首页不显示保险与保养卡`() {
        val all = listOf(
            HomeCardId.AC,
            HomeCardId.INSURANCE,
            HomeCardId.RANK,
            HomeCardId.MAINTENANCE,
            HomeCardId.OWNERSHIP,
        )
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.RANK, HomeCardId.OWNERSHIP),
            HomeCardLayout.visible(all, journalEnabled = false),
        )
        // 开着的时候一张都不许少
        assertEquals(all, HomeCardLayout.visible(all, journalEnabled = true))
    }

    @Test
    fun `零记关闭时添加卡片里不出现那两张卡`() {
        val shown = listOf(HomeCardId.AC)
        val off = HomeCardLayout.hidden(shown, journalEnabled = false)
        assertTrue(HomeCardId.INSURANCE !in off)
        assertTrue(HomeCardId.MAINTENANCE !in off)
        // 其它卡照旧可以加
        assertTrue(HomeCardId.RANK in off)

        val on = HomeCardLayout.hidden(shown, journalEnabled = true)
        assertTrue(HomeCardId.INSURANCE in on)
    }

    @Test
    fun `零记关闭期间编辑首页，保存时把两张卡插回原序号`() {
        val stored = listOf(
            HomeCardId.AC,
            HomeCardId.INSURANCE,
            HomeCardId.RANK,
            HomeCardId.MAINTENANCE,
            HomeCardId.OWNERSHIP,
        )
        // 关着零记时用户把「近 6 周能耗」拖到了最前
        val edited = listOf(
            HomeCardId.RANK,
            HomeCardId.AC,
            HomeCardId.OWNERSHIP,
        )
        assertEquals(
            listOf(
                HomeCardId.RANK,
                HomeCardId.INSURANCE,
                HomeCardId.AC,
                HomeCardId.MAINTENANCE,
                HomeCardId.OWNERSHIP,
            ),
            HomeCardLayout.restore(edited, stored),
        )
        // 什么都没动时，插回来必须和原样一模一样
        val untouched = listOf(HomeCardId.AC, HomeCardId.RANK, HomeCardId.OWNERSHIP)
        assertEquals(stored, HomeCardLayout.restore(untouched, stored))
    }

    @Test
    fun `关零记之前就删掉的卡不会因为重新开启而跑回来`() {
        // 用户早先把保养卡删了，只留了保险卡
        val stored = listOf(HomeCardId.AC, HomeCardId.INSURANCE)
        val edited = listOf(HomeCardId.AC)
        val restored = HomeCardLayout.restore(edited, stored)
        assertEquals(listOf(HomeCardId.AC, HomeCardId.INSURANCE), restored)
        assertTrue(HomeCardId.MAINTENANCE !in restored)
    }

    @Test
    fun `零记关闭时充电里程卡也收起`() {
        // 起点存在零记的 journal.json 里，零记关掉就没有数据可显示，别留一张空壳卡
        val all = listOf(HomeCardId.AC, HomeCardId.CHARGE_CYCLE, HomeCardId.RANK)
        assertEquals(
            listOf(HomeCardId.AC, HomeCardId.RANK),
            HomeCardLayout.visible(all, journalEnabled = false),
        )
        assertEquals(all, HomeCardLayout.visible(all, journalEnabled = true))
        assertTrue(
            HomeCardId.CHARGE_CYCLE !in
                HomeCardLayout.hidden(listOf(HomeCardId.AC), journalEnabled = false),
        )
    }

    @Test
    fun `零记卡全都不在原位时插到末尾，不吞掉用户的其它卡`() {
        val stored = listOf(HomeCardId.INSURANCE, HomeCardId.AC)
        // 用户把 AC 也删了，只剩 RANK
        val restored = HomeCardLayout.restore(listOf(HomeCardId.RANK), stored)
        assertEquals(listOf(HomeCardId.INSURANCE, HomeCardId.RANK), restored)
    }
}
