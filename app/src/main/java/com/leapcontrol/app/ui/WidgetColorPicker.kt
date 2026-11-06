package com.leapcontrol.app.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.widget.WidgetStyle
import kotlin.math.abs

/**
 * 「字体颜色 / 控件颜色」一行：8 个预设色 + 一个自定义取色器。
 *
 * 为什么给预设而不是只给取色器：
 * 小组件是**没有底**的，字压在用户自己的壁纸上，能看清的其实只有「反差够大」那几种颜色。
 * 预设里白 / 浅灰 / 灰 / 黑是「跟壁纸反着来」的四档，绿蓝橙红是强调色；
 * 真想要某个特定色再点最后一个圆环展开取色器 —— 两条路都留着。
 */
@Composable
fun WidgetColorRow(
    title: String,
    desc: String,
    color: Int,
    onPick: (Int) -> Unit,
) {
    val C = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // 当前色值直接写出来：用户反馈「选完不知道到底生效没有」时，一眼能对
            Text(
                text = "#%06X".format(color and 0xFFFFFF),
                color = C.textSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(text = desc, color = C.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PRESET_COLORS.forEach { preset ->
                ColorDot(
                    color = preset,
                    selected = (preset and 0xFFFFFF) == (color and 0xFFFFFF),
                    onClick = {
                        expanded = false
                        onPick(preset)
                    },
                )
            }
            CustomDot(
                color = color,
                selected = PRESET_COLORS.none { (it and 0xFFFFFF) == (color and 0xFFFFFF) } && expanded,
                active = expanded,
                onClick = { expanded = !expanded },
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            HsvPicker(color = color, onPick = onPick)
            Spacer(Modifier.height(6.dp))
            Text(
                "拖上面的方块调深浅，拖下面的彩条换色相。",
                color = C.textSecondary,
                fontSize = 10.5.sp,
            )
        }
    }
}

/** 预设色：先「跟壁纸反着来」的四档中性色，再四个强调色。 */
private val PRESET_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFFC9CED6.toInt(),
    0xFF5A626E.toInt(),
    0xFF12141A.toInt(),
    0xFF3DD68C.toInt(),
    0xFF6FBEFF.toInt(),
    0xFFFFA94D.toInt(),
    0xFFFF6B6B.toInt(),
)

/**
 * 一个色点。
 *
 * 选中态是「外圈主题色环 + 内圈留 2dp 空隙」而不是打个勾：
 * 白点、黑点上都画不出能看清的勾，环在任何颜色下都一样明显。
 */
@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(color))
            // 白点放在浅色面板上会「消失」，所以每个点都带一圈极淡描边
            .border(1.dp, C.border, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, C.accent, CircleShape),
            )
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(18.dp)
                    .border(2.dp, if (WidgetStyle.isLight(color)) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.75f), CircleShape),
            )
        }
    }
}

/** 最后一个圆环：彩虹描边 = 「自己调」。展开时环变主题色。 */
@Composable
private fun CustomDot(color: Int, selected: Boolean, active: Boolean, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(1.dp, C.border, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFF6B6B), Color(0xFFFFA94D), Color(0xFF3DD68C),
                        Color(0xFF4FA8FF), Color(0xFFB197FC),
                    ),
                ),
            )
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                1.dp,
                if (active || selected) C.accent else Color.Transparent,
                CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * 简易 HSV 取色器：上面是「饱和度 × 明度」方块，下面是色相条。
 *
 * 不用输入框写十六进制：手机键盘上敲 #RRGGBB 太慢，而且敲错了也没有反馈。
 * 拖拽只改内部状态、每次变化把结果抛给 [onPick]；[LaunchedEffect] 只在外部颜色
 * （点预设色 / 换另一个色）真的变了时才把状态拉回来 —— 否则拖动过程中会自己弹回去。
 */
@Composable
private fun HsvPicker(color: Int, onPick: (Int) -> Unit) {
    val initial = remember { FloatArray(3).also { AndroidColor.colorToHSV(color or 0xFF000000.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }

    // 外部换了颜色才同步；自己拖出来的颜色跟当前状态一致，不会走进来
    LaunchedEffect(color) {
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color or 0xFF000000.toInt(), it) }
        if (abs(hsv[0] - hue) > 0.5f || abs(hsv[1] - sat) > 0.03f || abs(hsv[2] - value) > 0.03f) {
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    fun emit() {
        onPick(
            AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)) or 0xFF000000.toInt(),
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(12.dp)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(shape)
                .pointerInput(Unit) {
                    fun apply(pos: Offset) {
                        sat = (pos.x / size.width).coerceIn(0f, 1f)
                        value = (1f - pos.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                    detectDragGestures(
                        onDragStart = { apply(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            apply(change.position)
                        },
                    )
                },
        ) {
            val pure = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRect(color = pure, size = size)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
                size = size,
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                size = size,
            )
            val x = sat * size.width
            val y = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = 8.5.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    fun apply(pos: Offset) {
                        hue = (pos.x / size.width).coerceIn(0f, 0.999f) * 360f
                        emit()
                    }
                    detectDragGestures(
                        onDragStart = { apply(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            apply(change.position)
                        },
                    )
                },
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                        Color(0xFFFF0000),
                    ),
                ),
                size = Size(size.width, size.height),
            )
            // 当前色相的游标：一根白色细竖线 + 两侧暗边，任何色相上都看得见
            val x = (hue / 360f) * size.width
            drawRect(
                color = Color.White,
                topLeft = Offset(x - 1.dp.toPx(), 0f),
                size = Size(2.dp.toPx(), size.height),
            )
        }
    }
}
