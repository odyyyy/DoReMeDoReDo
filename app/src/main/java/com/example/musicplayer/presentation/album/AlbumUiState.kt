package com.example.musicplayer.presentation.album

import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Track

/** UI state for the Album Detail screen. */
sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data object NotFound : AlbumUiState
    data class Ready(
        val album: Album,
        val tracks: List<Track>,
        val totalDurationMs: Long,
    ) : AlbumUiState
}
