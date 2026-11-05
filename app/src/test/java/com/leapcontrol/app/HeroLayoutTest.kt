package com.leapcontrol.app

import com.leapcontrol.app.ui.HeroLayout
import com.leapcontrol.app.ui.vehicleStatusRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页两套布局（经典 / 状态分置）的纯逻辑部分。
 *
 * 布局本身是像素活，单测守的是三件容易翻车的事：
 *  1. 存量用户没有这个 prefs 键时必须落在经典，绝不能默认新方案；
 *  2. 右上角那几行必须「闭锁在第一行、一次只有一项」，不许退回成合并成一句；
 *  3. 红色只给真正要看的问题（未闭锁 / 车窗 / 后备箱），空调和遮阳帘不开警示色。
 */
class HeroLayoutTest {

    // ---------------------------------------------------------------- 布局选择

    @Test
    fun `没有存过布局时默认经典`() {
        assertEquals(HeroLayout.CLASSIC, HeroLayout.fromId(null))
        assertEquals(HeroLayout.CLASSIC, HeroLayout.DEFAULT)
    }

    @Test
    fun `只剩两套排法`() {
        // 分栏与「状态下移」两版用户看过实测后都撤了，这里守住不再长回来
        assertEquals(2, HeroLayout.entries.size)
        assertEquals(listOf("classic", "separate"), HeroLayout.entries.map { it.id })
    }

    @Test
    fun `存过的布局能读回来`() {
        HeroLayout.entries.forEach { layout ->
            assertEquals(layout, HeroLayout.fromId(layout.id))
        }
    }

    @Test
    fun `存了不认识的值就退回经典`() {
        // 降级安装、手改 prefs、以后删掉某个方案，都不能让首页空白
        assertEquals(HeroLayout.CLASSIC, HeroLayout.fromId(""))
        assertEquals(HeroLayout.CLASSIC, HeroLayout.fromId("split"))
        assertEquals(HeroLayout.CLASSIC, HeroLayout.fromId("{}"))
    }

    @Test
    fun `旧的 below 值映射到分置 而不是被打回经典`() {
        // 选过「状态下移」的人升级后不该发现自己的选择被抹了：
        // below 与 separate 的语义都是「把状态往车模那边搬」，映射过去才不算骗人
        assertEquals(HeroLayout.SEPARATE, HeroLayout.fromId("below"))
    }

    @Test
    fun `默认还是经典这一套`() {
        // 用户 2026-11-05 明确要求：默认沿用现在的样子，另一套要自己挑
        assertEquals("经典", HeroLayout.DEFAULT.label)
    }

    // ---------------------------------------------------------- 右上角的状态行

    @Test
    fun `闭锁永远在第一个 后面才是车窗空调`() {
        val rows = vehicleStatusRows(
            locked = true,
            acOn = true,
            windowOpen = true,
            trunkOpen = null,
            sunshadeOpen = null,
        )
        assertEquals("车辆已闭锁", rows[0].label)
        assertEquals(listOf("车辆已闭锁", "车窗已开启", "空调已开启"), rows.map { it.label })
    }

    @Test
    fun `未闭锁是红项`() {
        val rows = vehicleStatusRows(
            locked = false,
            acOn = null,
            windowOpen = null,
            trunkOpen = null,
            sunshadeOpen = null,
        )
        assertEquals("车辆未闭锁", rows.single().label)
        assertTrue(rows.single().alert)
    }

    @Test
    fun `车窗和后备箱是红项 空调遮阳帘不是`() {
        // 老版本把四种「已开启」全画成红的，空调一开像报了四个故障。
        // 「恢复旧样式」只恢复图标逐行的形态，这条颜色规矩不能一起退回去。
        val rows = vehicleStatusRows(
            locked = true,
            acOn = true,
            windowOpen = true,
            trunkOpen = true,
            sunshadeOpen = true,
        )
        val byLabel = rows.associateBy { it.label }
        assertTrue(byLabel.getValue("车窗已开启").alert)
        assertTrue(byLabel.getValue("后备箱已开启").alert)
        assertFalse(byLabel.getValue("车辆已闭锁").alert)
        assertFalse(byLabel.getValue("空调已开启").alert)
        assertFalse(byLabel.getValue("遮阳帘已开启").alert)
    }

    @Test
    fun `没开启的项不占行`() {
        val rows = vehicleStatusRows(
            locked = true,
            acOn = false,
            windowOpen = false,
            trunkOpen = false,
            sunshadeOpen = false,
        )
        assertEquals(listOf("车辆已闭锁"), rows.map { it.label })
    }

    @Test
    fun `拿不到闭锁信号时不硬造一行未知`() {
        assertTrue(
            vehicleStatusRows(
                locked = null,
                acOn = null,
                windowOpen = null,
                trunkOpen = null,
                sunshadeOpen = null,
            ).isEmpty(),
        )
    }

    @Test
    fun `五种状态齐全时顺序稳定`() {
        // 最多铺 4 行，顺序一变「+N」折掉的就是另一项，所以顺序本身要守住
        val rows = vehicleStatusRows(
            locked = true,
            acOn = true,
            windowOpen = true,
            trunkOpen = true,
            sunshadeOpen = true,
        )
        assertEquals(
            listOf("车辆已闭锁", "车窗已开启", "后备箱已开启", "遮阳帘已开启", "空调已开启"),
            rows.map { it.label },
        )
        assertFalse(rows.isEmpty())
    }

    @Test
    fun `每行都带图标 不能画出空行`() {
        val rows = vehicleStatusRows(
            locked = true,
            acOn = true,
            windowOpen = true,
            trunkOpen = true,
            sunshadeOpen = true,
        )
        assertTrue(rows.all { it.iconRes != 0 })
        // 四个只开启的项必须落在快捷控车那一套矢量图上（与圆钮同源）
        assertTrue(rows.any { it.iconRes == R.drawable.ic_qc_lock })
        assertTrue(rows.any { it.iconRes == R.drawable.ic_qc_window })
    }
}
