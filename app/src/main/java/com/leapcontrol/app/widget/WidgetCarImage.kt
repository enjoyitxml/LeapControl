package com.leapcontrol.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * 小组件自己的车模图磁盘缓存。
 *
 * ## 为什么不能只靠内存
 * 小组件经常在「一次独立唤醒」的进程里重画：桌面定时更新、点一下按键、拖动改卡片尺寸，
 * 都会拉起一个刚启动的进程，[com.leapcontrol.app.ui.AppRuntime] 里那些位图缓存全是空的。
 * 而 [VehicleWidget.render] 是同步的 —— 拿不到图就只能把 ImageView 设成 GONE，
 * 桌面上那辆车就「消失」了（用户报的正是这个：续航、车况都在，右边车模那块空着）。
 *
 * 所以拿到图的第一时间就在这里落一份，之后冷进程第一帧直接读文件贴回去：
 * 既不联网，也不依赖 AppRuntime 的缓存还热着。
 *
 * ## 文件约定
 * - 按 VIN 分文件：换车天然换图，不需要额外的失效逻辑；
 * - 退出登录 / 换账号时整目录清掉（[clearAll]），别把上一辆车的图留在锁屏外；
 * - 车模换色、相册导入、恢复官方之后由 [clear] 删掉这一辆车的缓存图。
 *
 * 与 `CarModelStore` 的区别：那份是「用户的车模资产」（自定义图 / 原始导入图，1600px），
 * 只给 App 内首页用；这里只是给桌面卡片贴图用的缩略副本，随时可以重建。
 */
object WidgetCarImage {

    private const val DIR = "widget/car"

    /** 与 [VehicleWidget] 缩图目标一致：440px 宽的 PNG 贴 4×2 卡片足够清晰。 */
    private const val MAX_WIDTH = 440

    fun file(context: Context, vin: String): File =
        File(File(context.filesDir, DIR), "car_${vin.ifBlank { "default" }}.png")

    fun exists(context: Context, vin: String): Boolean =
        file(context, vin).let { it.isFile && it.length() > 0L }

    /** 读缓存。文件不在 / 解不出来一律返回 null，调用方按「没图」处理。 */
    @Synchronized
    fun load(context: Context, vin: String): Bitmap? = runCatching {
        val target = file(context, vin)
        if (!target.isFile || target.length() <= 0L) return null
        // 先量宽度再降采样：这张图正常情况下就是 440 宽，但别指望文件一定没被替换过
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(target.absolutePath, options)
    }.getOrNull()

    /** 存缓存。先写 `.tmp` 再 rename，中途被杀不会留下半张坏图。 */
    @Synchronized
    fun save(context: Context, vin: String, bitmap: Bitmap): Boolean = runCatching {
        val target = file(context, vin)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }.getOrDefault(false)

    /** 只删这一辆车的缓存（换色 / 导入 / 恢复官方之后用）。 */
    @Synchronized
    fun clear(context: Context, vin: String) {
        runCatching { file(context, vin).delete() }
    }

    /** 整个目录清掉：退出登录、换账号。 */
    @Synchronized
    fun clearAll(context: Context) {
        runCatching { File(context.filesDir, DIR).listFiles()?.forEach { it.delete() } }
    }

    private fun sampleSize(width: Int): Int {
        if (width <= 0 || width <= MAX_WIDTH) return 1
        var sample = 1
        while (width / (sample * 2) >= MAX_WIDTH) sample *= 2
        return sample
    }
}
