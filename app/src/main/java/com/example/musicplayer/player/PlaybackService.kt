package com.example.musicplayer.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.musicplayer.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Background playback service and the single owner of the app's ExoPlayer.
 *
 * Interruption handling follows Media3's guidance and is deliberately delegated
 * to ExoPlayer rather than re-implemented here:
 *
 * - **Audio focus** is requested by the player itself
 *   ([ExoPlayer.Builder.setAudioAttributes] with `handleAudioFocus = true`, usage
 *   `USAGE_MEDIA`, content type `AUDIO_CONTENT_TYPE_MUSIC`). A permanent loss
 *   pauses playback, a transient loss pauses and resumes when focus returns, and
 *   a transient loss that allows ducking lowers the volume. The app therefore
 *   never plays on top of a phone call or another music app.
 * - **Becoming noisy** ([ExoPlayer.Builder.setHandleAudioBecomingNoisy]`(true)`)
 *   pauses playback when headphones or a Bluetooth sink disconnect.
 * - **Screen off** is covered by [ExoPlayer.Builder.setWakeMode] with
 *   `C.WAKE_MODE_LOCAL`, which keeps the CPU and a partial wake lock while audio
 *   plays.
 * - **Foreground service and notification** are provided by
 *   [MediaSessionService]: it promotes itself to the foreground while playback
 *   is ongoing and publishes the standard media notification with previous,
 *   play/pause and next actions plus the session activity set below.
 * - **Task removal** keeps the default [MediaSessionService.onTaskRemoved]
 *   behaviour: swiping the app away while playing leaves playback and the
 *   notification running; when nothing is playing the session is stopped.
 *
 * Playback is not restored after the process is killed; see the notes in
 * `TASKS.md` (Task 18) for the documented limitation and the Media3
 * `onPlaybackResumption` path that would restore it.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val mediaSourceFactory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this))

        // Audio attributes define how the system treats this app's audio, so
        // they must describe music playback and let ExoPlayer manage audio
        // focus; both are what drives the interruption behaviour above.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppPendingIntent())
            .setCallback(PlaybackSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Tapping the media notification returns to the app. */
    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Exposes the read-only shuffle-order command to the app's controller.
     *
     * Media3 keeps the shuffle traversal order private on the
     * `androidx.media3.common.Player` surface exposed through a [MediaSession],
     * so the service answers a custom command
     * with a projection of `ExoPlayer.shuffleOrder`. The controller only reads
     * it to render the Queue in the order shuffle will play; nothing about the
     * player or its position is changed.
     */
    private inner class PlaybackSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PlaybackSessionCommands.getShuffleOrder)
                .build()
            // Build from the session and the controller info so its activity
            // (notification tap target) and other connection defaults are
            // preserved.
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != PlaybackSessionCommands.ACTION_GET_SHUFFLE_ORDER) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                )
            }

            val extras = Bundle().apply {
                putIntArray(
                    PlaybackSessionCommands.EXTRA_SHUFFLE_ORDER,
                    currentShuffleOrder(),
                )
            }
            return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_SUCCESS, extras),
            )
        }
    }

    /** Playlist indices in the order Media3 will traverse them while shuffling. */
    private fun currentShuffleOrder(): IntArray {
        val player = mediaSession?.player as? ExoPlayer ?: return IntArray(0)
        val order = player.shuffleOrder
        val result = IntArray(order.length)
        val seen = BooleanArray(order.length)
        var index = order.getFirstIndex()
        var position = 0

        while (index != C.INDEX_UNSET &&
            index in 0 until order.length &&
            !seen[index] &&
            position < result.size
        ) {
            seen[index] = true
            result[position++] = index
            index = order.getNextIndex(index)
        }

        return if (position == result.size) result else result.copyOf(position)
    }
}
