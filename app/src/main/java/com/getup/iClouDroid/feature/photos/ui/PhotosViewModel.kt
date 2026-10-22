package com.getup.iClouDroid.feature.photos.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.getup.iClouDroid.IClouDroidApp
import com.getup.iClouDroid.feature.photos.data.MediaStoreDownloader
import com.getup.iClouDroid.feature.photos.data.MonthScanEngine
import com.getup.iClouDroid.feature.photos.data.ScanProgress
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexEntity
import com.getup.iClouDroid.feature.photos.work.ScanScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Downloading : DownloadState
    data class Success(val uri: Uri, val filename: String) : DownloadState
    data class Error(val message: String) : DownloadState
}

/**
 * State of the gallery as a whole.
 *
 * [months] is the *index* — a few hundred rows at most — not the photos. Each month's contents are
 * subscribed separately by the UI so a 16k-item library never materialises at once.
 */
data class GalleryUiState(
    val months: List<PhotoMonthIndexEntity> = emptyList(),
    val totalCount: Int = 0,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as IClouDroidApp).container
    private val repository = container.photoRepository
    private val scanEngine = container.monthScanEngine

    /**
     * Month sections, newest first.
     *
     * P2-016: this is the scroll anchor. Previously the grid was driven by `PagingData`, whose
     * `PagingSource` was invalidated after every APPEND — collapsing `itemCount` and clamping the
     * scroll position. A month key does not change when rows are appended, so there is nothing to
     * clamp.
     */
    val monthIndex: StateFlow<List<PhotoMonthIndexEntity>> = repository.monthIndexFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = repository.totalCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Live scan progress: drives the bar and the "scanned so far" text. */
    val scanProgress: StateFlow<ScanProgress> = scanEngine.progress

    val galleryState: StateFlow<GalleryUiState> = monthIndex
        .map { GalleryUiState(months = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    private val _gridColumns = MutableStateFlow(4)
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _selectedPhoto = MutableStateFlow<PhotoAssetEntity?>(null)
    val selectedPhoto: StateFlow<PhotoAssetEntity?> = _selectedPhoto.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var hasAutoStarted = false
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            // Restore the persisted estimate first so the progress bar has a denominator even
            // before this run produces any progress of its own.
            scanEngine.restoreProgress()

            // Bucket any rows left over from a v3→v4 upgrade (or an interrupted scan) before the
            // UI reads the index. Without this an upgraded install shows a populated count but an
            // empty gallery, because `photo_assets` has rows while `photo_month_index` has none.
            scanEngine.ensureMonthIndexUpToDate()

            // Start the scan whenever it is not already finished — NOT only when the library is
            // empty.
            //
            // The original condition was `currentCount == 0`, which is wrong for the v3→v4
            // upgrade: an existing install has rows but no `yearMonth` and no month index, so the
            // gallery renders empty while the count reads 50. It also silently skipped resuming an
            // interrupted scan (a killed app leaves a partial library behind).
            repository.totalCountFlow.collect { _ ->
                if (!hasAutoStarted && !_isSyncing.value) {
                    hasAutoStarted = true
                    startInitialLoad()
                }
            }
        }
    }

    /**
     * Hands the scan off to WorkManager when the screen goes away.
     *
     * The foreground scan runs in `viewModelScope`, which is cancelled with the screen. Leaving it
     * at that means a user who switches apps mid-scan comes back to a partially populated gallery
     * and no indication anything is still pending. `ScanScheduler` uses `KEEP`, so this never
     * duplicates an already-queued job, and the engine's mutex stops it from racing the foreground
     * path.
     */
    fun onScreenLeaving() {
        if (scanEngine.progress.value.isComplete) {
            ScanScheduler.cancel(getApplication<Application>())
            return
        }
        ScanScheduler.ensureScheduled(getApplication<Application>())
    }

    /** Photos of one month, newest first. Consumers subscribe per month as it scrolls into view. */
    fun observeMonth(yearMonth: Int): Flow<List<PhotoAssetEntity>> =
        repository.observeMonth(yearMonth)

    /**
     * Photos of every month currently on screen, keyed by `yyyyMM`.
     *
     * The gallery emits one row per `columns` photos so that every cell is a direct child of the
     * single `LazyColumn` — nesting a `LazyVerticalGrid` per month meant the inner grid owned its
     * own measurement, and after a fast fling it came back with a single row and dropped the rest.
     *
     * Rows look their data up here rather than each starting its own query: a month with forty rows
     * would otherwise open forty cursors for identical data. Subscriptions are added as months
     * become visible and dropped once the list no longer needs them.
     */
    private val _monthPhotos = MutableStateFlow<Map<Int, List<PhotoAssetEntity>>>(emptyMap())
    val monthPhotos: StateFlow<Map<Int, List<PhotoAssetEntity>>> = _monthPhotos.asStateFlow()

    private val monthJobs = mutableMapOf<Int, Job>()

    /**
     * Subscribes to every month in the library.
     *
     * Deliberately **not** driven by viewport visibility. An earlier version subscribed only to the
     * months near the viewport and evicted the rest, which broke under fast flinging: the visible
     * window is rewritten several times per second during a fling, so months were torn down a frame
     * after being created and came back on screen before their query had emitted — rows found no
     * data and painted empty. Deferring the eviction only narrowed the window in which that could
     * happen.
     *
     * Subscribing to everything is affordable here precisely because of the row-based layout: every
     * month is a handful of `PhotoAssetEntity` objects (no bitmaps — Coil holds those), and a
     * 16k-item library across 108 months is a few thousand small objects. Correctness under
     * flinging is worth far more than trimming that.
     */
    fun subscribeToAllMonths(yearMonths: Collection<Int>) {
        for (ym in yearMonths) {
            if (monthJobs[ym] != null) continue
            monthJobs[ym] = viewModelScope.launch {
                repository.observeMonth(ym).collect { photos ->
                    _monthPhotos.update { it + (ym to photos) }
                }
            }
        }

        // Drop subscriptions for months that no longer exist at all (after a re-scan or a wipe).
        val known = yearMonths.toSet()
        val gone = monthJobs.keys - known
        for (ym in gone) {
            monthJobs.remove(ym)?.cancel()
            _monthPhotos.update { it - ym }
        }
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns.coerceIn(2, 6)
    }

    fun toggleGridColumns() {
        _gridColumns.value = when (_gridColumns.value) {
            3 -> 4
            4 -> 5
            5 -> 3
            else -> 4
        }
    }

    fun selectPhoto(photo: PhotoAssetEntity?) {
        _selectedPhoto.value = photo
        _downloadState.value = DownloadState.Idle
    }

    /**
     * First load: verify the zone, then walk the library into month sections.
     *
     * The walk is resumable and idempotent, so calling this after an interruption picks up where
     * it stopped rather than starting over.
     */
    private fun startInitialLoad() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = "正在连接 iCloud 照片库..."

        scanJob = viewModelScope.launch {
            try {
                val zoneReady = repository.checkZoneReady()
                if (zoneReady.isFailure) {
                    val err = zoneReady.exceptionOrNull()?.message ?: "PrimarySync 分区未就绪"
                    _syncMessage.value = "错误: $err"
                    Log.w(TAG, "checkZoneReady failed: $err")
                    return@launch
                }

                _syncMessage.value = "正在扫描照片库..."

                // Observe progress while the scan runs, so the banner tracks it.
                val progressJob = launch {
                    scanEngine.progress.collect { p ->
                        if (p.isScanning) {
                            _syncMessage.value = buildProgressMessage(p)
                        }
                    }
                }

                val result = scanEngine.scanAll()
                progressJob.cancel()

                if (result.isSuccess) {
                    val progress = scanEngine.progress.value
                    // Tell the truth about what just happened. `scanAll` returns success even when
                    // it stopped early (wipe in progress, unrecoverable session, exhausted
                    // retries) with the cursor preserved for the next attempt — claiming
                    // "扫描完成" there was actively misleading over a half-scanned library.
                    _syncMessage.value = if (progress.isComplete) {
                        "扫描完成，共 ${progress.scanned} 项"
                    } else {
                        "已扫描 ${progress.scanned} 项，稍后自动继续"
                    }
                    Log.i(TAG, "Scan finished, complete=${progress.isComplete}, scanned=${progress.scanned}")
                } else {
                    val err = result.exceptionOrNull()?.message ?: "扫描出现异常"
                    _syncMessage.value = "扫描失败: $err"
                    Log.w(TAG, "scanAll failed: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "initial load exception", e)
                _syncMessage.value = "异常: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Manual sync: pull incremental changes on top of the already-scanned library.
     *
     * Uses the `syncToken` from `/records/changes` rather than re-walking the cursor, so a refresh
     * is cheap. Falls back to the full scan when no token exists yet.
     */
    fun syncPhotos(isInitial: Boolean = false) {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = if (isInitial) "正在连接 iCloud 照片库..." else "正在检查增量更新..."

        viewModelScope.launch {
            try {
                val zoneReady = repository.checkZoneReady()
                if (zoneReady.isFailure) {
                    val err = zoneReady.exceptionOrNull()?.message ?: "PrimarySync 分区未就绪"
                    _syncMessage.value = "错误: $err"
                    return@launch
                }

                val syncResult = if (isInitial) {
                    _syncMessage.value = "正在扫描照片库..."
                    scanEngine.scanAll()
                } else {
                    _syncMessage.value = "正在同步最新照片变更..."
                    repository.syncIncremental(limit = 100)
                }

                if (syncResult.isSuccess) {
                    val count = syncResult.getOrDefault(0)
                    _syncMessage.value = "同步完成，新增/更新 $count 项"
                    Log.i(TAG, "Sync finished, count=$count")
                } else {
                    val err = syncResult.exceptionOrNull()?.message ?: "同步出现异常"
                    _syncMessage.value = "同步失败: $err"
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncPhotos exception", e)
                _syncMessage.value = "异常: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Banner text for a running scan.
     *
     * Shows both the concrete position ("3,847 项") and the month currently being written, because
     * a bare percentage is meaningless while `estimatedTotal` is unknown — and it frequently is,
     * since CloudKit's cursor never reports a total.
     */
    private fun buildProgressMessage(progress: ScanProgress): String {
        val monthLabel = com.getup.iClouDroid.feature.photos.data.MonthBucketing
            .labelOf(progress.currentYearMonth)
        val where = if (progress.currentYearMonth > 0) " · $monthLabel" else ""
        return if (progress.estimatedTotal > 0) {
            "正在扫描 ${progress.scanned} / ${progress.estimatedTotal} 项$where"
        } else {
            "正在扫描 · 已扫描 ${progress.scanned} 项$where"
        }
    }

    fun downloadPhoto(context: Context, photo: PhotoAssetEntity) {
        if (_downloadState.value is DownloadState.Downloading) return
        _downloadState.value = DownloadState.Downloading

        viewModelScope.launch {
            val result = MediaStoreDownloader.downloadAsset(context, photo, repository)
            if (result.isSuccess) {
                val uri = result.getOrThrow()
                _downloadState.value = DownloadState.Success(uri, photo.filename)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已下载到相册: ${photo.filename}", Toast.LENGTH_SHORT).show()
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "下载失败"
                _downloadState.value = DownloadState.Error(err)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载失败: $err", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val TAG = "PhotosViewModel"

        /**
         * How long a month stays subscribed after leaving the viewport.
         *
         * Long enough to survive the visible-window churn of a fast fling (which can rewrite the
         * window several times per second) but short enough that memory still tracks the viewport
         * rather than the whole library.
         */
        private const val MONTH_EVICTION_GRACE_MS = 4_000L
    }
}
