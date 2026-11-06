package com.example.musicplayer.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity

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
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @UnstableApi
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
}