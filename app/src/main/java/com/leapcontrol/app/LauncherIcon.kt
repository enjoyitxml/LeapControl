package com.leapcontrol.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标两套之间的切换。
 *
 * 为什么是两套预制图标、而不是「随便选张图」：
 * adaptive icon 的图是**编译期资源**，运行时改不了。想让桌面图标变，
 * 唯一的标准做法是在 Manifest 里为同一个 [MainActivity] 声明多个
 * `activity-alias`，每个 alias 挂一套 icon，切换时用
 * [PackageManager.setComponentEnabledSetting] 启用其一、禁用另一个。
 *
 * 所以这里只有两个固定选项：
 *  - [STYLE_STEERING] 方向盘（默认）—— 规避与零跑官方 logo 的商标纠纷
 *  - [STYLE_OFFICIAL] 零跑官方 logo —— 收在「设置 → 高级设置」后面，默认不暴露
 *
 * Manifest 里两个 alias 的名字必须与 [ALIAS_STEERING] / [ALIAS_OFFICIAL] 一致。
 */
object LauncherIcon {

    /** 默认样式：方向盘。 */
    const val STYLE_STEERING = "steering"

    /** 备用样式：零跑官方 logo。 */
    const val STYLE_OFFICIAL = "official"

    /** 与 AndroidManifest.xml 里的 `activity-alias android:name` 对应。 */
    const val ALIAS_STEERING = ".LauncherSteering"
    const val ALIAS_OFFICIAL = ".LauncherOfficial"

    fun allStyles(): List<String> = listOf(STYLE_STEERING, STYLE_OFFICIAL)

    /** 人类可读的名字，UI 与提示文案共用。 */
    fun displayName(style: String): String = when (style) {
        STYLE_OFFICIAL -> "官方 Logo"
        else -> "方向盘"
    }

    fun desc(style: String): String = when (style) {
        STYLE_OFFICIAL -> "零跑官方标志，与官网一致"
        else -> "通用方向盘图形，不带任何品牌标识"
    }

    private fun component(context: Context, style: String): ComponentName {
        val alias = if (style == STYLE_OFFICIAL) ALIAS_OFFICIAL else ALIAS_STEERING
        return ComponentName(context.packageName, context.packageName + alias)
    }

    /**
     * 当前生效的样式。
     *
     * 判断依据只能是 PackageManager 里的组件状态 —— 清单里 [STYLE_OFFICIAL] 默认
     * `enabled=false`，未切换过时查询结果是 DEFAULT 而不是 DISABLED，所以要反过来判
     * 「官方那个是不是被显式启用了」，而不是判「方向盘是不是被禁用了」。
     */
    fun current(context: Context): String {
        val state = runCatching {
            context.packageManager.getComponentEnabledSetting(component(context, STYLE_OFFICIAL))
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) STYLE_OFFICIAL else STYLE_STEERING
    }

    /**
     * 切换图标。**先启用目标、再禁用另一个**，避免中途出现「一个 launcher 都没有」的空窗。
     *
     * 返回是否切换成功。注意：被禁用的正是当前进程的入口组件，部分 ROM 会顺手把进程收掉，
     * 所以调用方拿到 true 后应提示用户「重启 App 生效」，不要假设界面还能接着用。
     */
    fun apply(context: Context, style: String): Boolean {
        val target = if (style == STYLE_OFFICIAL) STYLE_OFFICIAL else STYLE_STEERING
        val other = if (target == STYLE_OFFICIAL) STYLE_STEERING else STYLE_OFFICIAL
        val pm = context.packageManager
        return runCatching {
            pm.setComponentEnabledSetting(
                component(context, target),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                component(context, other),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * 启动时校准：清单里的默认值不可靠（升级安装会保留旧的组件状态），
     * 以持久化记录为准重放一次，保证桌面图标与用户上次的选择一致。
     */
    fun ensureApplied(context: Context, style: String) {
        if (current(context) != style) runCatching { apply(context, style) }
    }
}
