package com.leapcontrol.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 「行驶动效」设置页（个性化 → 车模外观 → 行驶动效）。
 *
 * ## 只有两个滑杆
 *
 * 上一版这一页有总开关 + 三层特效各自开关 / 强度 / 透明度 + 透视角度 + 放置位置 +
 * 轮位标定，用户实测后要求删掉。现在只留：**抖动幅度**、**风向**。其余参数
 * （线密度、长度、颜色、速度系数）按默认值写死，不再让用户面对十几个滑杆。
 *
 * ## 上半屏预览、下半屏参数
 *
 * 抖多少、风往哪斜，都是「看着调」的东西。参数和结果不在同一屏，改一个值要滑上去
 * 看了一眼再滑下来，十几次之后就放弃了。所以预览固定在上面，参数区自己滚。
 *
 * 预览固定 60 km/h 渲染：0 km/h 时一帧都不画，用户会以为「这功能坏了」。
 */
@Composable
fun CarMotionScreen(
    runtime: AppRuntime,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    var bitmap by remember { mutableStateOf(runtime.cachedDisplayCarBitmap()) }
    LaunchedEffect(Unit) {
        runCatching { runtime.displayCarBitmap() }.getOrNull()?.let { bitmap = it }
    }

    // 草稿：滑杆只改它，预览立刻跟着变；停手 280ms 再落盘。
    // 直接每帧写 prefs 的话，拖一次要写几十次 SharedPreferences。
    var draft by remember { mutableStateOf(runtime.carMotionSettings()) }
    LaunchedEffect(draft) {
        delay(280)
        runtime.setCarMotion(draft)
    }
    // 防抖有 280ms 窗口：改完立刻按返回，上面的协程会被取消，参数就丢了。
    // 页面销毁时再存一次兜底（拖到最后一下松手就走也存得住）。
    val latestDraft by rememberUpdatedState(draft)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runtime.setCarMotion(latestDraft) }
    }

    // 二级页必须自己注册返回，否则侧滑会直接 finish Activity。
    BackHandler(enabled = true) {
        runtime.setCarMotion(draft)
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bgTop)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsTopBar(title = "行驶动效", onBack = onBack)
        }

        // ---- 上半屏：预览 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            MotionPreview(bitmap = bitmap, settings = draft, dark = C.isDark)
        }
        Text(
            text = "预览按 60 km/h 渲染，首页跟着真实车速走 · 停车不动时一帧都不画",
            color = C.textSecondary,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )

        // ---- 下半屏：两个参数 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.56f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsCard {
                Text(
                    text = "车身抖动",
                    color = C.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "车速越快抖得越密、越明显。调 0 就完全不抖，只留风。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                MotionSlider(
                    label = "幅度",
                    value = draft.shakeDp,
                    range = CarMotionSettings.MIN_SHAKE_DP..CarMotionSettings.MAX_SHAKE_DP,
                    display = String.format(java.util.Locale.US, "%.1f dp", draft.shakeDp),
                    onChange = { draft = draft.copy(shakeDp = it).normalized() },
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingsCard {
                Text(
                    text = "风",
                    color = C.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "你的车模朝左，所以风一律从左往右掠过车身。这里只调它斜着吹的角度："
                        + "正值往下压，负值往上掀，0 是水平。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                MotionSlider(
                    label = "风向",
                    value = draft.windAngle,
                    range = -CarMotionSettings.MAX_WIND_ANGLE..CarMotionSettings.MAX_WIND_ANGLE,
                    steps = (CarMotionSettings.MAX_WIND_ANGLE * 2 - 1).toInt(),
                    display = windText(draft.windAngle),
                    onChange = { draft = draft.copy(windAngle = it).normalized() },
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "恢复默认（抖动 2.0 dp、风向水平）",
                color = C.accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { draft = CarMotionSettings.DEFAULT }
                    .padding(vertical = 10.dp),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 风向文案：0 说「水平」，正负各自说清往哪边斜。 */
private fun windText(angle: Float): String = when {
    abs(angle) < 0.5f -> "0°"
    angle > 0f -> "+${angle.roundToInt()}° 下斜"
    else -> "${angle.roundToInt()}° 上斜"
}

/** 预览区：车模 + 动效。固定 60 km/h，永远不暂停（这一页就是拿来看的）。 */
@Composable
private fun MotionPreview(
    bitmap: Bitmap?,
    settings: CarMotionSettings,
    dark: Boolean,
) {
    val C = LocalAppColors.current
    val shown = remember(bitmap) { bitmap?.asImageBitmap() }
    val paused = remember { mutableStateOf(false) }
    // 预览速度取 60：中风速，抖动和风都能看清；拉满 120 反而不容易判断「是不是太夸张」
    val previewSpeed = 60f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(C.accent.copy(alpha = 0.08f), C.iconBg),
                ),
            ),
    ) {
        if (shown == null) {
            Text(
                "还没取到车模图\n刷新一次车模后就能在这里预览",
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        CarMotionStage(
            speedKmh = previewSpeed,
            settings = settings,
            paused = paused,
            dark = dark,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
            Image(
                bitmap = shown,
                contentDescription = "车模动效预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 预览角标：说明这块是「60 km/h 的样子」，别让人以为首页也这么飘
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text("预览 · 60 km/h", color = Color.White, fontSize = 10.sp)
        }
    }
}

/**
 * 一行滑杆：左边标签、右边当前值。
 *
 * [steps] 传 0 表示连续（抖动幅度按 0.1 走，密度够细）；风向按 1° 吸附。
 */
@Composable
private fun MotionSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    val C = LocalAppColors.current
    // 滑杆内部用 Float，避免「0.1 步进 → round 成 Int 再乘回去」的抖动
    var local by remember(value) { mutableFloatStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = C.textSecondary,
            fontSize = 11.5.sp,
            modifier = Modifier.width(40.dp),
        )
        Slider(
            value = local,
            onValueChange = {
                local = it
                onChange(it)
            },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = C.accent,
                activeTrackColor = C.accent,
                inactiveTrackColor = C.barTrack,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = display,
            color = C.textPrimary,
            fontSize = 11.5.sp,
            maxLines = 1,
            modifier = Modifier.width(78.dp),
        )
    }
}
