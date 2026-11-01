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
        // 注意：这里**不再**主动申请/切换显示模式刷新率。
        //
        // 早先的 DisplayRefresh.apply() 会用 preferredDisplayModeId「硬切显示模式」，
        // 每次 onResume / onWindowFocusChanged 都会重新执行一次，导致系统在滑动过程中
        // 反复做「显示模式切换 → VSYNC 时钟重同步」，这就是首页滑动掉帧的真正元凶：
        // 渲染很轻（CPU 1ms / GPU 3ms）、重组为 0，掉帧却严重，因为每帧节奏被模式切换打断。
        // 用户已经在系统里把刷新率设成「固定 120Hz」，系统会自行给足高刷，
        // App 什么都不用做 —— 反复请求反而和系统打架。
        setContent {
            LeapControlTheme {
                LeapControlApp()
            }
        }
    }

    /** 冷启动与从后台切回都走 onResume，统一触发一次车况刷新。 */
    override fun onResume() {
        super.onResume()
        ForegroundSignal.emitResume()
        // 回到前台时让控制中心磁贴刷新一次文案与激活态
        QuickTiles.refreshAll(this)
    }

    /** 进入后台：暂停动态背景，避免不可见时继续解码耗电。 */
    override fun onPause() {
        super.onPause()
        ForegroundSignal.emitPause()
    }
}
