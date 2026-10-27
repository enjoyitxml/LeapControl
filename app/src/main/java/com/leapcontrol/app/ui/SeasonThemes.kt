package com.leapcontrol.app.ui

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * 春夏秋冬 + 星空 五套背景主题。
 *
 * 四季是浅色调：整页浅色渐变（顶部色浓、底部近白），与官方 App 的浅色沉浸式风格一致。
 * 星空是夜间深色调：整页深空蓝黑渐变，配动态星空视频。
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
    /**
     * 是否强制使用深色内容色（文字 / 图标 / 卡片）。
     *
     * 星空主题的页面底色本身就是深空黑，若沿用「跟随系统」的浅色内容会出现
     * 黑底黑字，所以只要选中它就一律按深色取内容色，与应用的外观模式设置无关。
     */
    val forcesDarkContent: Boolean = false,
    /**
     * 顶部雾化遮罩的强度倍率。
     *
     * 四季视频的主景集中在上部 38%，需要用较浓的雾把文字底下压干净；
     * 星空视频的星点是满屏分布的，同样的浓度会把星星本身糊掉，
     * 所以星空主题只留一层很淡的雾，够压住星点即可。
     */
    val veilAlphaScale: Float = 1f,
    /** 底部渐隐（控件区）的强度倍率，用途同上。 */
    val scrimAlphaScale: Float = 1f,
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

    /** 星空 · 夜间（同时作为自动夜间主题） */
    val STARS = SeasonTheme(
        id = "stars",
        label = "星空",
        gradient = listOf(Color(0xFF0B1226), Color(0xFF070C18), Color(0xFF04060C)),
        gradientDark = listOf(Color(0xFF070C1A), Color(0xFF04070F), Color(0xFF020408)),
        previewTop = Color(0xFF111C36),
        previewBottom = Color(0xFF05070D),
        forcesDarkContent = true,
        // 星空视频本身够暗，这层雾只要压住文字底下的星点，不能再把星星本体糊掉
        veilAlphaScale = 0.35f,
        scrimAlphaScale = 0.85f,
    )

    val ALL = listOf(SPRING, SUMMER, AUTUMN, WINTER, STARS)

    fun byId(id: String?): SeasonTheme = ALL.firstOrNull { it.id == id } ?: SPRING

    // ---------------- 夜间自动切换 ----------------

    /** 夜间主题（自动切换时用它）。 */
    val NIGHT = STARS

    /** 夜间时段起点（含）：22:00。 */
    const val NIGHT_START_HOUR = 22

    /** 夜间时段终点（不含）：次日 06:00。 */
    const val NIGHT_END_HOUR = 6

    /** 当前本地时间是否落在夜间时段内（22:00 – 次日 06:00）。 */
    fun isNight(now: Calendar = Calendar.getInstance()): Boolean {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }
}
