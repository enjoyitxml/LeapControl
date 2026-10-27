package com.leapcontrol.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AmapPoi
import com.leapcontrol.app.MapIntents
import kotlinx.coroutines.launch

/**
 * 「零记」页：围绕用车的轻服务入口。
 * 目前提供 发送到车机（目的地搜索，调起高德）/ 官方服务 两项。
 *
 * 注：车辆定位相关的「车辆位置 / 附近充电站 / 导航去取车」已下线——
 * 车端鲜少上报经纬度，入口留着只会一直提示「拿不到位置」。
 */
@Composable
fun DiscoverScreen(runtime: AppRuntime) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
            text = "围绕你的用车场景的常用服务",
            color = C.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(18.dp))

        DiscoverCard(
            icon = Icons.Filled.Navigation,
            title = "发送到车机",
            desc = "搜索目的地并发送到车机导航",
            onClick = { showPicker = true },
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

    if (showPicker) {
        DestinationPickerDialog(
            runtime = runtime,
            onDismiss = { showPicker = false },
            onResult = { msg -> showPicker = false; toast(msg) },
        )
    }
}

/** 目的地搜索弹窗：搜到 POI 后可发送到车机或直接导航。 */
@Composable
private fun DestinationPickerDialog(
    runtime: AppRuntime,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AmapPoi>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AmapPoi?>(null) }

    fun search(text: String) {
        if (text.isBlank()) {
            results = emptyList()
            searched = false
            return
        }
        scope.launch {
            searching = true
            searched = true
            results = runCatching { runtime.searchDestination(text) }.getOrDefault(emptyList())
            searching = false
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.dialogBg,
        title = {
            Text("搜索目的地", color = LocalAppColors.current.textPrimary, fontSize = 16.sp)
        },
        text = {
            Column {
                if (!runtime.hasAmapKey()) {
                    Text(
                        "未配置高德 Web Key，无法搜索。请在「我的 → 高德 Web Key」中填写。",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it; selected = null },
                    placeholder = { Text("输入地点名称", color = LocalAppColors.current.textSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search(keyword) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        cursorColor = LocalAppColors.current.accent,
                        focusedBorderColor = LocalAppColors.current.accent,
                        unfocusedBorderColor = LocalAppColors.current.barTrack,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.height(210.dp)) {
                    when {
                        searching -> Text("搜索中…", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                        results.isEmpty() && searched -> Text(
                            "没有找到相关地点",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        else -> androidx.compose.foundation.lazy.LazyColumn {
                            items(results) { poi ->
                                val picked = selected?.id == poi.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (picked) LocalAppColors.current.chipBgActive else Color.Transparent,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable { selected = poi }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(poi.name, color = LocalAppColors.current.textPrimary, fontSize = 13.sp, maxLines = 1)
                                        if (poi.address.isNotBlank()) {
                                            Text(
                                                poi.address,
                                                color = LocalAppColors.current.textSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (picked) {
                                        Icon(
                                            Icons.Filled.Check,
                                            null,
                                            tint = LocalAppColors.current.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    val poi = selected ?: return@TextButton
                    val usedAmap = MapIntents.sendToCar(context, poi.latitude, poi.longitude, poi.name)
                    onResult(
                        if (usedAmap) "已在高德中规划「${poi.name}」，手机与车机高德同账号即可同步"
                        else "未安装高德地图，已用网页版打开「${poi.name}」"
                    )
                    onDismiss()
                },
            ) {
                Text(
                    "发送到车机",
                    color = if (selected != null) LocalAppColors.current.accent else LocalAppColors.current.textSecondary,
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = selected != null,
                    onClick = {
                        val poi = selected ?: return@TextButton
                        MapIntents.navigate(context, poi.latitude, poi.longitude, poi.name)
                        onDismiss()
                    },
                ) {
                    Text(
                        "导航",
                        color = if (selected != null) LocalAppColors.current.textPrimary else LocalAppColors.current.textSecondary,
                    )
                }
                TextButton(onClick = onDismiss) { Text("取消", color = LocalAppColors.current.textSecondary) }
            }
        },
    )
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
