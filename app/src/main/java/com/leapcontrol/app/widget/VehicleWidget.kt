package com.leapcontrol.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.TypedValue
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
 * 所以用标准的 [RemoteViews]，但把版式做成澎湃原生那一套：
 * 车标 + 自定义车名、纯电 / 燃油两个续航数字各带进度条、右侧车模、底部四个纯图标按键
 * （图标直接用首页「快捷控车」栏那套 `ic_qc_*`，开启态是同图形的绿色版本）。
 *
 * ## 尺寸自适应
 * 小组件在桌面上被拉伸 / 压缩后，系统只给 `min/max width/height` 这一组 dp 值，
 * RemoteViews 也不会自动缩放字号。所以 [buildViews] 按实际尺寸重设一遍字号
 * （[applyTextScale]），图标则交给 `match_parent + fitCenter` 的 ImageView 自己缩放 ——
 * 这两条合起来才能保证「卡片大了内容跟着大」，而不是留一堆空白。
 *
 * ## 数据从哪来
 * 小组件进程 = App 进程。车况由 [AppRuntime] 的磁盘缓存（`status_<vin>`）预填，
 * 所以冷启动也能立刻画出上次的车况，再由 [VehicleWidgetProvider] 后台联网刷新。
 */
object VehicleWidget {

    /** 单次指令：解锁。 */
    const val ACTION_UNLOCK = "com.leapcontrol.app.widget.UNLOCK"

    /** 单次指令：上锁。 */
    const val ACTION_LOCK = "com.leapcontrol.app.widget.LOCK"

    /** 开关式：空调开 / 关。 */
    const val ACTION_TOGGLE_AC = "com.leapcontrol.app.widget.TOGGLE_AC"

    /** 开关式：后备箱开 / 关。 */
    const val ACTION_TOGGLE_TRUNK = "com.leapcontrol.app.widget.TOGGLE_TRUNK"

    /** 单次指令：鸣笛寻车。 */
    const val ACTION_HORN = "com.leapcontrol.app.widget.HORN"

    /** 点「打开 App」用的 requestCode（与五个按键岔开）。 */
    private const val RC_OPEN_APP = 90
    private const val RC_UNLOCK = 91
    private const val RC_LOCK = 92
    private const val RC_AC = 93
    private const val RC_TRUNK = 94
    private const val RC_HORN = 95

    private const val COLOR_TEXT = 0xFF12141A.toInt()
    private const val COLOR_SUB = 0xFF8A9099.toInt()
    private const val COLOR_WARN = 0xFFD98324.toInt()
    private const val COLOR_ACTIVE = 0xFF1FA45B.toInt()

    /** 版式基准：320×150dp（4 列 2 行在 400dp 级屏幕上的常见实际尺寸）。 */
    private const val BASE_WIDTH_DP = 320f
    private const val BASE_HEIGHT_DP = 150f

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
        val status = r.statusFlow.value
        val updatedAt = r.lastUpdatedFlow.value
        val signedIn = r.session.selectedVin.isNotBlank()
        // 车名用 APP 里那一份：自定义车名 → 官方昵称 → 我的零跑
        val title = r.displayName()
        val car = carCache?.takeIf { !it.isRecycled }
        ids.forEach { id ->
            val (widthDp, heightDp) = widgetSize(manager, id)
            val views = buildViews(
                context = context,
                status = status,
                updatedAt = updatedAt,
                busy = busy,
                signedIn = signedIn,
                title = title,
                car = car,
                widthDp = widthDp,
                heightDp = heightDp,
            )
            runCatching { manager.updateAppWidget(id, views) }
        }
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
        title: String = "我的零跑",
        car: Bitmap? = null,
        widthDp: Int = 320,
        heightDp: Int = 150,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vehicle)
        val scale = textScale(widthDp, heightDp)

        // ---- 标题：APP 里的车名（自定义车名优先，其次官方昵称） ----
        views.setTextViewText(R.id.wg_title, title.ifBlank { "我的零跑" })

        // ---- 续航：左纯电、右燃油 ----
        // 纯电车型只有纯电续航；增程车型两块都显示，燃油块单独收起 / 展开
        val extend = status?.isRangeExtender == true
        val evRange = numberText(status?.electricRange ?: status?.range)
        views.setTextViewText(R.id.wg_ev_range, evRange ?: "--")
        // 绿条刻度＝电量水位（拿不到「满电续航」标称值，不同电池版本不一样，不编假刻度）
        views.setProgressBar(R.id.wg_bar_ev, 100, status?.soc?.coerceIn(0, 100) ?: 0, false)

        if (extend) {
            views.setViewVisibility(R.id.wg_fuel_block, View.VISIBLE)
            views.setTextViewText(R.id.wg_fuel_range, numberText(status?.fuelRange) ?: "--")
            views.setProgressBar(
                R.id.wg_bar_fuel,
                100,
                status?.fuelSoc?.coerceIn(0, 100) ?: 0,
                false,
            )
        } else {
            // 纯电车型：整块收起（GONE 不参与 weight，纯电块会自动变宽，不留空洞）
            views.setViewVisibility(R.id.wg_fuel_block, View.GONE)
        }

        // ---- 右上：状态 + 更新时间（两种情况挤同一行，省下一整条竖排空间） ----
        val clock = if (updatedAt > 0L) {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(updatedAt))
        } else {
            null
        }
        val stateText: String
        val stateColor: Int
        when {
            busy != null -> {
                stateText = busy
                stateColor = COLOR_ACTIVE
            }
            !signedIn -> {
                stateText = "点此登录"
                stateColor = COLOR_SUB
            }
            status == null -> {
                stateText = "正在同步…"
                stateColor = COLOR_SUB
            }
            status.charging == true -> {
                stateText = "充电中" + clock?.let { " · $it" }.orEmpty()
                stateColor = COLOR_ACTIVE
            }
            status.locked == false -> {
                stateText = "未锁车" + clock?.let { " · $it" }.orEmpty()
                stateColor = COLOR_WARN
            }
            clock != null -> {
                stateText = "今天 $clock 更新"
                stateColor = COLOR_SUB
            }
            else -> {
                stateText = "尚未同步"
                stateColor = COLOR_SUB
            }
        }
        views.setTextViewText(R.id.wg_time, stateText)
        views.setTextColor(R.id.wg_time, stateColor)

        // ---- 车模 ----
        if (car != null) {
            views.setViewVisibility(R.id.wg_car, View.VISIBLE)
            views.setImageViewBitmap(R.id.wg_car, car)
        } else {
            views.setViewVisibility(R.id.wg_car, View.GONE)
        }

        // ---- 四个按键：解锁 / 上锁 是单次指令，空调 / 后备箱 点一下切一次 ----
        // 图形与首页「快捷控车」栏同一套（ic_qc_*），开启态用同图形的绿色版本
        bindCell(views, R.id.wg_cell_unlock, R.id.wg_icon_unlock, R.drawable.ic_qc_unlock,
            broadcast(context, ACTION_UNLOCK, RC_UNLOCK))
        bindCell(views, R.id.wg_cell_lock, R.id.wg_icon_lock, R.drawable.ic_qc_lock,
            broadcast(context, ACTION_LOCK, RC_LOCK))

        val acOn = status?.acOn == true
        bindCell(views, R.id.wg_cell_ac, R.id.wg_icon_ac,
            if (acOn) R.drawable.ic_wg_ac_on else R.drawable.ic_qc_ac,
            broadcast(context, ACTION_TOGGLE_AC, RC_AC))

        val trunkOpen = status?.trunkOpen == true
        bindCell(views, R.id.wg_cell_trunk, R.id.wg_icon_trunk,
            if (trunkOpen) R.drawable.ic_wg_trunk_on else R.drawable.ic_qc_trunk,
            broadcast(context, ACTION_TOGGLE_TRUNK, RC_TRUNK))

        // 鸣笛寻车是单次指令，没有开 / 关两态，图标固定
        bindCell(views, R.id.wg_cell_horn, R.id.wg_icon_horn, R.drawable.ic_qc_horn,
            broadcast(context, ACTION_HORN, RC_HORN))

        // 点数字区 / 车模打开 App（想手动刷新、改设置都从这里进）
        openAppIntent(context)?.let { views.setOnClickPendingIntent(R.id.wg_top, it) }

        applyTextScale(views, scale)
        return views
    }

    private fun bindCell(
        views: RemoteViews,
        container: Int,
        icon: Int,
        iconRes: Int,
        pendingIntent: PendingIntent,
    ) {
        views.setImageViewResource(icon, iconRes)
        views.setOnClickPendingIntent(container, pendingIntent)
    }

    /**
     * 按卡片实际尺寸放大 / 缩小字号。
     *
     * 宽度和高度各占一部分权重：桌面只改宽度（横向拉长）时字号跟着长一点，
     * 只改高度时才主要影响字号 —— 用长宽混合比单看一边更符合实际观感。
     * 上下限压住极端值：太小读不清、太大撑破卡片。
     */
    private fun textScale(widthDp: Int, heightDp: Int): Float {
        val byWidth = widthDp / BASE_WIDTH_DP
        val byHeight = heightDp / BASE_HEIGHT_DP
        return (byWidth * 0.55f + byHeight * 0.45f).coerceIn(0.85f, 1.30f)
    }

    private fun applyTextScale(views: RemoteViews, scale: Float) {
        fun sp(base: Float) = TypedValue.COMPLEX_UNIT_SP to base * scale
        listOf(
            R.id.wg_title to 15f,
            R.id.wg_time to 10f,
            R.id.wg_ev_range to 24f,
            R.id.wg_ev_unit to 10f,
            R.id.wg_fuel_range to 24f,
            R.id.wg_fuel_unit to 10f,
        ).forEach { (id, base) ->
            val (unit, size) = sp(base)
            runCatching { views.setTextViewTextSize(id, unit, size) }
        }
    }

    /** 从 "316km" / "316" / "316 km" 里取出数字部分；取不到返回 null。 */
    private fun numberText(raw: String?): String? =
        raw?.let { Regex("\\d+").find(it)?.value }

    /** 取卡片在桌面上的实际 dp 尺寸（取 min/max 中值，启动器两侧常不一致）。 */
    private fun widgetSize(manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            ?: return 320 to 150
        fun pick(minKey: String, maxKey: String, fallback: Int): Int {
            val min = options.getInt(minKey)
            val max = options.getInt(maxKey)
            return when {
                min > 0 && max > 0 -> (min + max) / 2
                min > 0 -> min
                max > 0 -> max
                else -> fallback
            }
        }
        val width = pick(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
        val height = pick(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150)
        return width to height
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
