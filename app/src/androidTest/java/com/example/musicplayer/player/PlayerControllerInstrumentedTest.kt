package com.example.musicplayer.player

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.model.Track
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end checks of the service/controller path against the real
 * [PlaybackService] and ExoPlayer on a device: connect, play, pause, seek, and
 * graceful failure for an unreadable URI.
 */
@RunWith(AndroidJUnit4::class)
class PlayerControllerInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var controller: PlayerController
    private val createdFiles = mutableListOf<File>()

    private val diagScope = CoroutineScope(Dispatchers.Default)

    @Before
    fun setUp() {
        controller = PlayerController(context)
        diagScope.launch {
            controller.state.collect { Log.d("PlayerDiag", "state: $it") }
        }
        Log.d("PlayerDiag", "setUp: calling connect")
        controller.connect()
    }

    @After
    fun cancelDiag() {
        diagScope.cancel()
    }

    @After
    fun tearDown() {
        controller.pause()
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun playPauseSeek_andUnreadableUriIsReportedWithoutCrashing() = runBlocking {
        Log.d("PlayerDiag", "stage: waiting for connection")
        withTimeout(CONNECT_TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }
        Log.d("PlayerDiag", "stage: connected")

        val file = TestTracks.wavFile(
            context = context,
            name = "playback-${System.nanoTime()}.wav",
            durationMs = 5_000,
        ).also { createdFiles += it }
        val track = TestTracks.track(file, "Playback Test", durationMs = 5_000)

        Log.d("PlayerDiag", "stage: setQueue")
        controller.setQueue(listOf(track), startIndex = 0)

        val playing = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.isPlaying }
        }
        assertEquals(track.uri, playing.currentTrack?.uri)
        assertEquals("Playback Test", playing.currentTrack?.title)
        assertEquals("Test Artist", playing.currentTrack?.artist)
        assertTrue("Duration should be known, was ${playing.durationMs}", playing.durationMs > 0L)

        controller.seekTo(1_000L)
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.positionMs >= 900L }
        }

        controller.pause()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { !it.isPlaying }
        }

        val missingTrack = track.copy(
            uri = Uri.fromFile(File(context.cacheDir, "missing-${System.nanoTime()}.wav")).toString(),
        )
        controller.setQueue(listOf(missingTrack), startIndex = 0)

        val failed = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.playbackError != null }
        }
        assertTrue(!failed.isPlaying)

        // A file that disappears is recoverable: starting something playable
        // again must clear the stale message once audio is running.
        val replacement = TestTracks.wavFile(
            context = context,
            name = "playback-recovery-${System.nanoTime()}.wav",
            durationMs = 5_000,
        ).also { createdFiles += it }
        controller.setQueue(
            listOf(TestTracks.track(replacement, "Recovered", durationMs = 5_000)),
            startIndex = 0,
        )

        val recovered = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.isPlaying }
        }
        assertEquals(null, recovered.playbackError)
    }

    /**
     * A track that cannot be read leaves the player idle. Next must still start
     * the following (playable) track instead of moving the queue silently.
     */
    @Test
    fun nextAfterUnreadableTrack_recoversAndPlaysTheNextItem() = runBlocking {
        withTimeout(CONNECT_TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }

        val missing = TestTracks.track(
            file = File(context.cacheDir, "gone-${System.nanoTime()}.wav"),
            title = "Missing",
        )
        val playable = wavTrack("recover-playable", "Playable")

        controller.setQueue(listOf(missing, playable), startIndex = 0)
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.playbackError != null }
        }

        controller.seekToNext()

        val recovered = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.isPlaying }
        }
        assertEquals(playable.uri, recovered.currentTrack?.uri)
        assertEquals(null, recovered.playbackError)
    }

    /**
     * A multi-item queue is projected into [PlaybackState.queue] and
     * Next/Previous move the current index while staying in sync with the
     * service-owned player.
     */
    @Test
    fun multiItemQueue_exposesQueueAndNavigatesWithNextAndPrevious() = runBlocking {
        withTimeout(CONNECT_TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }

        val tracks = (1..3).map { index ->
            val file = TestTracks.wavFile(
                context = context,
                name = "queue-$index-${System.nanoTime()}.wav",
                durationMs = 5_000,
            ).also { createdFiles += it }
            TestTracks.track(file, "Track $index", durationMs = 5_000)
        }

        controller.setQueue(tracks, startIndex = 0)

        val started = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.isPlaying && it.queue.size == 3 }
        }
        assertEquals(0, started.queue.currentIndex)
        assertEquals("Track 1", started.queue.currentItem?.title)
        assertEquals(tracks.map { it.uri }, started.queue.items.map { it.uri })

        controller.seekToNext()
        val second = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.currentIndex == 1 }
        }
        assertEquals("Track 2", second.currentTrack?.title)

        controller.seekToPrevious()
        val backToFirst = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.currentIndex == 0 }
        }
        assertEquals("Track 1", backToFirst.currentTrack?.title)
    }

    /**
     * The queue management operations: append, insert-next, reorder and remove
     * are applied to Media3 and the projected queue/current index stay in sync.
     */
    @Test
    fun queueManagement_addsMovesAndRemovesWithoutDesync() = runBlocking {
        withTimeout(CONNECT_TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }

        val a = wavTrack("a", "A")
        val b = wavTrack("b", "B")
        val c = wavTrack("c", "C")

        controller.setQueue(listOf(a, b, c), startIndex = 0)
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 3 && it.isPlaying }
        }

        // Add to queue appends to the end and does not change the current item.
        val d = wavTrack("d", "D")
        controller.addToQueue(d)
        val withD = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 4 }
        }
        assertEquals(d.uri, withD.queue.items.last().mediaId)
        assertEquals(0, withD.queue.currentIndex)

        // Play next inserts right after the current item.
        val e = wavTrack("e", "E")
        controller.playNext(e)
        val withE = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 5 }
        }
        assertEquals(e.uri, withE.queue.items[1].mediaId)

        // Moving an item ahead of the current one keeps the current item loaded.
        controller.moveQueueItem(fromIndex = 1, toIndex = 0)
        val moved = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.items.first().mediaId == e.uri }
        }
        assertEquals(1, moved.queue.currentIndex)
        assertEquals(a.uri, moved.queue.currentItem?.mediaId)

        // Removing the current item lets Media3 advance without desync.
        controller.removeQueueItem(moved.queue.currentIndex)
        val removed = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 4 }
        }
        assertEquals(4, removed.queue.size)
        assertEquals(b.uri, removed.queue.currentItem?.mediaId)
    }

    /**
     * Shuffle and repeat modes are written to Media3 and read back from it, so
     * the UI always observes the real player state.
     */
    @Test
    fun shuffleAndRepeatModes_areAppliedAndPublished() = runBlocking {
        withTimeout(CONNECT_TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }

        val tracks = (1..3).map { index -> wavTrack("mode$index", "Track $index") }
        controller.setQueue(tracks, startIndex = 0)
        val started = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 3 && it.isPlaying }
        }
        assertFalse(started.shuffleModeEnabled)
        assertEquals(PlaybackRepeatMode.OFF, started.repeatMode)

        controller.toggleShuffle()
        val shuffled = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.shuffleModeEnabled }
        }

        // Next still moves through the queue while shuffle is on. Repeat All is
        // enabled for this step because the shuffle order is a random
        // permutation: the current item can legitimately be last in it, and then
        // Media3 correctly reports that there is no next item (the UI disables
        // the button). Wrapping makes the check independent of that permutation.
        controller.setRepeatMode(PlaybackRepeatMode.ALL)
        controller.seekToNext()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.currentIndex != shuffled.queue.currentIndex }
        }
        controller.setRepeatMode(PlaybackRepeatMode.OFF)
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.repeatMode == PlaybackRepeatMode.OFF }
        }

        // Changing the queue while shuffle is on keeps the playlist coherent
        // and does not disturb the current item.
        val current = controller.state.value
        val appended = wavTrack("mode-extra", "Extra")
        controller.addToQueue(appended)
        val grown = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 4 }
        }
        assertTrue(grown.shuffleModeEnabled)
        assertEquals(current.queue.currentItem?.mediaId, grown.queue.currentItem?.mediaId)
        assertEquals(appended.uri, grown.queue.items.last().mediaId)

        controller.toggleShuffle()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { !it.shuffleModeEnabled }
        }

        // Repeat cycles Off -> All -> One -> Off and each value is published.
        controller.cycleRepeatMode()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.repeatMode == PlaybackRepeatMode.ALL }
        }

        controller.cycleRepeatMode()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.repeatMode == PlaybackRepeatMode.ONE }
        }

        controller.cycleRepeatMode()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.repeatMode == PlaybackRepeatMode.OFF }
        }

        controller.setRepeatMode(PlaybackRepeatMode.ONE)
        val direct = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.repeatMode == PlaybackRepeatMode.ONE }
        }
        assertEquals(PlaybackRepeatMode.ONE, direct.repeatMode)
    }

    /**
     * Selecting a queue item jumps playback to it and starts it, and an
     * out-of-bounds index is ignored instead of disturbing playback.
     */
    @Test
    fun selectingAQueueItem_startsThatItem() = runBlocking {
        withTimeout(CONNECT_TIMEOUT_MS) {
            controller.state.first { it.isConnected }
        }

        val first = wavTrack("select-a", "A")
        val second = wavTrack("select-b", "B")
        val third = wavTrack("select-c", "C")
        controller.setQueue(listOf(first, second, third), startIndex = 0)
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.size == 3 && it.isPlaying }
        }

        controller.pause()
        withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { !it.isPlaying }
        }

        controller.seekToQueueItem(2)
        val selected = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.queue.currentIndex == 2 && it.isPlaying }
        }
        assertEquals(third.uri, selected.queue.currentItem?.mediaId)

        // Out-of-bounds selection must not change the current item.
        controller.seekToQueueItem(99)
        controller.pause()
        val afterInvalidSelection = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { !it.isPlaying }
        }
        assertEquals(2, afterInvalidSelection.queue.currentIndex)
        assertEquals(third.uri, afterInvalidSelection.queue.currentItem?.mediaId)
    }

    /** A 30 s silent WAV track so playback never advances mid-test. */
    private fun wavTrack(name: String, title: String): Track {
        val file = TestTracks.wavFile(
            context = context,
            name = "queue-$name-${System.nanoTime()}.wav",
        ).also { createdFiles += it }
        return TestTracks.track(file, title)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val PLAYBACK_TIMEOUT_MS = 10_000L
    }
}
