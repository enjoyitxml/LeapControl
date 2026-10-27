package com.leapcontrol.app.ui

/**
 * 车漆重着色的**纯数学部分**。
 *
 * 刻意不引用 android.graphics：这里只做 ARGB Int 运算，JVM 单测可以直接跑，
 * 真正的 Bitmap 读写放在 [CarModelStore] 里。
 *
 * 原理：官方定妆照是透明底、带反光和阴影的「真车照片级渲染图」，
 * 直接整图染色的结果是塑料感。这里只做三件事：
 *  1. 换色相 —— 车漆色由目标色决定；
 *  2. 换饱和度 —— 向目标色靠拢，但保留像素原本的相对差异；
 *  3. 重曝光 —— 统计车身明度的 6% / 94% 分位，线性拉伸到目标色的明度带。
 * 三件事都是「按像素权重」混合，所以反光走向、腰线、轮毅阴影全都保留。
 *
 * 已知取舍（UI 上必须讲清楚）：算法分不清「车漆」和「玻璃 / 黑色饰条」，
 * 深色区域只降到 0.55 权重、纯黑降到 0 权重，能压住大部分脏边，
 * 但把白色车改成彩色时，车窗仍会带上一点目标色。
 */
object CarTint {

    /** 一个预设车漆：label 显示在色板下方，argb 是目标颜色。 */
    data class Preset(val label: String, val argb: Int)

    /**
     * 预设色板。顺序有意为之：前四个是「去饱和」方向（白 / 银 / 灰 / 黑），
     * 这条方向的效果最接近真车，放前面；后面五个是有彩色，效果是近似值。
     */
    val PRESETS: List<Preset> = listOf(
        Preset("珍珠白", 0xFFF4F5F7.toInt()),
        Preset("银灰", 0xFFC3C8CF.toInt()),
        Preset("哑灰", 0xFF8C9199.toInt()),
        Preset("曜石黑", 0xFF17181C.toInt()),
        Preset("深海蓝", 0xFF1F3C88.toInt()),
        Preset("天空蓝", 0xFF4A80B8.toInt()),
        Preset("松石绿", 0xFF1F6F5C.toInt()),
        Preset("熔岩红", 0xFFAF2B2B.toInt()),
        Preset("暮光紫", 0xFF4B3A6B.toInt()),
    )

    /** 默认强度：够明显又不会把反光压平。 */
    const val DEFAULT_STRENGTH: Float = 0.85f

    /** 强度滑杆允许的范围。 */
    const val MIN_STRENGTH: Float = 0.30f
    const val MAX_STRENGTH: Float = 1.00f

    /** 直方图桶数：64 桶足够定位分位，又不至于为了精度多扫一遍。 */
    private const val BUCKETS = 64

    /**
     * 车身明度分布。
     *
     * [loL]/[hiL] 是权重大于 0 的像素的 6% / 94% 分位 —— 用分位而不是 min/max，
     * 是为了不让个别高光点把拉伸区间撑满，否则车身会整片过曝。
     */
    data class Stats(val loL: Float, val hiL: Float, val avgL: Float) {
        companion object {
            /** 图里几乎没有可用像素时的兜底区间（浅色车漆的常见分布）。 */
            val EMPTY = Stats(0.15f, 0.85f, 0.5f)
        }
    }

    // ---------------- 颜色分解 ----------------

    fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    /** ARGB → (色相 0..1, 饱和度 0..1, 明度 0..1)。 */
    fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val delta = max - min
        if (delta <= 1e-6f) return Triple(0f, 0f, l)
        val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val h = when (max) {
            r -> ((g - b) / delta + if (g < b) 6f else 0f)
            g -> ((b - r) / delta + 2f)
            else -> ((r - g) / delta + 4f)
        } / 6f
        return Triple(h, s.coerceIn(0f, 1f), l)
    }

    /** (色相, 饱和度, 明度) + alpha → ARGB。 */
    fun toArgb(h: Float, s: Float, l: Float, alpha: Int): Int {
        val hh = (h % 1f + 1f) % 1f
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        if (ss <= 1e-6f) {
            val v = (ll * 255f + 0.5f).toInt().coerceIn(0, 255)
            return (alpha.coerceIn(0, 255) shl 24) or (v shl 16) or (v shl 8) or v
        }
        val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
        val p = 2f * ll - q
        fun channel(t: Float): Int {
            var tt = t
            if (tt < 0f) tt += 1f
            if (tt > 1f) tt -= 1f
            val v = when {
                tt < 1f / 6f -> p + (q - p) * 6f * tt
                tt < 0.5f -> q
                tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
                else -> p
            }
            return (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        return (alpha.coerceIn(0, 255) shl 24) or (channel(hh + 1f / 3f) shl 16) or
            (channel(hh) shl 8) or channel(hh - 1f / 3f)
    }

    // ---------------- 车身识别 ----------------

    /**
     * 这个像素「有多像车漆」：0 = 完全不参与换色，1 = 完全跟着目标色走。
     *
     * 判据只有明度（透明底 PNG 的车身是画面主体里最亮的一大片）：
     *  - 近黑（轮胎、轮拱缝隙、投影）：0，染了会脏；
     *  - 深色（玻璃、门缝、暗部）：0.55，压一半，避免玻璃染成彩色；
     *  - 近白（高光）：0.35，留一点原色，否则反光会被抹平；
     *  - 其余：1。
     * 最后按 alpha 加权，半透明边缘不会被硬染出锯齿。
     */
    fun bodyWeight(argb: Int): Float {
        val a = alphaOf(argb)
        if (a < 16) return 0f
        val l = toHsl(argb).third
        val base = when {
            l < 0.045f -> 0f
            l < 0.18f -> 0.55f
            l > 0.975f -> 0.35f
            else -> 1f
        }
        return base * (a / 255f)
    }

    /** 扫描一遍像素，拿到车身明度的分位与均值（决定重曝光的拉伸区间）。 */
    fun stats(pixels: IntArray): Stats {
        if (pixels.isEmpty()) return Stats.EMPTY
        val buckets = IntArray(BUCKETS)
        var count = 0
        var sum = 0f
        for (p in pixels) {
            if (bodyWeight(p) <= 0f) continue
            val l = toHsl(p).third
            buckets[(l * (BUCKETS - 1)).toInt().coerceIn(0, BUCKETS - 1)]++
            count++
            sum += l
        }
        if (count < 16) return Stats.EMPTY
        val lowTarget = count * 0.06f
        val highTarget = count * 0.94f
        var acc = 0
        var lo = 0.15f
        var hi = 0.85f
        var loFound = false
        for (i in buckets.indices) {
            acc += buckets[i]
            if (!loFound && acc >= lowTarget) {
                lo = i / (BUCKETS - 1f)
                loFound = true
            }
            if (acc >= highTarget) {
                hi = i / (BUCKETS - 1f)
                break
            }
        }
        // 区间太窄（纯色很平的车模）就撑开一点，否则拉伸倍数会爆炸
        if (hi - lo < 0.06f) {
            hi = (lo + 0.06f).coerceAtMost(1f)
            lo = (hi - 0.06f).coerceAtLeast(0f)
        }
        return Stats(lo, hi, sum / count)
    }

    /**
     * 把单个像素换到目标车漆。
     *
     * @param strength 0 = 原样；1 = 完全换成目标色
     * @param stats 同一张图的 [stats] 结果，决定明度怎么拉伸
     */
    fun tintPixel(
        argb: Int,
        targetArgb: Int,
        strength: Float,
        stats: Stats = Stats.EMPTY,
    ): Int {
        if (strength <= 0f) return argb
        val a = alphaOf(argb)
        if (a == 0) return argb
        val w0 = bodyWeight(argb)
        if (w0 <= 0f) return argb
        val w = (w0 * strength).coerceIn(0f, 1f)

        val (_, s0, l0) = toHsl(argb)
        val (ht, st, lt) = toHsl(targetArgb)

        // 目标色的明度带：上沿略高于目标明度，留出高光余量；下沿压到 42%，留出暗部
        val targetLo = (lt * 0.42f).coerceIn(0f, 1f)
        val targetHi = (lt * 1.06f + 0.04f).coerceIn(0f, 1f)
        val span = (stats.hiL - stats.loL).coerceAtLeast(0.04f)
        val mapped = (targetLo + (l0 - stats.loL) / span * (targetHi - targetLo))
            .coerceIn(0f, 1f)

        val ns = (s0 + (st - s0) * w).coerceIn(0f, 1f)
        val nl = (l0 + (mapped - l0) * w).coerceIn(0f, 1f)
        return toArgb(ht, ns, nl, a)
    }

    /** 整块像素批量换色（就地修改 [pixels]）。 */
    fun tintPixels(pixels: IntArray, targetArgb: Int, strength: Float) {
        if (pixels.isEmpty() || strength <= 0f) return
        val stats = stats(pixels)
        for (i in pixels.indices) {
            pixels[i] = tintPixel(pixels[i], targetArgb, strength, stats)
        }
    }
}
