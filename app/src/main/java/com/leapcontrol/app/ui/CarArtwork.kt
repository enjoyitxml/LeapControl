package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 车辆图形素材。
 *
 * 内置剪影（CarSilhouette）已按需求删除：官方 carpicture 接口拉不到车模图时
 * 首页直接不显示车模，不再用矢量剪影兜底。这里只保留胎压用的俯视图。
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
 * 刻意避免「两头都是大圆角」的胶囊感 —— 那是模型车，不是真车：
 *  - 车头：前缘几乎是平的，只在两端做小圆角，然后由翼子板向前外扩（前窄后宽）；
 *  - 车身：两侧腰线基本笔直，中间几乎没有弧度；
 *  - 车尾：比车头更宽、更方，只做很小的收角。
 */
private fun carBodyPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    // 车头前缘：几乎是直线，两角只做很小的圆角（大圆角会立刻变成「胶囊」）
    moveTo(26f, 6.5f)
    cubicTo(X(40f), Y(5.5f), X(80f), Y(5.5f), X(94f), Y(6.5f))
    // 右前翼子板：前脸本来就接近全宽，只在一小段里外扩
    cubicTo(X(101f), Y(8f), X(106f), Y(15f), X(107f), Y(26f))
    // 右侧腰线：笔直
    lineTo(X(108f), Y(198f))
    // 右后：只在最后收一点，圆角比车头更小
    cubicTo(X(108f), Y(216f), X(107.5f), Y(228f), X(105f), Y(235f))
    cubicTo(X(103f), Y(240f), X(100f), Y(242.5f), X(94f), Y(243.5f))
    // 车尾：平直、比车头略宽
    lineTo(X(26f), Y(243.5f))
    cubicTo(X(20f), Y(242.5f), X(17f), Y(240f), X(15f), Y(235f))
    cubicTo(X(12.5f), Y(228f), X(12f), Y(216f), X(12f), Y(198f))
    // 左侧腰线
    lineTo(X(13f), Y(26f))
    cubicTo(X(14f), Y(15f), X(19f), Y(8f), X(26f), Y(6.5f))
    close()
}

/** 前风挡（上窄下宽的梯形）与后风挡共用一套坐标，两个俯视图一致。 */
private fun windshieldPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    moveTo(X(41f), Y(63f))
    lineTo(X(79f), Y(63f))
    lineTo(X(87f), Y(96f))
    lineTo(X(33f), Y(96f))
    close()
}

private fun rearWindowPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    moveTo(X(33f), Y(158f))
    lineTo(X(87f), Y(158f))
    lineTo(X(79f), Y(184f))
    lineTo(X(41f), Y(184f))
    close()
}

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
        fun S(v: Float) = v * (scaleX + scaleY) / 2f

        // ---- 车身：几乎透明的玻璃壳 ----
        val body = carBodyPath(::X, ::Y)
        drawPath(body, ink.copy(alpha = 0.05f))
        drawPath(body, ink.copy(alpha = 0.22f), style = Stroke(width = S(1.2f)))

        // ---- 引擎盖缝 / 行李箱缝：两条很淡的横线，轮廓立刻从「圆角板」变成车 ----
        drawLine(ink.copy(alpha = 0.10f), Offset(X(18f), Y(32f)), Offset(X(102f), Y(32f)), S(1f))
        drawLine(ink.copy(alpha = 0.10f), Offset(X(19f), Y(216f)), Offset(X(101f), Y(216f)), S(1f))

        // ---- 前风挡 ----
        drawPath(windshieldPath(::X, ::Y), ink.copy(alpha = 0.07f))
        // ---- 车顶（只描线，不填充，保持通透）----
        drawRoundRect(
            color = ink.copy(alpha = 0.16f),
            topLeft = Offset(X(33f), Y(96f)),
            size = Size(S(54f), S(62f)),
            cornerRadius = CornerRadius(S(7f), S(7f)),
            style = Stroke(width = S(1f)),
        )
        // ---- 后风挡 ----
        drawPath(rearWindowPath(::X, ::Y), ink.copy(alpha = 0.07f))

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
                    size = Size(S(TIRE_W + 7f), S(TIRE_H + 7f)),
                    cornerRadius = CornerRadius(S(7f), S(7f)),
                )
            }
            drawRoundRect(
                color,
                topLeft = Offset(X(left), Y(top)),
                size = Size(S(TIRE_W), S(TIRE_H)),
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
        // ---- 车顶 ----
        drawRoundRect(
            Color(0xFF32363E),
            topLeft = Offset(X(33f), Y(96f)),
            size = Size(S(54f), S(62f)),
            cornerRadius = CornerRadius(S(7f), S(7f)),
        )
        // ---- 后风挡 ----
        drawPath(rearWindowPath(::X, ::Y), Color(0xFF23262C))

        // ---- 前后灯 ----
        drawRoundRect(
            Color(0xFFFFF6D6).copy(alpha = 0.55f),
            topLeft = Offset(X(21f), Y(14f)),
            size = Size(S(18f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )
        drawRoundRect(
            Color(0xFFFFF6D6).copy(alpha = 0.55f),
            topLeft = Offset(X(81f), Y(14f)),
            size = Size(S(18f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )
        drawRoundRect(
            Color(0xFFFF5A5F).copy(alpha = 0.5f),
            topLeft = Offset(X(19f), Y(231f)),
            size = Size(S(18f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )
        drawRoundRect(
            Color(0xFFFF5A5F).copy(alpha = 0.5f),
            topLeft = Offset(X(83f), Y(231f)),
            size = Size(S(18f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )

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
