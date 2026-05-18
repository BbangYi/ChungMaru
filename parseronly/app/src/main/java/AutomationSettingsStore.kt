package com.example.youtubeparser

import android.content.Context

data class AutomationStatus(
    val message: String,
    val updatedAt: Long
)

object AutomationSettingsStore {

    const val DEFAULT_ROTATION_INTERVAL_MINUTES = 1
    const val MIN_ROTATION_INTERVAL_MINUTES = 1
    const val MAX_ROTATION_INTERVAL_MINUTES = 180
    const val PLATFORM_ALL = "ALL"
    const val PLATFORM_YOUTUBE = "YOUTUBE"
    const val PLATFORM_TIKTOK = "TIKTOK"
    const val PLATFORM_INSTAGRAM = "INSTAGRAM"

    private const val PREFS_NAME = "youtube_parser_automation"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ROTATION_INTERVAL_MINUTES = "rotation_interval_minutes"
    private const val KEY_PLATFORM_INDEX = "platform_index"
    private const val KEY_PLATFORM_MODE = "platform_mode"
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

    fun getPlatformMode(context: Context): String {
        val mode = prefs(context).getString(KEY_PLATFORM_MODE, PLATFORM_ALL) ?: PLATFORM_ALL
        return when (mode) {
            PLATFORM_YOUTUBE,
            PLATFORM_TIKTOK,
            PLATFORM_INSTAGRAM -> mode
            else -> PLATFORM_ALL
        }
    }

    fun savePlatformMode(context: Context, mode: String) {
        val normalizedMode = when (mode) {
            PLATFORM_YOUTUBE,
            PLATFORM_TIKTOK,
            PLATFORM_INSTAGRAM -> mode
            else -> PLATFORM_ALL
        }

        prefs(context)
            .edit()
            .putString(KEY_PLATFORM_MODE, normalizedMode)
            .apply()
    }

    fun isPlatformAllowed(context: Context, platformName: String): Boolean {
        val mode = getPlatformMode(context)
        return mode == PLATFORM_ALL || mode == platformName
    }

    fun getPlatformModeLabel(context: Context): String {
        return when (getPlatformMode(context)) {
            PLATFORM_YOUTUBE -> "YouTube only"
            PLATFORM_TIKTOK -> "TikTok only"
            PLATFORM_INSTAGRAM -> "Instagram only"
            else -> "All platforms"
        }
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
