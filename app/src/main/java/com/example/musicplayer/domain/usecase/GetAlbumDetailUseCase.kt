package com.example.musicplayer.domain.usecase

import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.albumTrackComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Result model combining an album summary and its ordered tracks. */
data class AlbumDetail(
    val album: Album,
    val tracks: List<Track>,
)

/**
 * Observes the album summary and tracks for [albumId].
 */
class GetAlbumDetailUseCase @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
) {
    operator fun invoke(albumId: String): Flow<AlbumDetail?> =
        albumTracks(albumId)
            .map { tracks ->
                val albumTracks = tracks.filter { it.getAlbumGroupingKey() == albumId }
                if (albumTracks.isEmpty()) return@map null

                val albums = groupTracksIntoAlbums(albumTracks)
                val album = albums.firstOrNull() ?: return@map null

                // Album position, not the library's title order, defines how an
                // album is read and played.
                val orderedTracks = albumTracks.sortedWith(albumTrackComparator)

                AlbumDetail(
                    album = album,
                    tracks = orderedTracks,
                )
            }
            // Filtering, grouping and ordering a large album must not block
            // the frame that shows the screen.
            .flowOn(Dispatchers.Default)

    /**
     * Chooses the narrowest library query for [albumId].
     *
     * Scanner-produced album IDs are SHA-256-derived hex strings, so they
     * match the indexed `albumId` column directly. A key containing `|` is the
     * fallback `album|artist` form used for tracks without an album tag; it is
     * not stored in a column, so those rare albums fall back to observing the
     * library and filtering it.
     */
    private fun albumTracks(albumId: String): Flow<List<Track>> =
        if (albumId.contains(FALLBACK_KEY_SEPARATOR)) {
            getTracksUseCase()
        } else {
            getTracksUseCase.byAlbumId(albumId)
        }
}

/** Separator used by `albumGroupingKey` when no tagged album ID exists. */
private const val FALLBACK_KEY_SEPARATOR = '|'
