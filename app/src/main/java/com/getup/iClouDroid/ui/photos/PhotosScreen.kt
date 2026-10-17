package com.getup.iClouDroid.ui.photos

import android.util.Log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel,
    onOpenAccountInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lazyPagingItems = viewModel.photosPagingData.collectAsLazyPagingItems()
    val totalCount by viewModel.totalCount.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val selectedPhoto by viewModel.selectedPhoto.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    // Hoisted scroll state (P2-003): survives recomposition, configuration changes and process
    // death, so a Paging refresh no longer throws the user back to the top of the grid.
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var pendingRestoreIndex by remember { mutableIntStateOf(-1) }

    // P2-016 TEMP DIAGNOSTIC: record exactly what the scroll state does around each APPEND so we
    // stop guessing at the jump-to-top mechanism. Remove once the root cause is confirmed.
    LaunchedEffect(gridState) {
        snapshotFlow {
            Triple(
                gridState.firstVisibleItemIndex,
                lazyPagingItems.itemCount,
                lazyPagingItems.loadState.append !is LoadState.NotLoading
            )
        }.collect { snapshot ->
            val index = snapshot.first
            val count = snapshot.second
            val appending = snapshot.third
            Log.d("GridDiag", "index=$index itemCount=$count appending=$appending")
        }
    }

    LaunchedEffect(gridColumns) {
        if (pendingRestoreIndex >= 0) {
            gridState.scrollToItem(pendingRestoreIndex)
            pendingRestoreIndex = -1
        }
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
                            text = if (totalCount > 0) "共 $totalCount 项照片与视频" else "正在同步图库...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    // Grid Columns Zoom Toggle (3 -> 4 -> 5 -> 3)
                    IconButton(
                        onClick = {
                            pendingRestoreIndex = gridState.firstVisibleItemIndex
                            viewModel.toggleGridColumns()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "切换网格大小 ($gridColumns 列)"
                        )
                    }

                    // Sync / Refresh
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

                    // Account Info
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
            // Sync status banner
            AnimatedVisibility(visible = syncMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = syncMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            val refreshState = lazyPagingItems.loadState.refresh
            val appendState = lazyPagingItems.loadState.append

            // P2-012: the grid is ALWAYS composed, never swapped out of the tree.
            // Previously the empty/loading/error branches wrapped the LazyVerticalGrid in an
            // if/else chain, so any frame where `itemCount == 0` during a refresh disposed the
            // grid (and its LazyGridState), and re-adding it reset firstVisibleItemIndex to 0 —
            // which is exactly the "scrolled to the bottom, then jumped to the top" symptom.
            // The empty states are now an *overlay* on top of a permanently-present grid.
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(1.dp)
                    ) {
                        items(
                            count = lazyPagingItems.itemCount,
                            key = lazyPagingItems.itemKey { it.id }
                        ) { index ->
                            val photo = lazyPagingItems[index]
                            if (photo != null) {
                                PhotoGridItem(
                                    photo = photo,
                                    onClick = { viewModel.selectPhoto(photo) }
                                )
                            }
                        }
                        // NOTE: no `item {}` for the append indicator here. Adding a grid item
                        // makes itemCount fluctuate by ±1 on every LoadState transition, which
                        // is the other half of the jump-to-top bug. The footer lives outside.
                    }

                    // Empty / loading / error overlays — drawn on top, never replacing the grid.
                    if (lazyPagingItems.itemCount == 0) {
                        when {
                            refreshState is LoadState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("正在从 iCloud 拉取首屏照片...")
                                    }
                                }
                            }
                            refreshState is LoadState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "照片拉取失败",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = refreshState.error.message ?: "未知错误",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { lazyPagingItems.retry() }) {
                                            Text("重试")
                                        }
                                    }
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(32.dp)
                                    ) {
                                        Text(
                                            text = "暂无 iCloud 照片",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "请点击右上角同步按钮开始拉取云端图库",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { viewModel.syncPhotos(isInitial = true) }) {
                                            Text("开始同步")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // P2-012: append indicator lives OUTSIDE the grid so itemCount stays constant.
                when (appendState) {
                    is LoadState.Loading -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    is LoadState.Error -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = appendState.error.message ?: "加载更多失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(onClick = { lazyPagingItems.retry() }) {
                                Text("重试")
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    // Detail Preview Dialog
    selectedPhoto?.let { photo ->
        PhotoDetailViewer(
            photo = photo,
            downloadState = downloadState,
            onDismiss = { viewModel.selectPhoto(null) },
            onDownload = { viewModel.downloadPhoto(context, photo) }
        )
    }
}
