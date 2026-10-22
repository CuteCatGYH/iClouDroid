package com.getup.iClouDroid.feature.photos.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.getup.iClouDroid.IClouDroidApp
import com.getup.iClouDroid.feature.photos.data.MonthScanEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest

/**
 * Keeps the month scan running while the app is in the background (P2-016).
 *
 * ## Relationship with the foreground path
 *
 * The scan itself lives in [MonthScanEngine], which is app-scoped and holds its own mutex. This
 * worker does not own any state: it simply asks the engine to continue. If the UI is already
 * scanning, `scanAll()` returns immediately and the worker finishes — there is never a second
 * concurrent walk, and the persisted cursor means whichever path runs last still makes progress.
 *
 * ## Foreground service
 *
 * The scan is a long, network-bound task, so it runs as a foreground service to avoid being killed
 * mid-way. On Android 14+ that requires the `dataSync` type, which also caps the run at six hours —
 * far beyond what this needs, but the failure path is handled anyway (`Result.retry()` lets the
 * system reschedule).
 */
class MonthScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IClouDroidApp).container
        val engine = container.monthScanEngine

        // If the account is being wiped (logout), there is nothing to scan and writing rows would
        // resurrect rows the teardown is deleting (P2-018).
        if (container.photoRepository.isWipeInProgress()) {
            Log.d(TAG, "Scan worker skipped - logout teardown in progress")
            return Result.success()
        }

        return try {
            setForeground(createForegroundInfo(engine))
            observeProgress(engine)

            val result = engine.scanAll()
            if (result.isSuccess) {
                Log.i(TAG, "Background scan finished, scanned=${engine.progress.value.scanned}")
                Result.success()
            } else if (runAttemptCount < MAX_ATTEMPTS) {
                Log.w(TAG, "Background scan failed, will retry (attempt $runAttemptCount)")
                Result.retry()
            } else {
                Log.e(TAG, "Background scan failed permanently")
                Result.failure()
            }
        } catch (e: CancellationException) {
            // Cancelled (user cancelled, or the system stopped the foreground service). The
            // cursor is persisted, so the next run resumes.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Background scan threw", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /** Keeps the notification text in step with the engine's progress. */
    private suspend fun observeProgress(engine: MonthScanEngine) {
        try {
            engine.progress.collectLatest { progress ->
                if (!progress.isScanning) return@collectLatest
                setForeground(
                    ForegroundInfo(
                        ScanNotificationHelper.NOTIFICATION_ID,
                        ScanNotificationHelper.buildOngoing(applicationContext, progress),
                        foregroundServiceType()
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Notification updates are cosmetic; never let one fail the scan.
            Log.w(TAG, "Progress notification update failed: ${e.message}")
        }
    }

    private fun createForegroundInfo(engine: MonthScanEngine): ForegroundInfo {
        val progress = engine.progress.value
        return ForegroundInfo(
            ScanNotificationHelper.NOTIFICATION_ID,
            ScanNotificationHelper.buildOngoing(applicationContext, progress),
            foregroundServiceType()
        )
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    companion object {
        private const val TAG = "MonthScanWorker"
        const val UNIQUE_WORK_NAME = "month_scan"
        private const val MAX_ATTEMPTS = 3
    }
}
