package com.leapcontrol.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 「车模外观」页（个性化 → 车模外观）。
 *
 * 为什么要有这一页：官方车模图是服务端按车辆档案渲染好的定妆照，接口只收 VIN，
 * 客户端没有任何「选车型 / 选颜色」的入参（详见 LeapmotorApi.getVehiclePictureMeta）。
 * 所以想让首页那辆车跟自己车漆对得上，只能在自己这边做，两条路都在这里：
 *
 *  1. **内置换色** —— 拿官方 PNG 重着色，白/银/灰/黑这类"去饱和"方向效果最接近真车；
 *     改成有彩色时，车窗和黑色饰条会跟着带一点色，属于近似效果，页面里如实写明。
 *  2. **自定义图片** —— 把官方图存到相册，用户拿去 AI 软件按自己车的颜色改好，
 *     再从相册导回来。质量上限最高，代价是要跳出 App 折腾一趟。
 *
 * 无论走哪条，结果都存成「自定义车模」（按 VIN 分文件），
 * 官方图始终保留，「恢复官方车模」一键回落。
 */
@Composable
fun CarAppearanceScreen(
    runtime: AppRuntime,
    onBack: () -> Unit,
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var official by remember { mutableStateOf<Bitmap?>(null) }
    var custom by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var tintIndex by remember { mutableStateOf<Int?>(null) }
    var strength by remember { mutableStateOf(CarTint.DEFAULT_STRENGTH) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val car3DEnabled by runtime.car3DEnabled.collectAsState()
    val car3DState by runtime.car3DState.collectAsState()

    LaunchedEffect(Unit) {
        official = runCatching { runtime.officialCarModel() }.getOrNull()
        custom = runtime.customCarModel()
        loading = false
    }

    // 预览底图只缩一次：每次拖动强度都重新缩放一张 1600px 的图会明显卡顿
    val previewBase = remember(official) { official?.let { CarModelStore.scaleDown(it, 720) } }

    // 强度量化到 0.05 再做 key —— 滑杆回调比屏幕刷新还密，不量化会排一长串无用的重算
    val strengthKey = (strength * 20).roundToInt()
    LaunchedEffect(tintIndex, strengthKey, previewBase) {
        val base = previewBase
        val index = tintIndex
        if (base == null || index == null) {
            preview = null
            return@LaunchedEffect
        }
        preview = withContext(Dispatchers.Default) {
            CarModelStore.tint(base, CarTint.PRESETS[index].argb, strengthKey / 20f)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = runtime.importCarModel(uri)
            if (result != null) {
                custom = runtime.customCarModel()
                tintIndex = null
                preview = null
            }
            busy = false
            Toast.makeText(
                context,
                when {
                    result == null -> "这张图读不出来，换一张再试"
                    result.backgroundRemoved && result.aligned -> "已抠掉背景，并按官方车模比例对齐"
                    result.aligned -> "已按官方车模的比例对齐"
                    result.backgroundRemoved -> "已抠掉背景（没取到官方图，未做比例对齐）"
                    else -> "没识别出纯色背景，按原图导入"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val shown = preview ?: custom ?: official
    val shownImage = remember(shown) { shown?.asImageBitmap() }
    // 左上角标记按「真正生效」的来源判断：预览中单独标一下，避免误以为已经改好了
    val sourceLabel = when {
        preview != null -> "预览中"
        custom != null -> "自定义车模"
        else -> "官方车模"
    }
    val previewLabel = tintIndex?.let { CarTint.PRESETS[it].label }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "车模外观", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 预览：车模是透明底，必须给一层"展台"衬底，否则白车在白卡上看不见 ----
        SettingsCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(C.accent.copy(alpha = 0.10f), C.iconBg),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    shownImage != null -> Image(
                        bitmap = shownImage,
                        contentDescription = "车模预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                    )

                    loading -> Text("正在读取官方车模…", color = C.textSecondary, fontSize = 12.sp)

                    else -> Text(
                        "还没取到官方车模图\n刷新一次车模后就能在这里改",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                // 左上角来源标记：一眼看出现在用的是官方图、预览中的配色，还是自己那张
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (custom != null || preview != null) C.accent.copy(alpha = 0.16f)
                            else Color.Black.copy(alpha = 0.18f),
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        sourceLabel,
                        color = if (custom != null || preview != null) C.accent else C.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    previewLabel != null -> "正在预览「$previewLabel」，确认后点下面的按钮应用"
                    custom != null -> "首页正在用你自己的这张车模，官方图仍然留着"
                    car3DEnabled && car3DState == AppRuntime.Car3DState.READY ->
                        "首页现在用的是 3D 车模。下面这些改色 / 导入只在关掉 3D 之后才看得到"
                    else -> "官方 2D 图是按车辆档案预先渲染的，颜色可能跟实车不一致；" +
                        "想要颜色和配置都对，把下面的「3D 车模」打开"
                },
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- 3D 车模 ----
        // 2D 图改不了「车型配色」，3D 能 —— 它拿的是官方下发的 modelParam
        // （车型 / 年款 / 车漆色号 / 车顶色 / 座椅），在设备上实时渲染。
        SettingsSection(title = "3D 车模", desc = "官方模型 · 拖动可以转") {
            SettingsCard {
                SwitchRow(
                    label = "用 3D 车模显示我的车",
                    desc = "按官方下发的车漆色号和配置渲染，跟官方 App 看到的是同一套模型",
                    checked = car3DEnabled,
                    onCheckedChange = { on ->
                        runtime.setCar3DEnabled(on)
                        if (on) scope.launch { runCatching { runtime.prepareCarModel3D() } }
                    },
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    car3DStatusText(car3DState, car3DEnabled),
                    color = when (car3DState) {
                        AppRuntime.Car3DState.READY -> C.accent
                        else -> C.textSecondary
                    },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                if (car3DEnabled && custom != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "3D 开着的时候，下面「自定义图片」里的车模不会显示 —— 想用自己那张就先关掉 3D。",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                if (car3DState == AppRuntime.Car3DState.READY ||
                    car3DState == AppRuntime.Car3DState.FAILED
                ) {
                    HairLine()
                    Spacer(Modifier.height(10.dp))
                    ClickRow(
                        title = if (car3DState == AppRuntime.Car3DState.READY) "清除 3D 模型缓存" else "重新下载模型",
                        desc = if (car3DState == AppRuntime.Car3DState.READY) {
                            "${formatMb(runtime.carModel3DSize())} · 清掉后下次进首页会重新下载"
                        } else {
                            "下载失败通常是网络问题，重试一次"
                        },
                        icon = Icons.Filled.RestartAlt,
                        onClick = {
                            if (busy) return@ClickRow
                            runtime.clearCarModel3D()
                            scope.launch { runCatching { runtime.prepareCarModel3D(force = true) } }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 内置换色 ----
        SettingsSection(title = "换个车漆", desc = "以官方图为底 · 近似效果") {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // withIndex 一次拿全下标，后面 selected 判断直接用，不再 indexOf 反查
                    CarTint.PRESETS.withIndex().chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { entry ->
                                ColorSwatch(
                                    preset = entry.value,
                                    selected = tintIndex == entry.index,
                                    onClick = {
                                        tintIndex = if (tintIndex == entry.index) null else entry.index
                                        preview = null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("强度", color = C.textPrimary, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        valueRange = CarTint.MIN_STRENGTH..CarTint.MAX_STRENGTH,
                        enabled = tintIndex != null,
                        colors = SliderDefaults.colors(
                            thumbColor = C.accent,
                            activeTrackColor = C.accent,
                            inactiveTrackColor = C.barTrack,
                            disabledThumbColor = C.barTrackStrong,
                            disabledActiveTrackColor = C.barTrackStrong,
                            disabledInactiveTrackColor = C.barTrack,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(strength * 100).roundToInt()}%",
                        color = if (tintIndex != null) C.accent else C.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.width(38.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                PrimaryAction(
                    label = when {
                        busy -> "处理中…"
                        tintIndex == null -> "先选一个颜色"
                        else -> "应用为车模"
                    },
                    enabled = !busy && tintIndex != null,
                    onClick = {
                        val index = tintIndex ?: return@PrimaryAction
                        scope.launch {
                            busy = true
                            val ok = runtime.tintCarModel(CarTint.PRESETS[index].argb, strength)
                            if (ok) {
                                custom = runtime.customCarModel()
                                preview = null
                            }
                            busy = false
                            Toast.makeText(
                                context,
                                when {
                                    !ok -> "生成失败，先刷新一次车模再试"
                                    // 3D 开着时首页根本不看这张图，不说明白用户会以为没保存上
                                    car3DEnabled -> "已保存；3D 车模开着，首页仍然显示 3D"
                                    else -> "已应用，回首页就能看到"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "换色只改色相和明暗，车身的反光和阴影都保留。改成白 / 银 / 灰 / 黑最接近真车；"
                        + "改成蓝、绿、红这类有彩色时，车窗和黑色饰条会跟着带一点色 —— "
                        + "想要完全准确，用下面的方法把图导出去自己改。",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 自定义图片 ----
        SettingsSection(title = "自定义图片", desc = "质量上限最高的方式") {
            SettingsCard {
                ClickRow(
                    title = "保存当前车模到相册",
                    desc = "透明底 PNG，存到 Pictures/零控，方便拿去改色",
                    icon = Icons.Filled.Download,
                    onClick = {
                        if (busy) return@ClickRow
                        scope.launch {
                            busy = true
                            val ok = runtime.exportCarModelToGallery()
                            busy = false
                            Toast.makeText(
                                context,
                                if (ok) "已保存到相册 Pictures/零控" else "保存失败，请确认系统相册可用",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                HairLine()
                ClickRow(
                    title = "导入改好的图片",
                    desc = "自动抠背景，并按官方车模的比例和留白对齐",
                    icon = Icons.Filled.PhotoLibrary,
                    onClick = {
                        if (busy) return@ClickRow
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                if (custom != null) {
                    HairLine()
                    ClickRow(
                        title = "恢复官方车模",
                        desc = "删掉自定义图，回到官方按档案渲染的那张",
                        icon = Icons.Filled.RestartAlt,
                        onClick = {
                            runtime.clearCustomCarModel()
                            custom = null
                            preview = null
                            tintIndex = null
                            Toast.makeText(context, "已恢复官方车模", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "导入时会先把车模从背景里抠出来，再按官方车模的外框重新摆大小和位置 —— "
                + "所以不管原图是什么比例、车贴着哪个边，首页看到的都一样大、同一个位置。"
                + "白底或纯色底的图能自动去背景；实景照片去不掉，会按原图比例导入。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "车模图只在手机本地处理：不上传、不经过任何服务器，换色结果也只存在本机。"
                + "导入的图片请自己确认版权，仅作个人使用。",
            color = C.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 一个车漆色块：圆点 + 名称，选中时主题色描边 + 白勾。 */
@Composable
private fun ColorSwatch(
    preset: CarTint.Preset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val color = Color(preset.argb)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                // 白色车漆在浅色卡片上会"消失"，统一加一圈细边保证任何底色都看得见轮廓
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) C.accent else C.border.copy(alpha = 0.55f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    // 深色车漆上用白勾，白/银色上勾会自动转深色，避免看不见
                    tint = if (CarTint.toHsl(preset.argb).third > 0.6f) Color(0xFF202226) else Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            preset.label,
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 9.5.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 主操作按钮（整行实心，和弹窗里的确认键同一套观感）。 */
@Composable
private fun PrimaryAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) C.accent else C.barTrack)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else C.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 卡片内的细分隔线（比 Material 的 Divider 更淡，不抢内容）。 */
@Composable
private fun HairLine() {
    val C = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(C.border.copy(alpha = 0.45f)),
    )
}

/**
 * 3D 车模那一行的状态说明。
 *
 * 只说「现在怎样、下一步会怎样」，不写 H5 / WebGL / 模型包这类词 ——
 * 用户要知道的是「还要等多久」「会不会自己好」。
 */
private fun car3DStatusText(state: AppRuntime.Car3DState, enabled: Boolean): String = when {
    !enabled -> "打开后会在后台下载一次官方模型包（约 12MB），下完首页自动换成 3D"
    state == AppRuntime.Car3DState.READY -> "已就绪 · 回首页左右拖动车模可以转起来"
    state == AppRuntime.Car3DState.DOWNLOADING -> "正在下载官方模型包（约 12MB），下完首页会自动换成 3D"
    state == AppRuntime.Car3DState.FAILED -> "模型包没下下来，多半是网络问题；下次进首页会自动重试"
    state == AppRuntime.Car3DState.UNAVAILABLE -> "官方没有给这台车下发 3D 模型，只能用 2D 定妆照"
    else -> "等待下载…"
}

/** 字节数 → 「12.3 MB」，给「清除缓存」那行显示占用。 */
private fun formatMb(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
}
