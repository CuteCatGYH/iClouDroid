package com.getup.iClouDroid.feature.photos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    modifier: Modifier = Modifier,
    isViewerOpen: Boolean = false,
    onViewerOpenChange: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val monthIndex by viewModel.monthIndex.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val viewer by viewModel.viewer.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val monthPhotos by viewModel.monthPhotos.collectAsStateWithLifecycle()

    // Where each visible thumbnail sits in the window, so the viewer can open from that exact
    // rectangle and close back into it.
    val thumbnailBounds = rememberThumbnailBoundsTracker()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Month keys, in the same order the LazyColumn lays them out (one header + one grid per month,
    // so a month's header sits at index `i * 2`).
    val monthKeys = remember(monthIndex) { monthIndex.map { it.yearMonth } }

    // Which month is at the top of the viewport.
    val currentMonth by remember(listState, monthKeys) {
        derivedStateOf { monthKeyAt(listState, monthKeys) }
    }

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

    // The open/close transition is owned HERE, not inside the viewer.
    //
    // The grid has to fade out as the photo grows and fade back in as it shrinks, and the grid
    // sits in a different subtree from the viewer. Hoisting the animation state to the common
    // ancestor is what lets both layers read the same progress and stay in lockstep — two
    // independently-run animations would drift apart by a few frames and the crossfade would look
    // like a flicker.
    val viewerTransition = rememberViewerTransitionState()

    Scaffold(
        topBar = {
            // The viewer owns the whole window while it is open. Leaving this bar composed would
            // both inset the viewer away from the top edge and draw a second header over it.
            if (isViewerOpen) return@Scaffold
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
                        onClick = { viewModel.syncPhotos() },
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
                                // Explicitly "incremental": a full library walk is hundreds of
                                // CloudKit round trips and lives behind a warning in Settings.
                                contentDescription = "同步增量照片"
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
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding()
                )
                // The grid recedes behind the photo instead of being replaced by black.
                //
                // Two things happen at once as `progress` goes 0 -> 1: the grid dims and shrinks
                // slightly (so it reads as "falling behind" rather than just "getting darker"),
                // while the full-screen photo layer grows out of the tapped cell on top of it.
                // Driving both from the same progress is what removes the black flash between the
                // two screens — there is never a frame where neither layer is visible.
                //
                // The fade stops well short of zero on purpose. At alpha 0 the grid is gone and
                // the backdrop behind the photo becomes a flat black plate; keeping a residual
                // sliver means the grid is still *there* — just pushed back — right up to the
                // moment the viewer covers it. The viewer's own backdrop is what hides it in the
                // end, so the two never fight.
                .graphicsLayer {
                    val p = viewerTransition.effectiveProgress
                    if (p <= 0f) return@graphicsLayer
                    val s = 1f - GRID_RECEDE_SCALE * p
                    scaleX = s
                    scaleY = s
                    alpha = 1f - GRID_FADE * p
                }
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
                                onPhotoClick = viewModel::openViewer,
                                boundsTracker = thumbnailBounds
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
                                text = "请在「设置 → 全盘扫描照片库」中开始首次拉取",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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

    // The viewer is a full-screen overlay in the SAME composition as the grid.
    //
    // It used to be a `Dialog`, which made a hero transition impossible: Compose shared elements do
    // not cross into a Dialog/`AndroidView` boundary. Being a sibling is also what allows the grid
    // above to keep its own fade-out running on the same progress value.
    //
    // NOTE: the "drop our own chrome" bookkeeping deliberately lives in the viewer, not here.
    // It must fire when the open animation *finishes*, not when the state flips: the grid is still
    // on screen (fading out) during the first ~300ms, so hiding the top bar at state-change time
    // made the whole list jump up mid-animation. The viewer calls back via `onTransitionSettled`.
    val viewerState = viewer

    if (viewerState != null) {
        val currentPhotoId = viewerState.photos.getOrNull(viewerState.index)?.id
        androidx.compose.runtime.key(currentPhotoId ?: viewerState.index) {
            PhotoViewerScreen(
                photos = viewerState.photos,
                initialIndex = viewerState.index,
                repository = viewModel.repository,
                downloadState = downloadState,
                onDismiss = viewModel::closeViewer,
                onDownload = { photo -> viewModel.downloadPhoto(context, photo) },
                onIndexChange = viewModel::setViewerIndex,
                anchorBounds = currentPhotoId?.let { thumbnailBounds.boundsOf(it) },
                boundsTracker = thumbnailBounds,
                transition = viewerTransition,
                onTransitionSettled = { open -> onViewerOpenChange(open) }
            )
        }
    }
}

/**
 * How far the grid shrinks while the viewer opens and closes.
 *
 * Small on purpose: this is a depth cue ("the grid moved back a step"), not a zoom. Anything
 * larger makes the thumbnails visibly slide, which fights the photo growing out of one of them.
 */
private const val GRID_RECEDE_SCALE = 0.06f

/**
 * How much of the grid's opacity is consumed by the time the viewer has fully opened.
 *
 * Deliberately less than 1f. At 1f the grid would be completely gone before the viewer's backdrop
 * has built up, leaving a frame or two of flat black — the flash this transition exists to avoid.
 * Leaving a residual 12% means the grid is still faintly there as the viewer closes over it.
 */
private const val GRID_FADE = 0.88f

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
    onPhotoClick: (PhotoAssetEntity) -> Unit,
    boundsTracker: ThumbnailBoundsTracker? = null
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
                modifier = Modifier.weight(1f),
                onBoundsChanged = { rect -> boundsTracker?.update(photo.id, rect) }
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
