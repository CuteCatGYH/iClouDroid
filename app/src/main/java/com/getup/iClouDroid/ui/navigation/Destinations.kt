package com.getup.iClouDroid.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level sections of the app.
 *
 * Adding an iCloud feature means adding an entry here plus a branch in
 * [com.getup.iClouDroid.ui.AppShell] — no changes to authentication or to the other features.
 */
enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    Photos("照片", Icons.Default.Photo),
    Drive("Drive", Icons.Default.Folder),
    Notes("备忘录", Icons.AutoMirrored.Filled.Note)
}
