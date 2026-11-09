package com.example.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.domain.model.Track
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * App-side connection to the service-owned player.
 *
 * This is the only place the UI layer talks to playback: screens and
 * ViewModels call the command methods and observe [state], while the single
 * ExoPlayer instance lives in [PlaybackService]. No screen or ViewModel creates
 * a player of its own.
 *
 * Commands issued before the [MediaController] finishes connecting are queued
 * and replayed once the connection is ready, so selecting a track immediately
 * after launch still starts playback.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java),
    )

    private val _state = MutableStateFlow(PlaybackState())

    /** Playback state published by the service connection. */
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionUpdates: Job? = null

    /**
     * Cue start offset (ms within the shared audio file) of the track currently
     * loaded by the player. Zero for regular tracks. Used by [publish] and seeking
     * to project ExoPlayer's whole-file position onto the current CUE track's interval.
     */
    private var currentCueOffsetMs: Long = 0

    /** Commands that arrived before the controller connected. */
    private val pendingCommands = mutableListOf<(MediaController) -> Unit>()

    /** Last playback failure; kept until a new queue starts or it is dismissed. */
    var lastError: String? = null
        private set

    /**
     * Last projected playlist. Reused for position-only publishes so a large
     * queue is not rebuilt twice a second; refreshed whenever the player emits
     * a playlist/timeline event.
     */
    private var cachedQueue: PlaybackQueue = PlaybackQueue()

    /**
     * Playlist indices in Media3's shuffle traversal order, or `null` when
     * shuffle is off. Fetched from the service because Media3 does not expose
     * the order on the `Player` interface; it is used only to project the
     * Queue screen in play order.
     */
    private var shuffleOrder: IntArray? = null

    /**
     * Freshly indexed artwork for tracks currently in or entering the queue.
     * Media items keep the artwork they were created with, so this overlay lets
     * a rescan replace a cover without rebuilding the playlist.
     */
    private val artworkOverrides = HashMap<String, TrackArtwork>()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(
            currentItem: MediaItem?,
            reason: Int,
        ) {
            if (currentItem.isCueSplit) {
                currentCueOffsetMs = currentItem.cueStartOffsetMs
                controller?.seekTo(currentCueOffsetMs)
            } else {
                currentCueOffsetMs = 0L
            }
            controller?.let { publish(it, refreshQueue = true, currentItem) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                refreshShuffleOrder(player)
            }

            publish(
                player,
                refreshQueue = events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                ),
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            lastError = error.toUserMessage()
            controller?.let { publish(it, refreshQueue = false) }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerController.controller !== controller) return
            this@PlayerController.controller = null
            positionUpdates?.cancel()
            positionUpdates = null
            cachedQueue = PlaybackQueue()
            shuffleOrder = null
            _state.value = PlaybackState()
        }
    }

    /**
     * Binds to the playback service. Safe to call repeatedly; the first call
     * wins and later calls are ignored while connected/connecting.
     */
    fun connect() {
        if (controller != null || controllerFuture != null) return

        val future = MediaController.Builder(context, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controllerFuture = null
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(playerListener)
                        _state.value = _state.value.copy(isConnected = true)
                        flushPendingCommands(mediaController)
                        publish(mediaController, refreshQueue = true)
                        refreshShuffleOrder(mediaController)
                        startPositionUpdates()
                    }
                    .onFailure {
                        _state.value = _state.value.copy(
                            isConnected = false,
                            playbackError = "Could not connect to the playback service.",
                        )
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Replaces the player queue with [tracks] and starts at [startIndex].
     */
    fun setQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return

        val items = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        lastError = null

        val startTrack = tracks[index]
        val isCue = startTrack.uri.contains("#cue/") || startTrack.cueStartOffsetMs > 0L
        val startPosition = if (isCue) startTrack.cueStartOffsetMs else C.TIME_UNSET
        currentCueOffsetMs = if (isCue) startTrack.cueStartOffsetMs else 0L

        withController { mediaController ->
            mediaController.setMediaItems(items, index, startPosition)
            mediaController.prepare()
            mediaController.play()
        }
    }

    /**
     * Appends [track] to the end of the queue without changing playback.
     */
    fun addToQueue(track: Track) = withController { mediaController ->
        mediaController.addMediaItem(track.toMediaItem())
    }

    /**
     * Inserts [track] immediately after the current item so it plays next.
     */
    fun playNext(track: Track) = withController { mediaController ->
        val index = (mediaController.currentMediaItemIndex + 1)
            .coerceIn(0, mediaController.mediaItemCount)
        mediaController.addMediaItem(index, track.toMediaItem())
    }

    /**
     * Moves the queue item at [fromIndex] to [toIndex].
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = withController { mediaController ->
        val count = mediaController.mediaItemCount
        val from = cachedQueue.playlistIndexAt(fromIndex)
        val to = cachedQueue.playlistIndexAt(toIndex)
        if (from == to || from !in 0 until count || to !in 0 until count) {
            return@withController
        }
        mediaController.moveMediaItem(from, to)
    }

    /**
     * Removes the queue item at [index].
     */
    fun removeQueueItem(index: Int) = withController { mediaController ->
        val playlistIndex = cachedQueue.playlistIndexAt(index)
        if (playlistIndex in 0 until mediaController.mediaItemCount) {
            mediaController.removeMediaItem(playlistIndex)
        }
    }

    /**
     * Jumps playback to the queue item at [index] and starts it.
     */
    fun seekToQueueItem(index: Int) = withController { mediaController ->
        val playlistIndex = cachedQueue.playlistIndexAt(index)
        if (playlistIndex !in 0 until mediaController.mediaItemCount) return@withController

        val item = mediaController.getMediaItemAt(playlistIndex)
        if (item.isCueSplit) {
            val offset = item.cueStartOffsetMs
            currentCueOffsetMs = offset
            mediaController.seekTo(playlistIndex, offset)
        } else {
            currentCueOffsetMs = 0L
            mediaController.seekToDefaultPosition(playlistIndex)
        }
        if (!mediaController.isPlaying) {
            if (mediaController.playbackState == Player.STATE_IDLE) {
                mediaController.prepare()
            }
            mediaController.play()
        }
    }

    fun play() = withController { it.play() }

    fun pause() = withController { it.pause() }

    /**
     * Toggles play/pause, restarting the current queue when playback already
     * reached its end so the button is never a no-op.
     */
    fun togglePlayPause() = withController { mediaController ->
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            val current = mediaController.currentMediaItem
            if (mediaController.playbackState == Player.STATE_ENDED) {
                if (current.isCueSplit) {
                    mediaController.seekTo(currentCueOffsetMs)
                } else {
                    mediaController.seekToDefaultPosition()
                }
            }
            if (mediaController.playbackState == Player.STATE_IDLE) {
                mediaController.prepare()
            }
            mediaController.play()
        }
    }

    fun seekTo(positionMs: Long) = withController { mediaController ->
        val current = mediaController.currentMediaItem
        val absolutePosition = if (current.isCueSplit) {
            currentCueOffsetMs + positionMs.coerceAtLeast(0L)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        mediaController.seekTo(absolutePosition)
    }

    fun seekToNext() = withController { mediaController ->
        val current = mediaController.currentMediaItem
        if (current.isCueSplit) {
            if (mediaController.hasNextMediaItem()) {
                val nextIndex = mediaController.nextMediaItemIndex
                if (nextIndex != C.INDEX_UNSET) {
                    val nextItem = mediaController.getMediaItemAt(nextIndex)
                    val nextOffset = nextItem.cueStartOffsetMs
                    currentCueOffsetMs = nextOffset
                    mediaController.seekTo(nextIndex, nextOffset)
                } else {
                    mediaController.seekToNext()
                }
            } else if (mediaController.repeatMode == Player.REPEAT_MODE_ALL) {
                val firstItem = mediaController.getMediaItemAt(0)
                val firstOffset = firstItem.cueStartOffsetMs
                currentCueOffsetMs = firstOffset
                mediaController.seekTo(0, firstOffset)
            }
        } else {
            mediaController.seekToNext()
        }
        reloadIfFailed(mediaController)
    }

    /**
     * Applies the required previous-button rule: restart the current item after ~3 seconds,
     * otherwise move to the previous playlist item.
     */
    fun seekToPrevious() = withController { mediaController ->
        val current = mediaController.currentMediaItem
        if (current.isCueSplit) {
            val rawPositionMs = mediaController.currentPosition.validOrZero()
            val currentTrackPositionMs = (rawPositionMs - currentCueOffsetMs).coerceAtLeast(0L)
            if (currentTrackPositionMs > 3_000L) {
                mediaController.seekTo(currentCueOffsetMs)
            } else if (mediaController.hasPreviousMediaItem()) {
                val prevIndex = mediaController.previousMediaItemIndex
                if (prevIndex != C.INDEX_UNSET) {
                    val prevItem = mediaController.getMediaItemAt(prevIndex)
                    val prevOffset = prevItem.cueStartOffsetMs
                    currentCueOffsetMs = prevOffset
                    mediaController.seekTo(prevIndex, prevOffset)
                } else {
                    mediaController.seekTo(currentCueOffsetMs)
                }
            } else {
                mediaController.seekTo(currentCueOffsetMs)
            }
        } else {
            mediaController.seekToPrevious()
        }
        reloadIfFailed(mediaController)
    }

    /** Enables or disables Media3 shuffle mode. */
    fun setShuffleModeEnabled(enabled: Boolean) = withController { mediaController ->
        mediaController.shuffleModeEnabled = enabled
    }

    /** Flips Media3 shuffle mode. */
    fun toggleShuffle() = withController { mediaController ->
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
    }

    /** Applies [mode] to Media3. */
    fun setRepeatMode(mode: PlaybackRepeatMode) = withController { mediaController ->
        mediaController.repeatMode = mode.toPlayerRepeatMode()
    }

    /** Advances the repeat mode Off -> All -> One -> Off. */
    fun cycleRepeatMode() = withController { mediaController ->
        val current = PlaybackRepeatMode.fromPlayerRepeatMode(mediaController.repeatMode)
        mediaController.repeatMode = current.next().toPlayerRepeatMode()
    }

    /** Applies artwork indexed by the latest scan to the player projection. */
    fun updateQueueArtwork(artworkByUri: Map<String, TrackArtwork?>) {
        artworkOverrides.clear()
        for ((uri, artwork) in artworkByUri) {
            if (artwork != null) artworkOverrides[uri] = artwork
        }
        controller?.let { publish(it, refreshQueue = true) }
    }

    /** Hides the last playback failure without changing playback. */
    fun dismissPlaybackError() {
        if (lastError == null) return
        lastError = null
        controller?.let { publish(it, refreshQueue = false) }
            ?: run { _state.value = _state.value.copy(playbackError = null) }
    }

    private fun reloadIfFailed(mediaController: MediaController) {
        if (mediaController.playbackState == Player.STATE_IDLE) {
            mediaController.prepare()
        }
    }

    private fun withController(command: (MediaController) -> Unit) {
        val mediaController = controller
        if (mediaController != null) {
            runCommand(mediaController, command)
            return
        }

        pendingCommands += command
        connect()
    }

    private fun runCommand(mediaController: MediaController, command: (MediaController) -> Unit) {
        scope.launch {
            if (controller !== mediaController) return@launch
            runCatching { command(mediaController) }
                .onFailure { error ->
                    Log.e(TAG, "Playback command failed", error)
                    lastError = "Playback command failed."
                }
        }
    }

    private fun flushPendingCommands(mediaController: MediaController) {
        val commands = pendingCommands.toList()
        pendingCommands.clear()
        commands.forEach { command ->
            runCatching { command(mediaController) }
                .onFailure { error ->
                    Log.e(TAG, "Playback command failed", error)
                    lastError = "Playback command failed."
                }
        }
    }

    private fun startPositionUpdates() {
        positionUpdates?.cancel()
        positionUpdates = scope.launch {
            while (isActive) {
                val mediaController = controller ?: break
                if (mediaController.isPlaying) {
                    publish(mediaController, refreshQueue = false)
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    /** Publishes a fresh [PlaybackState] snapshot. */
    private fun publish(player: Player, refreshQueue: Boolean, item: MediaItem? = null) {
        val mediaItem = item ?: player.currentMediaItem
        val trackDurationMs = mediaItem?.mediaMetadata?.durationMs ?: 0L
        val fileDurationMs = player.duration.validOrZero()

        val isCue = mediaItem.isCueSplit
        val durationMs = if (isCue && trackDurationMs > 0L) {
            trackDurationMs
        } else if (fileDurationMs > 0L) {
            fileDurationMs
        } else {
            trackDurationMs
        }

        val rawPositionMs = player.currentPosition.validOrZero()
        val positionMs = if (isCue) {
            (rawPositionMs - currentCueOffsetMs).coerceIn(0L, durationMs)
        } else {
            rawPositionMs
        }

        // Automatic transition when a CUE track finishes playing:
        if (isCue && player.isPlaying && trackDurationMs > 0L) {
            val trackEndMs = currentCueOffsetMs + trackDurationMs
            if (rawPositionMs >= trackEndMs - 150L) {
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                } else {
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> player.seekTo(currentCueOffsetMs)
                        Player.REPEAT_MODE_ALL -> player.seekTo(0, C.TIME_UNSET)
                        else -> {
                            player.pause()
                            player.seekTo(trackEndMs)
                        }
                    }
                }
            }
        }

        if (lastError != null && player.isPlaying) {
            lastError = null
        }

        if (refreshQueue || cachedQueue.items.size != player.mediaItemCount) {
            cachedQueue = buildQueue(player)
        }

        _state.value = PlaybackState(
            isConnected = true,
            currentTrack = mediaItem?.toPlaybackTrack(
                durationOverrideMs = durationMs.takeIf { it > 0L },
            ),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = durationMs,
            positionMs = positionMs,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            shuffleModeEnabled = player.shuffleModeEnabled,
            repeatMode = PlaybackRepeatMode.fromPlayerRepeatMode(player.repeatMode),
            hasEnded = player.playbackState == Player.STATE_ENDED,
            queue = cachedQueue,
            playbackError = lastError,
        )
    }

    private fun refreshShuffleOrder(player: Player) {
        val mediaController = player as? MediaController ?: return
        if (!player.shuffleModeEnabled) {
            shuffleOrder = null
            return
        }

        val future = runCatching {
            mediaController.sendCustomCommand(
                PlaybackSessionCommands.getShuffleOrder,
                Bundle.EMPTY,
            )
        }.getOrNull() ?: return

        future.addListener(
            {
                if (controller !== mediaController) return@addListener
                if (!mediaController.shuffleModeEnabled) return@addListener

                val result = runCatching { future.get() }.getOrNull() ?: return@addListener
                val order = result.extras
                    .getIntArray(PlaybackSessionCommands.EXTRA_SHUFFLE_ORDER)
                    ?: return@addListener
                if (order.size != mediaController.mediaItemCount) return@addListener

                shuffleOrder = order
                publish(mediaController, refreshQueue = true)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun buildQueue(player: Player): PlaybackQueue {
        val count = player.mediaItemCount
        if (count == 0) return PlaybackQueue()

        val order = shuffleOrder
        if (player.shuffleModeEnabled && order != null && order.size == count) {
            val items = order.map { index ->
                player.getMediaItemAt(index).toPlaybackTrack()
            }
            val current = player.currentMediaItemIndex
            val currentDisplay = if (current == C.INDEX_UNSET) {
                -1
            } else {
                order.indexOf(current)
            }
            return PlaybackQueue(
                items = items,
                currentIndex = currentDisplay,
                playlistIndices = order.toList(),
            )
        }

        val items = ArrayList<PlaybackTrack>(count)
        for (index in 0 until count) {
            items += player.getMediaItemAt(index).toPlaybackTrack()
        }
        return PlaybackQueue(
            items = items,
            currentIndex = player.currentMediaItemIndex,
        )
    }

    private fun MediaItem.toPlaybackTrack(durationOverrideMs: Long? = null): PlaybackTrack {
        val artwork = artworkOverrides[mediaId]
        return PlaybackTrack(
            uri = localConfiguration?.uri?.toString() ?: mediaId,
            mediaId = mediaId,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString(),
            album = mediaMetadata.albumTitle?.toString(),
            artworkUri = if (artwork != null) {
                artwork.artworkUri
            } else {
                mediaMetadata.artworkUri?.toString()
            },
            durationMs = durationOverrideMs ?: mediaMetadata.durationMs ?: 0L,
            albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
            fullArtworkUri = if (artwork != null) {
                artwork.fullArtworkUri
            } else {
                mediaMetadata.extras?.getString(EXTRA_FULL_ARTWORK_URI)
            },
        )
    }

    private val MediaItem?.isCueSplit: Boolean
        get() {
            if (this == null) return false
            val extras = mediaMetadata.extras
            val hasCueExtra = extras?.containsKey(EXTRA_CUE_START_OFFSET_MS) == true
            return mediaId.contains("#cue/") || hasCueExtra
        }

    private val MediaItem?.cueStartOffsetMs: Long
        get() = this?.mediaMetadata?.extras?.getLong(EXTRA_CUE_START_OFFSET_MS, 0L) ?: 0L

    private fun Long.validOrZero(): Long =
        if (this == C.TIME_UNSET || this < 0L) 0L else this

    private fun PlaybackException.toUserMessage(): String = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "The track could not be read. The file may have been moved or access may have been revoked."

        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> "This audio format is not supported on this device."

        else -> "Playback failed."
    }

    private companion object {
        const val TAG = "PlayerController"

        /** Position is published twice a second, which is enough for a seek bar. */
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}
