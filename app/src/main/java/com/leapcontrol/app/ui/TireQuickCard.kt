package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.TirePressure

/**
 * 胎压监测卡（首页空调条下方，只占左半边）。
 *
 * 不设标题：四个「位置 / 数值 / kPa」+ 中间车模直接把卡片铺满，
 * 半屏宽的卡再塞一行标题只会把数字挤小。
 *
 * 对齐方式：左右两列用 SpaceEvenly 排布，中间车模的前后轴轮胎必须落在
 * 上排两个数值格的中心线上。格子的实际中心随字号和卡片高度变化，
 * 所以布局完成后实测一次「左前」格子的中心比例传给 [TireCarOutline]，
 * 车模据此定纵向比例 —— 换字号、改卡片高度都不用跟着改常量。
 *
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

    var frontFraction by remember { mutableStateOf(0.29f) }
    var areaTopPx by remember { mutableStateOf(0f) }
    var areaHeightPx by remember { mutableStateOf(0f) }

    Row(
        modifier = modifier
            .height(172.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .then(clickModifier)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .onGloballyPositioned {
                areaTopPx = it.positionInRoot().y
                areaHeightPx = it.size.height.toFloat()
            },
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
            TireValueCell(
                tire = tires.firstOrNull { it.position == "左前" },
                position = "左前",
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (areaHeightPx > 0f) {
                        val centerY = coords.positionInRoot().y + coords.size.height / 2f - areaTopPx
                        frontFraction = (centerY / areaHeightPx).coerceIn(0.15f, 0.45f)
                    }
                },
            )
            TireValueCell(tires.firstOrNull { it.position == "左后" }, "左后")
        }

        // 中间：透明车模（前后轴跟着两侧数字走）
        TireCarOutline(
            tires = tires,
            frontFraction = frontFraction,
            modifier = Modifier
                .width(60.dp)
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

/** 异常红：浅色主题用偏深的红（白底上够清楚），深色主题用亮一点的红。 */
private fun redFor(C: AppColors): Color = if (C.isDark) Color(0xFFFF6B6F) else Color(0xFFE5484D)

/** 单个轮子的「位置 / 数值 / kPa」。正常＝正文色，不足＝红，无数据＝次要灰。 */
@Composable
private fun TireValueCell(
    tire: TireInfo?,
    position: String,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val state = TirePressure.stateOf(tire?.pressure, tire?.warning == true)
    val valueColor = when (state) {
        // 正常不再用绿色高亮：四个轮子全绿会让人误以为一直在报警，正常就该是普通黑字
        TirePressure.State.Ok -> C.textPrimary
        TirePressure.State.Low -> redFor(C)
        TirePressure.State.Unknown -> C.textSecondary
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(position, color = C.textSecondary, fontSize = 9.5.sp)
        Spacer(Modifier.height(1.dp))
        Text(
            text = tire?.pressure ?: "--",
            color = valueColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = "kPa",
            color = if (state == TirePressure.State.Low) redFor(C).copy(alpha = 0.75f) else C.textSecondary,
            fontSize = 8.5.sp,
        )
    }
}
