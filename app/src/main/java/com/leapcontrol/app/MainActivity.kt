package com.leapcontrol.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leapcontrol.app.tiles.QuickTiles
import com.leapcontrol.app.ui.ForegroundSignal
import com.leapcontrol.app.ui.LeapControlApp
import com.leapcontrol.app.ui.theme.LeapControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate(window)
        setContent {
            LeapControlTheme {
                LeapControlApp()
            }
        }
    }

    /**
     * 申请 120Hz 高刷新率。
     *
     * 做法是给窗口挑一个「分辨率不变、刷新率最高但不超过 120Hz」的显示模式
     * （preferredDisplayModeId，API 23+）。之所以限 120 而不是无脑取最高：
     * 部分 144Hz 机型在同分辨率下的最高档由系统策略决定，主动锁到上限反而容易掉帧发热。
     *
     * 最终仍会以系统策略为准（省电模式、厂商限制会把它压回 60Hz）。
     */
    private fun requestHighRefreshRate(window: Window) {
        runCatching {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.context.display
            } else {
                @Suppress("DEPRECATION")
                window.windowManager.defaultDisplay
            } ?: return@runCatching

            val modes = display.supportedModes?.takeIf { it.isNotEmpty() } ?: return@runCatching
            val currentRate = runCatching { display.refreshRate }.getOrDefault(60f)
            val target = MAX_REFRESH_RATE
            // 只挑刷新率更高的档；并且保持物理分辨率不变，避免为了高频牺牲清晰度
            val best = modes.asSequence()
                .filter { it.refreshRate <= target + 0.5f }
                .filter { it.refreshRate > currentRate + 0.5f }
                .filter { it.physicalWidth >= display.mode.physicalWidth && it.physicalHeight >= display.mode.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: return@runCatching

            val attrs = window.attributes
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
            Log.i(TAG, "已申请 ${best.refreshRate}Hz 刷新率（原 ${currentRate}Hz）")
        }
    }

    private companion object {
        const val TAG = "LeapControl"
        /** App 期望的刷新率上限，超过这个值的模式不主动去挑。 */
        const val MAX_REFRESH_RATE = 120f
    }

    /** 冷启动与从后台切回都走 onResume，统一触发一次车况刷新。 */
    override fun onResume() {
        super.onResume()
        ForegroundSignal.emitResume()
        // 回到前台时让控制中心磁贴刷新一次文案与激活态
        QuickTiles.refreshAll(this)
    }
}
