package com.leapcontrol.app

import android.os.Bundle
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
        DisplayRefresh.apply(window)
        setContent {
            LeapControlTheme {
                LeapControlApp()
            }
        }
    }

    /** 冷启动与从后台切回都走 onResume，统一触发一次车况刷新。 */
    override fun onResume() {
        super.onResume()
        // 从后台回来时窗口的显示模式可能已被系统重置，每次回前台都重新申请一遍
        DisplayRefresh.apply(window)
        ForegroundSignal.emitResume()
        // 回到前台时让控制中心磁贴刷新一次文案与激活态
        QuickTiles.refreshAll(this)
    }

    /**
     * 拿到焦点时再申请一次刷新率。
     *
     * 分屏、小窗、下拉通知栏、被别的应用短暂压住之后，系统会把显示模式打回默认档；
     * onResume 不一定每次都来，onWindowFocusChanged 却几乎必来，补这一处能减少
     * 「用着用着就掉回 60 帧」的体感。
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) DisplayRefresh.apply(window)
    }

    /** 进入后台：暂停动态背景，避免不可见时继续解码耗电。 */
    override fun onPause() {
        super.onPause()
        ForegroundSignal.emitPause()
    }
}
