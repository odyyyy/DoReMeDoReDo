package com.example.musicplayer.player

import android.content.Context
import android.net.Uri
import com.example.musicplayer.domain.model.Track
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds real, decodable media for playback tests.
 *
 * The generated files are mono 16-bit PCM WAVs of silence: ExoPlayer can decode
 * them without any asset, and silence means a test never depends on what the
 * device does with audible output. Files are written to the app cache directory
 * and owned by the caller, which deletes them on teardown.
 */
internal object TestTracks {

    /** Default length used by queue tests so a track never ends mid-assertion. */
    const val DEFAULT_DURATION_MS = 30_000

    /** Writes a silent WAV file of [durationMs] and returns it. */
    fun wavFile(
        context: Context,
        name: String,
        durationMs: Int = DEFAULT_DURATION_MS,
    ): File {
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
        return file
    }

    /** Domain track pointing at [file]. */
    fun track(
        file: File,
        title: String,
        durationMs: Int = DEFAULT_DURATION_MS,
        artist: String = "Test Artist",
        album: String = "Test Album",
    ): Track = Track(
        uri = Uri.fromFile(file).toString(),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs.toLong(),
        albumId = null,
        artworkUri = null,
        dateAdded = 0L,
    )
}