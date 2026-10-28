package com.leapcontrol.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlin.math.cos
import kotlin.math.sin

/**
 * 语音记账浮层（Siri 风格）。
 *
 * 为什么不复用系统语音对话框：
 *  - `RecognizerIntent` 拉起的是**别的应用**的一个 Activity，界面完全不受控，
 *    既没有实时声量、也没法做成球；
 *  - 这里改用进程内的 [SpeechRecognizer]，`onRmsChanged` 拿声量、
 *    `onPartialResults` 拿半截文本，才能自己画一个「在听」的球。
 *
 * 代价是要自己申请 `RECORD_AUDIO`（Manifest 里已声明，进浮层时按需请求）。
 */
@Composable
fun JournalVoiceSheet(
    onCancel: () -> Unit,
    onTypeInstead: (String) -> Unit,
    onResult: (String) -> Unit,
) {
    // 用全屏 Dialog 而不是页面内的 Box：语音遮罩要连底部导航栏一起盖住，
    // 挂在页面里会被 Scaffold 的 bottomBar 挡掉一截，Siri 那种沉浸感就没了。
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        VoiceSheetContent(
            onCancel = onCancel,
            onTypeInstead = onTypeInstead,
            onResult = onResult,
        )
    }
}

@Composable
private fun VoiceSheetContent(
    onCancel: () -> Unit,
    onTypeInstead: (String) -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? ComponentActivity

    var partial by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }

    val onResultLatest = rememberUpdatedState(onResult)
    val onTypeLatest = rememberUpdatedState(onTypeInstead)

    val session = remember {
        VoiceSession(
            context = context,
            onPartial = { partial = it },
            onLevel = { level = it },
            onResult = { text -> onResultLatest.value(text) },
            onFail = { message ->
                listening = false
                level = 0f
                error = message
            },
            onListening = { listening = it },
        )
    }

    DisposableEffect(Unit) {
        onDispose { session.release() }
    }

    // 遮罩是全黑的，浅色主题下深色状态栏图标会看不见 —— 临时改成白图标，退出时还原
    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) controller?.isAppearanceLightStatusBars = wasLight }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            error = null
            session.start()
        } else {
            onTypeLatest.value("没有麦克风权限，先用打字吧")
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            !granted -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            !session.available -> onTypeLatest.value("这台手机没有语音识别，直接打字吧")
            else -> session.start()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .consumeClicks()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    error != null -> "没听清"
                    listening -> "我在听"
                    else -> "正在整理"
                },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(30.dp))

            SiriOrb(
                level = if (error == null) level else 0f,
                modifier = Modifier.size(176.dp),
            )

            Spacer(Modifier.height(30.dp))

            // 实时文本：识别过程中逐字长出来，静止时给一句示例
            Text(
                text = partial.ifBlank {
                    error ?: "比如「今天充电花了 50」"
                },
                color = if (error != null) Color.White.copy(alpha = 0.62f) else Color.White,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp),
            )

            Spacer(Modifier.height(34.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VoiceButton(
                    text = if (error == null) "取消" else "改用打字",
                    primary = false,
                    onClick = {
                        if (error == null) onCancel() else onTypeLatest.value(error.orEmpty().ifBlank { partial })
                    },
                )
                VoiceButton(
                    text = if (error == null) "说完了" else "再试一次",
                    primary = true,
                    onClick = {
                        if (error == null) {
                            session.finish()
                        } else {
                            error = null
                            partial = ""
                            session.start()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val accent = LocalAppColors.current.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (primary) accent else Color.White.copy(alpha = 0.14f))
            .border(
                1.dp,
                if (primary) Color.Transparent else Color.White.copy(alpha = 0.20f),
                RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 那个会呼吸、会跟着声音起伏的球。
 *
 * 三层叠出来：外圈柔光（有声音时张开）→ 旋转的扫掠渐变球体 → 三圈错相波纹。
 * 振幅来自识别服务的 `onRmsChanged`，已经过 [animateFloatAsState] 平滑，
 * 否则每 50ms 一个跳变，看着像在抖。
 */
@Composable
private fun SiriOrb(level: Float, modifier: Modifier = Modifier) {
    val accent = LocalAppColors.current.accent
    val transition = rememberInfiniteTransition(label = "voiceOrb")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5600, easing = LinearEasing)),
        label = "spin",
    )
    val breath by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val smooth by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(160),
        label = "level",
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f
        // 球只占 72%：剩下的留给外圈光晕，全占满会顶到遮罩边缘
        val ballR = r * 0.72f * (1f + smooth * 0.10f)
        val c = center

        // 1) 外圈柔光：声音越大越亮越开
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.26f + smooth * 0.22f), Color.Transparent),
                center = c,
                radius = ballR * 1.62f,
            ),
            radius = ballR * 1.55f,
            center = c,
        )

        // 2) 球内：先铺一个左上亮、右下深的底色把体积立起来，
        //    再叠四团绕着球心转的彩云 —— 只有扫掠渐变的话，中心会露出一个明显的汇合尖角
        val circle = Path().apply { addOval(Rect(center = c, radius = ballR)) }
        clipPath(circle) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerpColor(accent, Color.Black, 0.38f),
                        lerpColor(accent, Color.Black, 0.74f),
                    ),
                    center = Offset(c.x - ballR * 0.22f, c.y - ballR * 0.28f),
                    radius = ballR * 1.55f,
                ),
                radius = ballR,
                center = c,
            )
            val rad = Math.toRadians(spin.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            BLOBS.forEach { (dx, dy, color) ->
                val bx = c.x + (dx * cosA - dy * sinA) * ballR
                val by = c.y + (dx * sinA + dy * cosA) * ballR
                // 三档 alpha 而不是两档：两档会留下一个亮核，像灯泡而不是云
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = 0.62f),
                            0.45f to color.copy(alpha = 0.34f),
                            1f to color.copy(alpha = 0f),
                        ),
                        center = Offset(bx, by),
                        radius = ballR * 1.26f,
                    ),
                    radius = ballR * 1.26f,
                    center = Offset(bx, by),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(c.x - ballR * 0.34f, c.y - ballR * 0.38f),
                    radius = ballR * 0.52f,
                ),
                radius = ballR,
                center = c,
            )
        }

        // 3) 上沿细高光弧（整圈会像贴纸）
        drawArc(
            color = Color.White.copy(alpha = 0.14f),
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(c.x - ballR + 1.dp.toPx(), c.y - ballR + 1.dp.toPx()),
            size = Size((ballR - 1.dp.toPx()) * 2f, (ballR - 1.dp.toPx()) * 2f),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** 球内四团彩云的位置（以球半径为单位的偏移）。 */
private val BLOBS = listOf(
    Triple(-0.30f, -0.36f, Color(0xFF4C6FFF)),
    Triple(0.34f, -0.26f, Color(0xFF17D6C4)),
    Triple(0.18f, 0.34f, Color(0xFFA24CFF)),
    Triple(-0.34f, 0.16f, Color(0xFFFF5FA2)),
)

/**
 * 进程内的语音识别会话。
 *
 * 生命周期由 Composable 的 `DisposableEffect` 管：进浮层创建、退出销毁。
 * 单独抽成类是为了让「识别服务」这件事跟重绘彻底分开 —— 它不持有任何 Compose 状态，
 * 只往外回调。
 */
private class VoiceSession(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFail: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean
        get() = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    fun start() {
        release()
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (created == null) {
            onFail("这台手机没有语音识别服务，直接打字吧")
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                // 实测范围大致 -2dB（安静）~ 10dB（贴嘴说话），映射到 0~1
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = onListening(false)

            override fun onError(error: Int) {
                onListening(false)
                onFail(humanError(error))
            }

            override fun onResults(results: Bundle?) {
                onListening(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) onFail("没听清，再说一次吧") else onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            // 半截结果：球下面那行字要跟着说话长出来，不能等整句结束才出现
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            // 停顿 1.5 秒才判「说完了」；再短会把人换气当成结束
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
        runCatching { created.startListening(intent) }
            .onFailure { onFail("语音识别启动失败，直接打字吧") }
    }

    /** 用户点「说完了」：不再收音，等识别服务把最终结果送回来。 */
    fun finish() {
        runCatching { recognizer?.stopListening() }
    }

    fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}

private fun humanError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> "没听清，再说一次吧"

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> "识别服务连不上网络"

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
    SpeechRecognizer.ERROR_AUDIO -> "麦克风被占用了，稍后再试"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务正忙，稍后再试"
    SpeechRecognizer.ERROR_CLIENT -> "识别被打断了，再说一次吧"
    else -> "语音识别没成功，打字更快"
}
