package com.leapcontrol.app

import com.leapcontrol.app.ui.SceneKind
import com.leapcontrol.app.ui.SeasonThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题注册表的约束检查。
 *
 * 这些不是「实现细节」测试，而是产品约定：
 * 日间 10 套 / 夜间 10 套、每组 6 静态 + 4 动态、id 唯一、夜间主题必须转深色内容。
 * 以后加主题或改 id 时，这里会第一时间拦下来。
 */
class SeasonThemesTest {

    @Test
    fun `日间夜间各十套外加五套视频`() {
        assertEquals(10, SeasonThemes.DAY.size)
        assertEquals(10, SeasonThemes.NIGHT_ALL.size)
        assertEquals(5, SeasonThemes.VIDEO.size)
        assertEquals(25, SeasonThemes.ALL.size)
    }

    @Test
    fun `视频主题排在动态主题之后`() {
        // 页面按 DAY → NIGHT_ALL → VIDEO 渲染，视频主题必须落在整个列表末尾
        assertEquals(SeasonThemes.VIDEO, SeasonThemes.ALL.takeLast(SeasonThemes.VIDEO.size))
        assertTrue(SeasonThemes.VIDEO.all { it.video })
        assertTrue((SeasonThemes.DAY + SeasonThemes.NIGHT_ALL).none { it.video })
    }

    @Test
    fun `视频主题不画场景`() {
        SeasonThemes.VIDEO.forEach { theme ->
            assertEquals(theme.label, SceneKind.NONE, theme.scene)
            assertFalse(theme.label, theme.animated)
        }
    }

    @Test
    fun `日间是六静态四动态`() {
        assertEquals(6, SeasonThemes.DAY.count { !it.animated })
        assertEquals(4, SeasonThemes.DAY.count { it.animated })
    }

    @Test
    fun `夜间是六静态四动态`() {
        assertEquals(6, SeasonThemes.NIGHT_ALL.count { !it.animated })
        assertEquals(4, SeasonThemes.NIGHT_ALL.count { it.animated })
    }

    @Test
    fun `id 唯一且非空`() {
        val ids = SeasonThemes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `标签唯一`() {
        val labels = SeasonThemes.ALL.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `每套实时绘制的主题都有场景`() {
        assertTrue(SeasonThemes.ALL.filter { !it.video }.all { it.scene != SceneKind.NONE })
    }

    @Test
    fun `同一个场景不会被两套主题复用`() {
        val scenes = SeasonThemes.ALL.filter { !it.video }.map { it.scene }
        assertEquals(scenes.size, scenes.toSet().size)
    }

    @Test
    fun `动态标记与场景一致`() {
        SeasonThemes.ALL.forEach { theme ->
            assertEquals(theme.label, theme.scene.animated, theme.animated)
        }
    }

    @Test
    fun `夜间主题强制深色内容`() {
        SeasonThemes.NIGHT_ALL.forEach { theme ->
            assertTrue(theme.label, theme.night)
            assertTrue(theme.label, theme.forcesDarkContent)
        }
        SeasonThemes.DAY.forEach { theme ->
            assertFalse(theme.label, theme.night)
            assertFalse(theme.label, theme.forcesDarkContent)
        }
    }

    @Test
    fun `渐变至少三档且首尾不同`() {
        SeasonThemes.ALL.forEach { theme ->
            assertTrue(theme.label, theme.gradient.size >= 3)
            assertNotEquals(theme.label, theme.gradient.first(), theme.gradient.last())
        }
    }

    @Test
    fun `旧版本主题 id 仍能解析`() {
        // 旧版 spring / summer 本来就是视频四季，升级后应落回视频版（画面一致）
        assertEquals(SeasonThemes.VIDEO_SPRING.id, SeasonThemes.byId("spring").id)
        assertEquals(SeasonThemes.VIDEO_SUMMER.id, SeasonThemes.byId("summer").id)
        assertEquals(SeasonThemes.AUTUMN.id, SeasonThemes.byId("autumn").id)
        assertEquals(SeasonThemes.WINTER.id, SeasonThemes.byId("winter").id)
        assertEquals(SeasonThemes.STARRY.id, SeasonThemes.byId("stars").id)
    }

    @Test
    fun `未知或空 id 回落到默认主题`() {
        assertEquals(SeasonThemes.DAY.first().id, SeasonThemes.byId(null).id)
        assertEquals(SeasonThemes.DAY.first().id, SeasonThemes.byId("").id)
        assertEquals(SeasonThemes.DAY.first().id, SeasonThemes.byId("nope").id)
    }

    @Test
    fun `夜间判断只对夜间主题成立`() {
        assertTrue(SeasonThemes.isNightTheme(SeasonThemes.STARRY.id))
        assertTrue(SeasonThemes.isNightTheme(SeasonThemes.AURORA.id))
        assertFalse(SeasonThemes.isNightTheme(SeasonThemes.MIST_HILLS.id))
        assertFalse(SeasonThemes.isNightTheme(SeasonThemes.LIGHT_RAIN.id))
    }
}
