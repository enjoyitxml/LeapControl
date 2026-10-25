package com.leapbuddy.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 崩溃时展示堆栈的页面（纯 View，不依赖 Compose，保证一定能起来）。 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(CrashGuard.EXTRA_TRACE)
            ?: runCatching { filesDir.resolve("crash.txt").readText() }.getOrNull()
            ?: "没有捕获到堆栈信息"

        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(
            TextView(this).apply {
                text = "应用崩溃了"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        val btn = Button(this).apply { text = "复制堆栈" }
        btn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }
        root.addView(btn)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        scroll.addView(
            TextView(this).apply {
                text = trace
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextIsSelectable(true)
                setPadding(0, pad, 0, pad)
            },
        )
        root.addView(scroll)

        setContentView(root)
    }
}
