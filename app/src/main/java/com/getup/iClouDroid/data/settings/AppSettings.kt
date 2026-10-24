package com.getup.iClouDroid.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Schedule for the periodic full-library reconciliation walk.
 *
 * ## Why these units
 *
 * The walk is the only thing that can remove photos deleted on other devices (ISS-031), and it
 * costs a few hundred CloudKit round trips. That makes it the wrong thing to run on every launch,
 * but also the wrong thing to never run — so the cadence is a user choice, not a constant.
 *
 * [OFF] is the default: a fresh install should never start a long network walk on its own without
 * the user having been told what it does.
 */
enum class ScanFrequency(val label: String) {
    OFF("关闭"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    CUSTOM("自定义")
}

/**
 * App-wide user preferences.
 *
 * Deliberately a plain `SharedPreferences` + `StateFlow` object rather than DataStore: the set is
 * small, read once at startup, and WorkManager needs a synchronous read when scheduling.
 *
 * ## Adding a setting
 *
 * Each setting is one property with a private key, a getter that publishes the current value on a
 * `StateFlow`, and a setter that persists and republishes. Nothing else in the app reads the
 * preference directly — see [com.getup.iClouDroid.ui.settings.SettingsScreen], whose rows are
 * declared in a catalog so a new item is one entry plus one composable.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------------------------
    // Periodic full scan
    // ---------------------------------------------------------------------------------------

    private val _scanFrequency = MutableStateFlow(
        ScanFrequency.entries
            .firstOrNull { it.name == prefs.getString(KEY_SCAN_FREQUENCY, null) }
            ?: ScanFrequency.OFF
    )
    val scanFrequency: StateFlow<ScanFrequency> = _scanFrequency.asStateFlow()

    fun setScanFrequency(value: ScanFrequency) {
        prefs.edit { putString(KEY_SCAN_FREQUENCY, value.name) }
        _scanFrequency.value = value
    }

    /**
     * Days between walks for [ScanFrequency.CUSTOM].
     *
     * Clamped to at least 1: a zero interval would create a self-retriggering work request.
     */
    private val _customIntervalDays = MutableStateFlow(
        prefs.getInt(KEY_CUSTOM_INTERVAL_DAYS, DEFAULT_CUSTOM_DAYS).coerceIn(MIN_CUSTOM_DAYS, MAX_CUSTOM_DAYS)
    )
    val customIntervalDays: StateFlow<Int> = _customIntervalDays.asStateFlow()

    fun setCustomIntervalDays(value: Int) {
        val clamped = value.coerceIn(MIN_CUSTOM_DAYS, MAX_CUSTOM_DAYS)
        prefs.edit { putInt(KEY_CUSTOM_INTERVAL_DAYS, clamped) }
        _customIntervalDays.value = clamped
    }

    companion object {
        private const val PREFS_NAME = "icloud_droid_settings"
        private const val KEY_SCAN_FREQUENCY = "scan_frequency"
        private const val KEY_CUSTOM_INTERVAL_DAYS = "custom_interval_days"

        const val MIN_CUSTOM_DAYS = 1
        const val MAX_CUSTOM_DAYS = 365
        const val DEFAULT_CUSTOM_DAYS = 14
    }
}
