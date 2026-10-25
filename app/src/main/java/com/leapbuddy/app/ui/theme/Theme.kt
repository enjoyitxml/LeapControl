package com.leapbuddy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================
// LeapBuddy 主题占位 —— 界面后期由你自由重新设计，
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

@Composable
fun LeapBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
