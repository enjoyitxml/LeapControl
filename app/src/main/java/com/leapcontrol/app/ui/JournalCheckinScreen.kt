package com.leapcontrol.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.leapcontrol.app.journal.CityWish
import com.leapcontrol.app.journal.JournalDates
import com.leapcontrol.app.journal.KmRange
import com.leapcontrol.app.journal.MileageSeries

/**
 * 旗帜色板（不跟主题走：旗子是一张图上的标识，颜色越稳越好认）。
 *
 * 按**省份**取色，同一个省的城市是同一个颜色 —— 地图上扫一眼就能看出
 * 「这一趟跑了哪几个省」，比随机上色有信息量。
 */
private val FlagPaletteLight = listOf(
    Color(0xFFE2503C), // 红
    Color(0xFFE08A2E), // 橙
    Color(0xFFC9A227), // 琥珀
    Color(0xFF6F9E3C), // 草绿
    Color(0xFF2FA37A), // 青绿
    Color(0xFF2C8DC9), // 蓝
    Color(0xFF5566C9), // 靛
    Color(0xFF8E5BC9), // 紫
    Color(0xFFD0568F), // 玫红
    Color(0xFF8C6A4A), // 棕
)

private val FlagPaletteDark = listOf(
    Color(0xFFF2715C),
    Color(0xFFF0A455),
    Color(0xFFE7BE4C),
    Color(0xFF9CC75B),
    Color(0xFF54C29A),
    Color(0xFF5BA9DD),
    Color(0xFF7B8AE0),
    Color(0xFFAE7CDE),
    Color(0xFFE77BAE),
    Color(0xFFB48A63),
)

/** 同一省份固定同一个旗色（深浅主题各一套亮度）。 */
fun flagColorOf(province: String, dark: Boolean): Color {
    val palette = if (dark) FlagPaletteDark else FlagPaletteLight
    val index = Math.floorMod(province.hashCode(), palette.size)
    return palette[index]
}

/** 心愿城市的空心圈颜色：比旗子收敛，别抢了已点亮的注意力。 */
private val WishStroke = Color(0xFF8A8FA0)

/**
 * 用车打卡页。
 *
 * 「去过哪些城市」这件事，车端不提供、也不适合为了它接一个地图 SDK（体积 + 凭据），
 * 所以这里用一个**点阵足迹图**：所有收录的城市按经纬度铺开（暗点），
 * 去过的城市点亮并插一面小红旗；想去还没去的画成空心圈，收在心愿单里。
 *
 * 地图可双指放大、单指拖动（放大后右上角出现「复位」）。
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
    // 待确认的删除目标：打卡与心愿各一个。点垃圾桶先弹确认，确认后才真的删 ——
    // 打卡记录没有「撤销」，误删一个城市就得重新打卡（日期还会变成今天）。
    var pendingCheckin by remember { mutableStateOf<CityCheckin?>(null) }
    var pendingWish by remember { mutableStateOf<CityWish?>(null) }

    val totalKm = data.dailyKm.values.fold(0.0) { a, b -> a + b }
    val bestDay = data.dailyKm.maxByOrNull { it.value }

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
                        Text(
                            "打卡",
                            color = readableOn(C.accent),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            JournalCard {
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
                    wishes = data.wishes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp),
                    compact = false,
                    interactive = true,
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

            // ---- 心愿单 ----
            JournalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("心愿单", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("想去的城市", color = C.textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${data.wishCount}", color = C.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (data.wishes.isEmpty()) {
                    JournalEmpty("把想去的城市加进来：点右上角「打卡」，在列表里给城市点「想去」。去过之后打卡，心愿自动划掉。")
                } else {
                    data.wishes.forEach { wish ->
                        WishRow(
                            wish = wish,
                            onFulfill = {
                                val ok = journal.fulfillWish(wish.id)
                                toast(
                                    context,
                                    if (ok) "点亮 ${wish.city}，心愿达成" else "${wish.city} 已经打过卡了",
                                )
                            },
                            onRemove = { pendingWish = wish },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            JournalCard {
                // ---- 里程趋势：日 / 周 / 月 / 年 ----
                // 数据来自本地全量（dailyKm 一天天累积），官方接口那 7 天明细只用来校准最近一周，
                // 更早的历史不会被裁掉，所以这里的「年」能看到提车以来的全部记录。
                var kmRange by remember { mutableStateOf(KmRange.DAY) }
                // 不用 remember 缓存：跨天之后 today 变了，窗口要整体前移一格；
                // 缓存住会停在前一天。窗口只有 14 格，重算的开销可以忽略。
                val points = MileageSeries.of(data.dailyKm, kmRange)
                val kmMax = points.maxOfOrNull { it.km } ?: 0.0
                val hasKm = kmMax > 0.0
                val peak = MileageSeries.peak(points)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("里程趋势", color = C.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (hasKm) "合计 ${formatKm(MileageSeries.total(points))} km" else "暂无数据",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                JournalSegmented(
                    options = KmRange.entries.map { it.label },
                    selectedIndex = kmRange.ordinal,
                    onSelect = { kmRange = KmRange.entries[it] },
                )
                Spacer(Modifier.height(12.dp))
                JournalLineChart(
                    values = points.map { if (hasKm) (it.km / kmMax).toFloat() else 0f },
                    labels = points.map { it.label },
                    height = 124.dp,
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    JournalStat(
                        value = formatKm(MileageSeries.average(points)),
                        label = "平均每${kmRange.label}",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = peak?.let { formatKm(it.km) } ?: "—",
                        label = "最高",
                        modifier = Modifier.weight(1f),
                    )
                    JournalStat(
                        value = peak?.label ?: "—",
                        label = "出现在",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "里程存在手机本地，每天累计，不受官方 7 天限制；没有记录的日子按 0 计。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            JournalCard {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(flagColorOf(province, C.isDark)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(province, color = C.textSecondary, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        list.forEach { checkin ->
                            CheckinRow(
                                checkin = checkin,
                                onDelete = { pendingCheckin = checkin },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        // ---- 删除确认：打卡与心愿都要过一道，避免误触直接丢数据 ----
        val wishToDelete = pendingWish
        if (wishToDelete != null) {
            JournalConfirm(
                title = "移出心愿单？",
                desc = "「${wishToDelete.city}」会从心愿单移除，之后还可以重新加入。",
                confirmText = "移出",
                onConfirm = {
                    journal.removeWish(wishToDelete.id)
                    pendingWish = null
                    toast(context, "已从心愿单移除 ${wishToDelete.city}")
                },
                onDismiss = { pendingWish = null },
            )
        }

        val checkinToDelete = pendingCheckin
        if (checkinToDelete != null) {
            JournalConfirm(
                title = "取消这次打卡？",
                desc = "「${checkinToDelete.city}」的足迹会被删掉，点亮城市数与省份数立刻减少，" +
                    "删除后不能恢复（重新打卡日期会变成当天）。",
                confirmText = "确认删除",
                onConfirm = {
                    journal.removeCheckin(checkinToDelete.id)
                    pendingCheckin = null
                    toast(context, "已取消 ${checkinToDelete.city} 的打卡")
                },
                onDismiss = { pendingCheckin = null },
            )
        }

        if (showPicker) {
            CityPickerSheet(
                checkedIds = data.checkins.map { it.id }.toSet(),
                wishedIds = data.wishes.map { it.id }.toSet(),
                onDismiss = { showPicker = false },
                onWish = { city ->
                    val ok = journal.addWish(city)
                    toast(
                        context,
                        when {
                            ok -> "已加入心愿单：${city.name}"
                            data.checkins.any { it.id == city.id } -> "${city.name} 已经去过啦"
                            else -> "${city.name} 已在心愿单里"
                        },
                    )
                },
                onPick = { city ->
                    val ok = journal.checkin(city)
                    showPicker = false
                    toast(context, if (ok) "点亮 ${city.name}" else "${city.name} 已经打过卡了")
                },
            )
        }
    }
}

/** 心愿单里的一行：想去哪 + 「去过了」+ 移除。 */
@Composable
private fun WishRow(wish: CityWish, onFulfill: () -> Unit, onRemove: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 空心圈 = 还没点亮
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(1.4.dp, WishStroke.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(flagColorOf(wish.province, C.isDark).copy(alpha = 0.75f)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(wish.city, color = C.textPrimary, fontSize = 13.sp)
            Text(wish.province, color = C.textSecondary, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(C.accent.copy(alpha = 0.12f))
                .border(1.dp, C.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable(onClick = onFulfill)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("去过了", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(8.dp))
        DeleteCircleButton(contentDescription = "移出心愿单", onClick = onRemove)
    }
}

@Composable
private fun CheckinRow(checkin: CityCheckin, onDelete: () -> Unit) {
    val C = LocalAppColors.current
    val flag = flagColorOf(checkin.province, C.isDark)
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
                .background(flag.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val w = size.width
                val h = size.height
                drawLine(
                    flag,
                    Offset(w * 0.28f, h * 0.08f),
                    Offset(w * 0.28f, h * 0.94f),
                    strokeWidth = w * 0.14f,
                    cap = StrokeCap.Round,
                )
                val flagPath = Path().apply {
                    moveTo(w * 0.36f, h * 0.12f)
                    lineTo(w * 0.96f, h * 0.30f)
                    lineTo(w * 0.36f, h * 0.50f)
                    close()
                }
                drawPath(flagPath, flag)
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
        DeleteCircleButton(contentDescription = "取消这次打卡", onClick = onDelete)
    }
}

/**
 * 圆形删除键：垃圾桶图标 + 浅底。
 *
 * 原来这里是一个光秃秃的「删」字，既不像按钮、点击区也只有十来个 dp。
 * 换成带底的圆钮后，一眼能看出「这是个按钮」，手指也容易点中。
 */
@Composable
private fun DeleteCircleButton(contentDescription: String, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(C.chipBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = contentDescription,
            tint = C.textSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * 足迹图：城市点阵 + 小红旗（每省一个颜色）+ 心愿空心圈。
 *
 * [compact] = true 时不画旗子与标签（概览卡只有 118dp 高，画上去会糊成一团）。
 * [interactive] = true 时可双指放大 / 单指拖动，放大后右上角出现复位键。
 */
@Composable
fun FootprintMap(
    checkins: List<CityCheckin>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    wishes: List<CityWish> = emptyList(),
    interactive: Boolean = false,
) {
    val C = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val lit = remember(checkins) { checkins.mapNotNull { CityCatalog.byId(it.id) } }
    val wished = remember(wishes) { wishes.mapNotNull { CityCatalog.byId(it.id) } }
    val labelStyle = TextStyle(color = C.textPrimary, fontSize = 8.sp)

    // 地图视图：scale = 1 时正好铺满，offset 只在放大后才有意义
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val gestureModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val next = (scale * zoom).coerceIn(1f, 5.5f)
                val k = next / scale
                val cx = size.width / 2f
                val cy = size.height / 2f
                // 缩放锚点跟着手指走：手指下的那座城市不能跑
                val nx = (centroid.x - cx) * (1f - k) + offset.x * k + pan.x
                val ny = (centroid.y - cy) * (1f - k) + offset.y * k + pan.y
                val limX = (next - 1f) * size.width * 0.5f
                val limY = (next - 1f) * size.height * 0.5f
                offset = Offset(nx.coerceIn(-limX, limX), ny.coerceIn(-limY, limY))
                scale = next
            }
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize().then(gestureModifier)) {
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
            val cx = size.width / 2f
            val cy = size.height / 2f

            fun place(city: City): Offset {
                val bx = offX + CityProjection.x(city.lon) * drawW
                val by = offY + CityProjection.y(city.lat) * drawH
                return Offset(cx + (bx - cx) * scale + offset.x, cy + (by - cy) * scale + offset.y)
            }

            // 1) 底图：所有收录城市铺成暗点，勾出大致的国土形状（不画任何边界）
            val dotR = (if (compact) 0.9.dp else 1.35.dp).toPx() * (1f + (scale - 1f) * 0.12f)
            CityCatalog.ALL.forEach { city ->
                val p = place(city)
                // 放大后大量点落在画布外，跳过能省掉一半绘制
                if (p.x < -8f || p.y < -8f || p.x > size.width + 8f || p.y > size.height + 8f) return@forEach
                drawCircle(
                    color = C.textSecondary.copy(alpha = 0.16f),
                    radius = dotR,
                    center = p,
                )
            }

            // 2) 心愿城市：空心圈，不插旗（还没去过）
            wished.forEach { city ->
                val p = place(city)
                drawCircle(
                    color = WishStroke.copy(alpha = 0.55f),
                    radius = if (compact) 2.4.dp.toPx() else 3.2.dp.toPx(),
                    center = p,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // 3) 已点亮的城市：光晕 + 实心点（+ 小红旗与城市名）
            val litIds = lit.map { it.id }.toSet()
            lit.forEach { city ->
                val p = place(city)
                val flag = flagColorOf(city.province, C.isDark)
                drawCircle(
                    color = flag.copy(alpha = 0.22f),
                    radius = if (compact) 4.2.dp.toPx() else 6.4.dp.toPx(),
                    center = p,
                )
                drawCircle(
                    color = flag,
                    radius = if (compact) 1.9.dp.toPx() else 2.7.dp.toPx(),
                    center = p,
                )
                if (!compact) {
                    // 小红旗：一根短杆 + 三角旗
                    val poleH = 11.dp.toPx()
                    val poleX = p.x + 1.6.dp.toPx()
                    drawLine(
                        flag,
                        Offset(poleX, p.y),
                        Offset(poleX, p.y - poleH),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    val flagPath = Path().apply {
                        moveTo(poleX, p.y - poleH)
                        lineTo(poleX + 6.2.dp.toPx(), p.y - poleH + 2.4.dp.toPx())
                        lineTo(poleX, p.y - poleH + 4.8.dp.toPx())
                        close()
                    }
                    drawPath(flagPath, flag)
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

            // 4) 空状态：一个都没点亮时，在中央写一句提示（比干瞪着一堆暗点友好）
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

        if (interactive && scale > 1.02f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.panelBg)
                    .border(1.dp, C.border, RoundedCornerShape(10.dp))
                    .clickable {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("复位 ${(scale * 10).toInt() / 10f}x", color = C.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

/**
 * 城市选择弹层：搜索 + 按省份分组的城市列表。
 *
 * 每一行有两个动作：点名字 = 打卡，点右侧「想去」= 加心愿单。
 * 用 LazyColumn 而不是普通 Column：收录了三百多个城市，一次性组合出来会卡一下。
 */
@Composable
private fun CityPickerSheet(
    checkedIds: Set<String>,
    wishedIds: Set<String>,
    onDismiss: () -> Unit,
    onWish: (City) -> Unit,
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
            Spacer(Modifier.height(4.dp))
            Text(
                "点城市名 = 打卡；点「想去」= 加进心愿单",
                color = C.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
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
                            val wished = wishedIds.contains(city.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !checked) { onPick(city) }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    city.name,
                                    color = if (checked) C.textSecondary else C.textPrimary,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                when {
                                    checked -> Text("已点亮", color = C.accent, fontSize = 10.sp)
                                    wished -> Text("已在心愿单", color = WishStroke, fontSize = 10.sp)
                                    else -> Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(C.accent.copy(alpha = 0.10f))
                                            .border(
                                                1.dp,
                                                C.accent.copy(alpha = 0.30f),
                                                RoundedCornerShape(10.dp),
                                            )
                                            .clickable { onWish(city) }
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    ) {
                                        Text("想去", color = C.accent, fontSize = 10.sp)
                                    }
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
