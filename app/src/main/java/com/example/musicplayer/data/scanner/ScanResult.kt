package com.example.musicplayer.data.scanner

import android.net.Uri
import com.example.musicplayer.domain.model.DiscoveredAudioFile

/**
 * Result of an audio-file discovery scan over a SAF tree document folder.
 */
sealed interface ScanResult {
    /**
     * Indicates a scan completed successfully, containing all discovered audio files.
     *
     * @property folderUri Root tree URI of the scanned folder.
     * @property audioFiles List of discovered candidate audio files.
     * @property totalFilesScanned Total number of items (files + directories) encountered.
     * @property durationMs Elapsed scan duration in milliseconds.
     * @property warnings Non-fatal recoverable issues encountered during scanning (e.g. inaccessible subdirectories).
     */
    data class Success(
        val folderUri: Uri,
        val audioFiles: List<DiscoveredAudioFile>,
        val totalFilesScanned: Int,
        val durationMs: Long,
        val warnings: List<String> = emptyList(),
    ) : ScanResult

    /**
     * Indicates an unrecoverable failure during scanning.
     *
     * @property folderUri Root tree URI of the scanned folder.
     * @property error Underlaying exception causing the failure.
     * @property message Human-readable error description.
     */
    data class Failure(
        val folderUri: Uri,
        val error: Throwable,
        val message: String,
    ) : ScanResult
}
