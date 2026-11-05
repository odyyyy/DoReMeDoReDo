package com.example.musicplayer.domain.model

/**
 * Domain model representing an album grouped from indexed tracks.
 *
 * @property id Stable grouping key (`albumId` when available, or normalized album title and artist).
 * @property title Display name of the album.
 * @property artist Primary artist associated with the album.
 * @property trackCount Number of tracks in the album.
 * @property artworkUri Optional artwork reference from one of the album's tracks.
 */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val artworkUri: String?,
)
