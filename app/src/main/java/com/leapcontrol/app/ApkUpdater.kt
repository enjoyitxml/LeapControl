package com.leapcontrol.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 更新包下载与安装。
 *
 * 仓库为**公开**仓库，下载全程匿名，APK 内不带任何凭据。
 *
 * 走 GitHub API 的 `releases/assets/{id}` + `Accept: application/octet-stream`：
 * 该端点会 302 到短期签名的对象存储地址（`objects.githubusercontent.com`），
 * 真正的字节流从 CDN 取，不反复消耗 API 限额。
 * 备选的 `browser_download_url` 也匿名可用，但它是网页直链，将来若仓库再转为私有会失效；
 * asset id 是接口返回的稳定标识，两种仓库状态下都能用，所以沿用它。
 */
object ApkUpdater {

    /** 下载进度。[total] 为 0 表示服务端没给长度，界面只显示「下载中」。 */
    data class Progress(val downloaded: Long, val total: Long) {
        /** 0f..1f；总长未知时返回 null。 */
        val fraction: Float?
            get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // 安装包 30 MB 左右，弱网下要给足时间，但也不能无限挂
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    /**
     * 下载 [info] 指向的 APK 到缓存目录，返回本地文件。
     *
     * 每次下载前清空 `cacheDir/update/`，避免旧包占着几十兆空间，
     * 也避免"装了上上个版本"这种诡异问题。
     */
    fun download(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Progress) -> Unit = {},
    ): File {
        val assetId = info.assetId
            ?: error("这个版本没有可下载的安装包，请到发布页手动下载")

        val builder = Request.Builder()
            .url(GitHubUpdate.assetApiUrl(assetId))
            .header("Accept", "application/octet-stream")
        // 公开仓库：匿名请求即可，不带任何凭据

        client.newCall(builder.get().build()).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败（HTTP ${resp.code}）")
            val body = resp.body ?: error("安装包响应为空")
            val total = body.contentLength().takeIf { it > 0 } ?: info.assetSize

            // 放内部 files 而不是 cache：cache 在空间紧张时会被系统清掉，
            // 下载完点安装却提示文件不存在很难解释
            val dir = File(context.filesDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }

            val target = File(dir, info.assetName ?: "LeapControl-${info.version}.apk")
            // 先写 .part 再改名：中途失败不会留下一个"看起来完整"的残包
            val partial = File(dir, "${target.name}.part")
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        // 每 256 KB 回报一次，太频繁会让 Compose 每帧都重组
                        if (downloaded - lastReport >= 256 * 1024) {
                            lastReport = downloaded
                            onProgress(Progress(downloaded, total))
                        }
                    }
                    output.flush()
                    onProgress(Progress(downloaded, total))
                }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return target
        }
    }

    /** 是否已允许「安装未知应用」；Android 8 以下默认允许。 */
    fun canInstall(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    } catch (_: Exception) {
        true
    }

    /** 跳到「安装未知应用」授权页。 */
    fun openInstallPermission(context: Context): Boolean = runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * 唤起系统安装器。
     *
     * 用 FileProvider 暴露缓存目录里的文件（`cacheDir` 在 Android 7+ 不允许直接
     * `file://` 共享），并授予一次性读权限。
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() <= 0) return false
        if (!canInstall(context)) return openInstallPermission(context)
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
