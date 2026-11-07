package com.himalayasoni.ledger.ui.account

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for your fingerprint (or the phone's PIN/pattern as a fallback) before private entries show.
 * Ledger never sees the fingerprint: Android checks it and only tells us "yes" or "no".
 */
fun unlockWithPhoneLock(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    when (BiometricManager.from(activity).canAuthenticate(allowed)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> { onError("Set up a fingerprint or screen lock on your phone first."); return }
        else -> { onError("This phone can't check a fingerprint or screen lock right now."); return }
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(code: Int, message: CharSequence) {
            // closing the prompt yourself isn't an error worth showing
            if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON && code != BiometricPrompt.ERROR_CANCELED) onError(message.toString())
        }
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Show private expenses")
            .setSubtitle("Use your fingerprint or phone lock")
            .setAllowedAuthenticators(allowed)
            .build(),
    )
}
