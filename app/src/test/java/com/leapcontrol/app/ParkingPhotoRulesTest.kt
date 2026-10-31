package com.leapcontrol.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 停车位照片的文案规则。
 *
 * 只有一行字，但两种错法都会让用户犯迷糊：
 *  - 没照片却返回空串 → UI 划出一行空白，像加载失败；
 *  - 文件时间读不到（0）时硬算 → 算出「1970 年」或者一个负数时长。
 */
class ParkingPhotoRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `没有照片时不返回文案`() {
        assertNull(ParkingPhotoRules.caption(hasPhoto = false, savedAt = now - 60_000L, now = now))
    }

    @Test
    fun `刚拍完显示刚刚`() {
        assertEquals("车位已拍照 · 刚刚", captionAgo(30_000L))
    }

    @Test
    fun `按分钟小时天换算`() {
        assertEquals("车位已拍照 · 5 分钟前", captionAgo(5 * 60_000L))
        assertEquals("车位已拍照 · 2 小时前", captionAgo(2 * 3_600_000L))
        assertEquals("车位已拍照 · 3 天前", captionAgo(3 * 86_400_000L))
    }

    @Test
    fun `拍完正好一分钟算一分钟`() {
        assertEquals("车位已拍照 · 1 分钟前", captionAgo(60_000L))
    }

    /** 文件修改时间读不到（部分 ROM 上 lastModified 会返回 0）：只说拍过，不编时间。 */
    @Test
    fun `时间读不到时只说已拍照`() {
        assertEquals("车位已拍照", ParkingPhotoRules.caption(hasPhoto = true, savedAt = 0L, now = now))
    }

    /** 系统时间被往回调过：拍摄时间落在「未来」，这时不能算出一个负数时长。 */
    @Test
    fun `拍摄时间在未来时不显示时长`() {
        assertEquals(
            "车位已拍照",
            ParkingPhotoRules.caption(hasPhoto = true, savedAt = now + 60_000L, now = now),
        )
    }

    private fun captionAgo(ageMs: Long): String? =
        ParkingPhotoRules.caption(hasPhoto = true, savedAt = now - ageMs, now = now)
}
