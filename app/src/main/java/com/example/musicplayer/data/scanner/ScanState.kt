package com.example.musicplayer.data.scanner

/**
 * Canonical lifecycle of folder scans.
 *
 * A single instance is owned by the scan coordinator and exposed as a
 * [kotlinx.coroutines.flow.StateFlow], so the UI always observes the state of
 * the scan that is actually running instead of tracking one separately.
 */
sealed interface ScanState {

    /**
     * The URI string of the folder this state refers to, or `null` for [Idle].
     */
    val folderUri: String?

    /**
     * No scan has been requested yet (or the coordinator has just started).
     */
    data object Idle : ScanState {
        override val folderUri: String? get() = null
    }

    /**
     * A scan of [folderUri] is running. The persisted library is untouched
     * until the traversal completes.
     */
    data class Scanning(
        override val folderUri: String,
    ) : ScanState

    /**
     * The scan of [folderUri] completed successfully and the persisted library
     * was reconciled with its result.
     *
     * @property scanResult Files discovered during the traversal.
     * @property persistence How the database changed as a result.
     */
    data class Completed(
        override val folderUri: String,
        val scanResult: ScanResult.Success,
        val persistence: ScanPersistence,
    ) : ScanState

    /**
     * The scan of [folderUri] failed. The previously indexed library is left
     * untouched so a failed scan never wipes existing records.
     */
    data class Failed(
        override val folderUri: String,
        val message: String,
    ) : ScanState
}

/**
 * Summary of how a completed scan changed the persisted library.
 *
 * @property added Newly indexed tracks.
 * @property updated Existing tracks rewritten because size or modified time
 *   changed (or their metadata was re-read).
 * @property unchanged Existing tracks whose files were unchanged, plus tracks
 *   whose metadata could not be re-read and were kept as they were.
 * @property removed Tracks deleted because their files were no longer found by
 *   a completed scan.
 */
data class ScanPersistence(
    val added: Int,
    val updated: Int,
    val unchanged: Int,
    val removed: Int,
)