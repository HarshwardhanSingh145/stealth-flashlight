package com.qoder.stealthflash.vault

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.qoder.stealthflash.R

enum class BiometricStatus { Available, NoneEnrolled, Unavailable, HardwareMissing }

/**
 * Optional second factor. Biometrics are device-bound and never leave the secure hardware;
 * the PIN digest is the fallback that always works, so this is additive rather than required.
 */
class BiometricAuthenticator(private val context: Context) {

    private val allowedAuthenticators = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun status(): BiometricStatus = when (
        BiometricManager.from(context).canAuthenticate(allowedAuthenticators)
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Available
        BiometricManager.BIOMETRIC_STATUS_NO_BIOMETRICS,
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NoneEnrolled

        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HardwareMissing

        else -> BiometricStatus.Unavailable
    }

    /**
     * @param activity must be a [FragmentActivity]; BiometricPrompt attaches a hidden fragment
     *   to host the system sheet.
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }

                override fun onAuthenticationError(code: Int, errString: CharSequence) {
                    // USER_CANCELED and NEGATIVE_BUTTON are navigation, not failures.
                    if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                        code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        code != BiometricPrompt.ERROR_NO_TRANSACTION
                    ) {
                        onError(errString.toString())
                    }
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_title))
            .setSubtitle(context.getString(R.string.biometric_subtitle))
            .setNegativeButtonText(context.getString(R.string.biometric_negative))
            .setAllowedAuthenticators(allowedAuthenticators)
            .setConfirmationRequired(false)
            .build()

        runCatching { prompt.authenticate(info) }
            .onFailure { onError(context.getString(R.string.biometric_unavailable)) }
    }
}
