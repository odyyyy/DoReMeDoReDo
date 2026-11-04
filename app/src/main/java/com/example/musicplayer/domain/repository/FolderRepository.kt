package com.example.musicplayer.domain.repository

import android.net.Uri
import com.example.musicplayer.domain.model.SelectedFolder
import kotlinx.coroutines.flow.Flow

/**
 * Repository managing selected music folder preferences and SAF permissions.
 */
interface FolderRepository {
    /**
     * Emits the currently selected folder, or null if no folder has been selected.
     */
    val selectedFolder: Flow<SelectedFolder?>

    /**
     * Persists read permissions for the given tree [uri] (if supported) and saves it with [displayName].
     *
     * @return True if read permission is valid after saving.
     */
    suspend fun setSelectedFolder(uri: Uri, displayName: String): Boolean

    /**
     * Clears any saved folder selection preferences.
     */
    suspend fun clearSelectedFolder()

    /**
     * Checks if read access to the specified folder URI is currently valid.
     */
    fun hasValidPermission(uriString: String): Boolean
}
