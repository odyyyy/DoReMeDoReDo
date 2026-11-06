package com.example.musicplayer.domain.usecase

import com.example.musicplayer.data.mapper.toTrack
import com.example.musicplayer.data.repository.TrackRepository
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Provides the indexed library as domain [Track] models.
 *
 * Exists as the presentation-to-data boundary for the library: ViewModels
 * observe domain models and never see Room entities. Re-emits whenever the
 * underlying Room query changes, so the UI updates automatically after scans.
 */
class GetTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
) {

    /** Observes every indexed track, ordered as stored by Room. */
    operator fun invoke(): Flow<List<Track>> =
        trackRepository.observeAll().map { rows -> rows.map { it.toTrack() } }
}

/**
 * Applies the active search query and sort order to [tracks].
 *
 * Pure and side-effect free, so it can run on any dispatcher (the Tracks view
 * invokes it on [kotlinx.coroutines.Dispatchers.Default] through `flowOn`).
 *
 * - Empty or blank [query] returns every track, just ordered.
 * - [query] matches title, artist, or album, case-insensitively.
 * - The match is done with a case-insensitive `contains` instead of lowercasing
 *   every field first: with a large library that saves three string allocations
 *   per track on every debounced keystroke.
 * - Text comparisons are case-insensitive; `sortedWith` is stable, so tracks
 *   with equal keys keep the storage order (title) provided by Room.
 */
fun filterAndSortTracks(
    tracks: List<Track>,
    query: String,
    sort: TrackSort,
): List<Track> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) {
        tracks
    } else {
        tracks.filter { track ->
            track.title.contains(needle, ignoreCase = true) ||
                track.artist.contains(needle, ignoreCase = true) ||
                track.album.contains(needle, ignoreCase = true)
        }
    }
    return filtered.sortedWith(sort.comparator())
}

private fun TrackSort.comparator(): Comparator<Track> = when (this) {
    TrackSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    TrackSort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
    TrackSort.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.album }
    TrackSort.DURATION -> compareBy { it.durationMs }
    TrackSort.DATE_ADDED -> compareBy { it.dateAdded }
}