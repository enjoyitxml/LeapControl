package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R

/**
 * [HeroLayout.SEPARATE] 的两块拼图，以及个性化里的布局缩略图。
 *
 * ## 这一版为什么又把图标加回来了
 * 「双行文本流」（[HomeStatusText]）把闭锁 / 空调 / 车窗用「·」串成一句，省地方，
 * 但代价是**行驶状态和车辆状态挤在一起**：车在跑的时候，「行驶中 42km/h」和
 * 「已闭锁 · 空调 24°」叠在同一列，主次分不开。
 *
 * [HeroLayout.SEPARATE] 的做法是把两者物理拆开：
 *  - 行驶状态（已驻车 / 行驶中 / 充电中 / 倒车）→ [DrivingStatusBadge]，车模正下方居中；
 *  - 车辆状态（闭锁 / 车窗 / 后备箱 / 遮阳帘 / 空调）→ [VehicleStatusColumn]，
 *    右上角恢复成「图标 + 文字」逐行，一项一行，不再合并成一句话。
 *
 * 颜色沿用 v1.0.16 定下的规矩：**只有未闭锁 / 车窗开 / 后备箱开用红**，
 * 空调和遮阳帘开着是常态，图标和文字都用主色 —— 老版本把四种「已开启」全画成红的，
 * 空调一开就像报了四个故障，那条反馈不该因为「恢复旧样式」再退回去。
 */

// ------------------------------------------------- 右上的车辆状态（旧样式）

/** 右列宽度上限：约占半屏，再长就省略，不能把左边的续航大字挤扁。 */
private val STATUS_COLUMN_MAX = 168.dp

/** 最多铺几行：再多就把左边 40sp 的续航挤得没地方站。 */
private const val STATUS_MAX_ROWS = 4

/**
 * 右上角的车辆状态列：闭锁一行，之后每个「已开启」各占一行。
 *
 * 与 [HomeStatusText] 的区别只有两点：带了图标、一项一行不合并。
 * 判据仍然只看车况信号，拿不到的项直接不画（不留「未知」占位）。
 */
@Composable
internal fun VehicleStatusColumn(status: CarStatus?, modifier: Modifier = Modifier) {
    val rows = rememberVehicleStatusRows(status)
    if (rows.isEmpty()) return
    val visible = if (rows.size > STATUS_MAX_ROWS) rows.take(STATUS_MAX_ROWS - 1) else rows
    val hidden = rows.size - visible.size

    Column(
        modifier = modifier.widthIn(max = STATUS_COLUMN_MAX),
        horizontalAlignment = Alignment.End,
    ) {
        visible.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            VehicleStatusRowView(row)
        }
        if (hidden > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "+$hidden",
                color = LocalAppColors.current.textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
    }
}

/** 一行：16dp 图标 + 15sp 主色文字。红色只由图标承担，文字始终是主色。 */
@Composable
private fun VehicleStatusRowView(row: VehicleStatusRow) {
    val C = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(row.iconRes),
            contentDescription = null,
            tint = if (row.alert) AlertRed else C.textPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.label,
            color = C.textPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal data class VehicleStatusRow(val iconRes: Int, val label: String, val alert: Boolean)

/**
 * 车况 → 右上角那几行（纯函数，可单测）。
 *
 * 顺序和老版本一致：闭锁永远第一行（每天都要确认一遍的基线），
 * 之后才轮到「今天和昨天不一样」的项 —— 车窗、后备箱、遮阳帘、空调。
 */
internal fun vehicleStatusRows(
    locked: Boolean?,
    acOn: Boolean?,
    windowOpen: Boolean?,
    trunkOpen: Boolean?,
    sunshadeOpen: Boolean?,
): List<VehicleStatusRow> = buildList {
    when (locked) {
        true -> add(VehicleStatusRow(R.drawable.ic_qc_lock, "车辆已闭锁", false))
        false -> add(VehicleStatusRow(R.drawable.ic_qc_unlock, "车辆未闭锁", true))
        // 闭锁信号缺失时不写「状态未知」：占一整行却没有可操作价值
        null -> Unit
    }
    if (windowOpen == true) add(VehicleStatusRow(R.drawable.ic_qc_window, "车窗已开启", true))
    if (trunkOpen == true) add(VehicleStatusRow(R.drawable.ic_qc_trunk, "后备箱已开启", true))
    if (sunshadeOpen == true) add(VehicleStatusRow(R.drawable.ic_qc_sunshade, "遮阳帘已开启", false))
    if (acOn == true) add(VehicleStatusRow(R.drawable.ic_qc_ac, "空调已开启", false))
}

@Composable
private fun rememberVehicleStatusRows(status: CarStatus?): List<VehicleStatusRow> =
    remember(status) {
        if (status == null) {
            emptyList()
        } else {
            vehicleStatusRows(
                locked = status.locked,
                acOn = status.acOn,
                windowOpen = status.windowOpen,
                trunkOpen = status.trunkOpen,
                sunshadeOpen = status.sunshadeOpen,
            )
        }
    }

// --------------------------------------------- 车模正下方的行驶状态

/**
 * 车模正下方居中的行驶状态：`● 已驻车` / `● 行驶中 42 km/h` / `● 充电中 剩 42 分钟`。
 *
 * 只画**行驶状态这一件事**，闭锁空调那些留在右上角 —— 这就是 [HeroLayout.SEPARATE]
 * 和「双行文本流」的全部差别。
 *
 * 形态是一颗没有底板描边的轻胶囊（`chipBg`），和页面其它胶囊同源；
 * 判不出挡位和车速时整条不画（[HomeStatusLines] 在没有 badge 时会退回闭锁说法，
 * 那种情况右上角已经在说了，这里再重复一遍就是两句一样的话）。
 */
@Composable
internal fun DrivingStatusBadge(status: CarStatus?, modifier: Modifier = Modifier) {
    val view = rememberHomeStatusView(status)
    // 没有 badge 时 primary 会是「已闭锁」这类闭锁说法，那是右上角的活儿，这里不画
    if (view.tone == null) return
    val C = LocalAppColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(C.chipBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(toneColor(view.tone, C)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = view.primary,
            color = C.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
        )
        view.detail?.let {
            Spacer(Modifier.width(5.dp))
            Text(
                text = it,
                color = C.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ------------------------------------------------------------ 个性化里的缩略图

/**
 * 个性化里的两张布局缩略图（单选）。
 *
 * 每张只画「东西在哪儿」：几条灰块代表文字，一块浅色圆角代表车。
 * 不画真字 —— 缩略图只有 100dp 宽，写字只会糊成一团，位置关系才是要传达的信息。
 */
@Composable
internal fun HeroLayoutCards(selected: HeroLayout, onSelect: (HeroLayout) -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeroLayout.entries.forEach { layout ->
            val active = layout == selected
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (active) C.chipBgActive else C.chipBg)
                    .border(
                        if (active) 1.5.dp else 1.dp,
                        if (active) C.accent else C.border,
                        shape,
                    )
                    .clickable { onSelect(layout) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HeroLayoutThumb(layout)
                    if (active) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(C.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(9.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = layout.label,
                    color = if (active) C.accent else C.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 缩略图内部：两套排布各画几块信息位 + 一块车。 */
@Composable
private fun HeroLayoutThumb(layout: HeroLayout) {
    val C = LocalAppColors.current
    val line = C.textPrimary.copy(alpha = 0.55f)
    val faint = C.textSecondary.copy(alpha = 0.35f)
    val car = C.textPrimary.copy(alpha = 0.26f)
    when (layout) {
        // 经典：左上续航、右上状态、中间车、底部四个钮
        HeroLayout.CLASSIC -> Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 6.dp, start = 4.dp)
                    .width(30.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(line),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(faint),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 4.dp)
                    .width(46.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(car),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(faint),
                    )
                }
            }
        }

        // 状态分置：车下多一颗胶囊（行驶状态），右上仍是几行状态
        HeroLayout.SEPARATE -> Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 6.dp, start = 4.dp)
                    .width(30.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(line),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(faint),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 2.dp)
                    .width(46.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(car),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 34.dp)
                    .width(30.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(car.copy(alpha = 0.5f)),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(faint),
                    )
                }
            }
        }
    }
}
