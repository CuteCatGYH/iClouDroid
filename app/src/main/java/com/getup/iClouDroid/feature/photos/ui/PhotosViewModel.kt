package com.getup.iClouDroid.feature.photos.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.getup.iClouDroid.IClouDroidApp
import com.getup.iClouDroid.feature.photos.data.MediaStoreDownloader
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Downloading : DownloadState
    data class Success(val uri: Uri, val filename: String) : DownloadState
    data class Error(val message: String) : DownloadState
}

class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as IClouDroidApp).container.photoRepository

    val photosPagingData: Flow<PagingData<PhotoAssetEntity>> = repository.photoPagingFlow
        .cachedIn(viewModelScope)

    val totalCount: StateFlow<Int> = repository.totalCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    private var hasAutoSynced = false

    init {
        viewModelScope.launch {
            repository.totalCountFlow.collect { currentCount ->
                if (currentCount == 0 && !hasAutoSynced && !_isSyncing.value) {
                    hasAutoSynced = true
                    syncPhotos(isInitial = true)
                }
            }
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

    fun syncPhotos(isInitial: Boolean = false) {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = if (isInitial) "正在连接 iCloud 照片库..." else "正在检查增量更新..."

        viewModelScope.launch {
            try {
                // 1. Verify PrimarySync zone is ready
                val zoneReady = repository.checkZoneReady()
                if (zoneReady.isFailure) {
                    val err = zoneReady.exceptionOrNull()?.message ?: "PrimarySync 分区未就绪"
                    _syncMessage.value = "错误: $err"
                    Log.w(TAG, "checkZoneReady failed: $err")
                    return@launch
                }

                // 2. Fetch changes or initial page
                val syncResult = if (isInitial) {
                    _syncMessage.value = "正在拉取照片列表..."
                    repository.fetchInitialPage(limit = 100)
                } else {
                    _syncMessage.value = "正在同步最新照片变更..."
                    repository.syncIncremental(limit = 100)
                }

                if (syncResult.isSuccess) {
                    val count = syncResult.getOrDefault(0)
                    _syncMessage.value = "同步完成，新增/更新 $count 项"
                    Log.i(TAG, "Sync finished successfully, count=$count")
                } else {
                    val err = syncResult.exceptionOrNull()?.message ?: "同步出现异常"
                    _syncMessage.value = "同步失败: $err"
                    Log.w(TAG, "syncPhotos failed: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncPhotos exception", e)
                _syncMessage.value = "异常: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
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
    }
}
