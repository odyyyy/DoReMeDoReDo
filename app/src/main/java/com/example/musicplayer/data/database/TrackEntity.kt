package com.example.musicplayer.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A track persisted in the local library.
 *
 * The document URI is the stable identity of a track: rescanning the same file
 * upserts this row instead of creating a duplicate.
 *
 * @property uri Stable document/content URI of the audio file.
 * @property title Display title.
 * @property artist Track artist.
 * @property album Album name.
 * @property durationMs Duration in milliseconds, or `0` when unavailable.
 * @property albumId Optional album-grouping key derived from tags.
 * @property artworkUri Optional reference to local artwork, or `null` when the
 *   track has no artwork.
 * @property folderUri URI of the parent folder that was scanned.
 * @property fileSize File size in bytes, or `0` when the provider did not
 *   report it.
 * @property lastModified Last modified timestamp in milliseconds since epoch,
 *   or `0` when unavailable.
 * @property dateAdded Timestamp in milliseconds since epoch when the track was
 *   first indexed.
 */
@Entity(
    tableName = "tracks",
    indices = [
        // Used to diff a scanned folder against the persisted library.
        Index(value = ["folderUri"]),
        // Used by the default and selectable track orderings.
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["dateAdded"]),
    ],
)
data class TrackEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: String?,
    val artworkUri: String?,
    val folderUri: String,
    val fileSize: Long,
    val lastModified: Long,
    val dateAdded: Long,
)
