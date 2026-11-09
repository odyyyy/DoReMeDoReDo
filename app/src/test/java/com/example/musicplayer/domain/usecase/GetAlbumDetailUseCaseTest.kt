package com.example.musicplayer.domain.usecase

import com.example.musicplayer.data.database.TrackEntity
import com.example.musicplayer.data.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GetAlbumDetailUseCaseTest {

    private fun trackEntity(
        uri: String,
        title: String,
        artist: String = "Artist One",
        album: String = "Album One",
        albumId: String? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
    ) = TrackEntity(
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = 200_000L,
        trackNumber = trackNumber,
        discNumber = discNumber,
        albumId = albumId,
        artworkUri = null,
        folderUri = "folder://1",
        fileSize = 1000L,
        lastModified = 1000L,
        dateAdded = 1000L,
    )

    @Test
    fun `GetAlbumDetailUseCase returns album and its tracks for matching albumId`() = runTest {
        val entities = listOf(
            trackEntity("uri://1", "Track 1", albumId = "id-album-1"),
            trackEntity("uri://2", "Track 2", albumId = "id-album-1"),
            trackEntity("uri://3", "Track 3", albumId = "id-album-2"),
        )
        val repository = FakeTrackRepository(entities)
        val getTracksUseCase = GetTracksUseCase(repository)
        val useCase = GetAlbumDetailUseCase(getTracksUseCase)

        val detail = useCase("id-album-1").first()

        assertNotNull(detail)
        assertEquals("id-album-1", detail!!.album.id)
        assertEquals(2, detail.tracks.size)
        assertEquals(listOf("uri://1", "uri://2"), detail.tracks.map { it.uri })
    }

    @Test
    fun `GetAlbumDetailUseCase orders tracks by disc and track number`() = runTest {
        val entities = listOf(
            trackEntity("uri://5", "Fifth", albumId = "id-album-1", trackNumber = 5),
            trackEntity("uri://2", "Second", albumId = "id-album-1", trackNumber = 2),
            trackEntity("uri://1", "First", albumId = "id-album-1", trackNumber = 1),
            trackEntity("uri://4", "Fourth", albumId = "id-album-1", trackNumber = 4),
            trackEntity("uri://3", "Third", albumId = "id-album-1", trackNumber = 3),
        )
        val useCase = GetAlbumDetailUseCase(GetTracksUseCase(FakeTrackRepository(entities)))

        val detail = useCase("id-album-1").first()

        assertNotNull(detail)
        assertEquals(
            listOf("uri://1", "uri://2", "uri://3", "uri://4", "uri://5"),
            detail!!.tracks.map { it.uri },
        )
    }

    @Test
    fun `GetAlbumDetailUseCase keeps discs in order and numbered tracks before unnumbered`() = runTest {
        val entities = listOf(
            trackEntity("uri://disc2-1", "Disc 2 Track 1", albumId = "id-album-1", trackNumber = 1, discNumber = 2),
            trackEntity("uri://disc1-2", "Disc 1 Track 2", albumId = "id-album-1", trackNumber = 2, discNumber = 1),
            trackEntity("uri://none", "No Number", albumId = "id-album-1"),
            trackEntity("uri://disc1-1", "Disc 1 Track 1", albumId = "id-album-1", trackNumber = 1, discNumber = 1),
        )
        val useCase = GetAlbumDetailUseCase(GetTracksUseCase(FakeTrackRepository(entities)))

        val detail = useCase("id-album-1").first()

        assertEquals(
            listOf("uri://disc1-1", "uri://disc1-2", "uri://disc2-1", "uri://none"),
            detail!!.tracks.map { it.uri },
        )
    }

    @Test
    fun `GetAlbumDetailUseCase resolves a fallback album key`() = runTest {
        val entities = listOf(
            trackEntity("uri://1", "Track 1", artist = "Artist A", album = "Album A"),
            trackEntity("uri://2", "Track 2", artist = "Artist A", album = "Album A"),
            trackEntity("uri://3", "Track 3", artist = "Artist B", album = "Album A"),
        )
        val useCase = GetAlbumDetailUseCase(GetTracksUseCase(FakeTrackRepository(entities)))

        val detail = useCase("album a|artist a").first()

        assertNotNull(detail)
        assertEquals("album a|artist a", detail!!.album.id)
        assertEquals(listOf("uri://1", "uri://2"), detail.tracks.map { it.uri })
    }

    @Test
    fun `GetAlbumDetailUseCase returns null for unknown albumId`() = runTest {
        val entities = listOf(
            trackEntity("uri://1", "Track 1", albumId = "id-album-1"),
        )
        val repository = FakeTrackRepository(entities)
        val getTracksUseCase = GetTracksUseCase(repository)
        val useCase = GetAlbumDetailUseCase(getTracksUseCase)

        val detail = useCase("id-unknown").first()

        assertNull(detail)
    }
}
