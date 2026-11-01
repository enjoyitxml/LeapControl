package com.leapcontrol.app.ui

/**
 * 首页重组次数计数器（只用于调试浮层，不参与任何业务逻辑）。
 *
 * 定位「滑动掉帧」时最需要分清的一件事是：**主线程到底在重组，还是在绘制**。
 *  · 重组频率高（每秒几十次）→ Compose 侧反复遍历组件树，要去查哪个 State 在每帧变；
 *  · 重组几乎为零、但帧耗时仍然高 → 问题在绘制/合成，即 GPU 填充率或图层数量。
 *
 * 用普通 Int 自增而不是 Compose State：写它绝不能反过来触发重组，
 * 否则探针自己就成了污染源。读取只在调试浮层里按秒取差值。
 */
object RecomposeMeter {
    /** 累计重组次数。丢一次自增在并发场景下无害，这里也就不加锁。 */
    @Volatile
    var total: Long = 0L
        private set

    fun mark() {
        total++
    }
}
