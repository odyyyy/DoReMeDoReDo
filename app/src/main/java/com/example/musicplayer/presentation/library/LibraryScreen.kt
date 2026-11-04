package com.example.musicplayer.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.data.database.TrackEntity
import com.example.musicplayer.data.scanner.ScanPersistence
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.presentation.folders.FolderSelectionContent
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Main Library screen handling music-folder selection, traversal scanning, and audio-candidate list presentation.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexedTracks by viewModel.indexedTracks.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is LibraryUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is LibraryUiState.NoFolderSelected -> {
                FolderSelectionContent(
                    onSelectFolderClicked = { folderPickerLauncher.launch(null) },
                )
            }

            is LibraryUiState.FolderAccessRevoked -> {
                FolderSelectionContent(
                    isAccessRevoked = true,
                    folderDisplayName = state.displayName,
                    onSelectFolderClicked = { folderPickerLauncher.launch(null) },
                    onClearFolderClicked = { viewModel.onClearFolder() },
                )
            }

            is LibraryUiState.Ready -> {
                val failure = state.failure
                if (failure != null) {
                    ScanFailedContent(
                        folderName = state.folder.displayName,
                        errorMessage = failure.message,
                        onRetryClicked = { viewModel.rescanCurrentFolder() },
                        onChangeFolderClicked = { folderPickerLauncher.launch(null) },
                    )
                    return@Box
                }

                val scanResult = state.scanResult
                val trackedIndex = indexedTracks
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    FolderHeaderCard(
                        folderName = state.folder.displayName,
                        isScanning = state.isScanning,
                        discoveredCount = scanResult?.audioFiles?.size,
                        totalFilesScanned = scanResult?.totalFilesScanned,
                        durationMs = scanResult?.durationMs,
                        persistence = state.persistence,
                        indexedCount = trackedIndex.size,
                        onRescanClicked = { viewModel.rescanCurrentFolder() },
                        onChangeFolderClicked = { folderPickerLauncher.launch(null) },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        // A completed scan from this session: show what it found.
                        scanResult != null && scanResult.audioFiles.isEmpty() -> {
                            EmptyScanResultContent(
                                onRescanClicked = { viewModel.rescanCurrentFolder() },
                            )
                        }

                        scanResult != null -> {
                            DiscoveredAudioList(
                                audioFiles = scanResult.audioFiles,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Database-first startup (or a running rescan): the
                        // library comes straight from Room, so it stays visible
                        // and updates automatically after scan writes.
                        trackedIndex.isNotEmpty() -> {
                            IndexedTrackList(
                                tracks = trackedIndex,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // First-ever scan of this folder is still running.
                        state.isScanning -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Scanning music folder...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Indexed marker exists but nothing was ever stored.
                        else -> {
                            IndexedTrackList(
                                tracks = emptyList(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeaderCard(
    folderName: String,
    isScanning: Boolean,
    discoveredCount: Int?,
    totalFilesScanned: Int?,
    durationMs: Long?,
    persistence: ScanPersistence?,
    indexedCount: Int,
    onRescanClicked: () -> Unit,
    onChangeFolderClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isScanning) {
                            "Scanning folder..."
                        } else if (discoveredCount != null && totalFilesScanned != null && durationMs != null) {
                            "Found $discoveredCount audio tracks ($totalFilesScanned files scanned in ${durationMs}ms)"
                        } else if (indexedCount > 0) {
                            "Library loaded from Room - $indexedCount tracks indexed"
                        } else {
                            "Music folder ready"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (persistence != null && !isScanning) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Library updated: ${persistence.added} new, " +
                                "${persistence.updated} updated, " +
                                "${persistence.unchanged} unchanged, " +
                                "${persistence.removed} removed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onRescanClicked,
                    enabled = !isScanning,
                ) {
                    Text("Rescan")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onChangeFolderClicked,
                    enabled = !isScanning,
                ) {
                    Text("Change Folder")
                }
            }
        }
    }
}

@Composable
private fun EmptyScanResultContent(
    onRescanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No Supported Audio Files Found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No candidate audio files were found in this folder.\nSupported formats: MP3, FLAC, WAV, M4A, AAC, OGG, OPUS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onRescanClicked) {
            Text("Rescan Folder")
        }
    }
}

@Composable
private fun ScanFailedContent(
    folderName: String,
    errorMessage: String,
    onRetryClicked: () -> Unit,
    onChangeFolderClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Scan Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Could not scan '$folderName': $errorMessage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetryClicked) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onChangeFolderClicked) {
                        Text("Change Folder")
                    }
                }
            }
        }
    }
}

/**
 * Room-backed view of the indexed library. Rendered whenever there is no
 * scan result from the current session, so opening the app shows the persisted
 * library without scanning the filesystem.
 */
@Composable
private fun IndexedTrackList(
    tracks: List<TrackEntity>,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No indexed tracks yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Press Rescan to index the selected folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = tracks,
            key = { it.uri },
        ) { track ->
            IndexedTrackItem(track = track)
        }
    }
}

@Composable
private fun IndexedTrackItem(
    track: TrackEntity,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1_000L
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun DiscoveredAudioList(
    audioFiles: List<DiscoveredAudioFile>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = audioFiles,
            key = { it.uri.toString() },
        ) { audioFile ->
            DiscoveredAudioItem(audioFile = audioFile)
        }
    }
}

@Composable
private fun DiscoveredAudioItem(
    audioFile: DiscoveredAudioFile,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = audioFile.name.substringAfterLast('.', "AUDIO").uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formatFileSize(audioFile.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.US,
        "%.1f %s",
        sizeBytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups.coerceIn(0, units.size - 1)]
    )
}
