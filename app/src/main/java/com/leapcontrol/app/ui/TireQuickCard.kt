package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.TirePressure

/**
 * 胎压监测卡（首页空调条下方，只占左半边）。
 *
 * 版式对齐官方俯视图：中间一辆透明车模（车头朝上），四个角各一格「位置 + 数值 + bar」。
 * 颜色只表达「有没有问题」——正常时轮子是深灰实心轮胎、数字用正文黑（深色主题为白）；
 * 只有胎压不足时，那个轮子和对应数字才变红，另外三个保持原样。
 *
 * 胎压低到什么程度算「不足」：
 *   GB 26149-2017《乘用车轮胎气压监测系统》要求「低于推荐值 ×75% − 7kPa」时报警，
 *   按零跑常见的推荐胎压 2.3~2.5 bar 折算约 1.66~1.81 bar。
 *   这里取 **2.0 bar** 这条更保守的线（还没到国标报警值就先标红，属于提前预警），
 *   同时只要车端上报了异常（欠压/过压），也一律按红色显示，不跟车端判断打架。
 *   判定逻辑在 [TirePressure]（纯函数、有单测），本文件只负责画。
 *
 * 数据全部来自 AppRuntime 的车况轮询（四轮胎压 + 胎压状态位），本卡不发指令。
 */
@Composable
fun TireQuickCard(
    status: CarStatus?,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val C = LocalAppColors.current
    val tires = status?.tires.orEmpty()
    val clickModifier = onOpen?.let { open -> Modifier.clickable { open() } } ?: Modifier

    Column(
        modifier = modifier
            .height(172.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        // 标题行：左边「胎压」，右边只在异常时给一句提示（正常时保持安静）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("胎压", color = C.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            val alerts = tires.count { TirePressure.stateOf(it.pressure, it.warning) == TirePressure.State.Low }
            if (alerts > 0) {
                Text(
                    text = if (alerts == 1) "气压偏低" else "${alerts} 个气压偏低",
                    color = redFor(C),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左列：左前 / 左后
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                TireValueCell(tires.firstOrNull { it.position == "左前" }, "左前")
                TireValueCell(tires.firstOrNull { it.position == "左后" }, "左后")
            }

            // 中间：透明车模
            TireCarOutline(
                tires = tires,
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight(),
            )

            // 右列：右前 / 右后
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                TireValueCell(tires.firstOrNull { it.position == "右前" }, "右前")
                TireValueCell(tires.firstOrNull { it.position == "右后" }, "右后")
            }
        }
    }
}

/** 异常红：浅色主题用偏深的红（白底上够清楚），深色主题用亮一点的红。 */
private fun redFor(C: AppColors): Color = if (C.isDark) Color(0xFFFF6B6F) else Color(0xFFE5484D)

/** 单个轮子的「位置 / 数值 / bar」。正常＝正文色，不足＝红，无数据＝次要灰。 */
@Composable
private fun TireValueCell(tire: TireInfo?, position: String) {
    val C = LocalAppColors.current
    val state = TirePressure.stateOf(tire?.pressure, tire?.warning == true)
    val valueColor = when (state) {
        // 正常不再用绿色高亮：四个轮子全绿会让人误以为一直在报警，正常就该是普通黑字
        TirePressure.State.Ok -> C.textPrimary
        TirePressure.State.Low -> redFor(C)
        TirePressure.State.Unknown -> C.textSecondary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(position, color = C.textSecondary, fontSize = 9.sp)
        Spacer(Modifier.height(1.dp))
        Text(
            text = tire?.pressure ?: "--",
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = "bar",
            color = if (state == TirePressure.State.Low) redFor(C).copy(alpha = 0.75f) else C.textSecondary,
            fontSize = 8.sp,
        )
    }
}
