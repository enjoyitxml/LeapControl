package com.leapcontrol.app.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.leapcontrol.app.LauncherIcon
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

    // 桌面图标当前是哪一套，登录页的 LOGO 跟着它走
    val launcherStyle by runtime.launcherIcon

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown -= 1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- 背景：手绘星空（纯静态，一次绘制）----
        // 原来这里是一段循环播放的星空视频（MediaPlayer + TextureView + res/raw 里的 mp4）。
        // 视频背景这个方向已经明确放弃、以后不再提供：解码器占内存、TextureView 是独立图层
        // 会多一次合成、登录页还常被当成后台常驻页面重绘。改成直接复用主题里已经定稿的
        // 「浩瀚星空」场景，观感一致、零解码、零资源体积。
        StarryBackdrop(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.55f))

            // ---- LOGO：跟桌面图标走 ----
            // 默认是方向盘（新标），只有把图标切成「官方 Logo」时才显示零跑标志，
            // 两处保持一致，避免桌面是一个标、进去看到另一个标。
            val officialIcon = launcherStyle == LauncherIcon.STYLE_OFFICIAL
            Image(
                painter = painterResource(
                    if (officialIcon) R.drawable.login_logo else R.drawable.login_logo_steering,
                ),
                contentDescription = if (officialIcon) "零跑汽车" else "方向盘",
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
                                        message = UserMessage.of(e, "登录没完成，请再试一次")
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
                                message = UserMessage.of(e, "验证码没发出去，请稍后再试")
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
                            message = UserMessage.of(e, "登录没完成，请再试一次")
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
            // 对外发布：不出现真名，统一以「开发者」指称；
            // 并明确「仅限车友自用交流」，避免被当成可公开传播、可随意分发的软件
            Text(
                "非官方客户端，由开发者个人开发\n登录与控车均直连零跑官方服务",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "仅限车友自用交流，请勿外传或二次分发",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * 登录页背景：静态星空（复用主题「浩瀚星空」的场景画法）。
 *
 * 为什么要换掉视频：视频背景这条线已经删除且不再提供。用 MediaPlayer + TextureView
 * 放一段 173KB 的 mp4，代价是解码器常驻、独立合成图层、每帧都要解码上屏；
 * 而登录页又恰恰是「没登录时唯一一屏」，冷启动时和首屏渲染抢资源。
 * 现在改成一次绘制就完事：夜色渐变打底 → 场景画笔补星点与月晕 → 底部压暗保证白字可读。
 */
@Composable
private fun StarryBackdrop(modifier: Modifier = Modifier) {
    val theme = SeasonThemes.STARRY
    val palette = remember { theme.palette() }
    Spacer(
        modifier = modifier.drawBehind {
            // 1) 夜色底：直接用主题自带的深色渐变，和首页夜间主题同一套色
            drawRect(Brush.verticalGradient(theme.gradient))
            // 2) 星点 + 月晕（t = 0：静态相位，不闪不抖）
            paintScene(SceneKind.STARRY, 0f, palette)
            // 3) 底部压暗：输入框是白色半透明底，没有这一层在星点上会发糊
            drawRect(
                Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.55f to Color.Black.copy(alpha = 0.20f),
                    1.00f to Color.Black.copy(alpha = 0.46f),
                ),
            )
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
