package com.leapcontrol.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.leapcontrol.app.R
import com.leapcontrol.app.tiles.QuickTiles
import com.leapcontrol.app.ui.AppRuntime
import com.leapcontrol.app.ui.CarStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件的渲染与点击派发。
 *
 * ## 为什么是标准 Android 小组件
 * 澎湃 / MIUI 的「原生小部件」是小米私有体系，要求上架小米应用商店并申请白名单，
 * 侧载包走不通，所以用系统 AppWidgetProvider，视觉自己做。
 *
 * ## 两套版式
 * 用户在「个性化 → 桌面小组件」里选，见 [WidgetStyle]：
 * - **A 素纸**：浅色卡 + 发丝分隔线，字色跟系统深浅走；
 * - **D 沉浸**：一张图铺满，下半部分压暗，按键是白玻璃，字色恒为白。
 * （早先还有一版「色块」，用户否决后已删除。）
 *
 * ## 续航：纯电与增程是两种排版
 * - 纯电：一个大数字（26sp）+ 电量条 + 一行「电量 68%」；
 * - 增程：两行 —— 电（22sp，主题数字）+ 电量条，油（16sp，前面一个小「油」字）+ 油量条，
 *   副信息变成「电 68% · 油 45%」。
 * 判据是 [CarStatus.isRangeExtender]（按 VIN 粘滞的车型能力），不是「有没有油量信号」——
 * 后者在部分车型上会缺失，用它判断会把增程车错画成纯电。
 *
 * ## 尺寸自适应
 * 桌面把卡片拉宽 / 压扁后，系统只给 min/max width/height 这一组 dp 值，RemoteViews 不会自动缩放字号，
 * 所以 [buildViews] 按实际尺寸重设一遍字号（[applyTextScale]）；图标交给 match_parent + fitCenter 自己缩。
 *
 * ## 数据从哪来
 * 小组件与 App 同进程。车况先用磁盘缓存预填（冷启动也能立刻有内容），
 * 再由 [VehicleWidgetProvider] 在添加 / 点按键时后台刷新。
 *
 * 车模图同理，但多了一层 [WidgetCarImage]（磁盘）：桌面定时更新、拖动改尺寸都会在**新进程**里
 * 重画一次，那时 AppRuntime 的内存位图是空的 —— 只认内存就会把车模判成「没有」，让它在卡上消失。
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

    val ACTIONS = setOf(ACTION_UNLOCK, ACTION_LOCK, ACTION_TOGGLE_AC, ACTION_TOGGLE_TRUNK, ACTION_HORN)

    /** 点「打开 App」用的 requestCode（与五个按键岔开）。 */
    private const val RC_OPEN_APP = 90
    private const val RC_UNLOCK = 91
    private const val RC_LOCK = 92
    private const val RC_AC = 93
    private const val RC_TRUNK = 94
    private const val RC_HORN = 95

    // 浅色卡上的字色
    private const val INK_LIGHT = 0xFF12141A.toInt()
    private const val SUB_LIGHT = 0xFF8A9099.toInt()
    private const val WARN_LIGHT = 0xFFD98324.toInt()

    // 深色卡上的字色（对比度按 WCAG 正文 4.5:1 取）
    private const val INK_DARK = 0xFFF2F5FA.toInt()
    private const val SUB_DARK = 0xFFA9B3C1.toInt()
    private const val WARN_DARK = 0xFFFFB454.toInt()

    /** 沉浸样式压在图上：字恒白，副信息靠透明度分层。 */
    private const val INK_GLASS = 0xFFFFFFFF.toInt()
    private const val SUB_GLASS = 0xCCFFFFFF.toInt()
    private const val WARN_GLASS = 0xFFFFD9A0.toInt()

    private const val ACCENT_LIGHT = 0xFF1FA45B.toInt()
    private const val ACCENT_DARK = 0xFF3DD68C.toInt()
    private const val ACCENT_GLASS = 0xFF8BEFB8.toInt()

    /** 续航两行的小图标：与首页续航区同色（电绿、油蓝），沉浸样式提亮一档。 */
    private const val EV_LIGHT = 0xFF2FCC71.toInt()
    private const val EV_DARK = 0xFF3DD68C.toInt()
    private const val EV_GLASS = 0xFF8BEFB8.toInt()
    private const val FUEL_LIGHT = 0xFF2F86F6.toInt()
    private const val FUEL_DARK = 0xFF5AA9FF.toInt()
    private const val FUEL_GLASS = 0xFF9CC8FF.toInt()

    /**
     * 五个按键图标的留白基数（dp）。
     *
     * 它们不是同一套比例：空调的压缩机符号笔画密、层次多，撑满格子会显得比邻居大一圈；
     * 鸣笛的喇叭是横长形，按基数缩完看着略小，要少留一点边（但不能像上一版那样放太多）。
     * 统一留白时并排一眼就能看出不齐。
     */
    private const val AC_ICON_SHRINK = 2.5f
    private const val HORN_ICON_GROW = 1.5f

    /**
     * 鸣笛图标的上移量（dp）：靠上下不对称的 padding 把它顶高一点。
     * 喇叭图形本身重心偏下，跟旁边三个居中图标并排时看着像「坐」在格子里。
     */
    private const val HORN_ICON_LIFT = 1.5f

    /** 版式基准：320×150dp（4 列 2 行在 400dp 级屏幕上的常见实际尺寸）。 */
    private const val BASE_WIDTH_DP = 320f
    private const val BASE_HEIGHT_DP = 150f

    /**
     * 车模图的内存缓存。`render()` 会被 UI 线程（改尺寸）、IO 线程（刷新车况）、
     * 广播线程（点按键）同时调用，所以两个字段都标 `@Volatile`。
     */
    @Volatile
    private var carCache: Bitmap? = null

    @Volatile
    private var carCacheVin: String? = null

    /** 补车模图的后台任务。进程在任务就在，进程没了说明图也不需要再画。 */
    private val carFetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 找不到车模图时，两次「补图」之间的最小间隔（按 VIN 记）。 */
    private const val CAR_RETRY_MS = 15 * 60 * 1000L

    /** 补图节流记在哪个 prefs 里（与控车按键的 `widget_action_state` 分开）。 */
    private const val PREFS_CAR = "widget_car_state"

    // ---------------- 对外入口 ----------------

    /** 用内存 / 磁盘里已有的数据立刻重画一次（不发网络请求）。 */
    fun render(context: Context, runtime: AppRuntime? = null, busy: String? = null) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(context, VehicleWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return
        // 传进来的 runtime 已经是刚刷完的那一份，别再 loadCachedStatus：
        // 磁盘缓存比内存里的新鲜值旧，重读一次会把刚拿到的车况又盖回去。
        val r = runtime ?: AppRuntime(context).also { it.loadCachedStatus() }
        val status = r.statusFlow.value
        val updatedAt = r.lastUpdatedFlow.value
        val vin = r.session.selectedVin
        val signedIn = vin.isNotBlank()
        val title = r.displayName()
        // 内存没有就翻磁盘那份（[WidgetCarImage]）—— 冷进程第一帧全靠它，
        // 只认内存的话车模会时有时无：开过 App 之后有、被系统回收之后就没了。
        val car = cachedCar(context, vin)
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

        // 磁盘里也没有（第一次装、或者刚换登录账号）：后台补一张，补到就再画一遍。
        // 改卡片尺寸、桌面定时更新走的就是这条路径，以前它们只画不补，于是卡上一直缺车模。
        if (car == null && signedIn) fetchCarImageLater(context, r, vin)
    }

    /** 内存 → 磁盘。解码一张 440 宽的 PNG 是毫秒级的，而且一个进程最多只做一次。 */
    private fun cachedCar(context: Context, vin: String): Bitmap? {
        carCache?.let { if (!it.isRecycled && carCacheVin == vin) return it }
        if (vin.isBlank()) return null
        val fromDisk = WidgetCarImage.load(context, vin) ?: return null
        carCache = fromDisk
        carCacheVin = vin
        return fromDisk
    }

    /**
     * 车模图缺失时补一次。
     *
     * 按 VIN 限频：有些账号本来就没有官方定妆照（接口不给 shareBindUrl），
     * 每次重画都去问一遍没有意义，还会让每次桌面更新都多一次网络请求。
     */
    private fun fetchCarImageLater(context: Context, runtime: AppRuntime, vin: String) {
        if (vin.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_CAR, Context.MODE_PRIVATE)
        val key = "last_try_$vin"
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(key, 0L) < CAR_RETRY_MS) return
        prefs.edit().putLong(key, now).apply()
        carFetchScope.launch {
            val got = runCatching { ensureCar(context, runtime) }.getOrNull()
            // 没拿到就不用重画：卡片保持原样，等下一次（或用户点按键）再试
            if (got != null) runCatching { render(context, runtime) }
        }
    }

    /**
     * 取当前车辆的车模图（改过色优先），并缩到小组件够用的尺寸。
     * 官方接口返回 1000px 级别的大图，直接塞进 RemoteViews 会超 Binder 事务上限。
     *
     * 拿到之后**同时写进磁盘**（[WidgetCarImage]）：这张图是桌面卡片唯一的图像来源，
     * 进程一被回收内存就没了，不落盘的话下次唤醒又得从头取一遍。
     */
    suspend fun ensureCar(context: Context, runtime: AppRuntime): Bitmap? {
        val vin = runtime.session.selectedVin
        if (vin.isBlank()) return null
        carCache?.let { if (!it.isRecycled && carCacheVin == vin) return it }
        val raw = runtime.cachedDisplayCarBitmap()
            ?: runCatching { runtime.displayCarBitmap() }.getOrNull()
            ?: return null
        val scaled = scaleDown(raw, 440)
        carCache = scaled
        carCacheVin = vin
        WidgetCarImage.save(context, vin, scaled)
        return scaled
    }

    /** 换车 / 登出时清掉车模缓存（内存 + 磁盘），避免把上一辆车的图留在桌面上。 */
    fun clearCarCache(context: Context) {
        carCache = null
        carCacheVin = null
        WidgetCarImage.clearAll(context)
    }

    /**
     * 车模换了（刷新官方图 / 换色 / 相册导入 / 恢复官方）：丢掉旧图，重新取一张并让桌面重画。
     *
     * 只清内存是不够的 —— 磁盘那份缓存图会让桌面一直显示上一版车模，
     * 而且要等到进程被杀、重新取图才会变，看起来就像「换了没生效」。
     */
    fun refreshCarImage(context: Context, runtime: AppRuntime) {
        carCache = null
        carCacheVin = null
        WidgetCarImage.clear(context, runtime.session.selectedVin)
        carFetchScope.launch {
            runCatching { ensureCar(context, runtime) }
            runCatching { render(context, runtime) }
        }
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
        val style = WidgetStyle.style(context)
        val night = WidgetStyle.isNight(context)
        val glass = style == WidgetStyle.STYLE_D

        val layout = if (glass) R.layout.widget_d else R.layout.widget_a
        val views = RemoteViews(context.packageName, layout)
        val density = context.resources.displayMetrics.density
        val scale = textScale(widthDp, heightDp)

        val ink = when {
            glass -> INK_GLASS
            night -> INK_DARK
            else -> INK_LIGHT
        }
        val sub = when {
            glass -> SUB_GLASS
            night -> SUB_DARK
            else -> SUB_LIGHT
        }
        val warn = when {
            glass -> WARN_GLASS
            night -> WARN_DARK
            else -> WARN_LIGHT
        }
        val active = when {
            glass -> ACCENT_GLASS
            night -> ACCENT_DARK
            else -> ACCENT_LIGHT
        }
        // 续航那两行的小图标跟首页同色：电绿、油蓝。沉浸样式压在照片上，
        // 原色会被背景吃掉，所以单独提亮一档。
        val evColor = when {
            glass -> EV_GLASS
            night -> EV_DARK
            else -> EV_LIGHT
        }
        val fuelColor = when {
            glass -> FUEL_GLASS
            night -> FUEL_DARK
            else -> FUEL_LIGHT
        }

        // ---- 外壳 ----
        // 沉浸样式的底必须是深色：万一图片没画上（桌面不认 URI），白字还得有底可压。
        views.setInt(
            R.id.wg_root,
            "setBackgroundResource",
            if (glass || night) R.drawable.widget_card_dark else R.drawable.widget_card_light,
        )

        // ---- 沉浸样式的底图 ----
        if (glass) {
            val wallpaper = if (WidgetWallpaper.exists(context)) WidgetWallpaper.uri(context) else null
            // 走 URI：桌面进程自己按原图分辨率解码，绕开 setImageViewBitmap 的 1MB 上限
            if (wallpaper != null) applyImage(views, R.id.wg_bg, wallpaper) else
                views.setImageViewResource(R.id.wg_bg, R.drawable.widget_d_fallback)
        }

        // ---- 车标 + 车名 ----
        // 车名前固定放零跑官方 V 标（用户明确要一直显示，不再按「高级设置」是否解锁切方向盘）。
        // 它是纯白图形，浅色卡上必须染色，否则白字白底看不见。
        views.setImageViewResource(R.id.wg_logo, R.drawable.ic_leap_logo)
        views.setInt(R.id.wg_logo, "setColorFilter", ink)
        views.setTextViewText(R.id.wg_title, title.ifBlank { "我的零跑" })
        views.setTextColor(R.id.wg_title, ink)

        // ---- 状态 / 更新时间（卡片右上角）----
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
                stateColor = active
            }
            !signedIn -> {
                stateText = "点此登录"
                stateColor = sub
            }
            status == null -> {
                stateText = "正在同步…"
                stateColor = sub
            }
            status.charging == true -> {
                stateText = "充电中" + clock?.let { " · $it" }.orEmpty()
                stateColor = active
            }
            status.locked == false -> {
                stateText = "未锁车" + clock?.let { " · $it" }.orEmpty()
                stateColor = warn
            }
            clock != null -> {
                stateText = "$clock 更新"
                stateColor = sub
            }
            else -> {
                stateText = "尚未同步"
                stateColor = sub
            }
        }
        views.setTextViewText(R.id.wg_time, stateText)
        views.setTextColor(R.id.wg_time, stateColor)

        // ---- 续航：与首页续航区同一套口径（图标 · 里程 · 进度条 · 百分比）----
        // 纯电车型只有电一行、读的是「总续航」；增程车型电那一行读「纯电续航」，
        // 再补一行油 —— 跟首页 MileageRow 的取值规则一模一样，两边数字对得上。
        val reev = status?.isRangeExtender == true
        val soc = status?.soc?.coerceIn(0, 100)
        val fuelSoc = status?.fuelSoc?.coerceIn(0, 100)
        val evRangeText = kmText(if (reev) status?.electricRange else status?.range)
        val fuelRangeText = kmText(status?.fuelRange)
        views.setInt(R.id.wg_ev_icon, "setColorFilter", evColor)
        views.setTextViewText(R.id.wg_ev_value, evRangeText)
        views.setTextColor(R.id.wg_ev_value, ink)
        views.setTextViewText(R.id.wg_ev_pct, soc?.let { "$it%" } ?: "--")
        views.setTextColor(R.id.wg_ev_pct, ink)
        // 条刻度 = 电量 / 油量水位：拿不到「满电续航」标称值（不同电池版本不一样），不编假刻度。
        // 电绿油蓝这两套颜色写在布局引用的 progressDrawable 里（widget_bar_ev / widget_bar_fuel），
        // 运行时**不去换 drawable** —— ProgressBar 没有 setProgressDrawable(int) 这个签名，
        // setInt 反射调用会直接失败。
        views.setProgressBar(R.id.wg_ev_bar, 100, soc ?: 0, false)

        // 两行里程加起来比一张 4×1 高得多，高度不够时只留电那一行。
        // 纯电车型本来就只有一行，降级之后结构不变，不会突然换一套版式。
        if (reev && heightDp >= 120) {
            views.setViewVisibility(R.id.wg_fuel_row, View.VISIBLE)
            views.setInt(R.id.wg_fuel_icon, "setColorFilter", fuelColor)
            views.setTextViewText(R.id.wg_fuel_value, fuelRangeText)
            views.setTextColor(R.id.wg_fuel_value, ink)
            views.setTextViewText(R.id.wg_fuel_pct, fuelSoc?.let { "$it%" } ?: "--")
            views.setTextColor(R.id.wg_fuel_pct, ink)
            views.setProgressBar(R.id.wg_fuel_bar, 100, fuelSoc ?: 0, false)
        } else {
            views.setViewVisibility(R.id.wg_fuel_row, View.GONE)
        }

        // ---- 车模 ----
        if (car != null) {
            views.setViewVisibility(R.id.wg_car, View.VISIBLE)
            views.setImageViewBitmap(R.id.wg_car, car)
        } else {
            views.setViewVisibility(R.id.wg_car, View.GONE)
        }

        // ---- 五个按键 ----
        // 高亮规则：正在生效的状态才亮 —— 车门解锁时「解锁」亮、尾门开着时「后备箱」亮、
        // 空调开着时「空调」亮。上锁 / 鸣笛没有「开着」这一态，永远常态色（鸣笛是瞬时指令）。
        val unlocked = status?.locked == false
        // 空调 / 后备箱：车况读不到这个信号（部分车型恒为 null）时退回本地记住的开合态，
        // 否则按了也不会亮。判据和控制中心磁贴、按键点击方向是同一份（QuickTiles.state_<id>）。
        val acOn = status?.acOn
            ?: QuickTiles.byId("ac")?.let { QuickTiles.isOn(context, it) }
            ?: false
        val trunkOpen = status?.trunkOpen
            ?: QuickTiles.byId("trunk")?.let { QuickTiles.isOn(context, it) }
            ?: false
        val cellBg = when {
            glass -> R.drawable.widget_cell_d
            night -> R.drawable.widget_cell_a_dark
            else -> R.drawable.widget_cell_a
        }
        // 五个格子的图标不按同一套留白：空调那个压缩机符号笔画密，撑满会比邻居看着大一圈，
        // 多留一档边收小；鸣笛的喇叭是横长形，少留一点边才不至于显小，另外整体顶高一点点。
        val basePad = 5.5f
        bindCell(views, R.id.wg_cell_unlock, R.id.wg_icon_unlock, R.drawable.ic_qc_unlock,
            broadcast(context, ACTION_UNLOCK, RC_UNLOCK),
            if (unlocked) active else ink, cellBg, basePad, density)
        bindCell(views, R.id.wg_cell_lock, R.id.wg_icon_lock, R.drawable.ic_qc_lock,
            broadcast(context, ACTION_LOCK, RC_LOCK), ink, cellBg, basePad, density)
        bindCell(views, R.id.wg_cell_ac, R.id.wg_icon_ac, R.drawable.ic_qc_ac,
            broadcast(context, ACTION_TOGGLE_AC, RC_AC),
            if (acOn) active else ink, cellBg, basePad + AC_ICON_SHRINK, density)
        bindCell(views, R.id.wg_cell_trunk, R.id.wg_icon_trunk, R.drawable.ic_qc_trunk,
            broadcast(context, ACTION_TOGGLE_TRUNK, RC_TRUNK),
            if (trunkOpen) active else ink, cellBg, basePad, density)
        bindCell(views, R.id.wg_cell_horn, R.id.wg_icon_horn, R.drawable.ic_qc_horn,
            broadcast(context, ACTION_HORN, RC_HORN), ink, cellBg,
            (basePad - HORN_ICON_GROW).coerceAtLeast(0f), density,
            liftDp = HORN_ICON_LIFT)

        // 点文字区 / 车模都打开 App（车模不再兼做「换背景」按钮，那是设置页的事）
        openAppIntent(context)?.let {
            views.setOnClickPendingIntent(R.id.wg_info, it)
            views.setOnClickPendingIntent(R.id.wg_car_box, it)
        }

        applyTextScale(views = views, scale = scale)
        return views
    }

    private fun bindCell(
        views: RemoteViews,
        container: Int,
        icon: Int,
        iconRes: Int,
        pendingIntent: PendingIntent,
        tint: Int,
        cellBg: Int,
        paddingDp: Float,
        density: Float,
        liftDp: Float = 0f,
    ) {
        views.setInt(container, "setBackgroundResource", cellBg)
        views.setImageViewResource(icon, iconRes)
        // setColorFilter(int) 是 ImageView 的公开方法：SRC_ATOP 只换颜色、保留抗锯齿透明度，
        // 线稿图标才不会发糊（setImageViewTintList 要 API 31+，这里得兼容到 26）。
        views.setInt(icon, "setColorFilter", tint)
        // setViewPadding 收的是 px，不是 dp。下边多留 = 图形整体上移。
        val p = (paddingDp * density).toInt()
        val lift = (liftDp * density).toInt()
        views.setViewPadding(icon, p, (p - lift).coerceAtLeast(0), p, p + lift)
        views.setOnClickPendingIntent(container, pendingIntent)
    }

    /**
     * 按卡片实际尺寸放大 / 缩小字号。
     *
     * 宽度和高度各占一份权重：桌面只拉宽时字号长一点，只改高度时才主要影响字号 ——
     * 用长宽混合比单看一边更接近实际观感。上下限压住极端值：太小读不清，太大撑破卡片。
     */
    private fun textScale(widthDp: Int, heightDp: Int): Float {
        val byWidth = widthDp / BASE_WIDTH_DP
        val byHeight = heightDp / BASE_HEIGHT_DP
        return (byWidth * 0.55f + byHeight * 0.45f).coerceIn(0.82f, 1.28f)
    }

    private fun applyTextScale(views: RemoteViews, scale: Float) {
        fun size(id: Int, base: Float, cap: Float = 1.28f) {
            runCatching {
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, base * scale.coerceAtMost(cap))
            }
        }
        size(R.id.wg_title, 13f)
        size(R.id.wg_time, 10f)
        // 里程槽定宽 46dp（定宽才能让上下两行的进度条和百分比严格对齐），所以字号卡住上限：
        // 13sp 下「247km」约 40dp，还剩 6dp 余量；再放大就会被定宽裁掉末位。
        // 里程和百分比用同一档基数，任何缩放下两个数字都是一样大的。
        size(R.id.wg_ev_value, 13f, 1.1f)
        size(R.id.wg_ev_pct, 13f, 1.1f)
        size(R.id.wg_fuel_value, 13f, 1.1f)
        size(R.id.wg_fuel_pct, 13f, 1.1f)
    }

    /** setImageViewUri 在部分桌面进程里会抛，兜住之后退回默认渐变，别整张卡变空白。 */
    private fun applyImage(views: RemoteViews, viewId: Int, uri: Uri) {
        val ok = runCatching { views.setImageViewUri(viewId, uri) }.isSuccess
        if (!ok) views.setImageViewResource(viewId, R.drawable.widget_d_fallback)
    }

    /** 从 "316km" / "316" / "316 km" 里取出数字部分；取不到返回 null。 */
    private fun numberText(raw: String?): String? =
        raw?.let { Regex("\\d+").find(it)?.value }

    /**
     * 续航单元格里那一行「316km」：数字自己拼单位。
     * 直接拼接接口原文不行 —— 它有时带空格、有时已经带了 km，拼出来会变成「316km km」。
     */
    private fun kmText(raw: String?): String = numberText(raw)?.let { "${it}km" } ?: "--"

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
        val width = pick(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            320,
        )
        val height = pick(
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            150,
        )
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
