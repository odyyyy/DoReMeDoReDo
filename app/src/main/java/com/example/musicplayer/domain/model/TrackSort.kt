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

    /**
     * Random order for the displayed list. The order is derived from a seed that
     * advances every time this option is chosen, so picking it again reshuffles
     * the list instead of showing the same random order.
     */
    SHUFFLE("Shuffle"),
}