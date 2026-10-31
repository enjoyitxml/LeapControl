package com.leapcontrol.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS-84 → GCJ-02（「火星坐标」）换算。
 *
 * ## 什么时候需要它
 * 车端上报的经纬度，文档写的是 GCJ-02（与高德同系，可以直接传）。但这份文档是
 * 逆向整理出来的，**「坐标系」这一栏没有抓包证据**：如果云端存的是 GPS 原始的 WGS-84，
 * 那么把它当 GCJ-02 画在高德地图上，整张图会偏 **300~800 米** ——
 * 表现出来就是「点导航过去，目的地不是车停的地方」。
 *
 * 这两种情况的观感完全不同，可以先排掉别的再怀疑坐标：
 *  - 偏几十米、偏的方向不固定 → 是最后定位点的问题（地库丢星、车机休眠），换算救不了；
 *  - 偏几百米、且**每次偏的方向和距离都差不多** → 几乎一定是坐标系。
 *
 * ## 为什么做成开关而不是直接开
 * 如果车端本来就报 GCJ-02，我们再做一次换算等于**主动偏移 500 米**，
 * 比现在更糟。没有真机数据之前不能替用户拍板，所以在找车页给一个可当场验证的开关：
 * 打开 → 地图和地址立刻按换算后的坐标重拉 → 钉子有没有落到车上，一眼就能判断。
 *
 * ## 算法
 * 公开的 GCJ-02 加密算法（各地实现一致）：先按椭球参数求出纬度/经度方向上的偏移量，
 * 再叠加到 WGS-84 上。境外直接返回原值 —— 中国境外的地图不使用这套偏移。
 */
object ChinaCoordinate {
    private const val A = 6378245.0 // Krasovsky 1940 椭球长半轴
    private const val EE = 0.00669342162296594323 // 第一偏心率平方

    /** 中国境外不做偏移（含港澳台以外的地区；这里按官方实现的粗略矩形判断）。 */
    fun isOutOfChina(latitude: Double, longitude: Double): Boolean {
        if (longitude < 72.004 || longitude > 137.8347) return true
        if (latitude < 0.8293 || latitude > 55.8271) return true
        return false
    }

    /**
     * WGS-84 坐标 → GCJ-02 坐标。
     *
     * 返回顺序是 **(纬度, 经度)**，与项目里 `latitude` / `longitude` 的既有约定一致 ——
     * 别按数学书的 (x, y) 去理解，这里第一项是纬度。
     */
    fun wgs84ToGcj02(latitude: Double, longitude: Double): Pair<Double, Double> {
        if (!latitude.isFinite() || !longitude.isFinite()) return latitude to longitude
        if (isOutOfChina(latitude, longitude)) return latitude to longitude

        var deltaLat = transformLatitude(longitude - 105.0, latitude - 35.0)
        var deltaLng = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        deltaLat = deltaLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        deltaLng = deltaLng * 180.0 / (A / sqrtMagic * cos(radLat) * Math.PI)
        return (latitude + deltaLat) to (longitude + deltaLng)
    }

    /** 两个坐标之间偏了多远（米），用来在界面上把「差多少」说清楚。 */
    fun offsetMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double = VehicleLocationRules.distanceKm(fromLatitude, fromLongitude, toLatitude, toLongitude) * 1000.0

    private fun transformLatitude(x: Double, y: Double): Double {
        var result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        result += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        result += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        result += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        result += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return result
    }
}
