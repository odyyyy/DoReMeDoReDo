package com.example.musicplayer.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
     *
     * Files are read with a small bounded concurrency (see
     * [SCAN_EXTRACTION_PARALLELISM]) because each read is dominated by
     * `MediaMetadataRetriever` and SAF I/O rather than CPU on the caller. The
     * concurrency is deliberately low: one worker may hold a decoded
     * full-resolution cover while the next one is decoding its own, and a
     * larger fan-out would multiply that peak memory for little extra
     * throughput on shared storage.
     */
    override suspend fun extractAll(files: List<DiscoveredAudioFile>): List<MetadataResult> {
        if (files.isEmpty()) return emptyList()

        if (files.size == 1) {
            return withContext(ioDispatcher) {
                extractAllBlocking(files) { MediaMetadataRetriever() }
            }
        }

        // One cache per batch: a folder cover or an album's embedded picture is
        // decoded once and reused by the remaining tracks.
        val cache = ArtworkVariantCache()
        val dispatcher = ioDispatcher.limitedParallelism(
            parallelism = minOf(SCAN_EXTRACTION_PARALLELISM, files.size),
        )

        return coroutineScope {
            // CUE split tracks carry a `#cue/<n>` fragment so each split track
            // persists as a unique Room row. Group by the base URI (fragment
            // stripped) so split tracks sharing one source file are extracted
            // together and can derive each other's durations.
            val baseUri = { file: DiscoveredAudioFile ->
                file.uri.toString().takeIf { it.contains('#') }?.substringBefore('#') ?: file.uri.toString()
            }
            val byUri = files.groupBy { baseUri(it) }
            files.map { file ->
                val siblings = (byUri[baseUri(file)] ?: emptyList()).filter { it !== file }
                async(dispatcher) {
                    extractBlocking(
                        file = file,
                        cueSiblings = siblings,
                        createRetriever = { MediaMetadataRetriever() },
                        artworkCache = cache,
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * Blocking variant of [extract], exposed for tests and for callers that
     * already run on a background dispatcher.
     *
     * @param createRetriever Supplies a retriever; production uses
     *   [MediaMetadataRetriever], tests can supply a fake.
     */
    internal fun extractBlocking(
        file: DiscoveredAudioFile,
        cueSiblings: List<DiscoveredAudioFile> = emptyList(),
        artworkCache: ArtworkVariantCache? = null,
        createRetriever: () -> MediaMetadataRetriever,
    ): MetadataResult {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = createRetriever()
            // CUE split tracks carry a `#cue/<n>` fragment for row uniqueness;
            // strip it so MediaMetadataRetriever opens the underlying file.
            retriever.setDataSource(context, baseUriOf(file))
            MetadataResult.Success(
                file = file,
                metadata = readMetadata(
                    file = file,
                    retriever = retriever,
                    artworkCache = artworkCache,
                    cueSiblings = cueSiblings,
                ),
            )
        } catch (e: CancellationException) {
            throw e
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

    /** URI of the underlying audio file, with any CUE split fragment removed. */
    private fun baseUriOf(file: DiscoveredAudioFile): Uri =
        file.uri.buildUpon().fragment(null).build()

    /**
     * Blocking variant of [extractAll], exposed for tests.
     */
    internal fun extractAllBlocking(
        files: List<DiscoveredAudioFile>,
        artworkCache: ArtworkVariantCache? = null,
        createRetriever: () -> MediaMetadataRetriever,
    ): List<MetadataResult> {
        val byUri = files.groupBy { baseUriOf(it).toString() }
        return files.map { file ->
            val siblings = (byUri[baseUriOf(file).toString()] ?: emptyList()).filter { it !== file }
            extractBlocking(
                file = file,
                cueSiblings = siblings,
                artworkCache = artworkCache,
                createRetriever = createRetriever,
            )
        }
    }

    private fun readMetadata(
        file: DiscoveredAudioFile,
        retriever: MediaMetadataRetriever,
        artworkCache: ArtworkVariantCache?,
        cueSiblings: List<DiscoveredAudioFile>,
    ): TrackMetadata {
        val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val rawTrackNumber =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        val rawDiscNumber =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)

        val artwork = readArtwork(
            retriever = retriever,
            file = file,
            artworkCache = artworkCache,
        )

        // A cue sheet is the authoritative source for a track's identity when
        // the audio file's own tags are missing. Prefer the cue's title,
        // artist and album for cue-described tracks; fall back to the file's
        // tags otherwise. A single-file cue sheet (several cue tracks sharing
        // one audio file) additionally derives each track's duration from the
        // gap to the next cue track.
        val isCueDescribed = file.cueTitle != null ||
            file.cueAlbum != null ||
            file.cueArtist != null ||
            cueSiblings.isNotEmpty()
        val title = if (isCueDescribed) {
            file.cueTitle?.takeIf { it.isNotBlank() } ?: resolveTitle(rawTitle, file.name)
        } else {
            resolveTitle(rawTitle, file.name)
        }
        val resolvedArtist = if (isCueDescribed) {
            file.cueArtist?.takeIf { it.isNotBlank() } ?: rawArtist
        } else {
            rawArtist
        }
        val resolvedAlbum = if (isCueDescribed) {
            file.cueAlbum?.takeIf { it.isNotBlank() } ?: rawAlbum
        } else {
            rawAlbum
        }
        val durationMs = if (isCueDescribed && cueSiblings.isNotEmpty()) {
            cueDurationMs(file, cueSiblings, parseDurationMs(rawDuration))
        } else {
            parseDurationMs(rawDuration)
        }
        return buildMetadata(
            fileName = file.name,
            rawTitle = title,
            rawArtist = resolvedArtist,
            rawAlbum = resolvedAlbum,
            rawDuration = durationMs.toString(),
            rawTrackNumber = rawTrackNumber,
            rawDiscNumber = rawDiscNumber,
            cueTrackNumber = file.cueTrackNumber,
            cueDiscNumber = file.cueDiscNumber,
            artworkBytes = artwork.thumbnail,
            fullArtworkBytes = artwork.full,
        )
    }

    /**
     * Reads both artwork variants: the embedded picture first, then a cover
     * image found next to the audio file (`cover.jpg`, `folder.jpg`, …).
     * Missing, unreadable, or corrupt artwork is a normal state and resolves to
     * empty variants.
     */
    private fun readArtwork(
        retriever: MediaMetadataRetriever,
        file: DiscoveredAudioFile,
        artworkCache: ArtworkVariantCache?,
    ): ArtworkVariants {
        val embedded = try {
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        }

        if (embedded != null) {
            val variants = decodeEmbeddedArtwork(embedded, artworkCache)
            // A corrupt embedded picture falls through to a folder cover instead
            // of hiding artwork the folder may still provide.
            if (variants?.thumbnail != null) return variants
        }

        val artworkUri = file.artworkFileUri ?: return EMPTY_ARTWORK
        return readFolderArtwork(artworkUri, artworkCache) ?: EMPTY_ARTWORK
    }

    /**
     * Decodes an embedded picture, reusing a previous decode when another track
     * in the same batch embeds byte-identical artwork.
     */
    private fun decodeEmbeddedArtwork(
        embedded: ByteArray,
        artworkCache: ArtworkVariantCache?,
    ): ArtworkVariants? {
        if (artworkCache == null) {
            return try {
                extractArtworkVariants(embedded)
            } catch (_: Exception) {
                null
            }
        }

        val key = embeddedArtworkKey(embedded)
        artworkCache.get(key)?.let { return it }

        return try {
            extractArtworkVariants(embedded).also { variants ->
                // Cache only successfully decoded artwork: a corrupt picture
                // must still fall back to the folder cover on the next track.
                if (variants.thumbnail != null) artworkCache.put(key, variants)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads a folder cover document, reusing the decode for every track in the
     * same folder. A missing or unreadable cover is cached as empty so the scan
     * does not reopen the same document for each track.
     */
    private fun readFolderArtwork(
        artworkUri: Uri,
        artworkCache: ArtworkVariantCache?,
    ): ArtworkVariants? {
        if (artworkCache == null) return readArtworkFileVariants(artworkUri)

        val key = ArtworkVariantCache.Key(embedded = false, value = artworkUri.toString())
        artworkCache.get(key)?.let { return it }

        return (readArtworkFileVariants(artworkUri) ?: EMPTY_ARTWORK).also { variants ->
            artworkCache.put(key, variants)
        }
    }

    /** Content-addressed cache key for an embedded picture. */
    private fun embeddedArtworkKey(embedded: ByteArray): ArtworkVariantCache.Key {
        val digest = MessageDigest.getInstance("SHA-256").digest(embedded)
        val hash = buildString(ARTWORK_KEY_HASH_BYTES * 2) {
            for (index in 0 until ARTWORK_KEY_HASH_BYTES) {
                val value = digest[index].toInt() and 0xFF
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
        return ArtworkVariantCache.Key(embedded = true, value = hash)
    }

    /**
     * Reads a folder cover image through the document provider, producing the
     * bounded thumbnail and, when the source is larger, the full-resolution
     * variant used by Now Playing.
     */
    private fun readArtworkFileVariants(uri: Uri?): ArtworkVariants? {
        if (uri == null) return null
        return try {
            val bounds = readImageBounds(uri) ?: return null

            val thumbnail = decodeAndEncodeArtwork(
                sourceWidth = bounds.width,
                sourceHeight = bounds.height,
                maxDimension = MAX_ARTWORK_DIMENSION_PX,
                quality = ARTWORK_JPEG_QUALITY,
                maxBytes = MAX_ARTWORK_BYTES,
                decode = { options -> decodeImageFile(uri, options) },
            ) ?: return null

            if (bounds.width <= MAX_ARTWORK_DIMENSION_PX &&
                bounds.height <= MAX_ARTWORK_DIMENSION_PX
            ) {
                // Already thumbnail-sized: a larger decode would duplicate the
                // same pixels, so both columns can share one stored file.
                return ArtworkVariants(thumbnail = thumbnail, full = thumbnail)
            }

            val full = decodeAndEncodeArtwork(
                sourceWidth = bounds.width,
                sourceHeight = bounds.height,
                maxDimension = MAX_FULL_ARTWORK_DIMENSION_PX,
                quality = FULL_ARTWORK_JPEG_QUALITY,
                maxBytes = MAX_FULL_ARTWORK_BYTES,
                decode = { options -> decodeImageFile(uri, options) },
            ) ?: thumbnail

            ArtworkVariants(thumbnail = thumbnail, full = full)
        } catch (_: Exception) {
            null
        }
    }

    /** Reads the pixel dimensions of an image document without decoding it. */
    private fun readImageBounds(uri: Uri): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            ImageBounds(width = options.outWidth, height = options.outHeight)
        } else {
            null
        }
    }

    private fun decodeImageFile(uri: Uri, options: BitmapFactory.Options): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

    companion object {
        /**
         * Number of files metadata may be read from at once.
         *
         * Each read is dominated by `MediaMetadataRetriever` and SAF I/O rather
         * than CPU, so bounded parallelism hides provider latency. The fan-out
         * is capped at 8 to avoid decoding several full-resolution covers
         * simultaneously on memory-constrained devices.
         */
        private val SCAN_EXTRACTION_PARALLELISM: Int
            get() = minOf(8, Runtime.getRuntime().availableProcessors())

        /** Bytes of the SHA-256 digest used as an embedded-artwork cache key. */
        private const val ARTWORK_KEY_HASH_BYTES = 16

        private const val HEX_DIGITS = "0123456789abcdef"

        private val EMPTY_ARTWORK = ArtworkVariants(thumbnail = null, full = null)

        /** Maximum width/height of the thumbnail variant, in pixels. */
        const val MAX_ARTWORK_DIMENSION_PX = 512

        /** JPEG quality used when re-encoding the thumbnail variant. */
        const val ARTWORK_JPEG_QUALITY = 85

        /** Upper bound for the thumbnail variant bytes; larger results are dropped. */
        const val MAX_ARTWORK_BYTES = 512 * 1024

        /**
         * Maximum width/height of the full-resolution variant, in pixels.
         *
         * Now Playing draws the cover at roughly screen width, so this bound is
         * larger than any phone display while still protecting the library from
         * multi-megapixel source images. A cover that is already smaller keeps
         * its own resolution.
         */
        const val MAX_FULL_ARTWORK_DIMENSION_PX = 2048

        /** JPEG quality used for the full-resolution variant. */
        const val FULL_ARTWORK_JPEG_QUALITY = 92

        /**
         * Upper bound for the full-resolution variant bytes; larger results are
         * dropped and the thumbnail is used instead.
         */
        const val MAX_FULL_ARTWORK_BYTES = 4 * 1024 * 1024

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
            fullArtworkBytes: ByteArray? = null,
            rawTrackNumber: String? = null,
            rawDiscNumber: String? = null,
            cueTrackNumber: Int? = null,
            cueDiscNumber: Int? = null,
        ): TrackMetadata {
            val fileNameOrder = parseFileNameOrder(fileName)
            return TrackMetadata(
                title = resolveTitle(rawTitle, fileName),
                artist = resolveArtist(rawArtist),
                album = resolveAlbum(rawAlbum),
                durationMs = parseDurationMs(rawDuration),
                albumId = buildAlbumId(rawAlbum = rawAlbum, rawArtist = rawArtist),
                // Precedence: the file's own tag is most specific, then the
                // album's cue sheet, then the numbering embedded in the file
                // name. A lower-priority source never overrides a higher one.
                trackNumber = parseIndexNumber(rawTrackNumber)
                    ?: cueTrackNumber
                    ?: fileNameOrder.trackNumber,
                discNumber = parseIndexNumber(rawDiscNumber)
                    ?: cueDiscNumber
                    ?: fileNameOrder.discNumber,
                artworkBytes = artworkBytes,
                fullArtworkBytes = fullArtworkBytes,
            )
        }

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
         * Parses a disc or track number tag.
         *
         * Tag values commonly look like `"3"`, `"03"`, or `"3/12"`; only the
         * leading number is meaningful. Zero, negative, and non-numeric values
         * resolve to `null` so they cannot create a bogus position.
         */
        internal fun parseIndexNumber(raw: String?): Int? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val leadingDigits = text.takeWhile { it.isDigit() }
            return leadingDigits.toIntOrNull()?.takeIf { it > 0 }
        }

        /**
         * Best-effort track position from a file name.
         *
         * Handles the common rip conventions `01 - Title`, `01. Title`,
         * `01_Title`, `01 Title`, `[01] Title`, and the multi-disc form
         * `1-02 Title`. Only used when no tag or cue sheet supplies a number;
         * a leading year or any other 4+ digit number is never treated as a
         * track number.
         */
        internal fun parseFileNameOrder(fileName: String): FileNameOrder {
            val baseName = fileName.substringBeforeLast('.', fileName)

            DISC_TRACK_FILE_NAME.find(baseName)?.let { match ->
                val disc = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
                val track = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 }
                if (disc != null && track != null) {
                    return FileNameOrder(trackNumber = track, discNumber = disc)
                }
            }

            val track = TRACK_FILE_NAME.find(baseName)
                ?.groupValues?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }

            return FileNameOrder(trackNumber = track, discNumber = null)
        }

        private val DISC_TRACK_FILE_NAME =
            Regex("""^\s*(\d{1,2})\s*[-._]\s*(\d{1,2})(?:\s*[-._)]\s*|\s+|$)""")

        private val TRACK_FILE_NAME =
            Regex("""^\s*\[?(\d{1,3})\]?\s*(?:[-._)]\s*|\s+)""")

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
         * Decodes embedded artwork into the two stored variants: a bounded
         * thumbnail for lists and notifications, and a larger cover for Now
         * Playing.
         *
         * A source that already fits the thumbnail bound produces one image and
         * both variants reference the same bytes, so no duplicate file is
         * stored. Returns empty variants when the artwork cannot be decoded.
         */
        internal fun extractArtworkVariants(
            rawArtwork: ByteArray,
            maxThumbnailDimension: Int = MAX_ARTWORK_DIMENSION_PX,
            maxFullDimension: Int = MAX_FULL_ARTWORK_DIMENSION_PX,
            maxThumbnailBytes: Int = MAX_ARTWORK_BYTES,
            maxFullBytes: Int = MAX_FULL_ARTWORK_BYTES,
        ): ArtworkVariants {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawArtwork, 0, rawArtwork.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return ArtworkVariants(thumbnail = null, full = null)
            }

            val thumbnail = decodeAndEncodeArtwork(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                maxDimension = maxThumbnailDimension,
                quality = ARTWORK_JPEG_QUALITY,
                maxBytes = maxThumbnailBytes,
                decode = { options ->
                    BitmapFactory.decodeByteArray(rawArtwork, 0, rawArtwork.size, options)
                },
            ) ?: return ArtworkVariants(thumbnail = null, full = null)

            if (bounds.outWidth <= maxThumbnailDimension &&
                bounds.outHeight <= maxThumbnailDimension
            ) {
                return ArtworkVariants(thumbnail = thumbnail, full = thumbnail)
            }

            val full = decodeAndEncodeArtwork(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                maxDimension = maxFullDimension,
                quality = FULL_ARTWORK_JPEG_QUALITY,
                maxBytes = maxFullBytes,
                decode = { options ->
                    BitmapFactory.decodeByteArray(rawArtwork, 0, rawArtwork.size, options)
                },
            ) ?: thumbnail

            return ArtworkVariants(thumbnail = thumbnail, full = full)
        }

        /**
         * Decodes embedded artwork at the thumbnail size. Kept as the single
         * bounded-image entry point used by tests and older callers.
         */
        internal fun downscaleArtwork(
            rawArtwork: ByteArray,
            maxDimension: Int = MAX_ARTWORK_DIMENSION_PX,
            maxBytes: Int = MAX_ARTWORK_BYTES,
        ): ByteArray? = extractArtworkVariants(
            rawArtwork = rawArtwork,
            maxThumbnailDimension = maxDimension,
            maxFullDimension = maxDimension,
            maxThumbnailBytes = maxBytes,
            maxFullBytes = maxBytes,
        ).thumbnail

        /**
         * Decodes one bounded variant and compresses it to JPEG.
         *
         * The decode lambda is supplied by the caller so embedded artwork and
         * folder cover files share the same sampling and encoding rules.
         */
        private fun decodeAndEncodeArtwork(
            sourceWidth: Int,
            sourceHeight: Int,
            maxDimension: Int,
            quality: Int,
            maxBytes: Int,
            decode: (BitmapFactory.Options) -> Bitmap?,
        ): ByteArray? {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = sourceWidth,
                    height = sourceHeight,
                    maxDimension = maxDimension,
                )
            }
            val bitmap = decode(options) ?: return null

            return try {
                encodeArtwork(bitmap = bitmap, quality = quality, maxBytes = maxBytes)
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
            quality: Int = ARTWORK_JPEG_QUALITY,
            maxBytes: Int = MAX_ARTWORK_BYTES,
        ): ByteArray? = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray().takeIf { it.isNotEmpty() && it.size <= maxBytes }
        }
    }
}

/**
 * Computes the duration of a cue-split track from its start offset and the
 * start offsets of its siblings sharing the same audio file. The last track
 * runs to the end of the file.
 */
internal fun cueDurationMs(
    file: DiscoveredAudioFile,
    siblings: List<DiscoveredAudioFile>,
    fileDurationMs: Long,
): Long {
    val all = (siblings + listOf(file)).sortedBy { it.cueStartOffsetMs }
    val myIndex = all.indexOfFirst { it.uri == file.uri }
    if (myIndex < 0 || all.size < 2) return fileDurationMs.coerceAtLeast(0)

    val nextStart = if (myIndex + 1 < all.size) all[myIndex + 1].cueStartOffsetMs else null
    val start = file.cueStartOffsetMs.coerceAtLeast(0L).coerceAtMost(fileDurationMs)
    val end = nextStart ?: fileDurationMs
    return (end - start).coerceAtLeast(0L)
}

/**
 * The two artwork images stored per cover: the small [thumbnail] used by lists,
 * notifications and the mini player, and the larger [full] image used by Now
 * Playing. [full] may reference the same bytes as [thumbnail] when the source
 * image is already small.
 */
internal data class ArtworkVariants(
    val thumbnail: ByteArray?,
    val full: ByteArray?,
)

/** Pixel dimensions of an image document. */
private data class ImageBounds(
    val width: Int,
    val height: Int,
)

/**
 * Ordering information that can be read from an audio file name.
 *
 * @property trackNumber Position within the disc, or `null` when the name does
 *   not start with a track number.
 * @property discNumber Disc number parsed from a `disc-track` name, or `null`.
 */
internal data class FileNameOrder(
    val trackNumber: Int?,
    val discNumber: Int?,
)
