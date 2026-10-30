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
 * 日间 20 套 / 夜间 20 套（各再加一个「我的壁纸」自定义格）、每组 10 静态 + 10 动态、
 * id 唯一、夜间主题必须转深色内容。以后加主题或改 id 时，这里会第一时间拦下来。
 *
 * 自定义壁纸（[com.leapcontrol.app.ui.SeasonTheme.custom]）是个特殊项：它没有场景、
 * 日间与夜间两套刻意同名「我的壁纸」（不在同一屏出现），所以凡是「唯一性 / 必须有场景」
 * 这类约束都要把它排除在外。
 */
class SeasonThemesTest {

    @Test
    fun `日间夜间各二十套加一个自定义格`() {
        // 20 套内置 + 末尾一个「我的壁纸」，共 21 格
        assertEquals(21, SeasonThemes.DAY.size)
        assertEquals(21, SeasonThemes.NIGHT_ALL.size)
        assertEquals(42, SeasonThemes.ALL.size)
        // 计数文案用 BUILTIN_COUNT，别把自定义算成一套主题
        assertEquals(40, SeasonThemes.BUILTIN_COUNT)
        // 自定义格必须排在最后一格，用户才不用在 20 套里找它
        assertTrue(SeasonThemes.DAY.last().custom)
        assertTrue(SeasonThemes.NIGHT_ALL.last().custom)
    }

    @Test
    fun `日间是十静态十动态`() {
        val builtin = SeasonThemes.DAY.filter { !it.custom }
        assertEquals(10, builtin.count { !it.animated })
        assertEquals(10, builtin.count { it.animated })
    }

    @Test
    fun `夜间是十静态十动态`() {
        val builtin = SeasonThemes.NIGHT_ALL.filter { !it.custom }
        assertEquals(10, builtin.count { !it.animated })
        assertEquals(10, builtin.count { it.animated })
    }

    @Test
    fun `内置主题都有场景`() {
        // 视频主题已整体移除，内置主题也不该再走视频播放；
        // 自定义壁纸是唯一没有场景的（背景由用户上传的位图铺满）
        assertTrue(SeasonThemes.ALL.filter { !it.custom }.none { it.scene == SceneKind.NONE })
        assertTrue(SeasonThemes.ALL.filter { it.custom }.all { it.scene == SceneKind.NONE })
    }

    @Test
    fun `id 唯一且非空`() {
        val ids = SeasonThemes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `内置主题标签唯一`() {
        // 自定义壁纸日间/夜间两套同名「我的壁纸」：它们分属两个 tab，不会同屏出现
        val labels = SeasonThemes.ALL.filter { !it.custom }.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(
            SeasonThemes.CUSTOM_DAY.label,
            SeasonThemes.CUSTOM_NIGHT.label,
        )
    }

    @Test
    fun `同一个场景不会被两套主题复用`() {
        // custom 的 scene 都是 NONE，两套会重复，排除掉再比
        val scenes = SeasonThemes.ALL.filter { !it.custom }.map { it.scene }
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
        // 自定义壁纸的渐变只是兜底色，同样要求能给出一组两端有区别的渐变
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
