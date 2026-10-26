package com.getup.iClouDroid.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.getup.iClouDroid.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * View layout modes for the photo gallery.
 */
enum class GalleryViewMode(val label: String) {
    DAY("日"),
    MONTH("月")
}

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
    // Appearance & Theme
    // ---------------------------------------------------------------------------------------

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries
            .firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(value: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, value.name) }
        _themeMode.value = value
    }

    private val _useDynamicColor = MutableStateFlow(
        prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true)
    )
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    fun setUseDynamicColor(value: Boolean) {
        prefs.edit { putBoolean(KEY_USE_DYNAMIC_COLOR, value) }
        _useDynamicColor.value = value
    }

    // ---------------------------------------------------------------------------------------
    // Gallery View Mode (Day / Month)
    // ---------------------------------------------------------------------------------------

    private val _galleryViewMode = MutableStateFlow(
        GalleryViewMode.entries
            .firstOrNull { it.name == prefs.getString(KEY_GALLERY_VIEW_MODE, null) }
            ?: GalleryViewMode.DAY
    )
    val galleryViewMode: StateFlow<GalleryViewMode> = _galleryViewMode.asStateFlow()

    fun setGalleryViewMode(value: GalleryViewMode) {
        prefs.edit { putString(KEY_GALLERY_VIEW_MODE, value.name) }
        _galleryViewMode.value = value
    }

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

    // ---------------------------------------------------------------------------------------
    // Month cover previews (jump panel)
    // ---------------------------------------------------------------------------------------

    /**
     * Whether the gallery's jump card shows a thumbnail for each month.
     *
     * ## This is not a display-only switch
     *
     * It is the single source of truth for both halves of the feature, and all three consumers
     * read it:
     *
     *  - `PhotoRepository.rebuildMonthIndexFor` — skips the `coverAssetId` backfill when off
     *  - `PhotoRepository.prefetchMonthCovers` — returns immediately when off
     *  - `MonthIndexBar` — falls back to the plain text grid when off
     *
     * Hiding the images while still downloading them would be the worst of both outcomes: the user
     * would save neither disk space nor scan time. Anything added later that touches covers must
     * consult this flag too.
     */
    private val _monthCoverEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MONTH_COVER_ENABLED, true)
    )
    val monthCoverEnabled: StateFlow<Boolean> = _monthCoverEnabled.asStateFlow()

    fun setMonthCoverEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_MONTH_COVER_ENABLED, value) }
        _monthCoverEnabled.value = value
    }

    // ---------------------------------------------------------------------------------------
    // Bottom Navigation Bar Items
    // ---------------------------------------------------------------------------------------

    private val _navBarDestinations = MutableStateFlow(
        prefs.getStringSet(KEY_NAV_BAR_DESTINATIONS, null)?.mapNotNull { name ->
            com.getup.iClouDroid.ui.navigation.AppDestination.entries.find { it.name == name }
        }?.toSet() ?: (com.getup.iClouDroid.ui.navigation.AppDestination.entries.toSet() - com.getup.iClouDroid.ui.navigation.AppDestination.FindMy)
    )
    val navBarDestinations: StateFlow<Set<com.getup.iClouDroid.ui.navigation.AppDestination>> = _navBarDestinations.asStateFlow()

    fun setNavBarDestinations(destinations: Set<com.getup.iClouDroid.ui.navigation.AppDestination>) {
        if (destinations.isEmpty()) return // ensure at least one
        prefs.edit { putStringSet(KEY_NAV_BAR_DESTINATIONS, destinations.map { it.name }.toSet()) }
        _navBarDestinations.value = destinations
    }

    companion object {
        private const val PREFS_NAME = "icloud_droid_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
        private const val KEY_GALLERY_VIEW_MODE = "gallery_view_mode"
        private const val KEY_SCAN_FREQUENCY = "scan_frequency"
        private const val KEY_CUSTOM_INTERVAL_DAYS = "custom_interval_days"
        private const val KEY_MONTH_COVER_ENABLED = "month_cover_enabled"
        private const val KEY_NAV_BAR_DESTINATIONS = "nav_bar_destinations"

        const val MIN_CUSTOM_DAYS = 1
        const val MAX_CUSTOM_DAYS = 365
        const val DEFAULT_CUSTOM_DAYS = 14
    }
}
