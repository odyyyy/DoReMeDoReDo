package com.example.musicplayer.domain.usecase

import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Groups indexed library tracks into domain [Album] models.
 *
 * Grouping rule:
 *  - Uses [Track.albumId] when available (synthetic key derived during scan).
 *  - Fallbacks to normalized album title and artist (`album|artist`).
 *  - Keeps albums by different artists with the same title distinct.
 */
class GetAlbumsUseCase @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
) {
    /** Observes every album grouped from indexed tracks, sorted by title. */
    operator fun invoke(): Flow<List<Album>> = invoke(getTracksUseCase())

    /**
     * Groups albums from an already-subscribed track stream.
     *
     * A caller that also needs the tracks themselves (search, sorting) shares one
     * Room subscription and one entity-to-domain mapping this way, instead of
     * starting a second query and a second mapping pass per library change.
     */
    operator fun invoke(tracks: Flow<List<Track>>): Flow<List<Album>> =
        tracks.map { groupTracksIntoAlbums(it) }
}

/** Pure grouping helper for domain tracks into albums. */
fun groupTracksIntoAlbums(tracks: List<Track>): List<Album> {
    return tracks
        .groupBy { it.getAlbumGroupingKey() }
        .map { (key, albumTracks) ->
            val firstTrack = albumTracks.first()
            val artworkUri = albumTracks
                .mapNotNull { it.artworkUri }
                .firstOrNull()
            Album(
                id = key,
                title = firstTrack.album,
                artist = firstTrack.artist,
                trackCount = albumTracks.size,
                artworkUri = artworkUri,
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

/** Determines the stable grouping key for a track. */
fun Track.getAlbumGroupingKey(): String {
    if (!albumId.isNullOrBlank()) return albumId
    val normTitle = album.trim().lowercase()
    val normArtist = artist.trim().lowercase()
    return "$normTitle|$normArtist"
}
