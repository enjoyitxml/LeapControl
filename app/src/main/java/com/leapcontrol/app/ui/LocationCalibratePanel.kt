package com.leapcontrol.app.ui

import androidx.compose.foundation.background
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
                    if (calibration.convert) append(" · 已按 WGS-84 → GCJ-02 换算")
                },
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

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
                        .clickable { onChange(LocationCalibration(convert = calibration.convert)) },
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

        // ---- 坐标系换算（老版本唯一的能力，保留成一行开关） ----
        ConvertRow(
            checked = calibration.convert,
            onToggle = { onChange(calibration.copy(convert = !calibration.convert)) },
        )
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
 * 坐标系换算开关。
 *
 * 文案不写「WGS-84 / GCJ-02」两个术语就完事 —— 用户不需要懂这两个词，
 * 需要的是「什么症状下该打开它」。所以副标题写的是症状：整张图整体偏几百米、方向固定。
 */
@Composable
private fun ConvertRow(checked: Boolean, onToggle: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.chipBg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "按 WGS-84 → GCJ-02 换算",
                color = C.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "整张图整体偏几百米、而且每次都偏同一个方向时，打开它试试",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) C.accent.copy(alpha = 0.20f) else C.panelBg.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = C.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
