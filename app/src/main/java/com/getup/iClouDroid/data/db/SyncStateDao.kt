package com.getup.iClouDroid.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE `key` = :key LIMIT 1")
    suspend fun getSyncState(key: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun clearSyncState(key: String)
}
