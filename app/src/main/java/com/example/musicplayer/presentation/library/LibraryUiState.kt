package com.example.musicplayer.presentation.library

import com.example.musicplayer.data.scanner.ScanResult
import com.example.musicplayer.domain.model.SelectedFolder

/**
 * UI state representation for the Library screen.
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data object NoFolderSelected : LibraryUiState

    data class FolderAccessRevoked(
        val folderUri: String,
        val displayName: String,
    ) : LibraryUiState

    data class Ready(
        val folder: SelectedFolder,
        val isScanning: Boolean = false,
        val scanResult: ScanResult.Success? = null,
    ) : LibraryUiState

    data class ScanFailed(
        val folder: SelectedFolder,
        val message: String,
    ) : LibraryUiState
}
