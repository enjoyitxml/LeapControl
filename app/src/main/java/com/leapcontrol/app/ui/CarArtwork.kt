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

private val TireOk = Color(0xFF30D158)
private val TireBad = Color(0xFFFF5A5F)
private val TireUnknown = Color(0xFF3A3D44)

/** 四个轮胎在俯视图中的位置：左前 / 右前 / 左后 / 右后 */
private val TIRE_SLOTS = mapOf(
    "左前" to floatArrayOf(18f, 48f),
    "右前" to floatArrayOf(88f, 48f),
    "左后" to floatArrayOf(18f, 170f),
    "右后" to floatArrayOf(88f, 170f),
)

private fun tireStateColor(tire: TireInfo?): Color = when {
    tire == null -> TireUnknown
    tire.warning -> TireBad
    else -> TireOk
}

/** 车身俯视轮廓。位置换算由调用方传入的 X/Y 完成，两个俯视图共用同一套坐标。 */
private fun carBodyPath(X: (Float) -> Float, Y: (Float) -> Float): Path = Path().apply {
    moveTo(X(60f), Y(10f))
    cubicTo(X(72f), Y(10f), X(82f), Y(17f), X(87f), Y(29f))
    lineTo(X(92f), Y(46f))
    lineTo(X(92f), Y(200f))
    cubicTo(X(92f), Y(224f), X(86f), Y(236f), X(74f), Y(240f))
    lineTo(X(46f), Y(240f))
    cubicTo(X(34f), Y(236f), X(28f), Y(224f), X(28f), Y(200f))
    lineTo(X(28f), Y(46f))
    cubicTo(X(33f), Y(17f), X(48f), Y(10f), X(60f), Y(10f))
    close()
}

/**
 * 透明车模（首页胎压卡用）：只留一层极淡的车身轮廓，四轮按状态着色。
 *
 * 和 [TireTopView] 的深色实心车不同，这里刻意去掉填充感 —— 车身按 5% 不透明度铺底、
 * 边框 22% 描线，卡片底色直接透上来，看起来像一块玻璃模型；真正的信息量全压在四个轮子上。
 *
 * 轮子配色跟随「正常就该像真轮胎」的思路：正常时是深灰实心（深色主题下换成浅灰，
 * 否则深灰压在深底上看不见），只有胎压不足才变红并带一圈红色光晕 ——
 * 绿色高亮会让四个轮子看起来一直「在报警」，反而弱化了真正的异常。
 */
@Composable
fun TireCarOutline(
    tires: List<TireInfo>,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val ink = C.textPrimary
    val tireNormal = if (C.isDark) Color(0xFFB0B5BD) else Color(0xFF3A3D44)
    val tireBad = if (C.isDark) Color(0xFFFF6B6F) else Color(0xFFE5484D)
    val tireUnknown = ink.copy(alpha = 0.16f)

    Canvas(modifier = modifier) {
        val scale = minOf(size.width / TOP_W, size.height / TOP_H)
        val offX = (size.width - TOP_W * scale) / 2f
        val offY = (size.height - TOP_H * scale) / 2f
        fun X(v: Float) = offX + v * scale
        fun Y(v: Float) = offY + v * scale
        fun S(v: Float) = v * scale

        // ---- 车身：几乎透明的玻璃壳 ----
        val body = carBodyPath(::X, ::Y)
        drawPath(body, ink.copy(alpha = 0.05f))
        drawPath(body, ink.copy(alpha = 0.22f), style = Stroke(width = S(1.2f)))

        // ---- 前风挡 ----
        drawPath(
            Path().apply {
                moveTo(X(40f), Y(64f))
                lineTo(X(80f), Y(64f))
                lineTo(X(87f), Y(96f))
                lineTo(X(33f), Y(96f))
                close()
            },
            ink.copy(alpha = 0.07f),
        )
        // ---- 车顶（只描线，不填充，保持通透）----
        drawRoundRect(
            color = ink.copy(alpha = 0.16f),
            topLeft = Offset(X(33f), Y(96f)),
            size = Size(S(54f), S(62f)),
            cornerRadius = CornerRadius(S(5f), S(5f)),
            style = Stroke(width = S(1f)),
        )
        // ---- 后风挡 ----
        drawPath(
            Path().apply {
                moveTo(X(33f), Y(158f))
                lineTo(X(87f), Y(158f))
                lineTo(X(80f), Y(184f))
                lineTo(X(40f), Y(184f))
                close()
            },
            ink.copy(alpha = 0.07f),
        )

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
                    topLeft = Offset(X(left - 4.5f), Y(top - 4f)),
                    size = Size(S(24f), S(44f)),
                    cornerRadius = CornerRadius(S(8f), S(8f)),
                )
            }
            // 轮胎比槽位略宽一点（17×36）：参考官图那种粗实的实心胎，
            // 中心仍在 left+7 / top+17，所以位置和旧版完全对齐
            drawRoundRect(
                color,
                topLeft = Offset(X(left - 1.5f), Y(top - 1f)),
                size = Size(S(17f), S(36f)),
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
        drawPath(
            Path().apply {
                moveTo(X(40f), Y(64f))
                lineTo(X(80f), Y(64f))
                lineTo(X(87f), Y(96f))
                lineTo(X(33f), Y(96f))
                close()
            },
            Color(0xFF23262C),
        )
        // ---- 车顶 ----
        drawRoundRect(
            Color(0xFF32363E),
            topLeft = Offset(X(33f), Y(96f)),
            size = Size(S(54f), S(62f)),
            cornerRadius = CornerRadius(S(5f), S(5f)),
        )
        // ---- 后风挡 ----
        drawPath(
            Path().apply {
                moveTo(X(33f), Y(158f))
                lineTo(X(87f), Y(158f))
                lineTo(X(80f), Y(184f))
                lineTo(X(40f), Y(184f))
                close()
            },
            Color(0xFF23262C),
        )

        // ---- 前后灯 ----
        drawRoundRect(
            Color(0xFFFFF6D6).copy(alpha = 0.55f),
            topLeft = Offset(X(35f), Y(16f)),
            size = Size(S(15f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )
        drawRoundRect(
            Color(0xFFFFF6D6).copy(alpha = 0.55f),
            topLeft = Offset(X(70f), Y(16f)),
            size = Size(S(15f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )
        drawRoundRect(
            Color(0xFFFF5A5F).copy(alpha = 0.5f),
            topLeft = Offset(X(34f), Y(228f)),
            size = Size(S(16f), S(6f)),
            cornerRadius = CornerRadius(S(3f), S(3f)),
        )
        drawRoundRect(
            Color(0xFFFF5A5F).copy(alpha = 0.5f),
            topLeft = Offset(X(70f), Y(228f)),
            size = Size(S(16f), S(6f)),
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
                size = Size(S(22f), S(42f)),
                cornerRadius = CornerRadius(S(7f), S(7f)),
            )
            // 轮胎本体
            drawRoundRect(
                color,
                topLeft = Offset(X(left), Y(top)),
                size = Size(S(14f), S(34f)),
                cornerRadius = CornerRadius(S(4f), S(4f)),
            )
            // 轮毂纹理
            drawLine(
                Color(0xFF12141A).copy(alpha = 0.45f),
                start = Offset(X(left + 3f), Y(top + 11f)),
                end = Offset(X(left + 11f), Y(top + 11f)),
                strokeWidth = S(2f),
            )
            drawLine(
                Color(0xFF12141A).copy(alpha = 0.45f),
                start = Offset(X(left + 3f), Y(top + 23f)),
                end = Offset(X(left + 11f), Y(top + 23f)),
                strokeWidth = S(2f),
            )
        }
    }
}
