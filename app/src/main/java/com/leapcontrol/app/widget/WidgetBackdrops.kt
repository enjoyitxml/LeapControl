package com.leapcontrol.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.DrawableRes
import com.leapcontrol.app.R

/**
 * 桌面小组件的风景背景。
 *
 * ## 为什么分成日间 / 夜间两套
 * 卡面上的文字是深色的（日间）——背景必须是浅色，否则字看不清；
 * 系统切到深色模式后卡面整体换成深色、文字变浅，这时背景必须是夜景。
 * 所以两套图各自成套、各自记住「上一次选的是哪张」，跟着系统深浅自动换，
 * 而不是让同一张图在两种模式下硬撑（白天那张调暗会发灰，夜里那张调亮会发白）。
 *
 * ## 选中状态存哪
 * 单独一个 SharedPreferences（`widget_backdrop`），不复用 `widget_action_state`
 * ——那个文件里放着「后备箱二次点击确认」的时间戳，读写它的是广播接收器，
 * 混在一起以后排查误触问题会很乱。
 */
data class WidgetBackdrop(
    val id: String,
    val label: String,
    @DrawableRes val res: Int,
)

object WidgetBackdrops {

    /** 不选背景 = 原来的纯色玻璃卡。 */
    const val NONE = "none"

    private const val PREFS = "widget_backdrop"
    private const val KEY_DAY = "day"
    private const val KEY_NIGHT = "night"

    val DAY: List<WidgetBackdrop> = listOf(
        WidgetBackdrop("day_01", "晨雾山谷", R.drawable.widget_bg_day_01),
        WidgetBackdrop("day_02", "海岸日出", R.drawable.widget_bg_day_02),
        WidgetBackdrop("day_03", "雪原", R.drawable.widget_bg_day_03),
        WidgetBackdrop("day_04", "云海", R.drawable.widget_bg_day_04),
        WidgetBackdrop("day_05", "草原云影", R.drawable.widget_bg_day_05),
        WidgetBackdrop("day_06", "秋日湖面", R.drawable.widget_bg_day_06),
    )

    val NIGHT: List<WidgetBackdrop> = listOf(
        WidgetBackdrop("night_01", "星河雪山", R.drawable.widget_bg_night_01),
        WidgetBackdrop("night_02", "城市夜色", R.drawable.widget_bg_night_02),
        WidgetBackdrop("night_03", "月光海岸", R.drawable.widget_bg_night_03),
        WidgetBackdrop("night_04", "极光雪原", R.drawable.widget_bg_night_04),
    )

    /** 当前模式该看哪一套。 */
    fun pool(night: Boolean): List<WidgetBackdrop> = if (night) NIGHT else DAY

    /** 系统是不是深色模式（小组件自己判断，不依赖 App 的外观设置）。 */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 按 id 找图；认不出（升级后旧 id、被删掉的图）当作「无背景」。 */
    fun byId(id: String?, night: Boolean): WidgetBackdrop? =
        pool(night).firstOrNull { it.id == id }

    /**
     * 点车模时的下一张：无背景 → 第 1 张 → … → 最后一张 → 无背景。
     *
     * 循环里保留「无背景」这一档，用户在桌面上也能一键关掉，
     * 不用为了去掉背景专门进设置页。
     */
    fun next(currentId: String?, night: Boolean): String {
        val list = pool(night)
        val index = list.indexOfFirst { it.id == currentId }
        return if (index < 0) list.first().id
        else if (index == list.lastIndex) NONE
        else list[index + 1].id
    }

    /** 当前选中（默认无背景）。 */
    fun selectedId(context: Context, night: Boolean): String =
        prefs(context).getString(if (night) KEY_NIGHT else KEY_DAY, NONE) ?: NONE

    fun select(context: Context, night: Boolean, id: String) {
        val safe = if (id == NONE) NONE else (byId(id, night)?.id ?: NONE)
        prefs(context).edit().putString(if (night) KEY_NIGHT else KEY_DAY, safe).apply()
    }

    /** 名称给桌面 toast 用（「已换成 晨雾山谷」）。 */
    fun label(context: Context, night: Boolean, id: String): String? =
        byId(id, night)?.label

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
