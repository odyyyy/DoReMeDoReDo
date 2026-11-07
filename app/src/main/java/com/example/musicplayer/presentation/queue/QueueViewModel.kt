package com.example.musicplayer.presentation.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.player.PlaybackQueue
import com.example.musicplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Queue screen.
 *
 * The queue is read directly from [PlayerController.state] and never copied, so
 * reorders, removals and item selection are written straight back to Media3 and
 * any change that originates in the service is reflected automatically. All
 * screens observing playback therefore stay in sync.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    val queue: StateFlow<PlaybackQueue> = playerController.state
        .map { it.queue }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaybackQueue(),
        )

    /** True while the player is actively playing the current queue item. */
    val isPlaying: StateFlow<Boolean> = playerController.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * True while Media3 shuffle mode is on. The queue is then displayed in
     * shuffled traversal order, where drag reordering has no unambiguous
     * target, so the screen disables reordering in that state.
     */
    val shuffleModeEnabled: StateFlow<Boolean> = playerController.state
        .map { it.shuffleModeEnabled }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** Jumps playback to the queue item at [index]. */
    fun onSelect(index: Int) {
        playerController.seekToQueueItem(index)
    }

    /** Moves the queue item at [fromIndex] to [toIndex]. */
    fun onMove(fromIndex: Int, toIndex: Int) {
        playerController.moveQueueItem(fromIndex, toIndex)
    }

    /** Removes the queue item at [index]. */
    fun onRemove(index: Int) {
        playerController.removeQueueItem(index)
    }
}