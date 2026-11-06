package com.example.musicplayer.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import com.example.musicplayer.presentation.components.LoadingStatus
import com.example.musicplayer.presentation.components.MessageStatus
import com.example.musicplayer.presentation.components.PlaybackErrorBanner
import com.example.musicplayer.presentation.components.TrackItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
/**
 * Tracks tab: search, sort, and the indexed track list.
 *
 * Selecting a track starts playback with the currently displayed (filtered and
 * sorted) list as the queue, per the queue rules in `PlayerController`.
 */
@Composable
fun TracksScreen(
    viewModel: LibraryViewModel,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracksState by viewModel.tracksUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playbackHighlight by viewModel.playbackHighlight.collectAsStateWithLifecycle()
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()
    val queueSize by viewModel.queueSize.collectAsStateWithLifecycle()

    LibraryGate(
        state = uiState,
        onFolderSelected = viewModel::onFolderSelected,
        onClearFolder = viewModel::onClearFolder,
        onRescan = viewModel::rescanCurrentFolder,
    ) { ready ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            LibraryTopBar(
                title = "Tracks",
                subtitle = tracksSubtitle(tracksState, ready.isScanning),
                queueSize = queueSize,
                onOpenQueue = onOpenQueue,
            )

            if (playbackError != null) {
                Spacer(modifier = Modifier.height(8.dp))

                PlaybackErrorBanner(
                    message = playbackError.orEmpty(),
                    onDismiss = viewModel::dismissPlaybackError,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SearchAndSortBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                sort = (tracksState as? TracksUiState.Ready)?.sort ?: TrackSort.TITLE,
                onSortChange = viewModel::onSortChange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            TracksBody(
                tracksState = tracksState,
                isScanning = ready.isScanning,
                playbackHighlight = playbackHighlight,
                onTrackSelected = viewModel::onTrackSelected,
                onPlayNext = viewModel::onPlayNext,
                onAddToQueue = viewModel::onAddToQueue,
                onRetry = viewModel::retryTracks,
                onRescan = viewModel::rescanCurrentFolder,
                onClearQuery = { viewModel.onSearchQueryChange("") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TracksBody(
    tracksState: TracksUiState,
    isScanning: Boolean,
    playbackHighlight: PlaybackHighlight?,
    onTrackSelected: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (tracksState) {
        is TracksUiState.Loading -> LoadingStatus(
            message = "Loading library…",
            modifier = modifier,
        )

        is TracksUiState.Error -> MessageStatus(
            title = "Couldn't load tracks",
            message = tracksState.message,
            actionLabel = "Retry",
            onAction = onRetry,
            isError = true,
            modifier = modifier,
        )

        is TracksUiState.Ready -> when {
            // Nothing indexed yet: show progress while the first scan runs.
            tracksState.totalTracks == 0 && isScanning -> LoadingStatus(
                message = "Scanning music folder…",
                modifier = modifier,
            )

            tracksState.totalTracks == 0 -> MessageStatus(
                title = "No Tracks Yet",
                message = "No supported audio files were found in this folder.\n" +
                    "Supported formats: MP3, FLAC, WAV, M4A, AAC, OGG, OPUS.",
                actionLabel = "Rescan Folder",
                onAction = onRescan,
                modifier = modifier,
            )

            tracksState.tracks.isEmpty() -> MessageStatus(
                title = "No Matches",
                message = "No tracks match \"${tracksState.query}\".",
                actionLabel = "Clear Search",
                onAction = onClearQuery,
                modifier = modifier,
            )

            else -> TrackList(
                tracksState = tracksState,
                playbackHighlight = playbackHighlight,
                onTrackSelected = onTrackSelected,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TrackList(
    tracksState: TracksUiState.Ready,
    playbackHighlight: PlaybackHighlight?,
    onTrackSelected: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = tracksState.tracks,
            key = { it.uri },
            // One row layout for the whole list: lets the lazy list reuse the
            // same composed nodes while scrolling a large library.
            contentType = { "track" },
        ) { track ->
            val isCurrent = playbackHighlight?.trackUri == track.uri
            val isPlaying = isCurrent && playbackHighlight?.isPlaying == true
            val onClick = remember(track, onTrackSelected) { { onTrackSelected(track) } }
            val onPlayNextAction = remember(track, onPlayNext) { { onPlayNext(track) } }
            val onAddToQueueAction = remember(track, onAddToQueue) { { onAddToQueue(track) } }
            TrackItem(
                track = track,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                onClick = onClick,
                onPlayNext = onPlayNextAction,
                onAddToQueue = onAddToQueueAction,
            )
        }
    }
}

/** Subtitle line under the tab title: library size, active filter, sort order. */
private fun tracksSubtitle(state: TracksUiState, isScanning: Boolean): String = when {
    isScanning -> "Scanning folder…"
    state is TracksUiState.Ready && state.totalTracks > 0 -> buildString {
        append(
            if (state.isFiltered) {
                "${state.tracks.size} of ${state.totalTracks} tracks"
            } else {
                "${state.totalTracks} tracks"
            },
        )
        append(" · ")
        append(state.sort.label)
    }

    else -> "Local files"
}