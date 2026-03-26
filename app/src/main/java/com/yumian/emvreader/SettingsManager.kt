package com.yumian.emvreader

import android.content.Context
import androidx.biometric.BiometricManager

object SettingsManager {
    private const val PREF_NAME = "emv_reader_settings"
    private const val KEY_BIOMETRIC = "biometric_enabled"

    fun isBiometricEnabled(context: Context): Boolean {
        // If device is not secure/cannot authenticate, return false.
        // This ensures MainActivity does not try to authenticate when impossible.
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            return false
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_BIOMETRIC)) {
            // Default to enabled if capable
            return true
        }
        return prefs.getBoolean(KEY_BIOMETRIC, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }
}
