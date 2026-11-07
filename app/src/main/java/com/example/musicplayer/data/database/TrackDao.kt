package com.example.musicplayer.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the persisted track library.
 *
 * All suspend functions are executed by Room on its own background executor,
 * so callers do not need to dispatch them manually.
 */
@Dao
interface TrackDao {

    /**
     * Observes every indexed track, ordered by title.
     */
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    /**
     * Observes tracks belonging to the given [folderUri], ordered by title.
     */
    @Query(
        "SELECT * FROM tracks WHERE folderUri = :folderUri " +
            "ORDER BY title COLLATE NOCASE ASC",
    )
    fun observeByFolder(folderUri: String): Flow<List<TrackEntity>>

    /**
     * Returns tracks belonging to the given [folderUri].
     */
    @Query("SELECT * FROM tracks WHERE folderUri = :folderUri")
    suspend fun getByFolder(folderUri: String): List<TrackEntity>

    /**
     * Returns the URIs of tracks belonging to the given [folderUri]. Used to
     * detect records whose files disappeared after a completed scan.
     */
    @Query("SELECT uri FROM tracks WHERE folderUri = :folderUri")
    suspend fun getUrisByFolder(folderUri: String): List<String>

    /**
     * Returns every indexed track. Used to reconcile a completed scan against
     * what is already stored.
     */
    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    /**
     * Returns the number of indexed tracks.
     */
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    /**
     * Returns every artwork URI referenced by the library, covering both the
     * thumbnail and the full-resolution variant. Used to delete stored artwork
     * that no track points at any more.
     */
    @Query(
        "SELECT artworkUri FROM tracks WHERE artworkUri IS NOT NULL " +
            "UNION " +
            "SELECT fullArtworkUri FROM tracks WHERE fullArtworkUri IS NOT NULL",
    )
    suspend fun getArtworkUris(): List<String>

    /**
     * Number of indexed tracks whose artwork (thumbnail or full-resolution) has
     * never been read. Rows indexed before the artwork pipeline or the
     * full-resolution variant existed count here until a rescan backfills them.
     */
    @Query(
        "SELECT COUNT(*) FROM tracks " +
            "WHERE artworkScanned = 0 OR fullArtworkScanned = 0",
    )
    suspend fun countWithoutArtworkScan(): Int

    /**
     * Inserts new tracks and updates existing rows that share a URI.
     */
    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    /**
     * Inserts or updates a single track.
     */
    @Upsert
    suspend fun upsert(track: TrackEntity)

    /**
     * Deletes the given [track].
     */
    @Delete
    suspend fun delete(track: TrackEntity)

    /**
     * Deletes the tracks with the given [uris].
     */
    @Query("DELETE FROM tracks WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    /**
     * Deletes every track that belongs to [folderUri].
     */
    @Query("DELETE FROM tracks WHERE folderUri = :folderUri")
    suspend fun deleteByFolder(folderUri: String)
}
