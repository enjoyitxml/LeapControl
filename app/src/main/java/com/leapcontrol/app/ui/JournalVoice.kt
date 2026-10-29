package com.leapcontrol.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
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
import com.leapcontrol.app.journal.BaiduAsr
import kotlinx.coroutines.launch
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
    /** 已保存的百度 Key；为 null 时进入「先配 Key」引导页，而不是硬走系统识别。 */
    onlineKeys: Pair<String, String>?,
    /** 用户在浮层里当场填好 Key 时回调，由调用方落盘。 */
    onSaveKeys: (String, String) -> Unit,
    onCancel: () -> Unit,
    onTypeInstead: (String) -> Unit,
    onResult: (String) -> Unit,
) {
    // Key 存在本地状态里：在浮层里填完就能立刻切到在线识别，不用退出去再进来
    var keys by remember { mutableStateOf(onlineKeys) }

    // 用全屏 Dialog 而不是页面内的 Box：语音遮罩要连底部导航栏一起盖住，
    // 挂在页面里会被 Scaffold 的 bottomBar 挡掉一截，Siri 那种沉浸感就没了。
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val current = keys
        if (current != null) {
            OnlineVoiceContent(
                apiKey = current.first,
                secretKey = current.second,
                onEditKeys = {
                    BaiduAsr.forgetToken()
                    keys = null
                },
                onCancel = onCancel,
                onTypeInstead = onTypeInstead,
                onResult = onResult,
            )
        } else {
            VoiceSetupContent(
                onSaved = { api, secret ->
                    onSaveKeys(api, secret)
                    BaiduAsr.forgetToken()
                    keys = api to secret
                },
                onCancel = onCancel,
                onTypeInstead = onTypeInstead,
                onResult = onResult,
            )
        }
    }
}

/**
 * 还没配 Key 时的引导页。
 *
 * 这里**故意不再自动退回系统识别**：那台设备上没有可供第三方绑定的
 * `RecognitionService`，自动退回去只会让用户看到一句「识别服务没响应」，
 * 看不出到底哪里坏了。改成说清原因 + 当场填 Key（填完马上校验）+ 一个
 * 明确写着「仍然试试系统语音」的出口。
 */
@Composable
private fun VoiceSetupContent(
    onSaved: (String, String) -> Unit,
    onCancel: () -> Unit,
    onTypeInstead: (String) -> Unit,
    onResult: (String) -> Unit,
) {
    var showForm by remember { mutableStateOf(false) }
    var systemMode by remember { mutableStateOf(false) }

    if (systemMode) {
        VoiceSheetContent(
            onCancel = { systemMode = false },
            onTypeInstead = onTypeInstead,
            onResult = onResult,
        )
        return
    }

    val scope = rememberCoroutineScope()
    var apiInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .consumeClicks()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (showForm) "填百度语音 Key" else "语音还没配好",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (showForm) {
                    "到百度智能云「语音技术 → 短语音识别」创建应用，把应用列表里的 " +
                        "API Key 与 Secret Key 粘到这里。个人认证送 5 万次，够用很久。"
                } else {
                    "这台手机没有可用的系统语音服务（长按一直报「识别服务没响应」就是它）。" +
                        "填一次百度 Key 就能用：录音还是 App 自己录，只把音频发去转成文字。"
                },
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 13.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )

            if (showForm) {
                Spacer(Modifier.height(20.dp))
                VoiceField(
                    value = apiInput,
                    onValueChange = { apiInput = it.trim() },
                    placeholder = "API Key",
                    enabled = !checking,
                )
                Spacer(Modifier.height(10.dp))
                VoiceField(
                    value = secretInput,
                    onValueChange = { secretInput = it.trim() },
                    placeholder = "Secret Key",
                    enabled = !checking,
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = Color(0xFFFF8A8A),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            if (showForm) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VoiceButton(text = "返回", primary = false) {
                        if (!checking) {
                            showForm = false
                            error = null
                        }
                    }
                    VoiceButton(
                        text = if (checking) "校验中…" else "校验并开始",
                        primary = true,
                        enabled = !checking,
                    ) {
                        if (checking) return@VoiceButton
                        if (apiInput.isBlank() || secretInput.isBlank()) {
                            error = "两个 Key 都要填"
                            return@VoiceButton
                        }
                        checking = true
                        error = null
                        scope.launch {
                            val problem = BaiduAsr.checkCredentials(apiInput, secretInput)
                            checking = false
                            if (problem == null) onSaved(apiInput, secretInput) else error = problem
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VoiceButton(text = "改用打字", primary = false) { onTypeInstead("") }
                    VoiceButton(text = "填百度 Key", primary = true) { showForm = true }
                }
                Spacer(Modifier.height(14.dp))
                // 零配置的第三条路：输入法键盘上都有麦克风键，那条链路是输入法自己接的云端，
                // 跟系统的 RecognitionService 无关，这台设备上反而能用
                Text(
                    "也可以点「改用打字」，再用输入法键盘上的麦克风键说话",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "仍然试试系统语音",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { systemMode = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** 深色浮层里的输入框：白字、细边框，和遮罩风格一致。 */
@Composable
private fun VoiceField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.38f), fontSize = 14.sp) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.6f),
            cursorColor = Color.White,
            focusedBorderColor = Color.White.copy(alpha = 0.72f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
            disabledBorderColor = Color.White.copy(alpha = 0.18f),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    )
}

/**
 * 在线识别：App 自己录音，说完把 PCM 发给百度换文字。
 *
 * 这条路径**不依赖设备的 `android.speech.RecognitionService`** —— 国产 ROM 上那个服务
 * 多数没实现或不对第三方开放，绑定即 `ERROR_CLIENT`。自己录 + 云端转写就没这层依赖，
 * 顺带好处是声量直接来自采样，球跟得更紧。
 */
@Composable
private fun OnlineVoiceContent(
    apiKey: String,
    secretKey: String,
    /** 凭据失效时回到「填 Key」页，别让用户自己去设置里找。 */
    onEditKeys: () -> Unit,
    onCancel: () -> Unit,
    onTypeInstead: (String) -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()

    var level by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 录满上限（55 秒）自动收尾，由下面那个 LaunchedEffect 接住。 */
    var autoStop by remember { mutableStateOf(false) }
    /** 麦克风权限是否已给：没给时按下先去申请，不白白开一个必然失败的录音。 */
    var micGranted by remember { mutableStateOf(false) }

    val recorder = remember {
        VoiceRecorder(
            onLevel = { level = it },
            onLimitReached = { autoStop = true },
        )
    }

    val onResultLatest = rememberUpdatedState(onResult)
    val onTypeLatest = rememberUpdatedState(onTypeInstead)

    fun stopAndSend() {
        if (!recording) return
        recording = false
        val pcm = recorder.stop()
        level = 0f
        if (pcm.size < recorder.sampleRate / 2) {
            error = "没录到声音，再说一次吧"
            return
        }
        recognizing = true
        scope.launch {
            val reply = BaiduAsr.recognize(pcm, apiKey, secretKey)
            recognizing = false
            val text = reply.text
            if (text != null) onResultLatest.value(text) else error = reply.error ?: "识别失败，再说一次吧"
        }
    }

    fun startRecording() {
        error = null
        val ok = recorder.start()
        recording = ok
        if (!ok) error = "录音启动失败，检查麦克风权限或有没有别的应用在占用"
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        // 授权后不再自动开录：录音只由「按住」触发，授权完再按一次即可
        if (!granted) error = "没有麦克风权限，先用打字吧"
    }

    DisposableEffect(Unit) {
        onDispose { recorder.stop() }
    }

    // 遮罩全黑，浅色主题下要靠白图标才看得见状态栏（同系统识别路径）
    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) controller?.isAppearanceLightStatusBars = wasLight }
    }

    // 进浮层先确认权限，但**不自动开录** —— 现在的交互是「按住球说话、松手结束」
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        micGranted = granted
        if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(autoStop) {
        if (autoStop) {
            autoStop = false
            stopAndSend()
        }
    }

    val failure = error

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
                    failure != null -> "没听清"
                    recognizing -> "正在识别"
                    recording -> "我在听，松手结束"
                    else -> "按住说话"
                },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(30.dp))

            // 识别中让球维持在一点微光，别一下子塌回去。
            // 交互：按住 = 开始录，松手 = 立刻收音并送去识别，不再要求点「说完了」。
            // pointerInput 的 key 必须是常量：用 recording 当 key 的话，一开录就重组、
            // 手势会被立刻取消，等于刚按下就"松手"，录到一片空白。
            SiriOrb(
                level = if (recognizing) 0.14f else level,
                modifier = Modifier
                    .size(176.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            if (recognizing) return@awaitEachGesture
                            if (!micGranted) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@awaitEachGesture
                            }
                            error = null
                            startRecording()
                            waitForUpOrCancellation()
                            if (recording) stopAndSend()
                        }
                    },
            )

            Spacer(Modifier.height(30.dp))

            Text(
                text = failure ?: when {
                    recognizing -> "正在把语音转成文字…"
                    recording -> "松手就结束，说一句就行"
                    else -> "按住下面的球说一句，比如「今天充电花了 50」"
                },
                color = if (failure != null) Color.White.copy(alpha = 0.62f) else Color.White,
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
                when {
                    failure != null -> {
                        VoiceButton(text = "改用打字", primary = false) { onTypeLatest.value(failure) }
                        // 报错后不清空状态：点它只是回到「按住说话」，录不录由手指决定
                        VoiceButton(text = "再试一次", primary = true) { error = null }
                    }

                    recognizing -> VoiceButton(text = "取消", primary = false) { onCancel() }

                    else -> VoiceButton(text = "取消", primary = false) {
                        recorder.stop()
                        recording = false
                        onCancel()
                    }
                }
            }

            // 报错时给两条出口：确认是 Key 的问题就直接回到填 Key 页
            if (failure != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "重新填 Key",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEditKeys() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
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
    /** 正在连识别服务（含自动重试的那几次）。此时不能报错，否则会闪一下「被打断」。 */
    var preparing by remember { mutableStateOf(true) }
    var level by remember { mutableFloatStateOf(0f) }

    val onResultLatest = rememberUpdatedState(onResult)
    val onTypeLatest = rememberUpdatedState(onTypeInstead)

    val session = remember {
        VoiceSession(
            context = context,
            onPreparing = { preparing = true },
            onPartial = { partial = it },
            onLevel = { level = it },
            onResult = { text -> onResultLatest.value(text) },
            onFail = { message ->
                listening = false
                preparing = false
                level = 0f
                error = message
            },
            onListening = { active ->
                listening = active
                if (active) preparing = false
            },
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

    // 兜底：进程内识别三次都连不上时，交给系统听写 App —— 界面不是我们的，但至少能说出话
    val dictation = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            onResultLatest.value(text)
        } else {
            error = "系统语音也没返回结果，改用打字吧"
        }
    }

    fun systemDictation() {
        session.release()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句：今天充电花了 50")
        }
        val ok = runCatching { dictation.launch(intent) }.isSuccess
        if (!ok) error = "这台手机没有系统语音，改用打字吧"
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
                    preparing -> "正在准备"
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
                text = partial.ifBlank { error ?: "比如「今天充电花了 50」" },
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

            // 只在识别彻底失败后才露出这条，别让正常路径多出一个选择
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "还是不行？用系统语音",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { systemDictation() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun VoiceButton(
    text: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = LocalAppColors.current.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (primary) accent.copy(alpha = if (enabled) 1f else 0.45f) else Color.White.copy(alpha = 0.14f))
            .border(
                1.dp,
                if (primary) Color.Transparent else Color.White.copy(alpha = 0.20f),
                RoundedCornerShape(26.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
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
 * 三层叠出来：外圈柔光（有声音时张开）→ 球内几团绕球心旋转的彩云 → 上沿一道细高光。
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
 * 这里踩过的坑，动之前先看一眼：
 *  - `createSpeechRecognizer()` 的绑定是**异步**的。紧接着 `startListening()`，在多数 ROM 上
 *    会直接回 `ERROR_CLIENT` —— 界面上就是「识别被打断了，再说一次吧」。创建后要等一拍再 start；
 *  - 每次都想 `destroy()` 重建同样会撞窗口期，旧实例销毁后必须隔一会儿再建新的；
 *  - `EXTRA_SPEECH_INPUT_*_MILLIS` 这几个 extra 规范里是 **Int**，传 Long 时服务端
 *    `getIntExtra` 读不到值，个别 ROM 直接抛异常，错误码还是 `ERROR_CLIENT`；
 *  - 以上都可能偶发，所以 `ERROR_CLIENT` / `ERROR_RECOGNIZER_BUSY` 一律静默重试（最多 3 次），
 *    再配一个「4 秒内没有任何回调就当没连上」的看门狗。
 */
private class VoiceSession(
    context: Context,
    private val onPreparing: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFail: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    // 尽量用 Activity 的 Context 去连识别服务：少数 ROM 拿 applicationContext 会被拒
    private val host: Context = context.hostContext()
    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var tries = 0
    private var connectedGen = -1
    private var released = false

    val available: Boolean
        get() = runCatching { SpeechRecognizer.isRecognitionAvailable(host) }.getOrDefault(false)

    fun start() {
        released = false
        tries = 0
        attempt()
    }

    /** 用户点「说完了」：不再收音，等识别服务把最终结果送回来。 */
    fun finish() {
        runCatching { recognizer?.stopListening() }
    }

    fun release() {
        released = true
        generation++
        val dead = recognizer
        recognizer = null
        handler.removeCallbacksAndMessages(null)
        runCatching { dead?.cancel() }
        runCatching { dead?.destroy() }
    }

    // ---- 连接：销毁旧实例 → 重建 → 等绑定 → 收音 ----

    private fun attempt() {
        tries++
        val gen = ++generation
        onPreparing()
        onListening(false)
        val dead = recognizer
        recognizer = null
        if (dead != null) {
            runCatching { dead.cancel() }
            runCatching { dead.destroy() }
        }
        // 刚 destroy 完立刻 create，服务端还没断干净，给它 240ms
        handler.postDelayed({ if (alive(gen)) create(gen) }, if (dead == null) 0L else 240L)
    }

    private fun create(gen: Int) {
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(host) }.getOrNull()
        if (created == null) {
            fail(gen, "这台手机没有语音识别服务，直接打字吧")
            return
        }
        recognizer = created
        created.setRecognitionListener(listener(gen))
        // 绑定完成前 startListening 会被判成客户端错误，先让 90ms
        handler.postDelayed({
            if (!alive(gen)) return@postDelayed
            val ok = runCatching { created.startListening(speechIntent()) }.isSuccess
            if (!ok) {
                retryOrFail(gen, "语音识别启动失败")
                return@postDelayed
            }
            // 看门狗：4 秒内一个回调都没来，说明服务压根没连上
            handler.postDelayed({
                if (alive(gen) && connectedGen != gen) retryOrFail(gen, "识别服务没响应")
            }, 4000L)
        }, 90L)
    }

    @Suppress("TooManyFunctions")
    private fun listener(gen: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            markConnected(gen)
            if (alive(gen)) onListening(true)
        }

        override fun onBeginningOfSpeech() = markConnected(gen)

        override fun onRmsChanged(rmsdB: Float) {
            markConnected(gen)
            if (!alive(gen)) return
            // 实测范围大致 -2dB（安静）~ 10dB（贴嘴说话），映射到 0~1
            onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (alive(gen)) onListening(false)
        }

        override fun onError(error: Int) {
            if (!alive(gen)) return
            onListening(false)
            onLevel(0f)
            when (error) {
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                -> retryOrFail(gen, humanError(error))

                else -> fail(gen, humanError(error))
            }
        }

        override fun onResults(results: Bundle?) {
            if (!alive(gen)) return
            onListening(false)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) {
                fail(gen, "没听清，再说一次吧")
            } else {
                // 拿到结果就收工：先把这一代作废，避免收尾时再蹦出一条错误
                released = true
                generation++
                runCatching { recognizer?.cancel() }
                onResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!alive(gen)) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        // 半截结果：球下面那行字要跟着说话长出来，不能等整句结束才出现
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // 停顿 1.5 秒才判「说完了」；再短会把人换气当成结束。
        // 这三个 extra 必须是 Int —— 传 Long 会被服务端读歪（见类注释）
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
        putExtra("android.speech.extra.DICTATION_MODE", true)
    }

    /** 有些服务不发 `onReadyForSpeech` 直接开始报声量，收到任何一个回调都算连上了。 */
    private fun markConnected(gen: Int) {
        if (gen == generation) connectedGen = gen
    }

    private fun retryOrFail(gen: Int, why: String) {
        if (!alive(gen)) return
        if (tries < MAX_TRIES) attempt() else fail(gen, why)
    }

    private fun fail(gen: Int, message: String) {
        if (!alive(gen)) return
        onListening(false)
        onLevel(0f)
        onFail(message)
    }

    /** 这一代尝试是否还算数：重试或退出后，旧实例的回调一律丢掉。 */
    private fun alive(gen: Int) = gen == generation && !released

    private companion object {
        const val MAX_TRIES = 3
    }
}

/** 顺着 ContextWrapper 往上找 Activity；找不到就退回 applicationContext。 */
private fun Context.hostContext(): Context {
    var cursor: Context = this
    while (cursor is ContextWrapper) {
        if (cursor is Activity) return cursor
        cursor = cursor.baseContext
    }
    return applicationContext
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
