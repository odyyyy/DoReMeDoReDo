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
    ) = TrackEntity(
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = 200_000L,
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
