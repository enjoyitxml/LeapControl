package com.leapcontrol.app.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 控车二次确认的生物识别封装。
 *
 * 用途：在软件主界面向车端下发指令前，先要求用户验证指纹或设备密码，避免误触。
 * 系统控制中心磁贴（Tiles）直接调用 [AppRuntime.control]，不经过这里，因此无需二次确认。
 *
 * 行为：
 * - 指纹可用时优先指纹；设备已设置锁屏密码 / 图案 / PIN 时，系统弹窗会提供「使用密码」回退。
 * - [onSuccess] 验证通过后回调（主线程）。[onError] 验证失败或被用户取消（如返回键）时回调，
 *   调用方应放弃本次操作。
 * - 传入的 [activity] 必须是 FragmentActivity（ComponentActivity 子类），BiometricPrompt 依赖其
 *   FragmentManager 挂载系统对话框。
 * - 当 [setAllowedAuthenticators] 包含 DEVICE_CREDENTIAL 时不允许设置负面按钮，用户只能靠系统
 *   返回键取消，符合「无二次确认则不下发」的语义。
 */
object BiometricAuth {

    /** 设备是否支持生物识别或设备凭据（用于判断是否需要弹出二次确认）。 */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 弹出系统生物识别 / 设备凭据确认框。
     *
     * @param title 标题，如「确认操作」。
     * @param subtitle 副标题，说明要下发的具体指令，如「请验证以发送『解锁』」。
     * @param onSuccess 验证通过。
     * @param onError 验证失败或用户取消。
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError()
            }
        }
        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
