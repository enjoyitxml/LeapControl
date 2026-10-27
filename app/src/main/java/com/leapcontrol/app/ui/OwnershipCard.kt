package com.leapcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * 「与爱车相伴」卡（首页整行宽幅，横版）。
 *
 * 版式参考官方车况页那张雪山长条图：整张是风景实景，文字压在左上的天空区。
 * 背景支持 6 套内置图（见 [OwnershipBackdrops]，个性化页可切换），
 * 每张图按「天空亮不亮」单独给压暗强度，白字才在每张图上都站得住。
 *
 * 字号参照官方图的比例（卡片宽 ≈ 349dp 时）：说明文字 15sp、数字 30sp、单位 13sp。
 * 数字用常规字重而不是 Bold —— 图上那串「1013 天 44064 公里」笔画很细，
 * 加粗会让整块变笨重，也会把右侧的雪山留白吃掉。
 *
 * 两个数字都来自接口（见 [OwnershipStats]）：
 *  - 提车天数：里程/能耗累计接口的 ownershipDays；
 *  - 累计公里：同一份汇总的总里程，缺失时退回车况报文里的 totalMileage。
 *
 * 卡片配色固定为白字 + 图片，不随深浅色主题变化。
 */
@Composable
fun OwnershipCard(
    status: CarStatus?,
    energy: MileageEnergySummary?,
    backdropId: String? = null,
    modifier: Modifier = Modifier,
) {
    // 图片内容不可控（换图/裁切位置都会变），所以文字统一带一层柔和投影兜底
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.40f), offset = Offset(0f, 1f), blurRadius = 6f)
    val days = OwnershipStats.days(energy?.ownershipDays)
    val kmText = OwnershipStats.formatKm(energy?.totalMileageKm)
        ?: OwnershipStats.formatKm(OwnershipStats.kmFromText(status?.totalMileage))
    val backdrop = OwnershipBackdrops.byId(backdropId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        // 竖构图原图 Crop 到 3:1 后正好取到「天空 + 山脊」这一段，不裁峰尖
        Image(
            painter = painterResource(backdrop.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // 左侧压暗：文字全部靠左，强度按背景图有多亮单独给（见 OwnershipBackdrops.scrim），
        // 半屏以外（0.55 之后）就不再压，右侧留出图本身的通透感
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = backdrop.scrim),
                        0.55f to Color.Black.copy(alpha = backdrop.scrim * 0.28f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // 底部再压一点：山脊/水面本身花色，不压的话白字下缘会和山体纹路打架
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.26f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 20.dp, top = 16.dp, end = 16.dp),
        ) {
            Text(
                text = "我与我的爱车走过",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                style = TextStyle(shadow = shadow),
                letterSpacing = 0.3.sp,
                maxLines = 1,
            )

            Spacer(Modifier.height(7.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = days?.toString() ?: "--",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-1).sp,
                    style = TextStyle(shadow = shadow),
                    maxLines = 1,
                )
                // 数字与单位之间用显式间距：靠空格宽度不稳定，且容易被字体吞掉
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "天",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.padding(bottom = 5.dp),
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = kmText ?: "--",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-1).sp,
                    style = TextStyle(shadow = shadow),
                    maxLines = 1,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "公里",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}
