package com.leapcontrol.app.bluetooth

object BleAccessPolicy {
    fun isSupportedKeyType(keyType: Int?): Boolean = keyType == 0 || keyType == 1

    fun isCertificateReady(certificate: BleKeyCertificate?, selectedVin: String): Boolean =
        certificate != null && selectedVin.isNotBlank() && certificate.vin == selectedVin &&
            isSupportedKeyType(certificate.keyType)

    fun canScan(phase: BleConnectionPhase, certificateLoading: Boolean): Boolean =
        !certificateLoading && phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)

    fun canSyncCertificate(phase: BleConnectionPhase, certificateLoading: Boolean): Boolean =
        !certificateLoading && phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.FAILED)

    fun canConnect(phase: BleConnectionPhase, certificateLoading: Boolean, certificateReady: Boolean): Boolean =
        certificateReady && !certificateLoading &&
            phase in setOf(BleConnectionPhase.IDLE, BleConnectionPhase.SCANNING, BleConnectionPhase.FAILED)

    fun canStartCloudLockControl(phase: BleConnectionPhase, cloudBusy: Boolean): Boolean =
        !cloudBusy && phase != BleConnectionPhase.SENDING

    /**
     * 能不能把一版新设置写下去。
     *
     * 只拦「正在写配置 / 正在等锁车回执」和「正在扫描」两种状态：这两种时候写会把帧序搞乱。
     * 连接中 / 发现服务 / 认证中一律放行 —— 设置先落本地，连上后自动补发
     * （见 BleConnectionState.isWritingConfiguration 的说明）。
     */
    fun canApplyConfiguration(
        state: BleConnectionState,
        binding: BleManagedKey?,
        certificate: BleKeyCertificate?,
        configuration: BlePassiveConfiguration
    ): Boolean = !state.isWritingConfiguration && state.phase != BleConnectionPhase.SCANNING && binding != null &&
        (!configuration.enabled || (certificate != null && binding.matchesCertificate(certificate))) &&
        (!configuration.enabled || !configuration.buttonEnabled || (binding.device.protocolMinor ?: 0) >= 9)

    fun certificateMessage(certificate: BleKeyCertificate?, selectedVin: String): String = when {
        certificate == null -> "尚未同步钥匙"
        certificate.vin != selectedVin || selectedVin.isBlank() -> "钥匙与当前车辆不匹配，请重新同步"
        !isSupportedKeyType(certificate.keyType) -> "当前钥匙类型暂不支持连接"
        certificate.keyType == 1 -> "SM2 钥匙已就绪"
        else -> "钥匙已就绪"
    }
}
