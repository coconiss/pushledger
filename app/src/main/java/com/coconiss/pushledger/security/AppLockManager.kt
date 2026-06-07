package com.coconiss.pushledger.security

import android.app.Activity
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import com.coconiss.pushledger.data.SettingsStore
import java.security.MessageDigest

class AppLockManager(private val settings: SettingsStore) {
    fun setPin(pin: String) {
        settings.pinHash = hash(pin)
        settings.appLockEnabled = true
    }

    fun clear() {
        settings.pinHash = null
        settings.appLockEnabled = false
    }

    fun verify(pin: String): Boolean = settings.pinHash == hash(pin)

    fun canUsePlatformBiometric(): Boolean = Build.VERSION.SDK_INT >= 28

    fun authenticateWithBiometric(
        activity: Activity,
        onSuccess: () -> Unit,
        onFallback: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < 28) {
            onFallback()
            return
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("PushLedger 잠금 해제")
            .setSubtitle("등록된 생체 인증으로 앱을 엽니다")
            .setNegativeButton("PIN 사용", activity.mainExecutor) { _, _ -> onFallback() }
            .build()

        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("push-ledger:$pin").toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
