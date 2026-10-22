package com.getup.iClouDroid.feature.photos.ui

import android.text.format.Formatter
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.getup.iClouDroid.di.rememberAppContainer
import com.getup.iClouDroid.feature.photos.data.PhotoRepository
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import com.getup.iClouDroid.feature.photos.data.image.ICloudImageLoaderFactory
import com.getup.iClouDroid.feature.photos.data.image.ICloudImageModel
import com.getup.iClouDroid.feature.photos.data.image.ImageLoadFailure
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun PhotoDetailViewer(
    photo: PhotoAssetEntity,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val container = rememberAppContainer()
    val repository = container.photoRepository
    var showInfoSheet by remember { mutableStateOf(false) }
    val isVideo = photo.mediaType == "VIDEO" ||
        photo.filename.endsWith(".mp4", ignoreCase = true) ||
        photo.filename.endsWith(".mov", ignoreCase = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = photo.filename,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                color = Color.White
                            )
                            Text(
                                text = formatDate(photo.assetDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "详情",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在下载原文件...")
                            }
                        }
                        is DownloadState.Success -> {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("已保存到系统相册")
                            }
                        }
                        else -> {
                            Button(
                                onClick = onDownload,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isVideo) "下载原视频到相册" else "下载原图到相册")
                            }
                        }
                    }
                }
            },
            containerColor = Color.Black
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {
                    VideoPlayerView(photo = photo, repository = repository)
                } else {
                    ImageDetailView(photo = photo, repository = repository)
                }
            }

            if (showInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showInfoSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    PhotoInfoContent(photo = photo)
                }
            }
        }
    }
}

/**
 * Full-size image preview with automatic dead-link recovery (P2-004).
 *
 * CloudKit CDN signatures die whenever the Go session is rebuilt, which can be seconds after a
 * row was written. A single time-based pre-check is therefore not enough: on failure we re-mint
 * the URL and retry once, so the user is not left staring at "图片加载失败".
 */
@Composable
private fun ImageDetailView(
    photo: PhotoAssetEntity,
    repository: PhotoRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = rememberAppContainer()
    val imageLoader = remember { ICloudImageLoaderFactory.get(context, container) }

    var reloadToken by remember(photo.id) { mutableIntStateOf(0) }
    var retryCount by remember(photo.id) { mutableIntStateOf(0) }
    var loadFailed by remember(photo.id) { mutableStateOf(false) }
    var isUrlIssue by remember(photo.id) { mutableStateOf(false) }

    // P2-010: Apple HDR captures are high bit depth (16-bit PNG or 10-bit HEVC `heix`).
    // Android's BitmapFactory and ImageDecoder both refuse them, so the original always
    // decodes to a null bitmap while the server-transcoded 8-bit JPEG thumbnails are fine.
    // We degrade to the medium rendition (also a server-side JPEG) so the user sees the
    // picture, and only surface a notice if even that fails.
    //
    // This must be a *remembered* value: SubcomposeAsyncImage may deliver several onError
    // callbacks for one request, and without the flag we would keep re-requesting the
    // undecodable original.
    var degradedToMedium by remember(photo.id) { mutableStateOf(false) }

    // True during the one-frame gap between "ORIGINAL failed to decode" and "MEDIUM request
    // issued". Used to keep showing the spinner instead of flashing a failure panel.
    var isDegrading by remember(photo.id) { mutableStateOf(false) }

    val quality = if (degradedToMedium) {
        ICloudImageModel.Quality.MEDIUM
    } else {
        ICloudImageModel.Quality.ORIGINAL
    }

    val model = remember(photo.id, reloadToken, quality) {
        ICloudImageModel.fromEntity(photo, quality)
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            // Bound the decode to a sane resolution; the drone shots are 12288x8192 and would
            // otherwise need ~400MB for one bitmap.
            .size(2048)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = photo.filename,
        contentScale = ContentScale.Fit,
        onError = {
            val throwable = it.result.throwable
            // P2-011: distinguish "the signed URL is dead" from "the bytes would not decode".
            // Only the former is fixed by re-minting; the latter burn a /records/lookup and
            // still fail.
            val httpCode = ImageLoadFailure.httpCodeOf(throwable)
            isUrlIssue = ImageLoadFailure.isExpiredUrl(throwable)

            when {
                isUrlIssue && retryCount < MAX_IMAGE_RETRY -> {
                    retryCount++
                    Log.i(
                        TAG,
                        "image url expired (HTTP $httpCode), re-minting master=${photo.masterId}, " +
                            "retry=$retryCount"
                    )
                    scope.launch {
                        // force = true: a deliberate single-asset action must not be vetoed by
                        // the grid-thumbnail dedup window (P2-011).
                        val refreshed = repository.refreshUrlsForMaster(photo.masterId, force = true)
                        if (refreshed.isSuccess) {
                            reloadToken++
                        } else {
                            loadFailed = true
                        }
                    }
                }

                // Not a URL problem on the original -> almost certainly the high-bit-depth
                // decode failure above. Drop to the medium rendition and try once more.
                //
                // NOTE: the state flip is deferred to the next frame. Mutating it inline from
                // onError does not rebuild the AsyncImage: the callback runs while Coil is still
                // finishing the failing request, so the new model would be swallowed and the
                // retry never fires (observed as "degrading to MEDIUM" with no MEDIUM load).
                //
                // `isDegrading` covers that gap: it is flipped immediately so the error panel is
                // never painted between the failed original and the medium request. Without it
                // the user saw "该图片为 HDR..." flash for a frame or two before the picture.
                !isUrlIssue && quality == ICloudImageModel.Quality.ORIGINAL -> {
                    Log.w(
                        TAG,
                        "original decode failed master=${photo.masterId} " +
                            "error=${throwable.message}, degrading to MEDIUM"
                    )
                    isDegrading = true
                    scope.launch {
                        withFrameNanos { }
                        degradedToMedium = true
                    }
                }

                else -> {
                    Log.w(
                        TAG,
                        "image decode failed master=${photo.masterId} " +
                            "quality=$quality error=${throwable.message}"
                    )
                    isDegrading = false
                    loadFailed = true
                }
            }
        },
        // While degrading, keep showing the spinner rather than the failure panel.
        loading = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            if (isDegrading) {
                // Transitional frame between ORIGINAL failing and MEDIUM being requested.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                isUrlIssue -> "原图加载失败 (链接已失效)"
                                // A+C: degraded once, still failed -> tell the user why and
                                // point them at the download button.
                                degradedToMedium ->
                                    "该图片为 HDR 高动态范围格式，暂不支持预览\n可点击下方按钮下载原图"
                                else -> "原图无法解码 (该格式暂不支持)"
                            },
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        if (degradedToMedium) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = photo.filename,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Video player with in-place URL re-minting (P2-002) and human readable failure states (P2-007).
 *
 * Changing [currentUrl] rebuilds the player via `remember(currentUrl)`; playback position is
 * carried across the rebuild so the user barely notices the recovery.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(
    photo: PhotoAssetEntity,
    repository: PhotoRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialUrl = remember(photo.id) {
        photo.originalUrl ?: photo.medUrl ?: photo.thumbUrl
    }

    var currentUrl by remember(photo.id) { mutableStateOf(initialUrl) }
    var retryCount by remember(photo.id) { mutableIntStateOf(0) }
    var playerError by remember(photo.id) { mutableStateOf<String?>(null) }
    var lastPosition by remember(photo.id) { mutableLongStateOf(0L) }

    if (currentUrl.isNullOrBlank()) {
        CenterMessage("无法播放：无有效视频播放链接")
        return
    }

    fun handleExpiredUrl(code: Int) {
        if (retryCount >= MAX_VIDEO_RETRY) {
            playerError = "视频链接已过期 (HTTP $code)，多次刷新仍失败，请稍后重试"
            return
        }
        retryCount++
        Log.i(TAG, "video url expired (HTTP $code), refreshing master=${photo.masterId}, retry=$retryCount")
        scope.launch {
            val refreshed = repository.refreshUrlsForMaster(photo.masterId).getOrNull()
            val newUrl = refreshed?.originalUrl ?: refreshed?.medUrl ?: refreshed?.thumbUrl
            if (!newUrl.isNullOrBlank() && newUrl != currentUrl) {
                currentUrl = newUrl
                playerError = null
            } else {
                playerError =
                    "视频链接刷新失败 (HTTP $code)${refreshed?.videoComplUrl?.let { "" } ?: ""}"
            }
        }
    }

    val exoPlayer = remember(currentUrl) {
        val url = currentUrl ?: return@remember null
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            if (lastPosition > 0) seekTo(lastPosition)
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    lastPosition = currentPosition
                    val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

                    when {
                        code == 410 || code == 403 -> handleExpiredUrl(code)
                        code == 404 -> playerError = "视频已从云端删除"
                        code != null -> playerError = "网络错误 (HTTP $code)"
                        else -> playerError = "播放失败：${error.errorCodeName}"
                    }
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    if (playerError != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "在线播放失败",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playerError ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    retryCount = 0
                    playerError = null
                    reloadVideo(
                        photo = photo,
                        repository = repository,
                        scope = scope,
                        onUrl = { currentUrl = it },
                        onFail = { playerError = it }
                    )
                }
            ) {
                Text("重试")
            }
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun reloadVideo(
    photo: PhotoAssetEntity,
    repository: PhotoRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onUrl: (String?) -> Unit,
    onFail: (String) -> Unit
) {
    scope.launch {
        val refreshed = repository.refreshUrlsForMaster(photo.masterId).getOrNull()
        val newUrl = refreshed?.originalUrl ?: refreshed?.medUrl ?: refreshed?.thumbUrl
        if (!newUrl.isNullOrBlank()) {
            onUrl(newUrl)
        } else {
            onFail("视频链接刷新失败，请检查网络后重试")
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = Color.White)
    }
}

@Composable
private fun PhotoInfoContent(photo: PhotoAssetEntity) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "文件详细信息",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        InfoItem(label = "文件名", value = photo.filename)
        InfoItem(label = "拍摄/创建日期", value = formatDate(photo.assetDate))
        InfoItem(label = "媒体类型", value = photo.mediaType)
        if (photo.itemType.isNotBlank()) {
            InfoItem(label = "格式 UTI", value = photo.itemType)
        }
        if (photo.width > 0 && photo.height > 0) {
            InfoItem(label = "分辨率", value = "${photo.width} × ${photo.height}")
        }
        if (photo.size > 0) {
            InfoItem(label = "文件大小", value = Formatter.formatFileSize(context, photo.size))
        }
        if (photo.duration > 0) {
            InfoItem(label = "视频时长", value = "${photo.duration} 秒")
        }
        InfoItem(label = "CloudKit Asset ID", value = photo.id)
        InfoItem(label = "CloudKit Master ID", value = photo.masterId)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "未知时间"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private const val TAG = "PhotoDetailViewer"
private const val MAX_IMAGE_RETRY = 2
private const val MAX_VIDEO_RETRY = 2
