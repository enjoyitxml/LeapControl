package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.journal.City
import com.leapcontrol.app.journal.CityCatalog
import com.leapcontrol.app.journal.CityCheckin
import com.leapcontrol.app.journal.CityProjection
import com.leapcontrol.app.journal.JournalDates
import java.time.LocalDate

/** 小红旗的颜色（不跟主题走：旗子就是红的，换了色反而不像打卡）。 */
private val FlagRed = Color(0xFFE2503C)

/**
 * 用车打卡页。
 *
 * 「去过哪些城市」这件事，车端不提供、也不适合为了它接一个地图 SDK（体积 + 凭据），
 * 所以这里用一个**点阵足迹图**：所有收录的城市按经纬度铺开（暗点），
 * 去过的城市点亮并插一面小红旗。
 *
 * 合规说明：只投点，不画行政边界、国界线、陆地轮廓 —— 边界数据需要审图号，
 * 本项目不内置、也不从第三方拉取。
 */
@Composable
fun JournalCheckinScreen(runtime: AppRuntime, onBack: () -> Unit) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val journal = runtime.journal
    val data by journal.data

    var showPicker by remember { mutableStateOf(false) }

    val totalKm = data.dailyKm.values.fold(0.0) { a, b -> a + b }
    val bestDay = data.dailyKm.maxByOrNull { it.value }
    val days = journal.recentKm(7)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            JournalTopBar(
                title = "用车打卡",
                onBack = onBack,
                trailing = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(C.accent)
                            .clickable { showPicker = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("打卡", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            AppCard {
                Row(Modifier.fillMaxWidth()) {
                    JournalStat(
                        value = "${data.cityCount}",
                        label = "点亮城市",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = "${data.provinceCount}",
                        label = "到过省份",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = formatKm(totalKm),
                        label = "累计里程",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = bestDay?.let { formatKm(it.value) } ?: "--",
                        label = "单日最多",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                FootprintMap(
                    checkins = data.checkins,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp),
                    compact = false,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (data.cityCount == 0) "还没有点亮城市。点右上角「打卡」，把去过的城市一个个插上旗。"
                    else "已点亮 ${data.cityCount} 座城市；旗子按经纬度落点，不涉及任何地图边界数据。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每日里程", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (days.all { it.km <= 0.0 }) "近 7 天没有数据" else "近 7 天",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                val max = days.maxOfOrNull { it.km } ?: 0.0
                JournalBars(
                    values = days.map { if (max > 0) (it.km / max).toFloat() else 0f },
                    labels = days.map { dayLabel(it.date) },
                    valueTexts = days.map { if (it.km > 0) formatKm(it.km) else "" },
                    barHeight = 56.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "里程来自车辆数据（近 7 日明细 + 总里程每日差分），不用手动填。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("点亮的城市", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${data.cityCount}", color = C.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (data.checkins.isEmpty()) {
                    JournalEmpty("去过哪儿就记在哪儿，攒起来就是一张自己的出行地图。")
                } else {
                    data.checkins.groupBy { it.province }.forEach { (province, list) ->
                        Spacer(Modifier.height(10.dp))
                        Text(province, color = C.textSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        list.forEach { checkin ->
                            CheckinRow(
                                checkin = checkin,
                                onDelete = {
                                    journal.removeCheckin(checkin.id)
                                    toast(context, "已取消 ${checkin.city} 的打卡")
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        if (showPicker) {
            CityPickerSheet(
                checkedIds = data.checkins.map { it.id }.toSet(),
                onDismiss = { showPicker = false },
                onPick = { city ->
                    val ok = journal.checkin(city)
                    showPicker = false
                    toast(context, if (ok) "点亮 ${city.name}" else "${city.name} 已经打过卡了")
                },
            )
        }
    }
}

@Composable
private fun CheckinRow(checkin: CityCheckin, onDelete: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(FlagRed.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val w = size.width
                val h = size.height
                drawLine(
                    FlagRed,
                    Offset(w * 0.28f, h * 0.08f),
                    Offset(w * 0.28f, h * 0.94f),
                    strokeWidth = w * 0.14f,
                    cap = StrokeCap.Round,
                )
                val flag = Path().apply {
                    moveTo(w * 0.36f, h * 0.12f)
                    lineTo(w * 0.96f, h * 0.30f)
                    lineTo(w * 0.36f, h * 0.50f)
                    close()
                }
                drawPath(flag, FlagRed)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(checkin.city, color = C.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(
            if (checkin.date > 0) JournalDates.shortLabel(checkin.date) else "已点亮",
            color = C.textSecondary,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "删",
            color = C.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/**
 * 足迹图：城市点阵 + 小红旗。
 *
 * [compact] = true 时不画旗子与标签（概览卡只有 104dp 高，画上去会糊成一团）。
 */
@Composable
fun FootprintMap(
    checkins: List<CityCheckin>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val C = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val lit = remember(checkins) { checkins.mapNotNull { CityCatalog.byId(it.id) } }
    val labelStyle = TextStyle(color = C.textPrimary, fontSize = 8.sp)

    Canvas(modifier) {
        // 等比投点：经度跨度 63°、纬度跨度 37°，再按中纬度 cos(35°)≈0.82 折算，
        // 地理宽高比约 1.4。画布比例跟它对不上时按小的一边缩、另一边居中 ——
        // 直接拉满画布会把中国横着摊成一张饼。
        val geoAspect = (CityProjection.MAX_LON - CityProjection.MIN_LON) * 0.82f /
            (CityProjection.MAX_LAT - CityProjection.MIN_LAT)
        val boxAspect = size.width / size.height.coerceAtLeast(1f)
        val drawW: Float
        val drawH: Float
        if (boxAspect > geoAspect) {
            drawH = size.height
            drawW = drawH * geoAspect
        } else {
            drawW = size.width
            drawH = drawW / geoAspect
        }
        val offX = (size.width - drawW) / 2f
        val offY = (size.height - drawH) / 2f
        fun place(city: City): Offset = Offset(
            offX + CityProjection.x(city.lon) * drawW,
            offY + CityProjection.y(city.lat) * drawH,
        )

        // 1) 底图：所有收录城市铺成暗点，勾出大致的国土形状（不画任何边界）
        val dotR = if (compact) 0.9.dp.toPx() else 1.35.dp.toPx()
        CityCatalog.ALL.forEach { city ->
            drawCircle(
                color = C.textSecondary.copy(alpha = 0.16f),
                radius = dotR,
                center = place(city),
            )
        }

        // 2) 已点亮的城市：光晕 + 实心点（+ 小红旗与城市名）
        val litIds = lit.map { it.id }.toSet()
        lit.forEach { city ->
            val p = place(city)
            drawCircle(
                color = C.accent.copy(alpha = 0.22f),
                radius = if (compact) 4.2.dp.toPx() else 6.4.dp.toPx(),
                center = p,
            )
            drawCircle(
                color = C.accent,
                radius = if (compact) 1.9.dp.toPx() else 2.7.dp.toPx(),
                center = p,
            )
            if (!compact) {
                // 小红旗：一根短杆 + 三角旗
                val poleH = 11.dp.toPx()
                val poleX = p.x + 1.6.dp.toPx()
                drawLine(
                    FlagRed,
                    Offset(poleX, p.y),
                    Offset(poleX, p.y - poleH),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val flag = Path().apply {
                    moveTo(poleX, p.y - poleH)
                    lineTo(poleX + 6.2.dp.toPx(), p.y - poleH + 2.4.dp.toPx())
                    lineTo(poleX, p.y - poleH + 4.8.dp.toPx())
                    close()
                }
                drawPath(flag, FlagRed)
                // 城市名：只给已点亮的城市标，避免 300 多个标签糊成一片
                val textLayout = measurer.measure(city.name, labelStyle)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        (poleX + 2.dp.toPx()).coerceAtMost(size.width - textLayout.size.width - 2.dp.toPx()),
                        (p.y - poleH - textLayout.size.height - 1.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }
        }

        // 3) 空状态：一个都没点亮时，在中央写一句提示（比干瞪着一堆暗点友好）
        if (litIds.isEmpty() && !compact) {
            val tip = measurer.measure(
                "点亮你去过的城市",
                TextStyle(color = C.textSecondary.copy(alpha = 0.65f), fontSize = 10.sp),
            )
            drawText(
                textLayoutResult = tip,
                topLeft = Offset(
                    (size.width - tip.size.width) / 2f,
                    size.height * 0.46f,
                ),
            )
        }
    }
}

/**
 * 城市选择弹层：搜索 + 按省份分组的城市列表。
 *
 * 用 LazyColumn 而不是普通 Column：收录了三百多个城市，一次性组合出来会卡一下。
 */
@Composable
private fun CityPickerSheet(
    checkedIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (City) -> Unit,
) {
    val C = LocalAppColors.current
    var keyword by remember { mutableStateOf("") }

    val groups: List<Pair<String, List<City>>> = remember(keyword) {
        if (keyword.isBlank()) {
            CityCatalog.BY_PROVINCE
        } else {
            CityCatalog.search(keyword).groupBy { it.province }.toList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIALOG_SCRIM))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .consumeClicks()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(C.dialogBg)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("打卡新城市", color = C.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            JournalInput(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = "搜城市或省份，比如「杭州」「云南」",
            )
            Spacer(Modifier.height(10.dp))
            if (groups.isEmpty()) {
                JournalEmpty("没有找到这个城市。列表收录 300+ 个地级市，省会与热门城市都在。")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    groups.forEach { (province, cities) ->
                        item(key = "p_$province") {
                            Text(
                                province,
                                color = C.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            )
                        }
                        items(cities, key = { it.id }) { city ->
                            val checked = checkedIds.contains(city.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !checked) { onPick(city) }
                                    .padding(horizontal = 8.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    city.name,
                                    color = if (checked) C.textSecondary else C.textPrimary,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                if (checked) {
                                    Text("已点亮", color = C.accent, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 每天柱状图的横轴标签：今天 / 昨天 / 10/26。 */
private fun dayLabel(date: LocalDate): String {
    val today = JournalDates.today()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}
