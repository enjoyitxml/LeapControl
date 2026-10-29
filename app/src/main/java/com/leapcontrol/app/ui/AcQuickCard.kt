package com.leapcontrol.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R

/**
 * 快捷控车下方的「空调概览条」。
 *
 * 版式（从左到右，一条通栏）：
 *   - 最左：「目前车内温度」标签 + 实时读数（两行小字，左对齐）
 *   - 中部弹性留白，把右边一组推到右侧
 *   - 右侧成组：`−` 设定温度 `+`（温度大字 32sp，按 1℃ 调，范围由 [AcTempDraft] 管）
 *   - 最右：电源开关（**唯一**的下发入口），36dp 圆形按钮 + 22dp 四叶风扇
 *
 * 尺寸按卡片**实际内宽**等比缩放（[BASE_INNER_WIDTH] 为基准，宽屏略放大、窄屏略收），
 * 这样 320dp 的小屏到 430dp 的大屏上，温度组与两侧元素的占比都保持一致，不会出现
 * 大屏显得空旷、窄屏挤成一团。
 *
 * 温度是「待下发」的：车端没有单独的调温指令，温度只是「开空调」指令里的一个字段，
 * 所以空调关着时点加减号只改界面上的数字、不发任何指令；点右侧电源开关时才带着这个
 * 温度下发 `开空调`。空调开着时点加减号才会立即下发（那就是在调温）。详见 [AcTempDraft]。
 *
 * 交互：电源灰色＝空调关闭，点一下开空调；开启后圆底淡绿、图标变主题绿并持续旋转。
 * 点下去立刻按「意图」点亮（车端 acSwitch 上报有几秒延迟，否则会觉得点了没反应），
 * 指令结束回拉车况后以真实状态为准。
 *
 * 点卡片空白处（不含加减号与电源钮）会进入全屏空调页 [AcScreen]，
 * 车内温度块右侧那个 `›` 就是「这里能点进去」的提示。
 */
@Composable
fun AcQuickCard(
    status: CarStatus?,
    busy: Boolean,
    /** 点电源开关：参数是「目标状态」与「要下发的设定温度」。关闭时温度会被忽略。 */
    onToggle: (Boolean, Int) -> Unit,
    /**
     * 界面上显示的设定温度。草稿状态由调用方（HomeScreen）持有：
     * 展开面板与这条卡片共用同一份温度，两处加减号才不会各调各的。
     */
    target: Int,
    /** 点加减号（±1℃）。是否立即下发由调用方按空调开关状态决定，见 [AcTempDraft]。 */
    onStep: (Int) -> Unit,
    /** 点卡片空白处：进入全屏空调页。 */
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current

    // 上一次点击的意图：只在指令进行中（busy）用来先点亮，指令结束就不再参考
    var lastIntentOn by remember { mutableStateOf(false) }
    val acOn = status?.acOn == true || (busy && lastIntentOn)

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BAR_MIN_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            // 与胎压 / 近7天 / 相伴卡同一套卡片底：白里带一点主题色
            .background(C.panelBg, RoundedCornerShape(8.dp))
            // 整条卡片都可点：子级按钮（加减号 / 电源）自己消费点击，
            // 只有点在空白处才会落到这里进入全屏空调页
            .clickable(onClick = onOpen)
            // 左右内边距一致：最左是车内温度文字、最右是电源圆钮，取同一数值后
            // 两端到卡片边缘的留白才对称
            .padding(horizontal = BAR_PAD_H),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 按内宽等比缩放：maxWidth 已是去掉左右内边距之后的宽度
        val k = (maxWidth.value / BASE_INNER_WIDTH).coerceIn(MIN_SCALE, MAX_SCALE)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 最左：目前车内温度。标签与读数分两行放，横向只占一个字块宽，
            // 把剩下的横向空间留给右侧的温度组，窄屏也不会把加号挤到卡片外
            Column(modifier = Modifier.widthIn(max = INDOOR_BLOCK_MAX * k)) {
                Text(
                    text = "目前车内温度",
                    color = C.textSecondary,
                    fontSize = (INDOOR_LABEL_SP * k).sp,
                    lineHeight = (13f * k).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (indoor == "--") "--" else "$indoor℃",
                    color = C.textPrimary,
                    fontSize = (INDOOR_VALUE_SP * k).sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (18f * k).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 进入二级页的提示：紧贴车内温度块右侧。
            // 点它和点卡片空白处等效（父级 clickable 已经覆盖这块区域）
            AcIcon(
                glyph = AcGlyph.ChevronRight,
                tint = C.textSecondary.copy(alpha = 0.7f),
                size = 17.dp,
                stroke = 1.9.dp,
                modifier = Modifier.padding(start = 4.dp),
            )

            // 中部弹性留白：没有它右侧那组会紧贴左边文字，读数一变长度整条就会抖
            Spacer(Modifier.weight(1f))

            // 减号
            TempStepButton(
                plus = false,
                enabled = !busy && AcTempDraft.canStep(target, -1),
                onClick = { onStep(-1) },
                touch = STEP_TOUCH * k,
                arm = STEP_ARM * k,
                label = "降低设定温度",
            )

            Spacer(Modifier.width(TEMP_SIDE_GAP))

            // 温度：数字 + 小号 `°` 分开放。
            // 不写成一个 "24°" 字符串，是因为 `°` 是全角字形：实墨只占右上角一小块，
            // 但 advance 占满一格，整串居中后右侧会空出一大片，看起来「减号贴数字、加号离得远」。
            // 拆开之后 `°` 紧贴数字右上，温度组的实际墨迹左右对称，两颗按钮的间隙才一致
            Row(
                modifier = Modifier.widthIn(min = TEMP_SLOT_WIDTH * k),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "$target",
                    color = C.textPrimary,
                    fontSize = (TEMP_FONT_SP * k).sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.6).sp,
                    maxLines = 1,
                )
                Text(
                    text = "°",
                    color = C.textPrimary,
                    fontSize = (TEMP_DEGREE_SP * k).sp,
                    fontWeight = FontWeight.Normal,
                    // 上标位置：跟数字顶端对齐再下压一点
                    modifier = Modifier.padding(top = TEMP_DEGREE_TOP * k),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(TEMP_SIDE_GAP))

            // 加号
            TempStepButton(
                plus = true,
                enabled = !busy && AcTempDraft.canStep(target, 1),
                onClick = { onStep(1) },
                touch = STEP_TOUCH * k,
                arm = STEP_ARM * k,
                label = "升高设定温度",
            )

            Spacer(Modifier.width(POWER_GAP * k))

            // 最右：电源开关（唯一的下发入口）
            AcPowerButton(
                acOn = acOn,
                busy = busy,
                angle = fanAngle,
                size = POWER_SIZE * k,
                iconSize = POWER_ICON * k,
                onClick = {
                    lastIntentOn = !acOn
                    onToggle(!acOn, target)
                },
            )
        }
    }
}

/**
 * 电源开关：圆形底 + 四叶风扇，空调开启时淡绿底、主题色图标并持续旋转。
 *
 * 这是空调唯一的「下发」入口：关→开时会把界面上的设定温度一起带下去。
 * busy 期间不可点，图标半透明表示指令在途。
 *
 * 首页空调条与全屏空调页共用（后者传更大的 size）。
 */
@Composable
internal fun AcPowerButton(
    acOn: Boolean,
    busy: Boolean,
    angle: Float,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (acOn) C.accent.copy(alpha = 0.16f) else fanCircleBg(C.isDark))
            .clickable(enabled = !busy, onClick = onClick),
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
                .size(iconSize)
                .rotate(angle),
        )
    }
}

/**
 * 温度加减按钮：圆形触控框 + 手绘的 `−` / `+`。
 *
 * 不用 Material 的箭头图标：加号与减号的视觉重量本来就不一样（一个是单笔画、一个是十字），
 * 手绘能保证两者线宽、臂长、端点圆角完全一致，摆在一起不会一重一轻。
 *
 * 到 16 / 32 边界时整颗变淡并锁死点击（而不是点了没反应），让用户看得出「到底了」。
 */
@Composable
internal fun TempStepButton(
    plus: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    touch: Dp,
    arm: Dp,
    label: String,
) {
    val C = LocalAppColors.current
    // 符号刻意比温度数字轻一档（灰，而不是和数字同黑）：
    // 同黑细线紧挨数字时，减号会被读成负号；变灰之后它更像「按钮上的图形」
    val tint = if (enabled) C.textPrimary.copy(alpha = 0.46f)
    else C.textPrimary.copy(alpha = 0.16f)

    Box(
        modifier = Modifier
            .size(touch)
            .clip(CircleShape)
            // 一层极淡的圆底：让 `−` / `+` 看起来是两颗按钮，而不是散在数字边上的符号
            // （纯符号贴着数字时，减号很容易被读成负号）。用 textPrimary 的低透明度做底，
            // 浅色主题下是淡灰、深色主题下是淡白，两套主题都不用单独配色
            .background(
                color = C.textPrimary.copy(alpha = if (enabled) 0.055f else 0.022f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            // Canvas 本身不带描述，补一个，读屏能念出「降低/升高设定温度」
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(arm)) {
            val c = center
            val half = size.minDimension / 2f
            drawLine(
                color = tint,
                start = Offset(c.x - half, c.y),
                end = Offset(c.x + half, c.y),
                strokeWidth = STEP_STROKE.toPx(),
                cap = StrokeCap.Round,
            )
            if (plus) {
                drawLine(
                    color = tint,
                    start = Offset(c.x, c.y - half),
                    end = Offset(c.x, c.y + half),
                    strokeWidth = STEP_STROKE.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 卡片左右内边距：两端一致，电源钮与温度文字到卡片边缘的留白才对称。 */
private val BAR_PAD_H = 14.dp

/** 整条最小高度。 */
private val BAR_MIN_HEIGHT = 74.dp

/**
 * 缩放基准：393dp 屏下的卡片内宽（393 − 页面左右 22×2 − 卡片内边距 14×2 = 321dp）。
 * 元素尺寸都拿「实际内宽 / 这个值」当系数，保证不同屏宽下占比一致。
 */
private const val BASE_INNER_WIDTH = 321f

/** 缩放系数区间：收窄只是微调，避免大屏元素被放大到失真、小屏文字缩得看不清。 */
private const val MIN_SCALE = 0.94f
private const val MAX_SCALE = 1.10f

/** 电源圆钮直径与其中风扇图标的尺寸（基准，会乘缩放系数）。 */
private val POWER_SIZE = 36.dp
private val POWER_ICON = 23.dp

/** 电源钮与温度组之间的距离。 */
private val POWER_GAP = 10.dp

/** 加减号的触控半径与符号臂长（触控框比符号大一圈，手指好点）。 */
private val STEP_TOUCH = 28.dp
private val STEP_ARM = 12.dp

/** 加减号的线宽：固定 2dp，不随缩放变，否则小屏上的十字会糊成一点。 */
private val STEP_STROKE = 2.dp

/**
 * 温度数字与两侧加减号之间的固定间隙（左、右各一份，必须一致）。
 *
 * 不能太窄：减号离数字太近会被读成负号（`-24°`），所以留出 7dp，
 * 让 `−` / `+` 看起来是两颗独立的按钮，而不是数字的一部分。
 */
private val TEMP_SIDE_GAP = 6.dp

/**
 * 温度组的最小宽度：让两颗加减号的位置不受字形宽度影响。
 * 数字三档（16 / 24 / 32）等宽，所以槽固定；用 widthIn(min) 而不是 width，
 * 是为了在字体特别宽时宁可整条串宽一点，也不把数字裁掉。
 */
private val TEMP_SLOT_WIDTH = 44.dp

/** 温度大字字号（会乘缩放系数）。 */
private const val TEMP_FONT_SP = 32f

/** 温度单位 `°` 的字号：比数字小一号，才有上标的手感（会乘缩放系数）。 */
private const val TEMP_DEGREE_SP = 18f

/** `°` 相对数字顶端下压的距离，让它落在大写数字的肩部。 */
private val TEMP_DEGREE_TOP = 2.dp

/** 「目前车内温度」标签字号（次要灰，会乘缩放系数）。 */
private const val INDOOR_LABEL_SP = 10.5f

/** 车内温度读数字号（主色，比标签大一档，会乘缩放系数）。 */
private const val INDOOR_VALUE_SP = 15f

/**
 * 左侧车内温度块的宽度上限：「目前车内温度」是 6 个全角字，
 * 限住宽度既能保证标签不换行，异常长的读数（如 "-12.5℃"）走省略号而不是把温度组顶出卡片。
 */
private val INDOOR_BLOCK_MAX = 78.dp

/** 关闭态圆形按钮的底色：浅色主题用略深于卡片的浅灰，深色主题用一层淡白压出圆盘。 */
private fun fanCircleBg(isDark: Boolean): Color =
    if (isDark) Color(0x1FFFFFFF) else Color(0xFFF1F3F2)

/** 关闭态风扇图标色（设计稿实测 #9A9C9B） */
private val FAN_GRAY = Color(0xFF9A9C9B)

/**
 * 车内温度信号有时带单位、有时是纯数字（如 "32.5" / "32.5°C" / "32.5℃"），
 * 统一去掉单位，交给调用处拼「℃」，避免出现「32.5°C℃」。
 */
internal fun normalizeTemp(raw: String?): String {
    if (raw.isNullOrBlank()) return "--"
    return raw.trim()
        .removeSuffix("℃")
        .removeSuffix("°C")
        .removeSuffix("°")
        .removeSuffix("C")
        .trim()
        .ifEmpty { "--" }
}
