package com.leapcontrol.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R

/**
 * 首页底部的「更多功能，正在施工中」占位区。
 *
 * 造型是手绘的铁锹 + 路障（见 res/drawable/ic_shovel.xml、ic_barrier.xml），
 * 一把插在地上的铁锹配一块条纹路障，比原来的小人更像「工地现场」。
 *
 * 视觉上刻意压得很轻：全部取主题次要文字的浅灰（用透明度分层，深浅主题都自动适配），
 * 不抢上方车况与快捷控车的主视觉。铁锹绕锹尖（底边中点）做 ±2° 极慢的摇摆，
 * 幅度极小、周期 2.2s，只是让这块地方略微「活着」，不会晃眼。
 *
 * 位置：由 HomeScreen 用 weight 弹簧推到页面最底部，紧贴底部导航栏上方。
 */
@Composable
fun UnderConstructionHint(modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    val ink = C.textSecondary
    val transition = rememberInfiniteTransition(label = "under-construction")
    val sway by transition.animateFloat(
        initialValue = -12f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 铁锹：绕锹尖旋转（transformOrigin 落在底边中点），摇摆时锹尖一直扎在原地
            Icon(
                painter = painterResource(R.drawable.ic_shovel),
                contentDescription = null,
                tint = ink.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        rotationZ = sway
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                    },
            )
            // 路障：底部下移 1dp，和铁锹的地面线对齐
            Icon(
                painter = painterResource(R.drawable.ic_barrier),
                contentDescription = null,
                tint = ink.copy(alpha = 0.52f),
                modifier = Modifier
                    .size(40.dp)
                    .offset(y = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "更多功能，正在施工中",
            color = ink.copy(alpha = 0.72f),
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
        )
    }
}
