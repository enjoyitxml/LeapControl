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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapcontrol.app.NavMode

/**
 * 「怎么去车位」——点「导航到车辆」时先问一句用哪种方式。
 *
 * ## 为什么要问
 * 这个按钮以前一律调起高德的驾车导航。可「导航到车辆」的真实场景是**人已经在楼下、
 * 车停在几百米外的车位**：这时候让它开车过去，路线是绕行车道、时长几分钟，
 * 而人真正要做的是走两百米。默认步行才对得上场景。
 *
 * 但也不能把驾车删掉：停得远、下雨、带着东西的时候，确实要开车过去。
 * 所以做成一次选择，步行排第一并标「推荐」，不记住上次选择 ——
 * 每次都是「走到车库 / 开过去」二选一的场景，记错了反而更烦。
 *
 * ## 版式
 * 居中卡片，不是底部抽屉：Dialog 在 `decorFitsSystemWindows = false` 下拿不到导航栏 inset，
 * 贴着屏幕底边的内容一定被系统手势区吃掉（这个坑踩过三次，见 [DestinationSearchSheet]）。
 */
@Composable
fun NavModeDialog(
    /** 目的地名称（车辆地址），显示在标题下方，让用户确认「去的是这个点」。 */
    destination: String?,
    onDismiss: () -> Unit,
    onPick: (NavMode) -> Unit,
) {
    val C = LocalAppColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // 弹层开着时，侧滑 / 返回键只收弹层，别把首页那张卡或找车页一起退了
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
                    // 自绘弹层内部必须吃东西，否则点卡片空白处会穿透到遮罩上直接关掉
                    .consumeClicks()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.dialogBg)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "怎么去车位",
                    color = C.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!destination.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = destination,
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 顺序就是推荐顺序：步行在最上面，也用主题色底把它标成「默认那一个」
                NavModeRow(mode = NavMode.WALK, recommended = true) { onPick(NavMode.WALK) }
                Spacer(Modifier.height(8.dp))
                NavModeRow(mode = NavMode.BIKE, recommended = false) { onPick(NavMode.BIKE) }
                Spacer(Modifier.height(8.dp))
                NavModeRow(mode = NavMode.CAR, recommended = false) { onPick(NavMode.CAR) }

                Spacer(Modifier.height(14.dp))

                // 取消放右下角的小字：这一层只需要「选一个」，不需要第二个大按钮抢焦点
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "取消",
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

/**
 * 一行出行方式：圆形图标底 + 名称 + 一句场景说明。
 *
 * 推荐项（步行）用淡主题色底 + 主题色描边，其余用 chip 底 ——
 * 深浅主题下主题色都是用户自己选的，靠「淡色底 + 主题色字」才稳，
 * 直接铺主题色实底配白字在浅黄 / 浅绿主题下会糊。
 */
@Composable
private fun NavModeRow(
    mode: NavMode,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val icon: ImageVector = when (mode) {
        NavMode.WALK -> Icons.Filled.DirectionsWalk
        NavMode.BIKE -> Icons.Filled.DirectionsBike
        NavMode.CAR -> Icons.Filled.DirectionsCar
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (recommended) C.accent.copy(alpha = 0.14f) else C.chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (recommended) C.accent.copy(alpha = 0.18f) else C.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.label,
                    color = C.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (recommended) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "推荐",
                        color = C.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.accent.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = mode.hint,
                color = C.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = C.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
