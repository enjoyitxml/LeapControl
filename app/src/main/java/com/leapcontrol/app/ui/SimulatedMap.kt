package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 「模拟地图」底纹：几条斜穿画面的道路。
 *
 * ## 为什么画假的
 * 真地图底图要花一次静态地图额度（个人认证 5000 次/日，但没必要为一块底纹烧它），
 * 而且未配 Key、车机没定位、断网这几种情况下根本没有图 —— 那几帧原来是一块
 * 带「无位置」三个字的空色块，看着像坏了。这里用几条线补上「地图感」，
 * 真正的地图出来后被盖住，没有地图时也不显得残缺。
 *
 * ## 画法
 * 四条折线：一条主路（主题色，稍粗）+ 三条街区路（正文色，很淡），
 * 角度、折点都写死 —— 不能随机，否则每次重组线条都会跳一下。
 * 线宽按**短边**折算，方图和通栏卡上的粗细观感才一致。
 *
 * @param intensity 线条强度。整卡铺底用 0.5 左右（只是质感，不能抢内容），
 *   缩略图占位用 1（那里本来什么都没有）。
 * @param centerFade 中心要放文字时置 true：压一层径向渐变，把中央的线擦淡，
 *   否则「无位置」三个字正好压在主路上。
 * @param insetSpan 只占右端这一块（0~1，占宽度的比例），四边渐变融进卡片底色，
 *   当点缀用。0 = 铺满整块（缩略图占位那种「本来就是一张图」的用法）。
 *   为什么不让调用方自己套一个窄 Box 再画：卡片是 wrap-content 的 Box，
 *   子项 `fillMaxHeight` 在滚动容器里拿不到有限的高度约束，一撑就飞。
 *   所以「只占一块」这件事必须在画布里按比例裁，而不是靠布局裁。
 */
@Composable
fun SimulatedMapBackdrop(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    centerFade: Boolean = false,
    insetSpan: Float = 0f,
) {
    val C = LocalAppColors.current
    // 浅色主题下线条天生比深色底更不显眼，所以基线刻意比「看着刚好」再高一点点：
    // 整卡铺底会再乘 0.55，最后剩下的那点灰度正好是一层若有若无的纹理
    val minor = C.textPrimary.copy(alpha = 0.115f * intensity)
    val major = C.accent.copy(alpha = 0.22f * intensity)
    val wash = C.accent.copy(alpha = 0.05f * intensity)
    val fade = C.panelBg

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 注意 span=0 要显式当作「铺满」：`w * (1 - 0)` 会把左边界推到最右边、算出零宽，
        // 缩略图占位那两处（铺满）就会整块空白
        val span = insetSpan.coerceIn(0f, 1f)
        val left = if (span <= 0f) 0f else w * (1f - span)
        val rw = w - left
        if (rw <= 0f) return@Canvas

        // 底子：一层极淡的主题色渐变，和纯色面板拉开一点层次
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(wash, Color.Transparent),
                start = Offset(left, 0f),
                end = Offset(w, h),
            ),
            topLeft = Offset(left, 0f),
            size = Size(rw, h),
        )

        val unit = minOf(rw, h)
        road(left, rw, h, ROAD_MAIN, major, unit * 0.045f)
        road(left, rw, h, ROAD_LEFT, minor, unit * 0.022f)
        road(left, rw, h, ROAD_RIGHT, minor, unit * 0.022f)
        road(left, rw, h, ROAD_CROSS, minor, unit * 0.014f)

        // 点缀模式下四边擦淡：不然右边这一块会露出一条竖直的硬边，
        // 看着像「半张图被裁掉了」，而不是一层纹理
        if (span > 0f) dissolve(left, rw, w, h, fade)

        if (centerFade) {
            // 中心要放「无位置 / 未配 Key」这行字，所以在中间压一层卡片底色。
            // 关键是**别压太大**：半径 0.62 时整张 96dp 方图几乎被冲白，看着还是一条空色块；
            // 这里收到 0.52 并用三档 stop，让线条在中段还能透出来一点
            drawRect(
                brush = Brush.radialGradient(
                    0f to fade.copy(alpha = 0.90f),
                    0.45f to fade.copy(alpha = 0.52f),
                    1f to fade.copy(alpha = 0f),
                    center = center,
                    radius = unit * 0.52f,
                ),
            )
        }
    }
}

/**
 * 四边把底纹擦回卡片底色。
 *
 * 只画**边缘那一条**渐变（中间留空），所以线条在中心区原样保留。
 * 左侧的渐隐最长 —— 它朝着文字那一侧，是唯一会被误看成「一条竖线」的边。
 * 颜色必须是卡片的不透明底色（[com.leapcontrol.app.ui.AppColors.panelBg]），
 * 不能用 Color.Transparent 去「减淡」：drawRect 只有覆盖能力，没有降低已有像素的 alpha 的能力。
 */
private fun DrawScope.dissolve(left: Float, rw: Float, w: Float, h: Float, color: Color) {
    val solid = color.copy(alpha = 0.94f)
    // 左
    drawRect(
        brush = Brush.horizontalGradient(
            0f to solid,
            1f to Color.Transparent,
            startX = left,
            endX = left + rw * 0.42f,
        ),
        topLeft = Offset(left, 0f),
        size = Size(rw, h),
    )
    // 上 / 下：渐变段短一些，只在贴边处把线条抹掉，别把整块压成一条窄带
    drawRect(
        brush = Brush.verticalGradient(
            0f to solid,
            1f to Color.Transparent,
            startY = 0f,
            endY = h * 0.20f,
        ),
        topLeft = Offset(left, 0f),
        size = Size(rw, h),
    )
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to solid,
            startY = h * 0.80f,
            endY = h,
        ),
        topLeft = Offset(left, 0f),
        size = Size(rw, h),
    )
    // 右：贴着卡片右缘（卡片有圆角，太硬的边会被圆角切出一个小豁口）
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to solid,
            startX = w - rw * 0.22f,
            endX = w,
        ),
        topLeft = Offset(left, 0f),
        size = Size(rw, h),
    )
}

/** 按**相对坐标**（0~1）画一条折线，映射到 [left]..[left]+[rw] 这块区域里。 */
private fun DrawScope.road(
    left: Float,
    rw: Float,
    h: Float,
    points: List<Pair<Float, Float>>,
    color: Color,
    width: Float,
) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(left + points.first().first * rw, points.first().second * h)
    points.drop(1).forEach { (x, y) -> path.lineTo(left + x * rw, y * h) }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * 四条道路的相对折点。
 *
 * 角度故意不等（一条近水平、两条近垂直、一条斜穿），交叉处才像路口而不是网格；
 * 每条至少一个折点，避免画成死板的直线。
 */
private val ROAD_MAIN = listOf(0f to 0.34f, 0.42f to 0.46f, 0.68f to 0.20f, 1f to 0.32f)
private val ROAD_LEFT = listOf(0.12f to 0f, 0.40f to 0.52f, 0.30f to 1f)
private val ROAD_RIGHT = listOf(0.92f to 0f, 0.64f to 0.50f, 0.86f to 1f)
private val ROAD_CROSS = listOf(0f to 0.78f, 0.52f to 0.92f, 1f to 0.70f)
