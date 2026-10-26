package com.getup.iClouDroid.feature.photos.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.getup.iClouDroid.IClouDroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

/**
 * Background worker for scanning local enabled albums, enqueuing, and streaming uploads to iCloud.
 */
class PhotoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as IClouDroidApp).container
        val repo = container.photoRepository
        val db = container.photoDatabase

        if (repo.isWipeInProgress()) {
            Log.d(TAG, "PhotoBackupWorker skipped - logout teardown in progress")
            return@withContext Result.success()
        }

        try {
            val pendingTasks = db.uploadQueueDao().getPendingTasks(status = "PENDING", limit = 20)
            Log.d(TAG, "PhotoBackupWorker started. Pending tasks in queue: ${pendingTasks.size}")

            for (task in pendingTasks) {
                if (repo.isWipeInProgress()) {
                    Log.d(TAG, "Aborting upload loop - logout teardown started")
                    break
                }

                Log.d(TAG, "Processing upload for: ${task.filename} (id=${task.id})")
                val uploadResult = repo.uploadPhotoFromQueue(task, applicationContext)
                if (uploadResult.isSuccess) {
                    Log.i(TAG, "Successfully uploaded ${task.filename}!")
                } else {
                    Log.w(TAG, "Failed to upload ${task.filename}: ${uploadResult.exceptionOrNull()?.message}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PhotoBackupWorker encountered an error", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "PhotoBackupWorker"
        const val UNIQUE_WORK_NAME = "photo_backup_work"
        const val IMMEDIATE_WORK_NAME = "photo_backup_immediate_work"
        private const val MAX_ATTEMPTS = 3

        fun scheduleBackup(context: Context) {
            val appSettings = (context.applicationContext as? IClouDroidApp)?.container?.appSettings
            val isForceUpload = appSettings?.isForceUploadActive() == true

            val allowCellular = isForceUpload || (appSettings?.allowCellularUpload?.value ?: false)
            val requireCharging = if (isForceUpload) false else (appSettings?.requireCharging?.value ?: false)
            val requireBatteryNotLow = if (isForceUpload) false else (appSettings?.requireBatteryNotLow?.value ?: true)

            val networkType = if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(requireCharging)
                .setRequiresBatteryNotLow(requireBatteryNotLow)
                .build()

            val immediateRequest = OneTimeWorkRequestBuilder<PhotoBackupWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )

            val periodicRequest = PeriodicWorkRequestBuilder<PhotoBackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            Log.i(TAG, "Scheduled immediate & periodic PhotoBackupWorker (isForceUpload=$isForceUpload, cellular=$allowCellular, charging=$requireCharging, batteryNotLow=$requireBatteryNotLow)")
        }

        fun cancelBackup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "Cancelled PhotoBackupWorker")
        }
    }
}
