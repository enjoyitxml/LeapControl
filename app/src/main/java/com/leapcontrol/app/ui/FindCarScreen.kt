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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 车辆位置 / 寻车页（开源接口 23：GPS 经纬度 + 高德逆地理编码 + 鸣笛闪灯）。
 * - 经纬度来自车况信号 2/3（GCJ-02，可直接逆地理编码）；车端不上报时显示「位置未知」。
 * - 具体地址需用户在「我的」页填写高德 Web Key 后才可用。
 * - 鸣笛闪灯走 [onHorn]（由首页统一包二次确认）。
 */
@Composable
fun FindCarScreen(
    runtime: AppRuntime,
    status: CarStatus?,
    onHorn: () -> Unit,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val hasKey = runtime.hasAmapKey()
    val lat = status?.latitude
    val lng = status?.longitude
    val hasFix = lat != null && lng != null

    var address by remember { mutableStateOf<String?>(null) }
    var addrLoading by remember { mutableStateOf(false) }

    LaunchedEffect(lat, lng, hasKey) {
        address = null
        if (hasFix && hasKey) {
            addrLoading = true
            address = runCatching { runtime.reverseGeocodeLocation(lat!!, lng!!) }.getOrNull()
            addrLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        // 顶栏：左返回 + 居中标题
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
            Text(
                text = "车辆位置",
                color = C.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            Surface(
                shape = CircleShape,
                color = C.iconBg,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(34.dp)
                    .clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "返回",
                        tint = C.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 位置信息卡（无地图 SDK，以地址 + 坐标呈现；车端不上报时提示）
        AppCard {
            if (!hasFix) {
                Text("车辆未上报位置", color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "当前车辆暂未回传 GPS 坐标，开启 App 并等待车辆联网上报后即可查看。",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            } else {
                val shown = if (addrLoading) "地址解析中…" else (address ?: "暂未获取到地址")
                Text(shown, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "经度 %.6f，纬度 %.6f".format(lng ?: 0.0, lat ?: 0.0),
                    color = C.textSecondary,
                    fontSize = 12.sp,
                )
                if (!hasKey) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "在「我的」页填写高德 Web Key 后可显示具体地址。",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 鸣笛闪灯寻车
        Button(
            onClick = onHorn,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
            enabled = hasFix,
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("鸣笛闪灯寻车", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (!hasFix) {
            Spacer(Modifier.height(8.dp))
            Text("车辆未上报位置，无法寻车", color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))
    }
}
