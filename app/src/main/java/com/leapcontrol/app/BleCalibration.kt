package com.leapcontrol.app.bluetooth

/** Raw controller calibration parameters; the protocol does not establish units in meters. */
data class BleCalibration(
    val distanceCalibration: Int = 56,
    val coefficientHundredths: Int = 200,
    val unlockCalibration: Int = 8,
    val lockCalibration: Int = 16
) {
    init {
        require(distanceCalibration in BYTE_PROTOCOL_RANGE &&
            coefficientHundredths in COEFFICIENT_PROTOCOL_RANGE &&
            unlockCalibration in BYTE_PROTOCOL_RANGE && lockCalibration in BYTE_PROTOCOL_RANGE) {
            "Bluetooth calibration exceeds the protocol field width"
        }
    }

    fun toProtocolText(): String =
        "$distanceCalibration;${coefficientHundredths / 100}." +
            "${(coefficientHundredths % 100).toString().padStart(2, '0')};" +
            "${unlockCalibration.toString().padStart(2, '0')};${lockCalibration.toString().padStart(2, '0')}"

    /**
     * 落锁阈值的安全下限：至少比解锁远 [LOCK_UNLOCK_GAP]。
     *
     * 两个阈值贴太近（或者落锁反而比解锁近）会在临界点来回触发：
     * 走远一点就锁、退半步又解锁，车身反复弹把手。用户能调的只有这两个数，
     * 所以这道夹紧必须写在模型里 —— 界面、导入、老数据恢复走的都是同一个出口。
     */
    val safeLock: Int get() = maxOf(lockCalibration, unlockCalibration + LOCK_UNLOCK_GAP)

    /** 当前组合是否已经满足安全间距（不满足时调用方要告诉用户「已自动上调」）。 */
    val lockNeedsLift: Boolean get() = safeLock != lockCalibration

    fun withSafeLock(): BleCalibration =
        if (lockNeedsLift) copy(lockCalibration = safeLock.coerceAtMost(BYTE_PROTOCOL_RANGE.last)) else this

    fun encoded(): ByteArray = byteArrayOf(
        distanceCalibration.toByte(),
        coefficientHundredths.toByte(),
        (coefficientHundredths ushr 8).toByte(),
        unlockCalibration.toByte(),
        lockCalibration.toByte()
    )

    companion object {
        /**
         * 落锁阈值与解锁阈值之间至少要留的差值。
         *
         * 协议本身没有单位（没法换算成米），这个 4 是相对量：它保证落锁明显晚于解锁，
         * 而不是两个判定叠在同一个点上。
         */
        const val LOCK_UNLOCK_GAP = 4

        val BYTE_PROTOCOL_RANGE: IntRange = 0..255
        val COEFFICIENT_PROTOCOL_RANGE: IntRange = 0..65_535
        val DEFAULT = BleCalibration()
        val C16_DEFAULT = BleCalibration(
            distanceCalibration = 69,
            coefficientHundredths = 100,
            unlockCalibration = 4,
            lockCalibration = 21
        )
        private val TEXT = Regex("([0-9]{1,3});([0-9]{1,3})\\.([0-9]{2});([0-9]{1,3});([0-9]{1,3})")

        fun defaultForModel(carType: String?): BleCalibration {
            val model = carType.orEmpty().uppercase()
            return if (model.contains("C16") || model.contains("C10")) {
                C16_DEFAULT
            } else {
                DEFAULT
            }
        }

        fun parseOrNull(value: String): BleCalibration? {
            if (value.length > 32) return null
            val fields = TEXT.matchEntire(value)?.groupValues ?: return null
            return runCatching {
                BleCalibration(
                    fields[1].toInt(), fields[2].toInt() * 100 + fields[3].toInt(),
                    fields[4].toInt(), fields[5].toInt()
                )
            }.getOrNull()
        }
    }
}
