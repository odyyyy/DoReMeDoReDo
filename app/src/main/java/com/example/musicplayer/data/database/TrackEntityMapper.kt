package com.example.musicplayer.data.database

import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata

/**
 * Combines a discovered file with its extracted metadata into a persistable
 * row.
 *
 * @param dateAdded Timestamp to record as the track's first-seen time. Callers
 *   preserve the existing value for tracks that are already indexed.
 * @param artworkUri Optional reference to artwork stored outside the database.
 */
fun DiscoveredAudioFile.toTrackEntity(
    metadata: TrackMetadata,
    dateAdded: Long,
    artworkUri: String? = null,
): TrackEntity = TrackEntity(
    uri = uri.toString(),
    title = metadata.title,
    artist = metadata.artist,
    album = metadata.album,
    durationMs = metadata.durationMs,
    albumId = metadata.albumId,
    artworkUri = artworkUri,
    folderUri = parentFolderUri.toString(),
    fileSize = size,
    lastModified = lastModified,
    dateAdded = dateAdded,
)
