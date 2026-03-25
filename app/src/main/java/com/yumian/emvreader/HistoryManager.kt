package com.yumian.emvreader

import android.content.Context
import android.util.Log
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
    private const val PREF_NAME = "scan_history_prefs"
    private const val KEY_HISTORY = "history_json"

    fun saveScan(context: Context, card: CardInfo, customName: String? = null) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}

