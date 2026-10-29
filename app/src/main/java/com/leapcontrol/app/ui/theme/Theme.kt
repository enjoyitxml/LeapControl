package com.leapcontrol.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// ============================================================
// LeapControl 主题占位 —— 界面后期由你自由重新设计，
// 只需替换本文件配色与各 Screen 布局，业务逻辑层无需改动。
// ============================================================

val BrandBlue = Color(0xFF1A73E8)
val BrandDeepBlue = Color(0xFF0D47A1)
val BrandAmber = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = BrandDeepBlue,
    tertiary = BrandAmber,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFFAECBFA),
    tertiary = BrandAmber,
)

/**
 * 字号缩放的封顶倍数。
 *
 * 零控的版式是手工量出来的密排版式（40sp 续航大字、定宽里程槽、胶囊按钮、四个圆钮一排…），
 * 系统把字号拉到 1.5~2.0 倍时，这些定宽元素会被撑爆：里程行折成两行、百分比被挤掉。
 * 与其让整页塌掉，不如把缩放钳在 1.3 倍 —— 仍然明显放大（用户确实需要更大的字），
 * 但不会把版式挤坏。确实要更大字号，可以在局部用 `LocalDensity` 覆盖回来。
 */
private const val MAX_FONT_SCALE = 1.3f

@Composable
fun LeapControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val clamped = Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides clamped) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
