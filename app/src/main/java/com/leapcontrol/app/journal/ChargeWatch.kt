package com.leapcontrol.app.journal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 充电期间的「看一眼」闹钟：**只在充电时存在，拔枪即停**。
 *
 * ## 为什么需要它
 * 车端没有充电历史接口，「充到多少」（见 [ChargeAnchor.socPeak]）只能靠零控在充电期间
 * 看见车况时自己记。以前只有「打开 App / 手动刷新 / 点小组件」这几个时机，于是最常见的
 * 场景必然丢数：夜里插枪、早上开走，中间一次都没打开过 App —— 89% 这一格谁也没见过。
 *
 * ## 为什么不用「续航峰值」代替
 * 剩余续航和电量是**同一份车况报文**里的两个字段，同一次请求一起回来。选哪个都改变不了
 * 「没请求就没有值」这件事：续航峰值同样只会记下我们看过的最高那一格。所以真正决定
 * 成败的是**采样次数**，不是取哪个字段。
 *
 * ## 为什么敢每 10 分钟拉一次（而平时强调别拉）
 * 平时拉车况会唤醒休眠的 T-Box，费的是**车的小电瓶**，所以约定是「进首页一次 + 手动刷新」。
 * 充电时情况相反：T-Box 本来就在工作，车在充电、12V 由 DC-DC 供电 —— 这时候多读几个信号
 * 代价极小。而且只在 `chargeState ∈ {1,5}` 期间生效，非充电期一次都不会多发。
 *
 * ## 间隔是自适应的
 * 用 `chargeRemainTime`（预计充满剩余分钟，信号 1200）决定下一次多久后再看：
 * 还剩几个小时就 20 分钟看一次，快充满（≤10 分钟）时缩到 3 分钟 —— 抓住最后一格。
 * 次数和精度两头都占，一次 8 小时慢充大约 30 多次请求，而不是每 3 分钟一次跑 160 次。
 *
 * ## 前台活跃时不会重复请求
 * [reschedule] 每次都用「现在 + 间隔」重排，所以 App 在前台正常刷新时闹钟会被一直往后推，
 * 永远不会到点 —— 前台刷新本身就是采样。
 */
object ChargeWatch {

    /** 采样广播的 action（与 [ChargeSampleReceiver] 对齐）。 */
    const val ACTION = "com.leapcontrol.app.action.CHARGE_SAMPLE"

    private const val REQUEST_CODE = 0x1C0A5
    private const val PREFS = "charge_watch"
    private const val KEY_STARTED = "started_at"

    /**
     * 观察窗口上限：超过 14 小时就不再续闹钟。
     *
     * 正常一次充电不会超过它；超了说明报文异常（比如车端一直卡在 state=1），
     * 与其无限期地每 10 分钟唤醒一次，不如停下来。
     */
    private const val MAX_WATCH_MS = 14 * 60 * 60 * 1000L

    /** 快充满：3 分钟一记，尽量抓住最后一格。 */
    const val FAST_MS = 3 * 60_000L

    /** 常规充电（剩余 10~60 分钟）：10 分钟一记。 */
    const val NORMAL_MS = 10 * 60_000L

    /** 还剩 1~4 小时：15 分钟一记。 */
    const val SLOW_MS = 15 * 60_000L

    /** 还剩 4 小时以上 / 暂停中：20 分钟一记。 */
    const val LAZY_MS = 20 * 60_000L

    /** 预约等待（还没开始充）：30 分钟看一眼有没有转成真充电。 */
    const val IDLE_MS = 30 * 60_000L

    /** 这一次没读成功（断网等）：15 分钟后再试一次，别把整次充电丢掉。 */
    const val RETRY_MS = 15 * 60_000L

    /**
     * 下一次该多久之后再看一眼；返回 null 表示**不用看了**，闹钟应当撤掉。
     *
     * @param chargeState 1149 充电状态：0 未插枪 / 1 充电中 / 2 已完成 / 3 故障 /
     *   4 预约等待 / 5 直流快充中 / 6 暂停
     * @param remainMinutes `chargeRemainTime`（1200），缺失传 null
     * @param watchedMs 本次观察已经持续多久
     */
    fun intervalMs(chargeState: Int?, remainMinutes: Int?, watchedMs: Long): Long? {
        if (watchedMs >= MAX_WATCH_MS) return null
        return when (chargeState) {
            // 1 交流充电中 / 5 直流快充中
            1, 5 -> when {
                remainMinutes == null -> NORMAL_MS
                remainMinutes <= 10 -> FAST_MS
                remainMinutes <= 60 -> NORMAL_MS
                remainMinutes <= 240 -> SLOW_MS
                else -> LAZY_MS
            }
            4 -> IDLE_MS
            6 -> LAZY_MS
            // 0 未插枪 / 2 已完成 / 3 故障 / 读不到：都不必再唤醒车端
            else -> null
        }
    }

    /**
     * 按当前车况续上（或撤掉）采样闹钟。每次拿到车况后都该调一次。
     *
     * 充电中 → 排下一次；不在充电 → 撤掉并清空观察窗口。
     */
    fun reschedule(context: Context, chargeState: Int?, remainMinutes: Int?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val started = prefs.getLong(KEY_STARTED, 0L)
        // 已经在观察窗口里、但这一发没读到充电状态（信号缺失 / 解析不出）：
        // 当作暂时失败，等一会儿再看 —— 别因为一次读不到就把整次充电丢掉。
        if (chargeState == null) {
            if (started > 0L) retryLater(context)
            return
        }
        val watched = if (started <= 0L) 0L else System.currentTimeMillis() - started
        val interval = intervalMs(chargeState, remainMinutes, watched)
        if (interval == null) {
            // 没在观察窗口里就啥也别做：这条路径每次刷新都会走，
            // 每次都清一次 prefs / cancel 一次闹钟属于白开销
            if (started > 0L) cancel(context)
            return
        }
        if (started <= 0L) {
            prefs.edit().putLong(KEY_STARTED, System.currentTimeMillis()).apply()
        }
        schedule(context, interval)
    }

    /**
     * 一次采样失败了（断网、网关超时）：按常规间隔重试。
     *
     * 只在确实还在观察窗口内才重试 —— 否则会凭一个陈旧的 started 值把闹钟重新挂上去。
     */
    fun retryLater(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val started = prefs.getLong(KEY_STARTED, 0L)
        if (started <= 0L) return
        if (System.currentTimeMillis() - started >= MAX_WATCH_MS) {
            cancel(context)
            return
        }
        schedule(context, RETRY_MS)
    }

    /** 撤掉闹钟并清空观察窗口。 */
    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_STARTED).apply()
        runCatching { alarm(context)?.cancel(pending(context)) }
    }

    private fun schedule(context: Context, delayMs: Long) {
        val am = alarm(context) ?: return
        val at = System.currentTimeMillis() + delayMs
        // setAndAllowWhileIdle：免精确闹钟权限，Doze 里也能（大约每 9 分钟最多一次）。
        // 部分 OEM 会拦，拦不住就退到普通 set —— 晚一点总比没有强。
        val ok = runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        }.isSuccess
        if (!ok) runCatching { am.set(AlarmManager.RTC_WAKEUP, at, pending(context)) }
    }

    private fun alarm(context: Context): AlarmManager? =
        runCatching { context.getSystemService(AlarmManager::class.java) }.getOrNull()

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ChargeSampleReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
