package com.example.musicplayer.domain.usecase

import com.example.musicplayer.data.database.TrackEntity
import com.example.musicplayer.data.repository.TrackRepository
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetAlbumsUseCaseTest {

    private fun track(
        uri: String,
        title: String,
        artist: String = "Artist A",
        album: String = "Album X",
        albumId: String? = null,
        artworkUri: String? = null,
    ) = Track(
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = 180_000L,
        albumId = albumId,
        artworkUri = artworkUri,
        dateAdded = 1000L,
    )

    private fun trackEntity(
        uri: String,
        title: String,
        artist: String = "Artist A",
        album: String = "Album X",
        albumId: String? = null,
    ) = TrackEntity(
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = 180_000L,
        albumId = albumId,
        artworkUri = null,
        folderUri = "folder://1",
        fileSize = 1000L,
        lastModified = 1000L,
        dateAdded = 1000L,
    )

    @Test
    fun `groupTracksIntoAlbums groups tracks by explicit albumId`() {
        val tracks = listOf(
            track("uri://1", "Track 1", albumId = "id-album-1"),
            track("uri://2", "Track 2", albumId = "id-album-1"),
            track("uri://3", "Track 3", albumId = "id-album-2"),
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals(2, albums.size)
        val album1 = albums.first { it.id == "id-album-1" }
        assertEquals(2, album1.trackCount)
    }

    @Test
    fun `groupTracksIntoAlbums uses normalized fallback when albumId is null`() {
        val tracks = listOf(
            track("uri://1", "Track 1", artist = "The Beatles", album = "Abbey Road"),
            track("uri://2", "Track 2", artist = "The Beatles", album = "Abbey Road"),
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals(1, albums.size)
        assertEquals("abbey road|the beatles", albums.first().id)
        assertEquals(2, albums.first().trackCount)
    }

    @Test
    fun `different artists with same album title are kept separate`() {
        val tracks = listOf(
            track("uri://1", "Track A", artist = "Artist One", album = "Greatest Hits"),
            track("uri://2", "Track B", artist = "Artist Two", album = "Greatest Hits"),
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals(2, albums.size)
        assertEquals(setOf("greatest hits|artist one", "greatest hits|artist two"), albums.map { it.id }.toSet())
    }

    @Test
    fun `artworkUri picks first non-null track artwork`() {
        val tracks = listOf(
            track("uri://1", "Track 1", albumId = "id-1", artworkUri = null),
            track("uri://2", "Track 2", albumId = "id-1", artworkUri = "content://artwork/1"),
        )

        val albums = groupTracksIntoAlbums(tracks)

        assertEquals("content://artwork/1", albums.first().artworkUri)
    }

    @Test
    fun `GetAlbumsUseCase emits albums from repository flow`() = runTest {
        val entities = listOf(
            trackEntity("uri://1", "Song 1", album = "Album A"),
            trackEntity("uri://2", "Song 2", album = "Album B"),
        )
        val fakeRepository = FakeTrackRepository(entities)
        val getTracksUseCase = GetTracksUseCase(fakeRepository)
        val getAlbumsUseCase = GetAlbumsUseCase(getTracksUseCase)

        val result = getAlbumsUseCase().first()

        assertEquals(2, result.size)
        assertEquals(setOf("Album A", "Album B"), result.map { it.title }.toSet())
    }
    @Test
    fun `GetAlbumsUseCase can group an already-subscribed track stream`() = runTest {
        val getAlbumsUseCase = GetAlbumsUseCase(GetTracksUseCase(FakeTrackRepository(emptyList())))
        val tracks = listOf(
            track("uri://1", "Track 1", album = "Album A"),
            track("uri://2", "Track 2", album = "Album A"),
            track("uri://3", "Track 3", album = "Album B"),
        )

        val result = getAlbumsUseCase(flowOf(tracks)).first()

        assertEquals(2, result.size)
        assertEquals(2, result.first { it.title == "Album A" }.trackCount)
    }
}

internal class FakeTrackRepository(
    private val entities: List<TrackEntity>,
) : TrackRepository {
    override fun observeAll(): Flow<List<TrackEntity>> = flowOf(entities)
    override fun observeByFolder(folderUri: String): Flow<List<TrackEntity>> = flowOf(entities)
    override suspend fun getByFolder(folderUri: String): List<TrackEntity> = entities
    override suspend fun getUrisByFolder(folderUri: String): List<String> = entities.map { it.uri }
    override suspend fun getAll(): List<TrackEntity> = entities
    override suspend fun count(): Int = entities.size
    override suspend fun getArtworkUris(): List<String> = entities.mapNotNull { it.artworkUri }
    override suspend fun upsertAll(tracks: List<TrackEntity>) {}
    override suspend fun deleteByUris(uris: List<String>) {}
    override suspend fun deleteByFolder(folderUri: String) {}
}
