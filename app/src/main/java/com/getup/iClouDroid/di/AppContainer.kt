package com.getup.iClouDroid.di

import android.content.Context
import com.getup.iClouDroid.data.session.SessionManager
import com.getup.iClouDroid.data.settings.AppSettings
import com.getup.iClouDroid.feature.photos.data.MonthScanEngine
import com.getup.iClouDroid.feature.photos.data.PhotoDatabase
import com.getup.iClouDroid.feature.photos.data.PhotoRepository

/**
 * Single owner of app-scoped dependencies.
 *
 * This is deliberately hand-written rather than Hilt: the graph is tiny, and an explicit
 * container keeps the wiring readable while the app grows from one feature (photos) to many
 * (photos / drive / notes). Adding a feature means adding a field here — not cloning another
 * `getInstance()` singleton template.
 *
 * [SessionManager] lives here on purpose: cookies, dsid and the session generation counter are
 * shared by every iCloud feature, not just photos.
 */
class AppContainer(private val appContext: Context) {

    val sessionManager: SessionManager by lazy {
        SessionManager(appContext)
    }

    /**
     * User preferences.
     *
     * Lives here (not in a feature package) because settings are app-wide: the periodic scan
     * scheduler, the settings screen, and any future feature all read the same instance.
     */
    val appSettings: AppSettings by lazy {
        AppSettings(appContext)
    }

    /** App context, for the schedulers that need it without holding an Activity. */
    val context: Context get() = appContext

    val photoDatabase: PhotoDatabase by lazy {
        PhotoDatabase.getInstance(appContext)
    }

    val photoRepository: PhotoRepository by lazy {
        PhotoRepository(photoDatabase, sessionManager, appSettings, appContext)
    }

    /**
     * Walks the whole library into month sections (P2-016).
     *
     * App-scoped rather than ViewModel-scoped on purpose: the scan must keep running when the
     * screen goes away, and it is driven from both the UI (`viewModelScope`) and
     * `MonthScanWorker`. Both paths contend on the same instance, which serialises them.
     */
    val monthScanEngine: MonthScanEngine by lazy {
        MonthScanEngine(photoDatabase, photoRepository, appSettings)
    }

    val localAlbumRepository: com.getup.iClouDroid.feature.local.data.LocalAlbumRepository by lazy {
        com.getup.iClouDroid.feature.local.data.LocalAlbumRepository(appContext)
    }

    // Future features plug in here:
    // val driveRepository: DriveRepository by lazy { DriveRepository(...) }
    // val notesRepository: NotesRepository by lazy { NotesRepository(...) }
}
