package com.example.musicplayer.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
