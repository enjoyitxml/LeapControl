package com.leapcontrol.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.QuickLog
import kotlinx.coroutines.delay

/**
 * 零记首页右下角的「灵感球」。
 *
 * 记录这件事不该先想「进哪个页面、点哪个按钮」——想说什么就直接说：
 *  - **点一下**：弹出输入框，打字记；
 *  - **长按**：进 Siri 式语音浮层，说话记；
 *  - 球上方那句提示轮播着教你说什么（「今天充电花了 50」「今天点亮了杭州」）；
 *  - 记完球上方立刻回执「已记：杭州打卡 · 充电 ¥50」，消失后继续轮播。
 *
 * 呼吸动画只在球这一个小 Canvas 里跑，不牵动整页重组。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournalOrb(runtime: AppRuntime, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var showInput by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }
    /** 刚记完的回执：非空时顶替轮播提示，3.6 秒后自己消失。 */
    var flash by remember { mutableStateOf<String?>(null) }
    var hintIndex by remember { mutableIntStateOf(0) }

    // 把一句话落的账、插的旗真正写进去；返回是否认出了内容
    fun commit(raw: String): Boolean {
        val plan = QuickLog.parse(raw) ?: return false
        val journal = runtime.journal
        val parts = mutableListOf<String>()
        plan.checkin?.let { action ->
            if (journal.checkin(action.city, action.date)) parts += "${action.city.name}打卡"
            else parts += "${action.city.name}之前打过卡"
        }
        plan.cost?.let { parsed ->
            journal.addCost(parsed.category, parsed.amount, parsed.date, parsed.note)
            parts += "${parsed.category.label} ¥${CostStats.money(parsed.amount)}"
        }
        flash = "已记：${parts.joinToString(" · ")}"
        return true
    }

    LaunchedEffect(flash) {
        if (flash != null) {
            delay(3600)
            flash = null
        } else {
            while (true) {
                delay(4200)
                hintIndex += 1
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "orbBreath")
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val halo by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo",
    )

    val hint = flash ?: QuickLog.HINTS[hintIndex % QuickLog.HINTS.size]

    Column(
        modifier = modifier,
        // 球贴在右下角，提示条要跟着往右靠，否则会被挤到屏幕中间显得脱节
        horizontalAlignment = Alignment.End,
    ) {
        OrbHint(text = hint, emphasized = flash != null)
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                }
                .combinedClickable(
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showVoice = true
                    },
                    onClick = {
                        prefill = ""
                        showInput = true
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                // 外层光晕：跟呼吸同相，球「涨」的时候光也涨
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(C.accent.copy(alpha = 0.26f * halo), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r * 0.98f,
                    center = center,
                )
            }
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // 球体全部画在 Canvas 里：径向渐变的高光点在左上方，
                // 用 Modifier.background(brush) 只能给像素坐标，尺寸一变就跑偏。
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    // stops 必须写死：只给三个颜色时渐变半径会摊得太大，球看起来是一块纯色
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to lerpColor(C.accent, Color.White, 0.45f),
                                0.42f to C.accent,
                                1f to lerpColor(C.accent, Color.Black, 0.32f),
                            ),
                            center = Offset(size.width * 0.34f, size.height * 0.26f),
                            radius = radius * 1.55f,
                        ),
                        radius = radius,
                        center = center,
                    )
                    // 上沿一道细高光：整圈白描边会显得像贴纸
                    val hairline = 1.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.30f),
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius + hairline, center.y - radius + hairline),
                        size = Size((radius - hairline) * 2f, (radius - hairline) * 2f),
                        style = Stroke(width = hairline, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }

    if (showInput) {
        QuickLogSheet(
            initial = prefill,
            onDismiss = { showInput = false },
            onVoice = {
                showInput = false
                showVoice = true
            },
            onSubmit = { text ->
                if (commit(text)) showInput = false
                else toast(context, "没听懂：说个金额，或者说出城市名")
            },
        )
    }

    if (showVoice) {
        JournalVoiceSheet(
            onlineKeys = runtime.baiduAsrKeys(),
            onSaveKeys = { api, secret -> runtime.setBaiduAsrKeys(api, secret) },
            onCancel = { showVoice = false },
            onTypeInstead = { reason ->
                showVoice = false
                prefill = ""
                if (reason.isNotBlank()) toast(context, reason)
                showInput = true
            },
            onResult = { text ->
                showVoice = false
                // 认不出来就把听到的原话填进输入框，让用户改两个字就行
                if (!commit(text)) {
                    prefill = text
                    showInput = true
                    toast(context, "没太听懂，补两个字就行")
                }
            },
        )
    }
}

/** 画出朝下的小三角（提示条与球之间的引导）。 */
@Composable
private fun OrbHint(text: String, emphasized: Boolean) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    Crossfade(
        targetState = text to emphasized,
        animationSpec = tween(320),
        label = "orbHint",
    ) { (value, strong) ->
        Box(
            modifier = Modifier
                .widthIn(max = 250.dp)
                .clip(shape)
                // 实心底 + 发丝描边：半透明白压在季节渐变上会泛暖灰，一屏里就有两种白
                .background(C.panelBg)
                .border(1.dp, if (strong) C.accent.copy(alpha = 0.55f) else C.border, shape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                value,
                color = if (strong) C.accent else C.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 记录输入弹层：打字 + 实时预览 + 一键记录。 */
@Composable
private fun QuickLogSheet(
    initial: String,
    onDismiss: () -> Unit,
    onVoice: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    var input by remember { mutableStateOf(initial) }
    val plan = remember(input) { QuickLog.parse(input) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // 稍等一拍再抢焦点：底部弹层正在上滑，立刻请求焦点键盘会跟动画打架
        delay(120)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIALOG_SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .consumeClicks()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(C.dialogBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("记一句", color = C.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("说到点上就行", color = C.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    JournalInput(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "今天充电花了 50 / 今天点亮了杭州",
                        modifier = Modifier.focusRequester(focusRequester),
                        imeAction = ImeAction.Done,
                        onDone = { if (input.isNotBlank()) onSubmit(input) },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(C.accent.copy(alpha = 0.15f))
                        .border(1.dp, C.accent.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onVoice()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("语音", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    input.isBlank() -> "记账、打卡都能一句话说完，认得出日期和分类"
                    plan == null -> "还没认出内容：补一个金额，或者说出城市名"
                    else -> "将记录：${plan.describe()}"
                },
                color = if (plan != null) C.accent else C.textSecondary,
                fontSize = 11.5.sp,
            )

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (plan == null) C.chipBg else C.accent)
                    .clickable {
                        if (plan == null) {
                            toast(context, "没听懂：说个金额，或者说出城市名")
                        } else {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onSubmit(input)
                        }
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "一键记录",
                    color = if (plan == null) C.textSecondary else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Compose 的 `lerp` 在 1.7 已弃用（改名 lerp 到 ui.graphics 的新重载），这里自己插值更稳。 */
internal fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}
