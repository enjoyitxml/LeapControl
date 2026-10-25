package com.leapbuddy.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 逆地理解析结果（高德 GCJ-02 坐标系，与车辆下发的经纬度一致，无需转换）。 */
data class AmapAddress(
    val formatted: String,
    val poiName: String,
    val city: String,
    val district: String,
)

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
 * 高德 Web 服务 API 封装（逆地理编码 / 关键字搜索）。
 *
 * 说明：零跑车辆下发的经纬度是 GCJ-02（火星坐标），与高德坐标系一致，
 * 因此可以直接传给高德接口与高德地图 App，不需要做坐标转换。
 */
object AmapWebApi {
    private const val REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"
    private const val SEARCH_URL = "https://restapi.amap.com/v3/place/text"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 经纬度 → 中文地址（含最近兴趣点）。key 为空时直接返回 null。 */
    fun reverseGeocode(key: String, latitude: Double, longitude: Double): AmapAddress? {
        if (key.isBlank()) return null
        val location = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
        val url = "$REGEO_URL?key=$key&location=$location&extensions=all&output=JSON"
        val root = jsonOf(url) ?: return null
        if (root.optString("status") != "1") return null
        val re = root.optJSONObject("regeocode") ?: return null
        val comp = re.optJSONObject("addressComponent")
        val nearestPoi = re.optJSONArray("pois")?.optJSONObject(0)?.optString("name").orEmpty()
        return AmapAddress(
            formatted = re.optString("formatted_address"),
            poiName = nearestPoi,
            city = comp?.optString("city")?.takeIf { it.isNotBlank() }
                ?: comp?.optString("province").orEmpty(),
            district = comp?.optString("district").orEmpty(),
        )
    }

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
