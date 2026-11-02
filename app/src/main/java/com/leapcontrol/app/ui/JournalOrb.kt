package com.leapcontrol.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.leapcontrol.app.journal.AsrReply
import com.leapcontrol.app.journal.BaiduAsr
import com.leapcontrol.app.journal.CostStats
import com.leapcontrol.app.journal.QuickLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 球的直径。比之前小一圈，压在页面底部正中时不会挡内容。 */
private val OrbSize = 60.dp

/** 球的容器（含光晕）；呼吸动画放大到 1.06 倍时正好不出这个框。 */
private val OrbBoxSize = 84.dp

/** 输入弹层给球留的高度：球 60 + 上下留白，少一点球会压在卡片上。 */
private val OrbFloatHeight = 98.dp

/** 加号转成叉需要 45°，多转两圈半看起来更有「旋过去」的感觉（135 mod 90 = 45）。 */
private const val OrbSpinToClose = 135f

/**
 * 零记页面右下角的「灵感球」（位置可在个性化里改成居中 / 左下）。
 *
 * 记录这件事不该先想「进哪个页面、点哪个按钮」——想说什么就直接说：
 *  - **点一下**：中间那个「+」旋成「×」，下方弹出输入条，再点一次「×」收起；
 *  - **长按**：按住说话，松手即识别；
 *  - 球上方那句提示轮播着教你说什么（「今天充电花了 50」「今天点亮了杭州」）；
 *  - 记完球上方立刻回执「已记：杭州打卡 · 充电 ¥50」，消失后继续轮播。
 *
 * **造型：白瓷**。整颗球只用两种颜色 —— 面板底色（浅色接近白、深色为面板深灰）
 * 加一圈 1dp 发丝描边，加号用主题色。早先是写死的四色渐变（粉紫蓝青）加一圈紫光晕，
 * 换成冬季蓝、落叶橙之后那套粉紫永远差一档，所以整套砍掉：现在换任何主题，
 * 球都跟着 accent 走，不会出现「这一块颜色跟页面不搭」。
 *
 * 另外两点实现上的取舍：
 *  - 加号做得比常见尺寸更粗更大（见 [OrbPlusArm] / [OrbPlusStroke]）—— 白底上的细线
 *    在户外强光下几乎看不见，这是这个页面唯一的入口，必须一眼能找到；
 *  - 输入条弹出时球要**浮在弹层遮罩之上**（`zIndex`），否则会被 56% 的黑遮罩压成
 *    一团灰，那个「×」就既看不见也点不着了。
 */
@Composable
fun JournalOrb(
    runtime: AppRuntime,
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var showInput by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }
    /** 刚记完的回执：非空时顶替轮播提示，3.6 秒后自己消失。 */
    var flash by remember { mutableStateOf<String?>(null) }
    var hintIndex by remember { mutableIntStateOf(0) }

    /** 语音阶段：空闲 / 按住说话 / 正在识别。 */
    var voicePhase by remember { mutableIntStateOf(OrbVoice.IDLE) }
    /** 实时音量 0~1，驱动球体放大与外围波纹。 */
    var level by remember { mutableFloatStateOf(0f) }

    // 没有麦克风权限时长按先去要，授权后提示再按一次
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        flash = if (granted) "权限已给，按住球再说一次" else "没有麦克风权限，先用打字吧"
    }

    // 录满上限时兜底收尾：构造函数里拿不到 stopVoice，用一个可变槽位接上
    val stopVoiceRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val recorder = remember {
        VoiceRecorder(
            onLevel = { level = it },
            onLimitReached = { stopVoiceRef.value?.invoke() },
        )
    }

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

    // ---- 语音：全程在球上完成，不弹任何浮层 ----
    // 按住 → 球体随音量放大、外围扩散波纹、提示条写「松手就识别」；
    // 松手 → 停止录音、立刻送识别，识别结果直接落账，认不出才回填到输入框。

    fun startVoice() {
        if (showInput || voicePhase != OrbVoice.IDLE) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!recorder.start()) {
            flash = "麦克风被占用，稍后再试"
            return
        }
        level = 0f
        voicePhase = OrbVoice.LISTENING
    }

    fun stopVoice() {
        if (voicePhase != OrbVoice.LISTENING) return
        voicePhase = OrbVoice.RECOGNIZING
        val pcm = recorder.stop()
        level = 0f
        // 不到 0.15 秒基本是误触，别浪费一次请求，也别打断用户
        if (pcm.size < recorder.sampleRate * 2 * 15 / 100) {
            voicePhase = OrbVoice.IDLE
            flash = "按住球说话，松手就识别"
            return
        }
        scope.launch {
            val keys = runtime.baiduAsrKeys()
            val reply = if (keys == null) {
                AsrReply(error = "没有可用的识别凭据")
            } else {
                BaiduAsr.recognize(pcm, keys.first, keys.second)
            }
            voicePhase = OrbVoice.IDLE
            val text = reply.text?.trim().orEmpty()
            when {
                text.isEmpty() -> {
                    val why = reply.error?.takeIf { it.isNotBlank() }
                    flash = if (why == null) "没听清，再说一次" else "没听清：$why"
                }
                // 认出来了就直接记；认不出内容（比如只说了句闲话）才回填输入框让用户改
                commit(text) -> Unit
                else -> {
                    prefill = text
                    showInput = true
                    toast(context, "没太听懂，补两个字就行")
                }
            }
        }
    }

    // 录满 55 秒时由 recorder 回调这里收尾
    stopVoiceRef.value = { stopVoice() }

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
    // 呼吸幅度压到 ±2.5%：白瓷球是硬的，幅度大了会像果冻
    val breath by transition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    // 输入条展开时把加号旋成叉
    val spin by animateFloatAsState(
        targetValue = if (showInput) OrbSpinToClose else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "orbSpin",
    )
    // 录音时向外扩散的波纹相位
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)),
        label = "orbWave",
    )

    val listening = voicePhase == OrbVoice.LISTENING
    val recognizing = voicePhase == OrbVoice.RECOGNIZING

    // 加号颜色：浅色下就是主题色；深色下提亮一档，否则冬季蓝那类深色调压在
    // 深灰面板上会糊成一团，远看就是个空白球。
    val plusColor = if (C.isDark) lerpColor(C.accent, Color.White, 0.24f) else C.accent
    // 说话时把底色往主题色里揉一点当作「在听」的反馈；不整颗变彩，那又会变回贴纸
    val ballBg by animateColorAsState(
        targetValue = if (listening) lerpColor(C.panelBg, plusColor, 0.16f) else C.panelBg,
        animationSpec = tween(220),
        label = "orbBg",
    )

    // 提示条：语音优先于回执，回执优先于轮播
    val hint = when {
        listening -> "在听…松手就识别"
        recognizing -> "正在识别…"
        flash != null -> flash!!
        else -> QuickLog.HINTS[hintIndex % QuickLog.HINTS.size]
    }

    Column(
        // 球浮在输入条与遮罩之上：不然那个「×」会被 56% 的黑压成灰点
        modifier = modifier
            .zIndex(2f)
            // 输入条弹出时键盘会顶上来，球要跟着一起上移，否则被键盘盖住
            .then(if (showInput) Modifier.imePadding() else Modifier),
        horizontalAlignment = align,
    ) {
        // 输入条展开时收起提示条：这时候提示没用了，还会顶在卡片边上碍事
        if (!showInput) {
            OrbHint(text = hint, emphasized = flash != null || listening || recognizing)
            Spacer(Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .size(OrbBoxSize)
                .graphicsLayer {
                    // 说话时球随音量涨起来（最多 18%），比单纯呼吸更跟手
                    val extra = if (listening) 1f + level * 0.18f else 1f
                    scaleX = breath * extra
                    scaleY = breath * extra
                }
                .pointerInput(Unit) {
                    // 按住 320ms 判定为「长按说话」；在此之前松手算单击（展开/收起输入条）。
                    // key 必须是 Unit：绑上 voicePhase 会让每次状态变化重启手势，
                    // 一开录就被取消，等于按下即松手。
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                        val timedOut = withTimeoutOrNull(OrbLongPressMs) {
                            up = waitForUpOrCancellation()
                            true
                        } == null
                        if (timedOut) {
                            // 长按成立：开始录音，等松手再收尾
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            startVoice()
                            waitForUpOrCancellation()
                            stopVoice()
                        } else if (up != null) {
                            // 短按：切输入条
                            if (showInput) showInput = false else { prefill = ""; showInput = true }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (!listening) return@Canvas
                val outer = size.minDimension / 2f
                // 录音时的扩散波纹：三圈错开相位，用主题色、压得很淡，
                // 只是「它在听」的提示，不能盖过球体本身
                for (i in 0..2) {
                    val p = (wave + i * 0.33f) % 1f
                    drawCircle(
                        color = C.accent.copy(alpha = (1f - p) * 0.30f),
                        radius = outer * (0.70f + p * 0.30f),
                        center = center,
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(OrbSize)
                    // 极淡的投影：白球压在白卡上，没有这点影子就只是一枚贴纸；
                    // 透明度压到 10% 左右，暗色主题下自然看不出，也就不需要分叉
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.10f),
                    )
                    .clip(CircleShape)
                    .background(ballBg)
                    .border(1.dp, C.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val c = center
                    val arm = r * OrbPlusArm
                    val thick = r * OrbPlusStroke
                    // 加号：两条圆头短线；旋转 135° 后正好是叉
                    rotate(degrees = spin, pivot = c) {
                        drawLine(
                            color = plusColor,
                            start = Offset(c.x - arm, c.y),
                            end = Offset(c.x + arm, c.y),
                            strokeWidth = thick,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = plusColor,
                            start = Offset(c.x, c.y - arm),
                            end = Offset(c.x, c.y + arm),
                            strokeWidth = thick,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }

    if (showInput) {
        QuickLogSheet(
            // 压在遮罩与卡片之上而不遮住球自己：球在 zIndex 2
            modifier = Modifier.zIndex(1f),
            initial = prefill,
            onDismiss = { showInput = false },
            onSubmit = { text ->
                if (commit(text)) showInput = false
                else toast(context, "没听懂：说个金额，或者说出城市名")
            },
        )
    }
}

/**
 * 加号两臂的半长（相对球半径）。
 *
 * 0.38 → 0.46：加号横向占球径的 92%，60dp 球上约 27.6dp。
 * 原来那版细一号，白底上的主题色细线在户外强光下几乎看不出有个加号。
 */
private const val OrbPlusArm = 0.46f

/**
 * 加号笔画粗细（相对球半径）。
 *
 * 0.17 太细（白底上像根线）→ 0.21 又偏粗（实机看着像个厚十字）→ 收到 0.18。
 * 60dp 球上约 5.4dp，比常规图标略粗一点，但两臂相交处还留着加号该有的空隙。
 */
private const val OrbPlusStroke = 0.18f

/** 灵感球停在页面的哪个位置。默认右下，个性化里可改成居中 / 左下。 */
object OrbPosition {
    const val RIGHT = 0
    const val CENTER = 1
    const val LEFT = 2

    fun isValid(value: Int): Boolean = value == RIGHT || value == CENTER || value == LEFT
}

/** 按住多久算「长按说话」。短于这个时间松手就是单击（收/放输入条）。 */
private const val OrbLongPressMs = 320L

/** 语音的三个阶段：球上直接完成，不弹任何浮层。 */
private object OrbVoice {
    const val IDLE = 0
    const val LISTENING = 1
    const val RECOGNIZING = 2
}

/** 球上方那句提示：轮播时是灰字，刚记完的回执换成主题色。 */
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

/**
 * 记录输入弹层：打字 + 实时预览 + 一键记录。
 *
 * 语音不在这里 —— 长按灵感球直接在球上说话（松手即识别），
 * 所以这一层只负责「打字」这一件事，右侧不再放语音按钮。
 */
@Composable
private fun QuickLogSheet(
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxSize()
            // 不铺黑色遮罩：一块半透明方幕压在季节背景上很显脏，
            // 弹层自己就是实心卡片，靠它和页面区分就够了。clickable 留着，点空白处仍能收起。
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .consumeClicks()
                // 底部给球留位置：球浮在卡片下方，卡片压上去就把它埋了
                .padding(bottom = OrbFloatHeight)
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
                JournalInput(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "今天充电花了 50 / 今天点亮了杭州",
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    imeAction = ImeAction.Done,
                    onDone = { if (input.isNotBlank()) onSubmit(input) },
                )
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
