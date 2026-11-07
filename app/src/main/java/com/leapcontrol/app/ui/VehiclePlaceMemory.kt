package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import com.leapcontrol.app.VehicleLocationRules
import com.leapcontrol.app.VehicleLocationStore

/**
 * 车辆位置卡的**进程内**结果缓存，只为一件事服务：**切回首页时不要闪**。
 *
 * ## 为什么需要它
 * 首页用 `when(tab)` 切页，离开首页整棵子树就被丢掉，回来时是**新建**的。
 * 于是 [rememberVehiclePlace] 的 `produceState` 又从 `VehiclePlaceUi.Loading` 起手 ——
 * 方图里先画一帧「加载中」占位，等磁盘缓存读完（哪怕只有几十毫秒）才换成地图。
 * 用户看到的就是「每次切回首页，位置卡片闪一下」。
 *
 * 磁盘缓存本身已经很快，问题不在它慢，而在**首帧值一开始就是错的**：明明手上
 * 还有一份刚画过的图，却从「什么都没有」开始。所以这里只补一层内存缓存，
 * 让首帧直接拿到上一次的结果。
 *
 * ## 为什么按 4 个槽存
 * 图有两套尺寸（首页缩略图 / 找车页大图）× 深浅两种配色，四张图本来就是磁盘上
 * 四个互不覆盖的文件（见 `VehicleLocationStore.mapName`）。内存里同样分槽，
 * 从找车页退回首页时两份都还在，各自都不用重来一遍。
 *
 * ## 边界
 * - 只存引用，不拷贝 Bitmap；四张图加起来约 2MB 量级，比每次重建便宜得多。
 * - 读写都在主线程（组合期 + `produceState`），只做引用赋值，无需加锁。
 * - 换车 / 退出登录时必须清（见 [clear]），否则会有人看到上一位车主的停车位置。
 */
internal object VehiclePlaceMemory {

    /** 4 个槽：full(2) × dark(1)。 */
    private val slots = arrayOfNulls<VehiclePlaceUi>(4)

    /** 哪些槽已经从磁盘读过一次了：同一槽只读一次，之后由 [get] 命中。 */
    private val seededFromDisk = BooleanArray(4)

    private fun slot(full: Boolean, dark: Boolean): Int = (if (full) 2 else 0) + (if (dark) 1 else 0)

    fun get(full: Boolean, dark: Boolean): VehiclePlaceUi? = slots[slot(full, dark)]

    /** 只记「有内容可画」的结果：Loading 等于没记，写进去只会让下次又闪一次。 */
    fun put(full: Boolean, dark: Boolean, ui: VehiclePlaceUi) {
        if (ui === VehiclePlaceUi.Loading) return
        slots[slot(full, dark)] = ui
    }

    /**
     * **冷启动**的首帧兜底：内存里什么都没有时，同步把磁盘上那份读出来。
     *
     * 为什么可以同步：一次 `last.json`（几百字节）+ 一张 288px 的 PNG，实测个位数毫秒；
     * 而它换来的是「卡片第一帧就有图」，而不是先亮一帧「加载中」再换成地图 ——
     * 后者正是用户反馈的「第一次冷启动小地图闪一下」。每个槽只读一次，读完就进内存，
     * 之后切页都走 [get]，不会再碰磁盘。
     *
     * 图必须确实是画这个点的（[VehicleLocationRules.mapMatches]）：只比坐标不够，
     * 拉图失败时元数据照样会更新，磁盘上那张图可能还是上一处的。宁可不给图，
     * 也不能把别处的地图钉在当前地址上。
     */
    fun seedFromDisk(context: Context, full: Boolean, dark: Boolean): VehiclePlaceUi? {
        val index = slot(full, dark)
        slots[index]?.let { return it }
        if (seededFromDisk[index]) return null
        seededFromDisk[index] = true

        val place = runCatching { VehicleLocationStore.load(context) }.getOrNull() ?: return null
        val map = if (VehicleLocationRules.mapMatches(place, place.latitude, place.longitude)) {
            runCatching {
                VehicleLocationStore.loadMap(context, full = full, dark = dark)
            }.getOrNull()
        } else {
            null
        }
        val restored = VehiclePlaceUi.Ready(
            map = map,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            // 这是「上次记下的」，不是刚刚的：把时刻一并带出来，卡片上会显示成「更新于」，
            // 与「车况这一帧没有坐标」那条分支的语义保持一致
            cachedAt = place.seenAt.takeIf { it > 0L } ?: place.reportedAt.takeIf { it > 0L },
        )
        slots[index] = restored
        return restored
    }

    fun clear() {
        slots.fill(null)
        seededFromDisk.fill(false)
    }
}

/**
 * 这份内存结果能不能直接顶上来当首帧。
 *
 * [VehiclePlaceUi.Ready] 带着坐标，所以要多问一句「是不是同一个地方」：
 * 车真的挪窝了，就该重新加载，而不是先亮一张旧图再换掉（那同样是一次闪）。
 * 其余几种状态不带位置，先亮出来没有错。
 */
internal fun VehiclePlaceUi.fitsCoords(
    hasCoords: Boolean,
    latitude: Double?,
    longitude: Double?,
): Boolean = when (this) {
    VehiclePlaceUi.Loading -> false
    is VehiclePlaceUi.Ready -> !hasCoords || (
        latitude != null && longitude != null &&
            VehicleLocationRules.distanceKm(this.latitude, this.longitude, latitude, longitude) <=
            VehicleLocationRules.SAME_SPOT_RADIUS_KM
        )
    VehiclePlaceUi.NoLocation,
    VehiclePlaceUi.NoKey,
    VehiclePlaceUi.Failed -> true
}

/**
 * 停车位照片缩略图的内存缓存，同 [VehiclePlaceMemory] 的理由：
 * 切回首页时右上角那个按钮不该从「相机图标」闪成「照片」。
 *
 * 按解码尺寸分键：首页卡 240 像素、找车页 1400，两张图不能互相当缓存用。
 */
internal object ParkingThumbMemory {
    private val entries = HashMap<Int, Bitmap>()

    fun get(size: Int): Bitmap? = entries[size]

    /** [bitmap] 为 null 表示「确实没有照片」，要连旧的那份一起丢掉。 */
    fun put(size: Int, bitmap: Bitmap?) {
        if (bitmap == null) entries.remove(size) else entries[size] = bitmap
    }

    fun clear() {
        entries.clear()
    }
}
