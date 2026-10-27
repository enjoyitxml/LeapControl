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
 * 自定义头像的本地存取。
 *
 * 只做三件事：把相册里选的图**居中裁成正方形**、缩到 [MAX_SIDE] 以内、存成文件。
 * 裁成正方形放在这里而不是交给 Compose 的 `ContentScale.Crop`，
 * 是因为不同相册给的图长宽比差很多，先裁好能让内存占用和显示结果都可预期。
 */
object AvatarStore {

    private const val FILE_NAME = "profile_avatar.jpg"

    /** 存图边长上限。头像最大只显示到 ~200dp（选择器里的大图），512 够用且只占几十 KB。 */
    const val MAX_SIDE = 512

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).let { it.isFile && it.length() > 0L }

    /**
     * 计算解码采样率：把长边压到 [target] 附近，避免直接 decode 一张 8000px 的照片把堆撑爆。
     * 采样只取 2 的幂（BitmapFactory 的要求），所以结果会略小于目标。
     */
    fun sampleSizeFor(width: Int, height: Int, target: Int = MAX_SIDE): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    /** 把相册 Uri 导入成自定义头像。成功返回 true；读不出来（权限撤销、图损坏）返回 false。 */
    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val rawWidth = bounds.outWidth
            val rawHeight = bounds.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return@runCatching false

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(rawWidth, rawHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching false

            val side = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(
                bitmap,
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                side,
                side,
            )
            val scaled = if (side > MAX_SIDE) {
                Bitmap.createScaledBitmap(square, MAX_SIDE, MAX_SIDE, true)
            } else {
                square
            }

            // 先写临时文件再 rename：中途失败不会留下半张坏图
            val target = file(context)
            val temp = File(target.parentFile, "$FILE_NAME.tmp")
            FileOutputStream(temp).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (target.exists()) target.delete()
            val ok = temp.renameTo(target)
            if (scaled !== square) scaled.recycle()
            if (square !== bitmap) square.recycle()
            bitmap.recycle()
            ok
        }.getOrDefault(false)
    }

    /** 读取已保存的自定义头像；没有或坏掉都返回 null，调用方回落到内置头像。 */
    fun load(context: Context): Bitmap? = runCatching {
        val f = file(context)
        if (!f.isFile) return null
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
