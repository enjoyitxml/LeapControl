package com.leapcontrol.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.leapcontrol.app.SessionStore
import java.io.File

/**
 * 车模图的**冷启动预热**。
 *
 * ## 为什么需要它
 * 车模图本来有两层缓存：`AppRuntime` 里的内存热缓存 + `filesDir/car_model_<vin>.img`。
 * 内存缓存是进程级的，**冷启动必然是空的**；于是首页第一帧 `cachedDisplayCarBitmap()`
 * 什么也拿不到，车模区先空着，等 `LaunchedEffect` 里异步解码完才冒出来 ——
 * 用户看到的就是「第一次冷启动时车模闪一下」。
 *
 * 磁盘缓存本身够快，问题只在「第一帧手上还没有它」。所以这里在进程一启动就用后台线程
 * 把那张图解码进内存，首页组合时直接命中；[AppRuntime] 的两层缓存都空时才读这里兜底。
 *
 * ## 边界
 * - 只存引用，不拷贝；一张车模约 2MB，进程本来也要为它留这份内存。
 * - 读的是「最终显示的那张」：有自定义车模用自定义，否则用官方缓存，顺序与
 *   [AppRuntime.displayCarBitmap] 完全一致，否则预热出来的图会和随后刷新的图不一样，
 *   反而多闪一次。
 * - 按 VIN 校验：换车、换账号后旧图不会被当成当前车辆的车模。
 */
internal object CarImagePreload {

    @Volatile
    private var bitmap: Bitmap? = null

    @Volatile
    private var vin: String? = null

    /** 只给当前车辆用：PEEK 时要连 VIN 一起比。 */
    fun peek(currentVin: String): Bitmap? =
        bitmap?.takeIf { !it.isRecycled && vin == currentVin }

    fun clear() {
        bitmap = null
        vin = null
    }

    /**
     * 预热一次。幂等：同一台车已经有图就直接返回；换了车才重新解码。
     *
     * 调用点：[com.leapcontrol.app.LeapApplication]（冷启动）与登录成功之后。
     * 放在后台线程，绝不能在主线程调 —— 解码一张千像素 PNG 要几十毫秒。
     */
    fun warm(context: Context) {
        val app = context.applicationContext
        val targetVin = runCatching { SessionStore(app).load().selectedVin }.getOrNull().orEmpty()
        if (targetVin.isBlank()) return
        if (vin == targetVin && bitmap != null) return

        Thread({
            // 两个入口（Application / 登录成功）可能同时叫进来，锁里再判一次，
            // 避免同一张图被解码两遍
            synchronized(this) {
                if (vin == targetVin && bitmap != null) return@synchronized
                val decoded = runCatching { decode(app, targetVin) }.getOrNull()
                    ?: return@synchronized
                bitmap = decoded
                vin = targetVin
            }
        }, "car-image-preload").apply {
            isDaemon = true
            // 让位给真正影响首帧的 UI 线程：错开几十毫秒也来得及，
            // 首页要等登录校验 + 车况请求，远不止这点时间
            priority = Thread.MIN_PRIORITY
        }.start()
    }

    /** 自定义车模优先，其次官方图缓存；两者都没有返回 null（首页就不占位）。 */
    private fun decode(context: Context, vin: String): Bitmap? =
        CarModelStore.load(context, vin)
            ?: BitmapFactory.decodeFile(
                File(context.filesDir, "car_model_$vin.img").absolutePath,
            )
}
