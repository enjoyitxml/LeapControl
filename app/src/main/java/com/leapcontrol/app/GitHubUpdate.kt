package com.leapcontrol.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub 版本更新检测。
 *
 * 通过公开的 Releases API 取仓库最新发布版（[releases/latest] 已自动排除
 * Pre-release 与 Draft），与本地 [BuildConfig.VERSION_NAME] 比较，
 * 有新版本时给出下载入口。
 *
 * 仓库 [REPO] 与发版脚本（`scripts/gh-release.py`）保持一致：
 * tag 形如 `vX.Y.Z`，Release 附件为 `LeapControl-vX.Y.Z-debug.apk`。
 */
object GitHubUpdate {
    private const val REPO = "enjoyitxml/LeapControl"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 最新发布版信息；无网络 / 解析失败 / 无发布版时返回 null。 */
    fun latestRelease(): AppUpdateInfo? {
        val body = try {
            client.newCall(Request.Builder().url(API).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            return null
        }
        if (body.isNullOrBlank()) return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val version = tag.removePrefix("v").trim()
        // 直接给发布页；用户可在页面查看更新说明并下载 APK
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

/** 一个可用更新。 */
data class AppUpdateInfo(
    /** 版本号（不含前缀 v），如 "0.2.2"。 */
    val version: String,
    /** 原始 tag，如 "v0.2.2"。 */
    val tagName: String,
    /** GitHub 发布页地址。 */
    val htmlUrl: String,
    /** APK 直链（Release 含 .apk 附件时），可能为 null。 */
    val downloadUrl: String?,
)
