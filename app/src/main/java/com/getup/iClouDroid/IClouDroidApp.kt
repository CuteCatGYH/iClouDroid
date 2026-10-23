package com.getup.iClouDroid

import android.app.Application
import android.util.Log
import com.getup.iClouDroid.di.AppContainer
import com.getup.iClouDroid.feature.photos.data.image.ICloudImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application entry point; owns the single [AppContainer].
 *
 * ViewModels retrieve their dependencies via `(application as IClouDroidApp).container`, which
 * keeps them free of static singletons and makes each feature's dependencies explicit.
 */
class IClouDroidApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Outlives any screen; used only for fire-and-forget warm-up work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Warm the Coil disk cache off the main thread.
        //
        // On a cold start the first screen asks for ~24 thumbnails at once. Coil's
        // DiskLruCache.initialize() holds a monitor while it scans the journal, so all those
        // lookups queue behind it and the UI thread waits: we measured 160ms of monitor
        // contention and a ~47 frame drop. Paying that cost here, before the grid is composed,
        // moves it off the critical path.
        appScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    ICloudImageLoaderFactory.warmUp(this@IClouDroidApp, container)
                }
            } catch (e: Exception) {
                // Warm-up is best effort; a failure here must never break startup.
                Log.w(TAG, "Coil warm-up failed: ${e.message}")
            }
        }
    }

    private companion object {
        private const val TAG = "IClouDroidApp"
    }
}
