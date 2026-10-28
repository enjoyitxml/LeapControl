package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 车辆图形素材（俯视图）。
 *
 * 内置侧视剪影已删除：官方 carpicture 接口拉不到车模图时首页直接不显示车模。
 * 这里只保留胎压用的俯视图 —— 一张图被两处复用：
 *  - 首页胎压卡：[TireCarOutline]（近乎透明的玻璃壳，信息量全在四个轮子上）；
 *  - 胎压详情页：[TireTopView]（深色实心车 + 轮毂纹理）。
 *
 * 俯视造型要点（改坐标前先读一遍，否则很容易又画成「胶囊」）：
 *  - 车头接近全宽（78/96），前缘是**很缓的弧**而不是尖鼻子，两侧往前收的过程拉长到
 *    27 个单位，所以看着像真车的前脸而不是被捏扁的船头；
 *  - 车头两角各有一个**斜置车灯**（梯形，跟着翼子板往外斜），这是俯视下区分前后最有效的一笔；
 *  - 车尾比车头更方：只做 ~10 单位的小圆角，并留一块**后备箱盖**（带缝线的面板），
 *    一眼就能看出哪头是尾；
 *  - 顶棚是一整块**天幕玻璃**（前风挡 → 后风挡连通的大面板），带竖向渐变和一道斜向反光，
 *    而不是一个描线的方块 —— 这是新能源车最显著的特征。
 */
// ---------------- 胎压俯视图 ----------------

private const val TOP_W = 120f
private const val TOP_H = 250f

/**
 * 轮胎尺寸（俯视单位）。宽度比「完全塞进车身」再宽一点，
 * 让胎侧微微探出车身边缘 —— 真车俯视也是胎贴着轮眉、露出不到一指。
 */
private const val TIRE_W = 17f
private const val TIRE_H = 38f

/** 前 / 后轴轮胎中心线。车身纵向比例按这两条线定，改轮胎位置时一起改。 */
private const val AXLE_FRONT_Y = 61f
private const val AXLE_REAR_Y = 189f

/** 四个轮胎的左上角（俯视单位）：左前 / 右前 / 左后 / 右后 */
private val TIRE_SLOTS = mapOf(
    "左前" to floatArrayOf(10f, AXLE_FRONT_Y - TIRE_H / 2f),
    "右前" to floatArrayOf(93f, AXLE_FRONT_Y - TIRE_H / 2f),
    "左后" to floatArrayOf(10f, AXLE_REAR_Y - TIRE_H / 2f),
    "右后" to floatArrayOf(93f, AXLE_REAR_Y - TIRE_H / 2f),
)

private val TireOk = Color(0xFF30D158)
private val TireBad = Color(0xFFFF5A5F)
private val TireUnknown = Color(0xFF3A3D44)

private fun tireStateColor(tire: TireInfo?): Color = when {
    tire == null -> TireUnknown
    tire.warning -> TireBad
    else -> TireOk
}

/**
 * 车身俯视轮廓。
 *
 * 车头（上）：前缘是一段很缓的凸弧，两端圆角半径 ~14，翼子板在 27 个单位里慢慢外扩到全宽；
 * 车身：两侧腰线笔直；
 * 车尾（下）：几乎是直角收尾（圆角只有 ~10），比车头更方。
 */
private fun carBodyPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    // 车头：从左前角起，横穿前脸（中间比两端再往前 6 个单位，形成很缓的鼻梁）
    moveTo(X(18f), Y(17f))
    cubicTo(X(29f), Y(10f), X(91f), Y(10f), X(102f), Y(17f))
    // 右前翼子板：从 84 宽慢慢外扩到 96 宽
    cubicTo(X(106.5f), Y(22f), X(108f), Y(30f), X(108f), Y(40f))
    // 右侧腰线
    lineTo(X(108f), Y(233f))
    // 右后：小圆角，尾部明显更方
    cubicTo(X(108f), Y(239f), X(105.5f), Y(243f), X(98f), Y(243f))
    // 车尾：平直
    lineTo(X(22f), Y(243f))
    cubicTo(X(14.5f), Y(243f), X(12f), Y(239f), X(12f), Y(233f))
    // 左侧腰线
    lineTo(X(12f), Y(40f))
    cubicTo(X(12f), Y(30f), X(13.5f), Y(22f), X(18f), Y(17f))
    close()
}

/**
 * 前风挡：上窄下宽的梯形，底边直接接到天幕（两边等宽，接缝处不会有台阶）。
 */
private fun windshieldPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    moveTo(X(41f), Y(61f))
    lineTo(X(79f), Y(61f))
    lineTo(X(87f), Y(88f))
    lineTo(X(33f), Y(88f))
    close()
}

/**
 * 后风挡：**比天幕窄一圈**，并与天幕之间留出一段车身色（C 柱 / 顶盖后横梁）。
 *
 * 之前它上沿是 33~87、几乎和天幕（32~88）等宽，两块玻璃看起来像连成一整块、
 * 左右还在同一条竖线上，俯视时很像「对称图形」而不是车 —— 收窄到 38~82 之后，
 * 一眼就能分出「车顶玻璃 / 后窗」两段。梯形继续往下收（45~75），符合车尾收腰。
 */
private fun rearWindowPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    moveTo(X(38f), Y(170f))
    lineTo(X(82f), Y(170f))
    lineTo(X(75f), Y(192f))
    lineTo(X(45f), Y(192f))
    close()
}

/**
 * 天幕（整块玻璃车顶）：盖住车顶，前缘与风挡连成一片。
 *
 * 下沿从 172 收到 164：给后风挡让出 6 个单位（约一张卡片上的 5dp）的车身色，
 * 那是顶盖后横梁的位置，也是「两块玻璃不是同一块」最直接的视觉证据。
 */
private const val ROOF_LEFT = 32f
private const val ROOF_RIGHT = 88f
private const val ROOF_TOP = 84f
private const val ROOF_BOTTOM = 164f

/** 左前车灯：贴着翼子板斜着放，外宽内窄。右前按 X 镜像。 */
private fun headlightPath(X: (Float) -> Float, Y: (Float) -> Float, mirror: Boolean): Path =
    Path().apply {
        fun px(v: Float) = if (mirror) TOP_W - v else v
        moveTo(X(px(16.5f)), Y(31f))
        lineTo(X(px(31f)), Y(21f))
        lineTo(X(px(33.5f)), Y(26.5f))
        lineTo(X(px(19f)), Y(36.5f))
        close()
    }

/** 后备箱盖：尾部一块带缝线的面板。 */
private const val TRUNK_LEFT = 18f
private const val TRUNK_RIGHT = 102f
private const val TRUNK_TOP = 214f
private const val TRUNK_BOTTOM = 238f

/**
 * 透明车模（首页胎压卡用）：只留一层极淡的车身轮廓，四轮按状态着色。
 *
 * 和 [TireTopView] 的深色实心车不同，这里刻意去掉填充感 —— 车身按 5% 不透明度铺底、
 * 边框 22% 描线，卡片底色直接透上来，看起来像一块玻璃模型；真正的信息量全压在四个轮子上。
 *
 * [frontFraction] 是「前轴轮胎中心线在画布高度上的位置比例」（0~1），由调用方按两侧
 * 数值格子的实际中心位置传入 —— 左右两列用 SpaceEvenly 排布时，格子的中心会随字号、
 * 卡片高度变化，把实测值传进来，前后轮才能和四个胎压数字落在同一条水平线上。
 * 后轴默认取对称位置（1 - frontFraction）。
 *
 * 轮子配色跟随「正常就该像真轮胎」的思路：正常时是深灰实心（深色主题下换成浅灰，
 * 否则深灰压在深底上看不见），只有胎压不足才变红并带一圈红色光晕 ——
 * 绿色高亮会让四个轮子看起来一直「在报警」，反而弱化了真正的异常。
 */
@Composable
fun TireCarOutline(
    tires: List<TireInfo>,
    modifier: Modifier = Modifier,
    frontFraction: Float = 0.29f,
) {
    val C = LocalAppColors.current
    val ink = C.textPrimary
    val tireNormal = if (C.isDark) Color(0xFFB0B5BD) else Color(0xFF3A3D44)
    val tireBad = if (C.isDark) Color(0xFFFF6B6F) else Color(0xFFE5484D)
    val tireUnknown = ink.copy(alpha = 0.16f)

    Canvas(modifier = modifier) {
        // 横向按宽度等比；纵向由「前后轴要落在两侧数字中心线上」反推，
        // 差距通常不到 4%，肉眼看不出来，但车轮和数字能严丝合缝对上。
        val scaleX = size.width / TOP_W
        val axleSpanPx = size.height * (1f - 2f * frontFraction)
        val scaleY = if (axleSpanPx > 1f) {
            axleSpanPx / (AXLE_REAR_Y - AXLE_FRONT_Y)
        } else {
            scaleX
        }
        val offX = (size.width - TOP_W * scaleX) / 2f
        val originY = size.height * frontFraction - AXLE_FRONT_Y * scaleY
        fun X(v: Float) = offX + v * scaleX
        fun Y(v: Float) = originY + v * scaleY
        // 矩形/轮胎的宽高必须分别用 scaleX / scaleY，**不能**用两者的平均值：
        // 左边是 X(left) 锚定的，宽度若按平均值算，横向会比车身多出 (scaleY/scaleX-1) 倍，
        // 多出来的部分全堆在右边 —— 表现就是天幕、后尾门整体「偏右」（实测偏 1~3dp）。
        fun W(v: Float) = v * scaleX
        fun Hh(v: Float) = v * scaleY
        /** 线宽、圆角用「两轴平均值」，横竖看都一样粗。 */
        fun S(v: Float) = v * (scaleX + scaleY) / 2f

        // ---- 车身：几乎透明的玻璃壳 ----
        val body = carBodyPath(::X, ::Y)
        drawPath(body, ink.copy(alpha = 0.05f))
        drawPath(body, ink.copy(alpha = 0.22f), style = Stroke(width = S(1.2f)))

        // ---- 前舱盖后缘 / 后备箱前缘：两条很淡的横线，轮廓立刻从「圆角板」变成车 ----
        drawLine(ink.copy(alpha = 0.09f), Offset(X(19f), Y(57f)), Offset(X(101f), Y(57f)), S(1f))
        drawLine(ink.copy(alpha = 0.09f), Offset(X(19f), Y(210f)), Offset(X(101f), Y(210f)), S(1f))

        // ---- 前风挡 ----
        drawPath(windshieldPath(::X, ::Y), ink.copy(alpha = 0.07f))

        // ---- 天幕：一整块玻璃车顶（竖向渐变 + 一道斜向反光）----
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(ink.copy(alpha = 0.15f), ink.copy(alpha = 0.05f)),
                startY = Y(ROOF_TOP),
                endY = Y(ROOF_BOTTOM),
            ),
            topLeft = Offset(X(ROOF_LEFT), Y(ROOF_TOP)),
            size = Size(W(ROOF_RIGHT - ROOF_LEFT), Y(ROOF_BOTTOM) - Y(ROOF_TOP)),
            cornerRadius = CornerRadius(S(14f), S(14f)),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.18f),
            topLeft = Offset(X(ROOF_LEFT), Y(ROOF_TOP)),
            size = Size(W(ROOF_RIGHT - ROOF_LEFT), Y(ROOF_BOTTOM) - Y(ROOF_TOP)),
            cornerRadius = CornerRadius(S(14f), S(14f)),
            style = Stroke(width = S(0.9f)),
        )
        // 反光：从左前斜向右后的一道光带，玻璃感全靠它
        drawLine(
            color = Color.White.copy(alpha = if (C.isDark) 0.10f else 0.55f),
            start = Offset(X(42f), Y(103f)),
            end = Offset(X(58f), Y(133f)),
            strokeWidth = S(3.2f),
        )

        // ---- 后风挡 ----
        drawPath(rearWindowPath(::X, ::Y), ink.copy(alpha = 0.07f))

        // ---- 后备箱盖：尾部面板 + 中缝，车尾一下就认出来了 ----
        drawRoundRect(
            color = ink.copy(alpha = 0.04f),
            topLeft = Offset(X(TRUNK_LEFT), Y(TRUNK_TOP)),
            size = Size(W(TRUNK_RIGHT - TRUNK_LEFT), Y(TRUNK_BOTTOM) - Y(TRUNK_TOP)),
            cornerRadius = CornerRadius(S(6f), S(6f)),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.15f),
            topLeft = Offset(X(TRUNK_LEFT), Y(TRUNK_TOP)),
            size = Size(W(TRUNK_RIGHT - TRUNK_LEFT), Y(TRUNK_BOTTOM) - Y(TRUNK_TOP)),
            cornerRadius = CornerRadius(S(6f), S(6f)),
            style = Stroke(width = S(0.9f)),
        )

        // ---- 两个前车灯：俯视下分辨车头最直接的一笔 ----
        val lampColor = if (C.isDark) Color(0xFFFFE9A8).copy(alpha = 0.55f)
        else Color(0xFFFFC44D).copy(alpha = 0.70f)
        drawPath(headlightPath(::X, ::Y, mirror = false), lampColor)
        drawPath(headlightPath(::X, ::Y, mirror = true), lampColor)

        // ---- 四个轮胎 ----
        TIRE_SLOTS.forEach { (label, slot) ->
            val tire = tires.firstOrNull { it.position == label }
            // 车端置位了异常（欠压/过压/无信号）才算「红」，其余一律按正常轮子画
            val abnormal = tire != null && tire.warning
            val color = when {
                tire == null -> tireUnknown
                abnormal -> tireBad
                else -> tireNormal
            }
            val left = slot[0]
            val top = slot[1]
            // 光晕只给异常轮：正常轮子保持实心轮胎的干净外形
            if (abnormal) {
                drawRoundRect(
                    color.copy(alpha = 0.20f),
                    topLeft = Offset(X(left - 3.5f), Y(top - 3.5f)),
                    size = Size(W(TIRE_W + 7f), Hh(TIRE_H + 7f)),
                    cornerRadius = CornerRadius(S(7f), S(7f)),
                )
            }
            drawRoundRect(
                color,
                topLeft = Offset(X(left), Y(top)),
                size = Size(W(TIRE_W), Hh(TIRE_H)),
                cornerRadius = CornerRadius(S(5f), S(5f)),
            )
        }
    }
}

/**
 * 胎压俯视示意图：车头朝上，四个轮胎按状态着色
 * （正常绿色 / 异常红色 / 无数据灰色）。
 */
@Composable
fun TireTopView(
    tires: List<TireInfo>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val scale = minOf(size.width / TOP_W, size.height / TOP_H)
        val offX = (size.width - TOP_W * scale) / 2f
        val offY = (size.height - TOP_H * scale) / 2f
        fun X(v: Float) = offX + v * scale
        fun Y(v: Float) = offY + v * scale
        fun S(v: Float) = v * scale

        // ---- 车身（俯视轮廓） ----
        val body = carBodyPath(::X, ::Y)
        drawPath(body, Color(0xFF2A2D34))
        drawPath(body, Color(0xFF454A55), style = Stroke(width = S(1.4f)))

        // ---- 前后风挡 ----
        drawPath(windshieldPath(::X, ::Y), Color(0xFF23262C))
        drawPath(rearWindowPath(::X, ::Y), Color(0xFF23262C))

        // ---- 天幕：比车漆更亮的深色玻璃 + 斜向反光 ----
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3A4356), Color(0xFF242B3A)),
                startY = Y(ROOF_TOP),
                endY = Y(ROOF_BOTTOM),
            ),
            topLeft = Offset(X(ROOF_LEFT), Y(ROOF_TOP)),
            size = Size(S(ROOF_RIGHT - ROOF_LEFT), S(ROOF_BOTTOM - ROOF_TOP)),
            cornerRadius = CornerRadius(S(14f), S(14f)),
        )
        drawRoundRect(
            color = Color(0xFF5A6478).copy(alpha = 0.55f),
            topLeft = Offset(X(ROOF_LEFT), Y(ROOF_TOP)),
            size = Size(S(ROOF_RIGHT - ROOF_LEFT), S(ROOF_BOTTOM - ROOF_TOP)),
            cornerRadius = CornerRadius(S(14f), S(14f)),
            style = Stroke(width = S(1f)),
        )
        drawLine(
            color = Color(0xFFB9C6E0).copy(alpha = 0.22f),
            start = Offset(X(42f), Y(101f)),
            end = Offset(X(60f), Y(135f)),
            strokeWidth = S(4f),
        )

        // ---- 后备箱盖 ----
        drawRoundRect(
            color = Color(0xFF32363E),
            topLeft = Offset(X(TRUNK_LEFT), Y(TRUNK_TOP)),
            size = Size(S(TRUNK_RIGHT - TRUNK_LEFT), S(TRUNK_BOTTOM - TRUNK_TOP)),
            cornerRadius = CornerRadius(S(6f), S(6f)),
        )
        drawRoundRect(
            color = Color(0xFF454A55).copy(alpha = 0.8f),
            topLeft = Offset(X(TRUNK_LEFT), Y(TRUNK_TOP)),
            size = Size(S(TRUNK_RIGHT - TRUNK_LEFT), S(TRUNK_BOTTOM - TRUNK_TOP)),
            cornerRadius = CornerRadius(S(6f), S(6f)),
            style = Stroke(width = S(1f)),
        )

        // ---- 两个前车灯 ----
        drawPath(headlightPath(::X, ::Y, mirror = false), Color(0xFFFFF6D6).copy(alpha = 0.85f))
        drawPath(headlightPath(::X, ::Y, mirror = true), Color(0xFFFFF6D6).copy(alpha = 0.85f))

        // ---- 四个轮胎 ----
        TIRE_SLOTS.forEach { (label, slot) ->
            val color = tireStateColor(tires.firstOrNull { it.position == label })
            val left = slot[0]
            val top = slot[1]
            // 外发光
            drawRoundRect(
                color.copy(alpha = 0.18f),
                topLeft = Offset(X(left - 4f), Y(top - 4f)),
                size = Size(S(TIRE_W + 8f), S(TIRE_H + 8f)),
                cornerRadius = CornerRadius(S(7f), S(7f)),
            )
            // 轮胎本体
            drawRoundRect(
                color,
                topLeft = Offset(X(left), Y(top)),
                size = Size(S(TIRE_W), S(TIRE_H)),
                cornerRadius = CornerRadius(S(5f), S(5f)),
            )
            // 轮毂纹理
            drawLine(
                Color(0xFF12141A).copy(alpha = 0.45f),
                start = Offset(X(left + 3f), Y(top + 13f)),
                end = Offset(X(left + 14f), Y(top + 13f)),
                strokeWidth = S(2f),
            )
            drawLine(
                Color(0xFF12141A).copy(alpha = 0.45f),
                start = Offset(X(left + 3f), Y(top + 25f)),
                end = Offset(X(left + 14f), Y(top + 25f)),
                strokeWidth = S(2f),
            )
        }
    }
}
