package com.leapcontrol.app.ui

import android.content.Context

/**
 * 首页主区的两套布局（个性化 → 首页布局）。
 *
 * 2026-11-05 先做过「分栏」「状态下移」两版，用户看过实测后判定分栏把车模压得太小、
 * 状态下移又和右上角重复，**两版都撤掉**，只留这里两套：
 *
 * - [CLASSIC] 续航与状态并排，状态是「双行文本流」：
 *   一行主状态 + 一行把闭锁 / 空调 / 车窗串起来的小字。
 * - [SEPARATE]（默认）把行驶状态和车辆状态**拆开**：行驶状态（已驻车 / 行驶中 / 充电中）
 *   单独一条放在车模正下方居中，右上角只留闭锁与已开启提醒，
 *   并且恢复成带图标逐行的老样式（`车辆已闭锁` / `车窗已开启` 各一行，不合并）。
 *
 * 两套共用同一份车况、同一个 [DrivingStateRules.badge] 判据，只换摆放方式，
 * 不会出现「某个状态在 A 有、B 没有」。
 */
enum class HeroLayout(val id: String, val label: String, val desc: String) {
    CLASSIC("classic", "经典", "续航在左 · 状态在右上 · 车模居中"),
    SEPARATE("separate", "状态分置", "行驶状态在车模下 · 上锁车窗在右上"),
    ;

    companion object {
        /**
         * 默认「状态分置」：行驶状态单独一条放在车模下方，右上角只留闭锁与已开启提醒。
         *
         * 2026-11-05 用户看过后判定这套更清楚（状态不再挤在右上角一坨），
         * 设成默认。选过「经典」的人仍按自己存的走 —— [HeroLayoutPrefs] 里没有值才落到这里。
         */
        val DEFAULT: HeroLayout = SEPARATE

        /**
         * 存坏了 / 旧版本没有这个键，都退回默认，绝不抛异常。
         *
         * `below` 是已删掉的「状态下移」，语义上最接近 [SEPARATE]（都是把状态往车模那边搬），
         * 所以老值映射过去而不是静默退回经典 —— 选过的人升级后不该发现自己的选择被抹了。
         */
        fun fromId(id: String?): HeroLayout = when (id) {
            "below" -> SEPARATE
            else -> entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}

/**
 * 布局选择的落盘。
 *
 * 和车辆命名共用 `leapcontrol_naming`：这是「这台手机的偏好」，换车、登出都不该丢；
 * 也省得再开一个 prefs 文件给备份 / 清理多一个漏点。
 */
object HeroLayoutPrefs {
    private const val PREFS = "leapcontrol_naming"
    private const val KEY = "hero_layout"

    fun load(context: Context): HeroLayout = HeroLayout.fromId(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
    )

    fun save(context: Context, layout: HeroLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, layout.id)
            .apply()
    }
}
