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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.data.scanner.ScanPersistence
import com.example.musicplayer.domain.model.TrackSort
import com.example.musicplayer.presentation.components.TrackItem
import com.example.musicplayer.presentation.folders.FolderSelectionContent

/**
 * Main Library screen: folder selection/scan management plus the Tracks view
 * (searchable, sortable lazy list of the indexed library).
 *
 * The folder/scan lifecycle drives [LibraryUiState]; the track list itself is
 * rendered from [TracksUiState], which is Room-backed and independent of
 * filesystem scanning.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracksState by viewModel.tracksUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    FolderHeaderCard(
                        folderName = state.folder.displayName,
                        isScanning = state.isScanning,
                        discoveredCount = state.scanResult?.audioFiles?.size,
                        totalFilesScanned = state.scanResult?.totalFilesScanned,
                        durationMs = state.scanResult?.durationMs,
                        persistence = state.persistence,
                        indexedCount = (tracksState as? TracksUiState.Ready)?.totalTracks ?: 0,
                        onRescanClicked = { viewModel.rescanCurrentFolder() },
                        onChangeFolderClicked = { folderPickerLauncher.launch(null) },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TracksSection(
                        tracksState = tracksState,
                        searchQuery = searchQuery,
                        isScanning = state.isScanning,
                        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                        onSortChange = { viewModel.onSortChange(it) },
                        onRetry = { viewModel.retryTracks() },
                        onRescan = { viewModel.rescanCurrentFolder() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Renders the current [TracksUiState]: loading, error, empty-library, no
 * search results, or the lazy track list with its search/sort controls.
 */
@Composable
private fun TracksSection(
    tracksState: TracksUiState,
    searchQuery: String,
    isScanning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSortChange: (TrackSort) -> Unit,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (tracksState) {
        is TracksUiState.Loading -> {
            CenteredStatus(
                message = "Loading library...",
                modifier = modifier,
            )
        }

        is TracksUiState.Error -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Couldn't Load Tracks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tracksState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }

        is TracksUiState.Ready -> {
            when {
                // Nothing indexed yet: guide the user (or show progress while
                // the first scan is still running).
                tracksState.totalTracks == 0 && isScanning -> {
                    CenteredStatus(
                        message = "Scanning music folder...",
                        modifier = modifier,
                    )
                }

                tracksState.totalTracks == 0 -> {
                    NoTracksFoundContent(
                        onRescanClicked = onRescan,
                        modifier = modifier,
                    )
                }

                else -> {
                    Column(modifier = modifier.fillMaxSize()) {
                        TrackSearchBar(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            sort = tracksState.sort,
                            onSortChange = onSortChange,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (tracksState.isFiltered) {
                                "${tracksState.tracks.size} of ${tracksState.totalTracks} tracks"
                            } else {
                                "${tracksState.totalTracks} tracks"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (tracksState.tracks.isEmpty()) {
                            NoSearchResultsContent(
                                query = searchQuery,
                                onClearQuery = { onSearchQueryChange("") },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(
                                    items = tracksState.tracks,
                                    key = { it.uri },
                                ) { track ->
                                    TrackItem(track = track)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Search field + sort menu shown above the track list. */
@Composable
private fun TrackSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: TrackSort,
    onSortChange: (TrackSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            placeholder = {
                Text("Search title, artist, album")
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )

        SortMenu(sort = sort, onSortChange = onSortChange)
    }
}

/** Sort-order dropdown; the active option is highlighted. */
@Composable
private fun SortMenu(
    sort: TrackSort,
    onSortChange: (TrackSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                text = "Sort: ${sort.label}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TrackSort.entries.forEach { option ->
                val isSelected = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Centered progress indicator with a caption. */
@Composable
private fun CenteredStatus(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Empty state: the selected folder yields no supported audio files. */
@Composable
private fun NoTracksFoundContent(
    onRescanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No Tracks Found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No supported audio files were found in this folder.\n" +
                "Supported formats: MP3, FLAC, WAV, M4A, AAC, OGG, OPUS.",
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

/** Empty state: the search query matches nothing in the library. */
@Composable
private fun NoSearchResultsContent(
    query: String,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No Matches",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No tracks match \"$query\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onClearQuery) {
            Text("Clear Search")
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