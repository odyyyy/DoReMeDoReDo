package com.example.musicplayer.domain.usecase

import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure search + sorting logic behind the Tracks view.
 */
class GetTracksUseCaseTest {

    private fun track(
        uri: String,
        title: String,
        artist: String = "Unknown Artist",
        album: String = "Unknown Album",
        durationMs: Long = 0L,
        dateAdded: Long = 0L,
        trackNumber: Int? = null,
        discNumber: Int? = null,
    ) = Track(
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        albumId = null,
        artworkUri = null,
        dateAdded = dateAdded,
    )

    private val library = listOf(
        track("uri://1", "Alpha Song", "Artist One", "Album X", durationMs = 100, dateAdded = 30),
        track("uri://2", "Beta Song", "Artist Two", "Album Y", durationMs = 300, dateAdded = 10),
        track("uri://3", "Gamma Song", "Artist Three", "alpha vibes", durationMs = 200, dateAdded = 20),
    )

    @Test
    fun `empty query returns every track`() {
        val result = filterAndSortTracks(library, "", TrackSort.TITLE)
        assertEquals(3, result.size)
    }

    @Test
    fun `search matches title`() {
        val result = filterAndSortTracks(library, "beta", TrackSort.TITLE)
        assertEquals(listOf("uri://2"), result.map { it.uri })
    }

    @Test
    fun `search matches artist case-insensitively`() {
        val result = filterAndSortTracks(library, "ARTIST TWO", TrackSort.TITLE)
        assertEquals(listOf("uri://2"), result.map { it.uri })
    }

    @Test
    fun `search matches album`() {
        val result = filterAndSortTracks(library, "alpha vibes", TrackSort.TITLE)
        assertEquals(listOf("uri://3"), result.map { it.uri })
    }

    @Test
    fun `search matches across title and album for the same term`() {
        val result = filterAndSortTracks(library, "alpha", TrackSort.TITLE)
        assertEquals(setOf("uri://1", "uri://3"), result.map { it.uri }.toSet())
        assertEquals(2, result.size)
    }

    @Test
    fun `search trims surrounding whitespace`() {
        val result = filterAndSortTracks(library, "  alpha  ", TrackSort.TITLE)
        assertEquals(2, result.size)
    }

    @Test
    fun `search with no matches returns an empty list`() {
        assertTrue(filterAndSortTracks(library, "does-not-exist", TrackSort.TITLE).isEmpty())
    }

    @Test
    fun `sort by title is case-insensitive`() {
        val tracks = listOf(
            track("uri://c", "cherry"),
            track("uri://a", "apple"),
            track("uri://b", "Banana"),
        )
        val result = filterAndSortTracks(tracks, "", TrackSort.TITLE)
        assertEquals(listOf("apple", "Banana", "cherry"), result.map { it.title })
    }

    @Test
    fun `sort by artist orders case-insensitively`() {
        val tracks = listOf(
            track("uri://1", title = "T1", artist = "zebra"),
            track("uri://2", title = "T2", artist = "Ant"),
            track("uri://3", title = "T3", artist = "mongoose"),
        )
        val result = filterAndSortTracks(tracks, "", TrackSort.ARTIST)
        assertEquals(listOf("T2", "T3", "T1"), result.map { it.title })
    }

    @Test
    fun `sort by album groups alphabetically`() {
        val tracks = listOf(
            track("uri://1", title = "T1", album = "Zulu"),
            track("uri://2", title = "T2", album = "Alpha"),
        )
        val result = filterAndSortTracks(tracks, "", TrackSort.ALBUM)
        assertEquals(listOf("uri://2", "uri://1"), result.map { it.uri })
    }

    @Test
    fun `sort by album follows the album running order inside each album`() {
        val tracks = listOf(
            track("uri://3", title = "Third", album = "Album A", trackNumber = 3),
            track("uri://1", title = "First", album = "Album A", trackNumber = 1),
            track("uri://2", title = "Second", album = "Album A", trackNumber = 2),
            track("uri://b", title = "Other", album = "Album B", trackNumber = 1),
        )

        val result = filterAndSortTracks(tracks, "", TrackSort.ALBUM)

        assertEquals(
            listOf("uri://1", "uri://2", "uri://3", "uri://b"),
            result.map { it.uri },
        )
    }

    @Test
    fun `sort by duration is ascending`() {
        val result = filterAndSortTracks(library, "", TrackSort.DURATION)
        assertEquals(listOf(100L, 200L, 300L), result.map { it.durationMs })
    }

    @Test
    fun `sort by date added is ascending`() {
        val result = filterAndSortTracks(library, "", TrackSort.DATE_ADDED)
        assertEquals(listOf(10L, 20L, 30L), result.map { it.dateAdded })
    }

    @Test
    fun `sorting applies after filtering`() {
        val result = filterAndSortTracks(library, "artist", TrackSort.DURATION)
        assertEquals(listOf(100L, 200L, 300L), result.map { it.durationMs })
        assertEquals(3, result.size)
    }

    @Test
    fun `shuffle sort keeps every track and is stable for one seed`() {
        val first = filterAndSortTracks(library, "", TrackSort.SHUFFLE, shuffleSeed = 7L)
        val second = filterAndSortTracks(library, "", TrackSort.SHUFFLE, shuffleSeed = 7L)

        assertEquals(library.map { it.uri }.toSet(), first.map { it.uri }.toSet())
        assertEquals(first.map { it.uri }, second.map { it.uri })
    }

    @Test
    fun `shuffle sort changes order when the seed advances`() {
        val tracks = (1..16).map { index ->
            track("uri://$index", "Track $index")
        }
        val first = filterAndSortTracks(tracks, "", TrackSort.SHUFFLE, shuffleSeed = 1L)
        val second = filterAndSortTracks(tracks, "", TrackSort.SHUFFLE, shuffleSeed = 2L)

        assertEquals(tracks.map { it.uri }.toSet(), first.map { it.uri }.toSet())
        assertNotEquals(first.map { it.uri }, second.map { it.uri })
    }
}