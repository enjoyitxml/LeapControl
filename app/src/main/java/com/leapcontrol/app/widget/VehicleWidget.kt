package com.leapcontrol.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.leapcontrol.app.R
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件的渲染与点击派发。
 *
 * ## 为什么走标准 Android 小组件（AppWidgetProvider）
 * 澎湃 / MIUI 的「原生小部件」是小米私有体系（`miui.appwidget` + widget.xiaomi.com），
 * 硬性要求：应用上架小米应用商店并通过审核、邮件向 miui-widget@xiaomi.com 申请白名单、
 * 小部件在开放平台逐个提交审核。零控是自用侧载包，走不通这条路。
 * 所以这里用标准的 [RemoteViews] 小组件，但把视觉做成澎湃原生那一套：
 * 大圆角浅色玻璃卡、大字号数值、细电量条、底部等分四格 + 极细分割线。
 *
 * ## 数据从哪来
 * 小组件进程 = App 进程。车况由 [AppRuntime] 的磁盘缓存（`status_<vin>`）预填，
 * 所以冷启动也能立刻画出上次的车况，再由 [VehicleWidgetProvider] 后台联网刷新。
 */
object VehicleWidget {

    const val ACTION_TOGGLE_LOCK = "com.leapcontrol.app.widget.TOGGLE_LOCK"
    const val ACTION_TOGGLE_AC = "com.leapcontrol.app.widget.TOGGLE_AC"
    const val ACTION_TOGGLE_TRUNK = "com.leapcontrol.app.widget.TOGGLE_TRUNK"
    const val ACTION_HORN = "com.leapcontrol.app.widget.HORN"

    /** 点「打开 App」用的 requestCode（与四个按键岔开）。 */
    private const val RC_OPEN_APP = 90
    private const val RC_LOCK = 91
    private const val RC_AC = 92
    private const val RC_TRUNK = 93
    private const val RC_HORN = 94

    private const val COLOR_TEXT = 0xFF12141A.toInt()
    private const val COLOR_SUB = 0xFF8A9099.toInt()
    private const val COLOR_ACCENT = 0xFF2AC06A.toInt()

    /** 车模图的内存缓存：解码 PNG 比一次网络请求便宜，但没必要每次渲染都做。 */
    private var carCache: Bitmap? = null
    private var carCacheVin: String? = null

    // ---------------- 对外入口 ----------------

    /** 用内存 / 磁盘里已有的数据立刻重画一次（不发网络请求）。 */
    fun render(context: Context, runtime: AppRuntime? = null, busy: String? = null) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(context, VehicleWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return
        val r = runtime ?: AppRuntime(context)
        r.loadCachedStatus()
        val views = buildViews(
            context = context,
            status = r.statusFlow.value,
            updatedAt = r.lastUpdatedFlow.value,
            busy = busy,
            signedIn = r.session.selectedVin.isNotBlank(),
        )
        ids.forEach { id -> runCatching { manager.updateAppWidget(id, views) } }
    }

    /**
     * 取当前车辆的车模图（改过色优先），并缩到小组件够用的尺寸。
     * 官方接口返回的是 1000px 级别的大图，直接塞进 RemoteViews 会超 Binder 事务上限。
     */
    suspend fun ensureCar(context: Context, runtime: AppRuntime): Bitmap? {
        val vin = runtime.session.selectedVin
        if (vin.isBlank()) return null
        carCache?.let { if (!it.isRecycled && carCacheVin == vin) return it }
        val raw = runtime.cachedDisplayCarBitmap()
            ?: runCatching { runtime.displayCarBitmap() }.getOrNull()
            ?: return null
        val scaled = scaleDown(raw, 460)
        carCache = scaled
        carCacheVin = vin
        return scaled
    }

    // ---------------- RemoteViews 组装 ----------------

    internal fun buildViews(
        context: Context,
        status: CarStatus?,
        updatedAt: Long,
        busy: String?,
        signedIn: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vehicle)

        // ---- 续航大数字 ----
        val rangeText = status?.range?.trim()
        val rangeNumber = rangeText?.let { Regex("\\d+").find(it)?.value }
        val unit = when {
            rangeText == null -> "km"
            rangeText.contains("°") -> ""
            else -> "km"
        }
        views.setTextViewText(R.id.wg_range, rangeNumber ?: "--")
        views.setTextViewText(R.id.wg_unit, unit)

        // ---- 电量条 ----
        val soc = status?.soc?.coerceIn(0, 100) ?: 0
        views.setProgressBar(R.id.wg_bar, 100, soc, false)

        // ---- 状态行 ----
        val stateText = when {
            busy != null -> busy
            !signedIn -> "点此登录零控"
            status == null -> "正在同步车况…"
            status.charging == true -> "充电中 · ${soc}%"
            status.locked == false -> "未锁车"
            status.locked == true -> "已锁车"
            else -> "电量 ${soc}%"
        }
        views.setTextViewText(R.id.wg_state, stateText)
        views.setTextColor(
            R.id.wg_state,
            if (status?.locked == false && busy == null && signedIn) COLOR_ACCENT else COLOR_TEXT,
        )

        // ---- 更新时间（和状态同一行，省下一条竖排空间；卡片最窄时也不会被裁掉） ----
        val timeText = when {
            busy != null -> "下发中…"
            updatedAt <= 0L -> "尚未同步"
            else -> "更新于 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(updatedAt))
        }
        views.setTextViewText(R.id.wg_time, timeText)

        // ---- 车模 ----
        val car = carCache?.takeIf { !it.isRecycled }
        if (car != null) {
            views.setViewVisibility(R.id.wg_car, View.VISIBLE)
            views.setImageViewBitmap(R.id.wg_car, car)
        } else {
            views.setViewVisibility(R.id.wg_car, View.GONE)
        }

        // ---- 四个按键：标签写的是「点下去会发生什么」，和参考图一致 ----
        // 锁车方向以「确认已锁」为准：锁车信号缺失时按未锁处理（点下去发上锁，重复上锁无副作用；
        // 反过来在没锁的情况下发解锁才是真风险）
        val locked = status?.locked == true
        bindCell(
            views, Cell.LOCK,
            icon = if (locked) R.drawable.ic_wg_unlock else R.drawable.ic_wg_lock,
            label = if (locked) "解锁" else "上锁",
            active = false,
            pendingIntent = broadcast(context, ACTION_TOGGLE_LOCK, RC_LOCK),
        )
        bindCell(
            views, Cell.AC,
            icon = if (status?.acOn == true) R.drawable.ic_wg_ac_on else R.drawable.ic_wg_ac,
            label = if (status?.acOn == true) "关空调" else "开空调",
            active = status?.acOn == true,
            pendingIntent = broadcast(context, ACTION_TOGGLE_AC, RC_AC),
        )
        bindCell(
            views, Cell.TRUNK,
            icon = if (status?.trunkOpen == true) R.drawable.ic_wg_trunk_on else R.drawable.ic_wg_trunk,
            label = if (status?.trunkOpen == true) "关后备箱" else "开后备箱",
            active = status?.trunkOpen == true,
            pendingIntent = broadcast(context, ACTION_TOGGLE_TRUNK, RC_TRUNK),
        )
        bindCell(
            views, Cell.HORN,
            icon = R.drawable.ic_wg_horn,
            label = "鸣笛寻车",
            active = false,
            pendingIntent = broadcast(context, ACTION_HORN, RC_HORN),
        )

        // 点上半张卡片打开 App（想手动刷新、改设置都从这里进）
        openAppIntent(context)?.let { views.setOnClickPendingIntent(R.id.wg_top, it) }

        return views
    }

    /** 一个按键格子的三处控件：整格（点击热区）/ 图标 / 文字。 */
    private enum class Cell(val container: Int, val icon: Int, val label: Int) {
        LOCK(R.id.wg_cell_lock, R.id.wg_icon_lock, R.id.wg_label_lock),
        AC(R.id.wg_cell_ac, R.id.wg_icon_ac, R.id.wg_label_ac),
        TRUNK(R.id.wg_cell_trunk, R.id.wg_icon_trunk, R.id.wg_label_trunk),
        HORN(R.id.wg_cell_horn, R.id.wg_icon_horn, R.id.wg_label_horn),
    }

    private fun bindCell(
        views: RemoteViews,
        cell: Cell,
        icon: Int,
        label: String,
        active: Boolean,
        pendingIntent: PendingIntent,
    ) {
        views.setImageViewResource(cell.icon, icon)
        views.setTextViewText(cell.label, label)
        views.setTextColor(cell.label, if (active) COLOR_ACCENT else COLOR_SUB)
        views.setOnClickPendingIntent(cell.container, pendingIntent)
    }

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, VehicleWidgetProvider::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context.applicationContext, requestCode, intent, flags)
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return null
        return PendingIntent.getActivity(
            context.applicationContext,
            RC_OPEN_APP,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 等比缩到最长边 [maxWidth] 像素（Binder 事务 1MB 上限，留足余量）。 */
    private fun scaleDown(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width
        return Bitmap.createScaledBitmap(
            src,
            maxWidth,
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
