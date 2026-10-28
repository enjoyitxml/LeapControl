package com.leapcontrol.app

import com.leapcontrol.app.journal.CostCategory
import com.leapcontrol.app.journal.MedalGroup
import com.leapcontrol.app.journal.MedalStats
import com.leapcontrol.app.journal.MedalTier
import com.leapcontrol.app.journal.Medals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 勋章体系（108 枚 / 8 组 / 4 个金属档位）。
 *
 * 数量、分组、递增顺序都钉在这里：加一枚勋章忘了改数量、或者把某组阈值写反
 * （「二十城」排在「十城」前面），单测会立刻报出来 —— 这类错在界面上很难发现，
 * 只会表现为「明明到了却没解锁」。
 */
class MedalsTest {

    private val richStats = MedalStats(
        days = 4000,
        fraction = 1.2f,
        km = 600_000.0,
        cities = 120,
        provinces = 30,
        costCount = 600,
        maxDailyKm = 1_200.0,
        categories = CostCategory.entries.toSet(),
    )

    @Test
    fun `勋章正好一百零八枚`() {
        assertEquals(108, Medals.ALL.size)
        assertEquals(Medals.ALL.size, Medals.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `每组的数量`() {
        assertEquals(16, Medals.of(MedalGroup.DAYS).size)
        assertEquals(16, Medals.of(MedalGroup.DISTANCE).size)
        assertEquals(18, Medals.of(MedalGroup.PROGRESS).size)
        assertEquals(16, Medals.of(MedalGroup.CITY).size)
        assertEquals(12, Medals.of(MedalGroup.PROVINCE).size)
        assertEquals(14, Medals.of(MedalGroup.COST).size)
        assertEquals(8, Medals.of(MedalGroup.DAILY).size)
        assertEquals(8, Medals.of(MedalGroup.CATEGORY).size)
        // 八组加起来正好是总数，别出现「组里有、ALL 里没有」
        assertEquals(108, MedalGroup.entries.sumOf { Medals.of(it).size })
    }

    @Test
    fun `组内的阈值严格递增`() {
        MedalGroup.entries.filter { it != MedalGroup.CATEGORY }.forEach { group ->
            val thresholds = Medals.of(group).map { it.threshold }
            thresholds.zipWithNext().forEach { (a, b) ->
                assertTrue("$group 的阈值没有递增：$a → $b", b > a)
            }
        }
    }

    @Test
    fun `档位从头到尾由低到高`() {
        MedalGroup.entries.forEach { group ->
            val list = Medals.of(group)
            assertEquals("$group 第一枚应当是青铜", MedalTier.BRONZE, list.first().tier)
            assertEquals("$group 最后一枚应当是铂金", MedalTier.PLATINUM, list.last().tier)
            list.map { it.tier.ordinal }.zipWithNext().forEach { (a, b) ->
                assertTrue("$group 的档位在中间掉档：$a → $b", b >= a)
            }
        }
    }

    @Test
    fun `天数与进度勋章`() {
        val stats = MedalStats(days = 301, fraction = 0.08f)
        val unlocked = Medals.unlockedIds(stats)
        assertTrue(unlocked.contains("day_100"))
        assertFalse(unlocked.contains("day_365"))
        assertEquals("差 64 天", Medals.byId("day_365")!!.gapText(stats))
        assertEquals("差 2% 进度", Medals.byId("p_10")!!.gapText(stats))
    }

    @Test
    fun `城市与省份勋章`() {
        val stats = MedalStats(cities = 3, provinces = 2)
        val unlocked = Medals.unlockedIds(stats)
        assertTrue(unlocked.contains("city_3"))
        assertFalse(unlocked.contains("city_5"))
        assertTrue(unlocked.contains("prov_2"))
        assertFalse(unlocked.contains("prov_3"))
        assertEquals("差 2 座城市", Medals.byId("city_5")!!.gapText(stats))
        assertEquals("差 1 个省份", Medals.byId("prov_3")!!.gapText(stats))
    }

    @Test
    fun `记账笔数与单日里程勋章`() {
        val stats = MedalStats(costCount = 12, maxDailyKm = 320.0)
        val unlocked = Medals.unlockedIds(stats)
        assertTrue(unlocked.contains("cost_10"))
        assertFalse(unlocked.contains("cost_20"))
        assertEquals("差 8 笔记账", Medals.byId("cost_20")!!.gapText(stats))
        assertTrue(unlocked.contains("daily_300"))
        assertFalse(unlocked.contains("daily_400"))
        assertEquals("单日差 80 公里", Medals.byId("daily_400")!!.gapText(stats))
    }

    @Test
    fun `分类勋章按花过哪一类解锁`() {
        val stats = MedalStats(categories = setOf(CostCategory.CHARGE))
        val unlocked = Medals.unlockedIds(stats)
        assertTrue(unlocked.contains("cat_charge"))
        assertFalse(unlocked.contains("cat_fuel"))
        assertEquals("还没有加油花费", Medals.byId("cat_fuel")!!.gapText(stats))
    }

    @Test
    fun `里程勋章没有数据时不瞎算`() {
        val stats = MedalStats(km = null)
        val medal = Medals.byId("km_1000")!!
        assertFalse(medal.unlocked(stats))
        assertEquals("还没有里程数据", medal.gapText(stats))
        assertNull(medal.ratio(stats))
    }

    @Test
    fun `里程差距带千分位`() {
        val stats = MedalStats(km = 1_000.0)
        assertEquals("差 9,000 公里", Medals.byId("km_10000")!!.gapText(stats))
    }

    @Test
    fun `数据拉满时一百零八枚全亮`() {
        assertEquals(108, Medals.unlockedIds(richStats).size)
        assertNull(Medals.next(richStats))
    }

    @Test
    fun `下一枚与本组下一枚`() {
        val stats = MedalStats(days = 301, fraction = 0.08f, cities = 9)
        val next = Medals.next(stats)
        assertNotNull(next)
        // 「十城」完成度 90%，比「一年」的 82.5% 更近 → 跨组挑最接近的，而不是表里第一枚
        assertEquals("city_10", next!!.id)
        val nextInDays = Medals.nextIn(MedalGroup.DAYS, stats)
        assertEquals("day_365", nextInDays!!.id)
    }
}
