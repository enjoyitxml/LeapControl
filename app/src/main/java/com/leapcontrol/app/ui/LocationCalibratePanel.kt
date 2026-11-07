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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.CoordinateDatum
import com.leapcontrol.app.LocationCalibration
import com.leapcontrol.app.LocationCalibrator

/**
 * 位置校准面板（找车页下半屏）。
 *
 * ## 为什么不在弹层里做
 * 老版本是个居中弹窗，里面只有「开 / 不开换算」两个选项 —— 用户看不到自己那一按
 * 到底把钉子挪到哪了，只能关掉弹窗回去看地图，再进来改。**看不到结果，就没法调准**。
 *
 * 这一版把面板放在地图下面，地图一直露着：拖车标、点方向键，钉子当场跟着动，
 * 「对没对上」一眼就能判断。面板不盖住地图是它唯一的设计前提。
 *
 * ## 两条调整路径，对应两类误差
 *  - **拖车标**：改完立刻就知道差多少的人用它 —— 手指把钉子拖到车真正停的地方，
 *    位移自动折算成东 / 北偏移。这是最直观的一条。
 *  - **方向键 + 步长**：差几十米、想精确收尾时用。10 米一档比拖拽准得多
 *    （手指在缩放 17 的地图上滑 1 像素就是好几米）。
 *
 * ## 坐标系换算为什么还留着
 * 那是老版本唯一的能力，也是最常见的一类偏差：整张图整体偏几百米、方向固定。
 * 换算一次就到位，比让人按 200 米走 3 下靠谱。它和手动偏移**叠加**（见
 * [LocationCalibrator.apply] 的执行顺序），所以「先换算、再微调」是成立的。
 */
@Composable
internal fun LocationCalibratePanel(
    calibration: LocationCalibration,
    onChange: (LocationCalibration) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** 自动判定的结论（可空）：让用户知道「已经替你纠过一次」，否则准了他也以为是运气。 */
    autoNote: String? = null,
) {
    val C = LocalAppColors.current
    // 步长是面板内的临时状态，不落盘：下次进来重新从 50 米起手更符合直觉
    var stepIndex by remember { mutableStateOf(1) }
    val step = LocationCalibrator.STEPS[stepIndex]

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        // ---- 标题行：重置 / 完成都是文字，省下高度给方向键 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "位置校准",
                color = C.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!calibration.isIdentity) {
                Text(
                    text = "重置",
                    color = C.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onChange(LocationCalibration.NONE) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text = "完成",
                color = C.accent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(C.accent.copy(alpha = 0.14f))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "把地图上的车标拖到车真正停的地方；差得不多就点下面的方向键微调。" +
                "调完地图、地址和导航都会按新位置走。",
            color = C.textSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
        )

        Spacer(Modifier.height(12.dp))

        // ---- 当前偏移 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(C.chipBg)
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Text(
                text = LocationCalibrator.describe(calibration.east, calibration.north),
                color = if (calibration.hasOffset) C.accent else C.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    if (calibration.hasOffset) {
                        append("合位移 ${calibration.distanceMeters} 米")
                    } else {
                        append("还没挪过，车标就是车机上报的位置")
                    }
                    append(" · ${LocationCalibrator.datumSummary(calibration)}")
                },
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- 坐标系：整张图偏几百米时唯一有效的那一项 ----
        // 以前它是页面最底部一行小勾选，滚下去才看得到，勾没勾也只是一枚 22dp 的小方块 ——
        // 而「定位偏 500 米」恰恰是最常见、最需要被找到的问题。现在放在偏移卡正下方，
        // 四选一的实心按钮，选中的那块整个填主题色。
        DatumCard(
            current = calibration.datum,
            note = autoNote,
            onSelect = { onChange(calibration.copy(datum = it)) },
        )

        Spacer(Modifier.height(12.dp))

        // ---- 步长 ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocationCalibrator.STEPS.forEachIndexed { index, meters ->
                StepChip(
                    label = "$meters 米",
                    selected = index == stepIndex,
                    modifier = Modifier.weight(1f),
                ) { stepIndex = index }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- 方向键：上北下南，和地图朝向一致 ----
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DirKey(Icons.Filled.KeyboardArrowUp, "向北 $step 米") {
                onChange(calibration.copy(north = calibration.north + step))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DirKey(Icons.Filled.KeyboardArrowLeft, "向西 $step 米") {
                    onChange(calibration.copy(east = calibration.east - step))
                }
                // 中间那颗不是第五个方向，是「把这一次的偏移清掉」：
                // 调歪了以后最想做的就是回到原点重来，把它放在手指自然落下的位置
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(C.panelBg.copy(alpha = 0.9f))
                        .clickable { onChange(calibration.copy(east = 0, north = 0)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "归零",
                        color = if (calibration.hasOffset) C.accent else C.textSecondary.copy(alpha = 0.6f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                DirKey(Icons.Filled.KeyboardArrowRight, "向东 $step 米") {
                    onChange(calibration.copy(east = calibration.east + step))
                }
            }
            DirKey(Icons.Filled.KeyboardArrowDown, "向南 $step 米") {
                onChange(calibration.copy(north = calibration.north - step))
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** 步长档位。选中用淡主题色底，未选中用 chip 底。 */
@Composable
private fun StepChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) C.accent.copy(alpha = 0.16f) else C.chipBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 一个方向键（44dp 圆钮，间距靠调用方给）。 */
@Composable
private fun DirKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(C.chipBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = C.textPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 坐标系选择卡片：**四选一的实心按钮**，不是一行小勾选。
 *
 * 老版本这里是个 22dp 的方框 + 一行 11sp 灰字，压在页面最底部 —— 用户滚动都不一定看到，
 * 看到也认不出那是个能点的东西。而「整张图偏几百米」是定位问题里最常见的一类，
 * 换算一次就能全好，所以它必须一眼可辨、一点就变。
 *
 * 四档而不是开 / 关：车端到底报的是哪一套坐标，不同车型给出三种不同答案
 * （C11 原样、B10/C01 要 WGS-84、还有一批是百度），只给一个开关必然剩下一批人怎么点都不对。
 * 「自动」放在第一位：两套坐标能互证时它会自己挑对的那套，用户什么都不用懂。
 */
@Composable
private fun DatumCard(current: CoordinateDatum, note: String?, onSelect: (CoordinateDatum) -> Unit) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.panelBg)
            .border(1.dp, C.accent.copy(alpha = 0.32f), shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Public,
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "定位整体偏几百米？改这里",
                color = C.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "选对后地图立刻纠正",
                color = C.textSecondary,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(9.dp))

        // 2×2：左列是「常用」（自动 / WGS-84），右列是「特殊」（不换算 / 百度）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatumChip(CoordinateDatum.AUTO, current, Modifier.weight(1f), onSelect)
            DatumChip(CoordinateDatum.RAW, current, Modifier.weight(1f), onSelect)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatumChip(CoordinateDatum.WGS84, current, Modifier.weight(1f), onSelect)
            DatumChip(CoordinateDatum.BD09, current, Modifier.weight(1f), onSelect)
        }

        // 自动判定 / 无法确定的结论：默认不说，说了就说明这一帧确实有值得知道的东西
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                color = C.accent,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

/** 坐标系选项：选中时整块填主题色、字反色，并给一枚对勾。 */
@Composable
private fun DatumChip(
    datum: CoordinateDatum,
    current: CoordinateDatum,
    modifier: Modifier = Modifier,
    onSelect: (CoordinateDatum) -> Unit,
) {
    val C = LocalAppColors.current
    val selected = datum == current
    // 选中态用 accent 实底，字取 panelBg（浅色主题=近白、深色主题=深灰）——
    // 比写死白色更稳：深色主题的主题色通常是提亮过的浅绿，白字会糊成一片
    val ink = if (selected) C.panelBg else C.textPrimary
    val subInk = if (selected) C.panelBg.copy(alpha = 0.82f) else C.textSecondary
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) C.accent else C.chipBg)
            .clickable { onSelect(datum) }
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Column {
            Text(
                text = datum.label,
                color = ink,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = datumHint(datum),
                color = subInk,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                maxLines = 2,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp),
            )
        }
    }
}

/** 每个选项一句「什么情况下选它」——比术语管用。 */
private fun datumHint(datum: CoordinateDatum): String = when (datum) {
    CoordinateDatum.AUTO -> "两套坐标自动比对"
    CoordinateDatum.RAW -> "本来就是高德坐标"
    CoordinateDatum.WGS84 -> "偏的方向每次都一样"
    CoordinateDatum.BD09 -> "上面那条试过还偏"
}
