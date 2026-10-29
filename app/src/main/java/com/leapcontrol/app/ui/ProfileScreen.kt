package com.leapcontrol.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.AppUpdateInfo
import com.leapcontrol.app.R
import com.leapcontrol.app.UpdateCheckResult
import kotlinx.coroutines.launch

/** 意见反馈问卷地址（腾讯问卷）。 */
private const val FEEDBACK_URL = "https://wj.qq.com/s2/28372168/dzhz/"

/** QQ 交流群群号。 */
private const val QQ_GROUP = "179731222"

/**
 * 「我的」页面：入口列表 —— 设置 / 个性化 / 支持开发者 / 开源协议与致谢 / 意见反馈 / 关于，
 * 其中「意见反馈」直接跳外部问卷，「关于」内嵌「免责声明」「隐私条款」两个子入口，
 * 其余项都下沉到各自的二级页；页面顶部是头像（可换内置卡通头像或相册照片），
 * 页面底部是 QQ 交流群页脚与退出登录。
 *
 * 「检查更新」入口由 [SHOW_UPDATE_CHECK] 控制，当前隐藏（改为按发布页手动下载）。
 */
@Composable
fun ProfileScreen(
    runtime: AppRuntime,
    onLogout: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ---- 头像 ----
    // 偏好里只存「选了哪个」，自定义照片是文件；两者分开读，删照片不影响选择记录
    var avatarRaw by remember { mutableStateOf(runtime.avatarChoiceRaw()) }
    // 先吃运行时缓存：切 Tab 回来时第一帧就能显示自定义照片，不会闪一下内置头像
    var customBitmap by remember { mutableStateOf(runtime.cachedCustomAvatar()) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    // ---- 检查更新（从「关于」页上移到本页）----
    // 自动检查的结果来自 AppRuntime；这里只管「用户手动点了一次」的中间态与弹窗。
    val updateInfo by runtime.updateInfo.collectAsState()
    var checkingUpdate by remember { mutableStateOf(false) }
    // 上一次手动检查是否「失败」（网络 / 接口异常），与「已是最新」分开反馈
    var checkFailed by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    // 更新包下载状态：私有仓库必须带令牌在 App 内下载
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (AvatarStore.exists(context)) {
            customBitmap = runtime.customAvatar()
        }
    }

    // 系统相册选择器（Android 13+ 走照片选择器，低版本回落到文件选择，都不需要读存储权限）
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = AvatarStore.import(context, uri)
            if (ok) {
                customBitmap = runtime.reloadCustomAvatar()
                avatarRaw = AvatarChoice.CUSTOM_RAW
                runtime.setAvatarChoiceRaw(avatarRaw)
                showAvatarPicker = false
            } else {
                Toast.makeText(context, "这张图读不出来，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    MaterialTheme(
        colorScheme = if (LocalAppColors.current.isDark) {
            androidx.compose.material3.darkColorScheme(primary = LocalAppColors.current.accent)
        } else {
            androidx.compose.material3.lightColorScheme(primary = LocalAppColors.current.accent)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "我的",
                color = LocalAppColors.current.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))

            // 车辆 / 账号信息
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(
                        choice = AvatarChoice.decode(avatarRaw),
                        customBitmap = customBitmap,
                        size = 46.dp,
                        onClick = { showAvatarPicker = true },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            runtime.displayName(),
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        val model = runtime.vehicleModel()
                        Text(
                            text = listOfNotNull(
                                model.takeIf { it.isNotBlank() },
                                runtime.session.selectedVin.takeLast(6).takeIf { it.isNotBlank() }
                                    ?.let { "VIN $it" },
                            ).joinToString(" · ").ifEmpty { "暂无车辆信息" },
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    // 退出登录：放在车名容器右侧
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(LocalAppColors.current.chipBg)
                            .clickable { scope.launch { runtime.logout(); onLogout() } }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Logout,
                                null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("退出登录", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- 入口 ----
            ProfileItem(
                R.drawable.ic_settings,
                "设置",
                "控车密码、高德 Web Key、车模与系统磁贴",
            ) { onOpenPage(SettingsPage.SETTINGS) }
            Spacer(Modifier.height(12.dp))
            ProfileItem(
                R.drawable.ic_skin,
                "个性化",
                "外观模式、四季主题与动态背景",
            ) { onOpenPage(SettingsPage.PERSONALIZATION) }
            Spacer(Modifier.height(12.dp))
            // 支持开发者 / 开源协议与致谢 / 意见反馈：三个都是「面向外部」的模块，
            // 统一由 SHOW_DEV_ENTRIES 控制（对外发布版为 true）
            if (SHOW_DEV_ENTRIES) {
                // 支持开发者：紧跟在「个性化」之后，两个都是"想帮开发者"的入口
                ProfileItem(
                    R.drawable.ic_support,
                    "支持开发者",
                    "软件完全免费，如果帮到了你，请开发者喝杯咖啡",
                ) { onOpenPage(SettingsPage.SUPPORT) }
                Spacer(Modifier.height(12.dp))
                ProfileItem(
                    R.drawable.ic_opensource,
                    "开源协议与致谢",
                    "基于开源项目二次开发，感谢原作者",
                ) { onOpenPage(SettingsPage.OPEN_SOURCE) }
                Spacer(Modifier.height(12.dp))
                // 意见反馈：腾讯问卷，用系统浏览器打开；设备上没浏览器时会明确提示，而不是点了没反应。
                // 紧挨「关于」上方：都是"最后再看一眼"的位置，用户想吐槽时往下翻一眼就能找到。
                ProfileItem(
                    R.drawable.ic_feedback,
                    "意见反馈",
                    "填写问卷，反馈问题或提出功能建议",
                ) {
                    if (!runtime.openExternal(FEEDBACK_URL)) {
                        Toast.makeText(context, "没有找到可用的浏览器", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 检查更新：已按对外发布策略隐藏（开关见 SHOW_UPDATE_CHECK），
            // 页面与弹窗实现都保留，改回 true 即恢复「启动自动检测 + 手动检查」。
            if (SHOW_UPDATE_CHECK) {
                ProfileItem(
                    R.drawable.ic_system_update,
                    "检查更新",
                    desc = when {
                        checkingUpdate -> "正在检查…"
                        checkFailed -> "检查失败，请检查网络后重试"
                        updateInfo != null -> "发现新版本 v${updateInfo?.version}，点击前往下载"
                        else -> "当前已是最新版本（v${runtime.currentVersion()}）"
                    },
                    showDot = updateInfo != null,
                ) {
                    if (!checkingUpdate) {
                        scope.launch {
                            checkingUpdate = true
                            checkFailed = false
                            val result = runCatching { runtime.checkForUpdate() }.getOrNull()
                            checkingUpdate = false
                            when (result) {
                                is UpdateCheckResult.Newer -> {
                                    pendingUpdate = result.info
                                    updateDownloadError = null
                                    updateProgress = null
                                }
                                UpdateCheckResult.UpToDate -> Unit
                                else -> checkFailed = true
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 关于放最底部：内嵌「免责声明」「隐私条款」入口
            ProfileItem(
                R.drawable.ic_info,
                "关于",
                "版本信息、免责声明与隐私条款",
            ) { onOpenPage(SettingsPage.ABOUT) }

            // 页脚：交流群（放在所有入口之下，居中的小字，不抢主内容的视觉）。
            // 整行可点：点一下就把群号复制到剪贴板，省得手动选数字。
            Spacer(Modifier.height(26.dp))
            val clipboard = LocalClipboardManager.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(QQ_GROUP))
                        Toast.makeText(context, "已复制群号 $QQ_GROUP", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "QQ交流群：$QQ_GROUP",
                    color = LocalAppColors.current.textSecondary.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制群号",
                    tint = LocalAppColors.current.textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.size(13.dp),
                )
            }

            // 署名一行小字：不写真名，统一称「开发者」
            Spacer(Modifier.height(4.dp))
            Text(
                text = "由开发者个人开发 · 完全免费 · 开源",
                modifier = Modifier.fillMaxWidth(),
                color = LocalAppColors.current.textSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            if (showAvatarPicker) {
                AvatarPickerDialog(
                    current = AvatarChoice.decode(avatarRaw),
                    customBitmap = customBitmap,
                    onPickPreset = { index ->
                        avatarRaw = AvatarChoice.preset(index)
                        runtime.setAvatarChoiceRaw(avatarRaw)
                        showAvatarPicker = false
                    },
                    onPickCustom = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClearCustom = {
                        runtime.clearCustomAvatar()
                        customBitmap = null
                        avatarRaw = AvatarChoice.DEFAULT_RAW
                        runtime.setAvatarChoiceRaw(avatarRaw)
                    },
                    onDismiss = { showAvatarPicker = false },
                )
            }
        }

        // ---- 版本更新弹窗（手动「检查更新」触发）----
        // 必须放在 Column 外面：UpdateDialog 是 fillMaxSize 的全屏遮罩，
        // 塞进可滚动 Column 里会被垂直无界约束压成零高。
        if (pendingUpdate != null) {
            UpdateDialog(
                info = pendingUpdate!!,
                currentVersion = runtime.currentVersion(),
                downloading = updateDownloading,
                progress = updateProgress,
                downloadError = updateDownloadError,
                onOpenDownload = {
                    val info = pendingUpdate
                    if (info != null && !updateDownloading) {
                        updateDownloading = true
                        updateProgress = null
                        updateDownloadError = null
                        scope.launch {
                            val result = runtime.downloadUpdate(info) { p ->
                                updateProgress = p.fraction
                            }
                            updateDownloading = false
                            result.fold(
                                onSuccess = { file ->
                                    if (runtime.installUpdate(file)) {
                                        pendingUpdate = null
                                    } else {
                                        updateDownloadError =
                                            "安装包已下载，但需要先在系统里允许「安装未知应用」，授权后回来再点一次"
                                    }
                                },
                                onFailure = { e ->
                                    updateDownloadError = "下载失败：${e.message ?: "网络异常"}"
                                },
                            )
                        }
                    }
                },
                onOpenGithub = {
                    runtime.openExternal(pendingUpdate!!.htmlUrl)
                    pendingUpdate = null
                },
                onDismiss = { pendingUpdate = null },
                onNeverAgain = {
                    runtime.setDismissedVersion(pendingUpdate!!.version)
                    pendingUpdate = null
                },
            )
        }
    }
}

@Composable
internal fun ProfileItem(
    iconRes: Int,
    title: String,
    desc: String,
    /** 标题后跟一个小红点：表示这项有「待处理」的内容（目前只有检查更新用）。 */
    showDot: Boolean = false,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.panelBg, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(iconRes),
            null,
            tint = C.textPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (showDot) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF3B30), CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(desc, color = C.textSecondary, fontSize = 11.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.KeyboardArrowRight,
            null,
            tint = C.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 「我的」页顶部头像。
 *
 * 内置头像是自带色底的圆形 WebP，直接铺满就行；自定义照片先被 [AvatarStore]
 * 中心裁成正方形存下来，这里再用 `ContentScale.Crop` 兜一层，任何比例都不变形。
 *
 * 右下角那枚小相机是唯一的功能提示——没有它，用户不会知道头像能点。
 * 尺寸压到 [size] 的 30%，压在圆弧内圈，不额外描边（全 App 只有选中态才描边）。
 */
@Composable
private fun ProfileAvatar(
    choice: AvatarChoice,
    customBitmap: Bitmap?,
    size: Dp,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val custom = (choice as? AvatarChoice.Custom)?.let { customBitmap }

    // 外层方形、内层圆形：角标挂在方形层的右下角，才不会被圆弧切掉一角
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (custom != null) {
                Image(
                    bitmap = custom.asImageBitmap(),
                    contentDescription = "头像，点击更换",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 自定义照片可能被清掉（清缓存、换机恢复）：回落到内置头像，不能留一块空白
                val index = (choice as? AvatarChoice.Preset)?.index ?: AvatarCatalog.DEFAULT_INDEX
                Image(
                    painter = painterResource(AvatarCatalog.drawableAt(index)),
                    contentDescription = "头像，点击更换",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 唯一的「可点」提示。用 chipBg 而不是卡片底色：浅色主题下白底白圆会看不出来，
        // 只剩一个悬空的灰色相机，看着像头像被挖了个缺口
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.30f)
                .clip(CircleShape)
                .background(C.chipBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = C.textSecondary,
                modifier = Modifier.size(size * 0.17f),
            )
        }
    }
}
