package com.example.musicplayer.presentation.player

import com.example.musicplayer.domain.model.TrackMetadata
import com.example.musicplayer.player.PlaybackRepeatMode
import com.example.musicplayer.player.PlaybackState

/**
 * The slice of playback state that the compact player surfaces render.
 *
 * The playback position is deliberately absent: the panel only changes when the
 * track, the transport controls, or the error state change, so it does not
 * recompose on every playback tick. Screens that need a seek position collect
 * [PlaybackProgress] instead.
 *
 * @property trackUri Stable URI of the loaded item, used to tell whether the
 *   panel is still showing the same track.
 * @property title Display title of the loaded item.
 * @property artist Artist of the loaded item, or `null` when unknown.
 * @property album Album of the loaded item, or `null` when unknown.
 * @property artworkUri Optional artwork reference, or `null` when unavailable.
 * @property isPlaying True while the player is actively playing.
 * @property isBuffering True while the player is buffering the current item.
 * @property hasNext True when a next playlist item exists.
 * @property hasPrevious True when a previous playlist item exists.
 * @property shuffleModeEnabled True while Media3 shuffle mode is on.
 * @property repeatMode The repeat mode currently applied by Media3.
 * @property hasEnded True when playback finished and nothing is playing.
 * @property errorMessage User-facing playback failure, or `null` when healthy.
 */
data class PlayerUiState(
    val trackUri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val shuffleModeEnabled: Boolean,
    val repeatMode: PlaybackRepeatMode,
    val hasEnded: Boolean,
    val errorMessage: String?,
)

/** Shown when a queued item carries no title metadata. */
internal const val UNKNOWN_TRACK_TITLE = "Unknown Track"

/**
 * Projects the shared playback snapshot into [PlayerUiState].
 *
 * Returns `null` when the player holds no item at all, which is what keeps the
 * Mini Player hidden before playback has started.
 */
fun PlaybackState.toPlayerUiState(): PlayerUiState? {
    val track = currentTrack ?: return null
    return PlayerUiState(
        trackUri = track.uri,
        title = track.title.ifBlank { UNKNOWN_TRACK_TITLE },
        artist = track.artist?.takeIf { it.isNotBlank() },
        album = track.album?.takeIf { it.isNotBlank() },
        artworkUri = track.artworkUri?.takeIf { it.isNotBlank() },
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
        shuffleModeEnabled = shuffleModeEnabled,
        repeatMode = repeatMode,
        hasEnded = hasEnded,
        errorMessage = playbackError,
    )
}

/** Artist line shown when the loaded item has no artist metadata. */
internal fun PlayerUiState.artistLabel(): String = artist ?: TrackMetadata.UNKNOWN_ARTIST

/** Album line shown when the loaded item has no album metadata. */
internal fun PlayerUiState.albumLabel(): String = album ?: TrackMetadata.UNKNOWN_ALBUM