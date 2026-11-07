package com.example.musicplayer.domain.model

/**
 * Orders tracks the way they appear on an album.
 *
 * Disc number comes first (a multi-disc release must not interleave its
 * discs), then the track number, and finally the title. Tracks without a
 * reliable number sort after numbered tracks and are still deterministic
 * because the title and URI break the remaining ties.
 */
val albumTrackComparator: Comparator<Track> =
    compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy { it.uri }
