package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 通用卡片容器（模块、列表分组）。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(C.cardBg, shape)
            .border(1.dp, C.border, shape)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                color = C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** 顶栏用的小圆钮（半透明底，跟随主题）。 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: androidx.compose.ui.graphics.Color = LocalAppColors.current.textPrimary,
) {
    val C = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) C.chipBgActive else C.iconBg, CircleShape)
            .border(1.dp, C.border, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/**
 * 底部导航项。图标二选一：[icon] 用内置 Material 矢量，[iconRes] 用自绘 drawable。
 */
data class NavItem(
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
)

/**
 * 底部导航（参照主流车企 App 的样式）：
 * 平铺通栏、图标在上文字在下；未选中浅灰，选中深色（深色模式下为近白）。
 */
@Composable
fun DockBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    items: List<NavItem>,
) {
    val C = LocalAppColors.current
    val activeColor = if (C.isDark) Color(0xFFF2F2F4) else Color(0xFF14161A)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = C.dockBg,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(58.dp),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint = if (selected) activeColor else C.dockUnselected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when {
                            item.iconRes != null -> Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(26.dp),
                            )

                            item.icon != null -> Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
    )
}

/** 列表行容器（「我的」页设置项）。 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    val base = modifier
        .fillMaxWidth()
        .background(C.cardBg, shape)
        .border(1.dp, C.border, shape)
    Box(
        modifier = if (onClick != null) {
            base.clip(shape).clickable(onClick = onClick)
        } else {
            base
        },
    ) { content() }
}

/** 选项行（设置页单选）。 */
@Composable
fun OptionSurface(
    selected: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) C.chipBgActive else C.chipBg, shape)
            .border(1.dp, if (selected) C.accent.copy(alpha = 0.45f) else C.border, shape),
    ) { content() }
}
