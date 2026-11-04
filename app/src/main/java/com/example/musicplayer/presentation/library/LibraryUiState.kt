package com.example.musicplayer.presentation.library

import com.example.musicplayer.data.scanner.ScanPersistence
import com.example.musicplayer.data.scanner.ScanResult
import com.example.musicplayer.data.scanner.ScanState
import com.example.musicplayer.domain.model.SelectedFolder

/**
 * UI state representation for the Library screen.
 */
sealed interface LibraryUiState {

    /** Folder state (or scan) is being determined. */
    data object Loading : LibraryUiState

    data object NoFolderSelected : LibraryUiState

    data class FolderAccessRevoked(
        val folderUri: String,
        val displayName: String,
    ) : LibraryUiState

    /**
     * A folder is selected and readable.
     *
     * The scan lifecycle (Idle / Scanning / Completed / Failed) is mirrored
     * from the shared scan coordinator, so the screen reflects the scan that is
     * really running - including after ViewModel recreation.
     *
     * @property scanState State of the scan belonging to [folder].
     * @property lastScanResult Result of the previous completed scan, kept
     *   visible while a rescan is running.
     */
    data class Ready(
        val folder: SelectedFolder,
        val scanState: ScanState = ScanState.Idle,
        val lastScanResult: ScanResult.Success? = null,
    ) : LibraryUiState {

        val isScanning: Boolean
            get() = scanState is ScanState.Scanning

        /** Failure of the current scan, or `null` when nothing failed. */
        val failure: ScanState.Failed?
            get() = scanState as? ScanState.Failed

        /** Newest completed scan for this folder, if any. */
        val scanResult: ScanResult.Success?
            get() = (scanState as? ScanState.Completed)?.scanResult ?: lastScanResult

        /** How the last completed scan changed the persisted library. */
        val persistence: ScanPersistence?
            get() = (scanState as? ScanState.Completed)?.persistence
    }
}