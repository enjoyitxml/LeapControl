package com.leapcontrol.app.widget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.leapcontrol.app.ui.CarStatus

/**
 * **仅 debug 包**：把小组件的 RemoteViews 真实渲染出来，用来截图核对版式。
 *
 * 小组件只能由桌面宿主添加，模拟器里没有澎湃桌面，所以做一个临时页把同一份
 * RemoteViews 铺到指定 dp 尺寸上：`adb shell am start -n com.leapcontrol.app/.widget.WidgetPreviewActivity`
 *
 * release 包里不存在这个类（放在 src/debug 源集）。
 */
class WidgetPreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val host = FrameLayout(this)
        host.setBackgroundColor(0xFF9AAEC6.toInt()) // 假装桌面壁纸

        val column = FrameLayout(this)
        column.setBackgroundColor(0x00000000)
        val colParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        colParams.topMargin = (40 * density).toInt()
        host.addView(column, colParams)

        // 两种典型尺寸：2 行标准高度 110dp、常见 4x2 实际高度 150dp
        var top = 0
        listOf(250 to 110, 330 to 150).forEach { (w, h) ->
            val views = VehicleWidget.buildViews(
                context = this,
                status = fakeStatus(),
                updatedAt = System.currentTimeMillis(),
                busy = null,
                signedIn = true,
            )
            val card = views.apply(this, FrameLayout(this))
            val lp = FrameLayout.LayoutParams((w * density).toInt(), (h * density).toInt())
            lp.leftMargin = (24 * density).toInt()
            lp.topMargin = (top * density).toInt()
            column.addView(card, lp)

            val tag = TextView(this).apply {
                text = "${w}x${h}dp"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 10f
            }
            val tp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            tp.leftMargin = (24 * density).toInt()
            tp.topMargin = ((top - 16) * density).toInt()
            host.addView(tag, tp)

            top += h + 34
        }

        setContentView(host)
    }

    private fun fakeStatus() = CarStatus(
        soc = 68,
        range = "337 km",
        electricRange = "337 km",
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = "12,345 km",
        interiorTemp = "22.5",
        locked = true,
        acOn = false,
        trunkOpen = false,
    )
}
