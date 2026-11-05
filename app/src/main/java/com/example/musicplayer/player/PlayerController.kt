package com.example.musicplayer.player

import android.content.ComponentName
import android.content.Context
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

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            lastError = error.toUserMessage()
            controller?.let(::publish)
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerController.controller !== controller) return
            this@PlayerController.controller = null
            positionUpdates?.cancel()
            positionUpdates = null
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
                        publish(mediaController)
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
     * Replaces the player playlist with [tracks] and starts at [startIndex].
     *
     * A single track is just a one-item playlist, so the same path is used now
     * and when album/track-list queues arrive.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
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

    fun play() = withController { it.play() }

    fun pause() = withController { it.pause() }

    fun togglePlayPause() = withController { mediaController ->
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            if (mediaController.playbackState == Player.STATE_IDLE) {
                mediaController.prepare()
            }
            mediaController.play()
        }
    }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToNext() = withController { it.seekToNext() }

    /**
     * Media3 applies the required previous-button rule: restart the current
     * item after ~3 seconds, otherwise move to the previous playlist item.
     */
    fun seekToPrevious() = withController { it.seekToPrevious() }

    /** Hides the last playback failure without changing playback. */
    fun dismissPlaybackError() {
        if (lastError == null) return
        lastError = null
        controller?.let(::publish)
            ?: run { _state.value = _state.value.copy(playbackError = null) }
    }

    private fun withController(command: (MediaController) -> Unit) {
        val mediaController = controller
        if (mediaController != null) {
            runCatching { command(mediaController) }
                .onFailure { lastError = "Playback command failed." }
            return
        }

        pendingCommands += command
        connect()
    }

    private fun flushPendingCommands(mediaController: MediaController) {
        val commands = pendingCommands.toList()
        pendingCommands.clear()
        commands.forEach { command ->
            runCatching { command(mediaController) }
                .onFailure { lastError = "Playback command failed." }
        }
    }

    private fun startPositionUpdates() {
        positionUpdates?.cancel()
        positionUpdates = scope.launch {
            while (isActive) {
                val mediaController = controller ?: break
                if (mediaController.isPlaying) {
                    publish(mediaController)
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun publish(player: Player) {
        val durationMs = player.duration.validOrZero()
        val item = player.currentMediaItem

        _state.value = PlaybackState(
            isConnected = true,
            currentTrack = item?.toPlaybackTrack(durationMs),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = durationMs,
            positionMs = player.currentPosition.validOrZero(),
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            playbackError = lastError,
        )
    }

    private fun MediaItem.toPlaybackTrack(durationMs: Long): PlaybackTrack =
        PlaybackTrack(
            uri = localConfiguration?.uri?.toString() ?: mediaId,
            mediaId = mediaId,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString(),
            album = mediaMetadata.albumTitle?.toString(),
            artworkUri = mediaMetadata.artworkUri?.toString(),
            durationMs = durationMs,
        )

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
        /** Position is published twice a second, which is enough for a seek bar. */
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}
