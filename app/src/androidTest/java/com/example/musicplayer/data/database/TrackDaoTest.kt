package com.example.musicplayer.data.database

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the persisted library schema and queries against a real in-memory
 * SQLite database.
 */
@RunWith(AndroidJUnit4::class)
class TrackDaoTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: AppDatabase
    private lateinit var dao: TrackDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.trackDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAndObserve_roundTripsAllFields() = runBlocking {
        val track = sampleTrack(uri = "content://music/track-1")

        dao.upsert(track)

        assertEquals(listOf(track), dao.observeAll().first())
    }

    @Test
    fun upsert_sameUriUpdatesInsteadOfDuplicating() = runBlocking {
        val original = sampleTrack(uri = "content://music/track-1", title = "Original")
        dao.upsert(original)

        dao.upsert(original.copy(title = "Updated"))

        assertEquals(1, dao.count())
        assertEquals("Updated", dao.observeAll().first().single().title)
    }

    @Test
    fun getByFolderAndDeleteByUris_onlyAffectMatchingRows() = runBlocking {
        val first = sampleTrack(uri = "content://music/1", folderUri = FOLDER_A)
        val second = sampleTrack(uri = "content://music/2", folderUri = FOLDER_A)
        val third = sampleTrack(uri = "content://music/3", folderUri = FOLDER_B)
        dao.upsertAll(listOf(first, second, third))

        assertEquals(
            listOf(first.uri, second.uri).sorted(),
            dao.getUrisByFolder(FOLDER_A).sorted(),
        )

        dao.deleteByUris(listOf(first.uri, third.uri))

        assertEquals(listOf(second.uri), dao.getUrisByFolder(FOLDER_A))
        assertTrue(dao.getByFolder(FOLDER_B).isEmpty())
    }

    @Test
    fun deleteByFolder_removesOnlyThatFoldersTracks() = runBlocking {
        dao.upsertAll(
            listOf(
                sampleTrack(uri = "content://music/a-1", folderUri = FOLDER_A),
                sampleTrack(uri = "content://music/a-2", folderUri = FOLDER_A),
                sampleTrack(uri = "content://music/b-1", folderUri = FOLDER_B),
            ),
        )

        dao.deleteByFolder(FOLDER_A)

        assertEquals(1, dao.count())
        assertEquals(listOf("content://music/b-1"), dao.getUrisByFolder(FOLDER_B))
    }

    @Test
    fun getArtworkUris_includesBothThumbnailAndFullVariants() = runBlocking {
        dao.upsertAll(
            listOf(
                sampleTrack(uri = "content://music/a").copy(
                    artworkUri = "file:///artwork/a-thumb.jpg",
                    fullArtworkUri = "file:///artwork/a-full.jpg",
                ),
                sampleTrack(uri = "content://music/b").copy(
                    artworkUri = "file:///artwork/b-thumb.jpg",
                    fullArtworkUri = "file:///artwork/b-thumb.jpg",
                ),
                sampleTrack(uri = "content://music/c").copy(
                    artworkUri = null,
                    fullArtworkUri = null,
                ),
            ),
        )

        assertEquals(
            setOf(
                "file:///artwork/a-thumb.jpg",
                "file:///artwork/a-full.jpg",
                "file:///artwork/b-thumb.jpg",
            ),
            dao.getArtworkUris().toSet(),
        )
    }

    @Test
    fun fileBackedDatabase_retainsTracksAfterReopen() = runBlocking {
        val databaseName = "track-dao-reopen-test.db"
        context.deleteDatabase(databaseName)
        val track = sampleTrack(uri = "content://music/persisted")

        try {
            val firstSession = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .build()
            try {
                firstSession.trackDao().upsert(track)
            } finally {
                firstSession.close()
            }

            val secondSession = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .build()
            try {
                assertEquals(listOf(track), secondSession.trackDao().observeAll().first())
            } finally {
                secondSession.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun mapper_combinesFileFactsAndExtractedMetadata() {
        val file = DiscoveredAudioFile(
            uri = Uri.parse("content://music/folder/track.mp3"),
            name = "track.mp3",
            mimeType = "audio/mpeg",
            size = 4_096L,
            lastModified = 1_700_000_000_000L,
            parentFolderUri = Uri.parse(FOLDER_A),
        )
        val metadata = TrackMetadata(
            title = "Title",
            artist = "Artist",
            album = "Album",
            durationMs = 210_000L,
            albumId = "album-key",
            artworkBytes = byteArrayOf(1, 2, 3),
        )

        val entity = file.toTrackEntity(
            metadata = metadata,
            dateAdded = 1_700_000_100_000L,
            artworkUri = "file:///artwork/track.mp3.jpg",
            fullArtworkUri = "file:///artwork/track.mp3-full.jpg",
        )

        assertEquals("content://music/folder/track.mp3", entity.uri)
        assertEquals("Title", entity.title)
        assertEquals("Artist", entity.artist)
        assertEquals("Album", entity.album)
        assertEquals(210_000L, entity.durationMs)
        assertEquals("album-key", entity.albumId)
        assertEquals("file:///artwork/track.mp3.jpg", entity.artworkUri)
        assertEquals("file:///artwork/track.mp3-full.jpg", entity.fullArtworkUri)
        assertTrue(entity.artworkScanned)
        assertTrue(entity.fullArtworkScanned)
        assertEquals(FOLDER_A, entity.folderUri)
        assertEquals(4_096L, entity.fileSize)
        assertEquals(1_700_000_000_000L, entity.lastModified)
        assertEquals(1_700_000_100_000L, entity.dateAdded)
    }

    private fun sampleTrack(
        uri: String,
        title: String = "Title",
        folderUri: String = FOLDER_A,
    ) = TrackEntity(
        uri = uri,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 210_000L,
        albumId = "album-key",
        artworkUri = "file:///artwork/cover.jpg",
        folderUri = folderUri,
        fileSize = 4_096L,
        lastModified = 1_700_000_000_000L,
        dateAdded = 1_700_000_100_000L,
    )

    private companion object {
        const val FOLDER_A = "content://music/folder-a"
        const val FOLDER_B = "content://music/folder-b"
    }
}
