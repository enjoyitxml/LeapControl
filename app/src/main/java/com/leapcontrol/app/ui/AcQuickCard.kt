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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R

/**
 * 快捷控车下方的「空调概览条」。
 *
 * 版式来自设计稿（1080×289 的三倍图，折算到 dp），但高度在实机上偏薄、和上方圆钮不协调，
 * 所以整条按 ~1.2 倍等比撑高（宽度不变，横向比例不动）：
 *   - 整条：高 72dp、圆角 8dp、左右内边距 24dp
 *   - 左：`◀ 24° ▶` —— 温度大字 32sp，两侧各一个箭头用来减 / 加
 *   - 中：「目前车内温度 32.5℃」13.5sp 次要文字，在剩余宽度内居中
 *   - 右：34dp 圆形按钮 + 22dp 四叶风扇（**唯一**的空调开关）
 *
 * 温度是「待下发」的：车端没有单独的调温指令，温度只是「开空调」指令里的一个字段，
 * 所以空调关着时点箭头只改界面上的数字、不发任何指令；点右侧风扇开关时才带着这个
 * 温度下发 `开空调`。空调开着时点箭头才会立即下发（那就是在调温）。详见 [AcTempDraft]。
 *
 * 交互：风扇灰色＝空调关闭，点一下开空调；开启后圆底淡绿、图标变主题绿并持续旋转。
 * 点下去立刻按「意图」点亮（车端 acSwitch 上报有几秒延迟，否则会觉得点了没反应），
 * 指令结束回拉车况后以真实状态为准。
 */
@Composable
fun AcQuickCard(
    status: CarStatus?,
    busy: Boolean,
    /** 点风扇开关：参数是「目标状态」与「要下发的设定温度」。关闭时温度会被忽略。 */
    onToggle: (Boolean, Int) -> Unit,
    /** 空调**已开启**时改温度，立即下发。关闭态不会调到这个回调。 */
    onTempChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current

    // 上一次点击的意图：只在指令进行中（busy）用来先点亮，指令结束就不再参考
    var lastIntentOn by remember { mutableStateOf(false) }
    val acOn = status?.acOn == true || (busy && lastIntentOn)

    val remote = AcTempDraft.parse(status?.acTargetTemp)

    // 关机态用户调出来的预设温度；空调真正开起来之后就没用了，清掉回到车端真值
    var pendingTemp by remember { mutableStateOf<Int?>(null) }
    val target = AcTempDraft.shown(acOn = acOn, remote = remote, pending = pendingTemp)

    // 开机成功后车端会回报同一个温度：确认到账就把草稿交还给车端，
    // 否则下次关空调时这颗旧草稿还会盖掉车端值
    LaunchedEffect(acOn, remote, pendingTemp) {
        if (acOn && pendingTemp != null && remote == pendingTemp) pendingTemp = null
    }

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

    // 箭头：关机态只改草稿，开机态才下发。busy 期间一律锁住，避免连点把指令排队
    val onStep: (Int) -> Unit = { delta ->
        val next = AcTempDraft.step(target, delta)
        if (next != target) {
            if (AcTempDraft.sendOnArrow(acOn)) onTempChange(next) else pendingTemp = next
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(8.dp))
            // 与胎压 / 近7天 / 相伴卡同一套卡片底：白里带一点主题色，描边也用主题色
            .background(C.panelBg, RoundedCornerShape(8.dp))
            // 左右内边距一致：左边是箭头图标框、右边是 34dp 圆形按钮，取同一数值后
            // 两端到卡片边缘的视觉留白才对称
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // 左：◀ 设定温度 ▶
        // 数字给一个最小宽度并在槽内居中：`°` 是上标字形，右侧天然比左侧空，
        // 直接靠字形排会让两颗箭头到数字的间隙一宽一窄；定宽之后两侧间隙都是 3.5dp，
        // 箭头的位置也固定，「16°」→「32°」不会左右跳
        TempArrow(
            icon = Icons.Filled.KeyboardArrowLeft,
            description = "降低设定温度",
            enabled = !busy && AcTempDraft.canStep(target, -1),
            onClick = { onStep(-1) },
        )
        Spacer(Modifier.width(TEMP_SIDE_GAP))
        Text(
            text = "$target°",
            modifier = Modifier.widthIn(min = TEMP_SLOT_WIDTH),
            color = C.textPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.6).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.width(TEMP_SIDE_GAP))
        TempArrow(
            icon = Icons.Filled.KeyboardArrowRight,
            description = "升高设定温度",
            enabled = !busy && AcTempDraft.canStep(target, 1),
            onClick = { onStep(1) },
            // `°` 是上标字形，右侧自带一小段空白，箭头按包围盒排会显得离数字更远；
            // 往左收 2dp 之后，两侧的视觉间隙才一致（只动绘制位置，布局占位不变）
            modifier = Modifier.offset(x = (-2).dp),
        )

        Spacer(Modifier.width(8.dp))
        // 中：目前车内温度（占剩余宽度并居中；窄屏优先省略，而不是把风扇挤出去）
        Text(
            text = "目前车内温度 $indoor℃",
            color = C.textSecondary,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        // 右：风扇开关（唯一的下发入口）
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (acOn) C.accent.copy(alpha = 0.16f) else fanCircleBg(C.isDark))
                .clickable(enabled = !busy) {
                    lastIntentOn = !acOn
                    onToggle(!acOn, target)
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
                    .size(22.dp)
                    .rotate(fanAngle),
            )
        }
    }
}

/**
 * 温度加减箭头：28dp 触控框，视觉上只有 20dp 的箭头本身。
 *
 * 到 16 / 32 边界时整颗变淡并锁死点击（而不是点了没反应），
 * 让用户看得出「到底了」。
 */
@Composable
private fun TempArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .then(modifier)
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) C.textPrimary.copy(alpha = 0.62f)
            else C.textSecondary.copy(alpha = 0.26f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 温度数字与两侧箭头之间的固定间隙（左、右各一份，必须一致）。 */
private val TEMP_SIDE_GAP = 2.dp

/**
 * 温度数字的最小宽度：让两颗箭头的位置不受字形宽度影响（不同机型字体下「32°」宽 44~50dp）。
 * 用 widthIn(min) 而不是 width：字体特别宽时宁可整条串宽一点，也不能把 `°` 裁掉。
 */
private val TEMP_SLOT_WIDTH = 48.dp

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
