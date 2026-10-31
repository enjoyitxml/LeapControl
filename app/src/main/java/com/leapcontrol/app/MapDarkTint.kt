package com.leapcontrol.app

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * 把高德的浅色静态图翻成深色底图。
 *
 * ## 为什么要自己翻
 * 高德静态地图 API 没有样式参数：zoom 最大 17、size 最大 1024，能调的只有取景和标注，
 * 拿不到它 App 里那套夜间样式。而深色主题里贴一张纯白地图，整张卡片会变成整屏最亮的东西，
 * 比没有背景还扎眼。所以只能拿到浅色图后在本地重新调色。
 *
 * ## 调色思路：保色相、翻明度
 * 直接在 RGB 上取反（`255 - rgb`）会把绿地翻成紫色、水翻成棕色 —— 颜色信息全毁。
 * 这里走 HSV 里的 **V 反相、H/S 保留**：绿地仍是绿的（只是暗了），水仍是蓝的，
 * 深色文字与路名变成浅色可读，而大面积的米白底子落到近黑。
 *
 * 光反相还不够：浅色地图里底色（V≈0.97）和路面（纯白 V=1.0）只差 1%，反相后都是黑，
 * 路网会整条消失。所以反相后再乘一个 [CONTRAST] 把暗部拉开 ——
 * 路缘线（V≈0.87）翻到 0.27 左右，路面留在 0，于是道路成为「深色通道 + 浅色边线」，
 * 这在深色地图里是很常见的样子，比强行把路提亮更稳（把纯白像素识别成道路再提亮会连带
 * 文字描边一起变亮，整张图会起白噪点，试过，很脏）。
 *
 * ## 为什么在这里做而不是着色器
 * 一张 2 倍图（约 1074×1074）逐像素算一遍是几十毫秒，只在下载那一次、IO 线程上跑一次，
 * 结果直接落盘缓存，不进每一帧。做成 Compose 的 ColorFilter 反而每帧都要算，
 * 而且 [android.graphics.ColorMatrix] 是线性变换，做不到「保色相翻明度」。
 */
object MapDarkTint {
    /** 反相后的对比放大系数。1 = 原样反相（路网几乎看不见），2 是可读与不过曝的折中。 */
    private const val CONTRAST = 2.0f

    /**
     * 反相后压一点饱和度：0.2 = 往同明度的灰里混两成。
     * 深色下纯色（尤其绿地、水面）会显得荧光，压掉两成刚好收住。
     */
    private const val DESATURATE = 0.2f

    /** 深色化了的一份新 Bitmap；原图不动（调用方可能还要拿它写别的缓存）。 */
    fun darken(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        transform(pixels)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /** 就地变换一个 ARGB 像素数组（纯计算，单测直接喂数）。 */
    fun transform(pixels: IntArray) {
        val keep = 1f - DESATURATE
        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = color ushr 24 and 0xFF
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // HSV 里的 V（三个通道的最大值）—— 用它反相，色相与饱和度都还在原值上
            val v = maxOf(r, maxOf(g, b))
            val vNew = ((255 - v) * CONTRAST).toInt().coerceIn(0, 255)
            if (v == 0) {
                // 纯黑（其实只有标注的描边）反相后是纯白，按比例缩放除不了零，单独收掉
                pixels[i] = (alpha shl 24) or 0xFFFFFF
                continue
            }
            val k = vNew.toFloat() / v
            val nr = (r * k * keep + vNew * DESATURATE).roundToInt().coerceIn(0, 255)
            val ng = (g * k * keep + vNew * DESATURATE).roundToInt().coerceIn(0, 255)
            val nb = (b * k * keep + vNew * DESATURATE).roundToInt().coerceIn(0, 255)
            pixels[i] = (alpha shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
    }
}
