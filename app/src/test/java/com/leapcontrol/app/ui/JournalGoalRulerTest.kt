package com.leapcontrol.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 刻度尺分格规则。
 *
 * 「一格 = 一个季度、四格 = 一年」是这页的唯一读数语义，改它等于改口径，
 * 所以钉住：3 年目标必须是 12 格，而不是永远 40 格。
 */
class JournalGoalRulerTest {

    @Test
    fun `一年四季，格数按年数走`() {
        assertEquals(4, goalRulerDivisions(1))
        assertEquals(12, goalRulerDivisions(3))
        assertEquals(40, goalRulerDivisions(10))
        assertEquals(80, goalRulerDivisions(20))
    }

    @Test
    fun `超过二十年退化成四十格`() {
        // 21 年 = 84 格，一根尺子上 84 根刻度在手机上会糊成灰带
        assertEquals(40, goalRulerDivisions(21))
        assertEquals(40, goalRulerDivisions(40))
    }

    @Test
    fun `年数异常时不留零格`() {
        // 0 格会让除零，负数更糟；表单只允许 1~40，但旧数据可能带 0 进来
        assertEquals(4, goalRulerDivisions(0))
        assertEquals(4, goalRulerDivisions(-8))
    }
}
