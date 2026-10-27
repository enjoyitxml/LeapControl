package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 自定义车模图的本地存取。
 *
 * 官方车模图按 VIN 缓存在 `car_model_<vin>.img`；用户改过颜色的图放这里，
 * 文件名带 VIN —— 一个账号下多辆车各自一套，换车不会串。
 *
 * 与官方缓存的关系是「覆盖而不是替换」：官方图永远在，删掉自定义图就自动回到官方，
 * 所以「刷新车模图片」只动官方缓存，不会把用户辛苦改的图一起清掉。
 */
object CarModelStore {

    /** 存图长边上限：首页车模只显示到 ~200dp，1600 已经远超 3x 屏幕密度所需。 */
    const val MAX_SIDE = 1600

    /** 透明阈值：低于它的算背景，裁边时不参与取景。 */
    private const val ALPHA_THRESHOLD = 8

    /** 一次导入的处理结果，UI 靠它决定提示文案。 */
    data class Imported(
        val bitmap: Bitmap,
        /** 抠掉了背景（用户给的是白底 / 纯色底图）。 */
        val backgroundRemoved: Boolean,
        /** 已按官方车模的比例和留白重新摆位。 */
        val aligned: Boolean,
    )

    fun file(context: Context, vin: String): File =
        File(context.filesDir, "car_custom_${vin.ifBlank { "default" }}.png")

    fun exists(context: Context, vin: String): Boolean =
        file(context, vin).let { it.isFile && it.length() > 0L }

    /** 读取自定义车模；没有或损坏都返回 null，调用方回落到官方图。 */
    fun load(context: Context, vin: String): Bitmap? = runCatching {
        val f = file(context, vin)
        if (!f.isFile) return null
        BitmapFactory.decodeFile(
            f.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
    }.getOrNull()

    /** 保存自定义车模。先写 .tmp 再 rename，中途失败不会留下半张坏图。 */
    fun save(context: Context, vin: String, bitmap: Bitmap): Boolean = runCatching {
        val target = file(context, vin)
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { out ->
            // 必须 PNG：车模是透明底，JPEG 会把背景压成白块
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (target.exists()) target.delete()
        temp.renameTo(target)
    }.getOrDefault(false)

    fun clear(context: Context, vin: String) {
        runCatching { file(context, vin).delete() }
    }

    /**
     * 从相册导入一张图作为自定义车模。
     *
     * 顺序：解码（按 2 的幂采样，避免 decode 一张大照片撑爆堆）→ 自动抠背景
     * （白底 / 纯色底才动手，本来就透明底的图跳过）→ 裁掉全透明边 → **按官方图的
     * 车模外框重新摆位**（见 [alignToTemplate]）→ 存 PNG。
     *
     * 最后一步是「不管上传什么比例的图，观感都跟官方一致」的关键：
     * 少了它，紧裁的图会因为贴着边缘而比官方车大一圈（实测 1.33 倍），首页看着就不协调。
     *
     * 返回处理结果，失败返回 null。
     */
    suspend fun import(
        context: Context,
        vin: String,
        uri: Uri,
        template: Bitmap? = null,
    ): Imported? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val rawWidth = bounds.outWidth
            val rawHeight = bounds.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(rawWidth, rawHeight, MAX_SIDE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            val scaled = scaleDown(decoded, MAX_SIDE)
            val cut = cutout(scaled)
            val trimmed = trimTransparent(cut.second)
            val aligned = template?.let { alignToTemplate(trimmed, it) }
            val result = aligned ?: trimmed
            if (!save(context, vin, result)) return@runCatching null
            // 这里不主动 recycle：解码出来的中间图马上就没引用，交给 GC 处理。
            // Android 10 起 Bitmap 像素在 Java 堆上，GC 能回收；手动 recycle 反而容易
            // 把「返回给 UI 继续显示的那张」也带走。
            Imported(result, backgroundRemoved = cut.first, aligned = aligned != null)
        }.getOrNull()
    }

    /**
     * 把官方车模图当模板，把任意比例的导入图重新摆位。
     *
     * 官方图是「车模居中 + 一圈留白」的画布，用户那张是紧裁图，直接 Fit 显示必然一大一小。
     * 这里量出官方图里车模的真实外框，再把导入图的车模等比缩进这个外框、居中摆好，
     * 输出一张与官方图同尺寸（同留白）的画布 —— 首页显示出来的大小、位置就完全对得上了。
     *
     * 用 contain 而不是拉伸：比例差得多的图宁可小一圈，也不能把车拉变形。
     */
    fun alignToTemplate(content: Bitmap, template: Bitmap): Bitmap? = runCatching {
        val cw = content.width
        val ch = content.height
        if (cw <= 0 || ch <= 0) return null
        val cp = IntArray(cw * ch)
        content.getPixels(cp, 0, cw, 0, 0, cw, ch)
        val cBox = CarCutout.contentBox(cp, cw, ch, ALPHA_THRESHOLD) ?: return null

        val base = scaleDown(template, MAX_SIDE)
        val tw = base.width
        val th = base.height
        if (tw <= 0 || th <= 0) return null
        val tp = IntArray(tw * th)
        base.getPixels(tp, 0, tw, 0, 0, tw, th)
        val tBox = CarCutout.contentBox(tp, tw, th, ALPHA_THRESHOLD) ?: return null

        val fit = CarCutout.containRect(cBox, tBox)
        val dw = fit.width.roundToInt().coerceAtLeast(1)
        val dh = fit.height.roundToInt().coerceAtLeast(1)
        if (!fit.width.isFinite() || !fit.height.isFinite() || dw <= 0 || dh <= 0) return null

        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            isDither = true
        }
        // 先精确缩到目标像素尺寸再贴，比一次性 drawBitmap 缩放的边缘更干净
        val src = Rect(cBox.left, cBox.top, cBox.right + 1, cBox.bottom + 1)
        val body = if (dw == cBox.width && dh == cBox.height) {
            content
        } else {
            Bitmap.createScaledBitmap(content.subsetOrSelf(src), dw, dh, true)
        }
        canvas.drawBitmap(body, fit.left, fit.top, paint)
        out
    }.getOrNull()

    /**
     * 用官方图给一张已有的自定义图「补摆位」，老版本导入的图升级后就地修正。
     *
     * 判据是「车模占画布宽度的比例」而不是整图宽高比：换色生成的图与官方图逐像素同构
     * （占比完全相同 → 直接返回，不重采样掉画质），而老版本导入的紧裁图占比接近 100%
     * （官方是 76%）→ 重排。重排后再算一次占比就对上了，所以这步是幂等的。
     */
    fun normalizeToTemplate(custom: Bitmap, template: Bitmap): Bitmap? {
        val customBox = contentBoxOf(custom) ?: return custom
        val base = scaleDown(template, MAX_SIDE)
        val templateBox = contentBoxOf(base) ?: return custom
        if (custom.width <= 0 || base.width <= 0) return custom

        val customShare = customBox.width.toFloat() / custom.width
        val templateShare = templateBox.width.toFloat() / base.width
        if (templateShare <= 0f) return custom
        if (kotlin.math.abs(customShare - templateShare) / templateShare < 0.03f) return custom
        return alignToTemplate(custom, base) ?: custom
    }

    /** 一张 bitmap 里车模内容的外框。 */
    private fun contentBoxOf(bitmap: Bitmap): CarCutout.Box? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return CarCutout.contentBox(pixels, w, h, ALPHA_THRESHOLD)
    }

    private fun Bitmap.subsetOrSelf(rect: Rect): Bitmap {
        val full = rect.left == 0 && rect.top == 0 && rect.right == width && rect.bottom == height
        return if (full) this else Bitmap.createBitmap(this, rect.left, rect.top, rect.width(), rect.height())
    }

    /** 抠背景；返回「是否真的抠了」和结果图。本来透明底的图原样返回。 */
    private fun cutout(bitmap: Bitmap): Pair<Boolean, Bitmap> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return false to bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = CarCutout.removeBackground(pixels, w, h)
        if (!result.removed) return false to bitmap
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result.pixels, 0, w, 0, 0, w, h)
        return true to out
    }

    /**
     * 裁掉四周的全透明像素（留 [padding] 像素余量，免得边缘被切掉）。
     *
     * 全图都有内容时原样返回同一个对象，不做无谓的拷贝。
     */
    fun trimTransparent(bitmap: Bitmap, padding: Int = 2): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val box = CarCutout.contentBox(pixels, w, h, ALPHA_THRESHOLD) ?: return bitmap
        // 本来就铺满（或只差 1px）：不裁
        if (box.left <= padding && box.top <= padding &&
            box.right >= w - 1 - padding && box.bottom >= h - 1 - padding
        ) {
            return bitmap
        }

        val l = (box.left - padding).coerceAtLeast(0)
        val t = (box.top - padding).coerceAtLeast(0)
        val r = (box.right + padding).coerceAtMost(w - 1)
        val b = (box.bottom + padding).coerceAtMost(h - 1)
        return Bitmap.createBitmap(bitmap, l, t, r - l + 1, b - t + 1)
    }

    /** 等比缩到长边不超过 [maxSide]；不需要缩时返回原对象。 */
    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxSide || longSide <= 0) return bitmap
        val ratio = maxSide.toFloat() / longSide
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** 计算解码采样率，只取 2 的幂（BitmapFactory 的硬要求）。 */
    fun sampleSizeFor(width: Int, height: Int, target: Int = MAX_SIDE): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    /**
     * 给整张图换色，返回**新**的 bitmap（不动传入的那张，预览和原图互不影响）。
     */
    fun tint(bitmap: Bitmap, targetArgb: Int, strength: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        CarTint.tintPixels(pixels, targetArgb, strength)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
