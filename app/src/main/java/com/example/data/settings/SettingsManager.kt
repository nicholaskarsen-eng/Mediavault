package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("media_vault_settings", Context.MODE_PRIVATE)

    fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
    fun setString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    fun setBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    fun setInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    fun setLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    companion object {
        const val KEY_CUSTOM_RULE = "custom_rule"
        const val KEY_API_KEY = "api_key"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_THEME = "app_theme"
        const val KEY_AUTO_ORGANIZE = "auto_organize"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_CLOUD_SYNC = "cloud_sync_enabled"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_MAX_SIZE = "max_vault_size"
        const val KEY_AUTO_DELETE = "auto_delete_days"
        const val KEY_UNIVERSAL_REPO = "universal_repo_enabled"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_REDUNDANCY = "redundancy_level"
    }
}
