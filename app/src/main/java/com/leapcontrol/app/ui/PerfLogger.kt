package com.leapcontrol.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页滑动性能日志采集器（只服务于「排查滑动掉帧」，不参与任何业务逻辑）。
 *
 * 为什么需要它：帧率浮层只能实时显示一个「平均值」，没法把滑动过程中每一帧的
 * 情况留存下来分析。用户说「一滑动就掉帧」，但到底是：
 *   · 帧间隔被拉长（真的掉帧 / 卡顿）；
 *   · 主线程每帧重组次数飙升（State 在滚动路径上被每帧读）；
 *   · 主线程录制绘制指令太慢（SYNC+DRAW）；
 *   · GPU 画不完（填充率 / 图层太多）；
 *   · **屏幕刷新率在滑动时被系统动态降频**（120Hz 掉到 60Hz，帧间隔 8.3ms↔16.7ms 来回跳）；
 * 只有把滑动期间**每一帧**的数据按时间线记下来，才能确定性地下结论。
 *
 * 关键：用 [Choreographer.FrameData]（API 30+）拿到系统给每一帧分配的
 * `frameIntervalNanos` —— 这是系统「这一帧想按多少 Hz 出」的直接证据：
 *   · frameInterval 稳定 8.3ms（120Hz），但实际帧间隔却是 16/33ms → 渲染跟不上，查渲染；
 *   · frameInterval 自己就在 8.3↔16.7 之间跳 → 系统在动态降频，渲染再优化也没用。
 *
 * 设计原则（和已有的 FrameStats / RecomposeMeter 一脉相承）：
 *   · 采集只在「正在滑动」的窗口内进行，平时零开销；
 *   · 记录用普通字段 / 数组自增，绝不写 Compose State —— 探针不能反过来制造掉帧；
 *   · 数据落盘到 `filesDir/perf/`，卸载才删，可随时通过系统分享把文件发出去。
 */
object PerfLogger {

    /** 是否启用。默认关，由设置开关控制。关着完全无感。 */
    @Volatile
    var enabled: Boolean = false

    // ---- 采集窗口内的原始数据（普通数组，不触发任何重组）----
    private const val MAX_SAMPLES = 8192

    /** 距上一帧的实际间隔（ms），首帧 0。 */
    private val frameGaps = FloatArray(MAX_SAMPLES)

    /** 该帧时刻设备的实际刷新率（Hz），来自 display.refreshRate；0 表示没拿到。 */
    private val refreshHz = FloatArray(MAX_SAMPLES)

    /** 该帧的滚动偏移（px）。 */
    private val scrollOffsets = FloatArray(MAX_SAMPLES)

    /** 滚动速度（px/ms）。 */
    private val velocities = FloatArray(MAX_SAMPLES)

    /** 该帧是否掉帧（实际帧间隔超过一帧预算的 1.5 倍，预算按当前刷新率算）。 */
    private val jankFlags = BooleanArray(MAX_SAMPLES)

    /** 该帧主线程耗时 SYNC+DRAW（ms），-1 表示没拿到。 */
    private val cpuMsArr = FloatArray(MAX_SAMPLES) { -1f }

    /** 该帧 GPU 耗时（ms），-1 表示没拿到。 */
    private val gpuMsArr = FloatArray(MAX_SAMPLES) { -1f }

    /** 该帧 layout/measure 耗时（ms），-1 表示没拿到。 */
    private val layoutMsArr = FloatArray(MAX_SAMPLES) { -1f }

    /** doFrame 回调延迟（ms）：System.nanoTime() - frameTimeNanos。 */
    private val frameDelayMs = FloatArray(MAX_SAMPLES) { -1f }

    /**
     * 系统真正投递的 VSYNC 间隔（ms）= frameTimeNanos[i] - frameTimeNanos[i-1]。
     *
     * 这是本轮新增的**关键判据**，用来区分两个完全不同的世界：
     *   · 约 8.3ms（屏幕确实在 120Hz 吐 VSYNC），但我们的帧间隔 19.6ms
     *     → 主线程被别的东西占住，帧没来得及做，问题在 App 内部；
     *   · 本身就已经是 19~20ms（系统根本没按 120Hz 给这个窗口发 VSYNC）
     *     → 再怎么优化内容都没用，是刷新率/合成调度（系统侧）把窗口压到 ~50Hz。
     */
    private val vsyncGaps = FloatArray(MAX_SAMPLES) { -1f }

    /**
     * 主线程在这一帧的**真实 CPU 时间**（ms）= Debug.threadCpuTimeNanos 的帧间差。
     *
     * 和 FrameMetrics 的 SYNC+DRAW 互补：后者只算「画这一帧」的那段，
     * 主线程如果在两个 VSYNC 之间忙着跑别的消息，完全不会被 SYNC+DRAW 计入。
     * 所以：真实CPU ≈ SYNC+DRAW → 主线程闲、在等（系统侧/合成侧）；
     *      真实CPU ≫ SYNC+DRAW → 主线程被非帧任务占满（日志里会点出任务名）。
     */
    private val mainCpuMs = FloatArray(MAX_SAMPLES) { -1f }

    private var sampleCount = 0

    private var recording = false
    private var lastFrameNanos = 0L
    private var lastVsyncNanos = 0L
    private var lastMainCpuNanos = 0L
    private var lastScrollOffset = 0f
    private var recomposeAtStart = 0L

    // ---- 主线程长任务监控（Looper.setMessageLogging）----
    /**
     * 滑动期间挂在主线程 Looper 上的消息探针。
     *
     * 为什么必须要有它：FrameMetrics 只能看到「帧相关」的耗时，主线程被一条
     * 20ms 的普通消息（网络回调、prefs 落盘、协程恢复……）堵住时，SYNC/DRAW
     * 依然是漂亮的 1.6ms，日志就会呈现「哪里都不慢但就是掉帧」的假象。
     * 只有把每条消息的 dispatch 耗时打出来，才能指名道姓。
     */
    private var messageLogger: android.util.Printer? = null
    private var msgStartNanos = 0L
    private var msgLabel: String? = null
    private val longMessages = ArrayList<String>()

    /** 长任务阈值（ms）：低于它属正常，不记。 */
    private const val LONG_MSG_MS = 4f

    /** 采样窗口起点（真实时间），用于「滚动开始后固定采样 3 秒」判断。 */
    @Volatile
    var windowStartNanos = 0L

    /** 是否正在采样（防止同一帧重复注册回调）。 */
    @Volatile
    var samplingNow = false

    // 实时刷新率来源（滑动开始时注入的 window，每帧读 window.decorView.display.refreshRate）
    private var refreshSource: android.view.Display? = null

    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    private var frameMetricsWindow: Window? = null
    private var frameMetricsRegistered = false

    /** 由 App 启动时注入一次 filesDir。 */
    @Volatile
    private var injectedDir: File? = null

    /** 应用 Context，只用来在报告里读无障碍服务状态。 */
    @Volatile
    private var appContext: Context? = null

    fun inject(context: Context) {
        injectedDir = context.applicationContext.filesDir
        appContext = context.applicationContext
    }

    fun deviceInfo(): String {
        return "设备: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "版本: ${com.leapcontrol.app.BuildConfig.VERSION_NAME} " +
            "(${com.leapcontrol.app.BuildConfig.VERSION_CODE})\n"
    }

    /** 滑动开始：清空上一轮数据，进入采集态。 */
    fun onScrollStart(context: Context) {
        if (!enabled || recording) return
        recording = true
        sampleCount = 0
        lastFrameNanos = 0L
        lastVsyncNanos = 0L
        lastMainCpuNanos = 0L
        lastScrollOffset = 0f
        recomposeAtStart = RecomposeMeter.total
        longMessages.clear()
        installMessageLogger()

        val window = context.findActivityOrNull()?.window
        refreshSource = runCatching { window?.decorView?.display }.getOrNull()
        if (window == null) return
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            recordFrameCost(metrics)
        }
        val registered = runCatching {
            window.addOnFrameMetricsAvailableListener(
                listener,
                Handler(Looper.getMainLooper()),
            )
        }.isSuccess
        if (registered) {
            frameMetricsListener = listener
            frameMetricsWindow = window
            frameMetricsRegistered = true
        }
    }

    /**
     * 采样窗口起点：滚动开始、准备启动 Choreographer 采样时记一个真实时间戳。
     * 采样协程用「当前真实时间 - windowStartNanos > 3s」来决定何时收尾。
     */
    fun onScrollStartFrameWindow() {
        windowStartNanos = System.nanoTime()
    }

    /**
     * 每帧由 Choreographer 帧回调记录一次（帧间隔 + 实时刷新率 + 偏移 + 速度 + 掉帧）。
     *
     * 帧间隔用 System.nanoTime() 实测（不是 frameTimeNanos）——frameTimeNanos 在同一帧
     * 可能被回调两次导致「间隔 0」的假帧；System.nanoTime() 是真实单调时钟，最可靠。
     *
     * doFrame 延迟 = System.nanoTime() - frameTimeNanos，反映「主线程在这一帧被别的
     * 任务拖住、绘制回调晚于 VSYNC 多久」。差值大 → 主线程被阻塞。
     */
    fun onScrollFrame(scrollOffset: Float, frameTimeNanos: Long = 0L) {
        if (!recording || sampleCount >= MAX_SAMPLES) return
        val idx = sampleCount
        sampleCount++

        val now = System.nanoTime()

        // doFrame 回调延迟：仅当调用方传了 frameTimeNanos 时才有意义
        frameDelayMs[idx] = if (frameTimeNanos > 0L) (now - frameTimeNanos) / 1_000_000f else -1f

        // 系统真正投递的 VSYNC 间隔（跟「我们的帧间隔」是两回事，见字段注释）
        if (frameTimeNanos > 0L) {
            vsyncGaps[idx] = if (lastVsyncNanos == 0L) -1f
            else (frameTimeNanos - lastVsyncNanos) / 1_000_000f
            lastVsyncNanos = frameTimeNanos
        }

        // 主线程真实 CPU：帧间差。和 SYNC+DRAW 一比就知道是「忙」还是「在等」。
        val cpuNow = runCatching { android.os.Debug.threadCpuTimeNanos() }.getOrDefault(-1L)
        if (cpuNow > 0L) {
            mainCpuMs[idx] = if (lastMainCpuNanos == 0L) -1f
            else (cpuNow - lastMainCpuNanos) / 1_000_000f
            lastMainCpuNanos = cpuNow
        }

        // 实际帧间隔：用 System.nanoTime() 实测
        val gapMs = if (lastFrameNanos == 0L) 0f
        else (now - lastFrameNanos) / 1_000_000f
        frameGaps[idx] = gapMs
        lastFrameNanos = now

        // 实时刷新率：系统此刻实际给的 Hz（0 表示读不到）
        val hz = runCatching { refreshSource?.refreshRate ?: 0f }.getOrDefault(0f)
        refreshHz[idx] = hz

        // 滚动速度
        val dOffset = scrollOffset - lastScrollOffset
        velocities[idx] = if (gapMs > 0f) dOffset / gapMs else 0f
        scrollOffsets[idx] = scrollOffset
        lastScrollOffset = scrollOffset

        // 掉帧判定：实际帧间隔超过「一帧预算」的 1.5 倍。
        // 预算按当前刷新率算（120Hz→8.3ms，60Hz→16.7ms）；读不到刷新率就用 16.7ms 兜底。
        val budgetMs = if (hz > 0f) 1000f / hz else 16.7f
        jankFlags[idx] = gapMs > budgetMs * 1.5f
    }

    /**
     * 挂上主线程消息探针。Looper 是进程级的，只允许一个 Printer，
     * 所以采集结束必须摘掉（[uninstallMessageLogger]），否则会一直拖累正常使用。
     */
    private fun installMessageLogger() {
        if (messageLogger != null) return
        val p = android.util.Printer { line ->
            // 回调本身就在主线程，只做两次 nanoTime 和短字符串比较，开销可忽略
            if (line.startsWith(">>>>>")) {
                msgStartNanos = System.nanoTime()
                msgLabel = runCatching {
                    line.substringAfter("Dispatching to ", "").take(110)
                }.getOrDefault("?")
            } else if (line.startsWith("<<<<<")) {
                val label = msgLabel
                if (label != null && msgStartNanos != 0L) {
                    val d = (System.nanoTime() - msgStartNanos) / 1_000_000f
                    if (d >= LONG_MSG_MS && longMessages.size < 80) {
                        longMessages.add(String.format(Locale.US, "%6.1f ms  %s", d, label))
                    }
                    msgStartNanos = 0L
                    msgLabel = null
                }
            }
        }
        runCatching { Looper.getMainLooper().setMessageLogging(p) }
        messageLogger = p
    }

    private fun uninstallMessageLogger() {
        if (messageLogger == null) return
        messageLogger = null
        msgStartNanos = 0L
        msgLabel = null
        runCatching { Looper.getMainLooper().setMessageLogging(null) }
    }

    /** FrameMetrics 回调：记录 SYNC+DRAW / GPU 耗时（对应「下一帧」）。 */    private fun recordFrameCost(metrics: FrameMetrics) {
        if (!recording) return
        val sync = metrics.getMetric(FrameMetrics.SYNC_DURATION)
        val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION)
        val cpu = if (sync >= 0 && draw >= 0) (sync + draw) / 1_000_000f else -1f
        var gpu = -1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val g = metrics.getMetric(FrameMetrics.GPU_DURATION)
            gpu = if (g >= 0) g / 1_000_000f else -1f
        }
        // layout/measure 阶段耗时（FrameMetrics 里 SYNC/DRAW 不含 measure）
        val layout = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
        val layoutMs = if (layout >= 0) layout / 1_000_000f else -1f
        // 挂到「最新一帧」上（FrameMetrics 回调滞后一帧，这里挂到最近已采样帧）
        val i = sampleCount - 1
        if (i >= 0 && i < MAX_SAMPLES && cpuMsArr[i] < 0f) {
            cpuMsArr[i] = cpu
            gpuMsArr[i] = gpu
            layoutMsArr[i] = layoutMs
        }
    }

    /** 滑动结束：停采样、写日志、返回文件路径（失败或没数据返回 null）。 */
    fun onScrollEnd(): File? {
        if (!recording) return null
        recording = false
        uninstallMessageLogger()

        if (frameMetricsRegistered) {
            runCatching {
                frameMetricsWindow?.removeOnFrameMetricsAvailableListener(frameMetricsListener!!)
            }
        }
        frameMetricsRegistered = false
        frameMetricsListener = null
        frameMetricsWindow = null
        refreshSource = null

        if (sampleCount < 2) return null
        val recomposeTotal = RecomposeMeter.total - recomposeAtStart
        return buildReport(recomposeTotal)
    }

    private fun buildReport(recomposeTotal: Long): File? {
        val base = injectedDir ?: return null
        val dir = File(base, "perf")
        if (!dir.exists()) dir.mkdirs()

        val n = sampleCount
        var totalGap = 0.0
        var jankCount = 0
        var maxGap = 0.0
        var maxVel = 0.0
        var maxDelay = 0.0
        var cpuSum = 0.0
        var cpuCnt = 0
        var gpuSum = 0.0
        var gpuCnt = 0
        var layoutSum = 0.0
        var layoutCnt = 0
        var vsyncSum = 0.0
        var vsyncCnt = 0
        var mainCpuSum = 0.0
        var mainCpuCnt = 0
        val vsyncHist = HashMap<Int, Int>()
        // 统计帧间隔分布：看帧间隔是不是只有 8.3/16.7 两个离散值（刷新率跳档），
        // 还是连续分布（渲染慢）。
        val gapHist = HashMap<Int, Int>()
        // 统计实时刷新率变化：看系统是不是在 120↔60 之间跳
        val hzHist = HashMap<Int, Int>()
        for (i in 0 until n) {
            totalGap += frameGaps[i]
            if (jankFlags[i]) jankCount++
            if (frameGaps[i] > maxGap) maxGap = frameGaps[i].toDouble()
            if (velocities[i] > maxVel) maxVel = velocities[i].toDouble()
            if (frameDelayMs[i] > maxDelay) maxDelay = frameDelayMs[i].toDouble()
            if (cpuMsArr[i] >= 0f) { cpuSum += cpuMsArr[i]; cpuCnt++ }
            if (gpuMsArr[i] >= 0f) { gpuSum += gpuMsArr[i]; gpuCnt++ }
            if (layoutMsArr[i] >= 0f) { layoutSum += layoutMsArr[i]; layoutCnt++ }
            if (vsyncGaps[i] > 0f) {
                vsyncSum += vsyncGaps[i]; vsyncCnt++
                val v = vsyncGaps[i].toInt()
                if (v > 0) vsyncHist[v] = (vsyncHist[v] ?: 0) + 1
            }
            if (mainCpuMs[i] >= 0f) { mainCpuSum += mainCpuMs[i]; mainCpuCnt++ }
            val g = frameGaps[i].toInt()
            if (g > 0) gapHist[g] = (gapHist[g] ?: 0) + 1
            val h = refreshHz[i].toInt()
            if (h > 0) hzHist[h] = (hzHist[h] ?: 0) + 1
        }
        val avgGap = totalGap / n
        val jankRatio = jankCount.toFloat() / n
        val avgCpu = if (cpuCnt > 0) cpuSum / cpuCnt else -1.0
        val avgGpu = if (gpuCnt > 0) gpuSum / gpuCnt else -1.0
        val avgLayout = if (layoutCnt > 0) layoutSum / layoutCnt else -1.0
        val avgVsync = if (vsyncCnt > 0) vsyncSum / vsyncCnt else -1.0
        val avgMainCpu = if (mainCpuCnt > 0) mainCpuSum / mainCpuCnt else -1.0

        val sb = StringBuilder()
        sb.append("==== 零控首页滑动性能日志 ====\n")
        sb.append(deviceInfo())
        sb.append("采集时间: ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .append("\n")
        sb.append("样本帧数: ").append(n).append("\n")
        sb.append("平均帧间隔: ").append(fmt1(avgGap)).append(" ms\n")
        sb.append("最大帧间隔: ").append(fmt1(maxGap)).append(" ms\n")
        sb.append("掉帧帧数: ").append(jankCount).append("/").append(n)
            .append(" (").append(fmt1(jankRatio * 100.0)).append("%)\n")
        sb.append("最大滚动速度: ").append(fmt1(maxVel)).append(" px/ms\n")
        sb.append("最大doFrame延迟: ").append(fmt1(maxDelay)).append(" ms\n")
        sb.append("本次滑动重组次数: ").append(recomposeTotal).append("\n")
        if (avgCpu >= 0) sb.append("平均主线程耗时(SYNC+DRAW): ").append(fmt2(avgCpu)).append(" ms\n")
        if (avgLayout >= 0) sb.append("平均layout/measure耗时: ").append(fmt2(avgLayout)).append(" ms\n")
        if (avgGpu >= 0) sb.append("平均GPU耗时: ").append(fmt2(avgGpu)).append(" ms\n")
        sb.append("主线程真实CPU(每帧): ").append(fmt2(avgMainCpu)).append(" ms\n")
        sb.append(accessibilityLine())

        // 结论行：系统 VSYNC 到底是不是 8.3ms 来的
        if (avgVsync > 0) {
            sb.append("系统VSYNC间隔平均: ").append(fmt2(avgVsync)).append(" ms  ")
            sb.append(
                when {
                    avgVsync < 9.5 -> "→ 屏幕确实在 120Hz 出 VSYNC，帧是 App 侧没做出来"
                    avgVsync < 13.0 -> "→ VSYNC 时快时慢（刷新率抖动/进 LTPO 过渡档）"
                    avgVsync < 18.5 -> "→ 系统按 ~60Hz 在给这个窗口发 VSYNC，App 优化无效"
                    else -> "→ 系统给这个窗口的帧率被压到 ~50Hz 以下，纯系统调度问题"
                },
            )
            sb.append("\n")
        }
        // 帧间隔分布：8.3/16.7 两个离散值 = 刷新率跳档；连续分布 = 渲染慢
        if (gapHist.isNotEmpty()) {
            sb.append("帧间隔分布 (ms→帧数): ")
            sb.append(gapHist.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}→${it.value}" })
            sb.append("\n")
        }
        // 实时刷新率分布：120↔60 跳 = 系统动态降频
        if (hzHist.isNotEmpty()) {
            sb.append("实时刷新率分布 (Hz→帧数): ")
            sb.append(hzHist.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}→${it.value}" })
            sb.append("\n")
        }
        // 系统 VSYNC 间隔分布：8.3 说明屏幕在 120Hz 吐；16.7/20 说明系统就没给足帧
        if (vsyncHist.isNotEmpty()) {
            sb.append("VSYNC间隔分布 (ms→次数): ")
            sb.append(vsyncHist.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}→${it.value}" })
            sb.append("\n")
        }
        // 主线程长任务清单：这里出现谁，谁就是滑动时把帧顶掉的元凶
        sb.append("\n---- 滑动期间主线程长任务 (>")
        sb.append(LONG_MSG_MS.toInt()).append("ms，最多 80 条) ----\n")
        if (longMessages.isEmpty()) {
            sb.append("(无：主线程没有被单条消息长时间占住)\n")
        } else {
            longMessages.forEach { sb.append(it).append("\n") }
        }
        sb.append("\n---- 逐帧明细 (序号 实际间隔 刷新率Hz 偏移 速度 doFrame延迟 掉帧 CPU layout GPU 主CPU vsync间隔) ----\n")
        for (i in 0 until n) {
            sb.append(String.format(Locale.US, "%4d ", i))
            sb.append(String.format(Locale.US, "%7.1f ", frameGaps[i]))
            sb.append(String.format(Locale.US, "%7.0f ", refreshHz[i]))
            sb.append(String.format(Locale.US, "%8.1f ", scrollOffsets[i]))
            sb.append(String.format(Locale.US, "%7.2f ", velocities[i]))
            sb.append(String.format(Locale.US, "%7.1f ", frameDelayMs[i]))
            sb.append(if (jankFlags[i]) "JANK " else "ok   ")
            sb.append(String.format(Locale.US, "%6.1f ", cpuMsArr[i]))
            sb.append(String.format(Locale.US, "%6.1f ", layoutMsArr[i]))
            sb.append(String.format(Locale.US, "%6.1f ", gpuMsArr[i]))
            sb.append(String.format(Locale.US, "%6.1f ", mainCpuMs[i]))
            sb.append(String.format(Locale.US, "%6.1f", vsyncGaps[i]))
            sb.append("\n")
        }
        sb.append("---- 日志结束 ----\n")

        val file = File(dir, "perf_${System.currentTimeMillis()}.log")
        return runCatching { file.writeText(sb.toString()); file }.getOrNull()
    }

    fun listLogs(): List<File> {
        val base = injectedDir ?: return emptyList()
        val perf = File(base, "perf")
        if (!perf.exists()) return emptyList()
        return perf.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun share(context: Context, file: File): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出性能日志"))
            true
        }.getOrDefault(false)
    }

    private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

    /**
     * 无障碍服务状态 —— 首页掉帧的头号嫌疑。
     *
     * Compose 的语义树刷新闸门是 `accessibilityManager.isEnabled && enabledServices 非空`，
     * 所以这台机器上到底有没有无障碍服务，直接决定「抑制语义刷新」这条路有没有意义。
     * 把包名列出来，用户一看就知道该去哪里关。
     */
    private fun accessibilityLine(): String {
        val ctx = appContext ?: return "无障碍服务: 读不到 Context\n"
        val am = runCatching {
            ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        }.getOrNull() ?: return "无障碍服务: 读不到 AccessibilityManager\n"
        val enabled = runCatching { am.isEnabled }.getOrDefault(false)
        val names = runCatching {
            am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo }
                .map { "${it.packageName}/${it.name.substringAfterLast('.')}" }
                .distinct()
        }.getOrDefault(emptyList())
        val sb = StringBuilder()
        sb.append("无障碍开关: ").append(if (enabled) "已开启" else "已关闭").append("\n")
        sb.append("已启用无障碍服务: ").append(names.size).append(" 个")
        if (names.isNotEmpty()) sb.append(" → ").append(names.joinToString(", "))
        sb.append("\n")
        sb.append(AccessibilityQuiet.describe()).append("\n")
        return sb.toString()
    }

    private fun Context.findActivityOrNull(): android.app.Activity? {
        var ctx: Context? = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
