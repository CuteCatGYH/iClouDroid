package com.getup.iClouDroid.feature.photos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    // P2-016: `assetDate` alone is NOT a stable ordering key — many photos share the same second,
    // so ties resolve non-deterministically and the same row can land in a different position on
    // each query. Adding the unique primary key as a tiebreaker makes the ordering total.
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

    // ------------------------------------------------------------------------------------
    // Month sections (P2-016)
    // ------------------------------------------------------------------------------------

    /**
     * Photos belonging to one month, newest first.
     *
     * Each month is subscribed separately by the UI, so only the months actually on screen are
     * ever materialised — subscribing to the whole library instead would pin every entity in
     * memory for a 16k-item account.
     *
     * `id ASC` is the tiebreaker: many photos share an `assetDate` second, and without a total
     * order the same row can land in a different position on each query (ISS-021).
     */
    @Query(
        """
        SELECT * FROM photo_assets
        WHERE isHidden = 0 AND yearMonth = :yearMonth
        ORDER BY assetDate DESC, id ASC
        """
    )
    fun observeMonth(yearMonth: Int): Flow<List<PhotoAssetEntity>>

    @Query("SELECT COUNT(*) FROM photo_assets WHERE isHidden = 0 AND yearMonth = :yearMonth")
    suspend fun countMonth(yearMonth: Int): Int

    /**
     * Aggregate needed to rebuild one month's index row.
     *
     * Room cannot express an `INSERT ... SELECT` with aggregates, so the caller reads this and
     * then upserts the resulting [PhotoMonthIndexEntity].
     */
    @Query(
        """
        SELECT COUNT(*) AS itemCount, MAX(assetDate) AS firstAssetDate, MIN(assetDate) AS lastAssetDate
        FROM photo_assets
        WHERE isHidden = 0 AND yearMonth = :yearMonth
        """
    )
    suspend fun monthAggregate(yearMonth: Int): MonthAggregate?

    /** Distinct months currently present, so the index can be rebuilt wholesale after a backfill. */
    @Query("SELECT DISTINCT yearMonth FROM photo_assets WHERE isHidden = 0 AND yearMonth != 0")
    suspend fun distinctYearMonths(): List<Int>

    /** Rows written before the v4 migration (and not yet reached by the scanner). */
    @Query("SELECT id, assetDate FROM photo_assets WHERE yearMonth = 0")
    suspend fun getUnbucketed(): List<PhotoIdWithDate>

    @Query("UPDATE photo_assets SET yearMonth = :yearMonth WHERE id = :id")
    suspend fun setYearMonth(id: String, yearMonth: Int)

    @Query("SELECT COUNT(*) FROM photo_assets WHERE yearMonth = 0")
    suspend fun countUnbucketed(): Int
}

/** Projection for [PhotoDao.monthAggregate]. */
data class MonthAggregate(
    val itemCount: Int,
    val firstAssetDate: Long?,
    val lastAssetDate: Long?
)

/** Projection for [PhotoDao.getUnbucketed]. */
data class PhotoIdWithDate(
    val id: String,
    val assetDate: Long
)
