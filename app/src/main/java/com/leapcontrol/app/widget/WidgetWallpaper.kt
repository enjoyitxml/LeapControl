package com.leapcontrol.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 自定义背景图的落盘与对外暴露（只给沉浸样式用）。
 *
 * ## 为什么交给 ContentProvider，而不是直接塞位图
 * `RemoteViews.setImageViewBitmap` 走 Binder，事务上限 1MB —— 连同车模位图一起，
 * 背景最多只能给到 400px 宽，铺到 800+px 的卡片上必然发虚。
 * `setImageViewUri` 只是把 URI 交给桌面进程，由它按原图分辨率解码，没有体积上限。
 *
 * ## 为什么文件名带版本号
 * 桌面进程对同一个 URI 有解码缓存，URI 不变就不会重新读图。所以每存一次换一个文件名。
 *
 * ## 安全
 * 提供者是 exported 的（桌面进程要能读），因此只按「当前版本号」精确匹配文件名，
 * 不接受调用方传路径，杜绝 ../ 这类穿越。
 */
object WidgetWallpaper {

    const val MIME = "image/jpeg"

    private const val DIR = "widget"
    private const val PREFIX = "wall_"
    private const val AUTHORITY_SUFFIX = ".widgetwall"

    /** 存盘宽度：比常见卡片（约 900px）宽，又不至于把设置页缩略图拖慢。 */
    private const val MAX_WIDTH = 1440

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    /** 当前版本对应的文件；没有就是 null。 */
    fun file(context: Context): File? {
        val f = File(dir(context), "$PREFIX${WidgetStyle.revision(context)}.jpg")
        return f.takeIf { it.isFile && it.length() > 0L }
    }

    fun hasFile(context: Context): Boolean = file(context) != null

    /** 渲染时是否要画用户那张图（选了自定义 + 文件还在）。 */
    fun exists(context: Context): Boolean =
        WidgetStyle.wallpaper(context) == WidgetStyle.WALLPAPER_CUSTOM && file(context) != null

    fun uri(context: Context): Uri =
        Uri.parse("content://${authority(context)}/wall/${WidgetStyle.revision(context)}")

    /**
     * 保存背景并切到「自定义」。
     *
     * 先写 .tmp 再改名：中途被杀不会留下半张坏图，也不会提前删掉旧图。
     */
    fun save(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val scaled = if (bitmap.width > MAX_WIDTH) {
            val ratio = MAX_WIDTH.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(
                bitmap,
                MAX_WIDTH,
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val rev = WidgetStyle.revision(context) + 1
        val target = File(dir(context), "$PREFIX$rev.jpg")
        val temp = File(dir(context), "$PREFIX$rev.tmp")
        FileOutputStream(temp).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 88, out) }
        if (!temp.renameTo(target)) return@runCatching false
        // 旧版本文件删掉：否则换十次图就会在私有目录里堆十张
        dir(context).listFiles()?.forEach { old ->
            if (old.name.startsWith(PREFIX) && old.name != target.name) runCatching { old.delete() }
        }
        WidgetStyle.bumpRevision(context)
        WidgetStyle.setWallpaper(context, WidgetStyle.WALLPAPER_CUSTOM)
        true
    }.getOrDefault(false)

    /** 解码成缩略图给设置页预览；按最长边 [maxSize] 采样，别把原图整个读进内存。 */
    fun preview(context: Context, maxSize: Int = 360): Bitmap? {
        val f = file(context) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                f.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()
    }

    /** 撤掉自定义图，回到默认渐变。 */
    fun clear(context: Context) {
        runCatching {
            dir(context).listFiles()?.forEach { if (it.name.startsWith(PREFIX)) it.delete() }
        }
        WidgetStyle.setWallpaper(context, WidgetStyle.WALLPAPER_DEFAULT)
        WidgetStyle.bumpRevision(context)
    }
}
