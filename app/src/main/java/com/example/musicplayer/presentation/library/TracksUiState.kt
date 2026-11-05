package com.example.musicplayer.presentation.library

import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import com.example.musicplayer.domain.usecase.filterAndSortTracks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.FlowPreview

/**
 * UI state of the Tracks list, kept separate from the folder/scan lifecycle
 * state in [LibraryUiState].
 */
sealed interface TracksUiState {

    /** The first emission from Room has not arrived yet. */
    data object Loading : TracksUiState

    /** Reading the indexed library failed; nothing was deleted. */
    data class Error(val message: String) : TracksUiState

    /**
     * The library is loaded and the current search/sort has been applied.
     *
     * @property tracks Result of filtering + sorting, ready for the list.
     * @property totalTracks Size of the whole indexed library.
     * @property query Debounced search input the list was filtered with.
     * @property sort Active sort order.
     */
    data class Ready(
        val tracks: List<Track>,
        val totalTracks: Int,
        val query: String,
        val sort: TrackSort,
    ) : TracksUiState {

        /** True when the list is narrowed down by the search query. */
        val isFiltered: Boolean
            get() = tracks.size != totalTracks
    }
}

/** Search input must settle this long before the list is re-filtered. */
const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Builds the Track-list state stream from the three inputs that drive it.
 *
 * - [searchQuery] is debounced by [SEARCH_DEBOUNCE_MS] so typing does not
 *   re-filter per keystroke. A blank query bypasses the debounce, so the
 *   initial library appears without waiting.
 * - Explicit [sort] changes apply immediately (the debounce only gates the
 *   query stage of the `combine`).
 * - Filtering/sorting and the Room-to-domain mapping run on [dispatcher]
 *   rather than the main thread; a failing source is surfaced as
 *   [TracksUiState.Error] instead of crashing the stream.
 */
@OptIn(FlowPreview::class) // debounce() is still marked preview.
fun buildTracksUiState(
    tracks: Flow<List<Track>>,
    searchQuery: Flow<String>,
    sort: Flow<TrackSort>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<TracksUiState> {
    // Explicit type: R must widen to TracksUiState so .catch can emit Error.
    val state: Flow<TracksUiState> = combine(
        tracks,
        searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        sort,
    ) { allTracks, query, activeSort ->
        TracksUiState.Ready(
            tracks = filterAndSortTracks(allTracks, query, activeSort),
            totalTracks = allTracks.size,
            query = query,
            sort = activeSort,
        )
    }
    return state
        .flowOn(dispatcher)
        .catch { throwable ->
            emit(TracksUiState.Error(throwable.message ?: "Unknown error while loading tracks"))
        }
}