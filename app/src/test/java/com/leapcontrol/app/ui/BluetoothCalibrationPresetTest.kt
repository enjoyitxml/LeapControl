package com.leapcontrol.app.ui

import com.leapcontrol.app.bluetooth.BleCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 蓝牙钥匙感应距离档位（2026-11-06 用户反馈「自动锁车距离太远」后拆成两组）。
 *
 * 用户在意的只有两件事：**落锁真的能调近**，以及**调完不会在门口反复上锁**。
 * 这两条都由纯函数保证，所以全部钉成单测。
 */
class BluetoothCalibrationPresetTest {

    private val plainDefault = BleCalibration.DEFAULT
    private val leap3Default = BleCalibration.C16_DEFAULT

    @Test
    fun `默认值落在标准档上，升级后不动设置的人的落锁距离不变`() {
        assertEquals(UnlockPreset.STANDARD, matchUnlockPreset(plainDefault, isC16 = false))
        assertEquals(LockPreset.STANDARD, matchLockPreset(plainDefault, isC16 = false))
        assertEquals(UnlockPreset.STANDARD, matchUnlockPreset(leap3Default, isC16 = true))
        assertEquals(LockPreset.STANDARD, matchLockPreset(leap3Default, isC16 = true))
        // 「标准」必须还原出车型默认值，否则老用户升级就被换了距离
        assertEquals(plainDefault, lockCalibrationFor(LockPreset.STANDARD, isC16 = false))
        assertEquals(leap3Default, lockCalibrationFor(LockPreset.STANDARD, isC16 = true))
    }

    @Test
    fun `落锁能真的调近，而且三档依次变远`() {
        // 这就是用户要的：从 16 收到 12（C16 从 21 收到 12），不再恒定不动。
        // 普通车到不了更小的值 —— 落锁必须比「标准解锁 8」远 4 格以上。
        val plain = LockPreset.entries.map { lockCalibrationFor(it, isC16 = false).lockCalibration }
        assertEquals(listOf(12, 16, 20), plain)
        val leap3 = LockPreset.entries.map { lockCalibrationFor(it, isC16 = true).lockCalibration }
        assertEquals(listOf(12, 21, 26), leap3)
        assertTrue("默认落锁必须比更近档更远", plain[1] > plain[0])
        assertTrue("默认落锁必须比更近档更远", leap3[1] > leap3[0])
    }

    @Test
    fun `解锁三档依次变远`() {
        assertEquals(
            listOf(4, 8, 11),
            UnlockPreset.entries.map { unlockCalibrationFor(it, isC16 = false).unlockCalibration },
        )
        assertEquals(
            listOf(2, 4, 7),
            UnlockPreset.entries.map { unlockCalibrationFor(it, isC16 = true).unlockCalibration },
        )
    }

    @Test
    fun `解锁调到较远时，落锁被自动抬到至少晚四格`() {
        val far = unlockCalibrationFor(UnlockPreset.FAR, isC16 = false)
        val nearest = lockCalibrationFor(LockPreset.NEAR, isC16 = false, base = far)
        // 标称 10 已经不够了，必须被抬到 11 + 4
        assertTrue(nearest.lockCalibration > 10)
        assertEquals(far.unlockCalibration + BleCalibration.LOCK_UNLOCK_GAP, nearest.lockCalibration)
    }

    @Test
    fun `任意组合都满足落锁晚于解锁的硬性间距`() {
        listOf(true, false).forEach { isC16 ->
            UnlockPreset.entries.forEach { unlockPreset ->
                LockPreset.entries.forEach { lockPreset ->
                    val unlock = unlockCalibrationFor(unlockPreset, isC16)
                    val combined = lockCalibrationFor(lockPreset, isC16, base = unlock)
                    assertTrue(
                        "$isC16 / $unlockPreset / $lockPreset 违反间距保护：${combined.toProtocolText()}",
                        combined.lockCalibration >= combined.unlockCalibration + BleCalibration.LOCK_UNLOCK_GAP,
                    )
                }
            }
        }
    }

    @Test
    fun `已合规的组合不会被改动`() {
        val ok = BleCalibration(56, 200, 8, 16)
        assertEquals(ok, ok.withSafeLock())
        assertTrue(!ok.lockNeedsLift)
        // 老数据里落锁比解锁还近的那种，恢复时要被抬起来
        val broken = BleCalibration(56, 200, 8, 6)
        assertTrue(broken.lockNeedsLift)
        assertEquals(12, broken.withSafeLock().lockCalibration)
    }

    @Test
    fun `手工导入的怪值不会冒充某一档`() {
        val custom = BleCalibration(56, 200, 6, 30)
        assertNull(matchUnlockPreset(custom, isC16 = false))
        assertNull(matchLockPreset(custom, isC16 = false))
    }

    @Test
    fun `换档只动自己那一格，车型基线不跟着变`() {
        val before = plainDefault
        val afterUnlock = unlockCalibrationFor(UnlockPreset.FAR, isC16 = false)
        assertEquals(before.distanceCalibration, afterUnlock.distanceCalibration)
        assertEquals(before.coefficientHundredths, afterUnlock.coefficientHundredths)
        assertEquals(before.lockCalibration, afterUnlock.lockCalibration)
        val afterLock = lockCalibrationFor(LockPreset.NEAR, isC16 = false)
        assertEquals(before.unlockCalibration, afterLock.unlockCalibration)
        assertEquals(before.distanceCalibration, afterLock.distanceCalibration)
    }
}
