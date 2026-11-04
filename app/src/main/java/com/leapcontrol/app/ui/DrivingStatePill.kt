package com.leapcontrol.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.DrivingStateRules
import com.leapcontrol.app.DrivingTone

/** 充电绿：与首页电量进度条同一个色号，不做主题派生。 */
private val ChargeGreen = Color(0xFF2FCC71)

/** 倒车琥珀：前进用主题色，倒车必须一眼分得出来。 */
private val ReverseAmber = Color(0xFFF0A93B)

/**
 * 车辆正下方的状态胶囊：`P 已驻车` / `● 行驶中 · 42 km/h` / `● 充电中 · 剩 42 分钟`。
 *
 * ## 为什么用胶囊
 * 它和续航下方那颗「充电中心」同一套外观（`chipBg` 底、50% 圆角、12sp 字），
 * 放在车模与快捷控车之间像一句状态注解，而不是又一块卡片。
 *
 * ## 判不出来就整条不画
 * 规则见 [DrivingStateRules.badge]：挡位与车速都没有时返回 null，这里直接不占位。
 *
 * ## 熄火后不念挡位字母
 * 车已闭锁 / 已下电时一律说「已驻车」，N / D / R 徽标不画（车端熄火后不刷 P 是常态），
 * 只有真的 P 才留徽标。详见 [DrivingStateRules] 的类注释。
 *
 * ## 放在哪儿
 * 固定在车模正下方、水平居中（用户 2026-11-04 定的口径：不要再给位置开关，
 * 「车模右上」与「胶囊同行」两版都撤了）。整行没内容时它自己消失，不占位。
 *
 * ## 动画
 * 只有「行驶中」的点会呼吸，且**滚动时停**——首页上下滑时那一帧已经很紧，
 * 没必要为一个 6dp 的点每帧重绘。其余状态是静点，压根不建动画。
 *
 * [scrolling] 传的是 `MutableState` 而不是布尔值：在首页那一层读 `.value` 会让整棵首页
 * 跟着滚动状态重组，只有在这里读才只波及这颗胶囊（同 [ChargingPulseEntry] 的做法）。
 */
@Composable
fun DrivingStatePill(
    status: CarStatus?,
    scrolling: State<Boolean>,
    modifier: Modifier = Modifier,
) {
    if (status == null) return
    val badge = remember(
        status.gearStatus,
        status.speed,
        status.chargeState,
        status.chargeRemainMinutes,
        status.chargeTimeLeft,
        status.isShutDown,
        status.locked,
    ) {
        DrivingStateRules.badge(
            gearStatus = status.gearStatus,
            speedKmh = DrivingStateRules.parseSpeed(status.speed),
            chargeState = status.chargeState,
            chargeRemainMinutes = status.chargeRemainMinutes,
            chargeTimeLeft = status.chargeTimeLeft,
            // 已闭锁 / 已下电 / EPB 拉起时说「已驻车」，别把熄火后不可信的挡位字母念出来
            isShutDown = status.isShutDown,
            locked = status.locked,
        )
    } ?: return

    val C = LocalAppColors.current
    val toneColor = when (badge.tone) {
        DrivingTone.MOVING -> C.accent
        DrivingTone.REVERSING -> ReverseAmber
        DrivingTone.WARNING -> ReverseAmber
        DrivingTone.CHARGING -> ChargeGreen
        DrivingTone.PARKED -> C.textSecondary
    }
    // 行驶中才呼吸，且滚动时冻结在「亮」的那一端，避免滚动期间每帧都在插值
    val breathing = badge.tone == DrivingTone.MOVING && !scrolling.value
    val dotAlpha = if (breathing) rememberBreathAlpha() else 1f

    Surface(
        shape = RoundedCornerShape(50),
        color = C.chipBg,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GearBadge(gear = badge.gear)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(toneColor.copy(alpha = dotAlpha), CircleShape),
            )
            Text(
                text = badge.label,
                color = C.textPrimary,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            if (badge.detail != null) {
                Text(
                    text = "· ${badge.detail}",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    // 兜底：车模下方这一行宽度够用，但大字号 + 「剩 1 小时 15 分钟」
                    // 这种长副信息仍可能顶到屏幕边，宁可省略也不换行（一换行整条会变两行）。
                    // fill = false 保证宽裕时仍是自身宽度，不会把胶囊拉长。
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** 挡位徽标：17dp 小方块，车端没报挡位时什么都不画（不占位）。 */
@Composable
private fun GearBadge(gear: String?) {
    if (gear == null) return
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(17.dp)
            .background(C.iconBg, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = gear,
            color = C.textPrimary,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 0.45 ↔ 1.0 的往返呼吸：和首页充电头脉冲一个节奏，不另造一套。 */
@Composable
private fun rememberBreathAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "drivingDot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drivingDotPulse",
    )
    return 0.45f + 0.55f * pulse
}
