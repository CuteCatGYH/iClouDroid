package com.getup.iClouDroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.getup.iClouDroid.feature.photos.ui.PhotosScreen
import com.getup.iClouDroid.feature.photos.ui.PhotosViewModel
import com.getup.iClouDroid.ui.navigation.AppDestination

/**
 * Root container for every authenticated section.
 *
 * Authentication lives *above* this shell ([com.getup.iClouDroid.MainActivity]), so a feature
 * composable here can assume a valid session exists. Switching sections is plain Compose state
 * rather than a nav graph — with a handful of top-level tabs that is simpler and keeps each
 * feature's back stack out of the shell's concerns. Swap in Navigation Compose if nested
 * per-feature routes become necessary.
 */
@Composable
fun AppShell(
    photosViewModel: PhotosViewModel,
    onOpenAccountInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf(AppDestination.Photos) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
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
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selected) {
                    AppDestination.Photos -> PhotosScreen(
                        viewModel = photosViewModel,
                        onOpenAccountInfo = onOpenAccountInfo
                    )
                    AppDestination.Drive -> PlaceholderScreen(AppDestination.Drive)
                    AppDestination.Notes -> PlaceholderScreen(AppDestination.Notes)
                }
            }
        }
    }
}
