package com.leapcontrol.app

import com.leapcontrol.app.widget.WidgetBackdrops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件背景的选取规则。
 *
 * 这几条都是「点车模换背景」和「个性化里点缩略图」共用的判断，
 * 出错的后果很直观：点一下没反应、或者切到另一套图里去了。
 */
class WidgetBackdropsTest {

    @Test
    fun `两套图分别有 6 张和 4 张`() {
        assertEquals(6, WidgetBackdrops.DAY.size)
        assertEquals(4, WidgetBackdrops.NIGHT.size)
    }

    @Test
    fun `id 不重复，两套之间也不撞`() {
        val ids = (WidgetBackdrops.DAY + WidgetBackdrops.NIGHT).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `没选背景时点车模从第一张开始`() {
        assertEquals(WidgetBackdrops.DAY.first().id, WidgetBackdrops.next(null, night = false))
        assertEquals(WidgetBackdrops.DAY.first().id, WidgetBackdrops.next(WidgetBackdrops.NONE, false))
        assertEquals(WidgetBackdrops.NIGHT.first().id, WidgetBackdrops.next(WidgetBackdrops.NONE, true))
    }

    @Test
    fun `走完一轮回到无背景`() {
        val last = WidgetBackdrops.DAY.last().id
        assertEquals(WidgetBackdrops.NONE, WidgetBackdrops.next(last, night = false))
        assertEquals(WidgetBackdrops.NONE, WidgetBackdrops.next(WidgetBackdrops.NIGHT.last().id, true))
    }

    @Test
    fun `循环顺序就是图集顺序`() {
        val list = WidgetBackdrops.NIGHT
        list.zipWithNext().forEach { (a, b) ->
            assertEquals(b.id, WidgetBackdrops.next(a.id, night = true))
        }
    }

    @Test
    fun `旧 id 认不出来时当作没选`() {
        assertNull(WidgetBackdrops.byId("day_99", night = false))
        assertNull(WidgetBackdrops.byId(null, night = true))
        // 日间的 id 拿到夜间那套里也认不出来（两套不共用）
        assertNull(WidgetBackdrops.byId(WidgetBackdrops.DAY.first().id, night = true))
        assertNotNull(WidgetBackdrops.byId(WidgetBackdrops.DAY.first().id, night = false))
    }

    @Test
    fun `每张图都有名字和非零资源`() {
        (WidgetBackdrops.DAY + WidgetBackdrops.NIGHT).forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.res != 0)
        }
    }
}
