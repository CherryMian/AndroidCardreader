package com.yumian.emvreader

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdProvider {
    private const val PREF_NAME = "device_id_prefs"
    private const val KEY_ID = "device_id"

    /**
     * Returns a stable per-installation device id; falls back to ANDROID_ID if available.
     */
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val generated = androidId ?: UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, generated).apply()
        return generated
    }
}

