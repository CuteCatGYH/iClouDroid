package com.getup.iClouDroid.feature.photos.data

import android.util.Log
import com.getup.iClouDroid.feature.photos.data.db.PhotoDao
import com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexDao
import com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexEntity
import com.getup.iClouDroid.feature.photos.data.db.ScanStateDao
import com.getup.iClouDroid.feature.photos.data.db.ScanStateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Snapshot of a full-library scan's progress (P2-016).
 *
 * @param scanned how many assets this scan has written so far.
 * @param estimatedTotal best-effort library size, scraped from the iCloud web page at login.
 *   `0` means unknown — CloudKit's cursor API never reports a total, so that is the normal case
 *   for a user who restored a session instead of re-logging in.
 * @param currentYearMonth the month currently being written, or 0 before the first batch.
 */
data class ScanProgress(
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val estimatedTotal: Int = 0,
    val currentYearMonth: Int = 0,
    val isComplete: Boolean = false
) {
    /**
     * Fraction for the progress bar, or null when there is no denominator.
     *
     * Clamped to 0.99 while running: the web page's count and the `CPLAsset` count do not agree
     * exactly (hidden items, orphan masters, Live Photo composition), so the scan can genuinely
     * reach or pass the estimate. Showing "100%" and then continuing to work is worse than
     * stopping at 99% until it really finishes.
     */
    val fraction: Float?
        get() = when {
            isComplete -> 1f
            estimatedTotal <= 0 -> null
            else -> (scanned.toFloat() / estimatedTotal).coerceIn(0f, 0.99f)
        }
}

/**
 * Walks the whole photo library once and buckets it into month sections (P2-016).
 *
 * ## Why this replaces Paging 3
 *
 * The old grid used `RemoteMediator` + Paging 3, and Paging 3 invalidates its `PagingSource` after
 * **every** successful APPEND. The reload window is `initialLoadSize`, so scrolling past it
 * clamped `firstVisibleItemIndex` and the list visibly jumped to the top (ISS-021). That is
 * inherent to `RemoteMediator`, not a tunable.
 *
 * This engine therefore does the fetching itself and writes plain rows to Room. The UI then reads
 * month sections from the database and has nothing to invalidate: the scroll anchor is a month
 * key, which does not change when rows are appended.
 *
 * ## Scan while browsing
 *
 * The cursor walks newest → oldest, so the first batches fill the most recent months. Each batch is
 * committed and its month index rebuilt immediately, which means the user can browse while the
 * rest of the library streams in.
 */
class MonthScanEngine(
    private val database: PhotoDatabase,
    private val repository: PhotoRepository
) {
    private val photoDao: PhotoDao = database.photoDao()
    private val monthIndexDao: PhotoMonthIndexDao = database.photoMonthIndexDao()
    private val scanStateDao: ScanStateDao = database.scanStateDao()

    /** Serialises concurrent triggers (foreground VM + WorkManager). */
    private val scanMutex = Mutex()

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    /**
     * Runs (or resumes) the scan until the cursor is exhausted.
     *
     * Safe to call repeatedly: a second caller waits for the first to finish and then returns
     * immediately because the scan is already complete. Cancellation leaves the cursor persisted,
     * so a later call resumes where this one stopped.
     *
     * @param batchSize records per `/records/query` call.
     */
    suspend fun scanAll(batchSize: Int = DEFAULT_BATCH_SIZE): Result<Int> = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) {
            // Another trigger owns the scan. Report its current state rather than starting over.
            Log.d(TAG, "scanAll skipped - a scan is already running")
            return@withContext Result.success(0)
        }

        try {
            if (repository.isWipeInProgress()) {
                return@withContext Result.failure(Exception("正在退出登录，已暂停照片同步"))
            }

            // Session guard: the Go core's session pointer is process-global and is nil after a
            // cold start (ISS-012). Every call below needs it.
            val sessionCheck = repository.ensureGoSession()
            if (sessionCheck.isFailure) {
                return@withContext Result.failure(
                    sessionCheck.exceptionOrNull() ?: Exception("会话建立失败")
                )
            }

            var state = loadOrCreateState()

            // v4 migration left every pre-existing row at yearMonth = 0, and an interrupted scan
            // may have left the same. Bucket them BEFORE deciding whether there is anything to do:
            // otherwise an install that already fetched photos shows a populated count but an
            // empty gallery, because no month index row exists for those photos.
            backfillUnbucketedRows()
            rebuildMonthIndex()

            if (state.isComplete && photoDao.countUnbucketed() == 0 && monthIndexDao.getAll().isNotEmpty()) {
                Log.d(TAG, "scanAll - library already fully scanned")
                _progress.value = ScanProgress(
                    isScanning = false,
                    scanned = state.scannedCount,
                    estimatedTotal = state.estimatedTotal,
                    isComplete = true
                )
                return@withContext Result.success(0)
            }

            val written = runScanLoop(state, batchSize)

            // Rows can also arrive outside the scan loop — `syncIncremental` upserts them directly,
            // and a run that stopped early may have written some that were never bucketed. Sweep
            // once more here so nothing is left stranded at `yearMonth = 0`: such a row is counted
            // in the header total yet invisible in the gallery, which reads as "my photos are
            // missing" with no obvious cause.
            backfillUnbucketedRows()
            rebuildMonthIndex()

            // Report whether the walk actually finished, not merely that it ran without throwing.
            // `runScanLoop` returns early on: an in-progress wipe, an unrecoverable session, or
            // exhausted transient retries. Treating any of those as "done" produced a "扫描完成"
            // banner over a half-scanned library and left the newest months flagged incomplete.
            val finalState = loadOrCreateState()
            val finished = finalState.isComplete
            if (!finished) {
                Log.w(
                    TAG,
                    "scanAll stopped early (scanned=${finalState.scannedCount}, cursor kept) - " +
                        "will resume on the next trigger"
                )
            }
            Result.success(if (finished) written else 0)
        } catch (e: CancellationException) {
            // Cancellation is not a failure: the cursor is already persisted, so the next run
            // resumes. Rethrow so structured concurrency behaves.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "scanAll failed", e)
            Result.failure(e)
        } finally {
            _progress.value = _progress.value.copy(isScanning = false)
            scanMutex.unlock()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Core loop
    // ------------------------------------------------------------------------------------------

    private suspend fun runScanLoop(initial: ScanStateEntity, batchSize: Int): Int {
        var state = initial
        var marker = state.lastMarker ?: ""
        var scanned = state.scannedCount
        var attempt = 0

        _progress.value = ScanProgress(
            isScanning = true,
            scanned = scanned,
            estimatedTotal = state.estimatedTotal,
            currentYearMonth = _progress.value.currentYearMonth,
            isComplete = false
        )

        while (true) {
            if (repository.isWipeInProgress()) return scanned

            val fetched = repository.fetchScanPage(marker, batchSize)

            if (fetched.isFailure) {
                val rawJson = (fetched.exceptionOrNull() as? CoreFetchFailure)?.rawJson ?: ""
                val classified = classifyCoreResponse(rawJson)

                when (classified.kind) {
                    CoreErrorKind.AUTH_EXPIRED -> {
                        // Confirmed 401/421: the cursor belonged to the dead session. Drop it and
                        // restart from the newest record (ISS-020). Rows are keyed by id, so the
                        // overlap de-duplicates on upsert.
                        Log.w(TAG, "scan auth expired - dropping cursor and restarting")
                        marker = ""
                        attempt = 0
                        val rebuilt = repository.ensureGoSession(force = true)
                        if (rebuilt.isFailure) {
                            Log.e(TAG, "scan aborted - session rebuild failed")
                            return scanned
                        }
                        state = state.copy(lastMarker = null, scannedCount = scanned)
                        persistState(state)
                        continue
                    }

                    CoreErrorKind.RATE_LIMITED, CoreErrorKind.TIMEOUT, CoreErrorKind.NETWORK -> {
                        // Transient. Back off and retry the SAME cursor. Never rebuild the session
                        // here — a rebuild is a /validate round trip plus re-signed URLs, and
                        // doing it on a throttle storm is what turns a hiccup into a loop.
                        attempt += 1
                        if (attempt > MAX_TRANSIENT_RETRIES) {
                            Log.w(TAG, "scan giving up after $attempt transient failures: ${classified.message}")
                            return scanned
                        }
                        val backoff = backoffMs(attempt)
                        Log.w(
                            TAG,
                            "scan transient failure (${classified.kind}), retrying in ${backoff}ms " +
                                "(attempt $attempt/$MAX_TRANSIENT_RETRIES)"
                        )
                        delay(backoff)
                        continue
                    }

                    else -> {
                        Log.e(TAG, "scan failed (${classified.kind}): ${classified.message}")
                        return scanned
                    }
                }
            }

            val page = fetched.getOrThrow()
            attempt = 0

            if (page.photos.isNotEmpty()) {
                val entities = page.photos.map { it.copy(yearMonth = MonthBucketing.yearMonthOf(it.assetDate)) }
                photoDao.upsertAll(entities)

                val touchedMonths = entities.map { it.yearMonth }.distinct()
                for (ym in touchedMonths) {
                    // A month is complete only when the walk has finished outright. Marking it on
                    // `!page.hasMore` marked just the final batch's month and left every earlier
                    // month flagged "扫描中" forever. `markAllMonthsComplete()` does the real sweep
                    // at the end of the walk.
                    refreshMonthIndex(ym, isComplete = false)
                }

                scanned += entities.size
                state = state.copy(
                    lastMarker = page.nextMarker.ifBlank { null },
                    scannedCount = scanned,
                    isComplete = !page.hasMore,
                    updatedAt = System.currentTimeMillis()
                )
                scanStateDao.set(state)

                _progress.value = ScanProgress(
                    isScanning = true,
                    scanned = scanned,
                    estimatedTotal = state.estimatedTotal,
                    currentYearMonth = entities.lastOrNull()?.let {
                        MonthBucketing.yearMonthOf(it.assetDate)
                    } ?: _progress.value.currentYearMonth,
                    isComplete = !page.hasMore
                )

                Log.d(
                    TAG,
                    "scan page +${entities.size} -> total $scanned, months=${touchedMonths.size}, " +
                        "hasMore=${page.hasMore}"
                )
            } else {
                state = state.copy(
                    lastMarker = page.nextMarker.ifBlank { null },
                    scannedCount = scanned,
                    isComplete = !page.hasMore,
                    updatedAt = System.currentTimeMillis()
                )
                scanStateDao.set(state)
            }

            if (!page.hasMore || page.nextMarker.isBlank()) {
                // Mark every month complete now that the walk is finished.
                markAllMonthsComplete()
                Log.i(TAG, "scan complete - $scanned assets across ${monthIndexDao.getAll().size} months")
                _progress.value = ScanProgress(
                    isScanning = false,
                    scanned = scanned,
                    estimatedTotal = state.estimatedTotal,
                    currentYearMonth = _progress.value.currentYearMonth,
                    isComplete = true
                )
                return scanned
            }

            marker = page.nextMarker
        }
    }

    // ------------------------------------------------------------------------------------------
    // Month index maintenance
    // ------------------------------------------------------------------------------------------

    /**
     * Recomputes one month's index row from the photos currently in the database.
     *
     * Runs after every batch so a month becomes visible the moment its first photo lands — that is
     * what makes "scan while browsing" possible instead of showing an empty gallery for a minute.
     */
    private suspend fun refreshMonthIndex(yearMonth: Int, isComplete: Boolean) {
        if (yearMonth <= 0) return
        val aggregate = photoDao.monthAggregate(yearMonth) ?: return
        if (aggregate.itemCount <= 0) {
            monthIndexDao.deleteMonth(yearMonth)
            return
        }
        monthIndexDao.upsert(
            PhotoMonthIndexEntity(
                yearMonth = yearMonth,
                itemCount = aggregate.itemCount,
                firstAssetDate = aggregate.firstAssetDate ?: 0L,
                lastAssetDate = aggregate.lastAssetDate ?: 0L,
                isComplete = isComplete
            )
        )
    }

    /** Rebuilds every month row — used after the v3→v4 backfill. */
    private suspend fun rebuildMonthIndex() {
        for (ym in photoDao.distinctYearMonths()) {
            refreshMonthIndex(ym, isComplete = false)
        }
    }

    private suspend fun markAllMonthsComplete() {
        val months = monthIndexDao.getAll()
        if (months.isEmpty()) return
        monthIndexDao.upsertAll(months.map { it.copy(isComplete = true) })
    }

    /**
     * Assigns `yearMonth` to rows the migration left at 0.
     *
     * Purely local work — no network — so it runs before the first page request to make an
     * upgraded install immediately browsable.
     */
    private suspend fun backfillUnbucketedRows() {
        val pending = photoDao.getUnbucketed()
        if (pending.isEmpty()) return
        Log.i(TAG, "Backfilling yearMonth for ${pending.size} pre-v4 rows")
        for (row in pending) {
            photoDao.setYearMonth(row.id, MonthBucketing.yearMonthOf(row.assetDate))
        }
    }

    /**
     * Buckets legacy rows and rebuilds the month index without touching the network.
     *
     * Called on startup so a v3→v4 upgrade shows its existing photos immediately, instead of an
     * empty gallery behind a non-zero counter while a full re-scan happens.
     */
    suspend fun ensureMonthIndexUpToDate() = withContext(Dispatchers.IO) {
        runCatching {
            backfillUnbucketedRows()
            rebuildMonthIndex()

            // If the walk is already finished, the index must say so. Without this a library
            // scanned by an older build (or one whose final sweep was interrupted) shows every
            // month as "扫描中" forever, even though nothing is running.
            val state = scanStateDao.get(ScanStateEntity.KEY_MONTH_SCAN)
            if (state?.isComplete == true) {
                markAllMonthsComplete()
            }
        }.onFailure {
            Log.w(TAG, "Month index backfill failed: ${it.message}")
        }
    }

    // ------------------------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------------------------

    private suspend fun loadOrCreateState(): ScanStateEntity {
        val existing = scanStateDao.get(ScanStateEntity.KEY_MONTH_SCAN)
        if (existing != null) return existing
        val fresh = ScanStateEntity(
            key = ScanStateEntity.KEY_MONTH_SCAN,
            lastMarker = null,
            isComplete = false,
            scannedCount = 0,
            estimatedTotal = 0,
            updatedAt = System.currentTimeMillis()
        )
        scanStateDao.set(fresh)
        return fresh
    }

    private suspend fun persistState(state: ScanStateEntity) {
        scanStateDao.set(state)
    }

    /**
     * Records the library size scraped from the iCloud web page.
     *
     * Only used as the progress denominator; nothing depends on it being exact, and 0 (unknown)
     * simply means the bar switches to indeterminate mode.
     */
    suspend fun setEstimatedTotal(total: Int) {
        if (total <= 0) return
        val state = loadOrCreateState()
        if (state.estimatedTotal == total) return
        scanStateDao.set(state.copy(estimatedTotal = total, updatedAt = System.currentTimeMillis()))
        _progress.value = _progress.value.copy(estimatedTotal = total)
        Log.i(TAG, "Estimated library size set to $total")
    }

    /** Loads the persisted estimate so the progress bar has a denominator after a cold start. */
    suspend fun restoreProgress() = withContext(Dispatchers.IO) {
        val state = scanStateDao.get(ScanStateEntity.KEY_MONTH_SCAN) ?: return@withContext
        _progress.value = ScanProgress(
            isScanning = false,
            scanned = state.scannedCount,
            estimatedTotal = state.estimatedTotal,
            isComplete = state.isComplete
        )
    }

    companion object {
        private const val TAG = "MonthScanEngine"

        /** Records per page. 200 is the CloudKit maximum this endpoint accepts. */
        const val DEFAULT_BATCH_SIZE = 200

        /** Attempts for transient failures (429 / timeout / network) before giving up for now. */
        private const val MAX_TRANSIENT_RETRIES = 5

        /** 1s, 2s, 4s, 8s, 16s — enough to ride out a throttle without hanging the scan. */
        private fun backoffMs(attempt: Int): Long =
            (1000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(16_000L)

        /** Marker length is irrelevant; this just documents what a "no marker" page looks like. */
        internal fun isTerminalMarker(marker: String): Boolean = marker.isBlank()
    }
}
