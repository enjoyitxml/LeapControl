package com.leapcontrol.app.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * 把自定义壁纸以 `content://` 暴露给桌面进程。
 *
 * 桌面（launcher）通过 [android.widget.RemoteViews.setImageViewUri] 收到 URI 后，
 * 是**在它自己的进程里**打开这张图的，所以提供者必须 exported，且不能加自定义权限
 * （各家 launcher 都不会去申请我们的私有权限）。
 *
 * 只认一种路径：`/wall/<版本号>`。版本号必须等于当前版本，否则返回 null ——
 * 这样既挡住了路径穿越，也让「换了图之后桌面还拿着旧 URI」的情况自然落回纯色卡，
 * 由下一次 [VehicleWidget.render] 换成新 URI。
 */
class WidgetWallpaperProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = WidgetWallpaper.MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        if (mode != "r") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "wall") return null
        val rev = segments[1].toIntOrNull() ?: return null
        if (rev != WidgetStyle.revision(context)) return null
        val file = WidgetWallpaper.file(context) ?: return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
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
