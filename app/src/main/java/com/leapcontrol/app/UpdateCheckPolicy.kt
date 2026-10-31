package com.leapcontrol.app

/**
 * 自动检测更新的频率。
 *
 * 只在「App 启动」这一个时点判断要不要联网（零控是后台常驻用法，不做定时闹钟：
 * 定时任务在国产 ROM 上会被清理、还会多一条唤醒耗电的嫌疑）。所以这里的语义是
 * 「距上次成功检测满 N 天，下次启动才去查一次」，而不是每天定点查。
 *
 * [days] 同时是落盘的协议值（0 = 关闭），改动会读不到老用户的设置，别随便改数字。
 */
enum class UpdateCheckMode(val days: Int, val label: String, val desc: String) {
    /** 关闭：只在「我的 → 检查更新」里手动查，App 启动完全不联网。 */
    OFF(
        days = 0,
        label = "关闭",
        desc = "启动时不联网，只在「我的 → 检查更新」里手动查",
    ),
    WEEK(
        days = 7,
        label = "每 7 天",
        desc = "一周查一次，有更新会在启动时提醒",
    ),
    BIWEEK(
        days = 14,
        label = "每 14 天",
        desc = "两周查一次，适合不想频繁看到更新提示",
    ),
    MONTH(
        days = 30,
        label = "每 30 天",
        desc = "一个月查一次，只想知道大版本",
    ),
}

/**
 * 自动检测更新的判断逻辑（纯函数，方便单测）。
 *
 * 抽出来是因为这段逻辑有三处输入（用户档位 / 上次检测时间 / 当前时间）很容易写错：
 * 少判一个「时钟往前跳」就会变成永远不检测，少判 mode=OFF 就会变成关不掉。
 */
object UpdateCheckPolicy {

    /** 默认档位：一周一次。老用户没存过设置时也走这个值。 */
    val DEFAULT: UpdateCheckMode = UpdateCheckMode.WEEK

    const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /** 存储里读出来的天数 → 档位；不认识的值（脏数据 / 未来档位）回落到默认。 */
    fun fromDays(days: Int): UpdateCheckMode =
        UpdateCheckMode.entries.firstOrNull { it.days == days } ?: DEFAULT

    /**
     * 这次启动该不该自动联网检测。
     *
     * @param lastCheckAt 上次**成功**检测的时间戳（毫秒）；0 表示从未成功检测过。
     * @param now 当前时间戳（毫秒）。参数化是为了单测，正式调用传 System.currentTimeMillis()。
     */
    fun isDue(mode: UpdateCheckMode, lastCheckAt: Long, now: Long): Boolean {
        if (mode == UpdateCheckMode.OFF) return false
        // 从未成功检测过（新装 / 一直失败）→ 立刻查一次
        if (lastCheckAt <= 0L) return true
        // 时钟被往回调过（手动改时间 / 跨时区换机）→ 时间差是负的，
        // 按「已到期」处理。否则永远等不到 interval 之后，等于自动检测失效。
        if (now <= lastCheckAt) return true
        return now - lastCheckAt >= mode.days * DAY_MS
    }

    /**
     * 距离下次自动检测还有多久（毫秒）；不需要再等（已到期 / 关闭）时返回 0。
     * 设置页用它把「下次什么时候查」说清楚，免得用户以为开关没生效。
     */
    fun remainingMs(mode: UpdateCheckMode, lastCheckAt: Long, now: Long): Long {
        if (mode == UpdateCheckMode.OFF) return 0L
        if (lastCheckAt <= 0L) return 0L
        val interval = mode.days * DAY_MS
        val elapsed = now - lastCheckAt
        if (elapsed < 0L) return 0L
        return (interval - elapsed).coerceAtLeast(0L)
    }

    /** 设置行里的一句话状态：当前档位 + 下次检测时间。 */
    fun statusText(mode: UpdateCheckMode, lastCheckAt: Long, now: Long): String {
        if (mode == UpdateCheckMode.OFF) return "当前：已关闭，仅手动检查更新"
        val remaining = remainingMs(mode, lastCheckAt, now)
        if (remaining <= 0L) return "当前：${mode.label}，下次启动就会检测"
        val days = (remaining + DAY_MS - 1) / DAY_MS
        return "当前：${mode.label}，下次约 ${days} 天后"
    }
}
