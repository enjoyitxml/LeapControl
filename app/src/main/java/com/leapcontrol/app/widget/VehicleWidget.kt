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
 * 上半部分左边是「车标 + 自定义车名 / 纯电、燃油两个续航数字各带进度条 / 车况更新时间（靠左）」，
 * 右边整块留给车模（可以一直贴到卡片右边缘），底部是五个灰色纯图标按键。
 *
 * ## 图标颜色
 * 按键图标只有一套矢量图，颜色靠 `ImageView.setColorFilter` 现刷：常态灰、开关打开时绿。
 * 这样既不用为夜间另存浅色图，也不用为开启态存绿色副本。
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

    /** 点车模：在本模式（日间 / 夜间）的背景里换下一张，循环回「不用背景」。 */
    const val ACTION_NEXT_BG = "com.leapcontrol.app.widget.NEXT_BG"

    /** 点「打开 App」用的 requestCode（与五个按键岔开）。 */
    private const val RC_OPEN_APP = 90
    private const val RC_UNLOCK = 91
    private const val RC_LOCK = 92
    private const val RC_AC = 93
    private const val RC_TRUNK = 94
    private const val RC_HORN = 95
    private const val RC_NEXT_BG = 96

    // 日间：深色字压在浅色玻璃卡 / 浅色风景上
    private const val COLOR_TEXT = 0xFF12141A.toInt()
    private const val COLOR_SUB = 0xFF8A9099.toInt()
    private const val COLOR_UNIT = 0xFF6C7480.toInt()
    private const val COLOR_WARN = 0xFFD98324.toInt()
    private const val COLOR_ACTIVE = 0xFF1FA45B.toInt()

    // 夜间：浅色字压在深色卡 / 夜景上（对比度按 WCAG 正文 4.5:1 取的值）
    private const val COLOR_TEXT_DARK = 0xFFF2F5FA.toInt()
    private const val COLOR_SUB_DARK = 0xFFA9B3C1.toInt()
    private const val COLOR_UNIT_DARK = 0xFFA3AEBE.toInt()
    private const val COLOR_WARN_DARK = 0xFFFFB454.toInt()
    private const val COLOR_ACTIVE_DARK = 0xFF3DD68C.toInt()

    // 底部按键图标：常态是灰色（不再是纯黑），只有「开着」的开关才染成主题绿。
    // 颜色靠 ImageView.setColorFilter 现刷（SRC_ATOP，只换色不改透明度），
    // 所以同一张矢量图既能当灰图标也能当绿图标，不用为每种颜色单独存资源。
    private const val COLOR_ICON = 0xFF7C838D.toInt()
    private const val COLOR_ICON_DARK = 0xFF9BA5B2.toInt()

    /**
     * 单个按键图标的「内边距补偿」（dp，左/上/右/下）。
     *
     * 五个图形形状差别太大，只把盒子做成一样大是**看着不齐**的：
     * - 锁和后备箱都是瘦高的方块 + 顶部一条细线，重心在上方，撑满盒子才够大；
     * - 空调是四叶风扇，闭合圆形，同样的高度下面积最大，看着比别的都胖一圈 → 四面各收 2dp；
     * - 鸣笛是「车 + 声波」，实体全压在底部，盒子一样高的时候视觉重心明显下沉，
     *   像被人按着头 → 上下不对称（上 0、下 6），横向放 2dp 再整体往上提 3dp。
     *
     * 用 padding 而不是改矢量图的 viewBox：一笔就同时改了大小和位置，
     * 也不用为每个图标存一份补偿过的副本。
     */
    private data class IconInset(val l: Int, val t: Int, val r: Int, val b: Int)

    private val INSET_IDLE = IconInset(3, 3, 3, 3)
    private val INSET_AC = IconInset(5, 5, 5, 5)
    private val INSET_HORN = IconInset(1, 0, 1, 6)

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
        // 日间 / 夜间两套背景与配色都跟系统深浅模式走（不是跟 App 里的外观设置）
        val night = WidgetBackdrops.isNight(context)
        val bgRes = WidgetBackdrops.byId(WidgetBackdrops.selectedId(context, night), night)?.res
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
                night = night,
                bgRes = bgRes,
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
        night: Boolean = false,
        bgRes: Int? = null,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vehicle)
        val scale = textScale(widthDp, heightDp)
        val density = context.resources.displayMetrics.density

        val cTitle = if (night) COLOR_TEXT_DARK else COLOR_TEXT
        val cSub = if (night) COLOR_SUB_DARK else COLOR_SUB
        val cUnit = if (night) COLOR_UNIT_DARK else COLOR_UNIT
        val cWarn = if (night) COLOR_WARN_DARK else COLOR_WARN
        val cActive = if (night) COLOR_ACTIVE_DARK else COLOR_ACTIVE

        // ---- 外壳：底卡 / 按键水波 / 车标 / 分割线，日夜各一套 ----
        views.setInt(
            R.id.wg_root,
            "setBackgroundResource",
            if (night) R.drawable.widget_card_dark else R.drawable.widget_card,
        )
        views.setImageViewResource(
            R.id.wg_logo,
            if (night) R.drawable.ic_wg_logo_light else R.drawable.ic_wg_logo,
        )
        val cellBg = if (night) R.drawable.widget_cell_dark else R.drawable.widget_cell
        listOf(
            R.id.wg_cell_unlock, R.id.wg_cell_lock, R.id.wg_cell_ac,
            R.id.wg_cell_trunk, R.id.wg_cell_horn,
        ).forEach { views.setInt(it, "setBackgroundResource", cellBg) }
        val dividerColor = if (night) 0x1FFFFFFF else 0x1412141A
        listOf(R.id.wg_div_1, R.id.wg_div_2, R.id.wg_div_3, R.id.wg_div_4)
            .forEach { views.setInt(it, "setBackgroundColor", dividerColor) }

        // ---- 风景背景（没选就 GONE，露出玻璃卡） ----
        // 走资源 id：桌面进程自己按原图分辨率解码，比塞位图（受 1MB Binder 上限只能给 400px）清楚得多
        if (bgRes != null) {
            views.setViewVisibility(R.id.wg_bg, View.VISIBLE)
            views.setImageViewResource(R.id.wg_bg, bgRes)
        } else {
            views.setViewVisibility(R.id.wg_bg, View.GONE)
        }

        // ---- 标题：APP 里的车名（自定义车名优先，其次官方昵称） ----
        views.setTextViewText(R.id.wg_title, title.ifBlank { "我的零跑" })
        views.setTextColor(R.id.wg_title, cTitle)

        // ---- 续航：左纯电、右燃油 ----
        // 纯电车型只有纯电续航；增程车型两块都显示，燃油块单独收起 / 展开
        val extend = status?.isRangeExtender == true
        val evRange = numberText(status?.electricRange ?: status?.range)
        views.setTextViewText(R.id.wg_ev_range, evRange ?: "--")
        views.setTextColor(R.id.wg_ev_range, cTitle)
        views.setTextColor(R.id.wg_ev_unit, cUnit)
        // 绿条刻度＝电量水位（拿不到「满电续航」标称值，不同电池版本不一样，不编假刻度）
        views.setProgressBar(R.id.wg_bar_ev, 100, status?.soc?.coerceIn(0, 100) ?: 0, false)

        if (extend) {
            views.setViewVisibility(R.id.wg_fuel_block, View.VISIBLE)
            views.setTextViewText(R.id.wg_fuel_range, numberText(status?.fuelRange) ?: "--")
            views.setTextColor(R.id.wg_fuel_range, cTitle)
            views.setTextColor(R.id.wg_fuel_unit, cUnit)
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

        // ---- 车名后面跟状态 / 更新时间（原来单独占右上角一列，挪过来把横向空间让给车模） ----
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
                stateColor = cActive
            }
            !signedIn -> {
                stateText = "点此登录"
                stateColor = cSub
            }
            status == null -> {
                stateText = "正在同步…"
                stateColor = cSub
            }
            status.charging == true -> {
                stateText = "充电中" + clock?.let { " · $it" }.orEmpty()
                stateColor = cActive
            }
            status.locked == false -> {
                stateText = "未锁车" + clock?.let { " · $it" }.orEmpty()
                stateColor = cWarn
            }
            clock != null -> {
                // 不再写「今天 … 更新」：字太长会把车名往后顶，省略成「14:30 更新」意思一样
                stateText = "$clock 更新"
                stateColor = cSub
            }
            else -> {
                stateText = "尚未同步"
                stateColor = cSub
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

        // ---- 五个按键：解锁 / 上锁 / 鸣笛 是单次指令，空调 / 后备箱 点一下切一次 ----
        // 图形与首页「快捷控车」栏同一套（ic_qc_*）。颜色统一由 colorFilter 现刷：
        // 常态灰、开着绿 —— 所以不再需要按夜间另存一套浅色图，也不再需要 _on 的绿色副本。
        val cIcon = if (night) COLOR_ICON_DARK else COLOR_ICON
        bindCell(views, R.id.wg_cell_unlock, R.id.wg_icon_unlock,
            R.drawable.ic_qc_unlock,
            broadcast(context, ACTION_UNLOCK, RC_UNLOCK), cIcon, INSET_IDLE, density)
        bindCell(views, R.id.wg_cell_lock, R.id.wg_icon_lock,
            R.drawable.ic_qc_lock,
            broadcast(context, ACTION_LOCK, RC_LOCK), cIcon, INSET_IDLE, density)

        val acOn = status?.acOn == true
        bindCell(views, R.id.wg_cell_ac, R.id.wg_icon_ac,
            R.drawable.ic_wg_fan,
            broadcast(context, ACTION_TOGGLE_AC, RC_AC),
            if (acOn) cActive else cIcon, INSET_AC, density)

        val trunkOpen = status?.trunkOpen == true
        bindCell(views, R.id.wg_cell_trunk, R.id.wg_icon_trunk,
            R.drawable.ic_qc_trunk,
            broadcast(context, ACTION_TOGGLE_TRUNK, RC_TRUNK),
            if (trunkOpen) cActive else cIcon, INSET_IDLE, density)

        // 鸣笛寻车是单次指令，没有开 / 关两态，永远是灰的
        bindCell(views, R.id.wg_cell_horn, R.id.wg_icon_horn,
            R.drawable.ic_qc_horn,
            broadcast(context, ACTION_HORN, RC_HORN), cIcon, INSET_HORN, density)

        // 点数字区打开 App；点车模换背景（用户要的「一键换风景」，不用进设置页）
        openAppIntent(context)?.let { views.setOnClickPendingIntent(R.id.wg_top, it) }
        views.setOnClickPendingIntent(
            R.id.wg_car,
            broadcast(context, ACTION_NEXT_BG, RC_NEXT_BG),
        )

        applyTextScale(views, scale)
        return views
    }

    private fun bindCell(
        views: RemoteViews,
        container: Int,
        icon: Int,
        iconRes: Int,
        pendingIntent: PendingIntent,
        tint: Int,
        inset: IconInset,
        density: Float,
    ) {
        views.setImageViewResource(icon, iconRes)
        // setColorFilter(int) 反射调用：SRC_ATOP 只替换颜色、保留抗锯齿后的透明度，
        // 线稿图标才不会变糊；换成 setImageViewTintList 要 API 31+，这里得兼容到 26。
        views.setInt(icon, "setColorFilter", tint)
        // 逐图标视觉补偿：setViewPadding 收的是 px，不是 dp
        views.setViewPadding(
            icon,
            (inset.l * density).toInt(),
            (inset.t * density).toInt(),
            (inset.r * density).toInt(),
            (inset.b * density).toInt(),
        )
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
            R.id.wg_title to 13f,
            R.id.wg_time to 10f,
            R.id.wg_ev_range to 22f,
            R.id.wg_ev_unit to 10f,
            R.id.wg_fuel_range to 22f,
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
