package com.example.musicplayer.data.database

import androidx.room.ColumnInfo
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
 * @property artworkScanned True once the scanner has tried to read this track's
 *   artwork. Rows indexed before artwork extraction existed start as `false`,
 *   so the next scan backfills them; a track that genuinely has no artwork is
 *   marked `true` and is not re-read on every rescan.
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
    @ColumnInfo(defaultValue = "0")
    val artworkScanned: Boolean = false,
    val folderUri: String,
    val fileSize: Long,
    val lastModified: Long,
    val dateAdded: Long,
)

/**
 * True when the file stamped [size] / [modifiedAt] is the same version of the
 * file this row was indexed from, so its metadata does not need re-reading.
 *
 * Providers that report no stamp (both values are `0`) never compare equal, so
 * those files are re-read on every scan instead of being wrongly skipped.
 */
fun TrackEntity.isSameFile(size: Long, modifiedAt: Long): Boolean =
    fileSize > 0L && lastModified > 0L && fileSize == size && lastModified == modifiedAt
