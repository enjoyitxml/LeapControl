package com.leapcontrol.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 语义色：只用于「成功 / 在线」这类状态，不参与主题换色。
 *
 * 定位 / 电池 / 蓝牙钥匙这些地方的「正常」必须是稳定的一眼可辨的绿，
 * 跟着季节主题变色（秋天变橙）会让人误读成警告。
 */
val statusGood = Color(0xFF1FA45B)

/** 深色主题下用更亮一档的绿，压在深底上才看得清。 */
val statusGoodDark = Color(0xFF3DD68C)

/**
 * 语义色：需要注意但不算故障（如「有后台功能正在耗电」）。
 * 同样不跟主题变色 —— 秋天主题整体偏橙，跟着变就分不出「提示」和「正常」了。
 */
val statusWarn = Color(0xFFD98324)

/** 深色主题下的橙色，亮度提一档。 */
val statusWarnDark = Color(0xFFFFB454)
