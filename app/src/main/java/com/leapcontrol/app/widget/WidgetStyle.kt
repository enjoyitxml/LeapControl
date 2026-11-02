package com.leapcontrol.app.widget

import android.content.Context
import android.content.res.Configuration

/**
 * 桌面小组件的外观偏好：**版式** + **背景**。
 *
 * ## 两种版式（用户在「个性化 → 桌面小组件」里选）
 * - [STYLE_A] 素纸：浅色卡 + 发丝分隔线，最像系统自带小组件；
 * - [STYLE_D] 沉浸：图上铺字，下半部分压暗，按键是半透明白玻璃。
 *
 * 两个布局是两份独立的 RemoteViews XML，不是同一份布局加开关：
 * 标准小组件只能组合基础控件，靠 visibility 切换做不出两种完全不同的结构。
 *
 * 早先还有过一版「色块」（STYLE_F）：车模坐在主题绿底板上、五格带文字。
 * 用户否决后已删除，布局与 drawable 一并移除；老用户 prefs 里残留的 "f"
 * 由 [style] 兜底折回 [STYLE_A]，不需要单独迁移。
 *
 * ## 背景（只对 [STYLE_D] 有意义）
 * 没选自定义图时用 `widget_d_fallback` 那段黄昏渐变；选了就换成用户的照片，
 * 见 [WidgetWallpaper]。照片走 URI 交给桌面进程解码，不受 Binder 1MB 限制。
 */
object WidgetStyle {

    const val STYLE_A = "a"
    const val STYLE_D = "d"

    val ALL = listOf(STYLE_A, STYLE_D)

    const val WALLPAPER_DEFAULT = "default"
    const val WALLPAPER_CUSTOM = "custom"

    private const val PREFS = "widget_style"
    private const val KEY_STYLE = "style"
    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_REVISION = "revision"

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
        else -> "素纸"
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
