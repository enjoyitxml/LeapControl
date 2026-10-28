package com.leapcontrol.app.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.leapcontrol.app.R

/**
 * 登录页：手机号 + 短信验证码。
 *
 * 背景固定用一张星空图（drawable-nodpi/login_bg.png），不分深浅色；
 * 全部文字白色，元素自上而下：LOGO → 应用名 → slogan → 手机号 →
 * 验证码 + 获取验证码 → 登录 → 底部免责说明。
 */
/** LOGO 的视觉宽度约占屏幕宽度的四分之一。 */
private const val LOGO_WIDTH_FRACTION = 0.25f

/** login_logo.png 是 512×512 方形透明底白标。 */
private const val LOGO_ASPECT = 1f

@Composable
fun LoginScreen(
    runtime: AppRuntime,
    onLoginSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown -= 1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- 背景：星空视频循环播放（固定深色观感，不分深浅色模式）----
        VideoBackdrop(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.55f))

            // ---- 零跑 LOGO：横向约占屏幕四分之一 ----
            Image(
                painter = painterResource(R.drawable.login_logo),
                contentDescription = "零跑汽车",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(LOGO_WIDTH_FRACTION)
                    .aspectRatio(LOGO_ASPECT),
            )
            Spacer(Modifier.height(22.dp))

            // ---- 应用名 ----
            Text(
                "零控 LeapControl",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            // ---- slogan ----
            Text(
                "零跑控车，自在随行",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                letterSpacing = 3.sp,
            )

            Spacer(Modifier.weight(0.45f))

            // ---- 手机号 ----
            LoginTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                placeholder = "手机号",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Spacer(Modifier.height(12.dp))

            // ---- 验证码 + 获取验证码 ----
            Row(modifier = Modifier.fillMaxWidth()) {
                LoginTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it.filter(Char::isDigit).take(6) },
                    placeholder = "短信验证码",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (phone.length == 11 && smsCode.length == 6 && !busy) {
                                scope.launch {
                                    busy = true
                                    message = null
                                    try {
                                        runtime.login(phone, smsCode)
                                        onLoginSuccess()
                                    } catch (e: Exception) {
                                        message = e.message ?: "登录失败"
                                    }
                                    busy = false
                                }
                            }
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                runtime.sendSms(phone)
                                countdown = 60
                                message = "验证码已发送"
                            } catch (e: Exception) {
                                message = e.message ?: "验证码发送失败"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && phone.length == 11 && countdown == 0,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = if (countdown > 0) 0.15f else 0.45f),
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                        contentColor = Color.White,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.White.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(
                        if (countdown > 0) "${countdown}s" else "获取验证码",
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- 登录 ----
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            runtime.login(phone, smsCode)
                            onLoginSuccess()
                        } catch (e: Exception) {
                            message = e.message ?: "登录失败"
                        }
                        busy = false
                    }
                },
                enabled = !busy && phone.length == 11 && smsCode.length == 6,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A0A0C),
                    disabledContainerColor = Color.White.copy(alpha = 0.30f),
                    disabledContentColor = Color.White.copy(alpha = 0.55f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0A0A0C),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("处理中…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.weight(1f))

            // ---- 底部说明 ----
            Text(
                "本软件仅限夏明亮和他的好朋友使用\n登录与控车均直连零跑官方服务",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * 登录页背景视频：星空 loop，循环播放、静音、按 cover（裁满屏）方式缩放。
 * 视频放在 res/raw/login_bg_video.mp4（1440×2560，3.9s，173KB）。
 */
@Composable
private fun VideoBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                var videoW = 0f
                var videoH = 0f

                /** 按 cover 方式把视频缩放到铺满视图（多出的部分被裁掉）。 */
                fun applyCoverScale(w: Int, h: Int) {
                    if (videoW <= 0f || videoH <= 0f || w <= 0 || h <= 0) return
                    val viewRatio = w / h.toFloat()
                    val videoRatio = videoW / videoH
                    if (videoRatio > viewRatio) {
                        scaleX = videoRatio / viewRatio
                        scaleY = 1f
                    } else {
                        scaleX = 1f
                        scaleY = viewRatio / videoRatio
                    }
                }

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val mp = MediaPlayer.create(ctx, R.raw.login_bg_video) ?: return
                        videoW = mp.videoWidth.toFloat()
                        videoH = mp.videoHeight.toFloat()
                        mp.isLooping = true
                        mp.setVolume(0f, 0f) // 背景视频保持静音
                        mp.setSurface(Surface(st))
                        mp.start()
                        player = mp
                        applyCoverScale(w, h)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        applyCoverScale(w, h)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        player?.release()
                        player = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** 登录页输入框：半透明白底 + 白字，无边框的干净观感。 */
@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.40f), fontSize = 14.sp) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}
