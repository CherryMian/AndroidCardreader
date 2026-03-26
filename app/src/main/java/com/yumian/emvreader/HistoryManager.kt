package com.yumian.emvreader

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedScan(
    val pan: String,
    val expiry: String,
    val type: String,
    val aid: String,
    val standard: String,
    val timestamp: Long,
    val formattedTime: String,
    val customName: String? = null
)

object HistoryManager {
    private const val PREF_NAME_LEGACY = "scan_history_prefs"
    private const val PREF_NAME_SECURE = "scan_history_secure"
    private const val KEY_HISTORY = "history_json"

    private fun getPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREF_NAME_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasUnencryptedData(context: Context): Boolean {
        // Check if legacy file exists and has data
        val legacyPrefs = context.getSharedPreferences(PREF_NAME_LEGACY, Context.MODE_PRIVATE)
        return legacyPrefs.contains(KEY_HISTORY) && !legacyPrefs.getString(KEY_HISTORY, "[]").equals("[]")
    }

    fun migrateToEncrypted(context: Context) {
        val legacyPrefs = context.getSharedPreferences(PREF_NAME_LEGACY, Context.MODE_PRIVATE)
        val historyJson = legacyPrefs.getString(KEY_HISTORY, "[]")

        if (!historyJson.isNullOrEmpty() && historyJson != "[]") {
            val securePrefs = getPreferences(context)
            // Merge or overwrite? Usually migration implies moving.
            // If secure already has data, we might append. For now simpler to just write if empty or append.
            // Let's safe-append.

            val existingSecureJson = securePrefs.getString(KEY_HISTORY, "[]")
            val existingArray = JSONArray(existingSecureJson)
            val legacyArray = JSONArray(historyJson)

            for (i in 0 until legacyArray.length()) {
                existingArray.put(legacyArray.getJSONObject(i))
            }

            securePrefs.edit().putString(KEY_HISTORY, existingArray.toString()).apply()

            // Clear legacy
            legacyPrefs.edit().remove(KEY_HISTORY).apply()
        }
    }

    fun saveScan(context: Context, card: CardInfo, customName: String? = null) {
        val prefs = getPreferences(context)
        val historyJson = prefs.getString(KEY_HISTORY, "[]")
        val jsonArray = JSONArray(historyJson)

        val timestamp = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedTime = sdf.format(Date(timestamp))

        val newEntry = JSONObject().apply {
            put("pan", card.pan)
            put("expiry", card.expiry)
            put("type", card.type)
            put("aid", card.aid)
            put("standard", card.standard)
            put("timestamp", timestamp)
            put("formattedTime", formattedTime)
            if (!customName.isNullOrBlank()) {
                put("customName", customName)
            }
        }

        // Add to beginning
        val newArray = JSONArray()
        newArray.put(newEntry)
        for (i in 0 until jsonArray.length()) {
            newArray.put(jsonArray.get(i))
        }

        prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply()
    }

    fun updateScanName(context: Context, timestamp: Long, newName: String) {
        val prefs = getPreferences(context)
        val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val jsonArray = JSONArray(historyJson)
        val newArray = JSONArray()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.optLong("timestamp") == timestamp) {
                obj.put("customName", newName)
            }
            newArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply()
    }

    fun getHistory(context: Context): List<SavedScan> {
        val prefs = getPreferences(context)
        val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val jsonArray = JSONArray(historyJson)
        val list = mutableListOf<SavedScan>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                SavedScan(
                    pan = obj.optString("pan"),
                    expiry = obj.optString("expiry"),
                    type = obj.optString("type"),
                    aid = obj.optString("aid"),
                    standard = obj.optString("standard"),
                    timestamp = obj.optLong("timestamp"),
                    formattedTime = obj.optString("formattedTime"),
                    customName = if (obj.has("customName")) obj.getString("customName") else null
                )
            )
        }
        return list
    }

    fun clearHistory(context: Context) {
        val prefs = getPreferences(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
