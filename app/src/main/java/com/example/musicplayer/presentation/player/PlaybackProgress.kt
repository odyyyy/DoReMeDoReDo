package com.example.musicplayer.presentation.player

import com.example.musicplayer.player.PlaybackState
import kotlin.math.roundToLong

/**
 * The seek-bar slice of playback state.
 *
 * Kept apart from [PlayerUiState] so only a screen that actually renders a seek
 * bar (Now Playing) recomposes twice a second; the Mini Player and the library
 * lists never collect it.
 *
 * @property trackUri URI of the item the position belongs to, so a stale
 *   position is never rendered against a new track.
 * @property positionMs Current playback position, or `0` when unknown.
 * @property durationMs Duration of the current item, or `0` when unknown.
 * @property isPlaying True while the player is actively playing.
 */
data class PlaybackProgress(
    val trackUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
) {
    /** True when a seek bar makes sense, i.e. the duration is known. */
    val isSeekable: Boolean get() = durationMs > 0L

    /** Played fraction of the track in `0f..1f`, or `0f` when unknown. */
    val fraction: Float get() = seekPositionFraction(positionMs, durationMs)

    /** Playback position for a seek-bar [fraction] in `0f..1f`. */
    fun positionFor(fraction: Float): Long = positionForFraction(fraction, durationMs)
}

/** Projects the shared playback snapshot into a seek-bar state. */
fun PlaybackState.toPlaybackProgress(): PlaybackProgress? {
    val track = currentTrack ?: return null
    return PlaybackProgress(
        trackUri = track.uri,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
    )
}

/**
 * Converts a position into a `0f..1f` seek-bar fraction.
 *
 * A track with an unknown duration yields `0f` instead of dividing by zero, and
 * positions outside the track are clamped so a late position update cannot push
 * the thumb past either end of the bar.
 */
internal fun seekPositionFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/** Converts a seek-bar fraction back into a position in milliseconds. */
internal fun positionForFraction(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    return (fraction.coerceIn(0f, 1f) * durationMs).roundToLong().coerceIn(0L, durationMs)
}