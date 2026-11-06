package com.example.musicplayer.data.database

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.data.mapper.toTrack
import com.example.musicplayer.domain.model.TrackSort
import com.example.musicplayer.domain.usecase.filterAndSortTracks
import com.example.musicplayer.domain.usecase.groupTracksIntoAlbums
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic performance measurement of the library read path with a large synthetic
 * library (Task 19).
 *
 * It exercises exactly the work a screen opening performs — Room query,
 * entity-to-domain mapping, search/sort, and album grouping — over 10,000 tracks
 * and reports the timings. The assertions are deliberately generous bounds that
 * only fail on a pathological regression (for example an accidental per-item
 * query or a per-item artwork read); the numbers themselves are logged as
 * `LargeLibraryPerf` so they can be compared between runs.
 *
 * A real large-library scroll measurement with 5,000–10,000 decoded audio files
 * needs the multi-gigabyte test dataset that Task 20 prepares; this test covers
 * the data and transformation cost, which is the part that is independent of the
 * documents provider.
 */
@RunWith(AndroidJUnit4::class)
class LargeLibraryPerformanceTest {

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
    fun largeLibrary_queryMappingSortingAndGroupingStayWithinBudget() = runBlocking {
        dao.upsertAll(List(TRACK_COUNT) { index -> trackEntity(index) })

        val queryStartedAt = System.nanoTime()
        val rows = dao.observeAll().first()
        val queryMs = elapsedMs(queryStartedAt)

        val mappingStartedAt = System.nanoTime()
        val tracks = rows.map { it.toTrack() }
        val mappingMs = elapsedMs(mappingStartedAt)

        val sortStartedAt = System.nanoTime()
        val allSorted = filterAndSortTracks(tracks, query = "", sort = TrackSort.TITLE)
        val sortMs = elapsedMs(sortStartedAt)

        val searchStartedAt = System.nanoTime()
        val searched = filterAndSortTracks(tracks, query = "artist 7", sort = TrackSort.ARTIST)
        val searchMs = elapsedMs(searchStartedAt)

        val groupingStartedAt = System.nanoTime()
        val albums = groupTracksIntoAlbums(tracks)
        val groupingMs = elapsedMs(groupingStartedAt)

        val report = "tracks=$TRACK_COUNT query=${queryMs}ms mapping=${mappingMs}ms " +
            "sort=${sortMs}ms search(Sort)=${searchMs}ms group=${groupingMs}ms " +
            "albums=${albums.size} matched=${searched.size}"
        Log.i(TAG, report)
        println("$TAG: $report")

        assertEquals(TRACK_COUNT, rows.size)
        assertEquals(TRACK_COUNT, allSorted.size)
        assertEquals(ALBUM_COUNT, albums.size)
        assertTrue("search must still be correct at scale", searched.isNotEmpty())
        assertTrue("Room query took ${queryMs}ms", queryMs < BUDGET_MS)
        assertTrue("mapping took ${mappingMs}ms", mappingMs < BUDGET_MS)
        assertTrue("sorting took ${sortMs}ms", sortMs < BUDGET_MS)
        assertTrue("searching took ${searchMs}ms", searchMs < BUDGET_MS)
        assertTrue("grouping took ${groupingMs}ms", groupingMs < BUDGET_MS)
    }

    private fun trackEntity(index: Int) = TrackEntity(
        uri = "content://music/track-$index",
        title = "Track %05d".format(index),
        artist = "Artist ${index % ARTIST_COUNT}",
        album = "Album ${index % ALBUM_COUNT}",
        durationMs = 180_000L,
        albumId = "album-${index % ALBUM_COUNT}",
        artworkUri = null,
        folderUri = "content://music/tree",
        fileSize = 4_096L,
        lastModified = 1_700_000_000_000L,
        dateAdded = 1_700_000_000_000L,
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L

    private companion object {
        const val TAG = "LargeLibraryPerf"
        const val TRACK_COUNT = 10_000
        const val ALBUM_COUNT = 500
        const val ARTIST_COUNT = 200

        /**
         * Upper bound for a single step. Deliberately generous: this runs a
         * debug build with a cold JIT on an emulator, so only a pathological
         * regression (for example a query per item) can exceed it.
         */
        const val BUDGET_MS = 3_000L
    }
}
