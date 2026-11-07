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
 * @param artworkScanned Whether artwork extraction has been attempted for this
 *   file. A freshly read file has always been tried, even when it produced no
 *   artwork.
 * @param orderScanned Whether track-position extraction has been attempted for
 *   this file. A freshly read file has always been tried, even when no number
 *   could be resolved.
 */
fun DiscoveredAudioFile.toTrackEntity(
    metadata: TrackMetadata,
    dateAdded: Long,
    artworkUri: String? = null,
    artworkScanned: Boolean = true,
    orderScanned: Boolean = true,
): TrackEntity = TrackEntity(
    uri = uri.toString(),
    title = metadata.title,
    artist = metadata.artist,
    album = metadata.album,
    durationMs = metadata.durationMs,
    trackNumber = metadata.trackNumber,
    discNumber = metadata.discNumber,
    albumId = metadata.albumId,
    artworkUri = artworkUri,
    artworkScanned = artworkScanned,
    orderScanned = orderScanned,
    folderUri = parentFolderUri.toString(),
    fileSize = size,
    lastModified = lastModified,
    dateAdded = dateAdded,
)
