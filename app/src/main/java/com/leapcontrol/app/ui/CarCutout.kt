package com.leapcontrol.app.ui

/**
 * 车模抠图 + 取景框计算（纯 IntArray 运算，不 import android.graphics，方便 JVM 单测）。
 *
 * 为什么需要它：官方车模图是「车模居中、四周一大圈透明留白」的定妆照（实测车模内容只占
 * 画布约 76% 宽），而用户从外部拿回来的图往往是「车贴着画面边缘」的紧裁图。两者同样按
 * ContentScale.Fit 显示，后者的车就会比官方大一圈还贴边 —— 这就是「比例不协调」的根因。
 *
 * 所以导入分两步：
 *  1. [removeBackground]：把纯色 / 白底背景啃掉，只留车模本体（AI 已经抠好透明底的图跳过）；
 *  2. [contentBox] 量出车模的真实外框，交给 CarModelStore.alignToTemplate 按官方图的外框
 *     重新摆一遍位置和大小。
 */
object CarCutout {

    /** alpha 低于它算「空」—— 用户图里常有 1~2 的噪点 alpha。 */
    const val ALPHA_EMPTY = 8

    /**
     * 默认背景判定容差（三通道最大差值，0~255）。
     *
     * 30 对白底 / 灰底 / 纯色底都够用；再大容易沿着车身边缘的浅色反光啃进车身。
     */
    const val DEFAULT_TOLERANCE = 30

    /** 啃掉的面积超过这个比例，判定「把车也一起啃了」，放弃抠图。 */
    private const val MAX_REMOVED_RATIO = 0.78f

    /** 啃掉的面积不到这个比例，说明本来就没有背景可去。 */
    private const val MIN_REMOVED_RATIO = 0.05f

    /** 透明像素占比超过它，认为这张图本来就抠好了。 */
    private const val ALREADY_CUTOUT_RATIO = 0.02f

    /** 一位像素的外框（左右上下都是闭区间的绝对坐标）。 */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val centerX: Float get() = (left + right + 1) / 2f
        val centerY: Float get() = (top + bottom + 1) / 2f
    }

    /** [containRect] 的结果：目标区域里的一块浮点矩形。 */
    data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    /**
     * 把 [content] 等比缩进 [target] 居中，返回落位矩形。
     *
     * 这是「让所有导入图长得一样大」的那一步：以官方车模的外框为目标，导入图只缩不放，
     * 比例不同时宁可小一圈也不拉伸变形。
     */
    fun containRect(content: Box, target: Box): FitRect {
        if (content.width <= 0 || content.height <= 0) {
            return FitRect(target.left.toFloat(), target.top.toFloat(), 0f, 0f)
        }
        val scale = minOf(
            target.width.toFloat() / content.width,
            target.height.toFloat() / content.height,
        )
        val w = content.width * scale
        val h = content.height * scale
        return FitRect(target.centerX - w / 2f, target.centerY - h / 2f, w, h)
    }

    /**
     * 把 [content] 摆进 [target]，并**保证落位整体落在画布内**。
     *
     * [containRect] 只管「缩进目标框」，不管画布边界 —— 目标框贴着画布边、或者内容比画布
     * 还大（极端宽高比）时，浮点结果会顶出去一两个像素，`Canvas.drawBitmap` 就直接把
     * 车身切在画布外，看着就像「车底/车尾被一刀切掉」。
     *
     * 这里多做两件事：内容比画布大就先等比再缩一次；最后把左上角夹进 [0, 画布-尺寸]。
     * 宁可四周多留一像素空白，也不能切内容。
     */
    fun placeRect(
        content: Box,
        target: Box,
        canvasWidth: Int,
        canvasHeight: Int,
    ): FitRect {
        val fit = containRect(content, target)
        if (fit.width <= 0f || fit.height <= 0f || canvasWidth <= 0 || canvasHeight <= 0) return fit
        val shrink = minOf(
            1f,
            if (fit.width > canvasWidth) canvasWidth / fit.width else 1f,
            if (fit.height > canvasHeight) canvasHeight / fit.height else 1f,
        )
        val w = fit.width * shrink
        val h = fit.height * shrink
        val cx = fit.left + fit.width / 2f
        val cy = fit.top + fit.height / 2f
        return FitRect(
            (cx - w / 2f).coerceIn(0f, (canvasWidth - w).coerceAtLeast(0f)),
            (cy - h / 2f).coerceIn(0f, (canvasHeight - h).coerceAtLeast(0f)),
            w,
            h,
        )
    }

    /** 一个可复用的抠图结果，UI 侧靠它决定要不要提示「没识别出背景」。 */
    data class Cutout(val pixels: IntArray, val removed: Boolean)

    /** alpha 低于 [alphaThreshold] 的像素占比。 */
    fun transparentRatio(pixels: IntArray, alphaThreshold: Int = ALPHA_EMPTY): Float {
        if (pixels.isEmpty()) return 0f
        var empty = 0
        for (p in pixels) {
            if (((p ushr 24) and 0xFF) <= alphaThreshold) empty++
        }
        return empty.toFloat() / pixels.size
    }

    /**
     * 自动抠背景：从四条边出发，把与「主背景色」接近的连通像素吃掉（alpha 置 0）。
     *
     * 只从边界向内扩，所以车身内部与背景同色的区域（玻璃、白色车漆）不会被误伤 —— 除非背景
     * 真的从边界连到那里。啃完还会把紧贴边缘的一圈「介于背景与车之间的过渡像素」按接近程度
     * 削成半透明，否则白底图会留一圈白毛边。
     *
     * 三种情况原图返回：本来就有透明底 / 边界判不出背景色 / 啃得太多或太少。
     *
     * 不修改入参，永远返回新数组（或同一个引用）。
     */
    fun removeBackground(
        pixels: IntArray,
        width: Int,
        height: Int,
        tolerance: Int = DEFAULT_TOLERANCE,
    ): Cutout {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return Cutout(pixels, false)
        // AI 抠好的透明底 PNG：直接用，重复抠只会把边缘啃毛
        if (transparentRatio(pixels) > ALREADY_CUTOUT_RATIO) return Cutout(pixels, false)

        val bg = borderColor(pixels, width, height) ?: return Cutout(pixels, false)
        val (br, bgG, bgB) = bg

        val out = pixels.copyOf()
        val n = width * height
        val marked = BooleanArray(n)
        val stack = IntArray(n)
        var top = 0

        fun isBackground(index: Int): Boolean {
            if (marked[index]) return false
            val p = pixels[index]
            val dr = abs(((p shr 16) and 0xFF) - br)
            val dg = abs(((p shr 8) and 0xFF) - bgG)
            val db = abs((p and 0xFF) - bgB)
            return maxOf(dr, dg, db) <= tolerance
        }

        fun seed(index: Int) {
            if (isBackground(index)) {
                marked[index] = true
                stack[top++] = index
            }
        }

        for (x in 0 until width) {
            seed(x)
            seed((height - 1) * width + x)
        }
        for (y in 0 until height) {
            seed(y * width)
            seed(y * width + width - 1)
        }
        if (top == 0) return Cutout(pixels, false)

        while (top > 0) {
            val index = stack[--top]
            val x = index % width
            val y = index / width
            if (x > 0) seed(index - 1)
            if (x < width - 1) seed(index + 1)
            if (y > 0) seed(index - width)
            if (y < height - 1) seed(index + width)
        }

        var removed = 0
        for (i in 0 until n) if (marked[i]) removed++
        val ratio = removed.toFloat() / n
        // 啃太多 = 连车身一起吃了；啃太少 = 图里根本没有纯色背景
        if (ratio < MIN_REMOVED_RATIO || ratio > MAX_REMOVED_RATIO) return Cutout(pixels, false)

        for (i in 0 until n) {
            if (marked[i]) out[i] = out[i] and 0x00FFFFFF // alpha 清零，保留 RGB 免得到处是全 0
        }

        // 软边：紧贴背景、颜色介于背景与车之间的像素按接近程度削 alpha，避免白毛边
        val edge = tolerance * 2.2f
        for (i in 0 until n) {
            if (marked[i]) continue
            val x = i % width
            val y = i / width
            val near = (x > 0 && marked[i - 1]) ||
                (x < width - 1 && marked[i + 1]) ||
                (y > 0 && marked[i - width]) ||
                (y < height - 1 && marked[i + width])
            if (!near) continue
            val p = out[i]
            val alpha = (p ushr 24) and 0xFF
            if (alpha == 0) continue
            val d = maxOf(
                abs(((p shr 16) and 0xFF) - br),
                abs(((p shr 8) and 0xFF) - bgG),
                abs((p and 0xFF) - bgB),
            ).toFloat()
            if (d >= edge) continue
            val k = ((d - tolerance) / (edge - tolerance)).coerceIn(0f, 1f)
            val scaled = (alpha * k).toInt().coerceIn(0, alpha)
            out[i] = (scaled shl 24) or (p and 0x00FFFFFF)
        }

        return Cutout(out, true)
    }

    /**
     * 量出「有内容」的包围盒（alpha 大于阈值）。
     *
     * 边界上零星几个噪点会让框白扩一圈，所以要求一行/一列至少有 [minRun] 个有效像素才算数。
     * 全透明返回 null。
     */
    fun contentBox(
        pixels: IntArray,
        width: Int,
        height: Int,
        alphaThreshold: Int = ALPHA_EMPTY,
        minRun: Int = 2,
    ): Box? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            var count = 0
            var first = -1
            var last = -1
            for (x in 0 until width) {
                if (((pixels[row + x] ushr 24) and 0xFF) <= alphaThreshold) continue
                count++
                if (first < 0) first = x
                last = x
            }
            if (count < minRun) continue
            if (first < left) left = first
            if (last > right) right = last
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (right < 0 || bottom < 0) return null
        return Box(left, top, right, bottom)
    }

    /**
     * 主背景色：四条边按 4×4×4 量化桶取众数，再回算该桶的平均色。
     *
     * 用众数而不是平均值：车头/车尾常常贴着画面边缘，平均值会被车身颜色拖偏。
     * 边界上已经有透明像素时返回 null（说明背景是透明的，不用抠）。
     */
    private fun borderColor(pixels: IntArray, width: Int, height: Int): Triple<Int, Int, Int>? {
        val buckets = IntArray(64)
        val sumR = IntArray(64)
        val sumG = IntArray(64)
        val sumB = IntArray(64)

        fun sample(index: Int) {
            val p = pixels[index]
            if (((p ushr 24) and 0xFF) <= ALPHA_EMPTY) return
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val key = ((r shr 6) shl 4) or ((g shr 6) shl 2) or (b shr 6)
            buckets[key]++
            sumR[key] += r
            sumG[key] += g
            sumB[key] += b
        }

        for (x in 0 until width) {
            sample(x)
            sample((height - 1) * width + x)
        }
        for (y in 0 until height) {
            sample(y * width)
            sample(y * width + width - 1)
        }

        var best = -1
        var bestCount = 0
        for (k in buckets.indices) {
            if (buckets[k] > bestCount) {
                bestCount = buckets[k]
                best = k
            }
        }
        if (best < 0 || bestCount <= 0) return null
        // 边界上一半以上是「透明」以外的杂色，说明背景不是纯色（照片场景），别硬抠
        val total = 2 * (width + height)
        if (bestCount.toFloat() / total < 0.45f) return null
        return Triple(sumR[best] / bestCount, sumG[best] / bestCount, sumB[best] / bestCount)
    }

    private fun abs(v: Int): Int = if (v < 0) -v else v
}
