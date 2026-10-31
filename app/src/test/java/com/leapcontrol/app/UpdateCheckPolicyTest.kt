package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动检测更新档位与「这次该不该查」的判定。
 *
 * 这几条覆盖的是最容易写错、又最难在真机上复现的分支：
 * 关闭档不能联网、从未查过要立刻查、时钟回拨不能变成永不检测、边界正好满 7 天要查。
 */
class UpdateCheckPolicyTest {

    private val day = UpdateCheckPolicy.DAY_MS

    @Test
    fun `关闭档无论多久没查都不自动检测`() {
        val now = 1_700_000_000_000L
        assertFalse(UpdateCheckPolicy.isDue(UpdateCheckMode.OFF, 0L, now))
        // 十年前查过一次也一样：关闭就是关闭
        assertFalse(UpdateCheckPolicy.isDue(UpdateCheckMode.OFF, now - 3_650 * day, now))
    }

    @Test
    fun `从未成功检测过时立刻检测`() {
        val now = 1_700_000_000_000L
        UpdateCheckMode.entries.filter { it != UpdateCheckMode.OFF }.forEach { mode ->
            assertTrue(mode.name, UpdateCheckPolicy.isDue(mode, 0L, now))
        }
    }

    @Test
    fun `未满周期不检测_正好满周期检测`() {
        val now = 1_700_000_000_000L
        val mode = UpdateCheckMode.WEEK
        assertFalse(UpdateCheckPolicy.isDue(mode, now - (7 * day - 1), now))
        assertTrue(UpdateCheckPolicy.isDue(mode, now - 7 * day, now))
        assertTrue(UpdateCheckPolicy.isDue(mode, now - 8 * day, now))
    }

    @Test
    fun `各档位周期互不串味`() {
        val now = 1_700_000_000_000L
        val tenDaysAgo = now - 10 * day
        assertTrue(UpdateCheckPolicy.isDue(UpdateCheckMode.WEEK, tenDaysAgo, now))
        assertFalse(UpdateCheckPolicy.isDue(UpdateCheckMode.BIWEEK, tenDaysAgo, now))
        assertFalse(UpdateCheckPolicy.isDue(UpdateCheckMode.MONTH, tenDaysAgo, now))
        assertFalse(UpdateCheckPolicy.isDue(UpdateCheckMode.WEEK, now - 3 * day, now))
    }

    @Test
    fun `时钟被回拨时按已到期处理`() {
        val now = 1_700_000_000_000L
        // 上次检测时间在「未来」：说明系统时间被往回调过，不能因此永远不检测
        assertTrue(UpdateCheckPolicy.isDue(UpdateCheckMode.WEEK, now + 5 * day, now))
        assertEquals(0L, UpdateCheckPolicy.remainingMs(UpdateCheckMode.WEEK, now + 5 * day, now))
    }

    @Test
    fun `剩余时间与状态文案`() {
        val now = 1_700_000_000_000L
        // 刚查过 → 还剩整整一个周期
        assertEquals(7 * day, UpdateCheckPolicy.remainingMs(UpdateCheckMode.WEEK, now, now))
        // 已过 6 天半 → 剩半天，文案向上取整成 1 天（说「0 天后」等于糊弄人）
        val halfDayLeft = now - (7 * day) + day / 2
        assertEquals(
            "当前：每 7 天，下次约 1 天后",
            UpdateCheckPolicy.statusText(UpdateCheckMode.WEEK, halfDayLeft, now),
        )
        // 已到期 → 明说下次启动就会查，而不是「约 0 天后」
        assertEquals(
            "当前：每 7 天，下次启动就会检测",
            UpdateCheckPolicy.statusText(UpdateCheckMode.WEEK, now - 9 * day, now),
        )
        assertEquals(
            "当前：已关闭，仅手动检查更新",
            UpdateCheckPolicy.statusText(UpdateCheckMode.OFF, now - 9 * day, now),
        )
        // 从没查过也要说人话
        assertEquals(
            "当前：每 30 天，下次启动就会检测",
            UpdateCheckPolicy.statusText(UpdateCheckMode.MONTH, 0L, now),
        )
    }

    @Test
    fun `存储里的天数换算回档位_脏数据回落默认`() {
        assertEquals(UpdateCheckMode.OFF, UpdateCheckPolicy.fromDays(0))
        assertEquals(UpdateCheckMode.WEEK, UpdateCheckPolicy.fromDays(7))
        assertEquals(UpdateCheckMode.BIWEEK, UpdateCheckPolicy.fromDays(14))
        assertEquals(UpdateCheckMode.MONTH, UpdateCheckPolicy.fromDays(30))
        // 脏数据 / 已废弃的档位不能把自动检测弄成「静默关闭」
        assertEquals(UpdateCheckPolicy.DEFAULT, UpdateCheckPolicy.fromDays(3))
        assertEquals(UpdateCheckPolicy.DEFAULT, UpdateCheckPolicy.fromDays(-1))
        assertEquals(UpdateCheckPolicy.DEFAULT, UpdateCheckPolicy.fromDays(365))
    }

    @Test
    fun `默认档位是一周`() {
        assertEquals(7, UpdateCheckPolicy.DEFAULT.days)
        assertEquals(UpdateCheckMode.WEEK, UpdateCheckPolicy.DEFAULT)
    }
}
