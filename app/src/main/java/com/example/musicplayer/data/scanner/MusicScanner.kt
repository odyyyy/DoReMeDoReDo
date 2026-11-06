package com.example.musicplayer.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
         * Conventional cover file base names, most specific first. Used only when
         * a folder holds more than one image.
         */
        private val PREFERRED_ARTWORK_NAMES = listOf(
            "cover", "folder", "front", "album", "albumart", "artwork", "thumb",
        )

        /**
         * Chooses the cover image of one folder from its file names.
         *
         * A single image in the folder is always treated as the cover. When
         * several exist, only a conventionally named one is trusted, in
         * [PREFERRED_ARTWORK_NAMES] order; ambiguous folders fall back to the
         * embedded artwork only. Pure so the rule can be unit tested without a
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
            return null
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

            for (file in files) {
                totalFilesScanned++
                try {
                    if (file.isDirectory) {
                        traverse(file, currentDepth + 1)
                    } else if (file.isFile && isAudioCandidate(file)) {
                        discoveredFiles.add(
                            DiscoveredAudioFile(
                                uri = file.uri,
                                name = file.name ?: "Unknown Track",
                                mimeType = file.type,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                parentFolderUri = dir.uri,
                                artworkFileUri = folderArtworkUri,
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
}
