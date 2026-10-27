package com.leapcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 全局配色表：浅色 / 深色两套，由「外观模式」（跟随系统 / 始终浅色 / 始终深色）决定。
 * 通过 LocalAppColors 下发，首页、我的页、设置页、底部 Dock 统一取色。
 */
data class AppColors(
    val isDark: Boolean,
    /** 页面背景渐变（首页若选中四季主题，则用季节渐变替代） */
    val bgTop: Color,
    val bgBottom: Color,
    /** 首页沉浸式卡片 / 弹窗底色（半透明磨砂，让季节渐变透出） */
    val cardBg: Color,
    /**
     * 二级页（设置 / 个性化 / 关于 / 车辆 / 舒适等）的实心面板底色：
     * 不透光、与背景对比清晰，避免半透明白压在浅色渐变上发灰、出现"脏块"。
     */
    val panelBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    /** 圆形快捷按钮底色 */
    val iconBg: Color,
    /**
     * 首页快捷控车圆钮底色：对齐官方——纯白实心圆 + 轻微下投影、无描边。
     * 与 [iconBg] 分开，避免影响其它共用 iconBg 的小圆底。
     */
    val quickButtonBg: Color,
    /** 快捷控车圆钮按下时的底色 */
    val quickButtonPressed: Color,
    /** 开启态圆钮底色 */
    val iconBgActive: Color,
    /** 进度条 / 滑槽底色（弱，用于开关轨道等） */
    val barTrack: Color,
    /** 进度条滑槽底色（强，用于电量 / 油量百分比条：降低透明度、提高可读性） */
    val barTrackStrong: Color,
    /** 浅色小圆底（头像、图标底） */
    val chipBg: Color,
    /** 选中行底色 */
    val chipBgActive: Color,
    /** 描边 */
    val border: Color,
    /** 底部 Dock */
    val dockBg: Color,
    val dockUnselected: Color,
    val dockIndicator: Color,
)

private fun lightColors() = AppColors(
    isDark = false,
    bgTop = Color(0xFFF7F9F8),
    bgBottom = Color(0xFFF2F5F3),
    // 磨砂半透明：让四季背景透出来，同时保证文字对比度（仅用于首页沉浸式卡片）
    cardBg = Color(0xD9FFFFFF),
    // 二级页实心面板：纯白，与浅色渐变形成清晰对比、不发灰
    panelBg = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1B1D22),
    textSecondary = Color(0xFF7A7E87),
    accent = Color(0xFF2FA85C),
    iconBg = Color(0x9CFFFFFF),
    quickButtonBg = Color(0xFFFFFFFF),
    quickButtonPressed = Color(0xFFECEFF0),
    iconBgActive = Color(0x592FA85C),
    barTrack = Color(0x33000000),
    barTrackStrong = Color(0x8C000000),
    chipBg = Color(0x99FFFFFF),
    chipBgActive = Color(0x402FA85C),
    border = Color(0x1C000000),
    dockBg = Color(0xCCFFFFFF),
    dockUnselected = Color(0xFFB7BCC2),
    dockIndicator = Color(0x592FA85C),
)

private fun darkColors() = AppColors(
    isDark = true,
    bgTop = Color(0xFF0C0D10),
    bgBottom = Color(0xFF181A1F),
    cardBg = Color(0xCC14161B),
    // 二级页实心面板：中性深灰，压在深绿 / 深蓝等季节背景上清晰可辨
    panelBg = Color(0xFF1B1E24),
    textPrimary = Color(0xFFF2F2F4),
    textSecondary = Color(0xFF8E9099),
    accent = Color(0xFF30D158),
    iconBg = Color(0x1FFFFFFF),
    // 深色下用略高于页面的实心圆，靠投影与底色差异浮起来
    quickButtonBg = Color(0xFF25282F),
    quickButtonPressed = Color(0xFF32363E),
    iconBgActive = Color(0x4D30D158),
    barTrack = Color(0x33FFFFFF),
    barTrackStrong = Color(0x8CFFFFFF),
    chipBg = Color(0x14FFFFFF),
    chipBgActive = Color(0x4030D158),
    border = Color(0x2EFFFFFF),
    dockBg = Color(0xCC14161B),
    dockUnselected = Color(0xFF6A6E76),
    dockIndicator = Color(0x4D30D158),
)

fun appColors(dark: Boolean): AppColors = if (dark) darkColors() else lightColors()

val LocalAppColors = staticCompositionLocalOf { lightColors() }

@Composable
fun ProvideAppColors(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides appColors(dark), content = content)
}
