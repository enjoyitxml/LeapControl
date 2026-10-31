package com.leapcontrol.app.journal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 每天把零记自动备份一份到公共「下载」目录（`Download/LeapControl/`）。
 *
 * 为什么是下载目录，而不是 `filesDir`：
 * `filesDir`、`getExternalFilesDir` 都属于 App 私有数据，**卸载就会一起删掉**。
 * 用户这次反馈的「更新后数据全没了」如果发生在卸载重装（签名、清理工具、
 * 换机助手都可能触发），私有目录里的任何副本都救不回来。公共下载目录不属于 App，
 * 卸载不会动它，所以它是本机唯一能跨「卸载 / 重装」存活的落点。
 *
 * 代价是这份文件在文件管理器里可见（也能被用户自己拷走、发给自己），
 * 所以：默认开启但**可以在设置里关掉**，并且只保留最近 [KEEP] 份。
 * 走 MediaStore，不申请存储权限（API 29+）。
 */
object JournalAutoBackup {

    private const val PREFS = "leapcontrol_journal_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_LAST_ERROR = "last_error"

    /** 目标子目录名（在公共下载目录下）。 */
    private const val FOLDER = "LeapControl"

    /** 文件名前缀：`零控零记-自动备份-20261031.json`。 */
    private const val PREFIX = "零控零记-自动备份-"

    /** 保留份数（一天一份，约一周）。 */
    private const val KEEP = 7

    /** 两次自动备份的最小间隔。 */
    const val INTERVAL_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastAt(context: Context): Long = prefs(context).getLong(KEY_LAST_AT, 0L)

    fun lastName(context: Context): String? = prefs(context).getString(KEY_LAST_NAME, null)

    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    fun isDue(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        now - lastAt(context) >= INTERVAL_MS

    /** 自动备份状态摘要（设置页副标题用）。 */
    fun summary(context: Context, now: Long = System.currentTimeMillis()): String {
        if (!isEnabled(context)) return "已关闭：只在本机保留，卸载后不会留下副本"
        val error = lastError(context)
        if (!error.isNullOrBlank()) return "上次备份失败：$error"
        val last = lastAt(context)
        if (last <= 0L) return "还没备份过，今天内会自动写一份到 下载/$FOLDER"
        val days = (now - last) / (24 * 60 * 60 * 1000L)
        val whenText = when {
            days <= 0 -> "今天"
            days == 1L -> "昨天"
            else -> "$days 天前"
        }
        return "上次备份 $whenText · 下载/$FOLDER/${lastName(context) ?: ""}"
    }

    /** 立即写一份。失败会抛异常，调用方决定是否提示（自动路径应静默）。 */
    fun write(context: Context, text: String, now: Long = System.currentTimeMillis()) {
        try {
            writeInternal(context.applicationContext, text, now)
            prefs(context).edit()
                .putLong(KEY_LAST_AT, now)
                .putString(KEY_LAST_NAME, fileName(now))
                .remove(KEY_LAST_ERROR)
                .apply()
        } catch (t: Throwable) {
            prefs(context).edit()
                .putString(KEY_LAST_ERROR, t.message ?: t.javaClass.simpleName)
                .apply()
            throw t
        }
    }

    private fun writeInternal(context: Context, text: String, now: Long) {
        val name = fileName(now)
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, bytes)
        } else {
            writeViaFile(context, name, bytes)
        }
    }

    private fun writeViaMediaStore(context: Context, name: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
        // 同名先删：否则系统会自动改成「... (1).json」，一天堆一个
        runCatching {
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf("$relative%", name),
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("系统没有给出写入位置")
        resolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IOException("写不进下载目录")
        trim(resolver, collection, relative)
    }

    private fun trim(resolver: android.content.ContentResolver, collection: Uri, relative: String) {
        val ids = ArrayList<Long>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$relative%", "$PREFIX%"),
                "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) ids += cursor.getLong(0)
            }
        }
        ids.drop(KEEP).forEach { id ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    /** Android 8/9 兜底：直接写公共下载目录（清单里已声明 maxSdkVersion=28 的存储权限）。 */
    private fun writeViaFile(context: Context, name: String, bytes: ByteArray) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("建不了 下载/$FOLDER 目录")
        File(dir, name).writeBytes(bytes)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { runCatching { it.delete() } }
    }

    private fun fileName(now: Long): String {
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US))
        return "$PREFIX$day.json"
    }
}
