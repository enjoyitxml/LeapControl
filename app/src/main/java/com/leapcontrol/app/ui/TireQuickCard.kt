package com.leapcontrol.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.TirePressure
import kotlin.math.abs

/**
 * 车模前后轴的纵向比例（前轴中心线落在画布高度的哪个比例上）。
 *
 * 这个值只能布局完成后实测：左右两列用 SpaceEvenly 排布，格子中心随字号、卡片高度变化。
 * 但实测结果**不能存在页面的 remember 里** —— 切 Tab 回首页时 HomeScreen 整棵子树重建、
 * remember 归零，车模会先用估算值画一帧、量到真值再跳到正确比例，那一帧的错位就是
 * 「每次回首页车模闪一下」。
 *
 * 所以：① 值缓存在进程级单例，App 起来只量第一次，之后每次进首页直接就是正确值；
 * ② 该 State 只在 Canvas 的**绘制阶段**读，写值只重绘那块 Canvas，不重组整张卡片。
 */
private object TireCarGeometry {
    var frontFraction by mutableFloatStateOf(0.29f)
}

/** 布局期的临时量具。刻意不用 snapshot state：写入不触发重组，也不引起二次布局。 */
private class TireMeasure {
    var rowCoords: LayoutCoordinates? = null

    /**
     * 已经量过的那次行高（px）。
     *
     * 滚动时 `onGloballyPositioned` 每一帧都会回调（节点在根坐标里的位置变了），
     * 但「格子中心在卡片里的相对比例」只跟布局有关、跟滚动无关 ——
     * 行高没变就说明布局没变，直接跳过，省掉每帧两次 positionInRoot 读取。
     */
    var measuredHeight = -1
}

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
 * 实测只在布局期做一次、结果进程内复用（见 [TireCarGeometry]），换来的是切回首页时车模不再跳动。
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
 * 数据全部来自 AppRuntime 的车况轮询（四轮胎压 + 胎压状态位），本卡不发指令、也不跳二级页
 * （四轮数值在卡上已经全给了，再进一页只是把同样的数字放大一遍）。
 */
@Composable
fun TireQuickCard(
    status: CarStatus?,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val tires = status?.tires.orEmpty()
    val measure = remember { TireMeasure() }

    /**
     * 实测「左前」格子中心 → 前轴比例。
     *
     * 卡片和格子都取**同一瞬间**的根坐标再相减，滚动位置、状态栏高度都不参与运算，
     * 所以滑到哪儿量出来的都是同一个值（以前分别缓存上下沿，滚动中赶上一帧错位就会量偏）。
     */
    fun calibrate(coords: LayoutCoordinates) {
        val row = measure.rowCoords ?: return
        val height = row.size.height
        if (height <= 0) return
        // 布局没变就不重量（滚动期间这个回调每帧都会来）
        if (height == measure.measuredHeight) return
        val centerY = coords.positionInRoot().y + coords.size.height / 2f - row.positionInRoot().y
        val next = (centerY / height.toFloat()).coerceIn(0.15f, 0.45f)
        measure.measuredHeight = height
        // 门槛 0.004（卡高 152dp ≈ 0.6dp）：亚像素级的差异不值得写值，避免反复触发绘制
        if (abs(next - TireCarGeometry.frontFraction) > 0.004f) {
            TireCarGeometry.frontFraction = next
        }
    }

    Row(
        modifier = modifier
            .height(172.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panelBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .onGloballyPositioned { measure.rowCoords = it },
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
                modifier = Modifier.onGloballyPositioned { calibrate(it) },
            )
            TireValueCell(tires.firstOrNull { it.position == "左后" }, "左后")
        }

        // 中间：透明车模（前后轴跟着两侧数字走）
        TireCarOutline(
            tires = tires,
            frontFraction = { TireCarGeometry.frontFraction },
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
