package com.getup.iClouDroid.feature.photos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Access to the month index that drives the gallery's section list (P2-016).
 */
@Dao
interface PhotoMonthIndexDao {

    /** All months that contain photos, newest first — the top-level scroll order. */
    @Query("SELECT * FROM photo_month_index ORDER BY yearMonth DESC")
    fun observeAll(): Flow<List<PhotoMonthIndexEntity>>

    @Query("SELECT * FROM photo_month_index ORDER BY yearMonth DESC")
    suspend fun getAll(): List<PhotoMonthIndexEntity>

    @Query("SELECT * FROM photo_month_index WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun get(yearMonth: Int): PhotoMonthIndexEntity?

    @Query("SELECT MAX(yearMonth) FROM photo_month_index")
    suspend fun getNewestYearMonth(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PhotoMonthIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PhotoMonthIndexEntity)

    @Query("DELETE FROM photo_month_index")
    suspend fun clearAll()

    @Query("DELETE FROM photo_month_index WHERE yearMonth = :yearMonth")
    suspend fun deleteMonth(yearMonth: Int)
}
