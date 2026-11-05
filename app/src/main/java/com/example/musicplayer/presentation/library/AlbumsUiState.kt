package com.example.musicplayer.presentation.library

import com.example.musicplayer.domain.model.Album

/** UI state for the Albums view in Library. */
sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
    data class Ready(
        val albums: List<Album>,
        val totalAlbums: Int,
        val isFiltered: Boolean,
    ) : AlbumsUiState
}
