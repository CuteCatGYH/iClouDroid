package com.getup.iClouDroid

import android.app.Application
import android.util.Log
import com.getup.iClouDroid.di.AppContainer
import com.getup.iClouDroid.feature.photos.data.image.ICloudImageLoaderFactory
import com.getup.icloudcore.Icloudcore
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

        // TEMPORARY DIAGNOSTIC — DELETE BEFORE RELEASE.
        //
        // Feasibility probe for the photo-detail panel (EXIF/GPS) and Phase 3 dedup.
        // Runs inside the app process so it can use the stored session, without exposing the
        // cookie anywhere. Trigger: `adb shell am start` then read `adb logcat -s RecordFieldsProbe`.
        if (BuildConfig.DEBUG) {
            appScope.launch {
                try {
                    val cookie = container.sessionManager.getCookieString()
                    if (cookie.isNullOrBlank()) {
                        Log.i(PROBE_TAG, "No stored cookie - log in first")
                        return@launch
                    }
                    val isChina = container.sessionManager.isChinaMainland()
                    Log.i(PROBE_TAG, "initSession(isChina=$isChina)")
                    Log.i(PROBE_TAG, "init -> ${Icloudcore.initSession(cookie, isChina)}")
                    // Let the app's own session bootstrap settle first: the probe and
                    // ensureGoSession() otherwise race and clobber each other's session.
                    kotlinx.coroutines.delay(6_000)
                    Log.i(PROBE_TAG, "GPS >>> ${Icloudcore.probeGPS(50)}")
                } catch (e: Exception) {
                    Log.e(PROBE_TAG, "probe failed", e)
                }
            }
        }
    }

    private companion object {
        private const val PROBE_TAG = "RecordFieldsProbe"
        private const val TAG = "IClouDroidApp"
    }
}
