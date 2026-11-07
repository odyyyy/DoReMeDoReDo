package com.example.musicplayer.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traverses Storage Access Framework (SAF) document trees off the main thread
 * to discover supported audio files.
 */
@Singleton
class MusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioFileDiscovery {
    companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus"
        )

        /** Image extensions accepted as a folder cover. */
        private val ARTWORK_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")

        /**
         * Conventional cover file base names, preferred order. Used only when
         * a folder holds more than one image and none of the conventional names
         * match; the first image alphabetically is used as a last resort so any
         * image file (e.g. Cover.jpg, folder.png, my-cover.webp) can serve as
         * the folder cover instead of being silently ignored.
         */
        private val PREFERRED_ARTWORK_NAMES = listOf(
            "cover", "folder", "front", "album", "albumart", "artwork", "thumb",
        )

        /** Cue sheet extension. */
        private const val CUE_EXTENSION = "cue"

        /**
         * Upper bound for a cue sheet read. Sheets are a few kilobytes; the
         * limit only protects the scan from a provider returning a huge file.
         */
        private const val MAX_CUE_BYTES = 512 * 1024

        /**
         * Chooses the cover image of one folder from its file names.
         *
         * A single image in the folder is always treated as the cover. When
         * several exist, a conventionally named one is preferred in
         * [PREFERRED_ARTWORK_NAMES] order; otherwise the first image
         * alphabetically is used. Pure so the rule can be unit tested without a
         * document provider.
         */
        internal fun chooseFolderArtworkName(fileNames: List<String>): String? {
            val images = fileNames.filter { name ->
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ARTWORK_EXTENSIONS
            }
            if (images.isEmpty()) return null
            if (images.size == 1) return images.first()

            for (preferred in PREFERRED_ARTWORK_NAMES) {
                images.firstOrNull { name ->
                    name.substringBeforeLast('.').trim().lowercase(Locale.ROOT) == preferred
                }?.let { return it }
            }

            return images.sorted().first()
        }
    }

    /**
     * Scans the specified folder tree recursively for supported audio candidates.
     *
     * @param folderUri The SAF tree URI of the folder to scan.
     * @return A [ScanResult.Success] containing discovered audio files, or [ScanResult.Failure] if unreadable.
     */
    override suspend fun scanFolder(folderUri: Uri): ScanResult = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()

        val rootDoc = try {
            DocumentFile.fromTreeUri(context, folderUri)
        } catch (e: Exception) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = e,
                message = "Failed to access folder tree: ${e.localizedMessage ?: e.message}"
            )
        }

        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = IllegalArgumentException("Invalid folder URI or folder does not exist"),
                message = "Selected folder does not exist or is not a directory."
            )
        }

        if (!rootDoc.canRead()) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = SecurityException("Permission denied for folder URI"),
                message = "Read permission for the selected folder is no longer available."
            )
        }

        val discoveredFiles = mutableListOf<DiscoveredAudioFile>()
        val warnings = mutableListOf<String>()
        var totalFilesScanned = 0

        fun traverse(dir: DocumentFile, currentDepth: Int) {
            if (currentDepth > 20) {
                warnings.add("Exceeded maximum directory depth limit at ${dir.uri}")
                return
            }

            val files = try {
                dir.listFiles()
            } catch (e: Exception) {
                warnings.add("Failed to list contents in folder '${dir.name ?: dir.uri}': ${e.localizedMessage ?: e.message}")
                return
            }

            // One lookup per folder: all audio files in it share the same cover.
            val folderArtworkUri = selectFolderArtwork(files)

            // A cue sheet next to the audio files can define the album order
            // even when the files themselves carry no track number.
            val cueHints = readCueHints(files, warnings)

            for (file in files) {
                totalFilesScanned++
                try {
                    if (file.isDirectory) {
                        traverse(file, currentDepth + 1)
                    } else if (file.isFile && isAudioCandidate(file)) {
                        val fileName = file.name ?: "Unknown Track"
                        val cueHint = cueHints[CueSheetParser.normalizeFileName(fileName)]
                        discoveredFiles.add(
                            DiscoveredAudioFile(
                                uri = file.uri,
                                name = fileName,
                                mimeType = file.type,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                parentFolderUri = dir.uri,
                                artworkFileUri = folderArtworkUri,
                                cueTrackNumber = cueHint?.trackNumber,
                                cueDiscNumber = cueHint?.discNumber,
                            )
                        )
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to process item '${file.name ?: file.uri}': ${e.localizedMessage ?: e.message}")
                }
            }
        }

        try {
            traverse(rootDoc, 0)
        } catch (e: Exception) {
            return@withContext ScanResult.Failure(
                folderUri = folderUri,
                error = e,
                message = "Scan failed unexpectedly: ${e.localizedMessage ?: e.message}"
            )
        }

        val durationMs = System.currentTimeMillis() - startTime
        ScanResult.Success(
            folderUri = folderUri,
            audioFiles = discoveredFiles,
            totalFilesScanned = totalFilesScanned,
            durationMs = durationMs,
            warnings = warnings,
        )
    }

    /**
     * Reads the cue sheets of one folder and maps each audio file name to the
     * position declared for it.
     *
     * Only `FILE` blocks that contain exactly one `TRACK` are used. A block
     * with several tracks describes a single audio file that the app plays as
     * one item, so applying its first track number would be wrong.
     */
    private fun readCueHints(
        files: Array<DocumentFile>,
        warnings: MutableList<String>,
    ): Map<String, CueTrackHint> {
        val hints = mutableMapOf<String, CueTrackHint>()

        for (file in files) {
            val name = try {
                file.name
            } catch (_: Exception) {
                null
            } ?: continue

            if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) != CUE_EXTENSION) continue

            try {
                if (file.isDirectory) continue
                val text = readCueText(file.uri) ?: continue
                val sheet = CueSheetParser.parse(text)

                for (cueFile in sheet.files) {
                    val track = cueFile.tracks.singleOrNull() ?: continue
                    val key = CueSheetParser.normalizeFileName(cueFile.fileName)
                    if (key.isEmpty() || key in hints) continue
                    hints[key] = CueTrackHint(
                        trackNumber = track.number,
                        discNumber = sheet.discNumber,
                    )
                }
            } catch (e: Exception) {
                warnings.add(
                    "Failed to read cue sheet '$name': ${e.localizedMessage ?: e.message}",
                )
            }
        }

        return hints
    }

    /** Reads a cue sheet's text, tolerating missing or unreadable documents. */
    private fun readCueText(uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var total = 0
            while (total < MAX_CUE_BYTES) {
                val read = stream.read(chunk)
                if (read < 0) break
                output.write(chunk, 0, read)
                total += read
            }
            output.toByteArray()
        } ?: return null

        return decodeCueText(bytes)
    }

    /**
     * Decodes cue text. UTF-8 (with or without BOM) is the common case; legacy
     * single-byte sheets fall back to Latin-1, which keeps the ASCII keywords,
     * numbers, and file names the parser needs intact.
     */
    internal fun decodeCueText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    /**
     * Resolves the folder cover document, tolerating providers that fail on
     * individual entries. Returns `null` when the folder has no usable image.
     */
    private fun selectFolderArtwork(files: Array<DocumentFile>): Uri? {
        val images = files.mapNotNull { file ->
            val name = try {
                file.name
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            try {
                if (file.isDirectory) return@mapNotNull null
            } catch (_: Exception) {
                return@mapNotNull null
            }

            val isImage = try {
                file.type?.startsWith("image/", ignoreCase = true) == true ||
                    name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ARTWORK_EXTENSIONS
            } catch (_: Exception) {
                false
            }
            if (isImage) name to file.uri else null
        }

        val chosenName = chooseFolderArtworkName(images.map { it.first }) ?: return null
        return images.firstOrNull { it.first == chosenName }?.second
    }

    /**
     * Determines whether a document file represents a supported audio candidate.
     */
    fun isAudioCandidate(file: DocumentFile): Boolean {
        val name = file.name ?: return false
        return isAudioCandidateByNameAndMime(name, file.type)
    }

    /**
     * Pure function to determine audio candidacy from file name and MIME type.
     */
    fun isAudioCandidateByNameAndMime(name: String, mimeType: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        val isAudioMime = mimeType?.startsWith("audio/", ignoreCase = true) == true
        val isSupportedExt = extension in SUPPORTED_EXTENSIONS

        return isAudioMime || isSupportedExt
    }

    /** Ordering hint declared for one audio file by a folder cue sheet. */
    private data class CueTrackHint(
        val trackNumber: Int,
        val discNumber: Int?,
    )
}
