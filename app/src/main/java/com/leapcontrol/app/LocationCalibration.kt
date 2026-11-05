package com.leapcontrol.app

import android.content.Context
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 一次位置校准的完整状态。
 *
 * 两件事分开存，因为它们的性质不同：
 *  - [convert]「坐标系换算」是**一次性、整块**的修正：车端若报的是 WGS-84，
 *    换算一次整张图就会平移几百米，方向和距离由当地决定，用户只能选开或不开；
 *  - [east] / [north]「手动偏移」是**连续可调**的修正：拖车标、按方向键，差多少补多少，
 *    残差（比如车端坐标本身有几十米偏差、或换了停车场发现总差 30 米）只能靠它。
 *
 * 老版本只有一个 [convert] 开关，碰到「偏 30 米」这种就没有任何办法 ——
 * 那正是这次重做要解决的问题。
 *
 * @param east 向东为正（米）；西方为负
 * @param north 向北为正（米）；南方为负
 */
data class LocationCalibration(
    val convert: Boolean = false,
    val east: Int = 0,
    val north: Int = 0,
) {
    /** 有没有手动偏移（与 [convert] 无关）。 */
    val hasOffset: Boolean get() = east != 0 || north != 0

    /** 完全没动过：界面、缓存、日志都可以按「和原版一样」处理。 */
    val isIdentity: Boolean get() = !convert && !hasOffset

    /** 手动偏移的直线距离（米）。 */
    val distanceMeters: Int get() = hypot(east.toDouble(), north.toDouble()).roundToInt()

    companion object {
        val NONE = LocationCalibration()
    }
}

/**
 * 校准的算法与文案（纯逻辑，无 Android 依赖，可单测）。
 *
 * ## 为什么不直接用「米→度」的固定系数
 * 纬度方向一度约 111.32 km，全球通用；经度方向一度是 `111.32 km × cos(纬度)`，
 * 上海（31°）只有 95 km，漠河（53°）只有 67 km。用固定值会让「向东 500 米」
 * 在北方多走一截，校准过的点反而更偏 —— 所以这里按当前纬度算。
 *
 * ## 精度
 * 只保留整米。GPS 本身的抖动就有几米，再精细的数字是自欺欺人；
 * 整米也让界面上的「向东 120 米」读起来干净。
 */
object LocationCalibrator {
    /** 纬度方向 1 度对应的米数（WGS-84 平均值）。 */
    const val METERS_PER_DEGREE_LAT = 111_320.0

    /**
     * 允许的最大偏移：3 公里。
     *
     * 坐标系错位的典型量级是 300~800 米，留 3 公里足够覆盖各种离谱情况；
     * 再大就不该是「校准」而是「位置本身取错了」，继续放大只会把钉子拖到别的城市，
     * 让用户以为功能坏了。
     */
    const val MAX_OFFSET_METERS = 3_000

    /** 方向键的步长档位（米）。10 收尾、50 常用、200 一把跨过坐标系级别的误差。 */
    val STEPS = intArrayOf(10, 50, 200)

    /** 把偏移量夹到允许范围内，并取整到米。 */
    fun sanitize(calibration: LocationCalibration): LocationCalibration = calibration.copy(
        east = calibration.east.coerceIn(-MAX_OFFSET_METERS, MAX_OFFSET_METERS),
        north = calibration.north.coerceIn(-MAX_OFFSET_METERS, MAX_OFFSET_METERS),
    )

    /**
     * 施加校准：先换算坐标系，再叠加手动偏移。
     *
     * 顺序不能反。手动偏移是用户**对着换算后的那张图**量出来的，
     * 反过来的话每次改换算开关，之前调好的偏移就白调了。
     */
    fun apply(latitude: Double, longitude: Double, calibration: LocationCalibration): Pair<Double, Double> {
        if (!latitude.isFinite() || !longitude.isFinite()) return latitude to longitude
        if (calibration.isIdentity) return latitude to longitude
        val (lat, lng) = if (calibration.convert) {
            ChinaCoordinate.wgs84ToGcj02(latitude, longitude)
        } else {
            latitude to longitude
        }
        return shift(lat, lng, calibration.east, calibration.north)
    }

    /** 只做「向东 / 向北多少米」的平移。 */
    fun shift(latitude: Double, longitude: Double, east: Int, north: Int): Pair<Double, Double> {
        if (east == 0 && north == 0) return latitude to longitude
        val newLatitude = latitude + north / METERS_PER_DEGREE_LAT
        // 高纬度 cos 会趋近 0（极点附近除零）；夹到 0.01 相当于「最多按 89.4° 算」，
        // 中国境内永远走不到这个分支，只是不让它炸
        val scale = METERS_PER_DEGREE_LAT * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        return newLatitude to (longitude + east / scale)
    }

    /**
     * 从「原来的点」到「用户拖到的点」，量出需要补的东 / 北偏移（米）。
     *
     * 拖动时用这个：用户看到的是**换算后**的钉子，把它拖到正确位置，
     * 差量就是还要补的偏移。
     */
    fun deltaEastNorth(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Pair<Int, Int> {
        val meanLatitude = (fromLatitude + toLatitude) / 2.0
        val north = (toLatitude - fromLatitude) * METERS_PER_DEGREE_LAT
        val east = (toLongitude - fromLongitude) *
            METERS_PER_DEGREE_LAT * cos(Math.toRadians(meanLatitude)).coerceAtLeast(0.01)
        return east.roundToInt() to north.roundToInt()
    }

    /** 「向东 120 米 · 向北 40 米」；没有偏移时说「未校准」。 */
    fun describe(east: Int, north: Int): String {
        if (east == 0 && north == 0) return "未校准"
        val parts = ArrayList<String>(2)
        if (east != 0) parts.add((if (east > 0) "向东 " else "向西 ") + abs(east) + " 米")
        if (north != 0) parts.add((if (north > 0) "向北 " else "向南 ") + abs(north) + " 米")
        return parts.joinToString(" · ")
    }

    /**
     * 地图角上那颗药丸按钮用的短文案。
     *
     * 未校准那句必须**自带动作**（「点这里校准」）：早期写的是「位置不准？校准」，
     * 配一枚灰色图标压在白底上，读起来像一句说明而不是按钮，用户直接略过。
     */
    fun shortLabel(calibration: LocationCalibration): String = when {
        calibration.isIdentity -> "位置不准？点这里校准"
        calibration.distanceMeters == 0 -> "已换算坐标系"
        else -> "已校准 ${calibration.distanceMeters}m"
    }

    /** 落盘格式：`1|120|-40`（开换算、向东 120、向北 -40 = 向南 40）。 */
    fun serialize(calibration: LocationCalibration): String =
        "${if (calibration.convert) 1 else 0}|${calibration.east}|${calibration.north}"

    /**
     * 解析落盘内容。任何一格坏了都退回「未校准」——
     * 宁可让用户重新调一次，也不能因为一行脏数据把车标画到别处。
     */
    fun parse(raw: String?): LocationCalibration {
        if (raw.isNullOrBlank()) return LocationCalibration.NONE
        val parts = raw.split("|")
        if (parts.size != 3) return LocationCalibration.NONE
        val convert = parts[0].trim() == "1"
        val east = parts[1].trim().toIntOrNull() ?: return LocationCalibration.NONE
        val north = parts[2].trim().toIntOrNull() ?: return LocationCalibration.NONE
        return sanitize(LocationCalibration(convert = convert, east = east, north = north))
    }
}

/**
 * 校准的落盘。**按 VIN 分开存**：两辆车各自的偏差互不相干，
 * 把 A 车调好的 500 米套到 B 车上等于凭空造一个新错误。
 *
 * 单独一个 prefs 文件，**不能**塞进 `leapcontrol_location`：
 * [VehicleLocationAnchorStore.save] 在传 null 时走的是 `editor.clear()`，
 * 同一份文件里的其它键会被一起抹掉。
 */
object LocationCalibrationStore {
    private const val PREFS = "leapcontrol_location_calibration"

    private fun key(vin: String) = "cal_" + vin.ifBlank { "default" }

    fun load(context: Context, vin: String): LocationCalibration = runCatching {
        LocationCalibrator.parse(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(vin), null),
        )
    }.getOrDefault(LocationCalibration.NONE)

    fun save(context: Context, vin: String, calibration: LocationCalibration) {
        val clean = LocationCalibrator.sanitize(calibration)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(vin), LocationCalibrator.serialize(clean))
            .apply()
    }

    /** 退出登录：整份清掉（换账号后不该带着上一个人的校准）。 */
    fun clear(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
