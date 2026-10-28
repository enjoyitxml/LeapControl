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
 *
 * ## 版式：严格四宫格
 * 左上「车标 + 车名」／右上「车模」／左下「续航 + 进度条 + 更新时间」／右下「五个按键（3+2）」。
 * 车模用 `fitCenter` 摆在右上格里，所以**车模正中心恒等于卡片 75% / 25% 那个点** ——
 * 换任何一张自定义壁纸，落点都可预期，不会出现壁纸主体被车模压住。
 *
 * ## 颜色
 * 默认跟着系统深浅走（浅底深字 / 深底浅字），三组颜色（按钮 / 文字 / 车标）都可以在
 * 个性化里单独覆盖（[WidgetStyle]）。图标只有一套矢量图，颜色一律靠 `setColorFilter` 现刷，
 * 所以不必为夜间、开启态各存一份副本；也**必须每帧都刷**，否则用户把颜色改回「自动」时
 * 会残留上一次的滤镜。
 *
 * ## 尺寸自适应
 * 小组件在桌面上被拉伸 / 压缩后，系统只给 `min/max width/height` 这一组 dp 值，
 * RemoteViews 也不会自动缩放字号。所以 [buildViews] 按实际尺寸重设一遍字号
 * （[applyTextScale]），图标则交给 `match_parent + fitCenter` 的 ImageView 自己缩放。
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

    // 日间：深色字压在浅色卡上
    private const val COLOR_TEXT = 0xFF12141A.toInt()
    private const val COLOR_SUB = 0xFF8A9099.toInt()
    private const val COLOR_UNIT = 0xFF6C7480.toInt()
    private const val COLOR_WARN = 0xFFD98324.toInt()
    private const val COLOR_ACTIVE = 0xFF1FA45B.toInt()
    private const val COLOR_LOGO = 0xFF12141A.toInt()

    // 夜间：浅色字压在深色卡上（对比度按 WCAG 正文 4.5:1 取的值）
    private const val COLOR_TEXT_DARK = 0xFFF2F5FA.toInt()
    private const val COLOR_SUB_DARK = 0xFFA9B3C1.toInt()
    private const val COLOR_UNIT_DARK = 0xFFA3AEBE.toInt()
    private const val COLOR_WARN_DARK = 0xFFFFB454.toInt()
    private const val COLOR_ACTIVE_DARK = 0xFF3DD68C.toInt()
    private const val COLOR_LOGO_DARK = 0xFFF4F7FB.toInt()

    // 底部按键图标：默认沿用「之前的方案」—— 日间深色、夜间浅色，和 ic_qc_* 的原色一致。
    // 不做灰化：线稿图标本身已经够轻，再压成灰色会显得没质感（用户明确要求回退）。
    // 需要改色（比如换了自定义壁纸）时，用个性化里的「按钮颜色」覆盖，见 WidgetStyle。
    private const val COLOR_ICON = 0xFF12141A.toInt()
    private const val COLOR_ICON_DARK = 0xFFF4F7FB.toInt()

    /**
     * 单个按键图标的「内边距补偿」（dp，左/上/右/下）。
     *
     * 五个图形形状差别太大，只把盒子做成一样大是**看着不齐**的：
     * - 锁和后备箱都是瘦高的方块 + 顶部一条细线，重心在上方，撑满盒子才够大；
     * - 空调是四叶风扇，闭合圆形，同样的高度下面积最大，看着比别的都胖一圈 → 四面各多收 1dp；
     * - 鸣笛是「车 + 声波」，实体全压在底部，盒子一样高的时候视觉重心明显下沉，
     *   像被人按着头 → 上下不对称（上 0、下 5），整体往上提 2.5dp。
     *
     * 现在每格只有下半区的一半高（≈33dp @150dp 卡片），所以补偿量比上一版整体收小，
     * 否则小图标会被 padding 吃光。
     */
    private data class IconInset(val l: Int, val t: Int, val r: Int, val b: Int)

    private val INSET_IDLE = IconInset(3, 3, 3, 3)
    private val INSET_AC = IconInset(4, 4, 4, 4)
    private val INSET_HORN = IconInset(1, 0, 1, 5)

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
        // 日间 / 夜间两套配色都跟系统深浅模式走（不是跟 App 里的外观设置）
        val night = WidgetStyle.isNight(context)
        val wallpaper = customWallpaper(context)
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
                wallpaperUri = wallpaper,
            )
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    /** 自定义壁纸的 URI；没用自定义、或文件已经不在了，返回 null（落回简约纯色卡）。 */
    private fun customWallpaper(context: Context): Uri? =
        if (WidgetWallpaper.exists(context)) WidgetWallpaper.uri(context) else null

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
        wallpaperUri: Uri? = null,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vehicle)
        val scale = textScale(widthDp, heightDp)
        val density = context.resources.displayMetrics.density

        // ---- 三组可自定义的颜色：设了就用用户的，没设就跟系统深浅 ----
        val toneText = WidgetStyle.tone(context, WidgetStyle.SLOT_TEXT)
        val toneButton = WidgetStyle.tone(context, WidgetStyle.SLOT_BUTTON)
        val toneLogo = WidgetStyle.tone(context, WidgetStyle.SLOT_LOGO)

        val baseText = if (night) COLOR_TEXT_DARK else COLOR_TEXT
        val cTitle = if (toneText != WidgetStyle.AUTO) toneText else baseText
        // 次级信息（km / 更新时间）跟着主文字色走，靠透明度分层，用户只调一个色就够
        val cSub = if (toneText != WidgetStyle.AUTO) withAlpha(toneText, 0x99)
        else if (night) COLOR_SUB_DARK else COLOR_SUB
        val cUnit = if (toneText != WidgetStyle.AUTO) withAlpha(toneText, 0xB3)
        else if (night) COLOR_UNIT_DARK else COLOR_UNIT
        val cWarn = if (toneText != WidgetStyle.AUTO) cTitle else if (night) COLOR_WARN_DARK else COLOR_WARN
        val cActive = if (night) COLOR_ACTIVE_DARK else COLOR_ACTIVE
        val cIcon = if (toneButton != WidgetStyle.AUTO) toneButton
        else if (night) COLOR_ICON_DARK else COLOR_ICON
        val cLogo = if (toneLogo != WidgetStyle.AUTO) toneLogo
        else if (night) COLOR_LOGO_DARK else COLOR_LOGO

        // ---- 外壳：底卡 / 按键水波 ----
        // 底卡永远挂着：它既是「简约纯色背景」，也是根布局 clipToOutline 的轮廓来源
        views.setInt(
            R.id.wg_root,
            "setBackgroundResource",
            if (night) R.drawable.widget_card_dark else R.drawable.widget_card,
        )
        val cellBg = if (night) R.drawable.widget_cell_dark else R.drawable.widget_cell
        listOf(
            R.id.wg_cell_unlock, R.id.wg_cell_lock, R.id.wg_cell_ac,
            R.id.wg_cell_trunk, R.id.wg_cell_horn,
        ).forEach { views.setInt(it, "setBackgroundResource", cellBg) }

        // ---- 自定义壁纸 ----
        // 走 URI：桌面进程自己按原图分辨率解码，绕开 setImageViewBitmap 的 1MB 事务上限
        // （上一版背景发虚就是因为位图只能给到 400px）。圆角由 clipToOutline 现裁，
        // 图片本身存成直角，彻底避免「图片圆角与卡片圆角对不上 → 四个角露白边」。
        if (wallpaperUri != null) {
            views.setViewVisibility(R.id.wg_bg, View.VISIBLE)
            views.setImageViewUri(R.id.wg_bg, wallpaperUri)
            // 壁纸自己的底也是同一张卡（万一某些桌面不认 clipToOutline，仍能盖住白边）
            views.setInt(
                R.id.wg_bg,
                "setBackgroundResource",
                if (night) R.drawable.widget_card_dark else R.drawable.widget_card,
            )
        } else {
            views.setViewVisibility(R.id.wg_bg, View.GONE)
        }

        // ---- 车标：一套深色图，颜色全靠滤镜刷（切回「自动」也不会残留旧色）----
        views.setImageViewResource(R.id.wg_logo, R.drawable.ic_wg_logo)
        views.setInt(R.id.wg_logo, "setColorFilter", cLogo)

        // ---- 车名 ----
        views.setTextViewText(R.id.wg_title, title.ifBlank { "我的零跑" })
        views.setTextColor(R.id.wg_title, cTitle)

        // ---- 续航：左纯电、右燃油 ----
        val extend = status?.isRangeExtender == true
        views.setTextViewText(R.id.wg_ev_range, numberText(status?.electricRange ?: status?.range) ?: "--")
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
            views.setViewVisibility(R.id.wg_fuel_block, View.GONE)
        }

        // ---- 更新时间 / 状态一行（续航下方，左对齐）----
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

        // ---- 五个按键 ----
        // 高亮规则：正在生效的状态才亮绿 ——
        // 车门解锁时「解锁」亮、尾门打开时「后备箱」亮、空调开着时「空调」亮。
        // 上锁 / 鸣笛没有「开着」这一态，永远是常态色（鸣笛是瞬时指令）。
        val unlocked = status?.locked == false
        val acOn = status?.acOn == true
        val trunkOpen = status?.trunkOpen == true

        bindCell(views, R.id.wg_cell_unlock, R.id.wg_icon_unlock, R.drawable.ic_qc_unlock,
            broadcast(context, ACTION_UNLOCK, RC_UNLOCK),
            if (unlocked) cActive else cIcon, INSET_IDLE, density)
        bindCell(views, R.id.wg_cell_lock, R.id.wg_icon_lock, R.drawable.ic_qc_lock,
            broadcast(context, ACTION_LOCK, RC_LOCK), cIcon, INSET_IDLE, density)
        bindCell(views, R.id.wg_cell_ac, R.id.wg_icon_ac, R.drawable.ic_wg_fan,
            broadcast(context, ACTION_TOGGLE_AC, RC_AC),
            if (acOn) cActive else cIcon, INSET_AC, density)
        bindCell(views, R.id.wg_cell_trunk, R.id.wg_icon_trunk, R.drawable.ic_qc_trunk,
            broadcast(context, ACTION_TOGGLE_TRUNK, RC_TRUNK),
            if (trunkOpen) cActive else cIcon, INSET_IDLE, density)
        bindCell(views, R.id.wg_cell_horn, R.id.wg_icon_horn, R.drawable.ic_qc_horn,
            broadcast(context, ACTION_HORN, RC_HORN), cIcon, INSET_HORN, density)

        // 点文字区 / 车模都打开 App（换背景已经改成进「个性化」里选，车模不再兼做按钮）
        openAppIntent(context)?.let {
            views.setOnClickPendingIntent(R.id.wg_tl, it)
            views.setOnClickPendingIntent(R.id.wg_bl, it)
            views.setOnClickPendingIntent(R.id.wg_car, it)
        }

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

    /** 保留原透明度、只换 RGB —— 次级文字靠它从主文字色派生。 */
    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

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
