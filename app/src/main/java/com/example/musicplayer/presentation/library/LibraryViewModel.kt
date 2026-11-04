package com.example.musicplayer.presentation.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.scanner.ScanResult
import com.example.musicplayer.domain.model.SelectedFolder
import com.example.musicplayer.domain.usecase.CheckFolderPermissionUseCase
import com.example.musicplayer.domain.usecase.GetSelectedFolderUseCase
import com.example.musicplayer.domain.usecase.ScanMusicFolderUseCase
import com.example.musicplayer.domain.usecase.SetSelectedFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing music-folder selection state and audio-file scanning operations.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSelectedFolderUseCase: GetSelectedFolderUseCase,
    private val setSelectedFolderUseCase: SetSelectedFolderUseCase,
    private val checkFolderPermissionUseCase: CheckFolderPermissionUseCase,
    private val scanMusicFolderUseCase: ScanMusicFolderUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeSelectedFolder()
    }

    private fun observeSelectedFolder() {
        viewModelScope.launch {
            getSelectedFolderUseCase().collectLatest { folder ->
                if (folder == null) {
                    _uiState.value = LibraryUiState.NoFolderSelected
                } else {
                    val hasAccess = checkFolderPermissionUseCase(folder.uri)
                    if (!hasAccess) {
                        _uiState.value = LibraryUiState.FolderAccessRevoked(
                            folderUri = folder.uri,
                            displayName = folder.displayName,
                        )
                    } else {
                        val currentState = _uiState.value
                        if (currentState is LibraryUiState.Ready && currentState.folder.uri == folder.uri) {
                            // Retain current state if already scanning or completed for this folder
                        } else {
                            _uiState.value = LibraryUiState.Ready(folder = folder)
                            scanFolderInternal(folder)
                        }
                    }
                }
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            val success = setSelectedFolderUseCase(uri)
            if (!success) {
                _uiState.value = LibraryUiState.FolderAccessRevoked(
                    folderUri = uri.toString(),
                    displayName = uri.lastPathSegment ?: "Selected Folder",
                )
            }
        }
    }

    fun rescanCurrentFolder() {
        val currentState = _uiState.value
        val folder = when (currentState) {
            is LibraryUiState.Ready -> currentState.folder
            is LibraryUiState.ScanFailed -> currentState.folder
            else -> null
        } ?: return

        scanFolderInternal(folder)
    }

    fun onClearFolder() {
        viewModelScope.launch {
            setSelectedFolderUseCase.clearFolder()
            _uiState.value = LibraryUiState.NoFolderSelected
        }
    }

    private fun scanFolderInternal(folder: SelectedFolder) {
        viewModelScope.launch {
            val previousScanResult = (_uiState.value as? LibraryUiState.Ready)?.scanResult
            _uiState.value = LibraryUiState.Ready(
                folder = folder,
                isScanning = true,
                scanResult = previousScanResult,
            )

            val uri = Uri.parse(folder.uri)
            when (val result = scanMusicFolderUseCase(uri)) {
                is ScanResult.Success -> {
                    _uiState.value = LibraryUiState.Ready(
                        folder = folder,
                        isScanning = false,
                        scanResult = result,
                    )
                }

                is ScanResult.Failure -> {
                    _uiState.value = LibraryUiState.ScanFailed(
                        folder = folder,
                        message = result.message,
                    )
                }
            }
        }
    }
}
