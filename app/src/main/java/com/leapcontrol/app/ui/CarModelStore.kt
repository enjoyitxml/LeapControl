package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
     * 三步：解码（按 2 的幂采样，避免直接 decode 一张大照片撑爆堆）→ 裁掉全透明边
     * （用户从 AI 拿回来的图四周常有一大片透明 / 白底留白，不裁的话车模会显示得很小）
     * → 存 PNG。
     *
     * 返回存好的 bitmap，失败返回 null。
     */
    suspend fun import(context: Context, vin: String, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
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
                val trimmed = trimTransparent(scaled)
                // 这里不主动 recycle：解码出来的中间图马上就没引用，交给 GC 处理。
                // Android 10 起 Bitmap 像素在 Java 堆上，GC 能回收；手动 recycle 反而容易
                // 把「返回给 UI 继续显示的那张」也带走。
                if (save(context, vin, trimmed)) trimmed else null
            }.getOrNull()
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

        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val a = (pixels[row + x] ushr 24) and 0xFF
                if (a > ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        // 整张透明（用户选错图）或本来就铺满：都不裁
        if (right < 0) return bitmap
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return bitmap

        val l = (left - padding).coerceAtLeast(0)
        val t = (top - padding).coerceAtLeast(0)
        val r = (right + padding).coerceAtMost(w - 1)
        val b = (bottom + padding).coerceAtMost(h - 1)
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
