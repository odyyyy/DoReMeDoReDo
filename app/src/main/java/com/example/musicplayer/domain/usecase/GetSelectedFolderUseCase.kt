package com.example.musicplayer.domain.usecase

import com.example.musicplayer.domain.model.SelectedFolder
import com.example.musicplayer.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves the currently selected music folder as an observable flow.
 */
class GetSelectedFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
) {
    operator fun invoke(): Flow<SelectedFolder?> = folderRepository.selectedFolder
}
