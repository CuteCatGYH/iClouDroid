package com.getup.iClouDroid.di

import android.content.Context
import com.getup.iClouDroid.data.session.SessionManager
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

    private val photoDatabase: PhotoDatabase by lazy {
        PhotoDatabase.getInstance(appContext)
    }

    val photoRepository: PhotoRepository by lazy {
        PhotoRepository(photoDatabase, sessionManager)
    }

    // Future features plug in here:
    // val driveRepository: DriveRepository by lazy { DriveRepository(...) }
    // val notesRepository: NotesRepository by lazy { NotesRepository(...) }
}
