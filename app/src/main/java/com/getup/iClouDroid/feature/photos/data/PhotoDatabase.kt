package com.getup.iClouDroid.feature.photos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import com.getup.iClouDroid.feature.photos.data.db.PhotoDao
import com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexDao
import com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexEntity
import com.getup.iClouDroid.feature.photos.data.db.ScanStateDao
import com.getup.iClouDroid.feature.photos.data.db.ScanStateEntity
import com.getup.iClouDroid.feature.photos.data.db.SyncStateDao
import com.getup.iClouDroid.feature.photos.data.db.SyncStateEntity

/**
 * Photos-only database.
 *
 * Deliberately separate from any future feature database (drive, notes): bundling every feature's
 * tables into one Room database forces them to share a schema version, so adding a Drive table
 * would mean bumping (and, with a destructive fallback, wiping) the photo cache too.
 */
@Database(
    entities = [
        PhotoAssetEntity::class,
        SyncStateEntity::class,
        PhotoMonthIndexEntity::class,
        ScanStateEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PhotoDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun photoMonthIndexDao(): PhotoMonthIndexDao
    abstract fun scanStateDao(): ScanStateDao

    companion object {
        /**
         * v3 → v4 (P2-016): month sections.
         *
         * Adds `photo_assets.yearMonth` plus the month index and scan-state tables. Deliberately
         * DDL-only: computing `yearMonth` for existing rows means a local-timezone conversion
         * (Joda/`java.util.Calendar`), which SQLite cannot do in an `ALTER`. Existing rows are
         * inserted with the `0` default and backfilled by [MonthScanEngine] on first run, so the
         * migration itself stays a fast, fixed-cost operation.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE photo_assets ADD COLUMN yearMonth INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photo_month_index` (
                        `yearMonth` INTEGER NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `firstAssetDate` INTEGER NOT NULL,
                        `lastAssetDate` INTEGER NOT NULL,
                        `isComplete` INTEGER NOT NULL,
                        PRIMARY KEY(`yearMonth`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_state` (
                        `key` TEXT NOT NULL,
                        `lastMarker` TEXT,
                        `isComplete` INTEGER NOT NULL,
                        `scannedCount` INTEGER NOT NULL,
                        `estimatedTotal` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_photo_assets_yearMonth_assetDate_id` " +
                        "ON `photo_assets` (`yearMonth`, `assetDate`, `id`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: PhotoDatabase? = null

        /**
         * Kept as a convenience entry point for [com.getup.iClouDroid.di.AppContainer]; the
         * container is the only thing that should call it.
         *
         * NOTE: `fallbackToDestructiveMigration()` was removed alongside v4. Wiping the photo
         * cache on every schema bump meant a full re-scan of the account (hundreds of CloudKit
         * round trips) for something as routine as adding a column, so from v4 onwards a real
         * migration must be written. See AGENTS.md §12.
         */
        fun getInstance(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "icloud_photos.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
