package com.example.musicplayer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.musicplayer.domain.model.CompletedScan
import com.example.musicplayer.domain.repository.ScanStatusRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [ScanStatusRepository].
 *
 * The marker lives next to the selected-folder preference, so it survives
 * process death and is read before any scan decision is made. An unreadable or
 * missing marker resolves to `null`, which makes the app index the folder
 * again rather than assume it is up to date.
 */
@Singleton
class ScanStatusRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ScanStatusRepository {

    private companion object {
        val KEY_INDEXED_FOLDER_URI = stringPreferencesKey("indexed_folder_uri")
        val KEY_INDEXED_SCAN_AT = longPreferencesKey("indexed_scan_at")
    }

    override val lastCompletedScan: Flow<CompletedScan?> = dataStore.data
        .map { prefs ->
            val uri = prefs[KEY_INDEXED_FOLDER_URI]
            val at = prefs[KEY_INDEXED_SCAN_AT]
            if (!uri.isNullOrBlank() && at != null && at > 0L) {
                CompletedScan(folderUri = uri, completedAt = at)
            } else {
                null
            }
        }
        .flowOn(ioDispatcher)
        .catch { emit(null) }

    override suspend fun markCompleted(folderUri: String, completedAt: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_INDEXED_FOLDER_URI] = folderUri
            prefs[KEY_INDEXED_SCAN_AT] = completedAt
        }
    }
}