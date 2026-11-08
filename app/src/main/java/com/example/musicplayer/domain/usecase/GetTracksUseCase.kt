package com.example.musicplayer.domain.usecase

import com.example.musicplayer.data.mapper.toTrack
import com.example.musicplayer.data.repository.TrackRepository
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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

    /**
     * Observes every indexed track, ordered as stored by Room.
     *
     * Mapping 10,000 Room rows into domain models is a visible amount of work,
     * so it runs on [Dispatchers.Default] instead of the collector's thread
     * (normally the main thread). Room still performs the query on its own
     * background executor.
     */
    operator fun invoke(): Flow<List<Track>> =
        trackRepository.observeAll()
            .map { rows -> rows.map { it.toTrack() } }
            .flowOn(Dispatchers.Default)
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
    shuffleSeed: Long = 0L,
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
    return filtered.sortedWith(sort.comparator(shuffleSeed))
}

private fun TrackSort.comparator(shuffleSeed: Long): Comparator<Track> = when (this) {
    TrackSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    TrackSort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
    // Grouping by album also follows the album's own running order, so the
    // Tracks view is not alphabetical inside each album.
    TrackSort.ALBUM -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
        .thenBy { it.discNumber ?: Int.MAX_VALUE }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy { it.uri }
    TrackSort.DURATION -> compareBy { it.durationMs }
    TrackSort.DATE_ADDED -> compareBy { it.dateAdded }
    TrackSort.SHUFFLE -> compareBy { shuffleRank(it.uri, shuffleSeed) }
}

/**
 * Deterministic pseudo-random rank for [uri] under [seed].
 *
 * A hash rank keeps the shuffle pure: the same seed always produces the same
 * order, so recomposition or a repeat emission does not reshuffle the list. The
 * ViewModel advances the seed when the user picks Shuffle again.
 */
private fun shuffleRank(uri: String, seed: Long): Long {
    var hash = uri.hashCode().toLong() xor (seed * -7046029254386353131L)
    hash = (hash xor (hash ushr 33)) * -49064778989728563L
    hash = (hash xor (hash ushr 33)) * -4265267296055464877L
    return hash xor (hash ushr 33)
}