package com.leapcontrol.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.leapcontrol.app.HomeCardId
import com.leapcontrol.app.HomeCardLayout

/**
 * 首页卡片区：卡片列表 + 「编辑」按钮 + 编辑态（长按拖动排序 / 删除 / 添加）。
 *
 * 拖拽与编辑态的具体实现见 [ReorderableCardArea]（零记也复用它）。
 * 这里只把首页自己的顺序协议（[HomeCardLayout]）接上去。
 */
@Composable
fun HomeCardArea(
    cards: List<HomeCardId>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onCardsChange: (List<HomeCardId>) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    cardContent: @Composable (HomeCardId) -> Unit,
) {
    ReorderableCardArea(
        cards = cards,
        editing = editing,
        onEditingChange = onEditingChange,
        onCardsChange = onCardsChange,
        scrollState = scrollState,
        hidden = HomeCardLayout.hidden(cards),
        titleOf = { it.title },
        modifier = modifier,
        cardContent = cardContent,
    )
}
