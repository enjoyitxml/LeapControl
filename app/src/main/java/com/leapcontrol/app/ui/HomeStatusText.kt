package com.leapcontrol.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.DrivingBadge
import com.leapcontrol.app.DrivingStateRules
import com.leapcontrol.app.DrivingTone
import java.util.Locale

/**
 * 首页右上角的车辆状态区 —— **双行文本流**（用户 2026-11-05 从 14 个方案里选定的第 2 号）。
 *
 * ## 为什么推倒重来
 * 之前这里是「驾驶状态胶囊 + 车辆已闭锁（图标+文字）+ 若干已开启提醒（图标+文字）」
 * 三套不同外形硬堆成一列：胶囊有内边距、裸文字没有，右边缘实际参差；整列没有容器，
 * 直接浮在夜景上，越往下越糊；而且所有「已开启」项都走警示色，空调开着看着像报警。
 *
 * 现在的结构：
 * - **第一行**：5dp 圆点 + 15sp 主状态（已驻车 / 行驶中 / 充电中…），后面可跟 11sp 小字数值
 *   （`42 km/h`、`剩 42 分钟`）；
 * - **第二行**：11sp 次要色，把闭锁 / 空调 / 车窗 / 后备箱 / 遮阳帘用「 · 」串成一句。
 *   只有真需要看的问题（未闭锁、车窗开、后备箱开）才转红，其余一律灰色 —— 这才是
 *   「空调开着是常态，不该报警」的修正。
 *
 * 两行都右对齐、宽度上限 [MAX_WIDTH]，所以左边缘不齐也无所谓：右边缘是齐的。
 * 判据来自 [DrivingStateRules.badge]（挡位 / 车速 / 充电状态），判不出来就退回闭锁说法，
 * 绝不写「状态未知」占一行。
 */
@Composable
internal fun HomeStatusText(status: CarStatus?, modifier: Modifier = Modifier) {
    val C = LocalAppColors.current
    val view = rememberHomeStatusView(status)

    Column(
        modifier = modifier.widthIn(max = MAX_WIDTH),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(toneColor(view.tone, C)))
            Spacer(Modifier.width(6.dp))
            Text(
                text = view.primary,
                color = C.textPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false,
            )
            if (view.detail != null) {
                Spacer(Modifier.width(5.dp))
                // 数值比结论轻一档：`行驶中 42 km/h` 的重心应该落在「行驶中」上，
                // 也不至于像「已驻车」这种没有数值时右侧空一块
                Text(
                    text = view.detail,
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (view.tokens.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildAnnotatedString {
                    view.tokens.forEachIndexed { index, token ->
                        if (index > 0) {
                            withStyle(SpanStyle(color = C.textSecondary.copy(alpha = 0.55f))) {
                                append(" · ")
                            }
                        }
                        withStyle(
                            SpanStyle(
                                color = when (token.emphasis) {
                                    StatusEmphasis.ALERT -> AlertRed
                                    StatusEmphasis.MUTED -> C.textSecondary.copy(alpha = 0.7f)
                                    StatusEmphasis.NORMAL -> C.textSecondary
                                },
                            ),
                        ) { append(token.text) }
                    }
                },
                fontSize = 11.sp,
                // 三条以上就折到第二行，超过两行省略：这一列只有半屏宽，
                // 撑高了会把左边的续航大字挤扁
                lineHeight = 15.sp,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 右列宽度上限：约为页面的一半，超长的「剩 1 小时 15 分钟」宁可省略也不挤左边。 */
private val MAX_WIDTH = 168.dp

/**
 * 把车况折成状态区要渲染的内容。
 *
 * 抽出来是因为有两个调用方（经典右上角的 [HomeStatusText]、分置版车模下方的
 * [DrivingStatusBadge]），两处必须**同源**：以后加一项状态只改 [HomeStatusLines.build]
 * 一处，不会出现「车模下能看到、右上角看不到」这种分裂。
 */
@Composable
internal fun rememberHomeStatusView(status: CarStatus?): HomeStatusView =
    // 数据类做 key：车况刷新时整体换一个新对象，等价于按内容比对
    remember(status) {
        HomeStatusLines.build(
            badge = status?.let {
                DrivingStateRules.badge(
                    gearStatus = it.gearStatus,
                    speedKmh = DrivingStateRules.parseSpeed(it.speed),
                    chargeState = it.chargeState,
                    chargeRemainMinutes = it.chargeRemainMinutes,
                    chargeTimeLeft = it.chargeTimeLeft,
                    isShutDown = it.isShutDown,
                )
            },
            locked = status?.locked,
            acOn = status?.acOn,
            acTemp = status?.acTargetTemp,
            windowOpen = status?.windowOpen,
            trunkOpen = status?.trunkOpen,
            sunshadeOpen = status?.sunshadeOpen,
        )
    }

/** 行驶中不抢眼但要有生气；充电沿用首页电量条的绿；倒车 / 故障用琥珀。 */
private val ChargeGreen = Color(0xFF2FCC71)
private val Amber = Color(0xFFE0A048)

/** 真需要看一眼的问题（未闭锁 / 车窗开 / 后备箱开）。三个布局共用同一个红。 */
internal val AlertRed = Color(0xFFE5484D)

internal fun toneColor(tone: DrivingTone?, C: AppColors): Color = when (tone) {
    DrivingTone.MOVING -> C.accent
    DrivingTone.PARKED, null -> C.accent
    DrivingTone.CHARGING -> ChargeGreen
    DrivingTone.REVERSING -> Amber
    DrivingTone.WARNING -> AlertRed
}

/** 一行里每个词的强调等级（纯逻辑，见 [HomeStatusLines]）。 */
internal enum class StatusEmphasis { NORMAL, ALERT, MUTED }

internal data class StatusToken(val text: String, val emphasis: StatusEmphasis = StatusEmphasis.NORMAL)

/** 状态区要渲染的两行内容。 */
internal data class HomeStatusView(
    val primary: String,
    val detail: String? = null,
    val tone: DrivingTone? = null,
    val tokens: List<StatusToken> = emptyList(),
)

/**
 * 把车况字段折成「一行结论 + 一行从属项」（纯函数，可单测）。
 * 规则：
 * - 有 [DrivingBadge] → 结论取它，闭锁降级为从属项；
 * - 没有 → 说明挡位和车速都没有，这时只靠闭锁也能说清（`已闭锁` / `未闭锁`），
 *   不再写「状态未知」；
 * - 空调 / 遮阳帘「开着」是常态，用普通次要色；车窗开、后备箱开、未闭锁才转红。
 */
internal object HomeStatusLines {

    fun build(
        badge: DrivingBadge?,
        locked: Boolean?,
        acOn: Boolean?,
        acTemp: String?,
        windowOpen: Boolean?,
        trunkOpen: Boolean?,
        sunshadeOpen: Boolean?,
    ): HomeStatusView {
        val lockToken = when (locked) {
            true -> StatusToken("已闭锁")
            false -> StatusToken("未闭锁", StatusEmphasis.ALERT)
            null -> StatusToken("闭锁未知", StatusEmphasis.MUTED)
        }
        val openTokens = buildList {
            if (acOn == true) add(StatusToken(acText(acTemp)))
            if (windowOpen == true) add(StatusToken("车窗开", StatusEmphasis.ALERT))
            if (trunkOpen == true) add(StatusToken("后备箱开", StatusEmphasis.ALERT))
            if (sunshadeOpen == true) add(StatusToken("遮阳帘开"))
        }
        return if (badge != null) {
            HomeStatusView(
                primary = badge.label,
                detail = badge.detail,
                tone = badge.tone,
                tokens = listOf(lockToken) + openTokens,
            )
        } else {
            HomeStatusView(primary = lockToken.text, tokens = openTokens)
        }
    }

    /** `24.0` / `24°` / `24℃` 都说成「空调 24°」；温度拿不到就只说「空调开」。 */
    fun acText(raw: String?): String {
        val cleaned = raw
            ?.trim()
            ?.removeSuffix("°C")
            ?.removeSuffix("℃")
            ?.removeSuffix("°")
            ?.trim()
        val value = cleaned?.toDoubleOrNull()
        val label = when {
            value == null -> cleaned?.takeIf { it.isNotEmpty() }
            value % 1.0 == 0.0 -> String.format(Locale.CHINA, "%.0f", value)
            else -> String.format(Locale.CHINA, "%.1f", value)
        }
        return if (label != null) "空调 $label°" else "空调开"
    }
}
