package com.example.musicplayer.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import com.example.musicplayer.domain.model.SelectedFolder
import com.example.musicplayer.domain.repository.FolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FolderRepository {

    private companion object {
        val KEY_FOLDER_URI = stringPreferencesKey("selected_folder_uri")
        val KEY_FOLDER_NAME = stringPreferencesKey("selected_folder_name")
    }

    override val selectedFolder: Flow<SelectedFolder?> = dataStore.data
        .map { prefs ->
            val uriStr = prefs[KEY_FOLDER_URI]
            val nameStr = prefs[KEY_FOLDER_NAME]
            if (!uriStr.isNullOrBlank() && !nameStr.isNullOrBlank()) {
                val hasAccess = hasValidPermission(uriStr)
                SelectedFolder(
                    uri = uriStr,
                    displayName = nameStr,
                    isAccessPersisted = hasAccess,
                )
            } else {
                null
            }
        }
        .flowOn(ioDispatcher)
        .catch { emit(null) }

    override suspend fun setSelectedFolder(uri: Uri, displayName: String): Boolean {
        takePersistablePermission(uri)

        dataStore.edit { prefs ->
            prefs[KEY_FOLDER_URI] = uri.toString()
            prefs[KEY_FOLDER_NAME] = displayName
        }
        return hasValidPermission(uri.toString())
    }

    override suspend fun clearSelectedFolder() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_FOLDER_URI)
            prefs.remove(KEY_FOLDER_NAME)
        }
    }

    override fun hasValidPermission(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return false
        }

        val persisted = try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        } catch (e: Exception) {
            false
        }

        val docFile = try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            null
        }

        val canRead = docFile?.exists() == true && docFile.canRead()
        return canRead || persisted
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Ignore if provider does not support persistable permissions
        }
    }
}
