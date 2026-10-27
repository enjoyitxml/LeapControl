package com.leapcontrol.app.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 应用回到前台事件广播（MainActivity.onResume 发出）。
 * replay=1 保证冷启动时先于首次组合发出的事件不会被订阅方漏掉。
 */
object ForegroundSignal {
    private val _events = MutableSharedFlow<Unit>(replay = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /** 网络恢复事件（断网时刷新被跳过，恢复后自动补一次刷新）。 */
    private val _networkEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkEvents: SharedFlow<Unit> = _networkEvents.asSharedFlow()

    /** 进入后台事件（MainActivity.onPause 发出，用于暂停背景视频）。 */
    private val _pauseEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pauseEvents: SharedFlow<Unit> = _pauseEvents.asSharedFlow()

    fun emitResume() {
        _events.tryEmit(Unit)
    }

    fun emitPause() {
        _pauseEvents.tryEmit(Unit)
    }

    fun emitNetworkRestored() {
        _networkEvents.tryEmit(Unit)
    }
}
