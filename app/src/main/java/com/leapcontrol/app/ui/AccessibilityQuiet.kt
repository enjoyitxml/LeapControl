package com.leapcontrol.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.View
import android.view.ViewGroup
import android.view.Window

/**
 * 滚动期间临时「静音」Compose 的无障碍语义树刷新。
 *
 * ## 为什么需要它（实测数据，不是猜测）
 *
 * perf_1793544408267 这轮日志把首页卡顿的真凶钉住了：
 *  · 主线程**真实 CPU 24.45ms/帧**（`Debug.threadCpuTimeNanos` 帧间差），
 *    而「画这一帧」的 SYNC+DRAW 只有 2.79ms、重组 0 次、layout 0.08ms、GPU 1.68ms；
 *  · 帧间隔 21.6ms ≈ 24.45ms —— 主线程满负荷，帧间隔就是它跑完一轮的时间；
 *  · 长任务名单里反复出现 `AndroidComposeViewAccessibilityDelegateCompat`（20~24ms/次）。
 *
 * 去看 Compose 1.7.6 的字节码（ui-release.aar → AndroidComposeViewAccessibilityDelegateCompat）：
 * ```
 * isEnabled = accessibilityForceEnabledForTesting
 *          || (accessibilityManager.isEnabled && !enabledServices.isEmpty())
 *
 * onLayoutChange(node) {
 *     currentSemanticsNodesInvalidated = true
 *     if (!isEnabled) return                       // ← 唯一的闸门
 *     notifySubtreeAccessibilityStateChangedIfNeeded(node)   // 塞进 subtreeChangedLayoutNodes
 *     boundsUpdateChannel.trySend(Unit)            // 唤醒 boundsUpdatesEventLoop
 * }
 * ```
 * 也就是说：**只要设备上有一个无障碍服务在跑**（小米常见的语音助手/自动点击/输入法辅助等，
 * 术语上叫 enabledServices 非空），Compose 就会在每帧的布局回调里，把「位置变了的节点」
 * 全部收集起来重算一遍 boundsInScreen（每个节点都要做 localToRoot 的矩阵换算），
 * 再逐节点发无障碍事件。滑动时每帧**所有**节点都在移动 —— 于是每帧都在重算整棵树。
 *
 * 这也解释了那个一直想不通的现象：为什么只有首页卡、所有二级页都流畅、零记还能跑满 120Hz。
 * 首页卡片最密（语义节点几百个），二级页只有几十个 —— 同一份代码，开销差一个数量级。
 * 之前优化的渲染、图层、滚动容器、下拉刷新，全都碰不到这条路径，所以一直「哪儿都不慢但就是掉帧」。
 *
 * ## 做法与边界
 *
 * 只把 delegate 的私有字段 `enabledServices` 在滑动期间换成空列表（`isEnabled()` 立刻变 false，
 * 上面那条路径整体短路），滑动结束**当帧立刻恢复原对象**。因此：
 *  · 不修改任何全局系统设置，不申请任何权限，不碰无障碍服务本身；
 *  · 手指抬起后语义树马上恢复，TalkBack 读屏、点读等功能在静止时完全不受影响；
 *  · 只有在「正在滑动/惯性滑行」这一小段里不播报滚动位置 —— 这段既没人读屏，也正是掉帧的时段。
 *
 * 全程 `runCatching` 兜底：Compose 版本字段名变了就静默降级成「什么都没做」，
 * 绝不因为一个性能优化把 App 搞崩。日志里会写明到底成功没成功。
 */
object AccessibilityQuiet {

    /** 最近一次是否真的把无障碍刷新关掉了（写进性能日志用）。 */
    @Volatile
    var available: Boolean = false
        private set

    /** 当前是否处于「滑动静音」状态。 */
    @Volatile
    var muted: Boolean = false
        private set

    /** 失败原因（写进性能日志用）。 */
    @Volatile
    var lastError: String? = null
        private set

    private var cachedRoot: View? = null
    private var delegate: Any? = null
    private var servicesField: java.lang.reflect.Field? = null
    private var savedServices: Any? = null

    /**
     * 开始静音。返回是否真的静音成功（失败不影响调用方，滚动照常）。
     *
     * 只在一次滑动的起点调用一次，反射查找结果会缓存，别每帧调。
     */
    fun begin(context: android.content.Context?): Boolean {
        val activity = context?.findActivity() ?: run {
            lastError = "找不到宿主 Activity"
            return false
        }
        return begin(activity.window)
    }

    fun begin(window: Window?): Boolean {
        if (window == null) {
            lastError = "没有 window"
            return false
        }
        if (muted) return true
        val d = resolveDelegate(window)
        val f = servicesField
        if (d == null || f == null) {
            lastError = lastError ?: "没找到 Compose 无障碍委托字段"
            return false
        }
        return runCatching {
            // 已是空列表说明根本没启用无障碍服务 → 本次卡顿与它无关，别白折腾
            val current = f.get(d)
            val empty = (current as? Collection<*>)?.isEmpty() ?: true
            if (empty) {
                available = false
                lastError = "设备上没有已启用的无障碍服务，无需抑制"
                return false
            }
            savedServices = current
            f.set(d, emptyList<AccessibilityServiceInfo>())
            muted = true
            available = true
            lastError = null
            true
        }.getOrElse {
            lastError = "抑制失败: ${it.javaClass.simpleName} ${it.message}"
            false
        }
    }

    /** 结束静音：把原来的服务列表原样放回去。失败也要尽力，绝不能把无障碍永久关掉。 */
    fun end() {
        if (!muted) return
        val d = delegate
        val f = servicesField
        val saved = savedServices
        muted = false
        savedServices = null
        if (d == null || f == null || saved == null) return
        runCatching { f.set(d, saved) }.onFailure {
            lastError = "恢复失败: ${it.javaClass.simpleName} ${it.message}"
        }
    }

    /** 换车/换 Activity 后旧的 delegate 已经失效，缓存必须一起清掉。 */
    fun reset() {
        end()
        cachedRoot = null
        delegate = null
        servicesField = null
        savedServices = null
    }

    /** 给性能日志用的一句话描述。 */
    fun describe(): String = when {
        muted -> "滚动期间抑制语义刷新: 生效中"
        available -> "滚动期间抑制语义刷新: 已生效（本次滑动已结束并恢复）"
        else -> "滚动期间抑制语义刷新: 未生效（${lastError ?: "尚未滑动过"}）"
    }

    // ---- 反射定位：AndroidComposeView.composeAccessibilityDelegate.enabledServices ----

    private fun resolveDelegate(window: Window): Any? {
        val root = window.decorView
        if (root === cachedRoot && delegate != null) return delegate
        cachedRoot = root
        delegate = null
        servicesField = null
        val composeView = findComposeView(root, 0) ?: run {
            lastError = "没找到 AndroidComposeView"
            return null
        }
        return runCatching {
            // 外层字段不靠名字找（Compose 升级 / R8 改名都可能变），改成「按结构找」：
            // 谁身上挂着一个名叫 enabledServices 的 List 字段，谁就是那个无障碍委托。
            val outer = composeView.javaClass.declaredFields.firstOrNull { f ->
                val type = f.type
                type.name.endsWith("AndroidComposeViewAccessibilityDelegateCompat") ||
                    type.declaredFields.any { it.name == "enabledServices" }
            } ?: throw IllegalStateException("没有无障碍委托字段")
            outer.isAccessible = true
            val d = outer.get(composeView) ?: throw IllegalStateException("delegate 为 null")
            val g = d.javaClass.declaredFields.firstOrNull {
                it.type == java.util.List::class.java && it.name.contains("enabledServices")
            } ?: throw IllegalStateException("没有 enabledServices 字段")
            g.isAccessible = true
            delegate = d
            servicesField = g
            d
        }.getOrElse {
            lastError = "反射失败: ${it.javaClass.simpleName} ${it.message}"
            null
        }
    }

    private fun findComposeView(view: View, depth: Int): View? {
        if (depth > 12) return null
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeView(view.getChildAt(i), depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun android.content.Context.findActivity(): android.app.Activity? {
        var ctx: android.content.Context? = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
