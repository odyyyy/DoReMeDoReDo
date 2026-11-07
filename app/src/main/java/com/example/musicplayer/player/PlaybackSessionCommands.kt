package com.example.musicplayer.player

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Custom MediaSession commands exchanged between [PlayerController] and
 * [PlaybackService].
 *
 * Media3 deliberately keeps the shuffle order private on [androidx.media3.common.Player],
 * yet the Queue screen must display the order in which shuffled playback will
 * actually traverse the playlist. The service owns the ExoPlayer instance, so it
 * answers a read-only custom command with that order; no player state is mutated
 * and the controller never gains access to the raw player.
 */
internal object PlaybackSessionCommands {

    /** Read-only command returning the current Media3 shuffle order. */
    const val ACTION_GET_SHUFFLE_ORDER = "com.example.musicplayer.GET_SHUFFLE_ORDER"

    /** Playlist indices in the order Media3 will play them while shuffling. */
    const val EXTRA_SHUFFLE_ORDER = "com.example.musicplayer.SHUFFLE_ORDER"

    val getShuffleOrder: SessionCommand =
        SessionCommand(ACTION_GET_SHUFFLE_ORDER, Bundle.EMPTY)
}
