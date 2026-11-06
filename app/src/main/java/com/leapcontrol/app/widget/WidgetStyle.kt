package com.leapcontrol.app.widget

import android.content.Context
import android.content.res.Configuration

/**
 * 桌面小组件的外观偏好：**版式** + **背景** + **透明主题的字色 / 控件色**。
 *
 * ## 三种版式（用户在「个性化 → 桌面小组件」里选）
 * - [STYLE_A] 素纸：浅色卡 + 发丝分隔线，最像系统自带小组件；
 * - [STYLE_D] 沉浸：图上铺字，下半部分压暗，按键是半透明白玻璃；
 * - [STYLE_T] 全透明：没有卡片底，字和按键直接浮在壁纸上，字色与控件色可自己调。
 *
 * 三个布局是三份独立的 RemoteViews XML，不是同一份布局加开关：
 * 标准小组件只能组合基础控件，靠 visibility 切换做不出几种完全不同的结构。
 *
 * 早先还有过一版「色块」（STYLE_F）：车模坐在主题绿底板上、五格带文字。
 * 用户否决后已删除，布局与 drawable 一并移除；老用户 prefs 里残留的 "f"
 * 由 [style] 兜底折回 [STYLE_A]，不需要单独迁移。
 *
 * ## 背景（只对 [STYLE_D] 有意义）
 * 没选自定义图时用 `widget_d_fallback` 那段黄昏渐变；选了就换成用户的照片，
 * 见 [WidgetWallpaper]。照片走 URI 交给桌面进程解码，不受 Binder 1MB 限制。
 *
 * ## 透明主题的颜色
 * 壁纸深浅不可控，所以字色与控件色都交给用户。两色各自独立，默认都是白色。
 * 派生规则只有一条：**按颜色本身的亮度选亮版 / 暗版素材**（[isLight]）——
 * 浅色字配深玻璃底、深色字配浅玻璃底，否则浅壁纸上白字、深壁纸上黑字都会糊掉。
 * 进度条与按键底是 drawable 资源，运行时换不了颜色，所以各准备两份由这里挑。
 */
object WidgetStyle {

    const val STYLE_A = "a"
    const val STYLE_D = "d"
    const val STYLE_T = "t"

    val ALL = listOf(STYLE_A, STYLE_D, STYLE_T)

    const val WALLPAPER_DEFAULT = "default"
    const val WALLPAPER_CUSTOM = "custom"

    /**
     * 透明主题的默认字色：白色 + 文字阴影，深壁纸上是最好读的一套。
     *
     * 默认控件色**不能也是白的** —— 控件色负责「按键生效时的亮色」，
     * 跟字色一样的话，解锁 / 空调开着时按键根本看不出变化。所以给零控绿，
     * 白字 + 绿高亮跟沉浸样式的层次是一致的；浅壁纸的用户自己换成深色即可。
     */
    const val DEFAULT_INK = 0xFFFFFFFF.toInt()
    const val DEFAULT_CTRL = 0xFF3DD68C.toInt()

    private const val PREFS = "widget_style"
    private const val KEY_STYLE = "style"
    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_REVISION = "revision"
    private const val KEY_INK = "t_ink"
    private const val KEY_CTRL = "t_ctrl"

    /** 系统是不是深色模式。小组件自己判断，不跟 App 里的外观设置走。 */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun style(context: Context): String {
        val value = prefs(context).getString(KEY_STYLE, STYLE_A)
        return if (value in ALL) value!! else STYLE_A
    }

    fun setStyle(context: Context, value: String) {
        val safe = if (value in ALL) value else STYLE_A
        prefs(context).edit().putString(KEY_STYLE, safe).apply()
    }

    fun displayName(style: String): String = when (style) {
        STYLE_D -> "沉浸"
        STYLE_T -> "全透明"
        else -> "素纸"
    }

    // ---- 透明主题：字色与控件色 ----

    /** 文字（车名 / 续航数字 / 百分比 / 状态行）的颜色。 */
    fun ink(context: Context): Int =
        prefs(context).getInt(KEY_INK, DEFAULT_INK).let { if (it == 0) DEFAULT_INK else it }

    fun setInk(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_INK, color or 0xFF000000.toInt()).apply()
    }

    /** 控件（五个按键图标 / 刷新键 / 车标 / 分隔线）的颜色。 */
    fun ctrl(context: Context): Int =
        prefs(context).getInt(KEY_CTRL, DEFAULT_CTRL).let { if (it == 0) DEFAULT_CTRL else it }

    fun setCtrl(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_CTRL, color or 0xFF000000.toInt()).apply()
    }

    /** 恢复透明主题的两个默认色。 */
    fun resetColors(context: Context) {
        prefs(context).edit().remove(KEY_INK).remove(KEY_CTRL).apply()
    }

    /**
     * 这个颜色算不算「浅」。
     *
     * 只用来挑素材：浅色字要压在深玻璃底上、深色字要压在浅玻璃底上；
     * 进度条同理（白条 / 深条）。
     *
     * 用 WCAG 的相对亮度自己算，不用 `ColorUtils.calculateLuminance()`：
     * 那个方法内部走 `android.graphics.Color` 的静态方法，在纯 JVM 单测里拿不到实现，
     * 阈值一旦写错只会在真机上表现为「浅色壁纸上的白字看不见」，很难复现。
     * 阈值取 0.38：纯白 1.0、亮绿 #3DD68C 约 0.51、亮蓝 #6FBEFF 约 0.48（都算浅）、
     * 深灰 #12141A 约 0.01、灰 #5A626E 约 0.12。（蓝的亮度天然偏低，阈值再高一档
     * 就会把「亮蓝」判成暗色（#4FA8FF 实测只有 0.37），于是配出深色进度条压在深壁纸上 —— 那才是真的看不见。）
     */
    fun isLight(color: Int): Boolean {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.38
    }

    fun wallpaper(context: Context): String {
        val value = prefs(context).getString(KEY_WALLPAPER, WALLPAPER_DEFAULT)
        return if (value == WALLPAPER_CUSTOM) WALLPAPER_CUSTOM else WALLPAPER_DEFAULT
    }

    fun setWallpaper(context: Context, value: String) {
        val safe = if (value == WALLPAPER_CUSTOM) WALLPAPER_CUSTOM else WALLPAPER_DEFAULT
        prefs(context).edit().putString(KEY_WALLPAPER, safe).apply()
    }

    /**
     * 背景图版本号。
     *
     * 每存一张图就 +1，文件名与 URI 都带上它 —— 桌面进程里的 ImageView 对同一个 URI
     * 有缓存，URI 不变的话换了图也不会重新解码，卡片会一直停在旧图上。
     */
    fun revision(context: Context): Int = prefs(context).getInt(KEY_REVISION, 0)

    internal fun bumpRevision(context: Context): Int {
        val next = revision(context) + 1
        prefs(context).edit().putInt(KEY_REVISION, next).apply()
        return next
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
