package com.leapbuddy.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapbuddy.app.MapIntents
import kotlinx.coroutines.launch

/**
 * 「零记」页：围绕用车的轻服务入口（暂复用出行服务内容，后续可扩展行车记录/足迹）。
 * 目前提供 附近充电站 / 查看车辆位置 / 导航回车 三项（调起高德地图）。
 */
@Composable
fun DiscoverScreen(runtime: AppRuntime) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var locating by remember { mutableStateOf(false) }
    var located by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // 进入页面拉一次车况，拿到车辆经纬度（GCJ-02，可直接给高德）
    LaunchedEffect(Unit) {
        locating = true
        located = runCatching { runtime.refreshStatus() }.getOrNull()
            ?.let { st -> st.latitude?.let { lat -> st.longitude?.let { lng -> lat to lng } } }
        locating = false
    }

    fun requireLocation(block: (Double, Double) -> Unit) {
        val pos = located
        if (pos == null) {
            Toast.makeText(context, "暂时拿不到车辆位置，请稍后重试", Toast.LENGTH_SHORT).show()
        } else {
            block(pos.first, pos.second)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("零记", color = C.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (locating) "正在获取车辆位置…" else "围绕你的用车场景的常用服务",
            color = C.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(18.dp))

        DiscoverCard(
            icon = Icons.Filled.Bolt,
            title = "附近充电站",
            desc = "在高德地图中查看车辆周边的充电站",
            onClick = { requireLocation { lat, lng -> MapIntents.aroundSearch(context, lat, lng, "充电站") } },
        )
        Spacer(Modifier.height(12.dp))
        DiscoverCard(
            icon = Icons.Filled.LocationOn,
            title = "车辆位置",
            desc = "在地图中查看爱车当前停放的位置",
            onClick = { requireLocation { lat, lng -> MapIntents.showPoint(context, lat, lng, runtime.displayName()) } },
        )
        Spacer(Modifier.height(12.dp))
        DiscoverCard(
            icon = Icons.Filled.Navigation,
            title = "导航去取车",
            desc = "发起从当前位置到爱车的导航",
            onClick = { requireLocation { lat, lng -> MapIntents.navigate(context, lat, lng, runtime.displayName()) } },
        )
        Spacer(Modifier.height(12.dp))
        DiscoverCard(
            icon = Icons.Filled.SupportAgent,
            title = "官方服务",
            desc = "打开零跑汽车官网，查询服务网点与公告",
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.leapmotor.com"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiscoverCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(C.chipBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = C.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.5.sp)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = C.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
