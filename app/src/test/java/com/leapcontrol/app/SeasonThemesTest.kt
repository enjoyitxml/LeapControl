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
 * 日间 20 套 / 夜间 20 套、每组 10 静态 + 10 动态、id 唯一、夜间主题必须转深色内容。
 * 以后加主题或改 id 时，这里会第一时间拦下来。
 */
class SeasonThemesTest {

    @Test
    fun `日间夜间各二十套`() {
        assertEquals(20, SeasonThemes.DAY.size)
        assertEquals(20, SeasonThemes.NIGHT_ALL.size)
        assertEquals(40, SeasonThemes.ALL.size)
    }

    @Test
    fun `日间是十静态十动态`() {
        assertEquals(10, SeasonThemes.DAY.count { !it.animated })
        assertEquals(10, SeasonThemes.DAY.count { it.animated })
    }

    @Test
    fun `夜间是十静态十动态`() {
        assertEquals(10, SeasonThemes.NIGHT_ALL.count { !it.animated })
        assertEquals(10, SeasonThemes.NIGHT_ALL.count { it.animated })
    }

    @Test
    fun `日间夜里都不含视频主题`() {
        // 视频主题已整体移除，任何一套都不该再走视频播放
        assertTrue(SeasonThemes.ALL.none { it.scene == SceneKind.NONE })
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
    fun `同一个场景不会被两套主题复用`() {
        val scenes = SeasonThemes.ALL.map { it.scene }
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
        // 视频主题已移除：旧的四季 / 星空 id 折回画风最接近的实时绘制主题
        assertEquals(SeasonThemes.SAKURA.id, SeasonThemes.byId("spring").id)
        assertEquals(SeasonThemes.CLEAR_SKY.id, SeasonThemes.byId("summer").id)
        assertEquals(SeasonThemes.SAKURA.id, SeasonThemes.byId("video_spring").id)
        assertEquals(SeasonThemes.CLEAR_SKY.id, SeasonThemes.byId("video_summer").id)
        assertEquals(SeasonThemes.AUTUMN.id, SeasonThemes.byId("video_autumn").id)
        assertEquals(SeasonThemes.WINTER.id, SeasonThemes.byId("video_winter").id)
        assertEquals(SeasonThemes.STARRY.id, SeasonThemes.byId("video_stars").id)
        // 这些 id 一直没变，天然兼容
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
        assertTrue(SeasonThemes.isNightTheme(SeasonThemes.NIGHT_WAVES.id))
        assertFalse(SeasonThemes.isNightTheme(SeasonThemes.MIST_HILLS.id))
        assertFalse(SeasonThemes.isNightTheme(SeasonThemes.LIGHT_RAIN.id))
        assertFalse(SeasonThemes.isNightTheme(SeasonThemes.SUNRISE.id))
    }
}
