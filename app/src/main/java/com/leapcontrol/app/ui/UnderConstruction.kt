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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 首页底部的「更多功能，正在施工中」占位区。
 *
 * 视觉上刻意压得很轻：三个施工小人 + 一行小字，全部取主题次要文字的浅灰
 * （用透明度分层，深浅主题都自动适配），不抢上方车况与快捷控车的主视觉。
 * 中间那个小人做 ±1.5dp 的缓慢上下浮动，暗示「正在干活」，幅度极小不会晃眼。
 */
@Composable
fun UnderConstructionHint(modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    val ink = C.textSecondary
    val transition = rememberInfiniteTransition(label = "under-construction")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 左右两个淡一点、微微外倾；中间那个最实、上下浮动 —— 像三个人围着一个坑
            Icon(
                Icons.Filled.Engineering,
                contentDescription = null,
                tint = ink.copy(alpha = 0.26f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(-8f)
                    .offset(y = (-1).dp),
            )
            Icon(
                Icons.Filled.Engineering,
                contentDescription = null,
                tint = ink.copy(alpha = 0.52f),
                modifier = Modifier
                    .size(26.dp)
                    .offset(y = (-1.5f * bob).dp),
            )
            Icon(
                Icons.Filled.Engineering,
                contentDescription = null,
                tint = ink.copy(alpha = 0.26f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(8f)
                    .offset(y = (-1).dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = "更多功能，正在施工中",
            color = ink.copy(alpha = 0.72f),
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
        )
    }
}
