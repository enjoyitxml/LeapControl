package com.leapcontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 停车位照片从哪儿来。
 *
 * 零控原来只有一种：**下车时用手机拍一张**（[PHONE]）。官方 App 还有一种更省事的：
 * 车辆驻车后，环视摄像头自动拍一张俯视图传到云端，随时能翻出来看车位号
 * （[OFFICIAL]，接口见 [LeapmotorApi.queryParkingPhoto]）。
 *
 * ## 为什么不直接换掉手机拍照
 * 车端这张照片**不是全系都有**：没有环视摄像头的车型、没在车机里开「驻车拍照」的车、
 * 或者刚停下车还没上传，都会取不到。手机拍照是任何车、任何地库都成立的兜底方案，
 * 所以两种方式并存，由用户自己选，默认仍是手机拍照（老用户行为不变）。
 */
enum class ParkingPhotoSource {
    /** 下车用手机拍（一直以来的做法）。 */
    PHONE,

    /** 车辆环视摄像头自动上传的那张（部分车型支持）。 */
    OFFICIAL;

    companion object {
        /**
         * 从 prefs 里的字符串还原。
         *
         * 未知值 / 空值一律退回 [PHONE]：这是**老用户**的默认，也是任何情况下都能用的那条路，
         * 读到一个不认识的字符串时退回它，用户至少还能拍照，而不是面对一个空白的拍照页。
         */
        fun fromId(id: String?): ParkingPhotoSource =
            if (id == OFFICIAL.name) OFFICIAL else PHONE
    }
}

/**
 * 驻车拍照方案的文案与常量（纯逻辑，可单测）。
 *
 * 三处共用同一份说法：设置页的选择弹层、驻车拍照页的来源切换、以及取不到照片时的说明。
 * 分开写最容易出现「设置里说支持、页面里说没取到」这种自相矛盾。
 */
object ParkingPhotoSourceRules {
    /** prefs 键（写在 `leapcontrol_parking_photo` 里）。 */
    const val PREF_KEY = "parking_photo_source"

    fun label(source: ParkingPhotoSource): String = when (source) {
        ParkingPhotoSource.PHONE -> "手机拍照"
        ParkingPhotoSource.OFFICIAL -> "车端照片"
    }

    /** 选择弹层里的两行说明。 */
    fun description(source: ParkingPhotoSource): String = when (source) {
        ParkingPhotoSource.PHONE ->
            "停车后用手机拍一张，存本机，任何车型都能用"
        ParkingPhotoSource.OFFICIAL ->
            "车辆环视摄像头驻车后自动上传的俯视图，能直接看到车位号"
    }

    /**
     * 「部分车型不支持」这句必须说清楚，而且要用用户能判断的措辞。
     *
     * 不写「低配不支持」这种似懂非懂的话，只说**必要条件**：带环视摄像头 + 车机里开了
     * 驻车拍照。用户对不上号时，下面还给了兜底动作（改用手机拍照），不至于卡死。
     */
    const val SUPPORT_NOTE =
        "车端照片由车辆环视摄像头在驻车后自动上传，只有部分车型支持" +
            "（需要带环视摄像头，并在车机里开启驻车拍照）。取不到时请改用手机拍照。"

    /** 取不到车端照片时的说明（标题 + 一句解释），按失败类型区分。 */
    fun emptyTitle(source: ParkingPhotoSource): String = when (source) {
        ParkingPhotoSource.PHONE -> "还没有车位照片"
        ParkingPhotoSource.OFFICIAL -> "还没取到车端照片"
    }

    fun emptyHint(source: ParkingPhotoSource): String = when (source) {
        ParkingPhotoSource.PHONE -> "停车后随手拍一张，回来一眼认出是哪一格"
        ParkingPhotoSource.OFFICIAL ->
            "可能是这辆车不支持该功能，也可能刚停下车还没上传。可以重试，或改用手机拍照。"
    }

    /**
     * 获取进度的一句话说明；不需要说明时返回 null（已经取到了 / 还没开始）。
     *
     * 三类失败**说的话不一样**，这是这项功能最容易被误解的地方：
     * 「还没上传」值得等一下再重试，「不支持」重试到天荒地老也没用，
     * 「网络异常」则是过一会儿就好。用同一句「获取失败」会逼用户自己猜。
     */
    fun statusText(status: OfficialPhotoStatus): String? = when (status) {
        OfficialPhotoStatus.Idle -> null
        OfficialPhotoStatus.Loading -> "正在向车辆获取最新照片…"
        is OfficialPhotoStatus.Ready -> null
        OfficialPhotoStatus.Empty -> "车辆还没有上传这一趟的驻车照片"
        is OfficialPhotoStatus.Unsupported -> "这台车不支持车端驻车照片，请用手机拍照"
        is OfficialPhotoStatus.Failed -> "没取到车端照片，检查网络后再试一次"
    }
}

/**
 * 车端照片的获取进度（页面与卡片都订阅这一个状态）。
 *
 * 和「本地有没有照片」分开：本地有图时直接显示，这个状态只负责说明「正在取 / 取不到 / 取到了」——
 * 混在一起写会让「刷新失败但旧图还在」变成一次白屏。
 */
sealed interface OfficialPhotoStatus {
    data object Idle : OfficialPhotoStatus

    data object Loading : OfficialPhotoStatus

    /** 取到了并存盘。`revision` 每成功一次 +1，UI 据此重新读盘。 */
    data class Ready(val uploadTimeMs: Long, val revision: Int) : OfficialPhotoStatus

    /** 接口通了，但车端当前没有这张照片（还没上传 / 已被清掉）。 */
    data object Empty : OfficialPhotoStatus

    /** 车型不支持 / 未开通 / 无权限 —— 重试没有意义。 */
    data class Unsupported(val message: String) : OfficialPhotoStatus

    /** 网络或服务异常，可以重试。 */
    data class Failed(val message: String) : OfficialPhotoStatus
}

/**
 * 车端照片接口的结论。
 *
 * 和「有没有照片」分开：**接口明确说没有**（[Empty]）和**根本取不到**（[Failed] /
 * [Unsupported]）在界面上要说不同的话 —— 前者是「等一会儿才有」，后者是「换手机拍吧」。
 */
sealed interface ChassisPhotoQuery {
    data class Found(val photo: ChassisParkingPhoto) : ChassisPhotoQuery

    /** 接口通了，但这辆车当前没有可用的驻车照片（还没上传 / 已过期清掉）。 */
    data object Empty : ChassisPhotoQuery

    /** 接口明确拒绝：车型不支持、没开通、无权限。 */
    data class Unsupported(val message: String) : ChassisPhotoQuery

    /** 网络 / 签名 / 服务异常，过一会儿可以重试。 */
    data class Failed(val message: String) : ChassisPhotoQuery
}

/**
 * 把 `/carownerservice/v3/api/chassis/query` 的响应翻译成 [ChassisPhotoQuery]。
 *
 * 这个接口没有正式文档，实测三种形态都出现过：`code=0` 带 `data.fileUrl`、
 * `code=0` 但 `data` 为空、以及非 0 的 `code` 配一句中文 `message`。
 * 因此判据只能是「有没有 fileUrl + code 是否成功 + 拒绝类关键字」，不能只看 code。
 *
 * ## 为什么要单独识别「不支持」
 * 这句话要原样告诉用户（而不是一律说「网络错误，请重试」）：用户重试十次也不会成功，
 * 而换回手机拍照一次就好了。识别偏保守 —— 只有明确出现「不支持 / 未开通 / 无权限」
 * 这类词才归为 [ChassisPhotoQuery.Unsupported]，其余非成功一律按可重试的失败处理，
 * 免得把一个临时的服务端抖动说成「你的车不支持」。
 */
object ChassisPhotoRules {

    /** 与官方其它接口一致：0 / 200 都算成功。 */
    private val SUCCESS_CODES = setOf(0, 200)

    private val UNSUPPORTED_WORDS = listOf(
        "不支持", "未开通", "未配置", "无此功能", "没有此功能",
        "无权限", "没有权限", "不开放",
        "not support", "unsupported", "no permission", "forbidden",
    )

    fun classify(
        code: Int,
        message: String,
        fileUrl: String,
        uploadTimeMs: Long,
    ): ChassisPhotoQuery {
        val url = fileUrl.trim()
        if (code in SUCCESS_CODES) {
            return if (url.isNotEmpty()) {
                ChassisPhotoQuery.Found(ChassisParkingPhoto(url, uploadTimeMs))
            } else {
                ChassisPhotoQuery.Empty
            }
        }
        if (url.isNotEmpty()) {
            // 少数网关把 code 塞成业务码，但图确实给了 —— 有图就先用图
            return ChassisPhotoQuery.Found(ChassisParkingPhoto(url, uploadTimeMs))
        }
        val text = message.trim()
        return if (looksUnsupported(text)) {
            ChassisPhotoQuery.Unsupported(text)
        } else {
            ChassisPhotoQuery.Failed(text.ifEmpty { "服务没有返回照片" })
        }
    }

    fun looksUnsupported(message: String): Boolean {
        if (message.isBlank()) return false
        val lower = message.lowercase(Locale.ROOT)
        return UNSUPPORTED_WORDS.any { lower.contains(it) }
    }
}

/**
 * 车端驻车照片的本地缓存。
 *
 * ## 为什么只留一张
 * 和手机照片同一个理由：这是「这一趟停的车位」，不是相册。照片里必然有车牌和车位号，
 * 越少留存越好。换车、退出登录都会清掉（见 [clear]）。
 *
 * ## 为什么带 VIN
 * 手机照片是用户自己拍的，换车时**不清**（那是他的实物记录）；车端照片却是**这辆车**的，
 * 换到另一辆车上还显示上一辆车的俯视图，是彻底的错误信息。所以文件只有一份，
 * 但记着它属于哪个 VIN，[hasPhoto] / [load] 都要求 VIN 对得上。
 *
 * ## 落盘方式
 * 先写 `official.jpg.new` 再 rename：下载中途失败时上一张还在，
 * 不会出现「刷新一下把唯一一张照片刷没了」。
 */
object OfficialParkingPhotoStore {
    private const val DIR = "parking"
    private const val PHOTO = "official.jpg"
    private const val TEMP = "official.jpg.new"
    private const val PREFS = "leapcontrol_parking_photo"
    private const val KEY_VIN = "official_vin"
    private const val KEY_UPLOAD = "official_upload_time"
    private const val KEY_SAVED = "official_saved_at"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun photoFile(context: Context) = File(dir(context), PHOTO)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 这张照片是不是当前这辆车的。 */
    fun belongsTo(context: Context, vin: String): Boolean {
        if (vin.isBlank()) return false
        return prefs(context).getString(KEY_VIN, null) == vin
    }

    fun hasPhoto(context: Context, vin: String): Boolean =
        belongsTo(context, vin) && photoFile(context).length() > 0L

    /** 车辆上传这张照片的时刻；读不到为 0（不编时间）。 */
    fun uploadTime(context: Context, vin: String): Long =
        if (!belongsTo(context, vin)) 0L else prefs(context).getLong(KEY_UPLOAD, 0L)

    /** 这张照片是什么时候取回来的（当上传时间缺失时的兜底）。 */
    fun savedAt(context: Context, vin: String): Long =
        if (!belongsTo(context, vin)) 0L else prefs(context).getLong(KEY_SAVED, 0L)

    /** 读图，长边压到 [maxSize] 以内。 */
    fun load(context: Context, vin: String, maxSize: Int): Bitmap? = runCatching {
        if (!hasPhoto(context, vin)) return null
        val file = photoFile(context)
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

    /**
     * 保存一张新取回的官方照片。
     *
     * 用 JPEG 而不是 PNG：这是手机屏上看的俯视图，官方原图通常 1MB 上下，
     * 转 JPEG 后 200KB 左右，解码快、占空间小，肉眼看不出差别。
     */
    fun save(context: Context, vin: String, bitmap: Bitmap, uploadTimeMs: Long): Boolean =
        runCatching {
            if (vin.isBlank()) return false
            val temp = File(dir(context), TEMP)
            if (temp.exists()) temp.delete()
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.fd.sync()
            }
            if (temp.length() <= 0L) {
                temp.delete()
                return false
            }
            val target = photoFile(context)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) return false
            }
            prefs(context).edit()
                .putString(KEY_VIN, vin)
                .putLong(KEY_UPLOAD, uploadTimeMs)
                .putLong(KEY_SAVED, System.currentTimeMillis())
                .apply()
            target.length() > 0L
        }.getOrDefault(false)

    fun delete(context: Context) {
        runCatching { photoFile(context).delete() }
        runCatching { File(dir(context), TEMP).delete() }
        runCatching {
            prefs(context).edit()
                .remove(KEY_VIN)
                .remove(KEY_UPLOAD)
                .remove(KEY_SAVED)
                .apply()
        }
    }

    /** 退出登录：连 VIN 记录一起清。 */
    fun clear(context: Context) = delete(context)
}

/**
 * 车端照片缩略图的**进程内**缓存，理由同 `ParkingThumbMemory`：
 * 切回首页时那一格不该从占位闪成照片。
 *
 * 键里带 VIN：换车后旧图必须立刻失效，不能拿上一辆车的地库照片顶一帧。
 */
internal object OfficialThumbMemory {
    private val entries = HashMap<String, Bitmap>()

    private fun key(vin: String, size: Int) = "$vin@$size"

    fun get(vin: String, size: Int): Bitmap? = entries[key(vin, size)]

    /** [bitmap] 为 null 表示「确实没有照片」，要连旧的那份一起丢掉。 */
    fun put(vin: String, size: Int, bitmap: Bitmap?) {
        val k = key(vin, size)
        if (bitmap == null) entries.remove(k) else entries[k] = bitmap
    }

    fun clear() = entries.clear()
}
