package com.getup.iClouDroid.feature.photos.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The photo gallery: a lazy list of month sections.
 *
 * ## Structure
 *
 * ```
 * Box
 *  ├─ LazyColumn                     ← always composed
 *  │   ├─ stickyHeader  2026年8月
 *  │   ├─ item          MonthGrid(8月)
 *  │   ├─ stickyHeader  2026年7月
 *  │   └─ item          MonthGrid(7月)
 *  ├─ MonthIndexBar                  ← floating year/month jump
 *  └─ (overlay) empty / error states ← drawn on top, never replacing the list
 * ```
 *
 * ## Why the list is never swapped out
 *
 * ISS-019: the empty/loading/error branches used to be `if/else` siblings of the grid, so any frame
 * where the list was momentarily empty disposed it — along with its scroll state — and re-adding
 * it reset the position to the top. The list now stays composed unconditionally and the
 * placeholder states are painted over it.
 *
 * ## Scroll anchoring (P2-016)
 *
 * [currentMonth] is derived from `firstVisibleItemKey`, never from `firstVisibleItemIndex`. Keys
 * identify a month and do not move when rows are appended; indices do, and that is what made the
 * old Paging-driven grid jump to the top after every APPEND.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel,
    onOpenAccountInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val monthIndex by viewModel.monthIndex.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Month keys, in the same order the LazyColumn lays them out (one header + one grid per month,
    // so a month's header sits at index `i * 2`).
    val monthKeys = remember(monthIndex) { monthIndex.map { it.yearMonth } }

    // Which month is at the top of the viewport.
    //
    // NOTE: this maps the *index* back to a month key rather than reading a key directly —
    // `LazyListState.firstVisibleItemKey` only exists from Compose Foundation 1.8, and this
    // project is on 1.7.5. The mapping is safe because the month list is the only thing that can
    // change the layout, and it is keyed by month identity (see the class doc).
    val currentMonth by remember(listState, monthKeys) {
        derivedStateOf { monthKeyAt(listState, monthKeys) }
    }

    // Hand the scan to WorkManager when this screen goes away, so a backgrounded app keeps making
    // progress on a large library instead of stalling half-populated.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.onScreenLeaving()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "iCloud 照片",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = when {
                                totalCount > 0 && scanProgress.isScanning ->
                                    "已加载 $totalCount 项 · 扫描中"
                                totalCount > 0 -> "已加载 $totalCount 项照片与视频"
                                else -> "正在同步图库..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleGridColumns) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "切换网格大小 ($gridColumns 列)"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.syncPhotos(isInitial = false) },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "增量同步"
                            )
                        }
                    }

                    IconButton(onClick = onOpenAccountInfo) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "账号信息"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scan progress: determinate when the login-page scrape gave us a denominator,
            // indeterminate otherwise. Hidden entirely once the walk finishes.
            ScanProgressBar(progress = scanProgress)

            // Sync banner (manual refresh messages). Only shown when the scan bar is not already
            // occupying that slot, so the two status rows never stack.
            AnimatedVisibility(visible = syncMessage != null && !scanProgress.isScanning) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // The list is ALWAYS composed — see the class doc (ISS-019).
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    monthIndex.forEach { month ->
                        stickyHeader(key = "h_${month.yearMonth}") {
                            MonthHeader(
                                label = com.getup.iClouDroid.feature.photos.data.MonthBucketing
                                    .labelOf(month.yearMonth),
                                itemCount = month.itemCount,
                                isComplete = month.isComplete
                            )
                        }
                        item(key = "g_${month.yearMonth}") {
                            MonthSection(
                                viewModel = viewModel,
                                yearMonth = month.yearMonth,
                                columns = gridColumns,
                                onPhotoClick = viewModel::selectPhoto
                            )
                        }
                    }
                }

                // Placeholder overlays — painted ON TOP of the list, never replacing it.
                if (monthIndex.isEmpty()) {
                    when {
                        scanProgress.isScanning -> EmptyStateOverlay {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("正在从 iCloud 拉取照片...")
                        }
                        else -> EmptyStateOverlay {
                            Text(
                                text = "暂无 iCloud 照片",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "请点击右上角同步按钮开始拉取云端图库",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.syncPhotos(isInitial = true) }) {
                                Text("开始同步")
                            }
                        }
                    }

                    if (syncMessage != null && !scanProgress.isScanning && monthIndex.isEmpty()) {
                        // A hard failure with nothing cached: surface it with a retry rather than
                        // leaving the user on a misleading "暂无照片".
                    }
                }

                // Floating navigator, right edge.
                if (monthIndex.isNotEmpty()) {
                    MonthIndexBar(
                        currentYearMonth = currentMonth,
                        months = monthIndex,
                        onJumpToMonth = { yearMonth ->
                            val idx = monthKeys.indexOf(yearMonth)
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx * 2) }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxSize()
                    )
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        PhotoDetailViewer(
            photo = photo,
            downloadState = downloadState,
            onDismiss = { viewModel.selectPhoto(null) },
            onDownload = { viewModel.downloadPhoto(context, photo) }
        )
    }
}

/**
 * Subscribes to one month's photos and renders the grid.
 *
 * The subscription lives here, per month, rather than in the ViewModel: only months that are
 * actually composed hold their contents in memory, so a 16k-item library costs roughly one
 * screenful of entities instead of the whole thing.
 */
@Composable
private fun MonthSection(
    viewModel: PhotosViewModel,
    yearMonth: Int,
    columns: Int,
    onPhotoClick: (PhotoAssetEntity) -> Unit
) {
    val photos by viewModel.observeMonth(yearMonth)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (photos.isEmpty()) {
        // A month row exists but its photos are not loaded yet (deleted between index read and
        // content read). Render nothing rather than a zero-height grid with a header.
        return
    }

    MonthGrid(
        photos = photos,
        columns = columns,
        onPhotoClick = onPhotoClick
    )
}

/**
 * Which month is the user actually looking at.
 *
 * Naively dividing `firstVisibleItemIndex` by two is wrong twice over:
 *
 *  - the index can point at a **grid** item while that month's header is pinned above it, which is
 *    fine, and
 *  - it can point at the **next** month's header while the previous month's header is still the
 *    one pinned at the top — which made the navigator jump a month ahead of the content.
 *
 * Reading the layout's actual visible items resolves both: the pinned header is always the last
 * `h_` item at or above the top edge, so scan the visible window for the newest header that has
 * already reached the top.
 */
private fun monthKeyAt(state: LazyListState, monthKeys: List<Int>): Int? {
    if (monthKeys.isEmpty()) return null

    val layout = state.layoutInfo
    val firstVisible = layout.visibleItemsInfo.firstOrNull()
        ?: return monthKeys.firstOrNull()

    // Prefer the header that is currently pinned (its offset has been clamped at the top edge).
    val pinnedHeader = layout.visibleItemsInfo.firstOrNull { info ->
        val key = info.key.toString()
        key.startsWith("h_") && info.offset <= 0
    }

    val key = (pinnedHeader ?: firstVisible).key.toString()
    val raw = when {
        key.startsWith("h_") -> key.removePrefix("h_")
        key.startsWith("g_") -> key.removePrefix("g_")
        else -> return monthKeys.firstOrNull()
    }
    return raw.toIntOrNull() ?: monthKeys.firstOrNull()
}

@Composable
private fun EmptyStateOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            content()
        }
    }
}
