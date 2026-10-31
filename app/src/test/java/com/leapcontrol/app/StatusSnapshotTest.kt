package com.leapcontrol.app

import com.leapcontrol.app.ui.CarStatus
import com.leapcontrol.app.ui.StatusSnapshot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 进程内车况快照的新鲜度判断。
 *
 * 这套规则直接决定「在车里关掉空调 / 后备箱之后，控制中心那格还亮不亮」，
 * 所以两个边界要卡死：太旧的快照不能用，比用户最后一次点击还旧的快照更不能拿。
 */
class StatusSnapshotTest {

    @Before
    fun reset() {
        // object 单例，测试之间必须清干净，否则前一条的报文会漏到后一条
        StatusSnapshot.put(null)
    }

    private fun status(trunkOpen: Boolean? = null) = CarStatus(
        soc = 60,
        range = null,
        electricRange = null,
        fuelRange = null,
        fuelSoc = null,
        isRangeExtender = false,
        totalMileage = null,
        interiorTemp = null,
        locked = null,
        acOn = null,
        trunkOpen = trunkOpen,
    )

    @Test
    fun `刚放进来的快照能读到`() {
        val now = 1_000_000L
        StatusSnapshot.put(status(trunkOpen = true), now)
        val got = StatusSnapshot.get(maxAgeMs = 30_000L, now = now)
        assertNotNull(got)
    }

    @Test
    fun `超过最大年龄的快照一律当作没有`() {
        val at = 1_000_000L
        StatusSnapshot.put(status(trunkOpen = true), at)
        // 恰好超出一天，磁盘缓存里几小时前的报文不能拿来校准磁贴
        assertNull(StatusSnapshot.get(maxAgeMs = 30_000L, now = at + 30_001L))
        // 边界内仍然可用
        assertNotNull(StatusSnapshot.get(maxAgeMs = 30_000L, now = at + 30_000L))
    }

    @Test
    fun `比用户最后一次点击更旧的快照不采用`() {
        val at = 1_000_000L
        StatusSnapshot.put(status(trunkOpen = false), at)
        // 用户在 at+1s 点了「开后备箱」：这条更早的报文说他没开，不能拿来覆盖
        assertNull(StatusSnapshot.get(maxAgeMs = 30_000L, since = at + 1_000L, now = at + 2_000L))
        // 报文比点击更新时才可以用（车端执行完回传了新状态）
        assertNotNull(StatusSnapshot.get(maxAgeMs = 30_000L, since = at - 1_000L, now = at + 2_000L))
    }

    @Test
    fun `清空之后读不到`() {
        StatusSnapshot.put(status(trunkOpen = true), 1_000_000L)
        StatusSnapshot.put(null)
        assertNull(StatusSnapshot.get(maxAgeMs = 30_000L, now = 1_000_000L))
    }

    @Test
    fun `时间戳为零的快照视为无效`() {
        // 冷启动时磁盘缓存里没有时间戳（老版本没写过 status_time）→ 不能用
        StatusSnapshot.put(status(trunkOpen = true), 0L)
        assertNull(StatusSnapshot.get(maxAgeMs = 30_000L, now = 1_000_000L))
    }
}
