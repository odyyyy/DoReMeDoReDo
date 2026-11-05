package com.example.musicplayer.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.presentation.components.LoadingStatus
import com.example.musicplayer.presentation.folders.FolderSelectionContent
import com.example.musicplayer.presentation.library.LibraryUiState
import com.example.musicplayer.presentation.library.LibraryViewModel
import com.example.musicplayer.presentation.library.TracksUiState
import com.example.musicplayer.presentation.library.rememberFolderPicker

/**
 * Settings tab: the music folder (select, change, rescan) and the state of the
 * indexed library.
 *
 * Folder management lives here instead of on the browsing tabs so the Tracks and
 * Albums screens stay about music, and every folder action is in one predictable
 * place.
 */
@Composable
fun SettingsScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracksState by viewModel.tracksUiState.collectAsStateWithLifecycle()
    val selectFolder = rememberFolderPicker(viewModel::onFolderSelected)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Music folder and library",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (val state = uiState) {
                is LibraryUiState.Loading -> LoadingStatus(
                    message = "Preparing your library…",
                    modifier = Modifier.padding(top = 32.dp),
                )

                is LibraryUiState.NoFolderSelected -> FolderSelectionContent(
                    onSelectFolderClicked = selectFolder,
                )

                is LibraryUiState.FolderAccessRevoked -> FolderSelectionContent(
                    isAccessRevoked = true,
                    folderDisplayName = state.displayName,
                    onSelectFolderClicked = selectFolder,
                    onClearFolderClicked = viewModel::onClearFolder,
                )

                is LibraryUiState.Ready -> Column(modifier = Modifier.fillMaxSize()) {
                    FolderCard(
                        state = state,
                        onChangeFolderClicked = selectFolder,
                        onRescanClicked = viewModel::rescanCurrentFolder,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LibraryCard(
                        state = state,
                        indexedTracks = (tracksState as? TracksUiState.Ready)?.totalTracks ?: 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    state: LibraryUiState.Ready,
    onChangeFolderClicked: () -> Unit,
    onRescanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    SettingsCard(
        icon = Icons.Rounded.Folder,
        title = "Music folder",
        modifier = modifier,
    ) {
        Text(
            text = state.folder.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = folderStatusLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        if (state.isScanning) {
            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.surfaceContainerHigh,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRescanClicked,
                enabled = !state.isScanning,
            ) {
                Text("Rescan")
            }

            OutlinedButton(
                onClick = onChangeFolderClicked,
                enabled = !state.isScanning,
            ) {
                Text("Change folder")
            }
        }
    }
}

@Composable
private fun LibraryCard(
    state: LibraryUiState.Ready,
    indexedTracks: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val result = state.scanResult
    val persistence = state.persistence

    SettingsCard(
        icon = Icons.Rounded.LibraryMusic,
        title = "Library",
        modifier = modifier,
    ) {
        StatLine(label = "Indexed tracks", value = indexedTracks.toString())

        result?.let { scan ->
            StatLine(label = "Audio files found", value = scan.audioFiles.size.toString())
            StatLine(label = "Files scanned", value = scan.totalFilesScanned.toString())
        }

        persistence?.let { change ->
            StatLine(
                label = "Last update",
                value = "${change.added} new · ${change.updated} updated · ${change.removed} removed",
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The library is read from disk only when you rescan; nothing is " +
                "sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun folderStatusLine(state: LibraryUiState.Ready): String {
    val failure = state.failure
    return when {
        state.isScanning -> "Scanning folder…"
        failure != null -> "Last scan failed: ${failure.message}"
        state.scanResult != null -> "Folder indexed and up to date"
        else -> "Folder ready"
    }
}