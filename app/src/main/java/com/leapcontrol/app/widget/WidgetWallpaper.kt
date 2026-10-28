package com.leapcontrol.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 自定义壁纸的落盘与对外暴露。
 *
 * ## 为什么要把图交给 ContentProvider，而不是直接塞位图
 * `RemoteViews.setImageViewBitmap` 走 Binder，事务上限 1MB —— 连同车模位图一起，
 * 背景最多只能给到 400px 宽，铺到 800+px 的卡片上必然发虚（上一版用户就是抱怨这个）。
 * `setImageViewUri` 只是把 URI 交给桌面进程，由它自己按原始分辨率解码，**没有体积上限**。
 *
 * ## 为什么文件名叫 wall_<rev>.jpg
 * 桌面进程里的 ImageView 对同一个 URI 有缓存，URI 不变就**不会重新解码** ——
 * 换图后卡片还停在旧图上。所以每存一次就换一个文件名，URI 里带上版本号，缓存自然失效。
 *
 * ## 安全
 * 提供者是 `exported=true`（桌面进程要能读），所以只按「当前版本号」精确匹配文件名，
 * 不接受调用方传任意路径，杜绝 ../ 之类的路径穿越。
 */
object WidgetWallpaper {

    const val MIME = "image/jpeg"

    private const val DIR = "widget"
    private const val PREFIX = "wall_"
    private const val AUTHORITY_SUFFIX = ".widgetwall"

    /** 存盘宽度。比任何常见卡片（~900px）都宽，又不会把设置页缩略图拖慢。 */
    private const val MAX_WIDTH = 1440

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    /** 当前版本对应的文件；没有就是 null。 */
    fun file(context: Context): File? {
        val f = File(dir(context), "$PREFIX${WidgetStyle.revision(context)}.jpg")
        return f.takeIf { it.isFile && it.length() > 0L }
    }

    /** 有没有存着的自定义图（不管当前是不是在用）。设置页的缩略图按这个显示。 */
    fun hasFile(context: Context): Boolean = file(context) != null

    /** 当前是不是「正在用自定义壁纸」——渲染时按这个决定要不要画背景图。 */
    fun exists(context: Context): Boolean =
        WidgetStyle.wallpaper(context) == WidgetStyle.WALLPAPER_CUSTOM && file(context) != null

    /** 给 RemoteViews 用的 URI（带版本号，换图即失效）。 */
    fun uri(context: Context): Uri =
        Uri.parse("content://${authority(context)}/wall/${WidgetStyle.revision(context)}")

    /**
     * 保存裁剪结果并切到「自定义壁纸」。
     *
     * 先写 .tmp 再改名：中途被杀不会留下半张坏图，也不会把旧图提前删掉。
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
        FileOutputStream(temp).use { out ->
            // JPEG 没有透明通道，卡片圆角由运行时裁剪（clipToOutline）负责，图本身存成直角
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) return@runCatching false
        // 旧版本图没人再引用了，顺手清掉（远端可能还拿着旧 URI，读不到就走纯色卡）
        dir(context).listFiles()
            ?.filter { it.name.startsWith(PREFIX) && it.name != target.name }
            ?.forEach { runCatching { it.delete() } }
        WidgetStyle.bumpRevision(context)
        WidgetStyle.setWallpaper(context, WidgetStyle.WALLPAPER_CUSTOM)
        true
    }.getOrDefault(false)

    /** 恢复「简约纯色卡」：只切模式，文件留着，用户下次还能切回来。 */
    fun useDefault(context: Context) {
        WidgetStyle.setWallpaper(context, WidgetStyle.WALLPAPER_DEFAULT)
    }

    /** 删除自定义图（换车 / 清空配置时用）。 */
    fun clear(context: Context) {
        dir(context).listFiles()
            ?.filter { it.name.startsWith(PREFIX) }
            ?.forEach { runCatching { it.delete() } }
        WidgetStyle.setWallpaper(context, WidgetStyle.WALLPAPER_DEFAULT)
    }

    /**
     * 读当前壁纸的小图，给个性化页做缩略图。
     *
     * [maxWidth] 单位是像素：缩略图只有一两百 dp，按原图解码既慢又白占内存。
     */
    fun loadBitmap(context: Context, maxWidth: Int): Bitmap? {
        val f = file(context) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(
                f.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }.getOrNull()
    }
}
