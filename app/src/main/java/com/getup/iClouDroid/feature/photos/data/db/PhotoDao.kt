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

    @Query("SELECT DISTINCT yearMonth FROM photo_assets WHERE (id IN (:ids) OR masterId IN (:ids)) AND yearMonth != 0")
    suspend fun getYearMonthsForIds(ids: List<String>): List<Int>

    @Query("DELETE FROM photo_assets WHERE id IN (:ids) OR masterId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM photo_assets WHERE assetDate <= 0 OR id = masterId")
    suspend fun deleteOrphanRows(): Int

    // ------------------------------------------------------------------------------------
    // Reconciliation (ISS-031)
    // ------------------------------------------------------------------------------------

    /**
     * Just the identity columns, for diffing against the ids CloudKit returned in this walk.
     *
     * Deliberately not `SELECT *`: a 16k-row library would materialise every column of every row
     * (including four URLs each) just to compare two strings.
     */
    @Query("SELECT id, masterId FROM photo_assets")
    suspend fun getAllIdentities(): List<PhotoIdentity>

    /**
     * Deletes only exact asset ids.
     *
     * Distinct from [deleteByIds], which also matches `masterId` and is used when CloudKit has told
     * us a specific record is gone. Reconciliation must be narrower: it has already applied the
     * conservative "master still alive → keep" rule in memory, so widening the SQL again would
     * undo that decision.
     */
    @Query("DELETE FROM photo_assets WHERE id IN (:ids)")
    suspend fun deleteByAssetIds(ids: List<String>): Int

    @Query("DELETE FROM photo_assets")
    suspend fun clearAll()

    @Query("SELECT * FROM photo_assets WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): PhotoAssetEntity?

    /**
     * All rows sharing one `CPLMaster`.
     *
     * The same underlying media can be exposed by several `CPLAsset` records (edits, re-imports,
     * server-side remaps) — the measured library had 155 such rows. A single-asset lookup with
     * `LIMIT 1` therefore returns an arbitrary member of the group, which is why the refresh paths
     * below work per asset id instead.
     */
    @Query("SELECT * FROM photo_assets WHERE masterId = :masterId")
    suspend fun getPhotosByMasterId(masterId: String): List<PhotoAssetEntity>

    /**
     * Stores freshly minted URLs on exactly one asset.
     *
     * Keyed on `id`, not `masterId`: several assets can share a master, and writing to all of them
     * from one asset's refresh made three different photos share a single signature (and a single
     * `urlFetchedAt`, so the staleness detector could not tell them apart either).
     */
    @Query(
        """
        UPDATE photo_assets 
        SET thumbUrl = :thumbUrl, medUrl = :medUrl, originalUrl = :originalUrl, 
            videoComplUrl = :videoComplUrl, urlFetchedAt = :fetchedAt,
            sessionGeneration = :sessionGeneration
        WHERE id = :assetId
        """
    )
    suspend fun updateUrls(
        assetId: String,
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

    // ------------------------------------------------------------------------------------
    // EXIF / GPS cache (info panel)
    // ------------------------------------------------------------------------------------

    /**
     * Stores the resolved metadata for one asset.
     *
     * An empty [json] is a legitimate value ("fetched, and the record has none"), which is why
     * the column is nullable and the timestamp, not the payload, is the cache marker.
     */
    @Query(
        "UPDATE photo_assets SET exifJson = :json, exifFetchedAt = :fetchedAt WHERE masterId = :masterId"
    )
    suspend fun setExif(masterId: String, json: String?, fetchedAt: Long)

    /**
     * Reads the cached metadata for one asset without observing it.
     *
     * Returns the entity rather than just the two metadata columns because callers also need
     * `yearMonth`/`assetDate` to render the file-info fallback when metadata is absent.
     */
    @Query("SELECT * FROM photo_assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PhotoAssetEntity?
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

/** Projection for [PhotoDao.getAllIdentities] — the minimum needed to reconcile. */
data class PhotoIdentity(
    val id: String,
    val masterId: String
)
