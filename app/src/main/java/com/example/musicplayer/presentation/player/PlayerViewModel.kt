package com.example.musicplayer.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the player surfaces (Mini Player and Now Playing).
 *
 * It never touches ExoPlayer: every command is forwarded to
 * [PlayerController], which talks to the single player owned by
 * `PlaybackService`, and every exposed value is projected back from that
 * player, so the UI cannot drift from the real playback state.
 *
 * Two separate streams are published on purpose:
 * - [uiState] carries track/transport/mode changes only, so list screens and
 *   the Mini Player never recompose on a position tick.
 * - [progress] carries the seek position and is collected by Now Playing alone.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    /** Compact playback state, or `null` while no track is loaded. */
    val uiState: StateFlow<PlayerUiState?> = playerController.state
        .map { it.toPlayerUiState() }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Seek-bar state of the current item, or `null` while nothing is loaded. */
    val progress: StateFlow<PlaybackProgress?> = playerController.state
        .map { it.toPlaybackProgress() }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun onTogglePlayPause() {
        playerController.togglePlayPause()
    }

    fun onNext() {
        playerController.seekToNext()
    }

    fun onPrevious() {
        playerController.seekToPrevious()
    }

    /** Seeks to [positionMs] in the current item. */
    fun onSeekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun onToggleShuffle() {
        playerController.toggleShuffle()
    }

    /** Advances repeat Off -> All -> One -> Off. */
    fun onCycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun dismissPlaybackError() {
        playerController.dismissPlaybackError()
    }
}