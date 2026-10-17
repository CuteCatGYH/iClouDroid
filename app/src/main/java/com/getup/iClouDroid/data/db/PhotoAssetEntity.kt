package com.getup.iClouDroid.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photo_assets",
    indices = [
        Index(value = ["assetDate"]),
        Index(value = ["masterId"]),
        Index(value = ["isHidden"]),
        // P2-012: cover the paging query's exact ORDER BY so the stable sort stays cheap.
        Index(value = ["isHidden", "assetDate", "id"])
    ]
)
data class PhotoAssetEntity(
    @PrimaryKey
    val id: String,
    val masterId: String,
    val filename: String,
    val assetDate: Long,
    val addedDate: Long,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val duration: Long = 0,
    val mediaType: String = "IMAGE", // "IMAGE", "VIDEO", "LIVE_PHOTO"
    val itemType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0,
    val thumbUrl: String? = null,
    val medUrl: String? = null,
    val originalUrl: String? = null,
    val videoComplUrl: String? = null,
    val recordChangeTag: String? = null,
    val urlFetchedAt: Long = System.currentTimeMillis(),
    /**
     * Go core session generation at the time these URLs were minted.
     *
     * A rebuilt Go [Session] invalidates every previously issued CDN signature immediately, so
     * rows whose generation no longer matches the current one must be refreshed before use
     * (see ISS-016 / P2-005).
     */
    val sessionGeneration: Int = 0
)
