package com.leapbuddy.app.ui

import androidx.compose.ui.graphics.Color

/**
 * 春夏秋冬四套简约背景主题。
 * 整页浅色渐变（顶部色浓、底部近白），与官方 App 的浅色沉浸式风格一致。
 */
data class SeasonTheme(
    val id: String,
    val label: String,
    /** 整页背景渐变（顶部 → 底部），浅色 */
    val gradient: List<Color>,
    /** 整页背景渐变，深色模式下使用 */
    val gradientDark: List<Color>,
    /** 主题选择弹窗里的预览色 */
    val previewTop: Color,
    val previewBottom: Color,
) {
    fun gradient(dark: Boolean): List<Color> = if (dark) gradientDark else gradient
}

object SeasonThemes {

    /** 春 · 嫩绿 */
    val SPRING = SeasonTheme(
        id = "spring",
        label = "春",
        gradient = listOf(Color(0xFFD9F0E0), Color(0xFFEDF7EF), Color(0xFFF9FBF9)),
        gradientDark = listOf(Color(0xFF11251A), Color(0xFF0D1410), Color(0xFF0B0E0C)),
        previewTop = Color(0xFFBFE3CB),
        previewBottom = Color(0xFFEFF8F1),
    )

    /** 夏 · 晴蓝 */
    val SUMMER = SeasonTheme(
        id = "summer",
        label = "夏",
        gradient = listOf(Color(0xFFD3E9FB), Color(0xFFE7F3FC), Color(0xFFF8FBFE)),
        gradientDark = listOf(Color(0xFF0E2030), Color(0xFF0C141C), Color(0xFF0B0E11)),
        previewTop = Color(0xFFB0D7F5),
        previewBottom = Color(0xFFEDF6FD),
    )

    /** 秋 · 暖橙 */
    val AUTUMN = SeasonTheme(
        id = "autumn",
        label = "秋",
        gradient = listOf(Color(0xFFFAE7CF), Color(0xFFFCF2E4), Color(0xFFFDF9F3)),
        gradientDark = listOf(Color(0xFF2A1E11), Color(0xFF16110C), Color(0xFF0D0B09)),
        previewTop = Color(0xFFF5D4A8),
        previewBottom = Color(0xFFFBF1E3),
    )

    /** 冬 · 静白 */
    val WINTER = SeasonTheme(
        id = "winter",
        label = "冬",
        gradient = listOf(Color(0xFFE3E8F0), Color(0xFFEFF2F7), Color(0xFFFAFBFD)),
        gradientDark = listOf(Color(0xFF1A222E), Color(0xFF12161D), Color(0xFF0C0E11)),
        previewTop = Color(0xFFCBD4E1),
        previewBottom = Color(0xFFF2F5F9),
    )

    val ALL = listOf(SPRING, SUMMER, AUTUMN, WINTER)

    fun byId(id: String?): SeasonTheme = ALL.firstOrNull { it.id == id } ?: SPRING
}
