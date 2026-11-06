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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * 「怎么才能记到行程」完整说明弹窗。
 *
 * 入口在行程轨迹页右上角「怎么记」：原来是一条常显横幅，占一整行还把月合计挤下去，
 * 用户 2026-11-06 要求挪到右上角，横幅组件随之删掉。
 *
 * B 方案是搭车况刷新的顺风车采样：没有后台定位、不会额外唤醒车机，代价就是
 * App 没打开的路段一定缺。用户第一次进来往往以为跟高德足迹一样自动记录，
 * 结果跑完一趟发现是空的。所以空态复述要点、右上角给完整步骤，两处口径一致。
 */
@Composable
internal fun TripGuideDialog(onDismiss: () -> Unit) {
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
                    "怎么才能记到行程",
                    color = C.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "零控不在后台偷偷定位，所以记录方式有点讲究",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    GuideStep(
                        number = "1",
                        title = "默认手动开始；不想每次点就打开「自动开始记录」",
                        body = "关着时（默认）：打开 App、刷新车况、车动起来都不会自己开一段，上车前点一次「开始记录行程」才开始，那一刻就是出发时间。打开开关后，车辆连续两帧都在行驶（速度 ≥ 3 km/h 或挡位 D/R/N）才自己开一段 —— 单帧信号抖动不算，不会出现「刚打开 App 就显示行程中」。开关在本页「开始记录行程」上面，选手动还是自动由你定。",
                    )
                    GuideStep(
                        number = "2",
                        title = "锁车立即结束，也可以手动点结束",
                        body = "下车锁门那一刻这一段就收尾，不用等；忘了锁车或者在车里休息，点「结束行程」立即保存。已经开起来之后停稳不动约 5 分钟，系统也会自动收尾。只是点了开始还没动车时不会自动清掉这一段，免得你以为记录丢了。",
                    )
                    GuideStep(
                        number = "3",
                        title = "记录期间只要发生车况刷新，就记一个点",
                        body = "行驶中停在零控首页时约 60 秒自动刷一次；切回前台、下拉刷新、点更新时间，以及小组件 / 磁贴刷车况时也各记一次。正在记录的这一段会在行程卡上实时显示点数、里程和计时，不用等跑完。轨迹不会为此多发请求。",
                    )
                    GuideStep(
                        number = "4",
                        title = "中途停车办事被拆成几段？勾起来合并",
                        body = "下车锁门这一趟就结束了，回来再开是新的一段。在历史清单右上角点「选择」，把这几段勾上再点「合并」，就合成一段：起点沿用最早那段、终点沿用最后一段，中间停车的时间也算进总时长。不想要的段落展开后可以直接删除。",
                    )
                    GuideStep(
                        number = "5",
                        title = "后台那段会缺点，是正常的",
                        body = "60 秒的自动刷新只在零控首页前台生效。切到别的应用、锁屏或退到后台后就没有了，只在切回来 / 小组件刷新时补一两个点，中间路段会缺；加上地库没信号、车机没上报坐标，这不是连续 GPS 轨迹。展开行程看到的地图是把这些稀疏点用直线连起来的示意图，不是实际走过的路。",
                    )
                    GuideStep(
                        number = "6",
                        title = "想让起终点显示中文地址",
                        body = "在设置里填写高德 Web Key，行程收尾时各反查一次；没填就显示「起点 / 终点」。",
                    )
                    GuideStep(
                        number = "7",
                        title = "能耗按首末电量差算",
                        body = "每段只用起点与终点的电量百分比相减，不靠电压电流硬估。想要度数（kWh）和百公里电耗，在行程轨迹页「本月合计」下面那行点「去填写」填一次容量（设置 → 高级设置里也有同一个入口）；不填就只显示百分比，不会按车型猜一个数字。中途充过电的那段不显示耗电。",
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "这些点只存本机、按车架号分开、保留最近 90 天，退出登录会整份清除。",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.accent.copy(alpha = 0.14f))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "知道了",
                        color = C.accent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, body: String) {
    val C = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(C.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = C.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = body,
                color = C.textSecondary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
