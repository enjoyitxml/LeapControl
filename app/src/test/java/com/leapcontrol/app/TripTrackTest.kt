package com.leapcontrol.app

import com.leapcontrol.app.trip.TripCloseResult
import com.leapcontrol.app.trip.TripDeparture
import com.leapcontrol.app.trip.TripFormat
import com.leapcontrol.app.trip.TripMergeResult
import com.leapcontrol.app.trip.TripOverview
import com.leapcontrol.app.trip.TripRecord
import com.leapcontrol.app.trip.TripRules
import com.leapcontrol.app.trip.TripSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行程轨迹的纯逻辑：分段状态机、里程口径、裁剪与 JSON 往返。
 * 参考 LeapMotor Mate 的 state_machine.py，但阈值按零控 60 秒轮询放宽。
 */
class TripTrackTest {

    private val t0 = 1_700_000_000_000L

    private fun point(
        at: Long,
        driving: Boolean,
        odo: Double? = null,
        lat: Double? = null,
        lng: Double? = null,
        locked: Boolean? = null,
    ) = TripSample(
        at = at,
        latitude = lat,
        longitude = lng,
        speedKmh = if (driving) 30.0 else 0.0,
        odometerKm = odo,
        driving = driving,
        locked = locked,
    )

    /**
     * 新规则：ingest **不再自动开段**，起点只能由用户点「开始行程」给出。
     * 用例里的第一帧统一走这里，等价于「点了开始 → 这一帧就是起点」。
     */
    private fun started(sample: TripSample): TripOverview =
        TripRules.start(TripOverview("VIN"), sample).first

    @Test
    fun `行驶判据看速度和挡位`() {
        assertTrue(TripRules.isDriving(30.0, "P 挡"))
        assertTrue(TripRules.isDriving(TripRules.MIN_DRIVING_KMH, "P 挡"))
        assertTrue(TripRules.isDriving(0.0, "D 挡"))
        assertTrue(TripRules.isDriving(0.0, "R"))
        assertTrue(TripRules.isDriving(0.0, "N"))
        // 地库挪车 / GPS 噪声能摸到 1~2 km/h，不能再算「在开」（旧阈值 1 km/h 的坑）
        assertTrue(!TripRules.isDriving(1.1, "P 挡"))
        assertTrue(!TripRules.isDriving(1.0, "P 挡"))
        assertTrue(!TripRules.isDriving(null, null))
        // 车端熄火 / 信号缺失会下发 UNKNOWN、NONE：旧实现用 contains("N") 把它当成空挡，
        // 于是一打开 App 就「正在记录」（用户 2026-11-06 反馈）。必须精确匹配。
        assertTrue(!TripRules.isDriving(0.0, "UNKNOWN"))
        assertTrue(!TripRules.isDriving(0.0, "NONE"))
        assertTrue(!TripRules.isDriving(0.0, "P"))
        assertEquals(27.0, TripRules.parseSpeedKmh("27 km/h")!!, 0.001)
        assertNull(TripRules.parseSpeedKmh("--"))
    }

    @Test
    fun `20秒内同状态的重复刷新只留一个点`() {
        var state = started(point(t0, true, 100.0))
        val unchanged = TripRules.ingest(state, point(t0 + 5_000, true, 100.0))
        assertEquals(state, unchanged)
        assertEquals(1, unchanged.open.size)
    }

    @Test
    fun `短停不拆段 宽限期内又动起来继续同一段`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, false, 100.0))
        assertEquals(t0 + 60_000, state.pausedSince)
        state = TripRules.ingest(state, point(t0 + 120_000, true, 101.0, 39.91, 116.41))
        assertTrue(state.trips.isEmpty())
        assertNull(state.pausedSince)
        assertTrue(state.open.size >= 3)
    }

    @Test
    fun `停车超过宽限期才收尾 结束时间取刚停下那一帧`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        val stoppedAt = t0 + 120_000
        state = TripRules.ingest(state, point(stoppedAt, false, 105.0))
        assertTrue(state.trips.isEmpty())
        state = TripRules.ingest(
            state,
            point(stoppedAt + TripRules.PAUSE_GRACE_MS, false, 105.0),
        )
        assertEquals(1, state.trips.size)
        val trip = state.trips.single()
        assertEquals(t0, trip.startAt)
        assertEquals(stoppedAt, trip.endAt)
        assertEquals(5.0, trip.distanceKm, 0.001)
        assertTrue(!trip.distanceEstimated)
        assertTrue(state.open.isEmpty())
    }

    @Test
    fun `冻结超过30分钟按最后一次真实读数收尾`() {
        var state = started(point(t0, true, 100.0))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 105.0))
        state = TripRules.ingest(
            state,
            point(t0 + 60_000 + TripRules.STALE_TRIP_MS + 1_000, true, 110.0),
        )
        assertEquals(1, state.trips.size)
        assertEquals(t0 + 60_000, state.trips.single().endAt)
        // 冻结只收尾、不自动续段：新规则下要用户再点一次「开始」才有下一段
        assertEquals(0, state.open.size)
    }

    @Test
    fun `停车后App被切走很久 结束时间仍是刚停下那一刻`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 105.0, 39.91, 116.41))
        val stoppedAt = t0 + 120_000
        state = TripRules.ingest(state, point(stoppedAt, false, 105.0))
        // 半个多小时后才再次打开 App，车已经停好
        state = TripRules.ingest(
            state,
            point(stoppedAt + TripRules.STALE_TRIP_MS + 1_000, false, 105.0),
        )
        assertEquals(1, state.trips.size)
        assertEquals(stoppedAt, state.trips.single().endAt)
        assertEquals(120_000L, state.trips.single().durationMs)
    }

    @Test
    fun `表显里程缺失时按采样点估算并打标`() {
        val points = listOf(
            point(t0, true, null, 39.90, 116.40),
            point(t0 + 60_000, true, null, 39.91, 116.40),
        )
        val trip = TripRules.finish(points, t0 + 60_000)
        assertNotNull(trip)
        assertTrue(trip!!.distanceEstimated)
        assertEquals(1.11, trip.distanceKm, 0.05)
    }

    @Test
    fun `太短不生成行程 行驶过但没读到里程变化则存为0km`() {
        assertNull(TripRules.finish(listOf(point(t0, true, 100.0)), t0 + 60_000))
        assertNull(
            TripRules.finish(
                listOf(point(t0, true, 100.0), point(t0 + 10_000, true, 100.0)),
                t0 + 10_000,
            ),
        )
        // 车明确报过行驶帧，只是车机没把里程 / 坐标更新上来：宁可存 0 km 也别整趟空白
        val trip = TripRules.finish(
            listOf(point(t0, true, 100.0), point(t0 + 60_000, true, 100.0)),
            t0 + 60_000,
        )
        assertNotNull(trip)
        assertEquals(0.0, trip!!.distanceKm, 0.0)
        assertTrue(trip.distanceEstimated)
    }

    @Test
    fun `平均速度用里程除以时长`() {
        val trip = TripRecord(
            id = t0,
            startAt = t0,
            endAt = t0 + 120_000,
            points = emptyList(),
            distanceKm = 5.0,
            distanceEstimated = false,
        )
        assertEquals(150.0, trip.averageKmh!!, 0.001)
        assertNull(
            trip.copy(endAt = t0 + 20_000).averageKmh,
        )
    }

    @Test
    fun `裁剪只保留最近90天且最多300段`() {
        val now = t0 + 365L * 86_400_000
        val base = TripRecord(
            id = 1, startAt = t0, endAt = t0 + 60_000,
            points = emptyList(), distanceKm = 1.0, distanceEstimated = false,
        )
        val old = base.copy(id = 2, endAt = now - 100L * 86_400_000)
        val fresh = (3..305).map { base.copy(id = it.toLong(), endAt = now - it * 1_000L) }
        val pruned = TripRules.prune(
            TripOverview("VIN", trips = listOf(old) + fresh),
            now,
        )
        assertEquals(TripRules.MAX_TRIPS, pruned.trips.size)
        assertTrue(pruned.trips.none { it.id == 2L })
    }

    @Test
    fun `行程数据JSON往返不丢字段`() {
        val trip = TripRecord(
            id = t0,
            startAt = t0,
            endAt = t0 + 120_000,
            points = listOf(point(t0, true, 100.0, 39.9, 116.4), point(t0 + 60_000, false, 105.0)),
            distanceKm = 5.0,
            distanceEstimated = false,
            startLat = 39.9,
            startLng = 116.4,
            endLat = 39.91,
            endLng = 116.41,
            startLabel = "望京 SOHO",
            endLabel = "奥林匹克公园",
        )
        val state = TripOverview(
            vin = "VIN",
            open = listOf(point(t0 + 180_000, true, 105.0)),
            pausedSince = null,
            trips = listOf(trip),
        )
        assertEquals(state, TripOverview.fromJson("VIN", state.toJson()))
    }

    @Test
    fun `时长与数字格式`() {
        assertEquals("28 分", TripFormat.duration(28 * 60_000L))
        assertEquals("1 小时 12 分", TripFormat.duration(72 * 60_000L))
        assertEquals("12.6", TripFormat.km(12.64))
        assertEquals("27", TripFormat.speed(27.4))
        assertTrue(TripFormat.dayKey(t0).matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(TripFormat.monthKey(t0).matches(Regex("\\d{4}-\\d{2}")))
    }

    @Test
    fun `首页卡时间段带今天昨天`() {
        // 卡片只放最近一趟，光给 HH:mm 看不出是哪天，所以带上「今天 / 昨天 / 月日」。
        val hhmm = TripFormat.time(t0)
        assertEquals("今天 $hhmm", TripFormat.dayTime(t0, t0))
        assertEquals("昨天 $hhmm", TripFormat.dayTime(t0, t0 + 86_400_000L))

        val older = t0 - 3 * 86_400_000L
        val text = TripFormat.dayTime(older, t0)
        assertTrue(text, text.endsWith(" " + TripFormat.time(older)))
        assertTrue(text, text.contains("月") && text.contains("日"))
        assertFalse(text, text.startsWith("今天") || text.startsWith("昨天"))
    }

    @Test
    fun `按月份筛选与合计`() {
        val trip = TripRecord(
            id = 1, startAt = t0, endAt = t0 + 60_000,
            points = emptyList(), distanceKm = 10.0, distanceEstimated = false,
        )
        val state = TripOverview(
            vin = "VIN",
            trips = listOf(trip, trip.copy(id = 2, endAt = t0 + 120_000)),
        )
        val key = TripFormat.monthKey(t0)
        assertEquals(2, state.tripsOfMonth(key).size)
        assertEquals(20.0, com.leapcontrol.app.trip.TripTotals.of(state.tripsOfMonth(key)).km, 0.001)
        assertEquals(listOf(key), state.monthKeys())
        assertEquals(20.0, state.recentKm(t0 + 60_000), 0.001)
    }

    @Test
    fun `月份选择器拼出的key和磁盘格式一致`() {
        // 写成 "2026-1" 就跟磁盘里的 "2026-01" 对不上，选了等于没选
        assertEquals("2026-01", TripFormat.monthKeyOf(2026, 1))
        assertEquals("2026-11", TripFormat.monthKeyOf(2026, 11))
        assertEquals("2025-12", TripFormat.monthKeyOf(2025, 12))
        // 越界也夹住，别拼出 "2026-13" 这种永远选不中的月份
        assertEquals("2026-12", TripFormat.monthKeyOf(2026, 13))
        assertEquals("2026-01", TripFormat.monthKeyOf(2026, 0))
    }

    @Test
    fun `点选的月份能筛出那个月的行程`() {
        val cal = java.util.Calendar.getInstance(java.util.Locale.CHINA)
        cal.clear()
        cal.set(2026, java.util.Calendar.JANUARY, 15, 10, 0)
        val janAt = cal.timeInMillis
        cal.set(2026, java.util.Calendar.FEBRUARY, 15, 10, 0)
        val febAt = cal.timeInMillis
        val trip = TripRecord(
            id = 1, startAt = janAt - 60_000, endAt = janAt,
            points = emptyList(), distanceKm = 8.0, distanceEstimated = false,
        )
        val state = TripOverview(
            vin = "VIN",
            trips = listOf(trip, trip.copy(id = 2, startAt = febAt - 60_000, endAt = febAt)),
        )
        assertEquals(listOf("2026-02", "2026-01"), state.monthKeys())
        assertEquals(1, state.tripsOfMonth(TripFormat.monthKeyOf(2026, 1)).size)
        assertEquals(1, state.tripsOfMonth(TripFormat.monthKeyOf(2026, 2)).size)
        assertTrue(state.tripsOfMonth(TripFormat.monthKeyOf(2026, 3)).isEmpty())
    }

    @Test
    fun `进行中行程能算实时读数 但显示不等于结束`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        val live = TripRules.live(state)!!
        assertEquals(t0, live.startAt)
        assertEquals(t0 + 60_000, live.lastAt)
        assertEquals(2, live.sampleCount)
        assertEquals(3.0, live.distanceKm!!, 0.001)
        assertTrue(!live.distanceEstimated)
        assertTrue(!live.paused)
        assertTrue(live.hasLocation)
        assertEquals(30.0, live.lastSpeedKmh!!, 0.001)
        // 关键：读一遍实时数据不能把 open 收进 trips
        assertTrue(state.trips.isEmpty())
        assertEquals(2, state.open.size)
    }

    @Test
    fun `实时读数允许0公里 车机还没上报新数据也不显示成null`() {
        val state = TripOverview(
            "VIN",
            open = listOf(
                point(t0, true, 100.0, 39.9, 116.4),
                point(t0 + 60_000, true, 100.0, 39.9, 116.4),
            ),
        )
        val live = TripRules.live(state)!!
        assertEquals(0.0, live.distanceKm!!, 0.0)
        assertTrue(live.distanceEstimated)
    }

    @Test
    fun `手动结束把进行中的行程收进历史清单`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        // 停好车又过了两分钟才点结束：结束时间必须是最后一次真实读数，不能算到「现在」
        val (closed, result) = TripRules.closeOpen(state, t0 + 180_000)
        assertEquals(TripCloseResult.SAVED, result)
        assertTrue(closed.open.isEmpty())
        assertNull(closed.pausedSince)
        assertEquals(1, closed.trips.size)
        assertEquals(t0 + 60_000, closed.trips.single().endAt)
        assertEquals(3.0, closed.trips.single().distanceKm, 0.001)
    }

    @Test
    fun `手动结束且已进停车宽限期 结束时间取刚停下那一帧`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        state = TripRules.ingest(state, point(t0 + 120_000, false, 103.0, 39.91, 116.41))
        val (closed, result) = TripRules.closeOpen(state, t0 + 600_000)
        assertEquals(TripCloseResult.SAVED, result)
        assertEquals(t0 + 120_000, closed.trips.single().endAt)
        assertEquals(120_000L, closed.trips.single().durationMs)
    }

    @Test
    fun `结束失败时保留open不删数据 并给出具体原因`() {
        val one = TripOverview("VIN", open = listOf(point(t0, true, 100.0)))
        val (a, r1) = TripRules.closeOpen(one, t0 + 60_000)
        assertEquals(TripCloseResult.TOO_FEW_POINTS, r1)
        assertEquals(one, a)

        val tooShort = TripOverview(
            "VIN",
            open = listOf(point(t0, true, 100.0), point(t0 + 10_000, true, 101.0)),
        )
        val (b, r2) = TripRules.closeOpen(tooShort, t0 + 60_000)
        assertEquals(TripCloseResult.TOO_SHORT, r2)
        assertEquals(tooShort, b)

        // 防御性构造：一串纯静止帧（真实 ingest 不会产生），不能靠它凑出一段假行程
        val noKm = TripOverview(
            "VIN",
            open = listOf(
                point(t0, false), point(t0 + 60_000, false), point(t0 + 120_000, false),
            ),
        )
        val (c, r3) = TripRules.closeOpen(noKm, t0 + 180_000)
        assertEquals(TripCloseResult.NO_DISTANCE, r3)
        assertEquals(noKm, c)

        val (d, r4) = TripRules.closeOpen(TripOverview("VIN"), t0)
        assertEquals(TripCloseResult.NO_OPEN, r4)
        assertTrue(d.open.isEmpty())
    }

    @Test
    fun `不足 30 秒也能强行结束 只丢这一段不写历史`() {
        val state = TripOverview(
            "VIN",
            open = listOf(point(t0, true, 100.0), point(t0 + 10_000, true, 101.0)),
        )
        // 预判：这一段不会建档。界面凭它在确认框里提前说清「结束后不会记录」，
        // 而不是让用户点完才发现「点了没反应」。
        val live = TripRules.live(state)!!
        assertEquals(TripCloseResult.TOO_SHORT, TripRules.closeGuard(live))
        // 真实结束仍然是「不建档、保留 open」—— 数据本身没被动过
        val (kept, result) = TripRules.closeOpen(state, t0 + 20_000)
        assertEquals(TripCloseResult.TOO_SHORT, result)
        assertEquals(state, kept)
        // 强行结束 = 丢掉这一段：open 清空，历史清单里什么都不多
        val dropped = TripRules.discardOpen(state)
        assertTrue(dropped.open.isEmpty())
        assertNull(dropped.pausedSince)
        assertNull(dropped.lastSample)
        assertTrue(dropped.trips.isEmpty())
    }

    @Test
    fun `预判建档与真实结束保持一致`() {
        // 够 30 秒 + 里程读得到：预判 null，closeOpen 真的存下来
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        val live = TripRules.live(state)!!
        assertNull(TripRules.closeGuard(live))
        assertEquals(TripCloseResult.SAVED, TripRules.closeOpen(state, t0 + 60_000).second)

        // 只有 1 个采样点
        val one = TripOverview("VIN", open = listOf(point(t0, true, 100.0)))
        assertEquals(TripCloseResult.TOO_FEW_POINTS, TripRules.closeGuard(TripRules.live(one)!!))

        // 时长够但一帧行驶都没有、里程也读不到：纯静止帧不算行程
        val still = TripOverview(
            "VIN",
            open = listOf(point(t0, false), point(t0 + 60_000, false)),
        )
        assertEquals(TripCloseResult.NO_DISTANCE, TripRules.closeGuard(TripRules.live(still)!!))
    }

    @Test
    fun `手动结束后再来一个行驶点也不会自己另起一段`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        val (closed, _) = TripRules.closeOpen(state, t0 + 120_000)
        val next = TripRules.ingest(closed, point(t0 + 240_000, true, 110.0))
        // 车还在动，但用户没点「开始」：不能再自己开一段（旧版就是这里开出一堆空行程）
        assertEquals(1, next.trips.size)
        assertTrue(next.open.isEmpty())
        // 点了开始才另起一段，起点就是点下去的那一帧
        val restarted = TripRules.start(next, point(t0 + 240_000, true, 110.0)).first
        assertEquals(1, restarted.open.size)
        assertEquals(t0 + 240_000, restarted.open.single().at)
    }

    @Test
    fun `实时计时器格式`() {
        assertEquals("00:05", TripFormat.clock(5_000))
        assertEquals("12:34", TripFormat.clock(12 * 60_000L + 34_000L))
        assertEquals("1:02:03", TripFormat.clock(3_723_000L))
        assertEquals("00:00", TripFormat.clock(-1L))
    }

    /**
     * 真机反馈的场景：从家开到充电站、插枪开始充电，全程没在行驶中刷到车况。
     * 出发前一帧 P 挡静止、插枪后一帧 P 挡静止，旧算法两帧都直接丢掉 → 整趟空白。
     */
    @Test
    fun `家到充电站 只有两帧静止快照也能补成一段`() {
        var state = started(point(t0, false, 100.0, 39.90, 116.40))
        // 行驶中一帧都没刷到；插枪充电时刷到第二帧，仍是 P 挡，但里程 +6.4km、位置变了
        state = TripRules.ingest(
            state,
            point(t0 + 20 * 60_000L, false, 106.4, 39.95, 116.46),
        )
        assertEquals(2, state.open.size)
        assertEquals(t0, state.open.first().at)
        // 充电采样继续刷静止帧：先记停车时刻，5 分钟后收尾
        state = TripRules.ingest(
            state,
            point(t0 + 25 * 60_000L, false, 106.4, 39.95, 116.46),
        )
        state = TripRules.ingest(
            state,
            point(t0 + 31 * 60_000L, false, 106.4, 39.95, 116.46),
        )
        assertEquals(1, state.trips.size)
        assertEquals(6.4, state.trips.single().distanceKm, 0.001)
        assertEquals(t0 + 25 * 60_000L, state.trips.single().endAt)
        assertTrue(state.open.isEmpty())
    }

    @Test
    fun `纯静止帧不会凭空生成行程`() {
        var state = TripOverview("VIN")
        repeat(4) { i ->
            state = TripRules.ingest(
                state,
                point(t0 + i * 10 * 60_000L, false, 100.0, 39.9, 116.4),
            )
        }
        assertTrue(state.open.isEmpty())
        assertTrue(state.trips.isEmpty())
        assertEquals(t0 + 3 * 10 * 60_000L, state.lastSample!!.at)
    }

    /**
     * 用户 2026-11-06 的原始反馈：「我这才刚打开 APP，就显示行程中了」。
     * 新规则下**没点开始就什么都不记**，哪怕车端把档位报成行驶、里程还跳了一下。
     */
    @Test
    fun `没点开始 行驶帧也不会自己开一段`() {
        var state = TripOverview("VIN")
        state = TripRules.ingest(state, point(t0, true, 100.0, 39.90, 116.40))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 103.0, 39.91, 116.41))
        assertTrue(state.open.isEmpty())
        assertTrue(state.trips.isEmpty())
        assertNull(TripRules.live(state))
        // 快照还是要留：20 秒去重和「点开始时用哪一帧」都靠它
        assertEquals(t0 + 60_000, state.lastSample!!.at)
    }

    /** 点了开始但一直没动车就锁车走了：空段直接清掉，不能留一行「正在记录」。 */
    @Test
    fun `点了开始但没动车 锁车就清掉空段`() {
        var state = started(point(t0, false, 100.0, 39.90, 116.40, locked = false))
        assertEquals(1, state.open.size)
        state = TripRules.ingest(
            state,
            point(t0 + 5 * 60_000L, false, 100.0, 39.90, 116.40, locked = true),
        )
        assertTrue(state.open.isEmpty())
        assertTrue(state.trips.isEmpty())
        assertNull(TripRules.live(state))
    }

    @Test
    fun `行驶过但车机没上报里程变化 也保存为0km而不是整段丢弃`() {
        var state = started(point(t0, true, null, 39.9, 116.4))
        state = TripRules.ingest(state, point(t0 + 60_000, true, null, 39.9, 116.4))
        state = TripRules.ingest(state, point(t0 + 120_000, false, null, 39.9, 116.4))
        state = TripRules.ingest(state, point(t0 + 420_000, false, null, 39.9, 116.4))
        assertEquals(1, state.trips.size)
        assertEquals(0.0, state.trips.single().distanceKm, 0.0)
        assertTrue(state.trips.single().distanceEstimated)
    }

    /** 静止时点刷新不能开出行程：50 米漂移、0.1 km 里程跳变都不算「开过车」。 */
    @Test
    fun `位移判据要够大 静止刷新不会开出假行程`() {
        val atHome = point(t0, false, 100.0, 39.9, 116.4)
        // 表显里程只涨 0.1 km、坐标漂 50 米：都不作数（旧逻辑就是被这两条坑的）
        assertTrue(!TripRules.strongMove(atHome, point(t0 + 60_000, false, 100.1, 39.9005, 116.4)))
        // 表显里程涨 0.5 km 才算开过车
        assertTrue(TripRules.strongMove(atHome, point(t0 + 60_000, false, 100.5, 39.9, 116.4)))
        // 没有里程信号时，坐标要挪 1 km 才算
        assertTrue(TripRules.strongMove(atHome, point(t0 + 60_000, false, null, 39.91, 116.4)))
        assertNull(TripRules.live(TripOverview("VIN")))
    }

    /** 静止时连点刷新：只更新上一帧快照，不许开段、不许写进行中。 */
    @Test
    fun `在家连点刷新不会开始行程`() {
        var state = TripOverview("VIN")
        repeat(5) { i ->
            state = TripRules.ingest(
                state,
                point(t0 + i * 60_000L, false, 100.0, 39.9 + i * 0.0002, 116.4),
            )
            assertTrue(state.open.isEmpty())
        }
        assertNull(TripRules.live(state))
        assertTrue(state.trips.isEmpty())
    }

    @Test
    fun `上一帧快照会随行程数据一起落盘`() {
        val state = TripOverview(
            "VIN",
            lastSample = point(t0, false, 100.0, 39.9, 116.4),
        )
        assertEquals(state, TripOverview.fromJson("VIN", state.toJson()))
    }

    /**
     * 手动开始之后，出发时间就是**点下去那一刻**，不会被之后第一帧行驶数据顶走，
     * 也不会退回「打开 App 的时间」。这条替代了旧版「按第一帧行驶数据推出发时刻」。
     */
    @Test
    fun `出发时间就是点开始那一刻 不被第一帧行驶数据顶走`() {
        val beganAt = t0
        val points = listOf(
            point(beganAt, false, 100.0, 39.90, 116.40),              // 点「开始」时的快照
            point(beganAt + 26 * 60_000L, true, 106.4, 39.95, 116.46),
        )
        val live = TripRules.live(TripOverview("VIN", open = points))!!
        assertEquals(beganAt, live.departureAt)
        assertEquals(beganAt, live.effectiveStartAt)
        assertTrue(!live.departureApprox)
        assertEquals("开始", TripFormat.departureLabel(live).takeLast(2))
    }

    /** 车还停在家门口时 GPS 漂个几十米很常见，不能因此把出发时间算回那一帧。 */
    @Test
    fun `静止帧的GPS漂移不会把出发时间算回在家那一刻`() {
        val startedAt = t0 + 10 * 60_000L
        val points = listOf(
            point(t0, false, 100.0, 39.90, 116.40),
            point(t0 + 60_000, false, 100.0, 39.901, 116.40),
            point(startedAt, true, 100.2, 39.91, 116.41),
        )
        assertEquals(TripDeparture(startedAt, false), TripRules.departure(points))
    }

    /** 手动开始之前记的老数据没有行驶帧：读盘反推仍然按两帧位移中点估算。 */
    @Test
    fun `老数据没有行驶帧时按两帧位移中点估算`() {
        val points = listOf(
            point(t0, false, 100.0, 39.90, 116.40),
            point(t0 + 20 * 60_000L, false, 106.4, 39.95, 116.46),
        )
        assertEquals(TripDeparture(t0 + 10 * 60_000L, true), TripRules.departure(points))
    }

    @Test
    fun `收尾后时长从点开始那一刻算 且出发时间能落盘往返`() {
        val beganAt = t0
        val endAt = t0 + 15 * 60_000L
        val record = TripRules.finish(
            listOf(
                point(beganAt, false, 100.0),
                point(beganAt + 10 * 60_000L, true, 100.2),
                point(endAt, false, 106.4),
            ),
            endAt = endAt,
        )!!
        assertEquals(beganAt, record.departureAt)
        assertEquals(beganAt, record.effectiveStartAt)
        assertEquals(15 * 60_000L, record.durationMs)
        assertFalse(record.departureApprox)
        assertEquals(record, TripRecord.fromJson(record.toJson()))
        assertEquals(TripFormat.time(beganAt), TripFormat.startTime(record))
    }

    /** 老版本落盘的行程没有 dep 字段，读的时候要从采样点反推，不能显示成「在家的时间」。 */
    @Test
    fun `旧数据没有出发时间字段时从采样点反推`() {
        val raw = TripRules.finish(
            listOf(
                point(t0, false, 100.0, 39.90, 116.40),
                point(t0 + 10 * 60_000L, true, 106.4, 39.95, 116.46),
            ),
            endAt = t0 + 10 * 60_000L,
        )!!.toJson()
        raw.remove("dep")
        raw.remove("dapprox")
        val legacy = TripRecord.fromJson(raw)!!
        assertEquals(t0 + 10 * 60_000L, legacy.departureAt)
    }

    /** 99 个点全画出来就是真机上的马赛克：抽稀后固定不超过 6 个刻度且有序。 */
    @Test
    fun `行程条按时间分桶抽稀 99个点也最多6个刻度`() {
        val dense = (0..98).map { i -> point(t0 + i * 60_000L, true, 100.0 + i * 0.1) }
        val marks = TripRules.stripMarks(dense)
        assertEquals(TripRules.MAX_STRIP_MARKS, marks.size)
        assertEquals(marks, marks.sorted())
        assertTrue(marks.all { it in 0f..1f })

        val sparse = listOf(point(t0, true, 100.0), point(t0 + 60_000, true, 101.0))
        assertEquals(2, TripRules.stripMarks(sparse).size)
        assertTrue(TripRules.stripMarks(emptyList()).isEmpty())
    }

    // ---------------- 能耗 ----------------

    /** 带电量 / 续航的采样点。能耗只能靠首末 SOC 差，别的地方拿不到。 */
    private fun powered(
        at: Long,
        soc: Double?,
        range: Double? = null,
        odo: Double? = null,
        driving: Boolean = true,
    ) = TripSample(
        at = at,
        speedKmh = if (driving) 40.0 else 0.0,
        odometerKm = odo,
        driving = driving,
        socPercent = soc,
        rangeKm = range,
    )

    @Test
    fun `能耗按首末电量差算 有容量才换算度数`() {
        val points = listOf(
            powered(t0, soc = 80.0, range = 400.0),
            powered(t0 + 30 * 60_000L, soc = 76.0, range = 380.0),
        )
        val energy = TripRules.energy(points, distanceKm = 21.0, capacityKwh = 78.5)!!
        assertEquals(80.0, energy.startSoc!!, 0.001)
        assertEquals(76.0, energy.endSoc!!, 0.001)
        assertEquals(4.0, energy.usedPercent!!, 0.001)
        assertEquals(3.14, energy.kwh!!, 0.01)
        assertEquals(14.95, energy.per100Km!!, 0.05)
        assertEquals(20.0, energy.rangeUsedKm!!, 0.001)
    }

    /** 没有容量就只给百分比：按车型猜一个度数会把估算写成实测。 */
    @Test
    fun `没填电池容量只给百分比 不编度数`() {
        val points = listOf(
            powered(t0, soc = 80.0),
            powered(t0 + 30 * 60_000L, soc = 76.0),
        )
        val energy = TripRules.energy(points, distanceKm = 21.0, capacityKwh = null)!!
        assertEquals(4.0, energy.usedPercent!!, 0.001)
        assertNull(energy.kwh)
        assertNull(energy.per100Km)
    }

    /** 中途充过电：电量上升，这段没有「消耗」，取绝对值就是假数据。 */
    @Test
    fun `电量上升时不给耗电但保留起终点电量`() {
        val points = listOf(
            powered(t0, soc = 40.0),
            powered(t0 + 30 * 60_000L, soc = 65.0),
        )
        val energy = TripRules.energy(points, 10.0, 78.5)!!
        assertNull(energy.usedPercent)
        assertNull(energy.kwh)
        assertEquals(40.0, energy.startSoc!!, 0.001)
        assertEquals(65.0, energy.endSoc!!, 0.001)
    }

    /** SOC 是整数，0.3 个百分点这种差值是量化噪声，不能当耗电。 */
    @Test
    fun `电量差太小当作没读到`() {
        val points = listOf(
            powered(t0, soc = 80.0),
            powered(t0 + 30 * 60_000L, soc = 79.7),
        )
        assertNull(TripRules.energy(points, 5.0, null)!!.usedPercent)
    }

    /** 短行程折算百公里会把误差放大成离谱的数字，1 km 以下不给。 */
    @Test
    fun `里程不足一公里不折算百公里`() {
        val points = listOf(
            powered(t0, soc = 80.0),
            powered(t0 + 10 * 60_000L, soc = 75.0),
        )
        val energy = TripRules.energy(points, distanceKm = 0.6, capacityKwh = 78.5)!!
        assertNotNull(energy.kwh)
        assertNull(energy.per100Km)
    }

    /** 一帧电量都没读到：整个能耗是 null，界面写「未读到」，不是 0 度。 */
    @Test
    fun `没有电量信号的行程能耗为空`() {
        val points = listOf(point(t0, true, 100.0), point(t0 + 60_000, true, 101.0))
        assertNull(TripRules.energy(points, 1.0, 78.5))
    }

    @Test
    fun `收尾时把能耗一起算进行程`() {
        val points = listOf(
            powered(t0, soc = 80.0, odo = 1000.0),
            powered(t0 + 30 * 60_000L, soc = 75.0, odo = 1010.0),
        )
        val record = TripRules.finish(points, t0 + 30 * 60_000L, batteryKwh = 78.5)!!
        assertEquals(10.0, record.distanceKm, 0.001)
        assertEquals(5.0, record.energy!!.usedPercent!!, 0.001)
        assertEquals(3.925, record.energy!!.kwh!!, 0.01)
        assertEquals(39.25, record.energy!!.per100Km!!, 0.05)
    }

    /** 容量跟着行程数据落盘：用户以后改设置，历史行程的数字不能跟着变。 */
    @Test
    fun `能耗随行程数据一起落盘`() {
        val record = TripRules.finish(
            listOf(
                powered(t0, soc = 80.0, odo = 1000.0),
                powered(t0 + 30 * 60_000L, soc = 75.0, odo = 1010.0),
            ),
            t0 + 30 * 60_000L,
            batteryKwh = 78.5,
        )!!
        val restored = TripRecord.fromJson(record.toJson())!!
        assertEquals(record.energy!!.usedPercent!!, restored.energy!!.usedPercent!!, 0.001)
        assertEquals(record.energy!!.kwh!!, restored.energy!!.kwh!!, 0.001)
        assertEquals(78.5, restored.energy!!.capacityKwh!!, 0.001)
    }

    /** 月度合计只累加算得出度数的段，一段都没有就是 null（界面写 --，不是 0.0）。 */
    @Test
    fun `月合计耗电只统计算得出度数的段`() {
        val a = TripRules.finish(
            listOf(
                powered(t0, soc = 80.0, odo = 1000.0),
                powered(t0 + 30 * 60_000L, soc = 75.0, odo = 1010.0),
            ),
            t0 + 30 * 60_000L,
            batteryKwh = 78.5,
        )!!
        val b = TripRules.finish(
            listOf(
                powered(t0 + 3_600_000L, soc = 70.0, odo = 1010.0),
                powered(t0 + 5_400_000L, soc = 66.0, odo = 1020.0),
            ),
            t0 + 5_400_000L,
            batteryKwh = 78.5,
        )!!
        val totals = com.leapcontrol.app.trip.TripTotals.of(listOf(a, b))
        assertEquals(a.energy!!.kwh!! + b.energy!!.kwh!!, totals.kwh!!, 0.001)
        assertNull(com.leapcontrol.app.trip.TripTotals.of(listOf(b.copy(energy = null))).kwh)
    }

    /** 实时读数也要带能耗：进行中的那段不能等收尾才看到耗电。 */
    @Test
    fun `进行中行程的实时读数带能耗`() {
        val state = TripOverview(
            "VIN",
            open = listOf(
                powered(t0, soc = 80.0, odo = 1000.0),
                powered(t0 + 30 * 60_000L, soc = 75.0, odo = 1010.0),
            ),
            batteryKwh = 78.5,
        )
        val live = TripRules.live(state)!!
        assertEquals(5.0, live.energy!!.usedPercent!!, 0.001)
        assertEquals(3.925, live.energy!!.kwh!!, 0.01)
    }

    @Test
    fun `电池容量随数据落盘并过滤不合理值`() {
        val state = TripOverview("VIN", batteryKwh = 78.5)
        assertEquals(78.5, TripOverview.fromJson("VIN", state.toJson()).batteryKwh!!, 0.001)
        val bad = state.copy(batteryKwh = 9_999.0)
        assertNull(TripOverview.fromJson("VIN", bad.toJson()).batteryKwh)
    }

    /** 以前没填容量、只记到百分比的段，填上容量后应该立刻有度数，不该逼用户重跑。 */
    @Test
    fun `填上电池容量后历史段补算度数`() {
        val recorded = TripRules.finish(
            listOf(
                powered(t0, soc = 80.0, odo = 1000.0),
                powered(t0 + 30 * 60_000L, soc = 75.0, odo = 1010.0),
            ),
            t0 + 30 * 60_000L,
            batteryKwh = null,
        )!!
        assertNull(recorded.energy!!.kwh)
        val priced = TripRules.reprice(TripOverview("VIN", trips = listOf(recorded), batteryKwh = 78.5))
        assertEquals(3.925, priced.trips.single().energy!!.kwh!!, 0.01)
        assertEquals(39.25, priced.trips.single().energy!!.per100Km!!, 0.05)
    }

    /** 没容量时重算必须是 no-op，不能把已有度数抹掉。 */
    @Test
    fun `没有容量时重算不改数据`() {
        val record = TripRules.finish(
            listOf(
                powered(t0, soc = 80.0, odo = 1000.0),
                powered(t0 + 30 * 60_000L, soc = 75.0, odo = 1010.0),
            ),
            t0 + 30 * 60_000L,
            batteryKwh = 78.5,
        )!!
        val state = TripOverview("VIN", trips = listOf(record), batteryKwh = null)
        assertEquals(state, TripRules.reprice(state))
    }

    // ---------------- 解锁起步 / 锁车收尾（2026-11-06 定稿） ----------------

    /** 锁车那一刻这趟就结束，不用再等 5 分钟宽限期。 */
    @Test
    fun `锁车立即结束行程 不等五分钟宽限期`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40, locked = false))
        state = TripRules.ingest(
            state,
            point(t0 + 60_000, true, 101.0, 39.91, 116.41, locked = false),
        )
        state = TripRules.ingest(
            state,
            point(t0 + 120_000, false, 101.0, 39.92, 116.42, locked = true),
        )
        assertEquals(1, state.trips.size)
        assertTrue(state.open.isEmpty())
        assertNull(state.pausedSince)
        assertEquals(1.0, state.trips.single().distanceKm, 0.001)
        assertEquals(t0 + 120_000, state.trips.single().endAt)
    }

    /** 锁着车停在家里刷几次：只更新快照，不开段、不写进行中。 */
    @Test
    fun `锁车状态下刷新不采点也不开新段`() {
        var state = TripOverview("VIN")
        repeat(4) { i ->
            state = TripRules.ingest(
                state,
                point(t0 + i * 60_000L, false, 100.0, 39.9, 116.4, locked = true),
            )
            assertTrue(state.open.isEmpty())
            assertNull(TripRules.live(state))
        }
        assertTrue(state.trips.isEmpty())
        assertEquals(t0 + 3 * 60_000L, state.lastSample!!.at)
    }

    /**
     * 家 → 充电站：行驶中一帧都没刷到，出发前和插枪后两帧都是「锁车 + 静止」。
     * 强位移照样能补记，但必须**当场收尾**，不能一直显示「正在记录」。
     */
    @Test
    fun `点开始后一路没采到行驶帧 锁车时按首末两帧收成一段`() {
        var state = started(point(t0, false, 100.0, 39.90, 116.40, locked = true))
        state = TripRules.ingest(
            state,
            point(t0 + 30 * 60_000L, false, 112.0, 40.00, 116.50, locked = true),
        )
        assertEquals(1, state.trips.size)
        assertTrue(state.open.isEmpty())
        assertEquals(12.0, state.trips.single().distanceKm, 0.001)
        assertEquals(t0 + 30 * 60_000L, state.trips.single().endAt)
    }

    /** 锁车结束一段，解锁后再开是新的一段：这正是「停车办事」被拆开的原因。 */
    @Test
    fun `锁车结束一段 解锁再开是另一段`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40, locked = false))
        state = TripRules.ingest(
            state,
            point(t0 + 60_000, true, 101.0, 39.91, 116.41, locked = false),
        )
        state = TripRules.ingest(
            state,
            point(t0 + 120_000, false, 101.0, 39.92, 116.42, locked = true),
        )
        assertEquals(1, state.trips.size)

        val back = t0 + 10 * 60_000L
        // 新规则：回到车上要再点一次「开始」，不会因为车动了就自己续上一段
        state = TripRules.start(state, point(back, true, 101.0, 39.92, 116.42, locked = false)).first
        state = TripRules.ingest(
            state,
            point(back + 60_000, true, 102.5, 39.95, 116.45, locked = false),
        )
        state = TripRules.ingest(
            state,
            point(back + 120_000, false, 102.5, 39.95, 116.45, locked = true),
        )
        assertEquals(2, state.trips.size)
        assertEquals(1.5, state.trips.last().distanceKm, 0.001)
    }

    /** 开车时车门自动落锁很常见，这时候 locked=true 不能把行程砍断。 */
    @Test
    fun `行驶中自动落锁不算停车 行程继续`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40, locked = false))
        state = TripRules.ingest(
            state,
            point(t0 + 60_000, true, 101.0, 39.91, 116.41, locked = true),
        )
        // 自动落锁发生在行驶中：不许当成停车，段还得接着记
        assertTrue(state.trips.isEmpty())
        assertEquals(2, state.open.size)

        state = TripRules.ingest(
            state,
            point(t0 + 120_000, false, 101.5, 39.92, 116.42, locked = true),
        )
        assertEquals(1, state.trips.size)
        assertEquals(1.5, state.trips.single().distanceKm, 0.001)
    }

    /**
     * 车停稳后 60 秒自动刷新就停了，锁车那一帧往往等用户下次打开 App 才刷到。
     * 25 分钟后再刷到「已锁车」，这趟不能被记成 25 分钟。
     */
    @Test
    fun `锁车帧来得很晚时 结束时间仍按最后一次真实读数`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40, locked = false))
        state = TripRules.ingest(
            state,
            point(t0 + 60_000, true, 103.0, 39.93, 116.43, locked = false),
        )
        // 停车下车，25 分钟后才在 App 里刷到「已锁车」
        state = TripRules.ingest(
            state,
            point(t0 + 26 * 60_000L, false, 103.0, 39.93, 116.43, locked = true),
        )
        assertEquals(1, state.trips.size)
        assertEquals(t0 + 60_000, state.trips.single().endAt)
        assertEquals(60_000L, state.trips.single().durationMs)
    }

    /** 已经采到停车帧、过一会儿才锁车：结束时间取「刚停下」那一帧，不是锁车那一刻。 */
    @Test
    fun `锁车时已有停车帧 结束时间取刚停下那一帧`() {
        var state = started(point(t0, true, 100.0, 39.90, 116.40, locked = false))
        state = TripRules.ingest(
            state,
            point(t0 + 60_000, true, 103.0, 39.93, 116.43, locked = false),
        )
        state = TripRules.ingest(
            state,
            point(t0 + 120_000, false, 103.0, 39.93, 116.43, locked = false),
        )
        state = TripRules.ingest(
            state,
            point(t0 + 8 * 60_000L, false, 103.0, 39.93, 116.43, locked = true),
        )
        assertEquals(1, state.trips.size)
        assertEquals(t0 + 120_000, state.trips.single().endAt)
        assertEquals(3.0, state.trips.single().distanceKm, 0.001)
    }

    /** 锁车状态要能落盘往返；老数据没有 lk 键时必须是「没读到」而不是「已解锁」。 */
    @Test
    fun `锁车状态随采样点落盘 老数据保持未读到`() {
        val locked = point(t0, false, 100.0, 39.9, 116.4, locked = true)
        val unlocked = point(t0, false, 100.0, 39.9, 116.4, locked = false)
        val unknown = point(t0, false, 100.0, 39.9, 116.4)
        assertEquals(true, TripSample.fromJson(locked.toJson())!!.locked)
        assertEquals(false, TripSample.fromJson(unlocked.toJson())!!.locked)
        assertNull(TripSample.fromJson(unknown.toJson())!!.locked)
        assertFalse(unknown.toJson().has("lk"))
    }

    // ---------------- 删除 / 合并 ----------------

    @Test
    fun `删除一段历史行程 找不到时原样返回`() {
        val trip = TripRecord(
            id = 1, startAt = t0, endAt = t0 + 60_000,
            points = emptyList(), distanceKm = 10.0, distanceEstimated = false,
        )
        val state = TripOverview(
            vin = "VIN",
            trips = listOf(trip, trip.copy(id = 2, endAt = t0 + 120_000)),
        )
        val next = TripRules.delete(state, 1)
        assertEquals(1, next.trips.size)
        assertEquals(2L, next.trips.single().id)
        // 找不到就原样返回（同一个对象），UI 不会误报「已删除」
        assertTrue(TripRules.delete(state, 99) === state)
    }

    @Test
    fun `合并多段行程 里程用首末表显里程差把中间那段也算进去`() {
        val seg1 = TripRules.finish(
            listOf(
                powered(t0, soc = 80.0, odo = 100.0),
                powered(t0 + 10 * 60_000L, soc = 77.0, odo = 103.0),
            ),
            t0 + 10 * 60_000L,
        )!!.copy(startLabel = "家", endLabel = "超市")
        val seg2 = TripRules.finish(
            listOf(
                powered(t0 + 40 * 60_000L, soc = 76.0, odo = 105.0),
                powered(t0 + 50 * 60_000L, soc = 73.0, odo = 107.0),
            ),
            t0 + 50 * 60_000L,
        )!!.copy(startLabel = "超市", endLabel = "公司")
        val state = TripOverview("VIN", trips = listOf(seg1, seg2))

        val (merged, result) = TripRules.merge(state, listOf(seg1.id, seg2.id))
        assertEquals(TripMergeResult.MERGED, result)
        assertEquals(1, merged.trips.size)
        val one = merged.trips.single()
        // 100 → 107：中间那 2 km 没采到，但表显里程把它算进来了，比两段相加诚实
        assertEquals(7.0, one.distanceKm, 0.001)
        assertFalse(one.distanceEstimated)
        assertEquals(4, one.points.size)
        assertEquals("家", one.startLabel)
        assertEquals("公司", one.endLabel)
        assertEquals(t0, one.startAt)
        assertEquals(t0 + 50 * 60_000L, one.endAt)
        assertEquals(50 * 60_000L, one.durationMs)
    }

    /** 中间充过电时，拿首末电量差会算成负数；把各段的用电百分点加起来才对。 */
    @Test
    fun `合并时各段用电相加 中间充过电也不出现负数`() {
        val seg1 = TripRules.finish(
            listOf(
                powered(t0, soc = 80.0, odo = 100.0),
                powered(t0 + 10 * 60_000L, soc = 77.0, odo = 103.0),
            ),
            t0 + 10 * 60_000L,
        )!!
        // 第二段出发前充了一次电：SOC 从 90 掉到 86
        val seg2 = TripRules.finish(
            listOf(
                powered(t0 + 40 * 60_000L, soc = 90.0, odo = 105.0),
                powered(t0 + 50 * 60_000L, soc = 86.0, odo = 107.0),
            ),
            t0 + 50 * 60_000L,
        )!!
        val state = TripOverview("VIN", trips = listOf(seg1, seg2), batteryKwh = 60.0)
        val (merged, _) = TripRules.merge(state, listOf(seg1.id, seg2.id))
        val energy = merged.trips.single().energy!!
        assertEquals(7.0, energy.usedPercent!!, 0.001)   // 3% + 4%
        assertEquals(4.2, energy.kwh!!, 0.01)
    }

    @Test
    fun `合并不足两段或找不到时不改数据`() {
        val trip = TripRecord(
            id = 1, startAt = t0, endAt = t0 + 60_000,
            points = emptyList(), distanceKm = 10.0, distanceEstimated = false,
        )
        val state = TripOverview("VIN", trips = listOf(trip))
        val (next, result) = TripRules.merge(state, listOf(1L))
        assertEquals(TripMergeResult.TOO_FEW, result)
        assertTrue(next === state)
        val missing = TripRules.merge(state, listOf(1L, 2L))
        assertEquals(TripMergeResult.NOT_FOUND, missing.second)
        assertTrue(missing.first === state)
    }

    // ---- 自动开始（用户自己开的开关，默认关）----

    /**
     * 自动模式的核心：**连续两帧**都在行驶才开段。
     * 单帧行驶只更新 lastSample —— 那正是「刚打开 App 就显示行程中」的来源。
     */
    @Test
    fun `自动开始要连续两帧行驶 单帧不算`() {
        var state = TripRules.ingest(TripOverview("VIN"), point(t0, false, 100.0), autoStart = true)
        assertTrue(state.open.isEmpty())

        // 第一帧行驶，但上一帧还在静止：证据不够，不开段
        state = TripRules.ingest(state, point(t0 + 60_000, true, 100.4), autoStart = true)
        assertTrue(state.open.isEmpty())

        // 第二帧行驶（上一帧也是行驶）：开段，起点补上第一帧行驶，出发时间才准
        state = TripRules.ingest(state, point(t0 + 120_000, true, 101.0), autoStart = true)
        assertEquals(2, state.open.size)
        assertEquals(t0 + 60_000, state.open.first().at)
        assertEquals(t0 + 120_000, state.open.last().at)
    }

    /** 默认手动：没开开关时，车开着刷两帧也不会自己开一段。 */
    @Test
    fun `自动开始关着时 连续两帧行驶也不开段`() {
        var state = TripRules.ingest(TripOverview("VIN"), point(t0, true, 100.0))
        state = TripRules.ingest(state, point(t0 + 60_000, true, 101.0))
        assertTrue(state.open.isEmpty())
        assertTrue(state.trips.isEmpty())
    }

    /** 两帧行驶隔太久（>10 分钟）不算连续：昨天的行驶帧不能给今天当证据。 */
    @Test
    fun `自动开始两帧隔太久不算连续`() {
        var state = TripRules.ingest(TripOverview("VIN"), point(t0, true, 100.0), autoStart = true)
        val late = t0 + TripRules.MAX_AUTO_GAP_MS + 60_000
        state = TripRules.ingest(state, point(late, true, 120.0), autoStart = true)
        assertTrue(state.open.isEmpty())
    }

    /** 行驶中自动落锁是常态：不能因为 prev.locked=true 就判成「没在开」。 */
    @Test
    fun `自动开始不因行驶中自动落锁而失效`() {
        var state = TripRules.ingest(
            TripOverview("VIN"),
            point(t0, true, 100.0, locked = true),
            autoStart = true,
        )
        state = TripRules.ingest(state, point(t0 + 60_000, true, 101.0, locked = true), autoStart = true)
        assertEquals(2, state.open.size)
    }
}
