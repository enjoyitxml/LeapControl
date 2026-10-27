package com.leapcontrol.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub 版本更新检测。
 *
 * 仓库已公开，匿名访问即可，**不需要任何访问令牌**。
 *
 * 取数分两级、逐级降级，尽量不让「检测」因为单一接口故障而静默失败：
 *  1. REST API `releases/latest`：能拿到版本号、发布页与 APK 直链，信息最全；
 *  2. 兜底：`github.com/{repo}/releases/latest` 的 302 重定向，从最终 URL 解析 tag。
 *     该端点走 github.com 域名，与 api.github.com 是两条不同的网络路径，
 *     api 被限流 / 不可达时仍可能成功。此路径拿不到附件列表，
 *     APK 直链按发版脚本的固定命名规则拼接（`LeapControl-{tag}-debug.apk`）。
 *
 * 仓库 [REPO] 与发版脚本（`scripts/gh-release.py`）保持一致：
 * tag 形如 `vX.Y.Z`，Release 附件为 `LeapControl-vX.Y.Z-debug.apk`。
 */
object GitHubUpdate {
    private const val REPO = "enjoyitxml/LeapControl"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_LATEST = "https://github.com/$REPO/releases/latest"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        // 一次检测最多耗 15 秒：主接口不通时要尽快落到兜底路径，别让用户干等
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 最新发布版信息；无网络 / 解析失败 / 无发布版时返回 null。 */
    fun latestRelease(): AppUpdateInfo? = fetchViaApi() ?: fetchViaRedirect()

    /** 主路径：REST API，失败重试一次（应对偶发的网络抖动 / 5xx）。 */
    private fun fetchViaApi(): AppUpdateInfo? {
        repeat(2) {
            val info = runCatching { requestApi() }.getOrNull()
            if (info != null) return info
        }
        return null
    }

    private fun requestApi(): AppUpdateInfo? {
        val req = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        }
        if (body.isNullOrBlank()) return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val version = tag.removePrefix("v").trim()
        val htmlUrl = root.optString("html_url").takeIf { it.isNotBlank() }
            ?: "https://github.com/$REPO/releases/latest"

        // 优先挑 .apk 附件作为直链，便于「去下载」直达安装包
        val assets = root.optJSONArray("assets")
        var downloadUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
        }

        return AppUpdateInfo(
            version = version,
            tagName = tag,
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl,
        )
    }

    /**
     * 兜底路径：请求 `github.com/{repo}/releases/latest`，OkHttp 自动跟随 302，
     * 从最终 URL（`.../releases/tag/vX.Y.Z`）里解析版本号。
     */
    private fun fetchViaRedirect(): AppUpdateInfo? = try {
        val req = Request.Builder()
            .url(RELEASES_LATEST)
            .header("Accept", "text/html")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            // 跟随重定向后的最终地址，如 https://github.com/xxx/LeapControl/releases/tag/v0.2.4
            val finalUrl = resp.request.url.toString()
            val tag = Regex("/releases/tag/([^/?#]+)").find(finalUrl)?.groupValues?.get(1)
                ?: return null
            val version = tag.removePrefix("v").trim()
            if (version.isBlank()) return null
            AppUpdateInfo(
                version = version,
                tagName = tag,
                htmlUrl = if (finalUrl.contains("/releases/tag/")) finalUrl else RELEASES_LATEST,
                // 此路径拿不到附件列表，按固定命名规则拼直链；若附件改名会 404，
                // 弹窗里还有「GitHub」入口可退回发布页
                downloadUrl = "https://github.com/$REPO/releases/download/$tag/LeapControl-$tag-debug.apk",
            )
        }
    } catch (_: Exception) {
        null
    }

    /** [latest] 是否比 [current] 更新（语义化版本比较）。 */
    fun isNewer(latest: String, current: String): Boolean = compareVersion(latest, current) > 0

    /** 版本号比较：a>b 返回正数，相等返回 0，a<b 返回负数。逐段整数比较。 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

/**
 * 一次更新检测的结果。
 *
 * 必须区分「已是最新」与「检测失败」：以前两者都返回 null，
 * 检测失败时界面照样显示「已是最新」，用户根本看不出功能是否正常。
 */
sealed interface UpdateCheckResult {
    /** 确有更新。 */
    data class Newer(val info: AppUpdateInfo) : UpdateCheckResult

    /** 已是最新版本。 */
    data object UpToDate : UpdateCheckResult

    /** 网络不可达 / 接口异常 / 解析失败。 */
    data object Failed : UpdateCheckResult
}

/** 一个可用更新。 */
data class AppUpdateInfo(
    /** 版本号（不含前缀 v），如 "0.2.4"。 */
    val version: String,
    /** 原始 tag，如 "v0.2.4"。 */
    val tagName: String,
    /** GitHub 发布页地址。 */
    val htmlUrl: String,
    /** APK 直链（Release 含 .apk 附件时），可能为 null。 */
    val downloadUrl: String?,
)
