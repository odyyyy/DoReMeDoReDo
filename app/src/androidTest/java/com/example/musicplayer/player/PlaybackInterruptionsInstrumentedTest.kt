package com.example.musicplayer.player

import android.app.Notification
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests for the interruption behaviour that Task 18 owns.
 *
 * Focus is exercised for real: the test asks the platform for audio focus while
 * the service-owned player is playing, which is exactly what an incoming call or
 * another music app does. The assertions therefore check the actual Media3
 * behaviour rather than a mock.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackInterruptionsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var controller: PlayerController
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        controller = PlayerController(context)
        controller.connect()
        audioManager = context.getSystemService(AudioManager::class.java)
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    @After
    fun tearDown() {
        controller.pause()
        createdFiles.forEach { it.delete() }
    }

    /**
     * A permanent focus loss (a call, another music app) pauses playback and it
     * stays paused after the other client goes away.
     */
    @Test
    fun permanentAudioFocusLoss_pausesAndDoesNotResume() = runBlocking<Unit> {
        startPlaying()
        val request = focusRequest(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
            audioManager.requestAudioFocus(request),
        )

        withTimeout(TIMEOUT_MS) {
            controller.state.first { !it.isPlaying }
        }

        audioManager.abandonAudioFocusRequest(request)

        // A permanent loss is not resumed automatically.
        delay(1_000)
        assertTrue(
            "Playback must not resume after a permanent focus loss",
            !controller.state.value.isPlaying,
        )
    }

    /** A transient loss pauses, and playback resumes once focus is returned. */
    @Test
    fun transientAudioFocusLoss_pausesAndResumesOnRegain() = runBlocking<Unit> {
        startPlaying()
        val request = focusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

        assertEquals(
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
            audioManager.requestAudioFocus(request),
        )
        withTimeout(TIMEOUT_MS) {
            controller.state.first { !it.isPlaying }
        }

        audioManager.abandonAudioFocusRequest(request)
        withTimeout(TIMEOUT_MS) {
            controller.state.first { it.isPlaying }
        }
    }

    /**
     * While audio plays, the media session service is in the foreground and its
     * notification carries the transport actions and a tap target.
     *
     * Headphones/Bluetooth disconnection cannot be covered here: only the system
     * may send `ACTION_AUDIO_BECOMING_NOISY` (it is a protected broadcast, so a
     * test or `adb shell am broadcast` is rejected with a permission denial),
     * and the emulator has no wired or Bluetooth sink to disconnect. That path
     * is therefore enabled with `setHandleAudioBecomingNoisy(true)` in
     * [PlaybackService] and left for a physical-device check; see TASKS.md,
     * Task 18.
     */
    @Test
    fun mediaNotification_isPostedWithTransportActions() = runBlocking<Unit> {
        startPlaying()

        val notification = awaitMediaNotification()
        assertNotNull("No media notification was posted while playing", notification)

        val actions = notification!!.actions ?: emptyArray()
        assertTrue(
            "Expected previous, play/pause and next actions, found ${actions.size}",
            actions.size >= 3,
        )
        assertNotNull("The notification must open the app when tapped", notification.contentIntent)
    }

    /**
     * Starts playback of a three-track queue of silent 30 s tracks and waits
     * until it runs. Three items keep Next/Previous available, which is what
     * gives the media notification its full transport action set.
     */
    private suspend fun startPlaying() {
        withTimeout(TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }

        val tracks = (1..3).map { index ->
            val file = TestTracks.wavFile(
                context = context,
                name = "interruption-$index-${System.nanoTime()}.wav",
            ).also { createdFiles += it }
            TestTracks.track(file, "Interruption $index")
        }
        controller.setQueue(tracks, startIndex = 0)

        withTimeout(TIMEOUT_MS) {
            controller.state.first { it.isPlaying }
        }
    }

    /**
     * Polls the notification manager for the media notification.
     *
     * Posting it happens on the service side, so it can lag behind the state
     * change that started playback.
     */
    private suspend fun awaitMediaNotification(): Notification? {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            notificationManager.activeNotifications
                .firstOrNull { (it.notification.actions?.size ?: 0) >= 3 }
                ?.let { return it.notification }
            delay(200)
        }
        return null
    }

    private fun focusRequest(gainType: Int): AudioFocusRequest =
        AudioFocusRequest.Builder(gainType)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}