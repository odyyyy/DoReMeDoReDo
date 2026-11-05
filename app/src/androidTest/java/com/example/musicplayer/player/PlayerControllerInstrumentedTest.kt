package com.example.musicplayer.player

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.model.Track
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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

        val file = createWavFile("playback-${System.nanoTime()}.wav", durationMs = 5_000)
        val track = Track(
            uri = Uri.fromFile(file).toString(),
            title = "Playback Test",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 5_000,
            albumId = null,
            artworkUri = null,
            dateAdded = 0L,
        )

        Log.d("PlayerDiag", "stage: playQueue")
        controller.playQueue(listOf(track), startIndex = 0)

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
        controller.playQueue(listOf(missingTrack), startIndex = 0)

        val failed = withTimeout(PLAYBACK_TIMEOUT_MS) {
            controller.state.first { it.playbackError != null }
        }
        assertTrue(!failed.isPlaying)
    }

    /** Writes a mono 16-bit PCM WAV of silence. */
    private fun createWavFile(name: String, durationMs: Int): File {
        val sampleRate = 8_000
        val channels = 1
        val bitsPerSample = 16
        val sampleCount = sampleRate * durationMs / 1_000
        val bytesPerSample = bitsPerSample / 8
        val dataSize = sampleCount * channels * bytesPerSample

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * bytesPerSample)
        buffer.putShort((channels * bytesPerSample).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        val file = File(context.cacheDir, name)
        file.writeBytes(buffer.array())
        createdFiles += file
        return file
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val PLAYBACK_TIMEOUT_MS = 10_000L
    }
}
