package com.example.musicplayer.data.scanner

/**
 * Result of asking the scan coordinator to scan a folder.
 */
sealed interface ScanOutcome {

    /**
     * The scan ran to an end and produced [state], which is always a
     * [ScanState.Completed] or [ScanState.Failed].
     */
    data class Executed(val state: ScanState) : ScanOutcome

    /**
     * A scan is already in progress; this request was dropped so overlapping
     * scans of the same folder cannot happen. Observers should keep following
     * [com.example.musicplayer.data.scanner.ScanCoordinator.scanState].
     */
    data object Skipped : ScanOutcome
}