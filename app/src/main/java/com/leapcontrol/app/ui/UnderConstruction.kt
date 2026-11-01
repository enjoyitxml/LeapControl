package com.leapcontrol.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 不抢上方车况与快捷控车的主视觉。
 *
 * 这里**不带动画**：早先铁锹有一个 ±2° 的极慢摇摆，但它在首页滚动区的最末尾，
 * 而一个永不停止的动画意味着 App 只要停在这一页就永远不会真正静下来 ——
 * 每一帧都要重组这一块、都要重新提交一次绘制，白白占着滑动的帧预算。
 * 占位提示本来也不是需要吸引注意力的东西，静下来更合适。
 */
@Composable
fun UnderConstructionHint(modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    val ink = C.textSecondary
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shovel),
                contentDescription = null,
                tint = ink.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp),
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
