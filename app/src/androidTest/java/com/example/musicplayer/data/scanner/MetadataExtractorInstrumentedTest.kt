package com.example.musicplayer.data.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exercises [MetadataExtractor] against real Android framework metadata
 * parsing. The generated WAV contains silence only, so tags are absent and the
 * fallback rules are verified together with duration extraction.
 */
@RunWith(AndroidJUnit4::class)
class MetadataExtractorInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val extractor = MetadataExtractor(context = context)
    private val createdFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun extract_readsDurationFromWavAndAppliesFallbacks() = runBlocking {
        val file = createWavFile("test-tone.wav")

        val result = extractor.extract(file.toDiscoveredAudioFile())

        assertTrue("Expected success but was $result", result is MetadataResult.Success)
        val metadata = (result as MetadataResult.Success).metadata
        assertEquals("test-tone", metadata.title)
        assertEquals(TrackMetadata.UNKNOWN_ARTIST, metadata.artist)
        assertEquals(TrackMetadata.UNKNOWN_ALBUM, metadata.album)
        assertTrue(
            "Expected duration near 1000 ms but was ${metadata.durationMs}",
            metadata.durationMs in 900..1100,
        )
        assertNull(metadata.artworkBytes)
    }

    @Test
    fun extract_readsId3TagsAndEmbeddedArtwork() = runBlocking {
        val file = createTaggedMp3(
            name = "tagged.mp3",
            artwork = createJpeg(width = 64, height = 64),
        )

        val result = extractor.extract(file.toDiscoveredAudioFile(mimeType = "audio/mpeg"))

        assertTrue("Expected success but was $result", result is MetadataResult.Success)
        val metadata = (result as MetadataResult.Success).metadata
        assertEquals("Tagged Title", metadata.title)
        assertEquals("Tagged Artist", metadata.artist)
        assertEquals("Tagged Album", metadata.album)
        assertNotNull(metadata.albumId)
        val artwork = metadata.artworkBytes
        assertNotNull("Expected embedded artwork to be extracted", artwork)

        val bitmap = BitmapFactory.decodeByteArray(artwork!!, 0, artwork.size)
        assertNotNull("Extracted artwork should be a decodable image", bitmap)
        assertTrue(bitmap!!.width <= MetadataExtractor.MAX_ARTWORK_DIMENSION_PX)
        assertTrue(bitmap.height <= MetadataExtractor.MAX_ARTWORK_DIMENSION_PX)
        bitmap.recycle()

        // A 64 px source already fits the thumbnail bound, so the full variant
        // shares the same bytes instead of being re-encoded.
        val fullArtwork = metadata.fullArtworkBytes
        assertNotNull("Expected a full-resolution artwork variant", fullArtwork)
        assertTrue(fullArtwork!!.contentEquals(artwork))
    }

    @Test
    fun extract_keepsALargerFullArtworkVariantThanTheThumbnail() = runBlocking {
        val file = createTaggedMp3(
            name = "large-artwork.mp3",
            artwork = createJpeg(width = 1_024, height = 1_024),
        )

        val result = extractor.extract(file.toDiscoveredAudioFile(mimeType = "audio/mpeg"))

        assertTrue("Expected success but was $result", result is MetadataResult.Success)
        val metadata = (result as MetadataResult.Success).metadata

        val thumbnail = requireNotNull(metadata.artworkBytes)
        val full = requireNotNull(metadata.fullArtworkBytes)

        val thumbnailBitmap = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.size)
        val fullBitmap = BitmapFactory.decodeByteArray(full, 0, full.size)
        try {
            assertNotNull(thumbnailBitmap)
            assertNotNull(fullBitmap)
            assertTrue(thumbnailBitmap!!.width <= MetadataExtractor.MAX_ARTWORK_DIMENSION_PX)
            assertTrue(fullBitmap!!.width <= MetadataExtractor.MAX_FULL_ARTWORK_DIMENSION_PX)
            assertTrue(
                "the full variant must retain more detail than the thumbnail",
                fullBitmap.width > thumbnailBitmap.width,
            )
        } finally {
            thumbnailBitmap?.recycle()
            fullBitmap?.recycle()
        }
    }

    @Test
    fun extractAll_continuesAfterMissingFile() = runBlocking {
        val goodFile = createWavFile("good.wav").toDiscoveredAudioFile()
        val missingFile = DiscoveredAudioFile(
            uri = Uri.fromFile(File(context.cacheDir, "missing-${System.nanoTime()}.mp3")),
            name = "missing.mp3",
            mimeType = "audio/mpeg",
            size = 0L,
            lastModified = 0L,
            parentFolderUri = Uri.fromFile(context.cacheDir),
        )

        val results = extractor.extractAll(listOf(goodFile, missingFile))

        assertEquals(2, results.size)
        assertTrue("Expected first file to succeed but was ${results[0]}", results[0] is MetadataResult.Success)
        assertTrue("Expected missing file to fail but was ${results[1]}", results[1] is MetadataResult.Failure)
    }

    private fun File.toDiscoveredAudioFile(mimeType: String = "audio/wav") = DiscoveredAudioFile(
        uri = Uri.fromFile(this),
        name = name,
        mimeType = mimeType,
        size = length(),
        lastModified = lastModified(),
        parentFolderUri = Uri.fromFile(context.cacheDir),
    )

    private fun createJpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Writes a minimal MP3 with an ID3v2.3 tag containing title, artist, album,
     * and an embedded front-cover JPEG.
     */
    private fun createTaggedMp3(name: String, artwork: ByteArray): File {
        val tagBody = id3TextFrame("TIT2", "Tagged Title") +
            id3TextFrame("TPE1", "Tagged Artist") +
            id3TextFrame("TALB", "Tagged Album") +
            id3PictureFrame(artwork)

        val header = ByteBuffer.allocate(10).apply {
            put("ID3".toByteArray(Charsets.US_ASCII))
            put(3) // ID3v2.3
            put(0)
            put(0)
            put(syncSafeSize(tagBody.size))
        }.array()

        // One MPEG-1 Layer III frame (128 kbps, 44.1 kHz) keeps the file
        // decodable enough for metadata parsing.
        val audioFrame = ByteArray(417).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xFB.toByte()
            this[2] = 0x90.toByte()
            this[3] = 0x00
        }

        val file = File(context.cacheDir, name)
        file.outputStream().use { output ->
            output.write(header)
            output.write(tagBody)
            repeat(4) { output.write(audioFrame) }
        }
        createdFiles += file
        return file
    }

    private fun id3TextFrame(frameId: String, value: String): ByteArray =
        id3Frame(frameId, byteArrayOf(0x00) + value.toByteArray(Charsets.ISO_8859_1))

    private fun id3PictureFrame(artwork: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(0x00) // ISO-8859-1 text encoding
            write("image/jpeg".toByteArray(Charsets.US_ASCII))
            write(0x00) // MIME terminator
            write(0x03) // front cover
            write(0x00) // empty description
            write(artwork)
        }.toByteArray()
        return id3Frame("APIC", body)
    }

    private fun id3Frame(frameId: String, body: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(10).apply {
            put(frameId.toByteArray(Charsets.US_ASCII))
            putInt(body.size)
            put(0)
            put(0)
        }.array()
        return header + body
    }

    private fun syncSafeSize(size: Int): ByteArray = byteArrayOf(
        ((size shr 21) and 0x7F).toByte(),
        ((size shr 14) and 0x7F).toByte(),
        ((size shr 7) and 0x7F).toByte(),
        (size and 0x7F).toByte(),
    )

    /**
     * Writes a valid 1-second mono 16-bit PCM WAV file.
     */
    private fun createWavFile(name: String): File {
        val sampleRate = 8000
        val channels = 1
        val bitsPerSample = 16
        val durationMs = 1000
        val sampleCount = sampleRate * durationMs / 1000
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
        // The remaining bytes stay zero, i.e. one second of silence.

        val file = File(context.cacheDir, name)
        file.writeBytes(buffer.array())
        createdFiles += file
        return file
    }
}
