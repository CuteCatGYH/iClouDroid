package com.getup.iClouDroid.feature.photos.data.image

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.intercept.Interceptor
import coil.key.Keyer
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.Options
import com.getup.iClouDroid.data.session.SessionManager
import com.getup.iClouDroid.di.AppContainer
import com.getup.iClouDroid.feature.photos.data.PhotoRepository
import com.getup.iClouDroid.feature.photos.data.RefreshedUrls
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import java.net.URL

data class ICloudImageModel(
    val recordName: String,
    val masterId: String,
    val url: String?,
    val fetchedAt: Long,
    val sessionGeneration: Int = 0,
    val quality: Quality = Quality.THUMB
) {
    enum class Quality { THUMB, MEDIUM, ORIGINAL }

    companion object {
        fun fromEntity(entity: PhotoAssetEntity, quality: Quality = Quality.THUMB): ICloudImageModel {
            return ICloudImageModel(
                recordName = entity.id,
                masterId = entity.masterId,
                url = pickUrl(entity, quality),
                fetchedAt = entity.urlFetchedAt,
                sessionGeneration = entity.sessionGeneration,
                quality = quality
            )
        }

        fun pickUrl(entity: PhotoAssetEntity, quality: Quality): String? = when (quality) {
            Quality.THUMB -> entity.thumbUrl ?: entity.medUrl ?: entity.originalUrl
            Quality.MEDIUM -> entity.medUrl ?: entity.originalUrl ?: entity.thumbUrl
            Quality.ORIGINAL -> entity.originalUrl ?: entity.medUrl ?: entity.thumbUrl
        }

        fun pickUrl(urls: RefreshedUrls?, quality: Quality): String? = when (quality) {
            Quality.THUMB -> urls?.thumbUrl ?: urls?.medUrl ?: urls?.originalUrl
            Quality.MEDIUM -> urls?.medUrl ?: urls?.originalUrl ?: urls?.thumbUrl
            Quality.ORIGINAL -> urls?.originalUrl ?: urls?.medUrl ?: urls?.thumbUrl
        }
    }
}

class ICloudPhotoKeyer : Keyer<ICloudImageModel> {
    override fun key(data: ICloudImageModel, options: Options): String {
        return "icloud_${data.recordName}_${data.quality.name.lowercase()}"
    }
}

/**
 * Classifies why an image load failed (P2-011).
 *
 * Only a dead signature is fixable by re-minting the URL. A decoder failure (e.g. an exotic HEIC
 * variant) returns the same broken bytes no matter how many fresh URLs we ask for, so treating it
 * as a URL problem just burns `/records/lookup` calls and floods the log.
 */
internal object ImageLoadFailure {
    /** HTTP status code carried by [throwable], or null when it was not an HTTP failure. */
    fun httpCodeOf(throwable: Throwable?): Int? =
        (throwable as? coil.network.HttpException)?.response?.code

    /** The two statuses a CDN uses when a signed URL is no longer accepted. */
    fun isExpiredUrlCode(code: Int?): Boolean = code == 403 || code == 410

    /** True when the failure means "this signed URL is no longer accepted by the CDN". */
    fun isExpiredUrl(throwable: Throwable?): Boolean =
        isExpiredUrlCode(httpCodeOf(throwable))
}

/**
 * Resolves and, when needed, re-mints iCloud CDN download URLs before Coil executes a request.
 *
 * Two invalidation signals are honoured (P2-005):
 *  1. **Session generation** — a rebuilt Go session mints brand-new signatures, invalidating every
 *     previously stored URL within seconds. Time-based expiry alone cannot detect this.
 *  2. **Age** — CDN signatures expire roughly an hour after issue.
 *
 * On top of the proactive check, any failed load (403/410 or otherwise) triggers exactly one
 * refresh-and-retry, so we are never silently stuck on a dead link.
 */
class ICloudPhotoInterceptor(
    private val repository: PhotoRepository,
    private val sessionManager: SessionManager
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data
        if (data !is ICloudImageModel) {
            return chain.proceed(request)
        }

        val stableKey = "icloud_${data.recordName}_${data.quality.name.lowercase()}"
        val currentGeneration = sessionManager.getSessionGeneration()
        val generationStale = data.sessionGeneration != currentGeneration
        val ageStale = System.currentTimeMillis() - data.fetchedAt > URL_MAX_AGE_MS

        var activeUrl = data.url
        var refreshedProactively = false

        Log.d(
            TAG,
            "load ${data.recordName} quality=${data.quality} " +
                "hasUrl=${!activeUrl.isNullOrBlank()} ageStale=$ageStale genStale=$generationStale"
        )

        if (activeUrl.isNullOrBlank() || ageStale || generationStale) {
            val fresh = refresh(data)
            if (fresh != null) {
                activeUrl = pickFreshUrl(data, fresh)
                refreshedProactively = true
                Log.d(TAG, "refreshed ${data.recordName} -> host=${hostOf(activeUrl)}")
            }
        }

        val result = chain.proceed(buildRequest(request, activeUrl, stableKey))

        when (result) {
            is ErrorResult -> {
                val message = result.throwable.message ?: result.throwable::class.java.simpleName
                Log.w(TAG, "fail ${data.recordName} quality=${data.quality} error=$message")

                // One shot refresh-and-retry, unless we already minted a fresh URL this pass.
                if (!refreshedProactively) {
                    val fresh = refresh(data)
                    val retryUrl = pickFreshUrl(data, fresh)
                    if (!retryUrl.isNullOrBlank() && retryUrl != activeUrl) {
                        Log.i(
                            TAG,
                            "retrying ${data.recordName} with refreshed url host=${hostOf(retryUrl)}"
                        )
                        return chain.proceed(buildRequest(request, retryUrl, stableKey))
                    }
                }
            }
            else -> Log.d(TAG, "ok ${data.recordName} quality=${data.quality}")
        }

        return result
    }

    private fun buildRequest(
        request: coil.request.ImageRequest,
        url: String?,
        stableKey: String
    ): coil.request.ImageRequest {
        return request.newBuilder()
            .data(url ?: "")
            .memoryCacheKey(stableKey)
            .diskCacheKey(stableKey)
            .build()
    }

    private suspend fun refresh(data: ICloudImageModel): RefreshedUrls? {
        val result = repository.refreshUrlsForMaster(data.masterId)
        return result.getOrNull().also {
            if (it == null) {
                Log.w(
                    TAG,
                    "refresh failed for master=${data.masterId}: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    private fun pickFreshUrl(data: ICloudImageModel, urls: RefreshedUrls?): String? {
        return ICloudImageModel.pickUrl(urls, data.quality) ?: data.url
    }

    private fun hostOf(url: String?): String {
        if (url.isNullOrBlank()) return "none"
        return try {
            URL(url).host
        } catch (e: Exception) {
            "invalid"
        }
    }

    companion object {
        private const val TAG = "ICloudPhotoCoil"
        private const val URL_MAX_AGE_MS = 45 * 60 * 1000L
    }
}

object ICloudImageLoaderFactory {
    @Volatile
    private var INSTANCE: ImageLoader? = null

    /**
     * Builds (once) the Coil loader for iCloud assets.
     *
     * Dependencies come from [com.getup.iClouDroid.di.AppContainer] rather than being constructed
     * here, so the loader and the repositories share exactly one `SessionManager` instance.
     */
    fun get(context: Context, container: AppContainer): ImageLoader {
        return INSTANCE ?: synchronized(this) {
            val loader = ImageLoader.Builder(context)
                .components {
                    // P2-010: iCloud hands back the native HEIC for `resOriginalRes`, and some of
                    // those carry an HDR gain map (compatible brand `MiHE` rather than `MiPr`).
                    // Coil's default BitmapFactory decoder returns a null bitmap for them, so
                    // originals showed "image failed to load" while the JPEG thumbnails (already
                    // transcoded server-side) were fine.
                    // ImageDecoder handles gain maps and multi-image HEIF correctly.
                    add(ImageDecoderDecoder.Factory())
                    add(ICloudPhotoKeyer())
                    add(ICloudPhotoInterceptor(container.photoRepository, container.sessionManager))
                }
                .crossfade(true)
                .build()
            INSTANCE = loader
            loader
        }
    }
}
