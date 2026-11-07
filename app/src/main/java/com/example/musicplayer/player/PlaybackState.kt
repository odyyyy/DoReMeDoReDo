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
 * @property shuffleModeEnabled True while Media3 shuffle mode is on.
 * @property repeatMode The active Media3 repeat mode.
 * @property hasEnded True when playback reached the end of the queue (repeat
 *   off) and nothing is playing any more.
 * @property queue The current playlist and the position within it, kept in
 *   sync with the service-owned player.
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
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val hasEnded: Boolean = false,
    val queue: PlaybackQueue = PlaybackQueue(),
    val playbackError: String? = null,
)

/**
 * The track currently exposed by the player, rebuilt from Media3 metadata
 * because the service and the app exchange media items, not domain models.
 * The same type is used as a queue item so the playing track and the queue
 * entries share one identity ([mediaId] / [uri]).
 */
data class PlaybackTrack(
    val uri: String,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val durationMs: Long,
    /** Album grouping key carried from the library, or `null` when unknown. */
    val albumId: String? = null,
    /** Full-resolution cover used by Now Playing, or `null` when unavailable. */
    val fullArtworkUri: String? = null,
)