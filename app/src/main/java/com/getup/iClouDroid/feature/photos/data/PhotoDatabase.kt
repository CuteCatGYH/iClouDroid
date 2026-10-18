package com.getup.iClouDroid.feature.photos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import com.getup.iClouDroid.feature.photos.data.db.PhotoDao
import com.getup.iClouDroid.feature.photos.data.db.SyncStateDao
import com.getup.iClouDroid.feature.photos.data.db.SyncStateEntity

/**
 * Photos-only database.
 *
 * Deliberately separate from any future feature database (drive, notes): bundling every feature's
 * tables into one Room database forces them to share a schema version, so adding a Drive table
 * would mean bumping (and, with `fallbackToDestructiveMigration`, wiping) the photo cache too.
 */
@Database(
    entities = [
        PhotoAssetEntity::class,
        SyncStateEntity::class
    ],
    version = 3,   // v3: added composite (isHidden, assetDate, id) index for the stable paging sort (P2-012)
    exportSchema = false
)
abstract class PhotoDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: PhotoDatabase? = null

        /**
         * Kept as a convenience entry point for [com.getup.iClouDroid.di.AppContainer]; the
         * container is the only thing that should call it.
         */
        fun getInstance(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "icloud_photos.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
