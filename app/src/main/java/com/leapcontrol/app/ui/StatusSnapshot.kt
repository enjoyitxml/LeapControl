package com.leapcontrol.app.ui

/**
 * 进程内最近一次车况快照。
 *
 * 控制中心磁贴与 App 跑在同一个进程里，没必要为了校准磁贴再发一遍车况请求：
 * App 刚拉到的报文，磁贴这里直接读。
 *
 * 两个使用方：
 * 1. 磁贴 `onStartListening` —— 控制中心一展开就能拿到内存里的最新值，不用等网络；
 * 2. [AppRuntime] —— 每次拉取成功后写入，冷启动读磁盘缓存时也写（带原始时间戳）。
 *
 * 只留一份、不按 VIN 分：磁贴下发指令前会用 `SessionStore` 校验当前车辆，
 * 车换了之后第一件事就是重新拉车况，旧快照会在 [get] 的新鲜度窗口里自然失效。
 */
object StatusSnapshot {

    @Volatile
    private var value: CarStatus? = null

    @Volatile
    private var at: Long = 0L

    /** 记录一次车况。[capturedAt] 是该报文真正拿到的时间，冷启动读磁盘缓存时要传缓存时间而不是现在。 */
    fun put(status: CarStatus?, capturedAt: Long = System.currentTimeMillis()) {
        value = status
        at = if (status == null) 0L else capturedAt
    }

    /**
     * 读快照。
     *
     * @param maxAgeMs 超过这个年龄就当作没有（磁盘缓存可能是几小时前的，不能拿来校状态）
     * @param since 只接受**晚于**这个时刻的快照；传「用户最后一次点磁贴的时间」，
     *   就能保证不会拿一条比用户意图更旧的报文去覆盖它
     */
    fun get(
        maxAgeMs: Long = 30_000L,
        since: Long = 0L,
        now: Long = System.currentTimeMillis(),
    ): CarStatus? {
        val snapshot = value ?: return null
        if (at <= 0L) return null
        if (at < since) return null
        if (now - at > maxAgeMs) return null
        return snapshot
    }
}
