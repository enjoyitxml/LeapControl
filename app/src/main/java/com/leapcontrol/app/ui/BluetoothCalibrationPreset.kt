package com.leapcontrol.app.ui

import com.leapcontrol.app.bluetooth.BleCalibration
import java.math.BigDecimal

/**
 * 蓝牙钥匙的两组感应距离档位。
 *
 * 以前三档只改「解锁」那一格，落锁阈值恒等于车型默认值 —— 用户抱怨「自动锁车太远」时，
 * 无论选哪一档都毫无变化。现在解锁与落锁各自一组：想更近锁车就单独把落锁调近，
 * 不必连累解锁距离。
 *
 * 两组都只改自己那一格，`distanceCalibration` / `coefficientHundredths`（车型基线）不动。
 */
enum class UnlockPreset(val label: String, val desc: String) {
    CLOSE("贴近", "走到车门边才弹把手"),
    STANDARD("标准", "官方平衡值"),
    FAR("较远", "提前迎宾"),
}

enum class LockPreset(val label: String, val desc: String) {
    NEAR("更近", "走开几步就锁"),
    STANDARD("标准", "官方平衡值"),
    FAR("更远", "走远才锁，防误锁"),
}

/**
 * 解锁档位的原始数值（还没夹紧落锁间距）。
 *
 * 只换第 3 格，其余沿用车型基线 —— 车型基线（56;2.00 / 69;1.00）是车端控制器的标定，
 * 跟着车型走，不该被「距离档位」改掉。
 */
internal fun unlockCalibrationFor(
    preset: UnlockPreset,
    isC16: Boolean,
    base: BleCalibration = if (isC16) BleCalibration.C16_DEFAULT else BleCalibration.DEFAULT,
): BleCalibration {
    val unlock = when (preset) {
        UnlockPreset.CLOSE -> if (isC16) 2 else 4
        UnlockPreset.STANDARD -> if (isC16) 4 else 8
        UnlockPreset.FAR -> if (isC16) 7 else 11
    }
    return base.copy(unlockCalibration = unlock).withSafeLock()
}

/**
 * 落锁档位的数值。
 *
 * 默认值就是车型默认（普通 16 / C16·C10 21）—— 「标准」这一档必须与老版本一致，
 * 否则升级后所有没动过设置的人会突然换一个落锁距离。
 * `更近` 明显收小，这是本次要解决的问题；`更远` 给喜欢提前落锁的人留一条路。
 */
internal fun lockCalibrationFor(
    preset: LockPreset,
    isC16: Boolean,
    base: BleCalibration = if (isC16) BleCalibration.C16_DEFAULT else BleCalibration.DEFAULT,
): BleCalibration {
    val lock = when (preset) {
        LockPreset.NEAR -> if (isC16) 12 else 10
        LockPreset.STANDARD -> if (isC16) 21 else 16
        LockPreset.FAR -> if (isC16) 26 else 20
    }
    return base.copy(lockCalibration = lock).withSafeLock()
}

/** 当前标定落在解锁的哪一档；三个都不是（手工导入过）返回 null，界面上三格都不亮。 */
internal fun matchUnlockPreset(current: BleCalibration, isC16: Boolean): UnlockPreset? =
    UnlockPreset.entries.firstOrNull {
        unlockCalibrationFor(it, isC16, current).unlockCalibration == current.unlockCalibration
    }

/**
 * 当前标定落在落锁的哪一档。
 *
 * 用 `withSafeLock()` 之后的值比较：解锁选得远时，落锁档位会被自动抬到
 * `unlock + 4`，标称值对不上，但用户点的那一档仍应显示为选中。
 */
internal fun matchLockPreset(current: BleCalibration, isC16: Boolean): LockPreset? =
    LockPreset.entries.firstOrNull {
        lockCalibrationFor(it, isC16, current).lockCalibration == current.lockCalibration
    }

/** 蓝牙四元组标定输入的解析模型，负责把用户输入校验为可下发的标定参数。 */
internal data class BluetoothCalibrationInput(
    val signal: String,
    val coefficient: String,
    val unlock: String,
    val lock: String
) {
    fun parseOrNull(): BleCalibration? {
        if (!INTEGER.matches(signal) || !DECIMAL.matches(coefficient) ||
            !INTEGER.matches(unlock) || !INTEGER.matches(lock)) return null
        return runCatching {
            BleCalibration(signal.toInt(), BigDecimal(coefficient).movePointRight(2).intValueExact(),
                unlock.toInt(), lock.toInt())
        }.getOrNull()
    }

    companion object {
        private val INTEGER = Regex("[0-9]{1,3}")
        private val DECIMAL = Regex("[0-9]{1,3}(?:\\.[0-9]{0,2})?")

        fun from(calibration: BleCalibration): BluetoothCalibrationInput = BluetoothCalibrationInput(
            calibration.distanceCalibration.toString(),
            "${calibration.coefficientHundredths / 100}." +
                (calibration.coefficientHundredths % 100).toString().padStart(2, '0'),
            calibration.unlockCalibration.toString(), calibration.lockCalibration.toString()
        )
    }
}

internal fun bluetoothCalibrationVehicleStatus(applied: Boolean, pending: Boolean): String = when {
    pending -> "等待车辆应用"
    applied -> "车辆已应用"
    else -> "车辆尚未确认"
}
