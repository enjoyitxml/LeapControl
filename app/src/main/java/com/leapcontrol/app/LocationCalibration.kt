package com.leapcontrol.app

import android.content.Context
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 车端上报的坐标到底是哪一套 —— 这是「定位不准」唯一真正的分岔口。
 *
 * ## 为什么不是一个「换算开关」
 * 车端信号表写的是 GCJ-02，但那份文档没有抓包证据。实际反馈里三种车型三种表现：
 * C11 原样就准（GCJ-02）、有的车型勾上 WGS-84 → GCJ-02 才准（报的是 GPS 原始值）、
 * 还有一批勾了仍偏（百度那套 BD-09，偏移方向和前两者都不一样）。
 * 用一个布尔开关去覆盖三种情况，必然有一批人怎么点都不对。
 *
 * ## [AUTO] 是怎么判的
 * 不少车型会**同时**下发两对坐标（命名键 / `3724`+`3725` / `2`+`3`）。
 * 两对相差二三百米以上时，可以拿 [ChinaCoordinate.wgs84ToGcj02] 去互相印证：
 * 谁换算后正好等于另一个，谁就是 WGS-84，另一个就是 GCJ-02 ——
 * 证据是硬的就直接用对的那一对，用户什么都不用选（见 [VehicleDatumDetector]）。
 * 只有一套坐标时无法自证，[AUTO] 就退化成 [RAW]，保持原样。
 */
enum class CoordinateDatum(
    /** 落盘用的编码。`0` / `1` 与老版本一致，别改。 */
    val code: String,
    /** 选项上的标题。术语照写：会来找这一项的人，多少已经知道自己要找什么。 */
    val label: String,
) {
    AUTO(code = "3", label = "自动"),
    RAW(code = "0", label = "不换算"),
    WGS84(code = "1", label = "WGS-84 → GCJ-02"),
    BD09(code = "2", label = "百度坐标 → GCJ-02");

    companion object {
        fun ofCode(raw: String?): CoordinateDatum? = entries.firstOrNull { it.code == raw?.trim() }
    }
}

/**
 * 一次位置校准的完整状态。
 *
 * 两件事分开存，因为它们的性质不同：
 *  - [datum]「坐标系」是**一次性、整块**的修正：车端若报的是 WGS-84 或 BD-09，
 *    换算一次整张图就会平移几百米，方向和距离由当地决定；
 *  - [east] / [north]「手动偏移」是**连续可调**的修正：拖车标、按方向键，差多少补多少，
 *    残差（比如车端坐标本身有几十米偏差、或换了停车场发现总差 30 米）只能靠它。
 *
 * 老版本只有一个 `convert` 开关，碰到「偏 30 米」这种就没有任何办法，
 * 碰到百度坐标更是怎么开都不对 —— 那正是这次重做要解决的问题。
 *
 * @param east 向东为正（米）；西方为负
 * @param north 向北为正（米）；南方为负
 */
data class LocationCalibration(
    val datum: CoordinateDatum = CoordinateDatum.AUTO,
    val east: Int = 0,
    val north: Int = 0,
) {
    /** 有没有手动偏移（与坐标系选择无关）。 */
    val hasOffset: Boolean get() = east != 0 || north != 0

    /** 用户是不是明确指定了「要换算」的坐标系。 */
    val converts: Boolean
        get() = datum == CoordinateDatum.WGS84 || datum == CoordinateDatum.BD09

    /** 完全没动过：界面、缓存、日志都可以按「和原版一样」处理。 */
    val isIdentity: Boolean get() = datum == CoordinateDatum.AUTO && !hasOffset

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
     * 反过来的话每次改坐标系选项，之前调好的偏移就白调了。
     *
     * @param autoDatum [CoordinateDatum.AUTO] 时由调用方给出的判定结果
     *   （见 [VehicleDatumDetector]）：两套坐标互证成功才会非空，
     *   此时通常是 [CoordinateDatum.RAW] —— 因为那一对本身就是 GCJ-02，
     *   已经选对了，不再需要换算。null 表示没有证据，按原样输出。
     */
    fun apply(
        latitude: Double,
        longitude: Double,
        calibration: LocationCalibration,
        autoDatum: CoordinateDatum? = null,
    ): Pair<Double, Double> {
        if (!latitude.isFinite() || !longitude.isFinite()) return latitude to longitude
        val datum = when (calibration.datum) {
            CoordinateDatum.AUTO -> autoDatum ?: CoordinateDatum.RAW
            else -> calibration.datum
        }
        if (datum == CoordinateDatum.RAW && !calibration.hasOffset) return latitude to longitude
        val (lat, lng) = toGcj02(latitude, longitude, datum)
        return shift(lat, lng, calibration.east, calibration.north)
    }

    /** 把车端坐标换成高德那张图用的 GCJ-02。 */
    fun toGcj02(latitude: Double, longitude: Double, datum: CoordinateDatum): Pair<Double, Double> =
        when (datum) {
            CoordinateDatum.WGS84 -> ChinaCoordinate.wgs84ToGcj02(latitude, longitude)
            CoordinateDatum.BD09 -> ChinaCoordinate.bd09ToGcj02(latitude, longitude)
            CoordinateDatum.AUTO, CoordinateDatum.RAW -> latitude to longitude
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
        calibration.hasOffset -> "已校准 ${calibration.distanceMeters}m"
        calibration.converts -> "已换算坐标系"
        else -> "位置不准？点这里校准"
    }

    /** 坐标系那一项在面板里的状态说明。 */
    fun datumSummary(calibration: LocationCalibration): String = when (calibration.datum) {
        CoordinateDatum.AUTO -> "自动：两套坐标对得上就直接用对的那套"
        CoordinateDatum.RAW -> "不换算：车机上报的坐标原样使用"
        CoordinateDatum.WGS84 -> "已开启 WGS-84 → GCJ-02 换算"
        CoordinateDatum.BD09 -> "已开启百度坐标 → GCJ-02 换算"
    }

    /** 落盘格式：`3|120|-40`（自动、向东 120、向北 -40 = 向南 40）。 */
    fun serialize(calibration: LocationCalibration): String =
        "${calibration.datum.code}|${calibration.east}|${calibration.north}"

    /**
     * 解析落盘内容。任何一格坏了都退回「未校准」——
     * 宁可让用户重新调一次，也不能因为一行脏数据把车标画到别处。
     *
     * 前两版写进去的是 `0`（不换算）/ `1`（WGS-84 换算），编码保持不变，
     * 老用户升级后手里的校准原样生效。
     */
    fun parse(raw: String?): LocationCalibration {
        if (raw.isNullOrBlank()) return LocationCalibration.NONE
        val parts = raw.split("|")
        if (parts.size != 3) return LocationCalibration.NONE
        val datum = CoordinateDatum.ofCode(parts[0]) ?: return LocationCalibration.NONE
        val east = parts[1].trim().toIntOrNull() ?: return LocationCalibration.NONE
        val north = parts[2].trim().toIntOrNull() ?: return LocationCalibration.NONE
        return sanitize(LocationCalibration(datum = datum, east = east, north = north))
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
