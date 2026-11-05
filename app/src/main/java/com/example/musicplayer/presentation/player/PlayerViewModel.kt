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
 * ViewModel for the compact player surfaces (Mini Player and the Now Playing
 * shell).
 *
 * It never touches ExoPlayer: every command is forwarded to
 * [PlayerController], which talks to the single player owned by
 * `PlaybackService`. The exposed state is derived from the shared playback
 * stream with [distinctUntilChanged], so the 500 ms position ticks of the
 * controller do not trigger recomposition here.
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

    fun onTogglePlayPause() {
        playerController.togglePlayPause()
    }

    fun onNext() {
        playerController.seekToNext()
    }

    fun dismissPlaybackError() {
        playerController.dismissPlaybackError()
    }
}