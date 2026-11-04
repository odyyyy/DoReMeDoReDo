package com.example.musicplayer.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.domain.repository.FolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Persists user selection of a music folder URI and its display name.
 */
class SetSelectedFolderUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val folderRepository: FolderRepository,
) {
    suspend operator fun invoke(uri: Uri): Boolean {
        val displayName = try {
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Selected Folder"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Selected Folder"
        }
        return folderRepository.setSelectedFolder(uri, displayName)
    }

    suspend fun clearFolder() {
        folderRepository.clearSelectedFolder()
    }
}
