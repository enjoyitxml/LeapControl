package com.leapcontrol.app

import com.leapcontrol.app.bluetooth.BleAccessPolicy
import com.leapcontrol.app.bluetooth.BleConnectionPhase
import com.leapcontrol.app.bluetooth.BleConnectionState
import com.leapcontrol.app.bluetooth.BleKeyBootPolicy
import com.leapcontrol.app.bluetooth.BleLockAction
import com.leapcontrol.app.bluetooth.BleNearbyDevice
import com.leapcontrol.app.bluetooth.BlePassiveConfiguration
import com.leapcontrol.app.bluetooth.BleReconnectPolicy
import com.leapcontrol.app.bluetooth.BleCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 蓝牙钥匙的门禁与状态机单测。
 *
 * 这些判断决定「首页图标亮不亮」「能不能点开始搜索」「后台要不要自启动」，
 * 都是纯逻辑，不需要真机与 BLE 硬件，放在 JVM 上守住即可。
 */
class BleKeyPolicyTest {

    // ---- 开机 / 升级后自动恢复后台 ----

    @Test
    fun `开机且条件齐备时自动拉起后台钥匙`() {
        assertTrue(
            BleKeyBootPolicy.shouldAutoStart(
                action = android.content.Intent.ACTION_BOOT_COMPLETED,
                accountId = "acc",
                selectedVin = "L1234567890123456",
                hasOpPassword = true,
                needsBackground = true,
            ),
        )
    }

    @Test
    fun `缺少操控密码或未开启后台都不自启动`() {
        assertFalse(
            BleKeyBootPolicy.shouldAutoStart(
                android.content.Intent.ACTION_BOOT_COMPLETED, "acc", "VIN", hasOpPassword = false, needsBackground = true,
            ),
        )
        assertFalse(
            BleKeyBootPolicy.shouldAutoStart(
                android.content.Intent.ACTION_BOOT_COMPLETED, "acc", "VIN", hasOpPassword = true, needsBackground = false,
            ),
        )
    }

    @Test
    fun `其它广播一律不触发自启动`() {
        assertFalse(
            BleKeyBootPolicy.shouldAutoStart(
                android.content.Intent.ACTION_SCREEN_ON, "acc", "VIN", hasOpPassword = true, needsBackground = true,
            ),
        )
        assertFalse(
            BleKeyBootPolicy.shouldAutoStart(null, "acc", "VIN", hasOpPassword = true, needsBackground = true),
        )
    }

    // ---- 证书 / 连接门禁 ----

    @Test
    fun `扫描只在空闲或失败态允许`() {
        assertTrue(BleAccessPolicy.canScan(BleConnectionPhase.IDLE, certificateLoading = false))
        assertTrue(BleAccessPolicy.canScan(BleConnectionPhase.FAILED, certificateLoading = false))
        assertFalse(BleAccessPolicy.canScan(BleConnectionPhase.IDLE, certificateLoading = true))
        assertFalse(BleAccessPolicy.canScan(BleConnectionPhase.READY, certificateLoading = false))
        assertFalse(BleAccessPolicy.canScan(BleConnectionPhase.SCANNING, certificateLoading = false))
    }

    @Test
    fun `连接必须证书就绪且不处于忙碌态`() {
        assertFalse(BleAccessPolicy.canConnect(BleConnectionPhase.IDLE, false, certificateReady = false))
        assertTrue(BleAccessPolicy.canConnect(BleConnectionPhase.SCANNING, false, certificateReady = true))
        assertFalse(BleAccessPolicy.canConnect(BleConnectionPhase.CONNECTING, false, certificateReady = true))
        assertFalse(BleAccessPolicy.canConnect(BleConnectionPhase.READY, false, certificateReady = true))
    }

    @Test
    fun `未绑定车辆时不能写入钥匙设置`() {
        val configuration = BlePassiveConfiguration(enabled = true)
        assertFalse(
            BleAccessPolicy.canApplyConfiguration(
                BleConnectionState(phase = BleConnectionPhase.READY),
                binding = null,
                certificate = null,
                configuration = configuration,
            ),
        )
    }

    @Test
    fun `忙碌状态下不能写入钥匙设置`() {
        assertFalse(
            BleAccessPolicy.canApplyConfiguration(
                BleConnectionState(phase = BleConnectionPhase.SENDING),
                binding = null,
                certificate = null,
                configuration = BlePassiveConfiguration(),
            ),
        )
    }

    // ---- 重连节奏 ----

    @Test
    fun `重连退避逐级拉长且封顶`() {
        assertEquals(2_000L, BleReconnectPolicy.delayMillis(0))
        assertEquals(5_000L, BleReconnectPolicy.delayMillis(1))
        assertEquals(10_000L, BleReconnectPolicy.delayMillis(2))
        assertEquals(20_000L, BleReconnectPolicy.delayMillis(3))
        assertEquals(30_000L, BleReconnectPolicy.delayMillis(4))
        assertEquals(30_000L, BleReconnectPolicy.delayMillis(99))
        // 负值按第 0 次处理，不能返回 0（否则会退化成忙等）
        assertEquals(2_000L, BleReconnectPolicy.delayMillis(-1))
    }

    @Test
    fun `前台不做盲目直连，后台每五次未命中才兜底一次`() {
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = true, consecutiveMissCount = 5))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 0))
        assertFalse(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 4))
        assertTrue(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 5))
        assertTrue(BleReconnectPolicy.shouldAttemptDirectFallback(foreground = false, consecutiveMissCount = 10))
    }

    // ---- 配置与绑定的一致性 ----

    @Test
    fun `关闭总开关时不再需要后台保活`() {
        assertFalse(BlePassiveConfiguration(enabled = false).needsBackground)
        assertTrue(BlePassiveConfiguration(enabled = true).needsBackground)
    }

    @Test
    fun `只有开启态才把自动落锁与自动解锁写进协议帧`() {
        val off = BlePassiveConfiguration(
            enabled = false,
            autoUnlock = true,
            autoLock = true,
            calibration = BleCalibration.DEFAULT,
        )
        // 关闭时功能位必须清零，避免「关了钥匙但车端还在自动解锁」
        val flags = off.reconnectFields(supportsButton = false).let { it.copyOfRange(it.size - 4, it.size) }
        assertEquals(0, flags[0].toInt())
        assertEquals(0, flags[1].toInt())
        assertEquals(0, flags[2].toInt())

        val on = off.copy(enabled = true)
        val onFlags = on.reconnectFields(supportsButton = true).let { it.copyOfRange(it.size - 4, it.size) }
        assertEquals(1, onFlags[0].toInt())
        assertEquals(1, onFlags[1].toInt())
        assertEquals(1, onFlags[2].toInt())
    }

    @Test
    fun `新的车辆扫描结果可以当作连接目标`() {
        val device = BleNearbyDevice("AA:BB:CC:DD:EE:FF", "LEAP C11", -62, protocolMinor = 9)
        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        // 不支持列表里混入非车辆设备时，协议层应当拿到空 minor 而不崩
        val unknown = BleNearbyDevice("AA:BB:CC:DD:EE:FF", "", -90, protocolMinor = null)
        assertEquals(null, unknown.protocolMinor)
    }

    @Test
    fun `锁车动作的指令编号保持稳定`() {
        // 指令编号一旦改动，车端会直接拒收，必须锁死
        assertEquals(1, BleLockAction.UNLOCK.commandId)
        assertEquals(2, BleLockAction.LOCK.commandId)
    }
}
