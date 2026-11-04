package com.leapcontrol.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「胎压 · 行程」这对并排半屏卡的尺寸换算。
 *
 * ## 为什么要跟着字号放大
 * 这两张卡的内容都是竖排文字的堆叠（胎压卡是四格「位置 / 数值 / kPa」，行程卡是
 * 「标题 / 合计 / 柱状图」），高度写死 172dp 时，系统字号一放大，胎压卡最下面那行
 * `kPa` 就会顶出卡片底边 —— `clip(RoundedCornerShape)` 一刀切下去，字只剩上半截。
 * 用户 2026-11-04 反馈的「字体大时胎压显示不全、下方字体被裁剪」就是它。
 *
 * 所以高度按字号等比放大；两张卡共用同一个换算，并排时下边缘才仍然齐平。
 *
 * ## 车模宽度为什么也要跟着变
 * 车模的前后轴要对准左右两排数字的中心线（见 [TireCarOutline] 的 `frontFraction`）。
 * 卡片变高、车模宽度完全不动的话，车模会被纵向拉成一根窄条；但如果车模完全等比放大，
 * 它每宽一分，左右两列文字就窄一分 —— 而字号放大后文字恰恰更需要宽度。
 * 所以只做部分补偿（见 [CAR_WIDTH_COMPENSATION]）：剩下的部分让它略微拉长，
 * 视觉上几乎看不出来，换来的是窄屏机型上文字不被横向挤掉。
 *
 * ## 上限 1.3 的来历
 * `ui/theme/Theme.kt` 已经把全应用字号钳在 1.3 倍（`MAX_FONT_SCALE`），所以这里的
 * 上限与它保持一致；再钳一次是防止将来放宽全局上限时，这张密排卡先被撑破。
 */

/** 系统字号 1.0 时两张卡的高度。 */
private const val BASE_HEIGHT_DP = 172f

/** 系统字号 1.0 时胎压卡中间车模的宽度。 */
private const val BASE_CAR_WIDTH_DP = 60f

/**
 * 车模宽度的补偿比例。
 *
 * 1.0 = 完全等比（车模形状不变，但左右文字列被压窄）；
 * 0.0 = 完全不动（文字列最宽，但车模被纵向拉长）。
 * 取 0.6 是折中：字号 1.3 倍时车模只拉长约 10%，肉眼基本看不出，
 * 而左右两列仍能保住约 38dp 的宽度（393dp 宽的屏幕上），放得下「左前 / 245 / kPa」。
 */
private const val CAR_WIDTH_COMPENSATION = 0.6f

/** 这两张卡能承受的最大字号倍数（与 `theme/Theme.kt` 的 MAX_FONT_SCALE 对齐）。 */
private const val MAX_SCALE = 1.3f

/**
 * 把系统字号倍数收敛到这对卡能承受的区间。
 *
 * - 小于 1.0 不缩小：卡片缩下去只会让版面变挤，用户要的是更大的字；
 * - 大于 1.3 不放大：全局字号本来就被钳在 1.3，超出部分不可能真的渲染出来；
 * - NaN 退回 1.0：`Float.coerceIn` 对 NaN 会原样返回，得单独挡一下，
 *   否则 NaN 一路传到 `Dp` 会让布局尺寸非法。
 */
internal fun pairCardScale(fontScale: Float): Float = when {
    fontScale.isNaN() -> 1f
    fontScale > MAX_SCALE -> MAX_SCALE
    fontScale < 1f -> 1f
    else -> fontScale
}

/** 两张卡共用的高度：按字号等比放大。 */
internal fun pairCardHeight(fontScale: Float): Dp =
    (BASE_HEIGHT_DP * pairCardScale(fontScale)).dp

/** 胎压卡车模宽度：按字号的六成补偿，兼顾「不被拉长」与「左右文字留够宽度」。 */
internal fun pairCardCarWidth(fontScale: Float): Dp {
    val scale = pairCardScale(fontScale)
    return (BASE_CAR_WIDTH_DP * (1f + (scale - 1f) * CAR_WIDTH_COMPENSATION)).dp
}
