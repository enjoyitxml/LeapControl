package com.leapcontrol.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ScrollState
import com.leapcontrol.app.HomeCardId
import com.leapcontrol.app.HomeCardLayout

/** 卡片之间的间距，[HomeCardArea] 的拖拽位移计算直接用这个值。 */
private val CardGap = 12.dp

/**
 * 首页卡片区：卡片列表 + 「编辑」按钮 + 编辑态（长按拖动排序 / 删除 / 添加）。
 *
 * ## 为什么自己写拖拽而不是用现成的库
 * 首页是 `Column + verticalScroll`，不是 LazyColumn；把整页改成 LazyColumn 要重排
 * 车模、快捷控车这些非卡片内容，风险远大于收益。这里利用「卡片等宽、顺序渲染」的特点，
 * 每张卡的高度实测一次后就能算出所有槽位的纵向位置，拖拽只要维护一个 `dragOffset`：
 *   - 被拖的卡走 `graphicsLayer.translationY`（只重绘，不重排、不重组）；
 *   - 其余卡按「旧位置 → 目标位置」做 `animateFloatAsState` 让位；
 *   - 松手时只在**一次**回调里提交新顺序，避免拖拽过程中反复写 SharedPreferences。
 *
 * ## 编辑态的交互
 * 编辑时每张卡上盖一层透明的手势层，卡片内部的按钮（空调开关等）因此全部失效 ——
 * 这正是编辑态想要的（不然长按拖到一半会顺手把空调打开）。删除按钮盖在手势层之上。
 * 长按卡片 → 卡片跟手浮起 → 拖到目标位置松手 → 顺序落盘。
 *
 * ## 自动滚动
 * 卡片区通常比一屏长。拖到屏幕上下边缘附近时，滚动容器会持续滚动，
 * `dragOffset` 同步补偿滚动量，卡片才会「贴着手指」而不是被页面甩开。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val C = LocalAppColors.current
    val density = LocalDensity.current
    val gapPx = with(density) { CardGap.toPx() }
    // 屏幕可用高度（px）：只在自动滚动判断「拖到没拖到边缘」时用，取配置里的 dp 换算即可
    val windowHeight = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    // 每张卡的实测高度（px）。高度只在首次布局时写一次，拖拽期间不再变。
    val heights = remember { mutableStateMapOf<HomeCardId, Int>() }
    var dragging by remember { mutableStateOf<HomeCardId?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var areaTopInWindow by remember { mutableFloatStateOf(0f) }

    /** 第 [index] 张卡片相对卡片区顶部的纵向偏移（前面的卡 + 间距）。 */
    fun topOf(index: Int): Float {
        var y = 0f
        for (i in 0 until index) {
            y += (heights[cards.getOrNull(i)] ?: 0) + gapPx
        }
        return y
    }

    /**
     * 目标槽位：数「有几张卡的中心点还在被拖卡片中心之上」。
     * 用中心点而不是边界，短卡拖过半屏也不会提前跳位。
     */
    fun computeTarget(id: HomeCardId): Int {
        val index = cards.indexOf(id)
        if (index < 0) return 0
        val height = heights[id] ?: return index
        val center = topOf(index) + height / 2f + dragOffset
        var count = 0
        cards.forEachIndexed { i, other ->
            if (other != id) {
                val otherCenter = topOf(i) + (heights[other] ?: 0) / 2f
                if (otherCenter < center) count++
            }
        }
        return count.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    }

    fun startDrag(id: HomeCardId) {
        if (heights[id] == null) return
        dragging = id
        dragOffset = 0f
        targetIndex = cards.indexOf(id)
    }

    fun commitDrag() {
        val id = dragging
        val from = if (id != null) cards.indexOf(id) else -1
        if (id != null && from >= 0 && targetIndex in cards.indices && targetIndex != from) {
            onCardsChange(HomeCardLayout.move(cards, from, targetIndex))
        }
        dragging = null
        dragOffset = 0f
        targetIndex = -1
    }

    // 退出编辑态时把拖拽状态清干净（否则下次进来会残留一个位移）
    LaunchedEffect(editing) {
        if (!editing) {
            dragging = null
            dragOffset = 0f
            targetIndex = -1
        }
    }

    // 拖到上下边缘附近时持续滚动，并把滚动量补进 dragOffset（卡片才跟得住手指）
    LaunchedEffect(dragging) {
        val id = dragging ?: return@LaunchedEffect
        val stepPx = with(density) { 9.dp.toPx() }
        val topEdge = with(density) { 210.dp.toPx() }
        val bottomEdge = with(density) { 150.dp.toPx() }
        while (true) {
            withFrameNanos { }
            if (dragging != id) break
            val index = cards.indexOf(id)
            val height = heights[id] ?: 0
            if (index < 0) break
            val centerY = areaTopInWindow + topOf(index) + height / 2f + dragOffset
            val step = when {
                centerY < topEdge -> -stepPx
                centerY > windowHeight - bottomEdge -> stepPx
                else -> 0f
            }
            if (step != 0f) {
                val consumed = scrollState.dispatchRawDelta(step)
                if (consumed != 0f) {
                    dragOffset += consumed
                    targetIndex = computeTarget(id)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { areaTopInWindow = it.positionInWindow().y },
    ) {
        val dragIndex = if (dragging != null) cards.indexOf(dragging) else -1
        cards.forEachIndexed { index, id ->
            key(id) {
                if (index > 0) Spacer(Modifier.height(CardGap))
                val isDragging = dragging == id
                // 让位方向：被拖卡片往下走，中间的卡上移一格；往上走则下移一格
                val shiftTarget = when {
                    isDragging || dragIndex < 0 || targetIndex < 0 -> 0f
                    targetIndex > dragIndex && index in (dragIndex + 1)..targetIndex ->
                        -((heights[dragging] ?: 0) + gapPx)
                    targetIndex < dragIndex && index in targetIndex until dragIndex ->
                        ((heights[dragging] ?: 0) + gapPx)
                    else -> 0f
                }
                val shift by animateFloatAsState(
                    targetValue = shiftTarget,
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                    label = "card-shift",
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else shift
                            if (isDragging) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shape = RoundedCornerShape(8.dp)
                                shadowElevation = with(density) { 18.dp.toPx() }
                            }
                        }
                        .onGloballyPositioned { coords -> heights[id] = coords.size.height },
                ) {
                    // 卡片本体
                    cardContent(id)

                    if (editing) {
                        // 注意：这里必须用 matchParentSize 而不是 fillMaxSize ——
                        // 首页整页在 verticalScroll 里，纵向约束是无限的，fillMaxSize 撑不出高度，
                        // 手势层会变成 0dp 高、长按完全收不到事件。
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isDragging) C.accent.copy(alpha = 0.55f) else C.border,
                                    shape = RoundedCornerShape(8.dp),
                                ),
                        )
                        // 手势层：盖住卡片，长按后拖动排序（编辑态里卡片内的按钮全部让位）
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(id, cards) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { startDrag(id) },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            targetIndex = computeTarget(id)
                                        },
                                        onDragEnd = { commitDrag() },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = 0f
                                            targetIndex = -1
                                        },
                                    )
                                },
                        )
                        // 删除钮：盖在手势层之上，点它不会触发拖动
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onCardsChange(cards.filterNot { it == id }) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "删除卡片",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(CardGap))

        if (!editing) {
            EditCardsButton(onClick = { onEditingChange(true) })
        } else {
            EditFooter(
                cards = cards,
                onAdd = { added -> onCardsChange(cards + added) },
                onDone = { onEditingChange(false) },
            )
        }
    }
}

/** 「编辑」按钮：卡区最底下的一条虚线感按钮（用细边框 + 极淡底色替代）。 */
@Composable
private fun EditCardsButton(onClick: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.textPrimary.copy(alpha = 0.03f))
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = C.textSecondary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "编辑",
            color = C.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 编辑态底部：添加卡片 + 完成。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditFooter(
    cards: List<HomeCardId>,
    onAdd: (HomeCardId) -> Unit,
    onDone: () -> Unit,
) {
    val C = LocalAppColors.current
    val hidden = HomeCardLayout.hidden(cards)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.textPrimary.copy(alpha = 0.03f))
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "长按卡片拖动排序",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    // 与「保存并下发设置」同一种实心按钮：深底浅字，深浅色主题下都够清楚
                    .background(C.textPrimary)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = C.panelBg,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "完成",
                    color = C.panelBg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "添加卡片",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hidden.forEach { card ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.panelBg, RoundedCornerShape(6.dp))
                            .border(1.dp, C.border, RoundedCornerShape(6.dp))
                            .clickable { onAdd(card) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = C.accent,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = card.title,
                            color = C.textPrimary,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }
    }
}
