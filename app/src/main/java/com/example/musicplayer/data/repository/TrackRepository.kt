package com.example.musicplayer.data.repository

import com.example.musicplayer.data.database.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for the persisted track library.
 *
 * This is a data-layer interface: it exposes [TrackEntity] rows until the
 * domain `Track` model and its mapping are introduced with the library UI.
 */
interface TrackRepository {

    /**
     * Observes every indexed track.
     */
    fun observeAll(): Flow<List<TrackEntity>>

    /**
     * Observes tracks that belong to [folderUri].
     */
    fun observeByFolder(folderUri: String): Flow<List<TrackEntity>>

    /**
     * Returns tracks that belong to [folderUri].
     */
    suspend fun getByFolder(folderUri: String): List<TrackEntity>

    /**
     * Returns the URIs of tracks that belong to [folderUri].
     */
    suspend fun getUrisByFolder(folderUri: String): List<String>

    /**
     * Returns every indexed track, used to reconcile a completed scan.
     */
    suspend fun getAll(): List<TrackEntity>

    /**
     * Returns the number of indexed tracks.
     */
    suspend fun count(): Int

    /**
     * Returns the artwork URIs still referenced by the library, used to delete
     * stored artwork of removed or re-tagged tracks.
     */
    suspend fun getArtworkUris(): List<String>

    /**
     * True when at least one indexed track has never had its artwork read.
     *
     * Used once per upgrade to backfill covers for a library that was indexed
     * before artwork extraction existed, without rescanning on every launch.
     */
    suspend fun hasTracksWithoutArtworkScan(): Boolean

    /**
     * Inserts new tracks and updates rows that already exist for the same URI.
     */
    suspend fun upsertAll(tracks: List<TrackEntity>)

    /**
     * Deletes the tracks with the given [uris].
     */
    suspend fun deleteByUris(uris: List<String>)

    /**
     * Deletes every track that belongs to [folderUri].
     */
    suspend fun deleteByFolder(folderUri: String)
}
