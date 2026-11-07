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

/**
 * Stable album grouping key shared by the library and the playback surfaces.
 *
 * Uses the tagged album ID when the scanner produced one; otherwise falls back
 * to the normalized album title and artist, matching [com.example.musicplayer.domain.usecase.groupTracksIntoAlbums].
 * Keeping this in one place means a route built from the currently playing
 * track resolves to the same album as a card in the Albums grid.
 */
fun albumGroupingKey(albumId: String?, album: String, artist: String): String {
    if (!albumId.isNullOrBlank()) return albumId
    return "${album.trim().lowercase()}|${artist.trim().lowercase()}"
}
