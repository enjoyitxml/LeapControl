package com.leapcontrol.app.ui

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 车模行驶动效的参数（个性化 → 车模外观 → 行驶动效）。
 *
 * ## 只有两个可调项
 *
 * 上一版是「沙尘 / 风驰 / 掠影三层 + 十几个参数」，用户实测后要求删掉，
 * 只保留一套默认方案：**车身抖动 + 车周围的风**。能调的只剩两项 ——
 * 抖多狠（[shakeDp]）、风往哪斜（[windAngle]）。其余（线密度、长度、颜色、
 * 速度系数）全部写死在 [CarMotionFx] 里，不再暴露。
 *
 * ## 没有总开关
 *
 * 动效跟着行驶状态走：车速低于 [MIN_SPEED_KMH] 一帧都不画，停车 / 锁车 / 打开
 * App 停着不动都不会有东西在飘。所以不需要「先开开关才有动效」这一步。
 */
data class CarMotionSettings(
    /** 车身抖动幅度（dp），默认 [DEFAULT_SHAKE_DP]。 */
    val shakeDp: Float = DEFAULT_SHAKE_DP,
    /**
     * 风线倾角（度）：0 水平，正值往下斜，负值往上斜。
     *
     * 车模朝左，所以风一律**从左往右**吹（车往左开，空气相对车往右走），
     * 这个角度只决定「风往上飘还是往下压」。
     */
    val windAngle: Float = DEFAULT_WIND_ANGLE,
) {
    /** 夹紧到合法范围，并按 UI 精度取整（滑块给的是 0.1dp / 1°）。 */
    fun normalized(): CarMotionSettings = copy(
        shakeDp = (shakeDp * 10f).roundToInt() / 10f,
        windAngle = windAngle.roundToInt().toFloat(),
    ).let {
        it.copy(
            shakeDp = it.shakeDp.coerceIn(MIN_SHAKE_DP, MAX_SHAKE_DP),
            windAngle = it.windAngle.coerceIn(-MAX_WIND_ANGLE, MAX_WIND_ANGLE),
        )
    }

    /**
     * 这一帧该不该动。
     *
     * 速度读不到（null / NaN）也按静止处理：宁可不动，也不要拿一个猜出来的
     * 速度让首页莫名其妙地抖。
     */
    fun animatesAt(speedKmh: Float?): Boolean {
        val s = speedKmh ?: return false
        return s.isFinite() && s >= MIN_SPEED_KMH
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("sh", shakeDp.toDouble())
        put("wa", windAngle.toDouble())
    }

    companion object {
        /** 低于这个车速整层不画（蠕行、等红灯、刚挂挡还没走）。 */
        const val MIN_SPEED_KMH = 3f

        /** 抖动幅度上限。再高一停车一开车就像「车在跳」，不是「在跑」。 */
        const val MIN_SHAKE_DP = 0f
        const val MAX_SHAKE_DP = 5f

        /** 4.0 是演示里 2.6 的 1.5 倍左右，已经能看出明显俯仰；默认往下留了余量。 */
        const val DEFAULT_SHAKE_DP = 2.0f

        /** 风向超过 ±45° 就变成斜着刮的雨了，不像迎面气流。 */
        const val MAX_WIND_ANGLE = 45f
        const val DEFAULT_WIND_ANGLE = 0f

        val DEFAULT = CarMotionSettings()

        fun fromJson(raw: JSONObject?): CarMotionSettings {
            if (raw == null) return DEFAULT
            return CarMotionSettings(
                shakeDp = raw.optDouble("sh", DEFAULT_SHAKE_DP.toDouble()).toFloat(),
                windAngle = raw.optDouble("wa", DEFAULT_WIND_ANGLE.toDouble()).toFloat(),
            ).normalized()
        }
    }
}

/**
 * 行驶动效的落盘。
 *
 * 和车辆命名共用 `leapcontrol_naming`，按 VIN 分键：抖动幅度跟车模图的大小有关
 * （同样的 dp 在窄屏上更明显），换车不该沿用上一辆的手感。
 *
 * 键名带 `_v2`：上一版（三层沙尘那套）用的是 `car_motion_<vin>`，结构完全不同，
 * 直接复用会让老数据被当成新结构读出一堆默认值，不如另起一个键，老键留着不解析。
 */
object CarMotionPrefs {
    private const val PREFS = "leapcontrol_naming"

    private fun key(vin: String): String = "car_motion_v2_${vin.ifBlank { "default" }}"

    fun load(context: Context, vin: String): CarMotionSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(vin), null) ?: return CarMotionSettings.DEFAULT
        return runCatching { CarMotionSettings.fromJson(JSONObject(raw)) }
            .getOrElse { CarMotionSettings.DEFAULT }
    }

    fun save(context: Context, vin: String, settings: CarMotionSettings) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key(vin), settings.normalized().toJson().toString())
                .apply()
        }
    }
}

/** 车速原始值 → km/h。车端信号 1319 可能是 "60"、"60.5"、"60km/h"、"60 km/h"。 */
private val MOTION_SPEED_NUMBER = Regex("""\d+(?:\.\d+)?""")

internal fun parseSpeedKmh(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val hit = MOTION_SPEED_NUMBER.find(raw) ?: return null
    return hit.value.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
}

/**
 * 速度 → 0..1 的强度系数（120 km/h 封顶）。
 *
 * 风线的速度、长度、亮度都乘它：60 km/h 时大约是满速的一半观感，
 * 高速上才该有「呼啸而过」的样子。
 */
internal fun motionFactor(speedKmh: Float): Float =
    (speedKmh / 120f).coerceIn(0f, 1f)
