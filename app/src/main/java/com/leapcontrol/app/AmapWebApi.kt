package com.leapcontrol.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** POI 搜索结果。 */
data class AmapPoi(
    val id: String,
    val name: String,
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * 高德 Web 服务 API 封装（关键字搜索）。
 *
 * - 关键字搜索：用于「发送到车机」目的地检索。
 *   高德要求 GCJ-02 坐标，而车端上报的 GPS（信号 2/3）正是火星坐标系，可直接传入。
 */
object AmapWebApi {
    private const val SEARCH_URL = "https://restapi.amap.com/v3/place/text"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 关键字搜索目的地，最多返回 [limit] 条。 */
    fun searchPoi(
        key: String,
        keyword: String,
        city: String? = null,
        limit: Int = 20,
    ): List<AmapPoi> {
        if (key.isBlank() || keyword.isBlank()) return emptyList()
        val kw = java.net.URLEncoder.encode(keyword, "UTF-8")
        val cityPart = city?.takeIf { it.isNotBlank() }?.let { "&city=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val url = "$SEARCH_URL?key=$key&keywords=$kw$cityPart&offset=$limit&page=1&output=JSON"
        val root = jsonOf(url) ?: return emptyList()
        if (root.optString("status") != "1") return emptyList()
        val array = root.optJSONArray("pois") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val loc = item.optString("location").split(",")
            if (loc.size != 2) return@mapNotNull null
            val lng = loc[0].toDoubleOrNull() ?: return@mapNotNull null
            val lat = loc[1].toDoubleOrNull() ?: return@mapNotNull null
            AmapPoi(
                id = item.optString("id"),
                name = item.optString("name"),
                address = listOfNotNull(
                    item.optString("cityname").takeIf { it.isNotBlank() },
                    item.optString("adname").takeIf { it.isNotBlank() },
                    item.optString("address").takeIf { it.isNotBlank() },
                ).joinToString(""),
                city = item.optString("cityname"),
                latitude = lat,
                longitude = lng,
            )
        }
    }

    private fun jsonOf(url: String): JSONObject? {
        val body = try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
        if (body.isNullOrBlank()) return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

}
