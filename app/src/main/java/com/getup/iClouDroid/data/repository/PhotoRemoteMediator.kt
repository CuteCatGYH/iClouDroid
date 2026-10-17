package com.getup.iClouDroid.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.getup.iClouDroid.data.db.AppDatabase
import com.getup.iClouDroid.data.db.PhotoAssetEntity

@OptIn(ExperimentalPagingApi::class)
class PhotoRemoteMediator(
    private val repository: PhotoRepository,
    private val database: AppDatabase
) : RemoteMediator<Int, PhotoAssetEntity>() {

    private val syncStateDao = database.syncStateDao()

    override suspend fun initialize(): InitializeAction {
        val state = syncStateDao.getSyncState(PhotoRepository.KEY_PRIMARY_SYNC)
        return if (state == null) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PhotoAssetEntity>
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.REFRESH -> {
                    val result = repository.fetchInitialPage(limit = 60)
                    if (result.isSuccess) {
                        val syncState = syncStateDao.getSyncState(PhotoRepository.KEY_PRIMARY_SYNC)
                        MediatorResult.Success(endOfPaginationReached = !(syncState?.hasMore ?: false))
                    } else {
                        MediatorResult.Error(result.exceptionOrNull() ?: Exception("Failed to load initial photos"))
                    }
                }
                LoadType.PREPEND -> {
                    MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val syncState = syncStateDao.getSyncState(PhotoRepository.KEY_PRIMARY_SYNC)
                    if (syncState == null || !syncState.hasMore) {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }

                    val result = repository.fetchNextPage(limit = 60)
                    if (result.isSuccess) {
                        val count = result.getOrDefault(0)
                        val updatedState = syncStateDao.getSyncState(PhotoRepository.KEY_PRIMARY_SYNC)
                        MediatorResult.Success(
                            endOfPaginationReached = count == 0 || !(updatedState?.hasMore ?: false)
                        )
                    } else {
                        MediatorResult.Error(result.exceptionOrNull() ?: Exception("Failed to load next photo page"))
                    }
                }
            }
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
