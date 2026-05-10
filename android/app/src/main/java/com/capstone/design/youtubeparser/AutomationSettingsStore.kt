package com.capstone.design.youtubeparser

import android.content.Context

data class AutomationStatus(
    val message: String,
    val updatedAt: Long
)

object AutomationSettingsStore {

    const val DEFAULT_ROTATION_INTERVAL_MINUTES = 1
    const val MIN_ROTATION_INTERVAL_MINUTES = 1
    const val MAX_ROTATION_INTERVAL_MINUTES = 180

    private const val PREFS_NAME = "youtube_parser_automation"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ROTATION_INTERVAL_MINUTES = "rotation_interval_minutes"
    private const val KEY_PLATFORM_INDEX = "platform_index"
    private const val KEY_STATUS_MESSAGE = "status_message"
    private const val KEY_STATUS_UPDATED_AT = "status_updated_at"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getRotationIntervalMinutes(context: Context): Int {
        return prefs(context)
            .getInt(KEY_ROTATION_INTERVAL_MINUTES, DEFAULT_ROTATION_INTERVAL_MINUTES)
            .coerceIn(MIN_ROTATION_INTERVAL_MINUTES, MAX_ROTATION_INTERVAL_MINUTES)
    }

    fun saveRotationIntervalMinutes(context: Context, rawValue: String): Int {
        val minutes = rawValue
            .trim()
            .toIntOrNull()
            ?.coerceIn(MIN_ROTATION_INTERVAL_MINUTES, MAX_ROTATION_INTERVAL_MINUTES)
            ?: DEFAULT_ROTATION_INTERVAL_MINUTES

        prefs(context)
            .edit()
            .putInt(KEY_ROTATION_INTERVAL_MINUTES, minutes)
            .apply()

        return minutes
    }

    fun getRotationIntervalMs(context: Context): Long {
        return getRotationIntervalMinutes(context) * 60_000L
    }

    fun getPlatformIndex(context: Context): Int {
        return prefs(context).getInt(KEY_PLATFORM_INDEX, 0).coerceAtLeast(0)
    }

    fun savePlatformIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_PLATFORM_INDEX, index.coerceAtLeast(0)).apply()
    }

    fun saveStatus(context: Context, message: String) {
        prefs(context)
            .edit()
            .putString(KEY_STATUS_MESSAGE, message)
            .putLong(KEY_STATUS_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getStatus(context: Context): AutomationStatus? {
        val preferences = prefs(context)
        val message = preferences.getString(KEY_STATUS_MESSAGE, null) ?: return null
        val updatedAt = preferences.getLong(KEY_STATUS_UPDATED_AT, 0L)
        return AutomationStatus(message, updatedAt)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
