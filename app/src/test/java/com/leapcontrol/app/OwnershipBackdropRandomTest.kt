package com.leapcontrol.app

import com.leapcontrol.app.ui.OwnershipBackdrops
import com.leapcontrol.app.ui.pickOtherId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 相伴卡右上角小白点的随机规则。
 *
 * 规则只有两条，但都容易写漏：
 *  1. 只在相伴卡的 6 套内置背景里换，**不能碰主题**（这是和车模那个点的分工）；
 *  2. 必须换出一张**不同**的图，点一下看不出变化等于按钮坏了。
 */
class OwnershipBackdropRandomTest {

    private val ids = OwnershipBackdrops.ALL.map { it.id }

    // ---------------- 池子本身 ----------------

    @Test
    fun `内置背景至少两套否则随机没有意义`() {
        assertTrue("相伴卡背景只有 ${ids.size} 套", ids.size >= 2)
    }

    @Test
    fun `背景 id 不重复`() {
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `认不出的 id 退回默认那张`() {
        assertEquals(OwnershipBackdrops.DEFAULT_ID, OwnershipBackdrops.byId("not-exist").id)
        assertEquals(OwnershipBackdrops.DEFAULT_ID, OwnershipBackdrops.byId(null).id)
    }

    // ---------------- randomOther ----------------

    @Test
    fun `随机换背景永远不等于当前这张`() {
        ids.forEachIndexed { index, current ->
            repeat(30) { round ->
                val next = OwnershipBackdrops.randomOther(current, Random(index * 100 + round))
                assertNotEquals("从 $current 换出了同一张", current, next)
            }
        }
    }

    @Test
    fun `随机换背景的结果一定是内置背景之一`() {
        repeat(50) {
            val next = OwnershipBackdrops.randomOther(OwnershipBackdrops.DEFAULT_ID, Random(it))
            assertTrue("换出了未知背景 $next", next in ids)
        }
    }

    @Test
    fun `传入未知 id 时按默认那张去重`() {
        // 升级后配置里可能是已删掉的 id：先回落到默认，再从"除默认之外"里挑
        repeat(20) {
            val next = OwnershipBackdrops.randomOther("legacy-gone", Random(it))
            assertNotEquals(OwnershipBackdrops.DEFAULT_ID, next)
            assertTrue(next in ids)
        }
    }

    @Test
    fun `同一随机种子结果稳定`() {
        val a = OwnershipBackdrops.randomOther("mountain", Random(7))
        val b = OwnershipBackdrops.randomOther("mountain", Random(7))
        assertEquals(a, b)
    }

    // ---------------- 纯函数 pickOtherId ----------------

    @Test
    fun `池子只有一个元素时返回 null 表示原地不动`() {
        assertNull(pickOtherId(listOf("mountain"), "mountain", Random(1)))
    }

    @Test
    fun `候选里不会出现当前值`() {
        val pool = listOf("a", "b", "c")
        repeat(50) {
            val next = pickOtherId(pool, "b", Random(it))
            assertNotEquals("b", next)
            assertTrue(next == "a" || next == "c")
        }
    }

    @Test
    fun `当前值不在池子里时仍能从池子里挑`() {
        repeat(20) {
            val next = pickOtherId(listOf("a", "b"), "zzz", Random(it))
            assertTrue(next == "a" || next == "b")
        }
    }
}
