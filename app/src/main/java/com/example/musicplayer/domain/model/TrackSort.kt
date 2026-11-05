package com.example.musicplayer.domain.model

/**
 * Sort orders offered by the Tracks view.
 *
 * @property label User-visible name shown in the sort menu.
 */
enum class TrackSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Date added"),
}