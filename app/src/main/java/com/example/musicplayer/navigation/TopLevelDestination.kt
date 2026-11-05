package com.example.musicplayer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinations reachable from the bottom navigation bar.
 *
 * @property route Navigation route of the tab.
 * @property label Text label shown under the tab icon.
 * @property icon Icon shown for the tab.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TRACKS(Routes.TRACKS, "Tracks", Icons.Rounded.MusicNote),
    ALBUMS(Routes.ALBUMS, "Albums", Icons.Rounded.Album),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
}