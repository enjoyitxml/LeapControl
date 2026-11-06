package com.leapcontrol.app.tiles

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.widget.Toast
import com.leapcontrol.app.R

/**
 * 控制中心磁贴的**进度提示**（用户 2026-11-05：点磁贴一定要在手机上看到进度）。
 *
 * ## 首选：系统原生 Toast
 * 就是安卓那套底部浮出来的小字（「车辆上锁中…」/「车辆已上锁」），
 * 不占通知栏、不用权限、不打断操作 —— 用户要的就是这个。
 *
 * ## 但 toast 有时会被系统吞掉
 * Android 12 起，**纯后台应用**的 toast 会被系统直接丢弃。控制中心展开时
 * TileService 通常是后台（重要级低于「前台服务」），于是 `Toast.makeText` 什么都不会显示 ——
 * 这就是用户反馈的「点了磁贴没有任何反应」。
 *
 * 所以先判断本进程现在够不够「前台」（判据与系统一致：重要级 ≤ 前台服务），
 * 够就只弹 toast；不够就改用**通知横幅**兜底 —— 它在控制中心展开时以悬浮横幅
 * 浮在面板上方，效果同样是「弹一行字」，只是长得像通知。
 *
 * ## 文案口径
 * 与磁贴副标题、首页右上角同一套：
 * - 执行中：`车辆解锁中…`
 * - 确认成功：`车辆已解锁，执行成功`
 * - 没确认：`解锁指令已发出，但车辆状态还没变化…`
 * - 失败：给用户看的原因与下一步（见 [com.leapcontrol.app.ui.ControlFailure]）
 */
internal object TileNotice {

    private const val CHANNEL = "control_progress"

    /** 固定 id：同一时刻只有一条控车提示，新的直接覆盖旧的。 */
    private const val ID = 0x4C43

    /** 执行中的停留上限：结果回来会被覆盖，这里只是防「协程没了、提示一直挂着」。 */
    private const val RUNNING_TTL_MS = 30_000L

    /** 结果停留时间：够看清一行字，又不长期占着通知栏。 */
    private const val RESULT_TTL_MS = 6_000L

    /** 「再点一次确认」的停留时间：比 3 秒确认窗口稍长，第二次点之前字不会先消失。 */
    private const val CONFIRM_TTL_MS = 4_000L

    /** 通知能不能发（用户在系统里关掉了这个 App 的通知时为 false）。 */
    fun enabled(context: Context): Boolean =
        runCatching { context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() }
            .getOrNull() == true

    /** 指令刚下发：`车辆上锁中…` */
    fun running(context: Context, text: String) {
        show(context, text, "正在控车", RUNNING_TTL_MS)
    }

    /** 等着用户再点一次：`再点一次确认上锁`。停留时间盖住那 3 秒确认窗口。 */
    fun confirm(context: Context, text: String) {
        show(context, text, "待确认", CONFIRM_TTL_MS)
    }

    /** 车端确认执行了：`车辆已上锁，执行成功` */
    fun done(context: Context, text: String) {
        show(context, text, "控车完成", RESULT_TTL_MS)
    }

    /** 指令发出去了但没等到车端确认。 */
    fun pending(context: Context, text: String) {
        show(context, text, "已下发", RESULT_TTL_MS)
    }

    /** 没发出去 / 车端拒绝。 */
    fun failed(context: Context, text: String) {
        show(context, text, "控车未成功", RESULT_TTL_MS)
    }

    /** 撤销兜底通知（提示已经没必要留着了）。 */
    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(ID)
        }
    }

    /**
     * 先试原生 toast，弹不出来才发通知。
     *
     * [title] 只在走通知兜底时用得上（toast 只有一行字）。
     */
    private fun show(context: Context, text: String, title: String, ttlMs: Long) {
        if (toastAllowed(context) && toast(context, text)) {
            // toast 能弹，就把之前留下的兜底通知一并撤掉，别两处同时挂着
            cancel(context)
            return
        }
        postNotification(context, title, text, ttlMs)
    }

    /**
     * 本进程现在能不能弹 toast。
     *
     * Android 12 之前后台 toast 不受限制，一律直接弹。
     * 12 起按系统同一套判据：重要级不高于「前台服务」才不会被拦。
     * 拿不到进程信息就当作可以弹（宁可试一次，失败再走通知兜底）。
     */
    private fun toastAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching {
            val am = context.getSystemService(ActivityManager::class.java) ?: return true
            val mine = am.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() } ?: return true
            mine.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
        }.getOrDefault(true)
    }

    /** @return 是否真的调用了（只能判断「没抛异常」，无法确认系统有没有把字吞掉） */
    private fun toast(context: Context, text: String): Boolean = runCatching {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }.isSuccess

    private fun postNotification(context: Context, title: String, text: String, ttlMs: Long) {
        val nm = runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
        // 通知也被关掉：退回 toast 再试一次 —— 至少 App 在前台时还能看见
        if (nm == null || !runCatching { nm.areNotificationsEnabled() }.getOrDefault(false)) {
            toast(context, text)
            return
        }
        runCatching {
            ensureChannel(nm)
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_control)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setTimeoutAfter(ttlMs)
                .build()
            nm.notify(ID, notification)
        }.onFailure {
            toast(context, text)
        }
    }

    /**
     * 通道只建一次（同名重复创建是幂等的）。
     *
     * 必须是 HIGH：只有高优先级才会以**悬浮横幅**的形式弹出来 ——
     * 控制中心展开时，通知栏本身是看不见的，能浮在面板上面的只有横幅。
     * 声音和震动都关掉，控车是用户自己点的，不需要再吵一下。
     */
    private fun ensureChannel(nm: NotificationManager) {
        val channel = NotificationChannel(CHANNEL, "控车进度", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "在控制中心点磁贴控车时，提示正在执行和最终结果"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /** 点通知回到 App 首页，方便用户核对车况。 */
    private fun openApp(context: Context): PendingIntent? = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }.getOrNull()
}
