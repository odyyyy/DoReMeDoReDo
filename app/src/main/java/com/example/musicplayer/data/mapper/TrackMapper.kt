package com.example.musicplayer.data.mapper

import com.example.musicplayer.data.database.TrackEntity
import com.example.musicplayer.domain.model.Track

/**
 * Maps a persisted [TrackEntity] row to its domain [Track] representation.
 *
 * Fields without a domain counterpart (file size, modified stamp, parent
 * folder) stay in the data layer.
 */
fun TrackEntity.toTrack(): Track = Track(
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    albumId = albumId,
    artworkUri = artworkUri,
    dateAdded = dateAdded,
    fullArtworkUri = fullArtworkUri,
)