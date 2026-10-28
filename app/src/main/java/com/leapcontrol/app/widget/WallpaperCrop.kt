package com.leapcontrol.app.widget

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 自定义壁纸的裁剪几何（纯函数，可单测）。
 *
 * 坐标系约定：
 * - 裁剪框在屏幕上的大小是 `boxW × boxH`（px），比例固定 [ASPECT]；
 * - 图片以 `totalScale` 缩放后，**中心**落在框中心 + ([offsetX], [offsetY]) 处；
 * - `totalScale` 至少是「铺满」所需的 [coverScale]，再也缩不下去 —— 否则框里会露出底色。
 *
 * 抽出来的原因很实际：这套「缩放到盖住 + 位移夹边 + 反算原图矩形」的换算一旦差一点，
 * 结果就是裁出来的图和预览框里看到的不是同一块（用户拖到车头，保存出来的却是车尾）。
 * 所以夹边与反算都放这里，由 [WallpaperCropTest] 钉住。
 */
object WallpaperCrop {

    /** 裁剪框宽高比。与小组件卡片（4 列 2 行约 250~330 × 110~150dp）接近。 */
    const val ASPECT = 2.2f

    /** 导出宽度上限。够 3x 屏铺满，又不至于让文件大到拖慢桌面解码。 */
    const val OUT_WIDTH = 1440

    /** 原图里的一块（像素，左上角 + 宽高）。 */
    data class SourceRect(val left: Int, val top: Int, val width: Int, val height: Int)

    /** 铺满裁剪框所需的基准缩放（取「盖住」而不是「装下」）。 */
    fun coverScale(imageW: Float, imageH: Float, boxW: Float, boxH: Float): Float {
        if (imageW <= 0f || imageH <= 0f || boxW <= 0f || boxH <= 0f) return 1f
        return max(boxW / imageW, boxH / imageH)
    }

    /**
     * 把位移夹进合法范围：图片任何一条边都不能进到框里。
     *
     * 返回新的 (offsetX, offsetY)。缩放后图比框还小的那一维直接归零（正常不会发生，
     * 因为缩放下限就是 coverScale，这里只是不让 NaN / 负范围传到 Canvas）。
     */
    fun clampOffset(
        imageW: Float,
        imageH: Float,
        boxW: Float,
        boxH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Pair<Float, Float> {
        val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
        val maxX = max(0f, (imageW * safeScale - boxW) / 2f)
        val maxY = max(0f, (imageH * safeScale - boxH) / 2f)
        val x = if (offsetX.isFinite()) offsetX.coerceIn(-maxX, maxX) else 0f
        val y = if (offsetY.isFinite()) offsetY.coerceIn(-maxY, maxY) else 0f
        return x to y
    }

    /**
     * 框里看到的那块内容，对应原图的哪一块。
     *
     * 结果一定落在图片范围内（四边都夹过），宽高至少 1px —— `Bitmap.createBitmap`
     * 拿到越界或 0 尺寸会直接抛异常，而这时用户已经点过「确定」了，崩了很突兀。
     */
    fun sourceRect(
        imageW: Float,
        imageH: Float,
        boxW: Float,
        boxH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): SourceRect {
        val iw = imageW.toInt().coerceAtLeast(1)
        val ih = imageH.toInt().coerceAtLeast(1)
        val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
        val (ox, oy) = clampOffset(imageW, imageH, boxW, boxH, safeScale, offsetX, offsetY)

        // 图片左上角在框坐标系里的位置（可能是负数，表示超出框的左边）
        val imgLeft = boxW / 2f + ox - imageW * safeScale / 2f
        val imgTop = boxH / 2f + oy - imageH * safeScale / 2f

        val sw = (boxW / safeScale).roundToInt().coerceIn(1, iw)
        val sh = (boxH / safeScale).roundToInt().coerceIn(1, ih)
        val left = (-imgLeft / safeScale).roundToInt().coerceIn(0, iw - sw)
        val top = (-imgTop / safeScale).roundToInt().coerceIn(0, ih - sh)
        return SourceRect(left, top, sw, sh)
    }

    /** 导出尺寸：宽度取 [OUT_WIDTH]，高度按框比例。 */
    fun outputHeight(width: Int): Int = (width / ASPECT).roundToInt().coerceAtLeast(1)
}
