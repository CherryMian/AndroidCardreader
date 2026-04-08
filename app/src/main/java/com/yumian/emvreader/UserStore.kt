package com.yumian.emvreader

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

object UserStore {
    private const val PREF_NAME = "user_store"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"
    private const val logTag = "UserStore"

    // Basic hash to avoid plain-text storage; not meant for production security
    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(digest.digest(input.toByteArray()), Base64.NO_WRAP)
    }

    fun register(context: Context, username: String, password: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_USER, null)
        if (existing != null && existing.equals(username, ignoreCase = true)) {
            Log.d(logTag, "register failed: user exists user=$username")
            return false
        }
        prefs.edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, hash(password))
            .apply()
        Log.d(logTag, "register success user=$username")
        return true
    }

    fun login(context: Context, username: String, password: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val storedUser = prefs.getString(KEY_USER, null)
        val storedPass = prefs.getString(KEY_PASS, null)
        val ok = storedUser != null && storedUser.equals(username, ignoreCase = true) && storedPass == hash(password)
        Log.d(logTag, "login check user=$username success=$ok")
        return ok
    }
}

