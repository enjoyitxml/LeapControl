package com.leapcontrol.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 逆地理编码结果：坐标反查出来的中文地址。 */
data class AmapAddress(
    /** 标准中文地址，如「上海市闵行区申昆路 1500 号」。 */
    val formatted: String,
    val city: String? = null,
    val adcode: String? = null,
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

/** 路径规划返回的轨迹点（GCJ-02，与车端上报的坐标同一套）。 */
data class AmapPoint(val latitude: Double, val longitude: Double)

/**
 * 高德 Web 服务 API 封装。
 *
 * 三个能力，共用一个「Web 服务 Key」（用户在「车辆位置与目的地」卡里自己填的那个）：
 *  - [searchPoi] 关键字搜索：「发送目的地」的目的地检索；
 *  - [staticMap] 静态地图：**当前没有调用方**。找车页换成了可拖动的瓦片地图
 *    （`ui/FindCarMap`），首页缩略图换成了 `ui/MiniMapThumb`（自己拼 256 瓦片），
 *    两者都不需要 Key；函数留着是因为它是最省事的「一张图钉好点」的兜底方案；
 *  - [reverseGeocode] 逆地理编码：经纬度 → 中文地址。
 *
 * 三者都要求 GCJ-02，而车端上报的 GPS（信号 2/3）正是火星坐标系，可直接传入。
 * 绝不在这里做 WGS-84 → GCJ-02 的偏移修正：坐标本来就没偏，转了反而错几百米。
 *
 * 额度提醒：个人认证开发者静态地图 / 逆地理各 5000 次/日，**关键字搜索只有 100 次/日**。
 * 所以调用方必须走 [com.leapcontrol.app.VehicleLocationCache]，别在组合期直接发请求。
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

    /**
     * 静态地图：把车辆位置画成一张「已经钉好图钉」的图片。
     *
     * 为什么不用高德地图 SDK：Android 地图 SDK 的 Key 必须绑定包名 + 签名 SHA1，
     * 用户自己申请的 Key 填进这个包一定鉴权失败；而静态地图走 Web 服务 Key ——
     * 就是「发送目的地」里那一个，用户自己能换、能查额度。代价是不能拖动缩放。
     * **现在两处地图都不用它了**：找车页是开源控件 + 高德栅格瓦片（不需要任何 Key），
     * 首页那张 96dp 缩略图也改成了自己拼 2×2 瓦片（`ui/MiniMapThumb`）。
     * 这个函数保留为兜底：真需要「一张固定不动、钉好点的图」时它仍然是最省事的方案。
     *
     * 坐标系：车端上报的就是 GCJ-02（信号 2/3），与高德一致，**不做任何转换**。
     * 直接把 WGS-84 当 GCJ-02 用会偏几百米，反过来也一样，这个坑别踩。
     *
     * ## 取景由 [spanMeters] 决定
     * 调用方只说「画面里想装下多少米」，由 [MapFraming] 折算成高德的 `size`；
     * zoom 固定顶在 17（高德上限），再远就只剩更远的档位了。
     * 早期版本写死 `zoom=16`，找车页那张 1024 的图覆盖 2.1 公里，车钉在中间根本认不出是哪栋。
     *
     * ## 深色主题
     * 高德静态图没有样式参数，拿不到夜间配色。`dark = true` 时在本地把浅色图翻成深色
     * （见 [MapDarkTint]），转换只做一次，调用方应当把结果落盘缓存。
     *
     * 返回 null 表示拿不到图（没网 / Key 失效 / 额度用尽 / 返回的不是图片），
     * 调用方要降级成「只有地址文字」，不要画灰色占位块。
     */
    fun staticMap(
        key: String,
        latitude: Double,
        longitude: Double,
        /** 画面横向大致覆盖多少米（见 [MapFraming.THUMB_SPAN_METERS] / [MapFraming.FULL_SPAN_METERS]）。 */
        spanMeters: Double,
        /** true = 深色主题，返回翻转配色后的图。 */
        dark: Boolean = false,
        markerColor: String = "0x1D9E75",
    ): Bitmap? {
        if (key.isBlank()) return null
        if (!isValidCoordinate(latitude, longitude)) return null
        val point = "${fixed(longitude)},${fixed(latitude)}"
        val size = MapFraming.requestSize(spanMeters, latitude)
        val url = buildString {
            append("https://restapi.amap.com/v3/staticmap")
            append("?location=").append(point)
            append("&zoom=").append(MapFraming.MAX_ZOOM)
            append("&size=").append(size).append("*").append(size)
            // scale=2 要高清图：zoom 已经顶在 17，高德不会再抬级别（文档原文「当 zoom 为最大值时，
            // zoom 不再改变」），只把像素翻一倍 —— 取景范围由 size 决定，清晰度由 scale 决定
            append("&scale=2")
            append("&markers=").append("mid,").append(markerColor).append(",A:").append(point)
            append("&key=").append(key)
        }
        val bitmap = try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                // Key 失效 / 额度用尽时高德回的是 JSON，decodeStream 自然返回 null
                response.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            null
        } ?: return null
        // 翻转配色放在这里而不是展示层：展示层每次重组都翻一遍会白烧几十毫秒
        return if (dark) MapDarkTint.darken(bitmap) else bitmap
    }

    /**
     * 逆地理编码：坐标 → 中文地址。
     *
     * `extensions=base` 而不是 `all`：`all` 会连周边 POI 一起返回，包体大一倍，
     * 而我们只需要一句话地址。直辖市（北京 / 上海…）的 `city` 字段会返回空数组，
     * 这里统一兜逼到 `province`，别让 UI 出现空白。
     */
    fun reverseGeocode(key: String, latitude: Double, longitude: Double): AmapAddress? {
        if (key.isBlank()) return null
        if (!isValidCoordinate(latitude, longitude)) return null
        val url = "https://restapi.amap.com/v3/geocode/regeo" +
            "?key=$key&location=${fixed(longitude)},${fixed(latitude)}&extensions=base&output=json"
        val root = jsonOf(url) ?: return null
        if (root.optString("status") != "1") return null
        val regeo = root.optJSONObject("regeocode") ?: return null
        val formatted = regeo.optString("formatted_address").trim()
        if (formatted.isEmpty()) return null
        val component = regeo.optJSONObject("addressComponent")
        return AmapAddress(
            formatted = formatted,
            city = text(component?.opt("city")) ?: text(component?.opt("province")),
            adcode = text(component?.opt("adcode")),
        )
    }

    /**
     * 驾车路径规划：把稀疏采样点之间「没画到的路」按高德导航路线补齐。
     *
     * 为什么需要它：60 秒一个采样点，两点之间可能拐了好几个弯，直接连直线在地图上
     * 会穿楼穿河。用导航路线把每两个相邻点之间的真实道路补出来，观感才对。
     * **这不是真实行驶轨迹**：车当时到底走哪条路我们不知道，只是「两点之间最合理的
     * 开车路线」，所以图上必须写明这是按导航路线补齐的示意。
     *
     * `waypoints` 官方上限 16 个，调用方自己切段（见 `map/TripRoute`）。
     * `strategy=0`（速度优先）够用，别开 `extensions=all`：返回里那一堆收费 / 红绿灯
     * 信息我们一个都不用，白白把包体翻倍。
     *
     * 返回 null 表示拿不到（没 Key、没网、额度用尽、status!=1），调用方退回直线。
     */
    fun drivingRoute(
        key: String,
        origin: AmapPoint,
        destination: AmapPoint,
        waypoints: List<AmapPoint> = emptyList(),
    ): List<AmapPoint>? {
        if (key.isBlank()) return null
        if (!isValidCoordinate(origin.latitude, origin.longitude)) return null
        if (!isValidCoordinate(destination.latitude, destination.longitude)) return null
        if (waypoints.size > MAX_WAYPOINTS) return null
        fun point(p: AmapPoint) = "${fixed(p.longitude)},${fixed(p.latitude)}"
        val url = buildString {
            append("https://restapi.amap.com/v3/direction/driving")
            append("?key=").append(key)
            append("&origin=").append(point(origin))
            append("&destination=").append(point(destination))
            if (waypoints.isNotEmpty()) {
                append("&waypoints=").append(waypoints.joinToString(";") { point(it) })
            }
            append("&strategy=0&extensions=base&output=json")
        }
        val root = jsonOf(url) ?: return null
        if (root.optString("status") != "1") return null
        val paths = root.optJSONObject("route")?.optJSONArray("paths") ?: return null
        val steps = paths.optJSONObject(0)?.optJSONArray("steps") ?: return null
        val out = mutableListOf<AmapPoint>()
        for (i in 0 until steps.length()) {
            val polyline = steps.optJSONObject(i)?.optString("polyline").orEmpty()
            if (polyline.isEmpty()) continue
            for (pair in polyline.split(";")) {
                val parts = pair.split(",")
                if (parts.size != 2) continue
                val lng = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                if (!isValidCoordinate(lat, lng)) continue
                // 相邻 step 首尾重复，跳过一个
                val last = out.lastOrNull()
                if (last != null && last.latitude == lat && last.longitude == lng) continue
                out += AmapPoint(lat, lng)
            }
        }
        return out.takeIf { it.size >= 2 }
    }

    /** 驾车路径规划的途经点上限（高德官方限制）。 */
    const val MAX_WAYPOINTS = 16

    /** 经纬度是否看起来像真的（排除 0,0 与越界，车端偶尔会上报空值）。 */
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /** 高德要求「经度,纬度」且小数不超过 6 位。 */
    private fun fixed(value: Double): String = String.format(Locale.US, "%.6f", value)

    /** `city` 在直辖市是空数组，`optString` 会得到 "[]"，这里统一当空处理。 */
    private fun text(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        else -> value.toString().trim().takeIf { it.isNotEmpty() && it != "[]" }
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
