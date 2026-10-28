package com.leapcontrol.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.widget.VehicleWidget
import com.leapcontrol.app.widget.WidgetBackdrop
import com.leapcontrol.app.widget.WidgetBackdrops

/**
 * 「个性化 → 桌面小组件背景」的选择器。
 *
 * 日间 / 夜间两套分开选：卡面在两种模式下是反过来的（浅底深字 / 深底浅字），
 * 一套图硬撑两边必然有一边难看，所以干脆各挑各的，跟着系统深浅自动换。
 *
 * 第一格永远是「纯色」——那是小组件原本的玻璃卡外观，也是唯一不花钱的选项；
 * 每一格里点一下立刻落盘并重画桌面上的卡片，不用退出去看效果。
 */
@Composable
fun WidgetBackdropPicker(runtime: AppRuntime) {
    val context = LocalContext.current
    val C = LocalAppColors.current
    var dayId by remember { mutableStateOf(WidgetBackdrops.selectedId(context, false)) }
    var nightId by remember { mutableStateOf(WidgetBackdrops.selectedId(context, true)) }

    fun pick(night: Boolean, id: String) {
        WidgetBackdrops.select(context, night, id)
        if (night) nightId = id else dayId = id
        // 传 runtime 进去，避免为了重画一次卡片又新建一个 AppRuntime
        VehicleWidget.render(context, runtime)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackdropGroup(
            title = "日间",
            desc = "系统浅色时使用",
            items = WidgetBackdrops.DAY,
            selectedId = dayId,
            night = false,
            onPick = { pick(false, it) },
        )
        BackdropGroup(
            title = "夜间",
            desc = "系统深色时使用",
            items = WidgetBackdrops.NIGHT,
            selectedId = nightId,
            night = true,
            onPick = { pick(true, it) },
        )
        Text(
            "点桌面小组件上的车模可以换成下一张，走完一轮回到「纯色」。" +
                "图片都做过提亮/压暗处理，文字与车模不会被挡。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun BackdropGroup(
    title: String,
    desc: String,
    items: List<WidgetBackdrop>,
    selectedId: String,
    night: Boolean,
    onPick: (String) -> Unit,
) {
    val C = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.sp)
        }
        // 「纯色」+ 各张风景，按 3 列铺开
        val cells: List<Pair<String, WidgetBackdrop?>> =
            listOf(WidgetBackdrops.NONE to null) + items.map { it.id to it }
        cells.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (id, backdrop) ->
                    BackdropCell(
                        modifier = Modifier.weight(1f),
                        backdrop = backdrop,
                        night = night,
                        selected = id == selectedId,
                        onClick = { onPick(id) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BackdropCell(
    modifier: Modifier,
    backdrop: WidgetBackdrop?,
    night: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(9.dp)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.2f)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) C.accent else Color.Transparent,
                    shape = shape,
                )
                .clickable(onClick = onClick),
        ) {
            if (backdrop == null) {
                // 纯色 = 小组件原本的玻璃卡
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                if (night) listOf(Color(0xFF252A34), Color(0xFF1B1F27))
                                else listOf(Color(0xFFF7FAFF), Color(0xFFE9F0FA)),
                            ),
                        ),
                )
            } else {
                Image(
                    painter = painterResource(backdrop.res),
                    contentDescription = backdrop.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            backdrop?.label ?: "无背景",
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
