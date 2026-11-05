package com.example.musicplayer.player

/**
 * Immutable snapshot of the playback state owned by the playback service.
 *
 * The UI observes this stream instead of holding a player. Position updates are
 * published at a coarse interval, so screens should collect only the fields
 * they actually render (for example the current track for a row highlight)
 * rather than the whole snapshot on every tick.
 *
 * @property isConnected True once the app's [androidx.media3.session.MediaController]
 *   is connected to [PlaybackService].
 * @property currentTrack The item currently loaded by the player, if any.
 * @property isPlaying True while the player is actively playing.
 * @property isBuffering True while the player is buffering.
 * @property durationMs Duration of the current item, or `0` when unknown.
 * @property positionMs Current playback position, or `0` when unknown.
 * @property hasNext True when a next playlist item exists.
 * @property hasPrevious True when a previous playlist item exists.
 * @property playbackError User-facing message of the last playback failure,
 *   cleared when a new queue starts or the user dismisses it.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val currentTrack: PlaybackTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val playbackError: String? = null,
)

/**
 * The track currently exposed by the player, rebuilt from Media3 metadata
 * because the service and the app exchange media items, not domain models.
 */
data class PlaybackTrack(
    val uri: String,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val durationMs: Long,
)
