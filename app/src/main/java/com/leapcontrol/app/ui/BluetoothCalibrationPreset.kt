package com.leapcontrol.app.ui

import com.leapcontrol.app.bluetooth.BleCalibration
import java.math.BigDecimal

/** 蓝牙钥匙感应距离档位，供蓝牙被动配置面板提供快捷选择。 */
enum class CalibrationPreset(val label: String, val desc: String) {
    CLOSE("贴近车身", "极近感应 · 适合车门旁防误开"),
    STANDARD("标准距离", "平衡感应 · 官方平衡推荐"),
    FAR("较远感应", "远距感应 · 靠近提前迎宾")
}

internal fun getPresetCalibration(preset: CalibrationPreset, isC16: Boolean): BleCalibration = when (preset) {
    CalibrationPreset.CLOSE -> if (isC16) BleCalibration(69, 100, 2, 21) else BleCalibration(56, 200, 4, 16)
    CalibrationPreset.STANDARD -> if (isC16) BleCalibration.C16_DEFAULT else BleCalibration.DEFAULT
    CalibrationPreset.FAR -> if (isC16) BleCalibration(69, 100, 7, 21) else BleCalibration(56, 200, 11, 16)
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
