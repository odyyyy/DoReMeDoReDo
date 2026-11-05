package com.example.musicplayer.presentation.album

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.usecase.GetAlbumDetailUseCase
import com.example.musicplayer.presentation.library.PlaybackHighlight
import com.example.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel managing the Album Details screen.
 *
 * Retrieves [albumId] from [SavedStateHandle] (URI decoded) and observes
 * [GetAlbumDetailUseCase] to expose [AlbumUiState]. Selecting any track starts
 * playback using the album's track list as the queue.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val playerController: PlayerController,
) : ViewModel() {

    private val rawAlbumId: String = checkNotNull(savedStateHandle["albumId"])
    private val albumId: String = Uri.decode(rawAlbumId)

    val uiState: StateFlow<AlbumUiState> = getAlbumDetailUseCase(albumId)
        .map { detail ->
            if (detail == null) {
                AlbumUiState.NotFound
            } else {
                AlbumUiState.Ready(
                    album = detail.album,
                    tracks = detail.tracks,
                    totalDurationMs = detail.tracks.sumOf { it.durationMs },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumUiState.Loading,
        )

    val playbackHighlight: StateFlow<PlaybackHighlight?> = playerController.state
        .map { playback ->
            playback.currentTrack?.let { track ->
                PlaybackHighlight(trackUri = track.uri, isPlaying = playback.isPlaying)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** User-facing playback failure message, if any. */
    val playbackError: StateFlow<String?> = playerController.state
        .map { it.playbackError }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun onTrackSelected(track: Track) {
        val ready = uiState.value as? AlbumUiState.Ready ?: return
        val index = ready.tracks.indexOfFirst { it.uri == track.uri }
        if (index < 0) return
        playerController.setQueue(ready.tracks, index)
    }

    /** Plays the entire album starting from the first track. */
    fun playAlbum() {
        val ready = uiState.value as? AlbumUiState.Ready ?: return
        if (ready.tracks.isNotEmpty()) {
            playerController.setQueue(ready.tracks, startIndex = 0)
        }
    }

    /** Appends [track] to the end of the playback queue. */
    fun onAddToQueue(track: Track) {
        playerController.addToQueue(track)
    }

    /** Inserts [track] immediately after the current item. */
    fun onPlayNext(track: Track) {
        playerController.playNext(track)
    }

    fun dismissPlaybackError() {
        playerController.dismissPlaybackError()
    }
}
