package com.getup.iClouDroid.feature.photos.data

import android.content.Context
import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.getup.iClouDroid.data.session.SessionManager
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import com.getup.iClouDroid.feature.photos.data.db.SyncStateEntity
import com.getup.icloudcore.Icloudcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RefreshedUrls(
    val thumbUrl: String?,
    val medUrl: String?,
    val originalUrl: String?,
    val videoComplUrl: String?
)

class PhotoRepository(
    private val database: PhotoDatabase,
    private val sessionManager: SessionManager
) {
    private val photoDao = database.photoDao()
    private val syncStateDao = database.syncStateDao()
    private val sessionMutex = Mutex()

    // ----------------------------------------------------------------------------------------
    // URL refresh de-duplication (P2-008)
    // ----------------------------------------------------------------------------------------

    private val refreshMutex = Mutex()

    /** Dedicated scope for sharing in-flight refreshes across concurrent API + Coil callers. */
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** masterId -> epoch millis of the last *successful* URL refresh. */
    private val recentRefreshes = mutableMapOf<String, Long>()

    /** masterId -> in-flight refresh, so N concurrent failures share a single /records/lookup. */
    private val inFlightRefreshes = mutableMapOf<String, Deferred<Result<RefreshedUrls>>>()

    /** Timestamp of the last session rebuild (used to join the "everything just expired" storm). */
    @Volatile
    private var sessionUrlRefreshedAt: Long = 0L

    val totalCountFlow: Flow<Int> = photoDao.getCount()

    @OptIn(ExperimentalPagingApi::class)
    val photoPagingFlow: Flow<PagingData<PhotoAssetEntity>> = Pager(
        config = PagingConfig(
            pageSize = 60,
            // P2-016: every APPEND returns MediatorResult.Success, and Paging 3 responds by
            // invalidating the PagingSource and reloading up to `initialLoadSize` rows. Measured
            // on device: the reloaded `itemCount` always collapses to exactly `initialLoadSize`
            // (60 -> 60, 360 -> 360), and any scroll position beyond that gets clamped back.
            //
            // That invalidation cannot be avoided while a RemoteMediator is in play, so instead
            // make sure it always happens while the user is nowhere near the boundary:
            //   - prefetchDistance is huge, so APPEND kicks off many pages before the user
            //     reaches the end of the loaded window;
            //   - initialLoadSize is large enough to comfortably contain the anchor that Room's
            //     PagingSource hands back on reload.
            // The user then never sees the collapse, because it always happens far below the
            // viewport. Rows are plain Room entities (no bitmaps), so a wide window is cheap.
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false,
            initialLoadSize = INITIAL_LOAD_SIZE
        ),
        remoteMediator = PhotoRemoteMediator(this, database),
        pagingSourceFactory = { photoDao.pagingSource() }
    ).flow

    /**
     * Session Guard: Verifies that Go core has an active session.
     * Rebuilds session transparently using stored cookies if Go core's session is missing or expired.
     */
    suspend fun ensureGoSession(force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            try {
                if (!force) {
                    val summaryStr = Icloudcore.getSessionStateSummary()
                    val summaryJson = if (summaryStr.startsWith("{")) JSONObject(summaryStr) else null
                    val hasActiveSession = summaryJson != null &&
                        summaryJson.optBoolean("hasAuthToken", false) &&
                        summaryJson.optString("ckDatabaseURL", "").isNotBlank()

                    if (hasActiveSession) {
                        // NOTE: deliberately do NOT bump the generation here. This path runs on every
                        // API entry point; bumping would invalidate every stored URL dozens of times
                        // per second and force a `/records/lookup` per grid cell (P2-008 storm).
                        return@withContext Result.success(Unit)
                    }
                }

                Log.i(TAG, "Rebuilding Go core session (force=$force)...")
                val cookieStr = sessionManager.getCookieString()
                if (cookieStr.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("本地缺少 iCloud Cookie 凭证，请重新登录"))
                }

                val isChina = sessionManager.isChinaMainland()
                val jsonResult = Icloudcore.initSession(cookieStr, isChina)
                Log.d(TAG, "Rebuilt Go session result: $jsonResult")

                val json = JSONObject(jsonResult)
                val success = json.optBoolean("success", false)
                if (!success) {
                    val errMsg = json.optString("errorMessage", "会话初始化失败，请在“账号”页面重新登录")
                    return@withContext Result.failure(Exception(errMsg))
                }

                val dsid = json.optString("dsid", "")
                val ckUrl = json.optString("ckDatabaseUrl", "")
                if (dsid.isNotBlank() && ckUrl.isNotBlank()) {
                    sessionManager.updateRouteInfo(dsid, ckUrl)
                }

                // A brand new Go session has just minted fresh CWW signatures, so every URL
                // currently stored is dead. Bump the generation exactly once here — and only
                // here — so rows carry the generation that produced their URLs.
                markSessionReady()

                // NOTE (P2-014): deliberately do NOT drop the continuationMarker here. This
                // method also runs on the plain cold-start path (force=false) where we are
                // merely re-attaching to a cached DB — the persisted cursor is still valid and
                // is our only way to keep paginating. It is dropped only on the auth-error
                // recovery path, see [invalidateContinuationCursor].

                if (Icloudcore.hasCookieUpdates()) {
                    val updatedCookies = Icloudcore.getCurrentCookieString()
                    val oldCookies = sessionManager.getCookieString()
                    if (!updatedCookies.isNullOrBlank() && updatedCookies != oldCookies) {
                        Log.d(
                            TAG,
                            "Cookies refreshed (len ${oldCookies?.length ?: 0} -> ${updatedCookies.length})"
                        )
                        sessionManager.saveSession(updatedCookies, isChina, dsid, ckUrl)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "ensureGoSession error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Records that a fresh Go session generation is now active.
     *
     * Bumping the counter (persisted) is what invalidates every previously minted download URL:
     * any [PhotoAssetEntity] stamped with an older generation is treated as stale by
     * [com.getup.iClouDroid.feature.photos.data.image.ICloudPhotoInterceptor] and force-refreshed instead of
     * being requested with a dead signature.
     */
    private fun markSessionReady() {
        sessionManager.bumpSessionGeneration()
        sessionUrlRefreshedAt = System.currentTimeMillis()
    }

    /**
     * Drops the persisted `continuationMarker` (P2-014).
     *
     * The marker is a CloudKit cursor bound to the session that produced it. Once a fetch has
     * come back `401` and we have rebuilt the session, that cursor is known-dead: replaying it
     * just earns another `401`, another rebuild, and another dead cursor — the 17s / 4-rebuild
     * loop of P2-014.
     *
     * IMPORTANT: this must only be called on the **auth-error recovery path**, never from
     * [ensureGoSession] itself. `ensureGoSession(force = false)` also runs on plain cold start
     * while we are re-attaching to a cached DB, where the stored cursor is still perfectly
     * good — wiping it there silently stops all further pagination ("No more photos to fetch
     * via continuationMarker").
     *
     * After clearing, the caller should re-fetch with an empty marker. Results are keyed by
     * `id`, so the overlap de-duplicates on upsert.
     */
    private suspend fun invalidateContinuationCursor() {
        val state = runCatching { syncStateDao.getSyncState(KEY_PRIMARY_SYNC) }.getOrNull() ?: return
        if (state.continuationMarker.isNullOrBlank()) return
        Log.d(TAG, "Auth recovery - dropping dead continuationMarker")
        syncStateDao.setSyncState(state.copy(continuationMarker = null, hasMore = true))
    }

    /**
     * Structured auth-error detection.
     *
     * IMPORTANT: never do plain `jsonStr.contains("401")` on the raw response — CloudKit's
     * base64 `continuationMarker` / `syncToken` / record UUIDs routinely contain the literal
     * digits `401` / `421`, which used to make every other page look like an auth failure and
     * trigger a full session rebuild (see ISS-015 / P2-001).
     */
    private fun isAuthError(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)

            // Preference 1: structured errorCode emitted by the Go core layer.
            val errorCode = json.optString("errorCode", "")
            if (errorCode.isNotBlank()) {
                return errorCode == "AUTH_EXPIRED"
            }

            if (json.optBoolean("success", false)) {
                return false
            }

            // Preference 2: any explicitly reported HTTP status.
            val httpStatus = json.optInt("httpStatus", 0)
            if (httpStatus == 401 || httpStatus == 421) {
                return true
            }

            // Fallback: only inspect the errorMessage field, with a word-boundary regex, so
            // arbitrary base64 content can never be mistaken for a status code.
            val msg = json.optString("errorMessage", "")
            if (msg.isBlank()) {
                return false
            }
            msg.contains("AUTH_EXPIRED") ||
                msg.contains("No active session") ||
                msg.contains("CloudKit database URL is not set") ||
                AUTH_STATUS_REGEX.containsMatchIn(msg)
        } catch (e: Exception) {
            // Fail open: a parse failure must never trigger an expensive session rebuild.
            Log.w(TAG, "isAuthError parse failed, treating as non-auth error: ${e.message}")
            false
        }
    }

    private fun parseHttpStatus(jsonStr: String): Int {
        return try {
            JSONObject(jsonStr).optInt("httpStatus", 0)
        } catch (e: Exception) {
            0
        }
    }

    private fun parseErrorMessage(jsonStr: String): String {
        return try {
            JSONObject(jsonStr).optString("errorMessage", "")
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun checkZoneReady(): Result<Boolean> = withContext(Dispatchers.IO) {
        var sessionCheck = ensureGoSession()
        if (sessionCheck.isFailure) {
            return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话建立失败"))
        }

        try {
            var jsonStr = Icloudcore.checkPrimaryZone()
            if (isAuthError(jsonStr)) {
                Log.w(TAG, "checkZoneReady returned auth error, forcing session refresh...")
                sessionCheck = ensureGoSession(force = true)
                if (sessionCheck.isFailure) {
                    return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话重建失败"))
                }
                jsonStr = Icloudcore.checkPrimaryZone()
            }

            val json = JSONObject(jsonStr)
            val success = json.optBoolean("success", false)
            val ready = json.optBoolean("ready", false)
            if (success && ready) {
                Result.success(true)
            } else {
                val err = json.optString("errorMessage", "PrimarySync 分区尚未就绪")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchInitialPage(limit: Int = 100): Result<Int> = withContext(Dispatchers.IO) {
        var sessionCheck = ensureGoSession()
        if (sessionCheck.isFailure) {
            return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话建立失败"))
        }

        try {
            Log.d(TAG, "Fetching initial photo page (limit=$limit)...")
            var jsonStr = Icloudcore.fetchPhotoPage("", limit.toLong())
            if (isAuthError(jsonStr)) {
                Log.w(TAG, "fetchInitialPage returned auth error, forcing session refresh...")
                sessionCheck = ensureGoSession(force = true)
                if (sessionCheck.isFailure) {
                    return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话重建失败"))
                }
                jsonStr = Icloudcore.fetchPhotoPage("", limit.toLong())
            }

            val json = JSONObject(jsonStr)
            val success = json.optBoolean("success", false)

            if (!success) {
                val err = json.optString("errorMessage", "拉取首屏照片失败")
                return@withContext Result.failure(Exception(err))
            }

            val photos = parsePhotoItems(json.optJSONArray("photos"))
            val marker = json.optString("continuationMarker", "")
            val hasMore = json.optBoolean("hasMore", false)
            val syncToken = json.optString("syncToken", "")

            Log.d(TAG, "Initial page fetched: ${photos.size} photos, hasMore=$hasMore")

            photoDao.upsertAll(photos)
            syncStateDao.setSyncState(
                SyncStateEntity(
                    key = KEY_PRIMARY_SYNC,
                    syncToken = syncToken.ifBlank { null },
                    continuationMarker = marker.ifBlank { null },
                    hasMore = hasMore,
                    lastSyncTime = System.currentTimeMillis()
                )
            )

            Result.success(photos.size)
        } catch (e: Exception) {
            Log.e(TAG, "fetchInitialPage error", e)
            Result.failure(e)
        }
    }

    suspend fun fetchNextPage(limit: Int = 100): Result<Int> = withContext(Dispatchers.IO) {
        var sessionCheck = ensureGoSession()
        if (sessionCheck.isFailure) {
            return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话建立失败"))
        }

        try {
            val state = syncStateDao.getSyncState(KEY_PRIMARY_SYNC)
            val marker = state?.continuationMarker
            if (marker.isNullOrBlank() || state.hasMore == false) {
                Log.d(TAG, "No more photos to fetch via continuationMarker")
                return@withContext Result.success(0)
            }

            Log.d(TAG, "Fetching next photo page (limit=$limit)")
            var jsonStr = Icloudcore.fetchPhotoPage(marker, limit.toLong())
            if (isAuthError(jsonStr)) {
                Log.w(
                    TAG,
                    "fetchNextPage auth error (status=${parseHttpStatus(jsonStr)}), forcing session refresh. " +
                        "detail=${parseErrorMessage(jsonStr).take(200)}"
                )
                sessionCheck = ensureGoSession(force = true)
                if (sessionCheck.isFailure) {
                    return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话重建失败"))
                }
                // P2-014: `marker` belonged to the dead session and is now guaranteed invalid.
                // Replaying it is what produced the endless 401 -> rebuild -> 401 loop.
                // Drop the persisted cursor and restart from the live end; rows are keyed by
                // `id`, so the overlap de-duplicates on the upsert below.
                invalidateContinuationCursor()
                jsonStr = Icloudcore.fetchPhotoPage("", limit.toLong())
            }

            val json = JSONObject(jsonStr)
            val success = json.optBoolean("success", false)

            if (!success) {
                val err = json.optString("errorMessage", "分页拉取照片失败")
                return@withContext Result.failure(Exception(err))
            }

            val photos = parsePhotoItems(json.optJSONArray("photos"))
            val nextMarker = json.optString("continuationMarker", "")
            val hasMore = json.optBoolean("hasMore", false)
            val syncToken = json.optString("syncToken", state.syncToken ?: "")

            Log.d(TAG, "Next page returned: ${photos.size} photos, hasMore=$hasMore")
            photoDao.upsertAll(photos)

            syncStateDao.setSyncState(
                SyncStateEntity(
                    key = KEY_PRIMARY_SYNC,
                    syncToken = syncToken.ifBlank { null },
                    continuationMarker = nextMarker.ifBlank { null },
                    hasMore = hasMore,
                    lastSyncTime = System.currentTimeMillis()
                )
            )

            Result.success(photos.size)
        } catch (e: Exception) {
            Log.e(TAG, "fetchNextPage error", e)
            Result.failure(e)
        }
    }

    suspend fun syncIncremental(limit: Int = 100): Result<Int> = withContext(Dispatchers.IO) {
        var sessionCheck = ensureGoSession()
        if (sessionCheck.isFailure) {
            return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话建立失败"))
        }

        try {
            val state = syncStateDao.getSyncState(KEY_PRIMARY_SYNC)
            val syncToken = state?.syncToken ?: ""
            if (syncToken.isBlank()) {
                Log.d(TAG, "No syncToken found, falling back to fetchInitialPage")
                return@withContext fetchInitialPage(limit)
            }

            Log.d(TAG, "Starting incremental sync with token: ${syncToken.take(15)}...")
            var jsonStr = Icloudcore.fetchChanges(syncToken, limit.toLong())
            if (isAuthError(jsonStr)) {
                Log.w(TAG, "syncIncremental returned auth error, forcing session refresh...")
                sessionCheck = ensureGoSession(force = true)
                if (sessionCheck.isFailure) {
                    return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话重建失败"))
                }
                jsonStr = Icloudcore.fetchChanges(syncToken, limit.toLong())
            }

            val json = JSONObject(jsonStr)
            val success = json.optBoolean("success", false)

            if (!success) {
                val err = json.optString("errorMessage", "增量同步失败")
                return@withContext Result.failure(Exception(err))
            }

            val newPhotos = parsePhotoItems(json.optJSONArray("photos"))
            val deletedIds = parseStringArray(json.optJSONArray("deletedIds"))
            val nextToken = json.optString("syncToken", syncToken)
            val moreComing = json.optBoolean("moreComing", false)

            if (deletedIds.isNotEmpty()) {
                Log.d(TAG, "Deleting ${deletedIds.size} removed assets from local db")
                photoDao.deleteByIds(deletedIds)
            }

            if (newPhotos.isNotEmpty()) {
                Log.d(TAG, "Upserting ${newPhotos.size} new/modified photos to local db")
                photoDao.upsertAll(newPhotos)
            }

            syncStateDao.setSyncState(
                SyncStateEntity(
                    key = KEY_PRIMARY_SYNC,
                    syncToken = nextToken,
                    continuationMarker = state?.continuationMarker,
                    hasMore = state?.hasMore ?: false,
                    lastSyncTime = System.currentTimeMillis()
                )
            )

            Result.success(newPhotos.size)
        } catch (e: Exception) {
            Log.e(TAG, "syncIncremental error", e)
            Result.failure(e)
        }
    }

    /**
     * Single entry point for URL refresh used by every consumer (Coil interceptor, image/video
     * detail viewer, MediaStore downloader).
     *
     * De-duplicates aggressively (P2-008): when a session rebuild invalidates every cached
     * signature at once, dozens of grid items fail simultaneously. Without de-duplication each
     * failure would fire its own `/records/lookup` and get the account rate-limited.
     *
     * Protection layers:
     *  1. If a refresh for this master is already in flight, await it instead of firing a new one.
     *  2. If this master was successfully refreshed within [REFRESH_DEDUP_WINDOW_MS], reuse the DB
     *     values without any network call.
     *  3. If the whole session was just rebuilt within [REFRESH_DEDUP_WINDOW_MS], collapse the
     *     resulting thundering herd into a short randomized delay so lookups are spread out.
     *
     * [force] bypasses layer 2 only (P2-011). It exists for explicit, user-driven, single-asset
     * recovery — a tap on "retry" / opening the detail viewer. Those are one-shot actions for one
     * master, so they can never form a burst, but they *must* be able to override the window: a
     * grid thumbnail that refreshed 30s ago would otherwise silently veto the detail page's
     * attempt to re-mint a dead URL, leaving it permanently broken. Layer 1 still applies, so a
     * forced call joins any genuinely concurrent refresh instead of duplicating it.
     */
    suspend fun refreshUrlsForMaster(
        masterId: String,
        force: Boolean = false
    ): Result<RefreshedUrls> {
        // Layer 2: recent successful refresh -> serve from DB (unless the caller forces a re-mint).
        if (!force) {
            refreshMutex.withLock {
                val last = recentRefreshes[masterId] ?: 0L
                if (System.currentTimeMillis() - last < REFRESH_DEDUP_WINDOW_MS) {
                    Log.d(TAG, "refreshUrlsForMaster skipped (deduped within window) master=$masterId")
                    return withContext(Dispatchers.IO) { readUrlsFromDb(masterId) }
                }
            }
        }

        // Layer 1: join an in-flight refresh for the same master.
        val existing = refreshMutex.withLock { inFlightRefreshes[masterId] }
        if (existing != null) {
            Log.d(TAG, "refreshUrlsForMaster joining in-flight request master=$masterId")
            return existing.await()
        }

        val deferred = refreshScope.async {
            try {
                // Layer 3: spread out the burst that follows a session rebuild.
                val sinceSession = System.currentTimeMillis() - sessionUrlRefreshedAt
                if (sinceSession in 0 until REFRESH_DEDUP_WINDOW_MS) {
                    delay((20L..120L).random())
                }

                val result = doRefreshUrlsForMaster(masterId)
                if (result.isSuccess) {
                    refreshMutex.withLock {
                        recentRefreshes[masterId] = System.currentTimeMillis()
                    }
                }
                result
            } finally {
                refreshMutex.withLock { inFlightRefreshes.remove(masterId) }
            }
        }

        refreshMutex.withLock { inFlightRefreshes[masterId] = deferred }
        return deferred.await()
    }

    /**
     * Batched variant — one `/records/lookup` for many masters.
     * Go's [Icloudcore.refreshAssetUrls] already accepts a JSON array of record names.
     */
    suspend fun refreshUrlsForMasters(masterIds: List<String>): Result<Map<String, RefreshedUrls>> =
        withContext(Dispatchers.IO) {
            val unique = masterIds.distinct().filter { it.isNotBlank() }
            if (unique.isEmpty()) {
                return@withContext Result.success(emptyMap())
            }

            ensureGoSession().onFailure { return@withContext Result.failure(it) }

            try {
                val payload = JSONArray().apply { unique.forEach { put(it) } }.toString()
                Log.d(TAG, "refreshUrlsForMasters batch size=${unique.size}")

                var jsonStr = Icloudcore.refreshAssetUrls(payload)
                if (isAuthError(jsonStr)) {
                    ensureGoSession(force = true).onFailure { return@withContext Result.failure(it) }
                    jsonStr = Icloudcore.refreshAssetUrls(payload)
                }

                val json = JSONObject(jsonStr)
                if (!json.optBoolean("success", false)) {
                    val err = json.optString("errorMessage", "批量刷新直链失败")
                    return@withContext Result.failure(Exception(err))
                }

                val urlsObj = json.optJSONObject("urls")
                val now = System.currentTimeMillis()
                // Stamp the refreshed rows with the *current* generation, otherwise they stay
                // permanently "stale" and every subsequent load re-triggers a lookup.
                val generation = sessionManager.getSessionGeneration()
                val out = mutableMapOf<String, RefreshedUrls>()

                for (masterId in unique) {
                    val masterUrls = urlsObj?.optJSONObject(masterId) ?: continue
                    val thumb = masterUrls.optString("thumbUrl").ifBlank { null }
                    val med = masterUrls.optString("medUrl").ifBlank { null }
                    val orig = masterUrls.optString("originalUrl").ifBlank { null }
                    val vid = masterUrls.optString("videoComplUrl").ifBlank { null }

                    photoDao.updateUrls(masterId, thumb, med, orig, vid, now, generation)
                    out[masterId] = RefreshedUrls(thumb, med, orig, vid)
                }

                refreshMutex.withLock {
                    out.keys.forEach { recentRefreshes[it] = now }
                }

                Result.success(out)
            } catch (e: Exception) {
                Log.e(TAG, "refreshUrlsForMasters error", e)
                Result.failure(e)
            }
        }

    private suspend fun readUrlsFromDb(masterId: String): Result<RefreshedUrls> {
        val row = photoDao.getPhotoByMasterId(masterId)
            ?: return Result.failure(Exception("Master $masterId not found in local db"))
        return Result.success(
            RefreshedUrls(row.thumbUrl, row.medUrl, row.originalUrl, row.videoComplUrl)
        )
    }

    private suspend fun doRefreshUrlsForMaster(masterId: String): Result<RefreshedUrls> = withContext(Dispatchers.IO) {
        var sessionCheck = ensureGoSession()
        if (sessionCheck.isFailure) {
            return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话建立失败"))
        }

        try {
            val payload = JSONArray().apply { put(masterId) }.toString()
            var jsonStr = Icloudcore.refreshAssetUrls(payload)
            if (isAuthError(jsonStr)) {
                Log.w(
                    TAG,
                    "refreshUrlsForMaster auth error (status=${parseHttpStatus(jsonStr)}), forcing session refresh"
                )
                sessionCheck = ensureGoSession(force = true)
                if (sessionCheck.isFailure) {
                    return@withContext Result.failure(sessionCheck.exceptionOrNull() ?: Exception("会话重建失败"))
                }
                jsonStr = Icloudcore.refreshAssetUrls(payload)
            }

            val json = JSONObject(jsonStr)
            val success = json.optBoolean("success", false)

            if (!success) {
                val err = json.optString("errorMessage", "刷新直链失败")
                return@withContext Result.failure(Exception(err))
            }

            val urlsObj = json.optJSONObject("urls")
            val masterUrls = urlsObj?.optJSONObject(masterId)
            if (masterUrls != null) {
                val thumb = masterUrls.optString("thumbUrl").ifBlank { null }
                val med = masterUrls.optString("medUrl").ifBlank { null }
                val orig = masterUrls.optString("originalUrl").ifBlank { null }
                val vid = masterUrls.optString("videoComplUrl").ifBlank { null }

                val fetchedAt = System.currentTimeMillis()
                photoDao.updateUrls(
                    masterId = masterId,
                    thumbUrl = thumb,
                    medUrl = med,
                    originalUrl = orig,
                    videoComplUrl = vid,
                    fetchedAt = fetchedAt,
                    sessionGeneration = sessionManager.getSessionGeneration()
                )

                Result.success(RefreshedUrls(thumb, med, orig, vid))
            } else {
                Result.failure(Exception("Master $masterId not found in refresh response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshUrlsForMaster error", e)
            Result.failure(e)
        }
    }

    private fun parsePhotoItems(arr: JSONArray?): List<PhotoAssetEntity> {
        if (arr == null) return emptyList()
        val list = mutableListOf<PhotoAssetEntity>()
        val now = System.currentTimeMillis()
        // Stamp rows with the generation that actually minted these URLs.
        val generation = sessionManager.getSessionGeneration()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            if (id.isBlank()) continue

            val masterId = obj.optString("masterId", id)
            val filename = obj.optString("filename", id)
            val rawAssetDate = obj.optLong("assetDate", 0L)
            // P2-016: never fall back to `now` for a missing assetDate. Doing so gives the same
            // row a different sort key on every page fetch, which re-pins it to the top of the
            // `ORDER BY assetDate DESC` list and makes the grid jump on each APPEND. A stable 0
            // sorts to the bottom and stays put.
            val assetDate = if (rawAssetDate > 0L) rawAssetDate else 0L
            val addedDate = obj.optLong("addedDate", 0L).takeIf { it > 0L } ?: assetDate
            val isFavorite = obj.optBoolean("isFavorite", false)
            val isHidden = obj.optBoolean("isHidden", false)
            val duration = obj.optLong("duration", 0)
            val mediaType = obj.optString("mediaType", "IMAGE")
            val itemType = obj.optString("itemType", "")
            val width = obj.optInt("width", 0)
            val height = obj.optInt("height", 0)
            val size = obj.optLong("size", 0)
            val thumbUrl = obj.optString("thumbUrl").ifBlank { null }
            val medUrl = obj.optString("medUrl").ifBlank { null }
            val originalUrl = obj.optString("originalUrl").ifBlank { null }
            val videoComplUrl = obj.optString("videoComplUrl").ifBlank { null }
            val changeTag = obj.optString("recordChangeTag").ifBlank { null }

            list.add(
                PhotoAssetEntity(
                    id = id,
                    masterId = masterId,
                    filename = filename,
                    assetDate = assetDate,
                    addedDate = addedDate,
                    isFavorite = isFavorite,
                    isHidden = isHidden,
                    duration = duration,
                    mediaType = mediaType,
                    itemType = itemType,
                    width = width,
                    height = height,
                    size = size,
                    thumbUrl = thumbUrl,
                    medUrl = medUrl,
                    originalUrl = originalUrl,
                    videoComplUrl = videoComplUrl,
                    recordChangeTag = changeTag,
                    urlFetchedAt = now,
                    sessionGeneration = generation
                )
            )
        }
        return list
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) {
                list.add(s)
            }
        }
        return list
    }

    /**
     * Current Go core session generation. Rows stamped with a different value carry dead URLs.
     */
    fun currentSessionGeneration(): Int = sessionManager.getSessionGeneration()

    companion object {
        private const val TAG = "PhotoRepository"
        const val KEY_PRIMARY_SYNC = "PRIMARY_SYNC"

        /**
         * Rows fetched on the first page after a PagingSource invalidation (P2-016).
         *
         * Each APPEND's [androidx.paging.RemoteMediator.MediatorResult.Success] invalidates the
         * source and reloads up to this many rows — measured on device, `itemCount` always collapses
         * to exactly this value. Keep it well above any realistic anchor so a reload never truncates
         * the user's current position.
         */
        const val INITIAL_LOAD_SIZE = 60 * 20

        /**
         * How many items before the loaded boundary APPEND starts (P2-016).
         *
         * The invalidation that follows every APPEND cannot be avoided, so it must be scheduled to
         * happen while the user is still well clear of the boundary — the temporary `itemCount`
         * collapse then lands below the viewport and is invisible.
         *
         * This started out enormous (60*8) to push APPEND as early as possible, but on a fast
         * scroll that requested several pages back-to-back and CloudKit started returning
         * `context deadline exceeded` — the firehose was too wide. Three pages of lookahead is
         * still far more than a screenful, which is all that is needed.
         */
        const val PREFETCH_DISTANCE = 60 * 3

        /**
         * Window within which a successful URL refresh for a given master is reused instead of
         * hitting `/records/lookup` again (P2-008).
         */
        private const val REFRESH_DEDUP_WINDOW_MS = 5 * 60 * 1000L

        /**
         * Matches an explicit HTTP status token such as `HTTP 401`, `status:421`, `StatusCode=401`.
         * The `\b` word boundary prevents accidental hits inside longer digit/base64 runs.
         */
        private val AUTH_STATUS_REGEX =
            Regex("""(?:HTTP|status|StatusCode)\s*[:=]?\s*(?:401|421)\b""", RegexOption.IGNORE_CASE)
    }
}
