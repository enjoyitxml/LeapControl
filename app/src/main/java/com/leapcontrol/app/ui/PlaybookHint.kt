package com.leapcontrol.app.ui

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 新手引导的落盘状态（只存「看过没看过」，不存时间、不按 VIN 隔离）。
 *
 * 一个安装只看一次：点「不再提示」、点开玩法说明本身，都算看过。
 * 存在独立的 prefs 里而不是 AppRuntime，是因为它只跟界面引导有关，
 * 退出登录 / 换车都不该让它重新出现。
 */
object PlaybookHint {
    private const val PREFS = "onboarding_hints"
    private const val KEY_DONE = "playbook_hint_done_v1"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, true).apply()
    }
}

/**
 * 首次打开首页时，「玩法说明」图标旁的手势引导。
 *
 * 为什么不用全屏遮罩：遮罩会把首页所有按钮都吃掉，用户第一眼想点的往往是别处；
 * 这里只在图标位置画一圈呼吸光环 + 一个上下跳动的点击手势，其余区域照常可点。
 * 卡片钉在图标正下方、与图标右边缘对齐，指向关系靠「光环 + 手势」交代，不加箭头。
 *
 * [anchor] 是玩法说明图标在 root 坐标系里的矩形（由 HomeScreen 用
 * `onGloballyPositioned` 采集）。本组件自己铺满父容器，再把两个坐标系一减，
 * 就得到图标在本层里的落点 —— 这样顶栏图标有几个、字号多大都不影响对准。
 */
@Composable
fun PlaybookCoachMark(
    anchor: Rect,
    onOpen: () -> Unit,
    onNeverShowAgain: () -> Unit,
) {
    val C = LocalAppColors.current
    val density = LocalDensity.current
    // 本层在 root 里的原点：首帧还没测出来就什么都不画，避免先闪一下错位的光环
    var origin by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft },
    ) {
        val o = origin ?: return@BoxWithConstraints

        val cx = anchor.center.x - o.x
        val cy = anchor.center.y - o.y

        val ringPx = with(density) { 46.dp.toPx() }
        val handPx = with(density) { 28.dp.toPx() }
        // 手势压在图标**下沿**之外：指尖朝上指，但圆盘不盖住图标本身
        val handDx = with(density) { 11.dp.toPx() }
        val handDy = with(density) { 30.dp.toPx() }
        val bobPx = with(density) { 6.dp.toPx() }

        // 卡片：右边缘跟图标右边缘对齐，宽度固定，左边不许顶出屏幕
        val bubbleW = 250.dp
        val bubblePx = with(density) { bubbleW.toPx() }
        val edgePx = with(density) { 14.dp.toPx() }
        val bubbleX = max(edgePx, (anchor.right - o.x) - bubblePx).roundToInt()
        val bubbleY = (cy + with(density) { 52.dp.toPx() }).roundToInt()

        val transition = rememberInfiniteTransition(label = "playbookHint")
        // 呼吸波纹：从图标外沿散开、淡出，1.5 秒一轮
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
            label = "pulse",
        )
        // 点击手势：上下来回，回程更快一点，像真的在「点」
        val bob by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(720, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "bob",
        )

        // ---- 图标上的高亮光环 ----
        Box(
            modifier = Modifier
                .offset { IntOffset((cx - ringPx / 2).roundToInt(), (cy - ringPx / 2).roundToInt()) }
                .size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 常驻圈：把图标本身框住，说明「就是这个」
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(C.accent.copy(alpha = 0.14f), CircleShape)
                    .border(1.4.dp, C.accent.copy(alpha = 0.55f), CircleShape),
            )
            // 扩散圈：越往外越淡，循环不止
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f + pulse * 0.62f
                        scaleX = s
                        scaleY = s
                        alpha = (1f - pulse) * 0.45f
                    }
                    .border(1.4.dp, C.accent, CircleShape),
            )
        }

        // ---- 跳动的手势 ----
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (cx + handDx - handPx / 2).roundToInt(),
                        (cy + handDy - handPx / 2 - bob * bobPx).roundToInt(),
                    )
                }
                .size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 垫一层浅底圆盘：手势压在车模 / 场景上时不会糊成一团
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (C.isDark) C.bgTop.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.92f),
                        CircleShape,
                    ),
            )
            Icon(
                Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(20.dp),
            )
        }

        // ---- 说明卡 ----
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .offset { IntOffset(bubbleX, bubbleY) }
                .width(bubbleW)
                // shadow 必须在 clip 之前：否则投影被裁在圆角里面，卡面四周发灰
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .background(C.panelBg)
                .border(1.dp, C.accent.copy(alpha = 0.20f), shape)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = C.accent,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "先看一眼右上角",
                    color = C.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "点这个图标进「玩法说明」：车模、四季主题、快捷控车、怎么和官方 App 同时登录，都写在那几页里。",
                color = C.textSecondary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 次要动作：看过就永远不再打扰
                Text(
                    text = "不再提示",
                    color = C.textSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onNeverShowAgain)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                // 主按钮不用 accent 实底 + 白字：主题色是用户自己选的，浅色主题下白字会糊
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.accent.copy(alpha = 0.16f))
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "打开看看",
                        color = C.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
