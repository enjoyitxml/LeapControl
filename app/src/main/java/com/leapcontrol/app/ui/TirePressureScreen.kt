package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 胎压监测页（独立页）：俯视胎压示意图 + 四轮明细。
 * 仅展示开源已提供的胎压信号（四轮数值 + 欠压/过压告警）。
 */
@Composable
fun TirePressureScreen(
    status: CarStatus?,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val tires = status?.tires.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        // 顶栏：左返回 + 居中标题
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
            Text(
                text = "胎压监测",
                color = C.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            Surface(
                shape = CircleShape,
                color = C.iconBg,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(34.dp)
                    .clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "返回",
                        tint = C.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 俯视胎压示意图（轮胎按状态着色）
        AppCard {
            if (tires.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无胎压数据", color = C.textSecondary, fontSize = 14.sp)
                }
            } else {
                TireTopView(tires = tires, modifier = Modifier.fillMaxWidth().height(220.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // 四轮明细
        AppCard {
            val order = listOf("左前", "右前", "左后", "右后")
            order.forEachIndexed { i, pos ->
                val tire = tires.firstOrNull { it.position == pos }
                if (i > 0) Spacer(Modifier.height(12.dp))
                TireDetailRow(pos, tire)
                if (i < order.size - 1) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp).background(C.border),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "正常胎压范围约 230 ~ 250 kPa；低于下限提示欠压，高于上限提示过压。",
            color = C.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TireDetailRow(position: String, tire: TireInfo?) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(position, color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = tire?.pressure?.let { "$it kPa" } ?: "--",
                color = when {
                    tire == null -> C.textSecondary
                    tire.warning -> Color(0xFFE5484D)
                    else -> C.textPrimary
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            val state = when {
                tire == null -> "无数据"
                tire.warning -> "异常"
                else -> "正常"
            }
            Text(
                text = state,
                color = when {
                    tire == null -> C.textSecondary
                    tire.warning -> Color(0xFFE5484D)
                    else -> C.accent
                },
                fontSize = 11.sp,
            )
        }
    }
}
