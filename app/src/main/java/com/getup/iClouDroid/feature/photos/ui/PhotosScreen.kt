package com.getup.iClouDroid.feature.photos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.platform.LocalConfiguration
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
 *  │   ├─ item  row     (8月, 8 photos)
 *  │   ├─ item  row     (8月, 8 photos)
 *  │   ├─ stickyHeader  2026年7月
 *  │   └─ item  row     (7月, 8 photos)
 *  ├─ MonthIndexBar                  ← floating year/month jump
 *  └─ (overlay) empty / error states ← drawn on top, never replacing the list
 * ```
 *
 * ## Why the grid is emitted as rows
 *
 * Each month used to be a single `LazyVerticalGrid` nested in this list. That cost correctness:
 * an inner lazy layout inside an outer one must be given an explicit height, so its children were
 * measured by the inner layout while the outer was flinging. After a fast fling the inner grid came
 * back having laid out one row and collapsed the rest — the reported symptom being "the month
 * titles appear, but the photos never load and only the top month renders".
 *
 * Emitting one row per `columns` photos makes every cell a direct child of the single layout that
 * owns scrolling, so virtualisation and measurement happen in exactly one place.
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
    val monthPhotos by viewModel.monthPhotos.collectAsStateWithLifecycle()

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

    // Subscribe to every month up front.
    //
    // Driven by the month index rather than by what is currently visible: the visible window is
    // rewritten several times per second during a fast fling, so a viewport-driven subscription
    // tears months down a frame after creating them and the screen lands on rows whose query has
    // not emitted yet. Months hold only `PhotoAssetEntity` objects (no bitmaps), so keeping them all
    // subscribed is cheap and makes flinging land on content instead of placeholders.
    LaunchedEffect(monthKeys) {
        if (monthKeys.isNotEmpty()) {
            viewModel.subscribeToAllMonths(monthKeys)
        }
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
                        // One row per `columns` photos, emitted directly into this LazyColumn
                        // rather than nesting a LazyVerticalGrid per month.
                        //
                        // That nesting was the cause of the "titles appear but the photos never
                        // load" bug: an inner lazy grid inside an outer lazy list has to be given
                        // a fixed height, so its children are measured by the *inner* layout while
                        // the outer list is flinging. After a fast fling the inner grid came back
                        // with a single row laid out and the rest collapsed, leaving a month header
                        // followed by one lonely thumbnail. Emitting rows keeps every cell a direct
                        // child of the one layout that owns scrolling, so virtualisation and
                        // measurement happen in exactly one place.
                        items(
                            count = rowsFor(month.itemCount, gridColumns),
                            key = { row -> "r_${month.yearMonth}_$row" }
                        ) { row ->
                            MonthRow(
                                photos = monthPhotos[month.yearMonth].orEmpty(),
                                row = row,
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
                            val idx = listIndexOfMonth(monthIndex, gridColumns, yearMonth)
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx) }
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
 * Number of grid rows a month needs at the current column count.
 *
 * Exposed for the section list so the month can be emitted as individual rows.
 */
private fun rowsFor(itemCount: Int, columns: Int): Int {
    if (itemCount <= 0 || columns <= 0) return 0
    return (itemCount + columns - 1) / columns
}

/**
 * Position of a month's **header** in the `LazyColumn`.
 *
 * Each month occupies `1 + rowsFor(itemCount, columns)` items, so the offset of a month cannot be
 * computed from its ordinal alone. Used by the year/month navigator to scroll to a section.
 */
private fun listIndexOfMonth(
    months: List<com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexEntity>,
    columns: Int,
    yearMonth: Int
): Int {
    var index = 0
    for (month in months) {
        if (month.yearMonth == yearMonth) return index
        index += 1 + rowsFor(month.itemCount, columns)
    }
    return -1
}

/**
 * A single row of up to [columns] thumbnails belonging to one month.
 *
 * [photos] is supplied by the caller from a per-month cache, so scrolling through a month does not
 * re-run the database query once per row.
 */
@Composable
private fun MonthRow(
    photos: List<PhotoAssetEntity>,
    row: Int,
    columns: Int,
    onPhotoClick: (PhotoAssetEntity) -> Unit
) {
    val start = row * columns
    val end = (start + columns).coerceAtMost(photos.size)

    // Reserve the tile height when the month is still loading, so the list geometry is already
    // correct and nothing shifts when the photos arrive.
    val tileSize = LocalConfiguration.current.screenWidthDp.dp / columns

    if (start >= photos.size) {
        Spacer(modifier = Modifier.height(tileSize))
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        for (i in start until end) {
            val photo = photos[i]
            // PhotoGridItem already applies `aspectRatio(1f)`; adding another here would give the
            // cell two competing square constraints and collapse it. `weight` is all it needs to
            // share the row.
            PhotoGridItem(
                photo = photo,
                onClick = { onPhotoClick(photo) },
                modifier = Modifier.weight(1f)
            )
        }
        // Pad a partial row so its tiles keep the same size as a full one.
        repeat(columns - (end - start)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Which month is the user actually looking at, read from the list's own item keys.
 *
 * Earlier versions divided the visible index by a constant, which only worked while every month
 * was exactly two items (header + one grid). Months are now a header plus one row per `columns`
 * photos, so the item count per month varies — the month has to be read from the key on the item
 * itself: `h_202608` for a header, `r_202608_3` for a row.
 *
 * Reading a month *identity* (rather than an offset) is also what keeps the navigator stable: it
 * names the same month regardless of how many rows that month happens to occupy.
 */
private fun monthKeyAt(state: LazyListState, monthKeys: List<Int>): Int? {
    if (monthKeys.isEmpty()) return null

    val layout = state.layoutInfo
    val firstVisible = layout.visibleItemsInfo.firstOrNull()
        ?: return monthKeys.firstOrNull()

    // Prefer the header currently pinned at the top edge.
    val pinnedHeader = layout.visibleItemsInfo.firstOrNull { info ->
        info.key.toString().startsWith("h_") && info.offset <= 0
    }

    val key = (pinnedHeader ?: firstVisible).key.toString()
    val raw = when {
        key.startsWith("h_") -> key.removePrefix("h_")
        key.startsWith("r_") -> key.removePrefix("r_").substringBefore('_')
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
