package com.leapcontrol.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapcontrol.app.R
import java.io.File
import java.io.FileOutputStream

/** 收款码：显示名 + 图片资源。 */
private enum class PayQr(
    val label: String,
    val resId: Int,
    val fileName: String,
) {
    ALIPAY("支付宝", R.drawable.pay_qr_alipay, "零控-支付宝收款码.jpg"),
    WECHAT("微信", R.drawable.pay_qr_wechat, "零控-微信收款码.jpg"),
}

/**
 * 「支持开发者」页（从「我的」进入）：展示支付宝 / 微信收款码，可保存到相册。
 *
 * 保存走 MediaStore：Android 10 起无需任何权限；Android 9 及以下需要
 * WRITE_EXTERNAL_STORAGE，由页面在点击时按需申请（manifest 里也只声明到 28）。
 */
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onOpenOpenSource: () -> Unit = {},
) {
    val C = LocalAppColors.current
    val context = LocalContext.current
    var selected by remember { mutableIntStateOf(0) }
    val qr = PayQr.entries[selected]

    // Android 9 及以下保存图片需要存储权限，点击「保存」时按需申请
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) doSave(context, qr)
        else Toast.makeText(context, "需要存储权限才能保存收款码", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsTopBar(title = "支持开发者", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        // ---- 心里话 ----
        SettingsCard {
            Text(
                "零控完全免费",
                color = C.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "没有广告、没有付费墙、不卖数据，也不收集你的任何车辆信息。"
                    + "它最初只是我给自己写的一个小工具，后来用着顺手，就一点点打磨成了现在这个样子。\n\n"
                    + "如果这个 App 让你的用车更方便了，欢迎请我喝杯咖啡 —— "
                    + "你的每一份支持，都是我继续调界面、跟协议、修 bug 的动力。\n\n"
                    + "当然，不打赏也完全没关系：你愿意用它，本身就是对我的肯定。",
                color = C.textSecondary,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- 收款码 ----
        SettingsCard {
            // 两档切换：支付宝 / 微信
            Row(modifier = Modifier.fillMaxWidth()) {
                PayQr.entries.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    QrTab(
                        label = item.label,
                        selected = index == selected,
                        modifier = Modifier.weight(1f),
                    ) { selected = index }
                }
            }
            Spacer(Modifier.height(14.dp))
            // 收款码原图自带白底与平台标识，外面再垫一层白卡片，深/浅色下都干净
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(qr.resId),
                    contentDescription = "${qr.label}收款码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            Spacer(Modifier.height(14.dp))
            // 保存按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.accent)
                    .clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            doSave(context, qr)
                        } else {
                            permissionLauncher.launch(
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            )
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("保存${qr.label}收款码到相册", color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "保存后打开${qr.label} → 扫一扫 → 相册，选中这张图片即可。"
                    + "金额随意，一块钱也是心意。谢谢！",
                color = C.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        // ---- 也别忘了原作者 ----
        Spacer(Modifier.height(12.dp))
        ClickRow(
            title = "开源协议与致谢",
            desc = "零控基于开源项目二次开发，也欢迎支持原作者",
            iconRes = R.drawable.ic_opensource,
            onClick = onOpenOpenSource,
        )

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 收款码切换档位。 */
@Composable
private fun QrTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val C = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) C.chipBgActive else C.chipBg, shape)
            .border(1.dp, if (selected) C.accent.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 把收款码原图写入相册（Pictures/零控）。结果用 Toast 提示。 */
private fun doSave(context: Context, qr: PayQr) {
    val ok = runCatching { saveQrToGallery(context, qr) }.getOrDefault(false)
    Toast.makeText(
        context,
        if (ok) "已保存到相册 Pictures/零控" else "保存失败，请截图后扫码",
        Toast.LENGTH_SHORT,
    ).show()
}

private fun saveQrToGallery(context: Context, qr: PayQr): Boolean {
    val bmp = BitmapFactory.decodeResource(context.resources, qr.resId) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+：走 MediaStore，不需要任何存储权限
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, qr.fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/零控")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            val stream = context.contentResolver.openOutputStream(uri) ?: return false
            stream.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    } else {
        // Android 9 及以下：直接写公共目录并触发扫描（权限已在页面里申请）
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "零控",
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        val file = File(dir, qr.fileName)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null,
        )
        true
    }
}
