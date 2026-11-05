package com.example.musicplayer.presentation.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.presentation.components.MiniPlayer

/**
 * Stateful entry point for the persistent Mini Player, placed in the app-level
 * Scaffold so every main screen shares one panel.
 *
 * Nothing is rendered while the service player holds no item, which is what
 * keeps the panel absent before playback has started. The panel always reflects
 * the shared playback stream, so track changes and play/pause changes coming
 * from the service (notification, headset, track transition) update in place
 * without recreating the screen.
 */
@Composable
fun MiniPlayerHost(
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState = state ?: return

    MiniPlayer(
        state = miniPlayerState,
        onOpenNowPlaying = onOpenNowPlaying,
        onTogglePlayPause = viewModel::onTogglePlayPause,
        onNext = viewModel::onNext,
        modifier = modifier,
    )
}