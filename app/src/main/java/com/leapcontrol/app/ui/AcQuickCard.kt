package com.leapcontrol.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R

/**
 * 快捷控车下方的「空调概览条」。
 *
 * 版式照设计稿 1:1 还原（稿子 1080×289 的三倍图，折算到 dp）：
 *   - 整条：高 ≈59dp、圆角 8dp、左右内边距 24dp
 *   - 左：设定温度大字 30sp（如「24°」）
 *   - 中：距大字 24dp 的「车内温度 32.5℃」11.5sp 次要文字
 *   - 右：30dp 圆形按钮 + 20dp 四叶风扇
 *
 * 交互：风扇灰色＝空调关闭，点一下开空调；开启后圆底淡绿、图标变主题绿并持续旋转。
 * 点下去立刻按「意图」点亮（车端 acSwitch 上报有几秒延迟，否则会觉得点了没反应），
 * 指令结束回拉车况后以真实状态为准。
 */
@Composable
fun AcQuickCard(
    status: CarStatus?,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current

    // 上一次点击的意图：只在指令进行中（busy）用来先点亮，指令结束就不再参考
    var lastIntentOn by remember { mutableStateOf(false) }
    val acOn = status?.acOn == true || (busy && lastIntentOn)

    val target = status?.acTargetTemp?.toIntOrNull()
    val indoor = normalizeTemp(status?.interiorTemp)

    // 只有开机状态才挂无限旋转动画，关着的时候不空转
    val fanAngle = if (acOn) {
        val transition = rememberInfiniteTransition(label = "ac-fan")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearEasing)),
            label = "ac-fan-angle",
        )
        angle
    } else {
        0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 59.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.cardBg)
            .padding(start = 24.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // 左：设定温度
        Text(
            text = if (target != null) "$target°" else "--",
            color = C.textPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(24.dp))
        // 中：车内温度
        Text(
            text = "车内温度 $indoor℃",
            color = C.textSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        // 右：风扇开关
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (acOn) C.accent.copy(alpha = 0.16f) else fanCircleBg(C.isDark))
                .clickable(enabled = !busy) {
                    lastIntentOn = !acOn
                    onToggle(!acOn)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fan_blade),
                contentDescription = if (acOn) "关闭空调" else "开启空调",
                tint = when {
                    acOn && busy -> C.accent.copy(alpha = 0.55f)
                    acOn -> C.accent
                    busy -> FAN_GRAY.copy(alpha = 0.45f)
                    else -> FAN_GRAY
                },
                modifier = Modifier
                    .size(20.dp)
                    .rotate(fanAngle),
            )
        }
    }
}

/** 关闭态圆形按钮的底色：浅色主题用略深于卡片的浅灰，深色主题用一层淡白压出圆盘。 */
private fun fanCircleBg(isDark: Boolean): Color =
    if (isDark) Color(0x1FFFFFFF) else Color(0xFFF1F3F2)

/** 关闭态风扇图标色（设计稿实测 #9A9C9B） */
private val FAN_GRAY = Color(0xFF9A9C9B)

/**
 * 车内温度信号有时带单位、有时是纯数字（如 "32.5" / "32.5°C" / "32.5℃"），
 * 统一去掉单位，交给调用处拼「℃」，避免出现「32.5°C℃」。
 */
private fun normalizeTemp(raw: String?): String {
    if (raw.isNullOrBlank()) return "--"
    return raw.trim()
        .removeSuffix("℃")
        .removeSuffix("°C")
        .removeSuffix("°")
        .removeSuffix("C")
        .trim()
        .ifEmpty { "--" }
}
