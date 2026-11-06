package com.example.musicplayer.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads title, artist, album, duration, and embedded artwork from discovered
 * audio files using [MediaMetadataRetriever].
 *
 * Extraction always runs off the main thread. Failures are reported per file
 * through [MetadataResult.Failure] so a single corrupt or unsupported file
 * cannot abort metadata extraction for the rest of the scan.
 */
@Singleton
class MetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MetadataExtraction {
    /**
     * Reads metadata for a single file off the main thread.
     */
    suspend fun extract(file: DiscoveredAudioFile): MetadataResult =
        withContext(ioDispatcher) { extractBlocking(file) { MediaMetadataRetriever() } }

    /**
     * Reads metadata for every file off the main thread, in input order.
     *
     * A failure for one file does not stop extraction for the others.
     */
    override suspend fun extractAll(files: List<DiscoveredAudioFile>): List<MetadataResult> =
        withContext(ioDispatcher) { extractAllBlocking(files) { MediaMetadataRetriever() } }

    /**
     * Blocking variant of [extract], exposed for tests and for callers that
     * already run on a background dispatcher.
     *
     * @param createRetriever Supplies a retriever; production uses
     *   [MediaMetadataRetriever], tests can supply a fake.
     */
    internal fun extractBlocking(
        file: DiscoveredAudioFile,
        createRetriever: () -> MediaMetadataRetriever,
    ): MetadataResult {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = createRetriever()
            retriever.setDataSource(context, file.uri)
            MetadataResult.Success(
                file = file,
                metadata = readMetadata(file = file, retriever = retriever),
            )
        } catch (e: Exception) {
            MetadataResult.Failure(
                file = file,
                error = e,
                message = "Failed to read metadata for '${file.name}': " +
                    (e.localizedMessage ?: e.message ?: "unknown error"),
            )
        } finally {
            // release() is allowed to throw on some platform versions; a failed
            // release must not turn a successful extraction into a failure.
            retriever?.let { runCatching { it.release() } }
        }
    }

    /**
     * Blocking variant of [extractAll], exposed for tests.
     */
    internal fun extractAllBlocking(
        files: List<DiscoveredAudioFile>,
        createRetriever: () -> MediaMetadataRetriever,
    ): List<MetadataResult> = files.map { file ->
        extractBlocking(file) { createRetriever() }
    }

    private fun readMetadata(
        file: DiscoveredAudioFile,
        retriever: MediaMetadataRetriever,
    ): TrackMetadata {
        val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

        return buildMetadata(
            fileName = file.name,
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            rawAlbum = rawAlbum,
            rawDuration = rawDuration,
            artworkBytes = readArtwork(retriever = retriever, file = file),
        )
    }

    /**
     * Reads and bounds artwork: the embedded picture first, then a cover image
     * found next to the audio file (`cover.jpg`, `folder.jpg`, …). Missing,
     * unreadable, or corrupt artwork is a normal state and resolves to `null`.
     */
    private fun readArtwork(
        retriever: MediaMetadataRetriever,
        file: DiscoveredAudioFile,
    ): ByteArray? {
        val embedded = try {
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        }

        if (embedded != null) {
            return try {
                downscaleArtwork(embedded)
            } catch (_: Exception) {
                null
            }
        }

        return readArtworkFile(file.artworkFileUri)
    }

    /**
     * Reads a folder cover image through the document provider, decoding it at
     * the bounded artwork size instead of loading the full-resolution bitmap.
     */
    private fun readArtworkFile(uri: Uri?): ByteArray? {
        if (uri == null) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxDimension = MAX_ARTWORK_DIMENSION_PX,
                )
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            try {
                encodeArtwork(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Maximum width/height of retained artwork, in pixels. */
        const val MAX_ARTWORK_DIMENSION_PX = 512

        /** JPEG quality used when re-encoding artwork. */
        const val ARTWORK_JPEG_QUALITY = 85

        /** Upper bound for retained artwork bytes; larger results are dropped. */
        const val MAX_ARTWORK_BYTES = 512 * 1024

        private val WHITESPACE = Regex("\\s+")

        /**
         * Maps raw tag values to resolved [TrackMetadata]. Kept free of Android
         * framework types so the fallback rules can be unit tested.
         */
        internal fun buildMetadata(
            fileName: String,
            rawTitle: String?,
            rawArtist: String?,
            rawAlbum: String?,
            rawDuration: String?,
            artworkBytes: ByteArray? = null,
        ): TrackMetadata = TrackMetadata(
            title = resolveTitle(rawTitle, fileName),
            artist = resolveArtist(rawArtist),
            album = resolveAlbum(rawAlbum),
            durationMs = parseDurationMs(rawDuration),
            albumId = buildAlbumId(rawAlbum = rawAlbum, rawArtist = rawArtist),
            artworkBytes = artworkBytes,
        )

        /**
         * Resolves a display title, preferring the tag and falling back to the
         * file name without its extension.
         */
        internal fun resolveTitle(rawTitle: String?, fileName: String): String {
            sanitizeTag(rawTitle)?.let { return it }
            val nameWithoutExtension = fileName.substringBeforeLast('.', fileName).trim()
            return nameWithoutExtension.ifEmpty { TrackMetadata.UNKNOWN_TITLE }
        }

        internal fun resolveArtist(rawArtist: String?): String =
            sanitizeTag(rawArtist) ?: TrackMetadata.UNKNOWN_ARTIST

        internal fun resolveAlbum(rawAlbum: String?): String =
            sanitizeTag(rawAlbum) ?: TrackMetadata.UNKNOWN_ALBUM

        /**
         * Parses a duration string reported by [MediaMetadataRetriever].
         * Missing, malformed, or negative values resolve to `0`.
         */
        internal fun parseDurationMs(rawDuration: String?): Long =
            rawDuration?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

        /**
         * Builds a stable album-grouping key from normalized album and artist
         * tags.
         *
         * [MediaMetadataRetriever] does not expose an album ID for SAF
         * documents, so this normalized album/artist key is the available
         * identifier for grouping albums. Returns `null` when there is no album
         * tag at all.
         */
        internal fun buildAlbumId(rawAlbum: String?, rawArtist: String?): String? {
            val album = sanitizeTag(rawAlbum) ?: return null
            val artist = sanitizeTag(rawArtist) ?: TrackMetadata.UNKNOWN_ARTIST
            val normalized = "${normalizeTag(album)}|${normalizeTag(artist)}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte ->
                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
        }

        /**
         * Computes a power-of-two [BitmapFactory.Options.inSampleSize] that
         * keeps both decoded dimensions at or below [maxDimension].
         */
        internal fun calculateInSampleSize(
            width: Int,
            height: Int,
            maxDimension: Int = MAX_ARTWORK_DIMENSION_PX,
        ): Int {
            if ((width <= 0) || (height <= 0) || (maxDimension <= 0)) return 1
            var sampleSize = 1
            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            return sampleSize
        }

        private fun sanitizeTag(value: String?): String? =
            value?.trim()?.takeIf { it.isNotEmpty() }

        private fun normalizeTag(value: String): String =
            value.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

        /**
         * Decodes embedded artwork with a bounded sample size and re-encodes it
         * as a compact JPEG. Returns `null` when the artwork cannot be decoded
         * or the result still exceeds [MAX_ARTWORK_BYTES].
         */
        internal fun downscaleArtwork(
            rawArtwork: ByteArray,
            maxDimension: Int = MAX_ARTWORK_DIMENSION_PX,
            maxBytes: Int = MAX_ARTWORK_BYTES,
        ): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawArtwork, 0, rawArtwork.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxDimension = maxDimension,
                )
            }
            val bitmap = BitmapFactory.decodeByteArray(rawArtwork, 0, rawArtwork.size, options)
                ?: return null

            return try {
                encodeArtwork(bitmap = bitmap, maxBytes = maxBytes)
            } finally {
                bitmap.recycle()
            }
        }

        /**
         * Compresses a bitmap to a bounded JPEG. Pure with respect to the image
         * source, so embedded artwork and folder cover files share one encoder.
         */
        internal fun encodeArtwork(
            bitmap: Bitmap,
            maxBytes: Int = MAX_ARTWORK_BYTES,
        ): ByteArray? = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, output)
            output.toByteArray().takeIf { it.isNotEmpty() && it.size <= maxBytes }
        }
    }
}
