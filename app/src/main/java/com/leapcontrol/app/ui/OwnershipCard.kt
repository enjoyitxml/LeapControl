package com.leapcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.MileageEnergySummary
import com.leapcontrol.app.OwnershipStats
import com.leapcontrol.app.R

/**
 * 「与爱车相伴」卡（首页胎压卡右侧，占另一半）。
 *
 * 版式：整张是高山实景图，底三分之一压一层暗渐变，文字全部落在底部 ——
 * 图片上部天空偏亮，文字放那里会糊，压在山体暗部才稳。
 *
 * 两个数字都来自接口（见 [OwnershipStats]）：
 *  - 提车天数：里程/能耗累计接口的 ownershipDays；
 *  - 累计公里：同一份汇总的总里程，缺失时退回车况报文里的 totalMileage。
 *
 * 卡片配色固定为白字 + 图片，不随深浅色主题变化：照片本身就是深色，
 * 换成浅色主题下的深色文字反而看不清。
 */
@Composable
fun OwnershipCard(
    status: CarStatus?,
    energy: MileageEnergySummary?,
    modifier: Modifier = Modifier,
) {
    // 图片内容不可控（换图/裁切位置都会变），所以文字统一带一层柔和投影兜底
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 6f)
    val days = OwnershipStats.days(energy?.ownershipDays)
    val kmText = OwnershipStats.formatKm(energy?.totalMileageKm)
        ?: OwnershipStats.formatKm(OwnershipStats.kmFromText(status?.totalMileage))

    Box(
        modifier = modifier
            .height(172.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_ownership_mountain),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // 底部压暗：文字压在雪山亮部上会糊，所以从 55% 处就开始加重，
        // 70%~100% 这段（文字所在区）稳定在 0.58→0.84，保证白字对比；顶部只轻压一点，留住天空层次
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.34f to Color.Black.copy(alpha = 0.05f),
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        0.72f to Color.Black.copy(alpha = 0.58f),
                        1f to Color.Black.copy(alpha = 0.84f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "我与我的爱车走过",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 9.5.sp,
                style = TextStyle(shadow = shadow),
                letterSpacing = 0.2.sp,
                maxLines = 1,
            )

            Spacer(Modifier.height(1.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = days?.toString() ?: "--",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    style = TextStyle(shadow = shadow),
                    maxLines = 1,
                )
                // 数字与单位之间用显式间距：靠空格宽度不稳定，且容易被字体吞掉
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "天",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = kmText ?: "--",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(shadow = shadow),
                    maxLines = 1,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "公里",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 10.sp,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }

        // 右上角一行极小的说明，用来说明这两个数是什么口径（不抢主体）
        Text(
            text = "相伴时光",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 8.5.sp,
            style = TextStyle(shadow = shadow),
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp),
        )
    }
}
