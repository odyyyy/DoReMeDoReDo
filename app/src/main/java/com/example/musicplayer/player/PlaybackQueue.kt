package com.example.musicplayer.player

/**
 * Immutable snapshot of the player's queue.
 *
 * The queue *is* the Media3 playlist owned by [PlaybackService]; this type is
 * only a read-only projection of it, rebuilt from the player whenever the
 * playlist or the current item changes. The player stays the single source of
 * truth: screens and ViewModels observe this snapshot and send commands through
 * [PlayerController], but never hold an editable second copy of the queue.
 *
 * @property items The queued items in playback order. While shuffle is enabled
 *   this is the shuffled traversal order, so the Queue screen matches what will
 *   actually play next.
 * @property currentIndex Index of the current item inside [items], or `-1` when
 *   nothing is queued.
 * @property playlistIndices Playlist index of each entry in [items]. It is empty
 *   when [items] already follows the Media3 playlist order; otherwise it maps a
 *   displayed queue position back to the real playlist index for selection,
 *   removal and reordering.
 */
data class PlaybackQueue(
    val items: List<PlaybackTrack> = emptyList(),
    val currentIndex: Int = -1,
    val playlistIndices: List<Int> = emptyList(),
) {
    /** The item currently loaded by the player, or `null` for an empty queue. */
    val currentItem: PlaybackTrack? get() = items.getOrNull(currentIndex)

    /** Number of items in the queue. */
    val size: Int get() = items.size

    /** True when nothing is queued. */
    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * Maps a position in [items] to its index in the Media3 playlist.
     *
     * Falls back to the identity mapping for an unshuffled queue, which is the
     * common case and keeps the projection unchanged there.
     */
    fun playlistIndexAt(displayIndex: Int): Int =
        playlistIndices.getOrNull(displayIndex) ?: displayIndex
}
