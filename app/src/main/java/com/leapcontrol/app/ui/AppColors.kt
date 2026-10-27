package com.leapcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
    /** 首页沉浸式卡片底色（半透明磨砂，让季节渐变透出）。**不要用于弹窗** */
    val cardBg: Color,
    /**
     * 弹窗卡片底色：**完全不透明**。
     *
     * 弹窗浮在首页车模、四季渐变、视频背景之上，如果沿用半透明的 [cardBg]，
     * 底下的深色画面会透上来，白底变灰、深底发花，标题与正文糊成一片（实测可读性很差）。
     * 弹窗是模态的、盖住整屏，本来就不需要透光，统一用实心色。
     */
    val dialogBg: Color,
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

/**
 * 把主题色按比例混进卡片底色。
 * 卡片不是纯白/纯深灰，而是「白里透一点季节色」，同一套控件在四季主题下颜色自然不同。
 * 描边（[AppColors.border]）刻意保持中性灰、不掺主题色：描边一旦带色，
 * 满屏卡片就像被一圈彩线框住，反而显脏。
 */
private fun mix(base: Color, other: Color, amount: Float): Color = Color(
    red = base.red + (other.red - base.red) * amount,
    green = base.green + (other.green - base.green) * amount,
    blue = base.blue + (other.blue - base.blue) * amount,
    alpha = 1f,
)

/** 品牌绿：没有主题色注入时的兜底。 */
private val DefaultAccent = Color(0xFF2FA85C)

private fun lightColors(accent: Color = DefaultAccent) = AppColors(
    isDark = false,
    bgTop = Color(0xFFF7F9F8),
    bgBottom = Color(0xFFF2F5F3),
    // 磨砂半透明：让四季背景透出来，同时保证文字对比度（仅用于首页沉浸式卡片）
    cardBg = Color(0xD9FFFFFF),
    // 弹窗实心：只掺一点点主题色，肉眼接近纯白，压在暗下去的遮罩上对比最强
    dialogBg = mix(Color.White, accent, 0.02f),
    // 二级页实心面板：纯白，与浅色渐变形成清晰对比、不发灰
    panelBg = mix(Color.White, accent, 0.05f),
    textPrimary = Color(0xFF1B1D22),
    textSecondary = Color(0xFF7A7E87),
    accent = accent,
    iconBg = Color(0x9CFFFFFF),
    quickButtonBg = Color(0xFFFFFFFF),
    quickButtonPressed = Color(0xFFECEFF0),
    iconBgActive = accent.copy(alpha = 0.35f),
    barTrack = Color(0x33000000),
    barTrackStrong = Color(0x8C000000),
    chipBg = Color(0x99FFFFFF),
    chipBgActive = accent.copy(alpha = 0.25f),
    border = Color(0x1C000000),
    dockBg = Color(0xCCFFFFFF),
    dockUnselected = Color(0xFFB7BCC2),
    dockIndicator = accent.copy(alpha = 0.35f),
)

private fun darkColors(accent: Color = DefaultAccent) = AppColors(
    isDark = true,
    bgTop = Color(0xFF0C0D10),
    bgBottom = Color(0xFF181A1F),
    cardBg = Color(0xCC14161B),
    // 弹窗实心：比 panelBg 再亮一档，在压暗的遮罩上能看出「浮起来」的层次
    dialogBg = mix(Color(0xFF21242B), accent, 0.08f),
    // 二级页实心面板：中性深灰，压在深绿 / 深蓝等季节背景上清晰可辨
    panelBg = mix(Color(0xFF1B1E24), accent, 0.10f),
    textPrimary = Color(0xFFF2F2F4),
    textSecondary = Color(0xFF8E9099),
    accent = accent,
    iconBg = Color(0x1FFFFFFF),
    // 深色下用略高于页面的实心圆，靠投影与底色差异浮起来
    quickButtonBg = Color(0xFF25282F),
    quickButtonPressed = Color(0xFF32363E),
    iconBgActive = accent.copy(alpha = 0.35f),
    barTrack = Color(0x33FFFFFF),
    barTrackStrong = Color(0x8CFFFFFF),
    chipBg = Color(0x14FFFFFF),
    chipBgActive = accent.copy(alpha = 0.25f),
    border = Color(0x2EFFFFFF),
    dockBg = Color(0xCC14161B),
    dockUnselected = Color(0xFF6A6E76),
    dockIndicator = accent.copy(alpha = 0.4f),
)

fun appColors(dark: Boolean, accent: Color? = null): AppColors =
    if (dark) darkColors(accent ?: DefaultAccent) else lightColors(accent ?: DefaultAccent)

val LocalAppColors = staticCompositionLocalOf { lightColors() }

/**
 * 注入配色。
 *
 * [accent] 传当前季节主题的主题色，卡片底色、描边、强调元素会随之变化；
 * 不传就退回品牌绿（启动早期、未决定主题时用）。
 */
@Composable
fun ProvideAppColors(dark: Boolean, accent: Color? = null, content: @Composable () -> Unit) {
    // 配色表是 data class，值不变时读者不会重组；但这里每次重组都重新计算一遍
    // mix() 加两次对象构造是白费，用 remember 钉住。
    val colors = remember(dark, accent) { appColors(dark, accent) }
    CompositionLocalProvider(LocalAppColors provides colors, content = content)
}

/**
 * 模态弹窗的遮罩黑度。
 *
 * 以前是 0.45，背景压不下去：首页车模、四季渐变、视频背景的亮度跟弹窗正文差不多，
 * 眼睛会往背景上飘。0.56 把背景明确压成「远景」，弹窗卡片（不透明的 [AppColors.dialogBg]）
 * 才是唯一的视觉焦点，阅读顺序不再打架。
 */
const val DIALOG_SCRIM = 0.56f
