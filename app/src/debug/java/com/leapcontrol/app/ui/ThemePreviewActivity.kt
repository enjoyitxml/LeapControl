package com.leapcontrol.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.DailyMileage
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.R

/**
 * 仅 debug 包的主题预览页：把 20 套背景按真实尺寸画出来，用来核对观感与文字可读性。
 *
 * `adb shell am start -n com.leapcontrol.app/.ui.ThemePreviewActivity -e mode grid`
 * （mode=full 时按 1:1 首页头部尺寸渲染并叠上假的车名 / 续航文字）
 */
class ThemePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "grid"
        setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF101114))) {
                when (mode) {
                    "full" -> FullSizePreview()
                    "cards" -> CardsPreview()
                    else -> GridPreview()
                }
            }
        }
    }
}

/**
 * 「卡片观感」预览：按「我的」页与首页右列的真实版式渲染实体卡片，
 * 用来确认描边已全部去掉、卡片只靠底色与圆角分层。
 *
 * `adb shell am start -n com.leapcontrol.app/.ui.ThemePreviewActivity -e mode cards`
 */
@Composable
private fun CardsPreview() {
    val fakeEnergy = MileageEnergySummary(
        recentDays = listOf(
            DailyMileage("周一", 12.4),
            DailyMileage("周二", 31.8),
            DailyMileage("周三", 0.0),
            DailyMileage("周四", 8.6),
            DailyMileage("周五", 44.2),
            DailyMileage("周六", 23.1),
            DailyMileage("今天", 5.5),
        ),
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        listOf(false, true).forEach { dark ->
            ProvideAppColors(dark = dark, accent = SeasonThemes.MIST_HILLS.accent(dark)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(SeasonThemes.MIST_HILLS.gradient(dark)))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (dark) "深色 · 卡片（无描边）" else "浅色 · 卡片（无描边）",
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    SettingsCard(title = "车辆与账号") {
                        Text(
                            "零跑 C11 · VIN 8F3K2A",
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ProfileItem(
                        R.drawable.ic_settings,
                        "设置",
                        "控车密码、高德 Web Key、车模与系统磁贴",
                    ) {}
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { TireQuickCard(status = null) }
                        Box(Modifier.weight(1f)) { WeeklyTripCard(energy = fakeEnergy) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OptionSurface(selected = false) {
                                Text(
                                    "未选中项",
                                    color = LocalAppColors.current.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            OptionSurface(selected = true) {
                                Text(
                                    "选中项",
                                    color = LocalAppColors.current.accent,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridPreview() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeasonThemes.ALL.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { theme ->
                    ThemeBlock(
                        theme = theme,
                        height = 150.dp,
                        showText = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun FullSizePreview() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            SeasonThemes.MIST_HILLS,
            SeasonThemes.SAKURA,
            SeasonThemes.STARRY,
            SeasonThemes.AURORA,
            SeasonThemes.CITY_NIGHT,
        ).forEach { theme ->
            ThemeBlock(theme = theme, height = 250.dp, showText = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ThemeBlock(
    theme: SeasonTheme,
    height: Dp,
    showText: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = theme.night
    val ink = if (dark) Color.White else Color(0xFF14161A)
    val top = theme.gradient(dark).first()
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(theme.gradient(dark))),
    ) {
        val density = LocalDensity.current
        val wpx = with(density) { maxWidth.toPx() }
        val hpx = with(density) { height.toPx() }
        val veil = remember(theme.id, dark, wpx, hpx) {
            Brush.radialGradient(
                listOf(top.copy(alpha = 0f), top.copy(alpha = 0.14f), top.copy(alpha = 0.36f)),
                center = Offset(wpx / 2f, hpx * 0.62f),
                radius = hpx * 0.95f,
            )
        }
        // 走和首页完全相同的路径：雾化遮罩作为 veilBrush 传进画布，一起参与淡出
        ThemeBackdrop(
            theme = theme,
            dark = dark,
            animate = false,
            sceneHeight = height,
            veilBrush = veil,
        )

        if (showText) {
            Column(Modifier.padding(start = 18.dp, top = 14.dp)) {
                Text("零跑 C11", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("更新于 19:20", color = ink.copy(alpha = 0.55f), fontSize = 11.sp)
                Spacer(Modifier.height(20.dp))
                Row {
                    Text("512", color = ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("km", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("续航里程 · 剩余 78%", color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }

        Text(
            text = theme.label + if (theme.animated) " · 动态" else "",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
