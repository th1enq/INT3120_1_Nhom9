package com.example.coupleapp.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper class for biometric authentication
 * Provides device biometric security for sensitive features like messaging
 */
object BiometricHelper {
    private const val TAG = "BiometricHelper"
    
    private val _authenticationResult = MutableStateFlow<AuthenticationResult>(AuthenticationResult.None)
    val authenticationResult: StateFlow<AuthenticationResult> = _authenticationResult.asStateFlow()
    
    /**
     * Authentication result states
     */
    sealed class AuthenticationResult {
        object None : AuthenticationResult()
        object Success : AuthenticationResult()
        object Failed : AuthenticationResult()
        data class Error(val message: String) : AuthenticationResult()
        object Cancelled : AuthenticationResult()
    }
    
    /**
     * Check if biometric authentication is available on device
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "Biometric authentication is available")
                true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "No biometric hardware")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "Biometric hardware unavailable")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "No biometric enrolled, but device credential may be available")
                // Device credential (PIN/Pattern/Password) is still available
                true
            }
            else -> false
        }
    }
    
    /**
     * Get biometric authentication type description
     */
    fun getBiometricType(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        
        val hasBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val hasDeviceCredential = biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        
        return when {
            hasBiometric && hasDeviceCredential -> "Fingerprint / Face ID / PIN"
            hasBiometric -> "Fingerprint / Face ID"
            hasDeviceCredential -> "PIN / Pattern / Password"
            else -> "Not available"
        }
    }
    
    /**
     * Show biometric prompt for authentication
     * @param activity The FragmentActivity to show the prompt
     * @param title Title of the prompt
     * @param subtitle Subtitle/description
     * @param negativeButtonText Text for negative button (cancel)
     * @param onResult Callback with authentication result
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentication Required",
        subtitle: String = "Please authenticate to access this feature",
        negativeButtonText: String = "Cancel",
        onResult: (AuthenticationResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Authentication succeeded")
                _authenticationResult.value = AuthenticationResult.Success
                onResult(AuthenticationResult.Success)
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication failed")
                _authenticationResult.value = AuthenticationResult.Failed
                onResult(AuthenticationResult.Failed)
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "Authentication error: $errorCode - $errString")
                
                val result = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthenticationResult.Cancelled
                    else -> AuthenticationResult.Error(errString.toString())
                }
                _authenticationResult.value = result
                onResult(result)
            }
        }
        
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        
        // Build prompt info with both biometric and device credential options
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing biometric prompt", e)
            // Fallback to device credential only
            try {
                val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                biometricPrompt.authenticate(fallbackPromptInfo)
            } catch (e2: Exception) {
                Log.e(TAG, "Error showing fallback prompt", e2)
                onResult(AuthenticationResult.Error("Authentication not available"))
            }
        }
    }
    
    /**
     * Reset authentication result
     */
    fun resetResult() {
        _authenticationResult.value = AuthenticationResult.None
    }
}
