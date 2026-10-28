package com.leapcontrol.app.widget

import android.content.Context
import android.content.res.Configuration

/**
 * 桌面小组件的外观配置：背景 + 三组自定义颜色。
 *
 * ## 背景只有两种
 * - [WALLPAPER_DEFAULT]：内置的简约卡（日间浅色玻璃 / 夜间深色），跟着系统深浅自动换；
 * - [WALLPAPER_CUSTOM]：用户在个性化里选的自己的一张图，裁剪后存在私有目录，见 [WidgetWallpaper]。
 *
 * 之前那套 10 张内置风景图已经按用户要求整个删掉了 —— 内置图库属于「我替你挑好了」的思路，
 * 而用户要的是「默认干净、想要什么自己传」。所以默认只有两种纯色卡，其余交给自定义。
 *
 * ## 三组颜色
 * 按钮图标、文字、零跑车标各自可以单独指定颜色（[AUTO] = 跟随系统深浅的默认色）。
 * 一旦用了自定义壁纸，默认的深色字几乎必然看不清，这时候就得让用户自己配色 ——
 * 这也是这三组颜色存在的理由，不是为了好玩。
 */
object WidgetStyle {

    const val WALLPAPER_DEFAULT = "default"
    const val WALLPAPER_CUSTOM = "custom"

    /** 颜色槽位：底部按钮图标 / 卡面文字 / 左上角零跑车标。 */
    const val SLOT_BUTTON = "button"
    const val SLOT_TEXT = "text"
    const val SLOT_LOGO = "logo"

    /** 「不自定义」——用系统深浅对应的默认色。0 不会与任何可选颜色冲突（预设全是不透明色）。 */
    const val AUTO = 0

    private const val PREFS = "widget_style"
    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_REVISION = "revision"

    /** 系统是不是深色模式（小组件自己判断，不依赖 App 里的外观设置）。 */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun wallpaper(context: Context): String {
        val value = prefs(context).getString(KEY_WALLPAPER, WALLPAPER_DEFAULT)
        return if (value == WALLPAPER_CUSTOM) WALLPAPER_CUSTOM else WALLPAPER_DEFAULT
    }

    fun setWallpaper(context: Context, value: String) {
        val safe = if (value == WALLPAPER_CUSTOM) WALLPAPER_CUSTOM else WALLPAPER_DEFAULT
        prefs(context).edit().putString(KEY_WALLPAPER, safe).apply()
    }

    /** 取某个槽位的自定义色；[AUTO] 表示没设过。 */
    fun tone(context: Context, slot: String): Int = prefs(context).getInt(keyOf(slot), AUTO)

    /** [argb] 传 null 或 [AUTO] 就是恢复「跟随深浅」。 */
    fun setTone(context: Context, slot: String, argb: Int?) {
        val editor = prefs(context).edit()
        if (argb == null || argb == AUTO) editor.remove(keyOf(slot)) else editor.putInt(keyOf(slot), argb)
        editor.apply()
    }

    /** 有没有任何一项自定义（用于设置页显示「已自定义」）。 */
    fun hasAnyTone(context: Context): Boolean =
        prefs(context).contains(keyOf(SLOT_BUTTON)) ||
            prefs(context).contains(keyOf(SLOT_TEXT)) ||
            prefs(context).contains(keyOf(SLOT_LOGO))

    fun resetTones(context: Context) {
        prefs(context).edit()
            .remove(keyOf(SLOT_BUTTON))
            .remove(keyOf(SLOT_TEXT))
            .remove(keyOf(SLOT_LOGO))
            .apply()
    }

    /**
     * 壁纸版本号。
     *
     * 每换一张图就 +1，并让图片文件名与 URI 都带上它 —— 桌面进程里的 ImageView 对
     * 「同一个 URI」有缓存，URI 不变的话换了图也不会重新解码，卡片还停在旧图上。
     */
    fun revision(context: Context): Int = prefs(context).getInt(KEY_REVISION, 0)

    internal fun bumpRevision(context: Context): Int {
        val next = revision(context) + 1
        prefs(context).edit().putInt(KEY_REVISION, next).apply()
        return next
    }

    private fun keyOf(slot: String) = "tone_$slot"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
