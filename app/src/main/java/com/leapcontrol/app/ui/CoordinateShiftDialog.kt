package com.leapcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 「坐标纠偏」开关 —— 给「导航过去不是车停的地方」留一个能当场验证的出口。
 *
 * ## 为什么这件事不能让 App 自己猜
 * 车端上报的经纬度，逆向文档写的是 GCJ-02（与高德同系、可直接用），但这一栏**没有抓包证据**。
 * 如果云端存的其实是 GPS 原始的 WGS-84，那么当 GCJ-02 画出来整张图会偏 300~800 米，
 * 症状正是「点导航过去，目的地不在车那儿」。反过来，如果它本来就是 GCJ-02，
 * 我们主动换算一次等于**自己制造 500 米偏移**，比现在更糟 ——
 * 没有真机数据之前，只能让用户拿自己的车做一次判定，所以做成开关而不是默认开。
 *
 * ## 怎么判断
 * 打开后地图与地址立刻按换算后的坐标重拉：钉子落到车上 → 就是坐标系问题，留着；
 * 钉子反而跑得更远 → 关掉。整个过程两秒，不需要懂任何术语。
 *
 * 版式同 [NavModeDialog]：居中卡片，不贴底边（贴底必被手势区吃掉）。
 */
@Composable
fun CoordinateShiftDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onPick: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(enabled = true, onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = DIALOG_SCRIM))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .consumeClicks()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.dialogBg)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "定位偏了几百米？",
                    color = C.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "如果点导航过去，目的地总是落在车实际位置的几百米外，" +
                        "而且每次偏的方向和距离都差不多 —— 多半是车端报的是 GPS 原始坐标，" +
                        "地图用的却是 GCJ-02。换成下面第二种再点一次刷新就能看出来。",
                    color = C.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(14.dp))

                ShiftRow(
                    title = "不纠偏",
                    hint = "车端坐标就是地图坐标（默认）",
                    selected = !enabled,
                ) { onPick(false) }
                Spacer(Modifier.height(8.dp))
                ShiftRow(
                    title = "按 WGS-84 → GCJ-02 换算",
                    // 偏移方向随地区不同（华东偏西北、华北偏东北），别在文案里写死方向
                    hint = "整张图会整体挪几百米，方向固定",
                    selected = enabled,
                ) { onPick(true) }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "改完会自动重拉地图与地址：钉子落到车上就留着，跑得更远就关掉。",
                    color = C.textSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "关闭",
                        color = C.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 一行选项：选中项用淡主题色底 + 右侧勾，未选中用 chip 底。 */
@Composable
private fun ShiftRow(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) C.accent.copy(alpha = 0.14f) else C.chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                color = C.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(C.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = C.accent,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}
