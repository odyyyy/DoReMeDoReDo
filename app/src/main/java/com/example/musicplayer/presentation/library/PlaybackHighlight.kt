package com.example.musicplayer.presentation.library

/**
 * The small slice of playback state the track list renders.
 *
 * Deliberately excludes position/duration so the list does not recompose on
 * every playback tick.
 */
data class PlaybackHighlight(
    val trackUri: String,
    val isPlaying: Boolean,
)
