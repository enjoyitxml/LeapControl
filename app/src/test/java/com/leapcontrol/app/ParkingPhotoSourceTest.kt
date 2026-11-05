package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 驻车照片「从哪儿来」的规则。
 *
 * 这块最容易出错的地方不是算法，而是**判断**：
 *  - 老用户没设置过 → 必须还是手机拍照，不能因为新增了一项就把他切走；
 *  - 接口失败 ≠ 车型不支持 → 说成「你的车不支持」，用户会真以为自己少个配置；
 *  - 车端照片的时间是**上传时间**，不是我们取回的时间，否则「3 天前停的车」会显示成「刚刚」。
 */
class ParkingPhotoSourceTest {

    // ---- 方案偏好 ----

    @Test
    fun `没设置过时是手机拍照`() {
        assertEquals(ParkingPhotoSource.PHONE, ParkingPhotoSource.fromId(null))
    }

    @Test
    fun `未知值退回手机拍照`() {
        assertEquals(ParkingPhotoSource.PHONE, ParkingPhotoSource.fromId(""))
        assertEquals(ParkingPhotoSource.PHONE, ParkingPhotoSource.fromId("CLOUD"))
        assertEquals(ParkingPhotoSource.PHONE, ParkingPhotoSource.fromId("phone"))
    }

    @Test
    fun `车端照片按存储的名字还原`() {
        assertEquals(ParkingPhotoSource.OFFICIAL, ParkingPhotoSource.fromId("OFFICIAL"))
    }

    @Test
    fun `不支持的说明必须点到部分车型`() {
        // 这句是给用户看的唯一「前置告知」，写丢了就等于没告知
        assertTrue(ParkingPhotoSourceRules.SUPPORT_NOTE.contains("部分车型"))
        assertTrue(ParkingPhotoSourceRules.SUPPORT_NOTE.contains("手机拍照"))
    }

    // ---- 接口结论分类 ----

    @Test
    fun `成功且有图就是取到了`() {
        val r = ChassisPhotoRules.classify(0, "请求成功", "https://x/a.jpg", 1_700_000_000_000L)
        assertTrue(r is ChassisPhotoQuery.Found)
        assertEquals("https://x/a.jpg", (r as ChassisPhotoQuery.Found).photo.fileUrl)
        assertEquals(1_700_000_000_000L, r.photo.uploadTimeMs)
    }

    @Test
    fun `成功但没图是还没上传`() {
        assertEquals(ChassisPhotoQuery.Empty, ChassisPhotoRules.classify(0, "请求成功", "", 0L))
        assertEquals(ChassisPhotoQuery.Empty, ChassisPhotoRules.classify(200, "", "  ", 0L))
    }

    @Test
    fun `拒绝类文案判为不支持`() {
        val r = ChassisPhotoRules.classify(40001, "该车型不支持驻车拍照", "", 0L)
        assertTrue(r is ChassisPhotoQuery.Unsupported)
        val r2 = ChassisPhotoRules.classify(403, "no permission", "", 0L)
        assertTrue(r2 is ChassisPhotoQuery.Unsupported)
    }

    @Test
    fun `普通错误算可重试的失败`() {
        val r = ChassisPhotoRules.classify(500, "系统繁忙，请稍后再试", "", 0L)
        assertTrue(r is ChassisPhotoQuery.Failed)
        // 别说成「不支持」：这种是过一会儿就好的
        assertFalse(ChassisPhotoRules.looksUnsupported("系统繁忙，请稍后再试"))
    }

    @Test
    fun `业务码非零但确实给了图时先用图`() {
        val r = ChassisPhotoRules.classify(1001, "部分成功", "https://x/b.jpg", 0L)
        assertTrue(r is ChassisPhotoQuery.Found)
    }

    @Test
    fun `空文案不算不支持`() {
        assertFalse(ChassisPhotoRules.looksUnsupported(""))
        assertFalse(ChassisPhotoRules.looksUnsupported("   "))
    }

    // ---- 文案 ----

    private val now = 1_700_000_000_000L

    @Test
    fun `车端照片的时间按上传时间算`() {
        assertEquals(
            "车端照片 · 5 分钟前",
            ParkingPhotoRules.officialCaption(true, now - 5 * 60_000L, now),
        )
    }

    @Test
    fun `车端照片没有时间时只说来源`() {
        assertEquals("车端照片", ParkingPhotoRules.officialCaption(true, 0L, now))
    }

    @Test
    fun `没有车端照片时不返回文案`() {
        assertNull(ParkingPhotoRules.officialCaption(false, now - 60_000L, now))
        assertNull(ParkingPhotoRules.captionFor(ParkingPhotoSource.OFFICIAL, false, now, now))
    }

    @Test
    fun `手机方案仍说已拍照`() {
        assertEquals(
            "车位已拍照 · 2 小时前",
            ParkingPhotoRules.captionFor(
                ParkingPhotoSource.PHONE,
                hasPhoto = true,
                savedAt = now - 2 * 3_600_000L,
                now = now,
            ),
        )
    }

    @Test
    fun `详情页的说法区分上传与拍摄`() {
        assertEquals("刚刚由车辆上传", ParkingPhotoRules.officialTakenAt(true, now - 10_000L, now))
        assertEquals(
            "车辆上传于 3 天前",
            ParkingPhotoRules.officialTakenAt(true, now - 3 * 86_400_000L, now),
        )
        // 上传时间读不到 / 落在未来：不编
        assertNull(ParkingPhotoRules.officialTakenAt(true, 0L, now))
        assertNull(ParkingPhotoRules.officialTakenAt(true, now + 60_000L, now))
    }

    // ---- 状态文案 ----

    @Test
    fun `三种失败说的是三件事`() {
        assertEquals(
            "车辆还没有上传这一趟的驻车照片",
            ParkingPhotoSourceRules.statusText(OfficialPhotoStatus.Empty),
        )
        assertTrue(
            ParkingPhotoSourceRules.statusText(OfficialPhotoStatus.Unsupported("x"))!!.contains("不支持"),
        )
        assertTrue(
            ParkingPhotoSourceRules.statusText(OfficialPhotoStatus.Failed("x"))!!.contains("网络"),
        )
    }

    @Test
    fun `取到了或还没开始时不显示状态`() {
        assertNull(ParkingPhotoSourceRules.statusText(OfficialPhotoStatus.Idle))
        assertNull(ParkingPhotoSourceRules.statusText(OfficialPhotoStatus.Ready(0L, 1)))
    }
}
