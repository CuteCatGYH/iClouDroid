package com.getup.iClouDroid.feature.photos.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import com.getup.iClouDroid.feature.photos.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaStoreDownloader {
    private const val TAG = "MediaStoreDownloader"

    suspend fun downloadAsset(
        context: Context,
        photo: PhotoAssetEntity,
        repository: PhotoRepository
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            var downloadUrl = photo.originalUrl ?: photo.medUrl ?: photo.thumbUrl
            val isExpired = downloadUrl.isNullOrBlank() ||
                photo.sessionGeneration != repository.currentSessionGeneration() ||
                (System.currentTimeMillis() - photo.urlFetchedAt > 45 * 60 * 1000L)

            if (isExpired) {
                Log.i(TAG, "Download URL stale for master=${photo.masterId}, refreshing...")
                val refreshed = repository.refreshUrlsForMaster(photo.masterId)
                if (refreshed.isSuccess) {
                    val urls = refreshed.getOrNull()
                    downloadUrl = urls?.originalUrl ?: urls?.medUrl ?: urls?.thumbUrl
                }
            }

            if (downloadUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("无法获取有效的照片/视频下载链接"))
            }

            val isVideo = photo.mediaType == "VIDEO" ||
                photo.filename.endsWith(".mp4", ignoreCase = true) ||
                photo.filename.endsWith(".mov", ignoreCase = true)

            val mimeType = when {
                isVideo -> if (photo.filename.endsWith(".mov", ignoreCase = true)) "video/quicktime" else "video/mp4"
                photo.filename.endsWith(".heic", ignoreCase = true) -> "image/heic"
                photo.filename.endsWith(".png", ignoreCase = true) -> "image/png"
                else -> "image/jpeg"
            }

            val collection = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }

            val relativePath = if (isVideo) {
                Environment.DIRECTORY_MOVIES + "/iClouDroid"
            } else {
                Environment.DIRECTORY_PICTURES + "/iClouDroid"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, photo.filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                if (photo.assetDate > 0) {
                    put(MediaStore.MediaColumns.DATE_ADDED, photo.assetDate / 1000)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, photo.assetDate / 1000)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("创建 MediaStore 记录失败"))

            var connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            // Retry on HTTP 403 or 410 (CDN signature expired)
            if (connection.responseCode == HttpURLConnection.HTTP_GONE ||
                connection.responseCode == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                Log.w(
                    TAG,
                    "Download URL returned ${connection.responseCode}, refreshing URL for master=${photo.masterId}..."
                )
                connection.disconnect()
                val refreshed = repository.refreshUrlsForMaster(photo.masterId)
                if (refreshed.isSuccess) {
                    val urls = refreshed.getOrNull()
                    downloadUrl = urls?.originalUrl ?: urls?.medUrl ?: urls?.thumbUrl
                    if (!downloadUrl.isNullOrBlank()) {
                        connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 30000
                            readTimeout = 60000
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "Mozilla/5.0")
                        }
                    }
                }
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                resolver.delete(uri, null, null)
                return@withContext Result.failure(
                    Exception("HTTP ${connection.responseCode} 下载失败")
                )
            }

            var inStream: InputStream? = null
            var outStream: OutputStream? = null
            try {
                inStream = connection.inputStream
                outStream = resolver.openOutputStream(uri)
                    ?: throw Exception("无法打开 MediaStore 输出流")

                val buffer = ByteArray(64 * 1024) // 64 KB buffer for streaming
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
                outStream.flush()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            } finally {
                inStream?.close()
                outStream?.close()
                connection.disconnect()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, "Successfully downloaded ${photo.filename} to $uri")
            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download asset", e)
            Result.failure(e)
        }
    }
}
