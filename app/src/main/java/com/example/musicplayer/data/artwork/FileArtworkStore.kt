package com.example.musicplayer.data.artwork

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-backed [ArtworkStore] writing content-addressed JPEGs into an
 * app-private directory.
 *
 * Storage strategy:
 *  - The directory lives in `noBackupFilesDir`, so artwork is never uploaded by
 *    auto backup and deleting a file is always safe: the images are regenerated
 *    from the audio files by a (manual) rescan.
 *  - The file name is the SHA-256 hash of the stored bytes, which makes identical
 *    album covers share one file and makes the URI change whenever the image
 *    content changes. That keeps the indexed `artworkUri` valid for the file
 *    version it was read from and gives the UI an unambiguous cache key.
 *  - Only the bounded JPEG produced by `MetadataExtractor` is stored (max 512 px
 *    on the long edge), and Coil decodes that master at the exact size each
 *    surface draws, so no separate on-disk thumbnail variant is kept.
 *  - Writes go through a temporary file and a rename, so a crash or a full disk
 *    can never leave a half-written image behind.
 */
@Singleton
class FileArtworkStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ArtworkStore {

    private val directory: File
        get() = File(context.noBackupFilesDir, DIRECTORY_NAME)

    override suspend fun store(artworkBytes: ByteArray): String? {
        if (artworkBytes.isEmpty()) return null
        return withContext(ioDispatcher) { write(artworkBytes) }
    }

    override suspend fun deleteUnreferenced(referencedArtworkUris: Collection<String>) {
        withContext(ioDispatcher) { removeUnreferenced(referencedArtworkUris) }
    }

    private fun write(artworkBytes: ByteArray): String? {
        val file = File(directory, fileNameFor(artworkBytes))
        // Content addressing makes the write idempotent: an image that is already
        // stored (an album cover shared by many tracks, or the same picture in a
        // rescan) is not written again.
        if (file.isFile) return file.toUriString()

        return try {
            directory.mkdirs()
            val partial = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, directory)
            partial.writeBytes(artworkBytes)
            if (partial.renameTo(file)) {
                file.toUriString()
            } else {
                // The destination appeared concurrently (another track of the same
                // album was scanned first): the existing file is identical, so it
                // is a valid result.
                partial.delete()
                if (file.isFile) file.toUriString() else null
            }
        } catch (_: Exception) {
            // Out of space or an unwritable directory: artwork is optional.
            null
        }
    }

    private fun removeUnreferenced(referencedArtworkUris: Collection<String>) {
        val referencedNames = referencedArtworkUris.mapNotNullTo(HashSet()) { uri -> fileNameOf(uri) }
        val files = try {
            directory.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        for (file in files) {
            // Leftover `tmp-*` files from an interrupted write are removed too:
            // they are never referenced by the library.
            if (file.name !in referencedNames) {
                runCatching { file.delete() }
            }
        }
    }

    private fun File.toUriString(): String = Uri.fromFile(this).toString()

    companion object {
        /** Name of the artwork directory inside `noBackupFilesDir`. */
        const val DIRECTORY_NAME = "artwork"

        /** Number of hash bytes kept for a file name (128 bits). */
        private const val HASH_BYTES = 16

        private const val FILE_SUFFIX = ".jpg"
        private const val TEMP_PREFIX = "tmp-"
        private const val TEMP_SUFFIX = ".tmp"

        /**
         * Content-addressed file name for [artworkBytes].
         *
         * Pure and framework free so the identity of a stored image can be unit
         * tested without a device.
         */
        internal fun fileNameFor(artworkBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(artworkBytes)
            val hex = buildString(HASH_BYTES * 2) {
                for (index in 0 until HASH_BYTES) {
                    val value = digest[index].toInt() and 0xFF
                    append(HEX_DIGITS[value ushr 4])
                    append(HEX_DIGITS[value and 0x0F])
                }
            }
            return hex + FILE_SUFFIX
        }

        /**
         * File name referenced by a stored artwork URI.
         *
         * Returns `null` for anything that is not a file URI in this app's
         * artwork directory, so a foreign URI can never mark a local file as
         * "still in use" (or delete one).
         */
        internal fun fileNameOf(artworkUri: String): String? {
            val parsed = runCatching { Uri.parse(artworkUri) }.getOrNull() ?: return null
            if (parsed.scheme != SCHEME_FILE) return null
            return parsed.lastPathSegment?.takeIf { it.isNotBlank() }
        }

        private const val SCHEME_FILE = "file"
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
