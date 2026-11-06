package com.leapcontrol.app

import com.leapcontrol.app.ui.CarMotionSettings
import com.leapcontrol.app.ui.carShake
import com.leapcontrol.app.ui.motionFactor
import com.leapcontrol.app.ui.parseSpeedKmh
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 行驶动效（抖动 + 风）的纯逻辑测试。
 *
 * 这一版把上一版的「三层特效 + 十几个参数」整套删掉了，能调的只剩两个值，
 * 所以测试也只守三件事：默认值合不合理、坏数据读进来别把界面搞崩、
 * 抖动在停车时必须是死的（否则首页会一直抖）。
 */
class CarMotionTest {

    @Test
    fun `默认参数在合法范围内`() {
        val d = CarMotionSettings.DEFAULT
        assertEquals(CarMotionSettings.DEFAULT_SHAKE_DP, d.shakeDp, 0.001f)
        assertEquals(0f, d.windAngle, 0.001f)
        // 归一化之后不该有任何变化：默认值本身就是合法的
        assertEquals(d, d.normalized())
    }

    @Test
    fun `抖动幅度夹紧到 0 到 5 dp`() {
        assertEquals(0f, CarMotionSettings(shakeDp = -3f).normalized().shakeDp, 0.001f)
        assertEquals(
            CarMotionSettings.MAX_SHAKE_DP,
            CarMotionSettings(shakeDp = 99f).normalized().shakeDp,
            0.001f,
        )
        // 归一化按 0.1dp 取整：滑块给的是这个精度，存进去也别留 2.0000001
        assertEquals(2.3f, CarMotionSettings(shakeDp = 2.26f).normalized().shakeDp, 0.001f)
    }

    @Test
    fun `风向夹紧到正负 45 度并取整`() {
        assertEquals(-45f, CarMotionSettings(windAngle = -80f).normalized().windAngle, 0.001f)
        assertEquals(45f, CarMotionSettings(windAngle = 80f).normalized().windAngle, 0.001f)
        assertEquals(12f, CarMotionSettings(windAngle = 11.6f).normalized().windAngle, 0.001f)
    }

    @Test
    fun `停在阈值以下一帧都不动`() {
        val s = CarMotionSettings.DEFAULT
        assertFalse(s.animatesAt(null))
        assertFalse(s.animatesAt(0f))
        assertFalse(s.animatesAt(CarMotionSettings.MIN_SPEED_KMH - 0.1f))
        assertFalse(s.animatesAt(Float.NaN))
        assertTrue(s.animatesAt(CarMotionSettings.MIN_SPEED_KMH))
        assertTrue(s.animatesAt(60f))
    }

    @Test
    fun `JSON 往返`() {
        val s = CarMotionSettings(shakeDp = 3.2f, windAngle = -18f).normalized()
        val back = CarMotionSettings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s.shakeDp, back.shakeDp, 0.001f)
        assertEquals(s.windAngle, back.windAngle, 0.001f)
    }

    @Test
    fun `坏数据退回默认值`() {
        assertEquals(CarMotionSettings.DEFAULT, CarMotionSettings.fromJson(null))
        assertEquals(CarMotionSettings.DEFAULT, CarMotionSettings.fromJson(JSONObject("{}")))
        // 上一版三层特效写下的老 JSON：字段名完全不同，应该整份被忽略
        val legacy = JSONObject("""{"on":true,"sandS":85,"gAng":30,"wAng":20}""")
        assertEquals(CarMotionSettings.DEFAULT, CarMotionSettings.fromJson(legacy))
        // 越界的值读进来要夹紧，而不是原样信
        val wild = CarMotionSettings.fromJson(JSONObject("""{"sh":99,"wa":-120}"""))
        assertEquals(CarMotionSettings.MAX_SHAKE_DP, wild.shakeDp, 0.001f)
        assertEquals(-CarMotionSettings.MAX_WIND_ANGLE, wild.windAngle, 0.001f)
    }

    @Test
    fun `车速解析`() {
        assertEquals(60f, parseSpeedKmh("60")!!, 0.001f)
        assertEquals(60.5f, parseSpeedKmh("60.5 km/h")!!, 0.001f)
        assertEquals(0f, parseSpeedKmh("0 km/h")!!, 0.001f)
        assertEquals(null, parseSpeedKmh(null))
        assertEquals(null, parseSpeedKmh(""))
        assertEquals(null, parseSpeedKmh("--"))
    }

    @Test
    fun `速度系数在 120 封顶`() {
        assertEquals(0f, motionFactor(0f), 0.001f)
        assertEquals(0.5f, motionFactor(60f), 0.001f)
        assertEquals(1f, motionFactor(120f), 0.001f)
        assertEquals(1f, motionFactor(200f), 0.001f)
    }

    @Test
    fun `停车时抖动严格为零`() {
        val off = carShake(t = 3.7f, speedKmh = 0f, shakeDp = 2f)
        assertEquals(0f, off.x, 0.0001f)
        assertEquals(0f, off.y, 0.0001f)
        assertEquals(0f, off.rotation, 0.0001f)
    }

    @Test
    fun `抖动幅度为零时不动`() {
        val off = carShake(t = 1.3f, speedKmh = 80f, shakeDp = 0f)
        assertEquals(0f, off.x, 0.0001f)
        assertEquals(0f, off.y, 0.0001f)
    }

    @Test
    fun `抖动幅度大体上随设置增长`() {
        // 同一时刻、同一车速，只改幅度：位移峰值必须跟着变大
        fun peak(dp: Float) = (0..120).maxOf { i ->
            val o = carShake(t = i * 0.017f, speedKmh = 60f, shakeDp = dp)
            abs(o.x) + abs(o.y)
        }
        val small = peak(1f)
        val big = peak(3f)
        assertTrue("3dp 应该比 1dp 抖得多：$small vs $big", big > small * 2f)
    }

    @Test
    fun `抖动峰值不会夸张到离谱`() {
        // 5dp 是滑块上限：纵向峰值不该超过 12dp（否则车模会在卡片里乱跳）
        val peak = (0..200).maxOf { i ->
            abs(carShake(t = i * 0.013f, speedKmh = 120f, shakeDp = 5f).y)
        }
        assertTrue("5dp 时纵向峰值 $peak 太大", peak < 12f)
        assertTrue("5dp 时纵向峰值 $peak 太小，等于没抖", peak > 2f)
    }

    @Test
    fun `抖动不会突然闪跳`() {
        // 5Hz 的抖动一帧本来就有 1dp 左右的位移（这是要的），这里守的是「不该出现
        // 十几 dp 的瞬移」—— 那会让车模在卡片里闪一下。
        var prev = carShake(0f, 60f, 2f)
        var worst = 0f
        for (i in 1..120) {
            val cur = carShake(i * 0.016f, 60f, 2f)
            worst = maxOf(worst, abs(cur.y - prev.y), abs(cur.x - prev.x))
            prev = cur
        }
        assertTrue("相邻帧位移差 $worst 太大", worst < 3f)
    }
}
