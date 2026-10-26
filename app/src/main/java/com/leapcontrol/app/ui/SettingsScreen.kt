package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppearanceMode

/**
 * 设置页。
 *  - 外观模式：跟随系统（默认）/ 始终浅色 / 始终深色
 *  - 四季背景：春夏秋冬
 *  - 车模显示方式：官方车图优先 / 内置剪影
 */
@Composable
fun SettingsScreen(
    runtime: AppRuntime,
    mode: AppearanceMode,
    onModeChange: (AppearanceMode) -> Unit,
    onSeasonChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var themeId by remember { mutableStateOf(runtime.seasonThemeId()) }
    var artworkEnabled by remember { mutableStateOf(runtime.carArtworkEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = C.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text("设置", color = C.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))

        // ---- 外观模式 ----
        SettingsCard("外观模式") {
            Text(
                "默认跟随手机系统；开启系统深色模式后自动切换。",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))
            AppearanceMode.entries.forEach { item ->
                if (item.ordinal > 0) Spacer(Modifier.height(8.dp))
                OptionRow(
                    label = when (item) {
                        AppearanceMode.SYSTEM -> "跟随系统"
                        AppearanceMode.LIGHT -> "始终浅色"
                        AppearanceMode.DARK -> "始终深色"
                    },
                    desc = when (item) {
                        AppearanceMode.SYSTEM -> "随手机深色模式自动切换（默认）"
                        AppearanceMode.LIGHT -> "不受系统影响，固定使用浅色界面"
                        AppearanceMode.DARK -> "不受系统影响，固定使用深色界面"
                    },
                    selected = mode == item,
                ) { onModeChange(item) }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- 四季背景 ----
        SettingsCard("四季背景") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SeasonThemes.ALL.forEach { theme ->
                    val selected = theme.id == themeId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                runtime.setSeasonThemeId(theme.id)
                                themeId = theme.id
                                onSeasonChange(theme.id)
                            }
                            .padding(4.dp)
                            .weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(theme.gradient(C.isDark))
                                )
                                .border(
                                    if (selected) 2.dp else 0.5.dp,
                                    if (selected) C.accent else C.border,
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, null, tint = C.accent, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            theme.label,
                            color = if (selected) C.accent else C.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- 车模显示开关 ----
        SettingsCard("车模显示") {
            SwitchRow(
                label = "显示车模",
                desc = "开启时用官方接口下发的本车定妆照；取不到时不显示。关闭后首屏不占位、也不发请求",
                checked = artworkEnabled,
                onCheckedChange = { enabled ->
                    runtime.setCarArtworkEnabled(enabled)
                    artworkEnabled = enabled
                },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 带开关的设置行。 */
@Composable
private fun SwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = C.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = C.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = C.barTrack,
            ),
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(C.cardBg, shape)
            .border(1.dp, C.border, shape)
            .padding(16.dp),
    ) {
        Text(title, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun OptionRow(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) C.chipBgActive else C.chipBg, shape)
            .border(1.dp, if (selected) C.accent.copy(alpha = 0.45f) else C.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) C.accent else C.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = C.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
