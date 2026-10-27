package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.WeeklyTripStats

/**
 * 「近 7 天行程」卡（首页胎压卡右侧，占另一半）。
 *
 * 版式：标题「近7天行程」+ 合计里程 + 7 根柱状图。柱顶逐根标注当天公里数，
 * 最后一根（今天）用主题色高亮加粗，其余用主题色的低透明度渐变 ——
 * 这样换季节主题时整组柱子会跟着变（春绿 / 夏蓝 / 秋橙 / 冬雾蓝），不用另做一套配色。
 *
 * 柱高按 7 日峰值归一化（见 [WeeklyTripStats]），并且只占柱区高度的 82%，
 * 上面留出位置写数字，避免标注被卡片顶边裁掉。不画坐标轴 —— 半屏宽的卡放不下刻度，
 * 能看出「哪几天跑得多、各跑了多少」就够了。
 *
 * 数据来自里程能耗接口的近 7 日明细，本卡不发指令、不做估算：
 * 明细为空时数字显示「--」，柱状图退化成 7 根很矮的灰色占位柱。
 */
@Composable
fun WeeklyTripCard(
    energy: MileageEnergySummary?,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val days = energy?.recentDays.orEmpty()
    val total = WeeklyTripStats.totalText(days)
    val values = WeeklyTripStats.values(days)
    val maxKm = values.maxOrNull() ?: 0.0
    val hasData = maxKm > 0.0

    Column(
        modifier = modifier
            .height(172.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            text = "近7天行程",
            color = C.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = total ?: "--",
                color = C.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "公里",
                color = C.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, km ->
                val isToday = hasData && index == values.lastIndex
                // 82%：柱区上方要给数字标注留位置，否则最高的那根会把标签顶出卡片
                val fraction = if (!hasData) {
                    0.08f
                } else {
                    maxOf(0.08f, (km / maxKm).toFloat() * 0.82f)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = WeeklyTripStats.dayText(km),
                        color = if (isToday) C.accent else C.textSecondary.copy(alpha = 0.9f),
                        fontSize = 7.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 2.dp,
                                    bottomEnd = 2.dp,
                                ),
                            )
                            .background(
                                Brush.verticalGradient(
                                    if (isToday) {
                                        listOf(C.accent, C.accent.copy(alpha = 0.50f))
                                    } else {
                                        listOf(C.accent.copy(alpha = 0.65f), C.accent.copy(alpha = 0.14f))
                                    },
                                ),
                            ),
                    )
                }
            }
        }
    }
}
