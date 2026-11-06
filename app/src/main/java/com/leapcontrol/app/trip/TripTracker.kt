package com.leapcontrol.app.trip

import android.content.Context
import com.leapcontrol.app.AmapWebApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 行程采集的进程级入口。
 *
 * 只在 `AppRuntime.refreshStatus()` 成功后被调用 —— **不新增任何一次车况请求**，
 * 也不注册后台闹钟 / 前台服务。StateFlow 是进程级的，跨页返回不会丢数据。
 *
 * 起终点中文地址是唯一会额外联网的地方：一段行程收尾时，用用户自填的高德 Web Key
 * 对起终点各反查一次并缓存；没填 Key 或失败就保持「起点 / 终点」占位，
 * 绝不在行驶途中跟着坐标反复请求（那会吃额度、也没意义）。
 */
object TripTracker {
    private val _flow = MutableStateFlow<TripOverview?>(null)
    val flow: StateFlow<TripOverview?> = _flow.asStateFlow()

    /** 进程级 scope：地址补全不能挂在会被取消的页面协程上。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * 落盘专用 scope：**单线程**排队，保证写入顺序，同时把 JSON 序列化与文件读写挪出主线程。
     *
     * 之前每次开始 / 结束 / 删除 / 合并都在主线程 `TripStore.save()`：它要先把整份
     * trips.json 读回来、合并这一辆车、再整份写出去。90 天的采样点攒到几 MB 时，
     * 点「结束行程」要卡住几百毫秒到一两秒 —— 用户看到的就是「点了没反应」。
     * 内存里的 [_flow] 仍然是同步更新的，界面立刻切换；落盘慢一点不影响正确性。
     */
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val lock = Any()
    private var loadedVin: String? = null

    /** 异步落盘：只写 [_flow] 当前这份快照，调用方保证快照是最新的。 */
    private fun persist(context: Context, state: TripOverview) {
        storeScope.launch { runCatching { TripStore.save(context, state) } }
    }


    fun load(context: Context, vin: String, batteryKwh: Double? = null) {
        synchronized(lock) {
            loadedVin = vin
            val stored = runCatching { TripStore.load(context, vin) }
                .getOrElse { TripOverview(vin) }
            // 容量存在数据文件里，是为了让「收尾那一刻用的是哪个容量」可追溯；
            // 用户改了设置就以设置为准，历史行程里的数字按新容量重算（容量是车的常量，
            // 改正它是纠错，不是篡改数据）。
            val merged = if (stored.batteryKwh == batteryKwh) stored
            else stored.copy(batteryKwh = batteryKwh)
            _flow.value = TripRules.reprice(merged)
        }
    }

    /**
     * 用户改了电池容量：立刻作用到进程内状态并落盘。
     *
     * 只改容量、没有新采样点时也要写盘 —— 否则下次读盘又把旧容量读回来，
     * 新填的度数永远算不出来。
     */
    fun setBatteryKwh(context: Context, kwh: Double?) {
        synchronized(lock) {
            val state = _flow.value ?: return
            if (state.batteryKwh == kwh) return
            // 填了容量就把历史里「有电量差但没度数」的段一起补算：用户填的是车的常量，
            // 那些段本来就该有度数，不该逼他重跑一趟。
            val next = TripRules.reprice(state.copy(batteryKwh = kwh))
            _flow.value = next
            persist(context, next)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { TripStore.clear(context) }
            loadedVin = null
            _flow.value = TripOverview()
        }
    }

    /**
     * @param autoStart 用户在行程页开了「自动开始记录」：连续两帧行驶就自己开一段。
     *   默认 false，只能手动点开始（见 [TripRules.ingest]）。
     */
    fun observe(
        context: Context,
        vin: String,
        amapKey: String,
        batteryKwh: Double?,
        sample: TripSample,
        autoStart: Boolean = false,
    ) {
        if (vin.isBlank()) return
        var justClosed: TripRecord? = null
        synchronized(lock) {
            if (loadedVin != vin) {
                loadedVin = vin
                _flow.value = runCatching { TripStore.load(context, vin) }
                    .getOrElse { TripOverview(vin) }
            }
            val stored = _flow.value ?: TripOverview(vin)
            // 设置里改了电池容量要能立刻生效：只改容量、没有新采样点时也得落盘，
            // 否则下次读盘又把旧容量读回来，kWh 永远按旧值算。
            val before = if (stored.batteryKwh == batteryKwh) stored
            else TripRules.reprice(stored.copy(batteryKwh = batteryKwh))
            val capacityChanged = before !== stored
            val known = before.trips.mapTo(HashSet()) { it.id }
            val next = TripRules.prune(
                TripRules.ingest(before, sample, autoStart),
                sample.at,
            )
            if (next == before && !capacityChanged) return
            _flow.value = next
            persist(context, next)
            justClosed = next.trips.lastOrNull()?.takeIf { it.id !in known }
        }
        val closed = justClosed ?: return
        if (amapKey.isBlank()) return
        scope.launch { attachLabels(context, vin, amapKey, closed) }
    }

    /**
     * 用户手动点「开始行程」。
     *
     * 行程只由用户开始（自动判据会被车端的挡位噪声骗到，一打开 App 就「正在记录」）；
     * 结束仍是手动 / 锁车 / 停车宽限三路兜底。开段立刻落盘：用户点完就把 App 切走，
     * 回来还得是「正在记录」。
     */
    fun start(
        context: Context,
        vin: String,
        sample: TripSample,
    ): TripStartResult {
        if (vin.isBlank()) return TripStartResult.NO_STATUS
        var result = TripStartResult.NO_STATUS
        synchronized(lock) {
            if (loadedVin != vin) {
                loadedVin = vin
                _flow.value = runCatching { TripStore.load(context, vin) }
                    .getOrElse { TripOverview(vin) }
            }
            val state = _flow.value ?: TripOverview(vin)
            val (next, r) = TripRules.start(state, sample)
            result = r
            if (r == TripStartResult.STARTED) {
                _flow.value = next
                persist(context, next)
            }
        }
        return result
    }

    /**
     * 用户手动点「结束行程」。
     *
     * 只收尾、不联网拉车况：需要的点已经在 `open` 里了，再拉一次反而可能把停车帧
     * 追加进去。同步写盘并刷新 StateFlow，首页卡和二级页会同时切到「已结束」视图。
     * 失败（点太少 / 太短 / 算不出里程）时**保留 open**，由 UI 给具体原因。
     */
    fun finishCurrent(
        context: Context,
        vin: String,
        amapKey: String,
        now: Long = System.currentTimeMillis(),
    ): TripCloseResult {
        if (vin.isBlank()) return TripCloseResult.NO_OPEN
        var result = TripCloseResult.NO_OPEN
        var closed: TripRecord? = null
        synchronized(lock) {
            if (loadedVin != vin) {
                loadedVin = vin
                _flow.value = runCatching { TripStore.load(context, vin) }
                    .getOrElse { TripOverview(vin) }
            }
            val state = _flow.value ?: TripOverview(vin)
            val (next, r) = TripRules.closeOpen(state, now)
            result = r
            if (r == TripCloseResult.SAVED) {
                _flow.value = next
                persist(context, next)
                closed = next.trips.lastOrNull()
            }
        }
        val record = closed
        if (record != null && amapKey.isNotBlank()) {
            scope.launch { attachLabels(context, vin, amapKey, record) }
        }
        return result
    }

    /**
     * 用户确认「这趟不要了」：清掉进行中的一段，不写历史。
     *
     * 只服务「不到 30 秒 / 车机没读到位移」这种本来就建不了档的段 —— 用户点了结束
     * 就必须真的结束，不能因为「不够建档门槛」而一直卡在记录中。
     * 返回 false = 当前没有进行中的行程（可能已经被锁车自动收尾了）。
     */
    fun discardCurrent(context: Context, vin: String): Boolean {
        if (vin.isBlank()) return false
        var dropped = false
        synchronized(lock) {
            if (loadedVin != vin) {
                loadedVin = vin
                _flow.value = runCatching { TripStore.load(context, vin) }
                    .getOrElse { TripOverview(vin) }
            }
            val state = _flow.value ?: TripOverview(vin)
            if (state.open.isEmpty()) return false
            val next = TripRules.discardOpen(state)
            _flow.value = next
            persist(context, next)
            dropped = true
        }
        return dropped
    }

    /**
     * 删除一段历史行程。**只删已收尾的段**，进行中的那段不在清单里，删不到。
     * 返回 false = 没找到（已被 90 天裁剪 / 换了车），UI 不该说「已删除」。
     */
    fun delete(context: Context, vin: String, id: Long): Boolean {
        if (vin.isBlank()) return false
        synchronized(lock) {
            val state = _flow.value ?: return false
            if (state.vin != vin) return false
            val next = TripRules.delete(state, id)
            if (next === state) return false
            _flow.value = next
            persist(context, next)
        }
        return true
    }

    /**
     * 把用户勾选的几段合并成一段。
     *
     * 合并后起点沿用最早那段，所以起终点地址大概率已经在；缺的话顺手补一次
     * （终点换成了最后一段的终点，本来就可能是新地址）。
     */
    fun merge(
        context: Context,
        vin: String,
        amapKey: String,
        ids: Collection<Long>,
    ): TripMergeResult {
        if (vin.isBlank()) return TripMergeResult.NOT_FOUND
        var result = TripMergeResult.NOT_FOUND
        var merged: TripRecord? = null
        synchronized(lock) {
            val state = _flow.value ?: return TripMergeResult.NOT_FOUND
            if (state.vin != vin) return TripMergeResult.NOT_FOUND
            // 合并后的 id 是最早那段的 id，先记下来，等下从这个位置取回结果去补地址
            val targetId = state.trips.filter { it.id in ids }
                .minByOrNull { it.startAt }?.id
            val (next, r) = TripRules.merge(state, ids)
            result = r
            if (r == TripMergeResult.MERGED) {
                _flow.value = next
                persist(context, next)
                merged = next.trips.firstOrNull { it.id == targetId }
            }
        }
        val record = merged
        if (record != null && amapKey.isNotBlank()) {
            scope.launch { attachLabels(context, vin, amapKey, record) }
        }
        return result
    }

    private fun attachLabels(context: Context, vin: String, key: String, record: TripRecord) {
        if (record.startLabel != null && record.endLabel != null) return
        val start = usable(record.startLat, record.startLng)?.let { (lat, lng) ->
            runCatching { AmapWebApi.reverseGeocode(key, lat, lng)?.formatted }.getOrNull()
        }?.let { TripFormat.shortPlace(it) }
        val end = usable(record.endLat, record.endLng)?.let { (lat, lng) ->
            runCatching { AmapWebApi.reverseGeocode(key, lat, lng)?.formatted }.getOrNull()
        }?.let { TripFormat.shortPlace(it) }
        if (start == null && end == null) return
        synchronized(lock) {
            val state = _flow.value ?: return@synchronized
            if (state.vin != vin) return@synchronized
            val updated = state.copy(
                trips = state.trips.map { trip ->
                    if (trip.id != record.id) trip
                    else trip.copy(
                        startLabel = trip.startLabel ?: start,
                        endLabel = trip.endLabel ?: end,
                    )
                },
            )
            _flow.value = updated
            persist(context, updated)
        }
    }

    private fun usable(lat: Double?, lng: Double?): Pair<Double, Double>? {
        if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite()) return null
        if (lat == 0.0 && lng == 0.0) return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }
}
