package com.example.musicplayer.data.repository

import com.example.musicplayer.data.database.TrackDao
import com.example.musicplayer.data.database.TrackEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [TrackRepository].
 *
 * Room executes suspend queries and Flow queries on its own background
 * executor, so no additional dispatcher switching is required here.
 */
@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
) : TrackRepository {

    override fun observeAll(): Flow<List<TrackEntity>> = trackDao.observeAll()

    override fun observeByFolder(folderUri: String): Flow<List<TrackEntity>> =
        trackDao.observeByFolder(folderUri)

    override suspend fun getByFolder(folderUri: String): List<TrackEntity> =
        trackDao.getByFolder(folderUri)

    override suspend fun getUrisByFolder(folderUri: String): List<String> =
        trackDao.getUrisByFolder(folderUri)

    override suspend fun getAll(): List<TrackEntity> = trackDao.getAll()

    override suspend fun count(): Int = trackDao.count()

    override suspend fun upsertAll(tracks: List<TrackEntity>) {
        if (tracks.isNotEmpty()) {
            trackDao.upsertAll(tracks)
        }
    }

    override suspend fun deleteByUris(uris: List<String>) {
        if (uris.isNotEmpty()) {
            trackDao.deleteByUris(uris)
        }
    }

    override suspend fun deleteByFolder(folderUri: String) {
        trackDao.deleteByFolder(folderUri)
    }
}
