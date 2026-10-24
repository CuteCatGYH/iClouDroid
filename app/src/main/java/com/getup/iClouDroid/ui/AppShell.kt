package com.getup.iClouDroid.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getup.iClouDroid.IClouDroidApp
import com.getup.iClouDroid.feature.photos.ui.PhotosScreen
import com.getup.iClouDroid.feature.photos.ui.PhotosViewModel
import com.getup.iClouDroid.feature.photos.work.PeriodicScanScheduler
import com.getup.iClouDroid.ui.navigation.AppDestination
import com.getup.iClouDroid.ui.settings.SettingsContainer

/**
 * Root container for every authenticated section.
 *
 * Authentication lives *above* this shell ([com.getup.iClouDroid.MainActivity]), so a feature
 * composable here can assume a valid session exists. Switching sections is plain Compose state
 * rather than a nav graph — with a handful of top-level tabs that is simpler and keeps each
 * feature's back stack out of the shell's concerns. Swap in Navigation Compose if nested
 * per-feature routes become necessary.
 *
 * Settings is a *full-screen overlay* rather than a fourth tab: it is not a place you browse to and
 * from, and giving it a permanent slot would dilute the three real sections while the set of
 * settings is still small.
 */
@Composable
fun AppShell(
    photosViewModel: PhotosViewModel,
    onOpenAccountInfo: () -> Unit,
    modifier: Modifier = Modifier,
    openSettings: Boolean = false,
    onOpenSettingsHandled: () -> Unit = {}
) {
    var selected by rememberSaveable { mutableStateOf(AppDestination.Photos) }

    // True while the photo viewer is full-screen. The bottom navigation is removed then:
    // the viewer is only "full screen" if nothing else is drawing below it.
    //
    // NOTE: this is deliberately NOT hoisted into a shared `Scaffold`'s bottomBar. It used to be,
    // and it broke in a way that was hard to see: toggling the `bottomBar` composable changes the
    // child's `Scaffold` slot, and Compose's movable-content handling resets the state of anything
    // that sits in a changed slot. The `viewerOpen` flag went stale as a result and the navigation
    // bar stayed on screen — squarely on top of the viewer's 信息 / 下载 row.
    //
    // Rendering the navigation bar as an ordinary sibling that is simply not composed avoids that
    // entirely, and has the side benefit that the viewer never shifts when the bar comes back.
    var viewerOpen by rememberSaveable { mutableStateOf(false) }

    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // The entry point (账号信息 sheet) lives in MainActivity, above this shell, so an open request
    // arrives as a one-shot flag that is consumed here.
    androidx.compose.runtime.LaunchedEffect(openSettings) {
        if (openSettings) {
            settingsOpen = true
            onOpenSettingsHandled()
        }
    }

    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as IClouDroidApp).container }
    val settings = container.appSettings

    // Settings live here, not inside `SettingsScreen`, because they outlive it: scheduling must be
    // applied even if the user navigates straight back out.
    val frequency by settings.scanFrequency.collectAsStateWithLifecycle()
    val syncMessage by photosViewModel.syncMessage.collectAsStateWithLifecycle()
    val isSyncing by photosViewModel.isSyncing.collectAsStateWithLifecycle()
    val scanProgress by photosViewModel.scanProgress.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        // Height the navigation bar occupies. Reserved manually so content is never laid out
        // underneath it, and so that laying the bar over `Drive` / `备忘录` needs no extra padding.
        // `NavigationBar` is M3's 80dp; keeping the number here (rather than measuring) means the
        // padding does not change when the bar is removed, so the grid never shifts on open/close.
        val navBarHeight = 80.dp
        val navBarPadding = if (viewerOpen || settingsOpen) 0.dp else navBarHeight

        Box(modifier = Modifier.fillMaxSize()) {
            when (selected) {
                AppDestination.Photos -> PhotosScreen(
                    viewModel = photosViewModel,
                    onOpenAccountInfo = onOpenAccountInfo,
                    isViewerOpen = viewerOpen,
                    onViewerOpenChange = { viewerOpen = it },
                    contentPadding = PaddingValues(bottom = navBarPadding)
                )
                AppDestination.Drive -> PlaceholderScreen(AppDestination.Drive)
                AppDestination.Notes -> PlaceholderScreen(AppDestination.Notes)
            }
        }

        if (settingsOpen) {
            SettingsContainer(
                onClose = { settingsOpen = false },
                onStartFullScan = { photosViewModel.startFullScan() },
                settings = settings,
                fullScanMessage = syncMessage,
                isFullScanRunning = isSyncing || scanProgress.isScanning,
                onFrequencyChanged = { newFrequency ->
                    settings.setScanFrequency(newFrequency)
                    PeriodicScanScheduler.apply(context, settings)
                },
                onCustomIntervalChanged = { days ->
                    settings.setCustomIntervalDays(days)
                    // Only re-schedule when the custom value is actually in use; otherwise this
                    // would silently replace an active daily/weekly schedule with a custom one.
                    if (frequency == com.getup.iClouDroid.data.settings.ScanFrequency.CUSTOM) {
                        PeriodicScanScheduler.apply(context, settings)
                    }
                }
            )
        }

        if (!viewerOpen && !settingsOpen) {
            NavigationBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    }

    /**
     * Keeps WorkManager in step with the stored preference.
     *
     * Runs on first composition so a schedule set in a previous session is re-asserted even if the
     * app was reinstalled or WorkManager dropped the job; the `UPDATE` policy makes this idempotent.
     */
    androidx.compose.runtime.LaunchedEffect(Unit) {
        PeriodicScanScheduler.apply(context, settings)
    }
}
