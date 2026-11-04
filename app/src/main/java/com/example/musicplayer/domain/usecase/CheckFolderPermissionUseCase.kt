package com.example.musicplayer.domain.usecase

import com.example.musicplayer.domain.repository.FolderRepository
import javax.inject.Inject

/**
 * Verifies if read access to a specified SAF folder URI remains valid.
 */
class CheckFolderPermissionUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
) {
    operator fun invoke(uriString: String): Boolean {
        return folderRepository.hasValidPermission(uriString)
    }
}
