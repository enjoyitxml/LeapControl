package com.leapcontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.Locale

/**
 * 停车位照片：拍一张，找车时能认出「我停的是哪一格」。
 *
 * ## 为什么需要它
 * 位置能告诉你车在地图的哪个点，但到了地库就不管用了 —— 负一层还是负二层、
 * B 区还是 C 区、柱子边上是 12 号还是 13 号，只有当时的照片记得住。
 * 这是地库找车时唯一真正有效的信息，比坐标有用。
 *
 * ## 存法
 * `filesDir/parking/space.jpg` 只保留**一张**，重拍即覆盖旧照。
 * 不存相册、不存多张：这是「这一趟停的车位」的临时备忘，不是相册功能；
 * 而且照片里常拍到车牌，越少留存越好（退出登录一并清掉，见 [clear]）。
 *
 * 拍照走系统相机（`ACTION_IMAGE_CAPTURE`），由调用方通过 [captureFile] 给出输出路径，
 * 回来后再 [commit]。中间用 `capture.jpg` 当临时文件：
 * 相机写失败或被用户取消时旧照片仍在，不会出现「照片空了」。
 */
object ParkingPhotoStore {
    private const val DIR = "parking"

    /** 已确认的停车位照片。 */
    private const val PHOTO = "space.jpg"

    /** 相机正在写的临时文件；成功后转正、失败即删。 */
    private const val CAPTURE = "capture.jpg"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun photoFile(context: Context) = File(dir(context), PHOTO)

    /** 交给相机的输出文件。每次拍照前先清掉上一次的残留。 */
    fun captureFile(context: Context): File {
        val file = File(dir(context), CAPTURE)
        runCatching { if (file.exists()) file.delete() }
        return file
    }

    fun hasPhoto(context: Context): Boolean = photoFile(context).length() > 0L

    /** 拍摄时间（毫秒）；没有照片返回 0。 */
    fun savedAt(context: Context): Long =
        photoFile(context).takeIf { it.length() > 0L }?.lastModified() ?: 0L

    /**
     * 相机写完了：把临时文件转正。
     *
     * 先 rename 再校验 —— rename 是原子的，不会出现「旧照片删掉了、新照片还没写完」的空档。
     * 少数 ROM 的相机不按给的路径写（写进了自己的缓存），此时 [captureFile] 长度是 0，
     * 直接判失败，让用户重拍，而不是留一张 0 字节的破图。
     */
    fun commit(context: Context, takenAt: Long = System.currentTimeMillis()): Boolean = runCatching {
        val capture = File(dir(context), CAPTURE)
        if (capture.length() <= 0L) return false
        val target = photoFile(context)
        if (!capture.renameTo(target)) {
            target.delete()
            if (!capture.renameTo(target)) return false
        }
        // rename 会保留临时文件的 mtime（就是刚拍的），但有些文件系统会在失败分支里
        // 走 delete + rename，这里统一写一次，保证「拍摄于 x 前」总是从这一刻算起
        target.setLastModified(takenAt)
        target.length() > 0L
    }.getOrDefault(false)

    /** 取消 / 失败：清掉临时文件，旧照片不动。 */
    fun discard(context: Context) {
        runCatching { File(dir(context), CAPTURE).delete() }
    }

    /** 读图，长边压到 [maxSize] 以内（缩略图 480、找车页 1400，都够看）。 */
    fun load(context: Context, maxSize: Int): Bitmap? = runCatching {
        val file = photoFile(context)
        if (file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest > 0 && longest / sample > maxSize) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    fun delete(context: Context) {
        runCatching { photoFile(context).delete() }
    }

    /** 退出登录 / 换车时清干净：照片里很可能拍到车牌和车位号，不该留给下一个人。 */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }
}

/**
 * 停车位照片的文案（纯逻辑，可单测）。
 *
 * 两处用同一份说法：首页卡副标题后面挂的「已拍车位」后缀、找车页照片卡的说明行。
 */
object ParkingPhotoRules {
    /**
     * 「车位已拍照 · 12 分钟前」。
     *
     * 没有照片返回 null（调用方据此不画这一行），而不是返回空串 ——
     * 空串在 UI 上会占掉一行高度，等于留了个白洞。
     *
     * [savedAt] 为 0（文件时间读不到）时只说「车位已拍照」，不编一个假时间出来。
     */
    fun caption(hasPhoto: Boolean, savedAt: Long, now: Long): String? {
        if (!hasPhoto) return null
        val ageMs = if (savedAt > 0L && savedAt <= now) now - savedAt else null
        val age = VehicleLocationRules.ageText(ageMs) ?: return "车位已拍照"
        return String.format(Locale.CHINA, "车位已拍照 · %s", age)
    }
}
