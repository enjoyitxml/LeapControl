package com.leapbuddy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leapbuddy.app.tiles.QuickTiles
import com.leapbuddy.app.ui.ForegroundSignal
import com.leapbuddy.app.ui.LeapBuddyApp
import com.leapbuddy.app.ui.theme.LeapBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeapBuddyTheme {
                LeapBuddyApp()
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
}
