package com.getup.iClouDroid.feature.photos.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    // P2-012: `assetDate` alone is NOT a stable ordering key — many photos share the same
    // second, so ties resolve non-deterministically. Every APPEND calls upsertAll(), Room
    // invalidates the PagingSource, and with an unstable sort the same row can land at a
    // different index afterwards. LazyPagingItems then rebuilds its index map while the grid is
    // scrolled down, which is the "jumped back to the top" symptom on real devices.
    // Adding the unique primary key as a tiebreaker makes the ordering total and stable.
    @Query("SELECT * FROM photo_assets WHERE isHidden = 0 ORDER BY assetDate DESC, id ASC")
    fun pagingSource(): PagingSource<Int, PhotoAssetEntity>

    @Query("SELECT * FROM photo_assets WHERE isHidden = 0 ORDER BY assetDate DESC, id ASC")
    fun getAllPhotos(): Flow<List<PhotoAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(photos: List<PhotoAssetEntity>)

    @Query("DELETE FROM photo_assets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM photo_assets")
    suspend fun clearAll()

    @Query("SELECT * FROM photo_assets WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): PhotoAssetEntity?

    @Query("SELECT * FROM photo_assets WHERE masterId = :masterId LIMIT 1")
    suspend fun getPhotoByMasterId(masterId: String): PhotoAssetEntity?

    @Query(
        """
        UPDATE photo_assets 
        SET thumbUrl = :thumbUrl, medUrl = :medUrl, originalUrl = :originalUrl, 
            videoComplUrl = :videoComplUrl, urlFetchedAt = :fetchedAt,
            sessionGeneration = :sessionGeneration
        WHERE masterId = :masterId
        """
    )
    suspend fun updateUrls(
        masterId: String,
        thumbUrl: String?,
        medUrl: String?,
        originalUrl: String?,
        videoComplUrl: String?,
        fetchedAt: Long,
        sessionGeneration: Int
    )

    @Query("SELECT COUNT(*) FROM photo_assets WHERE isHidden = 0")
    fun getCount(): Flow<Int>
}
