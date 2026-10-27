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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.WeeklyTripStats

/**
 * 「近 7 天行程」卡（首页胎压卡右侧，占另一半）。
 *
 * 版式：标题「近7天行程」+ 合计里程 + 7 根柱状图，最后一根（今天）用品牌绿高亮。
 * 柱高按当天里程相对 7 日峰值归一化（见 [WeeklyTripStats]），不画坐标轴 ——
 * 半屏宽的卡放不下刻度，能看出「哪几天跑得多」就够了。
 *
 * 数据来自里程能耗接口的近 7 日明细，本卡不发指令、不做估算：
 * 明细为空时数字显示「--」，柱状图退化成 7 根灰色占位柱。
 */
@Composable
fun WeeklyTripCard(
    energy: MileageEnergySummary?,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val days = energy?.recentDays.orEmpty()
    val total = WeeklyTripStats.totalText(days)
    val series = WeeklyTripStats.series(days)
    val hasData = days.any { it.km > 0.0 }

    Column(
        modifier = modifier
            .height(172.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.panelBg, RoundedCornerShape(16.dp))
            .border(1.dp, C.border, RoundedCornerShape(16.dp))
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

        Spacer(Modifier.height(9.dp))

        // 柱状图：固定 7 根，等宽留缝，底部对齐；有数据时最矮也留 10%，
        // 否则「某天没开车」会退化成一条看得见的细线，容易被误读成有行程。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            series.forEachIndexed { index, ratio ->
                val isToday = hasData && index == series.lastIndex
                val fraction = if (hasData) maxOf(0.10f, ratio) else 0.10f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(if (isToday) C.accent else C.barTrack),
                )
            }
        }
    }
}
