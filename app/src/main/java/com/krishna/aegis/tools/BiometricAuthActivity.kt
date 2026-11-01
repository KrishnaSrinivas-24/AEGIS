package com.krishna.aegis.tools

import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.krishna.aegis.telemetry.AegisEvent
import com.krishna.aegis.telemetry.EventLogger

class BiometricAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sensitive Action Detected"
        val executor = ContextCompat.getMainExecutor(this)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AEGIS Security Gate")
            .setSubtitle(reason)
            .setDescription("Verify your biometric identity to authorize this autonomous OS action.")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                EventLogger.log(AegisEvent.BiometricIntercept(reason, approved = true))
                HITLInterceptionWrapper.onBiometricResult(true)
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                EventLogger.log(AegisEvent.BiometricIntercept("$reason ($errString)", approved = false))
                HITLInterceptionWrapper.onBiometricResult(false)
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onDestroy() {
        super.onDestroy()
        HITLInterceptionWrapper.onBiometricResult(false)
    }

    companion object {
        const val EXTRA_REASON = "extra_reason"
    }
}
