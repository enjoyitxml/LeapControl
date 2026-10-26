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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.BuildConfig
import com.leapcontrol.app.tiles.QuickTiles
import kotlinx.coroutines.launch


/** 「我的」页面：车辆信息、控车密码、车模缓存、退出登录。 */
@Composable
fun ProfileScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showRename by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showArtwork by remember { mutableStateOf(false) }
    var showAmapKey by remember { mutableStateOf(false) }
    var artworkMode by remember { mutableStateOf(runtime.carArtworkMode()) }
    var message by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = if (LocalAppColors.current.isDark) androidx.compose.material3.darkColorScheme(primary = LocalAppColors.current.accent) else androidx.compose.material3.lightColorScheme(primary = LocalAppColors.current.accent)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("我的", color = LocalAppColors.current.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))

            // 车辆信息
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(LocalAppColors.current.chipBg, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(runtime.displayName(), color = LocalAppColors.current.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        val model = runtime.vehicleModel()
                        Text(
                            text = listOfNotNull(
                                model.takeIf { it.isNotBlank() },
                                runtime.session.selectedVin.takeLast(6).takeIf { it.isNotBlank() }?.let { "VIN $it" },
                            ).joinToString(" · ").ifEmpty { "暂无车辆信息" },
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            ProfileItem(Icons.Filled.Settings, "设置", "外观模式、四季背景、车模显示方式") { onOpenSettings() }
            Spacer(Modifier.height(12.dp))
            ProfileItem(Icons.Filled.Edit, "自定义车名", "修改首页显示的车辆昵称") { showRename = true }
            Spacer(Modifier.height(12.dp))
            ProfileItem(
                Icons.Filled.Lock,
                "控车密码",
                if (runtime.hasPin) "已保存 4 位操控密码，点击修改" else "未设置，点击设置",
            ) { showPin = true }
            Spacer(Modifier.height(12.dp))
            ProfileItem(Icons.Filled.Refresh, "刷新车模图片", "清除缓存并从官方接口重新拉取") {
                scope.launch {
                    runtime.clearCarImageCache()
                    val ok = runCatching { runtime.fetchCarImage(force = true) }.getOrNull() != null
                    message = if (ok) "车模已刷新" else "未取到官方车图，首页将显示内置剪影"
                }
            }
            Spacer(Modifier.height(12.dp))
            ProfileItem(
                Icons.Filled.Image,
                "车模显示方式",
                if (artworkMode == "builtin") "内置剪影（按车型轮廓）" else "自动（优先官方车图）",
            ) { showArtwork = true }
            Spacer(Modifier.height(12.dp))
            ProfileItem(
                Icons.Filled.LocationOn,
                "高德 Web Key",
                if (runtime.hasAmapKey()) "已配置，可搜索目的地" else "未配置，无法搜索目的地",
            ) { showAmapKey = true }
            Spacer(Modifier.height(12.dp))

            // ---- 系统控制中心快捷磁贴 ----
            val context = androidx.compose.ui.platform.LocalContext.current
            var tileTick by remember { mutableStateOf(0) }
            val addedIds = remember(tileTick) {
                QuickTiles.ALL.filter { QuickTiles.isAdded(context, it) }.map { it.id }.toSet()
            }
            AppCard(title = "系统控制中心") {
                    Text(
                        "点击图标即可添加到下拉控制中心，锁屏状态下也能直接控车",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    val rows = QuickTiles.ALL.chunked(4)
                    rows.forEachIndexed { index, rowSpecs ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowSpecs.forEach { spec ->
                                val added = spec.id in addedIds
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            QuickTiles.requestAdd(context, spec) { tileTick++ }
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    if (added) LocalAppColors.current.iconBgActive else LocalAppColors.current.chipBg,
                                                    androidx.compose.foundation.shape.CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(spec.iconRes),
                                                contentDescription = spec.title,
                                                tint = if (added) LocalAppColors.current.accent else LocalAppColors.current.textSecondary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        if (added) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                null,
                                                tint = LocalAppColors.current.accent,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .align(Alignment.TopEnd),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        spec.title,
                                        color = if (added) LocalAppColors.current.accent else LocalAppColors.current.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                            repeat(4 - rowSpecs.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
            }

            Spacer(Modifier.height(12.dp))
            // 版本号取自构建配置，避免与 build.gradle.kts 里的版本脱节
            ProfileItem(
                Icons.Filled.Info,
                "关于",
                "零控 · 非官方自用客户端 v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
            ) { }

            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalAppColors.current.cardBg, RoundedCornerShape(14.dp))
                    .clickable { scope.launch { runtime.logout(); onLogout() } }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Logout, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("退出登录", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
            }

            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showRename) {
            var nameInput by remember { mutableStateOf(runtime.customName().ifEmpty { runtime.displayName() }) }
            AlertDialog(
                onDismissRequest = { showRename = false },
                containerColor = LocalAppColors.current.cardBg,
                title = { Text("自定义车名", color = LocalAppColors.current.textPrimary) },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(12) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LocalAppColors.current.textPrimary,
                            unfocusedTextColor = LocalAppColors.current.textPrimary,
                            cursorColor = LocalAppColors.current.accent,
                            focusedBorderColor = LocalAppColors.current.accent,
                            unfocusedBorderColor = LocalAppColors.current.border,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        runtime.setCustomName(nameInput)
                        showRename = false
                    }) { Text("保存", color = LocalAppColors.current.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }) { Text("取消", color = LocalAppColors.current.textSecondary) }
                },
            )
        }

        if (showArtwork) {
            AlertDialog(
                onDismissRequest = { showArtwork = false },
                containerColor = LocalAppColors.current.cardBg,
                title = { Text("车模显示方式", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        ArtworkOption(
                            title = "自动（优先官方车图）",
                            desc = "用官方接口下发的本车定妆照，取不到时回退内置剪影",
                            selected = artworkMode != "builtin",
                        ) { artworkMode = "auto" }
                        Spacer(Modifier.height(10.dp))
                        ArtworkOption(
                            title = "内置剪影（按车型轮廓）",
                            desc = "不发网络请求，按车型绘制对应的车身侧视轮廓",
                            selected = artworkMode == "builtin",
                        ) { artworkMode = "builtin" }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        runtime.setCarArtworkMode(artworkMode)
                        showArtwork = false
                    }) { Text("保存", color = LocalAppColors.current.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showArtwork = false }) { Text("取消", color = LocalAppColors.current.textSecondary) }
                },
            )
        }

        if (showAmapKey) {
            var keyInput by remember { mutableStateOf(runtime.amapKey()) }
            AlertDialog(
                onDismissRequest = { showAmapKey = false },
                containerColor = LocalAppColors.current.cardBg,
                title = { Text("高德 Web Key", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text(
                            "用于把车辆经纬度解析成中文地址，以及搜索目的地。"
                                + "请在高德开放平台申请「Web服务」类型的 Key。",
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it.trim() },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LocalAppColors.current.textPrimary,
                                unfocusedTextColor = LocalAppColors.current.textPrimary,
                                cursorColor = LocalAppColors.current.accent,
                                focusedBorderColor = LocalAppColors.current.accent,
                                unfocusedBorderColor = LocalAppColors.current.border,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        runtime.setAmapKey(keyInput)
                        showAmapKey = false
                    }) { Text("保存", color = LocalAppColors.current.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showAmapKey = false }) { Text("取消", color = LocalAppColors.current.textSecondary) }
                },
            )
        }

        if (showPin) {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPin = false },
                containerColor = LocalAppColors.current.cardBg,
                title = { Text("控车密码", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text("输入零跑官方 App 中设置的 4 位操控密码", color = LocalAppColors.current.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword
                            ),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LocalAppColors.current.textPrimary,
                                unfocusedTextColor = LocalAppColors.current.textPrimary,
                                cursorColor = LocalAppColors.current.accent,
                                focusedBorderColor = LocalAppColors.current.accent,
                                unfocusedBorderColor = LocalAppColors.current.border,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = pin.length == 4,
                        onClick = {
                            scope.launch {
                                val ok = runCatching { runtime.verifyAndSavePin(pin) }.getOrDefault(false)
                                message = if (ok) "控车密码已保存" else "密码校验失败"
                                showPin = false
                            }
                        },
                    ) { Text("确认", color = if (pin.length == 4) LocalAppColors.current.accent else LocalAppColors.current.textSecondary) }
                },
                dismissButton = {
                    TextButton(onClick = { showPin = false }) { Text("取消", color = LocalAppColors.current.textSecondary) }
                },
            )
        }
    }
}

@Composable
private fun ArtworkOption(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAppColors.current.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) LocalAppColors.current.chipBgActive else LocalAppColors.current.chipBg,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            null,
            tint = if (selected) accent else LocalAppColors.current.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = LocalAppColors.current.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = LocalAppColors.current.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ProfileItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.cardBg, RoundedCornerShape(16.dp))
            .border(1.dp, LocalAppColors.current.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = LocalAppColors.current.textPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(18.dp))
    }
}
