package com.leapcontrol.app

import com.leapcontrol.app.ui.buildParamJson
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 3D 车模包的下载编排与官方渲染参数。
 *
 * 这里钉的是两件曾经出过错的事：
 *  1. 包必须**先 H5 再资产**，否则中途被杀会留下「有资产没页面」的半成品目录；
 *  2. `year` 必须 ≥ 2026 —— 官方引擎的动画参数表以 2026 为基线，传 2025 会中断初始化。
 */
class CarModel3DTest {

    private lateinit var base: File

    @Before
    fun setUp() {
        base = Files.createTempDirectory("car3d-test").toFile()
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    // ---------------------------------------------------------------- 目录与就绪

    @Test
    fun `key 里的路径字符被清成下划线`() {
        assertEquals("a_b_c_d", CarModel3DManager.sanitizeKey("a/b:c.d"))
        assertEquals("H5KEY_2026", CarModel3DManager.sanitizeKey("H5KEY-2026"))
        assertEquals("", CarModel3DManager.sanitizeKey(""))
    }

    @Test
    fun `空 key 永远不算就绪`() {
        assertFalse(CarModel3DManager.isReady(base, null))
        assertFalse(CarModel3DManager.isReady(base, ""))
    }

    @Test
    fun `只有 index 没有就绪标记不算就绪`() {
        val dir = CarModel3DManager.modelDir(base, "h5")
        dir.mkdirs()
        File(dir, "index.html").writeText("<html></html>")
        assertFalse(CarModel3DManager.isReady(base, "h5"))
    }

    @Test
    fun `就绪标记在但 index 是空文件也不算就绪`() {
        val dir = CarModel3DManager.modelDir(base, "h5")
        dir.mkdirs()
        File(dir, "index.html").writeText("")
        File(dir, ".ready").createNewFile()
        assertFalse(CarModel3DManager.isReady(base, "h5"))
    }

    // ---------------------------------------------------------------- 下载编排

    @Test
    fun `先下 H5 再下资产，两个包落同一个目录`() {
        val calls = mutableListOf<String>()
        val ok = CarModel3DManager.syncPackage(base, "h5", "src") { key, dir ->
            calls += key
            dir.mkdirs()
            if (key == "h5") File(dir, "index.html").writeText("<html></html>")
            else File(dir, "car.fbx").writeText("asset")
            true
        }
        assertTrue(ok)
        // 顺序是有意的：资产先落盘会留下没有 index.html 的半成品目录
        assertEquals(listOf("h5", "src"), calls)
        assertTrue(File(CarModel3DManager.modelDir(base, "h5"), "car.fbx").isFile)
        assertTrue(CarModel3DManager.isReady(base, "h5", "src"))
    }

    @Test
    fun `已经就绪时一个包都不再下`() {
        CarModel3DManager.syncPackage(base, "h5", "src") { key, dir ->
            dir.mkdirs()
            if (key == "h5") File(dir, "index.html").writeText("<html></html>")
            true
        }
        val again = mutableListOf<String>()
        val ok = CarModel3DManager.syncPackage(base, "h5", "src") { key, _ ->
            again += key
            true
        }
        assertTrue(ok)
        assertTrue("已就绪不该重复下载：$again", again.isEmpty())
    }

    @Test
    fun `H5 包下下来但没有 index 直接判失败`() {
        val ok = CarModel3DManager.syncPackage(base, "h5", null) { _, dir ->
            dir.mkdirs()
            File(dir, "readme.txt").writeText("not a runtime")
            true
        }
        assertFalse(ok)
        assertFalse(CarModel3DManager.isReady(base, "h5"))
        // 没写就绪标记：下次进来还会重试，而不是把一个空壳目录当成好的
        assertFalse(File(CarModel3DManager.modelDir(base, "h5"), ".ready").exists())
    }

    @Test
    fun `H5 包下载失败时不碰资产包`() {
        val calls = mutableListOf<String>()
        val ok = CarModel3DManager.syncPackage(base, "h5", "src") { key, _ ->
            calls += key
            false
        }
        assertFalse(ok)
        assertEquals(listOf("h5"), calls)
    }

    @Test
    fun `资产包失败时整体算失败，下次只补资产`() {
        var h5Calls = 0
        var srcCalls = 0
        val first = CarModel3DManager.syncPackage(base, "h5", "src") { key, dir ->
            if (key == "h5") {
                h5Calls++
                dir.mkdirs()
                File(dir, "index.html").writeText("<html></html>")
                true
            } else {
                srcCalls++
                false
            }
        }
        assertFalse(first)
        assertEquals(1, h5Calls)
        assertEquals(1, srcCalls)

        // 第二次：H5 已就绪不再下，只补资产
        val second = CarModel3DManager.syncPackage(base, "h5", "src") { key, dir ->
            if (key == "h5") {
                h5Calls++
                true
            } else {
                srcCalls++
                File(dir, "car.fbx").writeText("asset")
                true
            }
        }
        assertTrue(second)
        assertEquals("H5 不该重下", 1, h5Calls)
        assertEquals(2, srcCalls)
    }

    @Test
    fun `没有 srcKey 的车型只要 H5 就够`() {
        val ok = CarModel3DManager.syncPackage(base, "h5", null) { _, dir ->
            dir.mkdirs()
            File(dir, "index.html").writeText("<html></html>")
            true
        }
        assertTrue(ok)
        assertTrue(CarModel3DManager.isReady(base, "h5", null))
    }

    // ---------------------------------------------------------------- 渲染参数

    @Test
    fun `年款低于 2026 会被抬到 2026`() {
        val p = buildParamJson(JSONObject().put("year", 2025), "C11", false)
        assertEquals(2026, p.getInt("year"))
        assertEquals(2027, buildParamJson(JSONObject().put("year", 2027), "C11", false).getInt("year"))
    }

    @Test
    fun `没有 modelParam 时用车辆档案的车型`() {
        assertEquals("C10", buildParamJson(null, "C10", false).getString("carType"))
        // 两个来源都空才退到默认车型，避免官方引擎拿不到 carType 直接不画
        assertEquals("C16", buildParamJson(null, "", false).getString("carType"))
    }

    @Test
    fun `车漆色号 车顶色 座椅 别名原样透传`() {
        val param = JSONObject()
            .put("carType", "C11")
            .put("carTypeCode", "C11_580")
            .put("colorCode", 7)
            .put("roofColor", 3)
            .put("seat", 1)
            .put("alias", "C11-580")
        val p = buildParamJson(param, "C11", false)
        assertEquals("C11", p.getString("carType"))
        assertEquals("C11_580", p.getString("carTypeCode"))
        assertEquals(7, p.getInt("colorCode"))
        assertEquals(3, p.getInt("roofColorCode"))
        assertEquals(1, p.getInt("seat"))
        assertEquals("C11-580", p.getString("alias"))
    }

    @Test
    fun `roofColorCode 优先于 roofColor`() {
        val param = JSONObject().put("roofColor", 2).put("roofColorCode", 9)
        assertEquals(9, buildParamJson(param, "C11", false).getInt("roofColorCode"))
    }

    @Test
    fun `机位与半径固定，不让官方引擎自己算`() {
        val p = buildParamJson(JSONObject().put("radius", 4.0).put("offset", 3.0), "C11", false)
        assertEquals(7.2, p.getDouble("radius"), 0.001)
        assertEquals(0.0, p.getDouble("offset"), 0.001)
        assertEquals("default", p.getString("theme"))
    }
}
