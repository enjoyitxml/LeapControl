package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 用户自己上传的三张背景图：日间壁纸 / 夜间壁纸 / 相伴卡背景。
 *
 * 为什么单独一套存储而不塞进 ThemeScenes 那套：
 * 40 套内置主题全是**实时绘制**的矢量场景，一张图都不存；自定义壁纸是另一条路
 * （位图铺满整页），两者的加载方式、内存占用、失败兜底都不同，硬塞进 [SeasonTheme]
 * 会让「场景」这个概念变浑。这里只负责文件读写，渲染在 UI 层按需加载。
 *
 * 存盘前一律先【裁切 + 缩到目标尺寸】：相册里动辄 4000×3000 的原图，
 * 直接解码进内存是几十 MB 起步，页面上却只用到屏幕这么大一块。
 */
enum class CustomBackdropKind(
    /** 文件名：固定三个，日间与夜间各一张，互不覆盖。 */
    val fileName: String,
    /** 存盘后的最大边长（px）：够铺满 1080p 级别的屏幕，再大只是浪费内存。 */
    val maxSide: Int,
) {
    /** 首页日间壁纸：整页铺满，比例 = 屏幕比例。 */
    WALLPAPER_DAY("wallpaper_day.jpg", 1080),

    /** 首页夜间壁纸。 */
    WALLPAPER_NIGHT("wallpaper_night.jpg", 1080),

    /** 相伴卡背景：卡片是 3:1 的横条，比例完全不同的另一张图。 */
    OWNERSHIP("ownership_backdrop.jpg", 1080),
    ;

    val isWallpaper: Boolean get() = this != OWNERSHIP
}

object CustomBackdropStore {

    private const val DIR = "custom_backdrop"

    /** JPEG 质量：背景图不参与文字渲染，88 已经看不出压缩痕迹，体积却小一截。 */
    private const val QUALITY = 88

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    fun file(context: Context, kind: CustomBackdropKind): File =
        File(dir(context), kind.fileName)

    fun exists(context: Context, kind: CustomBackdropKind): Boolean =
        file(context, kind).let { it.isFile && it.length() > 0L }

    /**
     * 版本号：文件最后修改时间。
     *
     * UI 拿它当 `remember` 的键 —— 换了一张图，时间戳变化，页面就会重新解码；
     * 没换图时它不变，重组不会反复解码大图。
     */
    fun version(context: Context, kind: CustomBackdropKind): Long =
        file(context, kind).let { if (it.isFile) it.lastModified() else 0L }

    /** 读已保存的图；没有、读不出来都返回 null（调用方回落到内置主题/背景）。 */
    fun load(context: Context, kind: CustomBackdropKind): Bitmap? = runCatching {
        val f = file(context, kind)
        if (!f.isFile || f.length() <= 0L) return null
        // 存盘时已经缩到 maxSide 以内，这里按 1:1 解码就行，不需要再算采样率
        BitmapFactory.decodeFile(
            f.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
    }.getOrNull()

    /**
     * 存图：先写临时文件再改名 —— 中途被杀进程不会留下一张半截的坏图，
     * 那样下次进首页会解码失败、直接掉回默认主题，用户还以为自己白设了。
     */
    suspend fun save(
        context: Context,
        kind: CustomBackdropKind,
        bitmap: Bitmap,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = dir(context).apply { if (!isDirectory) mkdirs() }
            val target = File(targetDir, kind.fileName)
            val temp = File(targetDir, "${kind.fileName}.tmp")
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            if (target.exists()) target.delete()
            val ok = temp.renameTo(target)
            // 时间戳精度到秒：连续两次保存同一秒内可能相同，补一个固定偏移没有意义，
            // 这里直接靠文件内容变化 + 上层强制重组（见 AppRuntime.saveCustomBackdrop）
            ok
        }.getOrDefault(false)
    }

    fun clear(context: Context, kind: CustomBackdropKind) {
        runCatching { file(context, kind).delete() }
    }
}

/**
 * 从壁纸里挑一个「能当主题色」的颜色。
 *
 * 为什么要挑：自定义壁纸一旦生效，卡片描边、进度条、强调文字全都用主题色。
 * 固定给一个中性灰绿，配蓝天壁纸会脏、配沙漠壁纸会闷；从图里取主色，
 * 界面和壁纸就是一套的，这也是各家「自适应壁纸」的通行做法。
 *
 * 不引 androidx.palette：它只为这一处功能就多一个依赖，而算法本身并不复杂 ——
 * 这里做的是「按色相分桶 + 饱和度加权投票」的简化版，比 Palette 更可控：
 *  · 天空/白墙这类「面积大但很淡」的像素会被饱和度门槛滤掉，不会把主色带成灰蓝；
 *  · 取到色相后再把饱和度 / 明度**规整到可用区间**，保证无论壁纸多艳多暗，
 *    落到界面上的强调色都能看清（浅色模式 V≈0.55，深色模式 V≈0.74）。
 *
 * 纯函数、不碰 Bitmap，方便单测。
 */
object WallpaperPalette {

    /** 色相分桶数：每桶 30°，足够了 —— 再细则同一片天空会被拆成好几桶互相稀释。 */
    private const val HUE_BUCKETS = 12

    /**
     * @param pixels ARGB 像素数组（`Bitmap.getPixels` 的结果）
     * @return 0xAARRGGBB；图里没有可用颜色（纯灰、纯黑白）时返回 null，调用方用默认主题色
     */
    fun accentFrom(
        pixels: IntArray,
        width: Int,
        height: Int,
        dark: Boolean,
    ): Int? {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return null

        // 桶内累计：饱和度加权票数 / 色相圆周累计 / 饱和度累计 / 明度累计
        val weight = FloatArray(HUE_BUCKETS)
        val hueSin = FloatArray(HUE_BUCKETS)
        val hueCos = FloatArray(HUE_BUCKETS)
        val satSum = FloatArray(HUE_BUCKETS)
        val valSum = FloatArray(HUE_BUCKETS)

        // 逐像素太慢，按目标 64×64 左右的步长采样；壁纸的主色是「大面积色块」，
        // 抽样完全够，而且这个函数在保存后只跑一次
        val step = maxOf(1, minOf(width, height) / 64)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val c = pixels[y * width + x]
                val alpha = (c ushr 24) and 0xFF
                if (alpha >= 128) {
                    val hsv = rgbToHsv(
                        r = (c shr 16) and 0xFF,
                        g = (c shr 8) and 0xFF,
                        b = c and 0xFF,
                    )
                    val (h, s, v) = hsv
                    // 太暗（纯黑）、太亮（曝光过度/白墙）、太淡（灰）都不作为主色候选
                    if (s >= 0.12f && v in 0.16f..0.95f) {
                        val bucket = (((h / 360f) * HUE_BUCKETS).toInt()).coerceIn(0, HUE_BUCKETS - 1)
                        val w = s * v
                        weight[bucket] += w
                        val rad = Math.toRadians(h.toDouble())
                        hueSin[bucket] += (Math.sin(rad).toFloat()) * w
                        hueCos[bucket] += (Math.cos(rad).toFloat()) * w
                        satSum[bucket] += s * w
                        valSum[bucket] += v * w
                    }
                }
                x += step
            }
            y += step
        }

        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        val total = weight[best]
        // 有效像素太少（整张图都是灰/黑白）：不硬取，交给默认主题色
        if (total < 0.02f) return null

        // 色相做圆周平均：0° 与 350° 其实是同一类红，直接算术平均会得到 175°（青色）
        val h = Math.toDegrees(
            Math.atan2(hueSin[best].toDouble(), hueCos[best].toDouble()),
        ).toFloat().let { if (it < 0f) it + 360f else it }
        val s = satSum[best] / total
        val v = valSum[best] / total

        // 规整到「当强调色一定看得清」的区间
        val outS = if (dark) s.coerceIn(0.38f, 0.62f) else s.coerceIn(0.46f, 0.74f)
        val outV = if (dark) v.coerceIn(0.66f, 0.80f) else v.coerceIn(0.44f, 0.58f)
        return hsvToRgb(h, outS, outV)
    }

    /** RGB → (H 0..360, S 0..1, V 0..1)。不用 `Color.RGBToHSV` 是为了纯 JVM 可测。 */
    internal fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val h = when {
            delta < 1e-6f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max <= 1e-6f) 0f else delta / max
        return Triple(h, s, max)
    }

    /** (H 0..360, S 0..1, V 0..1) → 0xFFRRGGBB。 */
    internal fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
