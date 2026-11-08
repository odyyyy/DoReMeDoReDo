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
        getTracksUseCase()
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
            // Opening an album scans the whole library; a large collection must
            // not block the frame that shows the screen.
            .flowOn(Dispatchers.Default)
}
