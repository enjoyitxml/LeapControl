package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 4×2 与 5×2 两个桌面入口的配置守门。
 *
 * 5×2 是「多一个 receiver + 多一份 provider-info + 多三份宽版布局」拼出来的，
 * 而这几处漏一个都**编译得过、跑起来也看不出异常**：
 * - 忘了注册 receiver → 「添加小部件」里根本没有这个尺寸；
 * - 忘了 `targetCellWidth="5"` → 桌面还是按 4 列摆，等于没做；
 * - 宽版布局少一个 id → 增量更新（partiallyUpdateAppWidget）按 id 找不到视图，
 *   整帧被丢掉，卡片停在旧车况上，既不报错也不刷；
 * - 忘了把新 provider 加进 `VehicleWidget.PROVIDERS` → 那张卡从头到尾画不出来。
 *
 * 全是配置问题，只能靠这类静态断言钉住。
 */
class WidgetSizesTest {

    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app").isDirectory }

    private fun read(path: String) = File(projectDir, path).readText()

    @Test
    fun `5×2 入口声明了五列两行`() {
        val xml = read("app/src/main/res/xml/widget_info_wide.xml")
        assertTrue("5×2 必须声明目标格宽 5，否则桌面当 4 列摆", xml.contains("""android:targetCellWidth="5""""))
        assertTrue("高度仍是 2 行", xml.contains("""android:targetCellHeight="2""""))
        // 旧桌面（API < 31）不认 targetCell*，只能靠 minWidth 兜底：70×5-30 = 320dp
        assertTrue("minWidth 要用官方公式 70×5-30=320dp", xml.contains("""android:minWidth="320dp""""))
        assertTrue("minHeight 要用 70×2-30=110dp", xml.contains("""android:minHeight="110dp""""))
        assertTrue("预览图必须是宽版布局，否则列表里看着还是 4×2", xml.contains("@layout/widget_a_wide"))
    }

    @Test
    fun `两个尺寸都在清单里注册，各用各的 provider-info`() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertTrue("4×2 的 receiver 不见了", manifest.contains("""android:name=".widget.VehicleWidgetProvider""""))
        assertTrue("5×2 的 receiver 没注册", manifest.contains("""android:name=".widget.VehicleWidgetProviderWide""""))
        assertTrue("@xml/widget_info 没挂上", manifest.contains("""android:resource="@xml/widget_info""""))
        assertTrue("widget_info_wide.xml 没挂上", manifest.contains("""android:resource="@xml/widget_info_wide""""))
        // 两个条目同名的话，用户在小部件列表里分不清哪个是 5×2
        assertTrue(
            "5×2 需要一个与 4×2 区分得开的名字",
            manifest.contains("""android:label="@string/widget_label_wide""""),
        )
    }

    @Test
    fun `render 会遍历两个 component`() {
        // 只注册 receiver、不把它放进 PROVIDERS，那张卡就永远画不出来（不报错、就是空白）
        val source = read("app/src/main/java/com/leapcontrol/app/widget/VehicleWidget.kt")
        assertTrue("PROVIDERS 里没有 5×2 的 component", source.contains("VehicleWidgetProviderWide::class.java"))
        listOf("widget_a_wide", "widget_d_wide", "widget_t_wide").forEach { layout ->
            assertTrue("layoutFor 没给 $layout 分配任何版式", source.contains("R.layout.$layout"))
        }
    }

    @Test
    fun `宽版 provider 只是继承，没有改行为`() {
        val source = read("app/src/main/java/com/leapcontrol/app/widget/VehicleWidgetProviderWide.kt")
        assertTrue(
            "5×2 必须是 VehicleWidgetProvider 的子类，否则控车 / 二次确认要再实现一遍",
            source.contains("class VehicleWidgetProviderWide : VehicleWidgetProvider()"),
        )
        assertTrue("别在宽版里重写方法，行为会分叉", !source.contains("override fun"))
    }

    @Test
    fun `宽版布局与 4×2 的视图 id 完全一致`() {
        // 增量更新靠 id 定位，两份布局的 id 集合必须相同 —— 少一个就有一帧刷不动，
        // 多一个则说明有人在宽版里偷偷加了结构（那样两份布局就得各自维护两遍逻辑）。
        listOf("a" to "素纸", "d" to "沉浸", "t" to "全透明").forEach { (key, name) ->
            val base = idsOf("app/src/main/res/layout/widget_$key.xml")
            val wide = idsOf("app/src/main/res/layout/widget_${key}_wide.xml")
            assertEquals("$name 宽版的 id 与 4×2 不一致（多了或少了）", base, wide)
            assertTrue("$name 宽版布局是空的？", wide.size > 20)
        }
    }

    @Test
    fun `宽版真的把多出来的宽度分给了车模与续航条`() {
        // 如果只是把 4×2 那份拉宽，这里会全部相等 —— 那就等于没做宽版
        listOf("a", "d", "t").forEach { key ->
            val base = read("app/src/main/res/layout/widget_$key.xml")
            val wide = read("app/src/main/res/layout/widget_${key}_wide.xml")
            assertTrue("$key 宽版的车模区权重没有加大", wide.contains("""android:layout_weight="1.2""""))
            assertTrue("$key 宽版的里程槽没有加宽", wide.contains("""android:layout_width="52dp""""))
            assertTrue("$key 宽版的进度条没有加长", wide.contains("""android:layout_width="50dp""""))
            assertTrue("$key 宽版的按键行没有放高", wide.contains("""android:layout_height="42dp""""))
            assertTrue("$key 的 4×2 版本不该被改动", !base.contains("""android:layout_weight="1.2""""))
        }
    }

    private fun idsOf(path: String): Set<String> =
        Regex("""@\+id/(\w+)""").findAll(read(path)).map { it.groupValues[1] }.toSet()
}
