package com.example.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

    /** Commands that arrived before the controller connected. */
    private val pendingCommands = mutableListOf<(MediaController) -> Unit>()

    /** Last playback failure; kept until a new queue starts or it is dismissed. */
    private var lastError: String? = null

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
        override fun onEvents(player: Player, events: Player.Events) {
            // The shuffle order changes when shuffle is toggled or the playlist
            // itself changes, so refresh the projection on those events only.
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                refreshShuffleOrder(player)
            }

            // onEvents fires for every player event, including frequent ones
            // that cannot change the queue (buffering, playing state, metadata,
            // volume, errors, ...). Only a timeline/current-item/shuffle change
            // can alter the projection, so only those rebuild it: rebuilding a
            // 10,000-item queue allocates a 10,000-element list and projects
            // every media item again, on the main thread.
            publish(
                player,
                refreshQueue = events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
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
                        // The session may already be shuffling (UI reattached
                        // while playback continues in the background).
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
     *
     * This is the single entry point for starting playback. The queue rules are:
     * - The whole collection passed in becomes the queue, in the given order.
     *   Selecting a track from the Library uses the currently displayed,
     *   filtered and sorted track list; selecting from Album Details uses the
     *   album's tracks.
     * - Playback starts at the position of the selected track ([startIndex]),
     *   clamped to the queue bounds.
     * - Previous/Next then walk the Media3 playlist, so the item index the UI
     *   observes always matches the player.
     *
     * A single track is just a one-item playlist, so the same path serves both.
     */
    fun setQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return

        val items = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        lastError = null

        withController { mediaController ->
            mediaController.setMediaItems(items, index, 0L)
            mediaController.prepare()
            mediaController.play()
        }
    }

    /**
     * Appends [track] to the end of the queue without changing playback.
     *
     * This is the documented default for "Add to queue": new items always go to
     * the end of the queue and the current item keeps playing. When nothing is
     * loaded yet the item is queued but playback does not start on its own.
     */
    fun addToQueue(track: Track) = withController { mediaController ->
        mediaController.addMediaItem(track.toMediaItem())
    }

    /**
     * Inserts [track] immediately after the current item so it plays next.
     *
     * When the queue is empty the item becomes the only queue entry; playback
     * still does not start on its own.
     */
    fun playNext(track: Track) = withController { mediaController ->
        val index = (mediaController.currentMediaItemIndex + 1)
            .coerceIn(0, mediaController.mediaItemCount)
        mediaController.addMediaItem(index, track.toMediaItem())
    }

    /**
     * Moves the queue item at [fromIndex] to [toIndex].
     *
     * Delegates to Media3, which reorders the playlist while keeping the current
     * item and its playback position correct.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = withController { mediaController ->
        val count = mediaController.mediaItemCount
        // Queue positions may be shuffled; map them back to playlist indices.
        val from = cachedQueue.playlistIndexAt(fromIndex)
        val to = cachedQueue.playlistIndexAt(toIndex)
        if (from == to || from !in 0 until count || to !in 0 until count) {
            return@withController
        }
        mediaController.moveMediaItem(from, to)
    }

    /**
     * Removes the queue item at [index].
     *
     * Media3 handles removal of the current item by moving playback to the next
     * (or previous) item, so the observed index stays consistent with the player.
     */
    fun removeQueueItem(index: Int) = withController { mediaController ->
        val playlistIndex = cachedQueue.playlistIndexAt(index)
        if (playlistIndex in 0 until mediaController.mediaItemCount) {
            mediaController.removeMediaItem(playlistIndex)
        }
    }

    /**
     * Jumps playback to the queue item at [index] and starts it.
     *
     * Selecting a queue row is an explicit request to hear that item, so a paused
     * player resumes; nothing changes when the index is out of bounds.
     */
    fun seekToQueueItem(index: Int) = withController { mediaController ->
        // The Queue screen shows the shuffled order while shuffle is on, so a
        // tapped row has to be translated to its real playlist index.
        val playlistIndex = cachedQueue.playlistIndexAt(index)
        if (playlistIndex !in 0 until mediaController.mediaItemCount) return@withController

        mediaController.seekToDefaultPosition(playlistIndex)
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
            if (mediaController.playbackState == Player.STATE_ENDED) {
                mediaController.seekToDefaultPosition()
            }
            if (mediaController.playbackState == Player.STATE_IDLE) {
                mediaController.prepare()
            }
            mediaController.play()
        }
    }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToNext() = withController { mediaController ->
        mediaController.seekToNext()
        reloadIfFailed(mediaController)
    }

    /**
     * Media3 applies the required previous-button rule: restart the current
     * item after ~3 seconds, otherwise move to the previous playlist item.
     */
    fun seekToPrevious() = withController { mediaController ->
        mediaController.seekToPrevious()
        reloadIfFailed(mediaController)
    }

    /**
     * Enables or disables Media3 shuffle mode.
     *
     * Shuffle only changes the order in which the existing playlist is
     * traversed; the playlist itself (and therefore the queue projection shown
     * by the UI) keeps its original order, exactly like Media3.
     */
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

    /**
     * Applies artwork indexed by the latest scan to the player projection.
     *
     * Keys are track URIs; a `null` value clears any previous override for
     * that URI. Only the state published to the UI changes, so a replaced
     * cover cannot interrupt or reload the current track. The media
     * notification keeps the artwork present when its item was loaded until
     * that item is loaded again.
     */
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

    /**
     * Loads the item a seek just moved to when the player is sitting idle.
     *
     * A track that cannot be read (its file was removed, or access to the folder
     * was revoked) leaves ExoPlayer in [Player.STATE_IDLE] with an error, and a
     * seek alone does not make it prepare again. Without this the Next and
     * Previous buttons would move the queue and the artwork while staying silent
     * on a perfectly playable track.
     */
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

    /**
     * Runs a player command on the controller's application thread.
     *
     * [MediaController] rejects calls made from any other thread, so commands
     * are marshalled through the main dispatcher. `Dispatchers.Main.immediate`
     * executes inline when the caller is already on the main thread, so UI
     * commands stay synchronous while tests and other callers are still safe.
     */
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

    /**
     * Publishes a fresh [PlaybackState] snapshot.
     *
     * [refreshQueue] rebuilds the queue projection from the player. It is set
     * only for playlist/timeline/track-transition events; the periodic position
     * updates and the many other player events reuse the cached queue so the
     * queue list is not reallocated on every tick.
     */
    private fun publish(player: Player, refreshQueue: Boolean) {
        val durationMs = player.duration.validOrZero()
        val item = player.currentMediaItem

        // A track that failed can be left behind by pressing play or Next; once
        // audio is running again the stale message must not stay on screen.
        if (lastError != null && player.isPlaying) {
            lastError = null
        }

        // The size check is an O(1) safety net: a playlist change must never be
        // missed by the projection even if its event was coalesced away.
        if (refreshQueue || cachedQueue.items.size != player.mediaItemCount) {
            cachedQueue = buildQueue(player)
        }

        _state.value = PlaybackState(
            isConnected = true,
            currentTrack = item?.toPlaybackTrack(
                durationOverrideMs = durationMs.takeIf { it > 0L },
            ),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = durationMs,
            positionMs = player.currentPosition.validOrZero(),
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            shuffleModeEnabled = player.shuffleModeEnabled,
            repeatMode = PlaybackRepeatMode.fromPlayerRepeatMode(player.repeatMode),
            hasEnded = player.playbackState == Player.STATE_ENDED,
            queue = cachedQueue,
            playbackError = lastError,
        )
    }

    /**
     * Asks the service for the current shuffle traversal order.
     *
     * The request is read-only and asynchronous; once the order arrives the
     * queue is rebuilt so the Queue screen shows the order shuffle will
     * actually play. While shuffle is off the cached order is dropped.
     */
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

    /** Projects the player's playlist and current index into a [PlaybackQueue]. */
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
            // An override wins even when its URI is null: that is how a cover
            // removed from the library is cleared instead of falling back to
            // the stale URI stored in the media item.
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
