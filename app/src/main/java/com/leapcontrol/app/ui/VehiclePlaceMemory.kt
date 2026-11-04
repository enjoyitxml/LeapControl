package com.leapcontrol.app.ui

import android.graphics.Bitmap
import com.leapcontrol.app.VehicleLocationRules

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

    private fun slot(full: Boolean, dark: Boolean): Int = (if (full) 2 else 0) + (if (dark) 1 else 0)

    fun get(full: Boolean, dark: Boolean): VehiclePlaceUi? = slots[slot(full, dark)]

    /** 只记「有内容可画」的结果：Loading 等于没记，写进去只会让下次又闪一次。 */
    fun put(full: Boolean, dark: Boolean, ui: VehiclePlaceUi) {
        if (ui === VehiclePlaceUi.Loading) return
        slots[slot(full, dark)] = ui
    }

    fun clear() {
        slots.fill(null)
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
