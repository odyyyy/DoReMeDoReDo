package com.example.musicplayer.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.TrackSort
import com.example.musicplayer.presentation.components.AlbumCard
import com.example.musicplayer.presentation.components.LoadingStatus
import com.example.musicplayer.presentation.components.MessageStatus

/**
 * Albums tab: the indexed albums as an adaptive artwork grid.
 */
@Composable
fun AlbumsScreen(
    viewModel: LibraryViewModel,
    onAlbumSelected: (String) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val albumsState by viewModel.albumsUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
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
                title = "Albums",
                subtitle = albumsSubtitle(albumsState, ready.isScanning),
                queueSize = queueSize,
                onOpenQueue = onOpenQueue,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Albums are filtered by the same search field as the track list.
            SearchAndSortBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                sort = TrackSort.TITLE,
                onSortChange = viewModel::onSortChange,
                showSort = false,
            )

            Spacer(modifier = Modifier.height(12.dp))

            AlbumsBody(
                albumsState = albumsState,
                isScanning = ready.isScanning,
                onAlbumSelected = { album -> onAlbumSelected(album.id) },
                onRescan = viewModel::rescanCurrentFolder,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlbumsBody(
    albumsState: AlbumsUiState,
    isScanning: Boolean,
    onAlbumSelected: (Album) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (albumsState) {
        is AlbumsUiState.Loading -> LoadingStatus(
            message = "Loading albums…",
            modifier = modifier,
        )

        is AlbumsUiState.Error -> MessageStatus(
            title = "Couldn't load albums",
            message = albumsState.message,
            isError = true,
            modifier = modifier,
        )

        is AlbumsUiState.Ready -> when {
            albumsState.totalAlbums == 0 && isScanning -> LoadingStatus(
                message = "Scanning music folder…",
                modifier = modifier,
            )

            albumsState.totalAlbums == 0 -> MessageStatus(
                title = "No Albums Yet",
                message = "Albums appear here once tracks are indexed from the selected folder.",
                actionLabel = "Rescan Folder",
                onAction = onRescan,
                modifier = modifier,
            )

            albumsState.albums.isEmpty() -> MessageStatus(
                title = "No Matches",
                message = "No albums match the current search.",
                modifier = modifier,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = albumsState.albums,
                    key = { it.id },
                    // One card layout for the whole grid: lets the lazy grid reuse
                    // the same composed nodes while scrolling.
                    contentType = { "album" },
                ) { album ->
                    val onClick = remember(album, onAlbumSelected) { { onAlbumSelected(album) } }
                    AlbumCard(
                        album = album,
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

private fun albumsSubtitle(state: AlbumsUiState, isScanning: Boolean): String = when {
    isScanning -> "Scanning folder…"
    state is AlbumsUiState.Ready && state.totalAlbums > 0 -> if (state.isFiltered) {
        "${state.albums.size} of ${state.totalAlbums} albums"
    } else {
        if (state.totalAlbums == 1) "1 album" else "${state.totalAlbums} albums"
    }

    else -> "Grouped from track metadata"
}