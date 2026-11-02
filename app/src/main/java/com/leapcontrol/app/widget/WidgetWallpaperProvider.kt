package com.leapcontrol.app.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * 把 [WidgetWallpaper] 存的那张图交给桌面进程读。
 *
 * 只响应 `content://<pkg>.widgetwall/wall/<版本号>`，并且必须与当前版本号完全相等 ——
 * 调用方是任意桌面，所以不能让它凭一个路径读到私有目录里的别的东西。
 */
class WidgetWallpaperProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = WidgetWallpaper.MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "wall") return null
        val requested = segments[1].toIntOrNull() ?: return null
        if (requested != WidgetStyle.revision(context)) return null
        val file = WidgetWallpaper.file(context) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
