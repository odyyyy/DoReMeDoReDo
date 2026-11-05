package com.example.musicplayer.presentation.library

import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Virtual-time tests for the Tracks state stream: search debounce, sorting,
 * and error propagation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TracksUiStateTest {

    private fun track(
        uri: String,
        title: String,
        durationMs: Long = 0L,
    ) = Track(
        uri = uri,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = durationMs,
        albumId = null,
        artworkUri = null,
        dateAdded = 0L,
    )

    @Test
    fun `initial library appears without waiting for the search debounce`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val results = mutableListOf<TracksUiState>()

        backgroundScope.launch(dispatcher) {
            buildTracksUiState(
                tracks = flowOf(listOf(track("uri://1", "Alpha"))),
                searchQuery = MutableStateFlow(""),
                sort = MutableStateFlow(TrackSort.TITLE),
                dispatcher = dispatcher,
            ).collect { results.add(it) }
        }

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, results.size)
        val ready = results.single() as TracksUiState.Ready
        assertEquals(1, ready.totalTracks)
        assertEquals("", ready.query)
    }

    @Test
    fun `rapid search input is debounced and settles on the final query`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val query = MutableStateFlow("")
        val results = mutableListOf<TracksUiState>()

        backgroundScope.launch(dispatcher) {
            buildTracksUiState(
                tracks = flowOf(
                    listOf(
                        track("uri://1", "Alpha"),
                        track("uri://2", "Beta"),
                    ),
                ),
                searchQuery = query,
                sort = MutableStateFlow(TrackSort.TITLE),
                dispatcher = dispatcher,
            ).collect { results.add(it) }
        }

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, results.size)

        // Two keystrokes within the debounce window.
        query.value = "alp"
        advanceTimeBy(100)
        runCurrent()
        query.value = "alpha"
        advanceTimeBy(100)
        runCurrent()

        // Nothing has settled yet: the intermediate value was swallowed.
        assertEquals(1, results.size)

        advanceTimeBy(SEARCH_DEBOUNCE_MS + 100)
        runCurrent()

        assertEquals(2, results.size)
        val ready = results.last() as TracksUiState.Ready
        assertEquals("alpha", ready.query)
        assertEquals(listOf("Alpha"), ready.tracks.map { it.title })
        assertTrue(ready.isFiltered)
        assertEquals(2, ready.totalTracks)
    }

    @Test
    fun `changing the sort order applies immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sort = MutableStateFlow(TrackSort.TITLE)
        val results = mutableListOf<TracksUiState>()

        backgroundScope.launch(dispatcher) {
            buildTracksUiState(
                tracks = flowOf(
                    listOf(
                        track("uri://1", "Alpha", durationMs = 300),
                        track("uri://2", "Beta", durationMs = 100),
                    ),
                ),
                searchQuery = MutableStateFlow(""),
                sort = sort,
                dispatcher = dispatcher,
            ).collect { results.add(it) }
        }

        advanceTimeBy(1)
        runCurrent()
        sort.value = TrackSort.DURATION
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, results.size)
        val ready = results.last() as TracksUiState.Ready
        assertEquals(TrackSort.DURATION, ready.sort)
        assertEquals(listOf("Beta", "Alpha"), ready.tracks.map { it.title })
    }

    @Test
    fun `a failing source becomes an error state instead of crashing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val results = mutableListOf<TracksUiState>()

        backgroundScope.launch(dispatcher) {
            buildTracksUiState(
                tracks = flow<List<Track>> { throw IllegalStateException("db gone") },
                searchQuery = MutableStateFlow(""),
                sort = MutableStateFlow(TrackSort.TITLE),
                dispatcher = dispatcher,
            ).collect { results.add(it) }
        }

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, results.size)
        val error = results.single() as TracksUiState.Error
        assertEquals("db gone", error.message)
    }
}