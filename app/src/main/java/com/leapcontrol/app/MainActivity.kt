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
        // 注意：这里**不**主动切换显示模式。
        //
        // 早先的 DisplayRefresh.apply() 会用 preferredDisplayModeId「硬切显示模式」，
        // 每次 onResume / onWindowFocusChanged 都会重新执行一次，导致系统在滑动过程中
        // 反复做「显示模式切换 → VSYNC 时钟重同步」，这就是首页滑动掉帧的真正元凶。
        //
        // 现在只走 DisplayRefresh.applyMaxFrameRate()：用 preferredRefreshRate 声明
        // 「本窗口希望按最高帧率出帧」，不指定显示模式 id，也就不会触发 VSYNC 重同步。
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
        // 声明目标刷新率。放在 post 里等窗口真正 attach 到 Surface 之后再做，
        // 每次 onResume 只声明一次 —— 反复重设等于反复和系统协商，反而引入抖动。
        window.decorView.post { DisplayRefresh.applyMaxFrameRate(this) }
    }

    /** 进入后台：暂停动态背景，避免不可见时继续解码耗电。 */
    override fun onPause() {
        super.onPause()
        ForegroundSignal.emitPause()
    }
}
