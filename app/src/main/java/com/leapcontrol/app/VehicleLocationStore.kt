package com.leapcontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 一次「车停在哪」的完整快照。
 *
 * [reportedAt] 是**车况报文的时间**，不是我们写缓存的时间：
 * 熄火后车机休眠，车况会一直是同一条，用它才能算出「这个位置到底是几分钟前的」。
 */
data class VehiclePlace(
    val latitude: Double,
    val longitude: Double,
    val reportedAt: Long,
    val address: String? = null,
    /**
     * 这条坐标**从什么时候起没变过**（[LocationAnchor] 的 since）。
     *
     * 和 [reportedAt]（我们拉取的时刻）分开存：断网重进首页时，显示的必须是
     * 「这条位置本身有多旧」，而不是「我上次问云端是什么时候」。0 表示不知道。
     */
    val seenAt: Long = 0L,
    /** 磁盘上是否已经有这张图（只看文件在不在，不预先解码，避免组合期做 IO）。 */
    val hasThumb: Boolean = false,
    val hasFull: Boolean = false,
)

/**
 * 位置的判定规则（纯逻辑，无 Android 依赖，可单测）。
 *
 * 「车还停在不在原地」用**两点距离**判断，不用坐标取整：
 * 取整在网格边界上会出错 —— 车停在 31.2300 附近，GPS 漂 30 米就可能跨到 31.231，
 * 被当成「换了地方」，于是每次进首页都重新请求一次逆地理 + 静态地图。
 * 距离判断没有这个边界问题，成本也就是一次 haversine，几微秒。
 *
 * 100 米这个半径的取法：换车位 / 换停车场一定超过，同一个停车场内漂移一定不到。
 */
object VehicleLocationRules {
    /** 判定「同一停放点」的半径：100 米。 */
    const val SAME_SPOT_RADIUS_KM = 0.1

    /** 缓存里的位置和这次车况的位置是否算「同一个地方」。 */
    fun sameSpot(stored: VehiclePlace?, latitude: Double?, longitude: Double?): Boolean {
        if (stored == null || latitude == null || longitude == null) return false
        if (!isValid(latitude, longitude)) return false
        val km = distanceKm(stored.latitude, stored.longitude, latitude, longitude)
        return km <= SAME_SPOT_RADIUS_KM
    }

    /** 坐标是否可信：排除 null、非有限值、0,0 与越界（车端偶尔上报空值）。 */
    fun isValid(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /** 两点间大圆距离（km）。 */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0088
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 2 * earthRadiusKm * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * 位置已经多久没更新了（毫秒）。
     *
     * 车况时间取不到（0 或未来时间）时返回 null —— 这时不显示「更新于 x 前」，
     * 而不是显示一个假的「刚刚」。
     */
    fun ageMs(reportedAt: Long, now: Long): Long? {
        if (reportedAt <= 0L || reportedAt > now) return null
        return now - reportedAt
    }

    /**
     * 新鲜度文案。
     *
     * 熄火后车机休眠，位置就冻结在熄火点 —— 所以这个时间必须显示出来。
     * 用户按「12 分钟前」去找车是合理的，按「3 天前」去找会白跑一趟，
     * 得让他自己看出来这条位置不新鲜。
     */
    fun ageText(ageMs: Long?): String? = when {
        ageMs == null -> null
        ageMs < 60_000L -> "刚刚"
        ageMs < 3_600_000L -> "${ageMs / 60_000L} 分钟前"
        ageMs < 86_400_000L -> "${ageMs / 3_600_000L} 小时前"
        else -> "${ageMs / 86_400_000L} 天前"
    }

    /** 拿不到中文地址时的兜底：直接给坐标，至少能拿去别处搜。 */
    fun coordinateText(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}

/**
 * 「这条坐标是从什么时候起就是这个值」。
 *
 * ## 为什么需要它
 * 车况报文里的经纬度**没有自己的时间戳**，而 [_lastUpdated][com.leapcontrol.app.ui.AppRuntime]
 * 是**我们拉取的时刻**。于是不管车停在原地三小时还是三天，只要 App 刷新一次车况，
 * 界面就写「更新于 刚刚」—— 这是**假新鲜**：用户看到的是一个说得斩钉截铁、
 * 但其实只表示「我刚问过云端」的时间。
 *
 * 能确定的事实只有一个：**这组数字从什么时候起没变过**。所以这里按坐标值判断
 * 稳定起点：值没变（GPS 抖动不算变）就一直沿用它，值变了才记成现在。
 * 车真的在跑时坐标每次刷新都变，时间照样是「刚刚」；车停着不动，时间就会一直往回长，
 * 长到「3 小时前」「1 天前」—— 那正是该怀疑这个位置的时候。
 */
data class LocationAnchor(
    val latitude: Double,
    val longitude: Double,
    /** 这条坐标第一次被看到的时刻（毫秒）。 */
    val since: Long,
)

/** [LocationAnchor] 的推进规则（纯逻辑，可单测）。 */
object LocationAnchorRule {
    /**
     * 多大的位移还算「没动」：50 米。
     *
     * 取 50 而不取 0：车辆定位只要重新定一次位就会漂几米到几十米，
     * 按「完全相等」判断会让时间每漂一次就归零，等于没修。
     * 也不取 100（和 [VehicleLocationRules.sameSpot] 一致）：那里是「算不算同一个停车点」，
     * 这里是「位置有没有真的动过」，标准该更严一点。
     */
    const val SAME_POSITION_KM = 0.05

    /**
     * 用这一帧的坐标推进锚点。
     *
     * 坐标无效（null / 0,0 / 越界）时**原样返回旧锚点**：这一帧没报位置，
     * 不代表车动了，不能因此把「几小时前」重置成「刚刚」。
     */
    fun next(previous: LocationAnchor?, latitude: Double?, longitude: Double?, now: Long): LocationAnchor? {
        if (!VehicleLocationRules.isValid(latitude, longitude)) return previous
        val lat = latitude!!
        val lng = longitude!!
        if (previous == null) return LocationAnchor(lat, lng, now)
        val moved = VehicleLocationRules.distanceKm(previous.latitude, previous.longitude, lat, lng)
        return if (moved <= SAME_POSITION_KM) {
            // 位置只挪了几米：起点不变，但要跟着更新坐标本身（下次比较用新坐标）
            previous.copy(latitude = lat, longitude = lng)
        } else {
            LocationAnchor(lat, lng, now)
        }
    }
}

/**
 * 坐标纠偏开关的存储（WGS-84 → GCJ-02，见 [ChinaCoordinate]）。
 *
 * 和锚点放在同一个 prefs 里：两者都是「位置这件事」的本地设置，
 * 退出登录 / 换车时由 [VehicleLocationStore.clear] 一起收拾才不会漏。
 */
object CoordinateShiftPrefs {
    private const val PREFS = "leapcontrol_location"
    private const val KEY_WGS_TO_GCJ = "shift_wgs_to_gcj"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WGS_TO_GCJ, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WGS_TO_GCJ, enabled)
            .apply()
    }
}

/**
 * 锚点的落盘（单独一个 prefs，不塞进 `last.json`）。
 *
 * 分开存是有意的：`last.json` 由位置卡在「取图成功」时写，锚点由车况每次刷新时写，
 * 两个节奏完全不同 —— 挤进同一个文件必然互相覆盖（要么地址被抹掉，要么时间被重置）。
 */
object VehicleLocationAnchorStore {
    private const val PREFS = "leapcontrol_location"
    private const val KEY_LAT = "anchor_lat"
    private const val KEY_LNG = "anchor_lng"
    private const val KEY_SINCE = "anchor_since"

    fun load(context: Context): LocationAnchor? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val latitude = prefs.getFloat(KEY_LAT, Float.NaN).toDouble()
        val longitude = prefs.getFloat(KEY_LNG, Float.NaN).toDouble()
        val since = prefs.getLong(KEY_SINCE, 0L)
        if (!VehicleLocationRules.isValid(latitude, longitude) || since <= 0L) return null
        return LocationAnchor(latitude, longitude, since)
    }

    fun save(context: Context, anchor: LocationAnchor?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (anchor == null) {
            editor.clear().apply()
            return
        }
        editor
            // 坐标按 float 存：精度约 1 米，用来判「有没有动过」足够，也不必担心 Double 序列化
            .putFloat(KEY_LAT, anchor.latitude.toFloat())
            .putFloat(KEY_LNG, anchor.longitude.toFloat())
            .putLong(KEY_SINCE, anchor.since)
            .apply()
    }
}

/**
 * 车辆位置的本地缓存（元数据 + 两张地图图）。
 *
 * 目录 `filesDir/location/`：
 *  - `last.json`            坐标 / 报文时间 / 中文地址
 *  - `map_r2.png`           卡片缩略图（浅色主题）
 *  - `map_r2_full.png`      找车页大图（浅色主题）
 *  - `map_r2_dark.png`      卡片缩略图（深色主题）
 *  - `map_r2_full_dark.png` 找车页大图（深色主题）
 *
 * 文件名里的 `r2` 是取景方案的版本号（见 [MAP_REVISION]），升版后旧图自然失效。
 *
 * 深浅两份分开存：深色那份是在下载时翻过配色的（见 [MapDarkTint]），
 * 共用一份文件的话，切换外观模式后要么重新下载一次、要么把反色的图当正常图用 ——
 * 两种都会让人以为「地图坏了」。多存一张几十 KB 的 PNG 比这便宜得多。
 *
 * 写文件一律「先写 `.new` 再 rename」：和老零记同一套理由 ——
 * 落盘中途被杀进程时，replace 是原子操作，不会留下半截 JSON 让下次启动直接崩在解析上。
 *
 * 这里只存**一个位置**，不按坐标分多份：车开到别处时旧缓存已经没有意义，
 * 覆盖掉比留着占空间更合理。
 */
object VehicleLocationStore {
    private const val DIR = "location"
    private const val META = "last.json"

    /**
     * 取景方案的版本号。**改了静态地图的 zoom / size 就必须 +1。**
     *
     * 文件名里带着它：车没挪窝时加载逻辑会命中缓存、一个请求都不发，于是升级后
     * 用户看到的还是上一版那张「两公里远景图」，得等车开出去才换新的。
     * 带上版本号，旧图自然不再被读取，[prune] 顺手清掉。
     */
    private const val MAP_REVISION = 2

    /** 所有地图文件共用的前缀，清理旧版本时按它扫。 */
    private const val MAP_PREFIX = "map"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun metaFile(context: Context) = File(dir(context), META)

    /** 缩略图 / 大图 × 浅色 / 深色：四张图四个名字，互不覆盖。 */
    private fun mapName(full: Boolean, dark: Boolean): String = buildString {
        append(MAP_PREFIX).append("_r").append(MAP_REVISION)
        if (full) append("_full")
        if (dark) append("_dark")
        append(".png")
    }

    private fun mapFile(context: Context, full: Boolean, dark: Boolean): File =
        File(dir(context), mapName(full, dark))

    /** 删掉不属于当前版本的地图文件（升级前留下的旧取景 / 旧的深浅命名）。 */
    private fun prune(context: Context) {
        val keep = setOf(
            mapName(full = false, dark = false),
            mapName(full = true, dark = false),
            mapName(full = false, dark = true),
            mapName(full = true, dark = true),
        )
        runCatching {
            dir(context).listFiles()?.forEach { file ->
                val name = file.name
                // 只管地图这一族（`map*.png`），last.json 与其它文件一律不碰
                if (name.startsWith(MAP_PREFIX) && name.endsWith(".png") && name !in keep) file.delete()
            }
        }
    }

    fun load(context: Context): VehiclePlace? = runCatching {
        val file = metaFile(context)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val latitude = json.optDouble("lat", Double.NaN)
        val longitude = json.optDouble("lng", Double.NaN)
        if (!VehicleLocationRules.isValid(latitude, longitude)) return null
        VehiclePlace(
            latitude = latitude,
            longitude = longitude,
            reportedAt = json.optLong("at", 0L),
            address = json.optString("address").takeIf { it.isNotBlank() },
            // 老缓存没有这个键：退回 at（那时它同时也被当成位置时间用）
            seenAt = json.optLong("seen", 0L).takeIf { it > 0L } ?: json.optLong("at", 0L),
            hasThumb = mapFile(context, full = false, dark = false).length() > 0L,
            hasFull = mapFile(context, full = true, dark = false).length() > 0L,
        )
    }.getOrNull()

    /**
     * 落盘。传 null 的图表示「这次没拿到新图」，**不动磁盘上已有的那张** ——
     * 断网时重新进首页，不该把上次存下来的地图擦成空白。
     *
     * [dark] 决定写进哪一组文件名：图是跟着配色走的一份，不能混。
     */
    fun save(
        context: Context,
        latitude: Double,
        longitude: Double,
        reportedAt: Long,
        address: String?,
        thumb: Bitmap? = null,
        full: Bitmap? = null,
        dark: Boolean = false,
        /** 这条坐标从什么时候起没变过（[LocationAnchor.since]）；0 = 不知道。 */
        seenAt: Long = 0L,
    ) {
        if (!VehicleLocationRules.isValid(latitude, longitude)) return
        runCatching {
            thumb?.let { writeBitmap(mapFile(context, full = false, dark = dark), it) }
            full?.let { writeBitmap(mapFile(context, full = true, dark = dark), it) }
            val json = JSONObject().apply {
                put("lat", latitude)
                put("lng", longitude)
                put("at", reportedAt)
                put("seen", seenAt)
                put("address", address.orEmpty())
            }
            val target = metaFile(context)
            val temp = File(target.parentFile, "$META.new")
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            // 顺手清掉上一版取景留下的图：不清也不会再被读到，只是白占空间
            prune(context)
        }
    }

    fun loadMap(context: Context, full: Boolean = false, dark: Boolean = false): Bitmap? = runCatching {
        val file = mapFile(context, full = full, dark = dark)
        if (file.length() <= 0L) null else BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    /** 换车 / 退出登录时清干净，免得下一个人看到上一位车主的停车位置。 */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    private fun writeBitmap(target: File, bitmap: Bitmap) {
        val temp = File(target.parentFile, "${target.name}.new")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }
}
