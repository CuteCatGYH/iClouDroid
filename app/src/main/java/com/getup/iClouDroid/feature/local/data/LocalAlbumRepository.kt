package com.getup.iClouDroid.feature.local.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository querying local MediaStore for photos and videos grouped by folder (bucket).
 */
class LocalAlbumRepository(private val context: Context) {

    suspend fun queryLocalAlbums(): List<LocalAlbum> = withContext(Dispatchers.IO) {
        val albumsMap = linkedMapOf<String, AlbumAccumulator>()

        // 1. Query Images
        queryMediaBuckets(
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isImage = true,
            accumulator = albumsMap
        )

        // 2. Query Videos
        queryMediaBuckets(
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isImage = false,
            accumulator = albumsMap
        )

        albumsMap.values.map { acc ->
            LocalAlbum(
                bucketId = acc.bucketId,
                name = acc.name,
                coverUri = acc.coverUri,
                itemCount = acc.count,
                relativePath = acc.relativePath
            )
        }.sortedByDescending { it.itemCount }
    }

    private fun queryMediaBuckets(
        contentUri: Uri,
        isImage: Boolean,
        accumulator: MutableMap<String, AlbumAccumulator>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                contentUri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val bucketId = cursor.getString(bucketIdCol) ?: "unknown"
                    val bucketName = cursor.getString(bucketNameCol) ?: "未命名相册"

                    val itemUri = ContentUris.withAppendedId(contentUri, id)

                    val existing = accumulator[bucketId]
                    if (existing == null) {
                        accumulator[bucketId] = AlbumAccumulator(
                            bucketId = bucketId,
                            name = bucketName,
                            coverUri = itemUri,
                            count = 1
                        )
                    } else {
                        existing.count += 1
                        if (existing.coverUri == null) {
                            existing.coverUri = itemUri
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Permission denied or table unavailable
        }
    }

    private data class AlbumAccumulator(
        val bucketId: String,
        val name: String,
        var coverUri: Uri?,
        var count: Int,
        var relativePath: String = ""
    )
}
